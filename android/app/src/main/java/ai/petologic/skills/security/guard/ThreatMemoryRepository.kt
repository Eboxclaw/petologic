package ai.petologic.skills.security.guard

import android.content.Context
import androidx.room.withTransaction
import ai.petologic.core.ModelCatalog
import ai.petologic.paladino.runtime.LocalVectorMemory
import ai.petologic.security.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

interface SecurityVectorEmbedder {
 val ready:Boolean
 val modelId:String
 val modelRevision:String
 suspend fun embedQuery(text:String):FloatArray
 suspend fun embedDocument(text:String):FloatArray
}
class LiquidSecurityVectorEmbedder(private val delegate:LocalVectorMemory):SecurityVectorEmbedder {
 override val ready get()=delegate.ready.value
 override val modelId=LocalVectorMemory.ID
 override val modelRevision=ModelCatalog.get(modelId).sha256
 override suspend fun embedQuery(text:String)=delegate.embedQuery(text)
 override suspend fun embedDocument(text:String)=delegate.embedDocument(text)
}

class ThreatMemoryRepository(private val context:Context,private val db:SecurityDatabase,
 private val embedder:SecurityVectorEmbedder,private val index:ThreatVectorIndex=ThreatVectorIndex(context),
 prefsName:String=PREFS):ThreatMemory {
 private val dao=db.dao()
 private val prefs=context.getSharedPreferences(prefsName,Context.MODE_PRIVATE)
 private val modelId=embedder.modelId
 private val modelRevision=embedder.modelRevision
 private val mutableStatus=MutableStateFlow<ThreatMemoryStatus>(ThreatMemoryStatus.NotReady)
 val status:StateFlow<ThreatMemoryStatus> = mutableStatus

 suspend fun install(pack:SecurityKnowledgePack){
  require(pack.version>=prefs.getLong("knowledge_version",0)){"Knowledge pack rollback rejected."}
  val obsolete=dao.obsoleteKnowledgeIds(pack.version)
  val rows=pack.records.map { record->KnowledgeRow(record.id,record.revision,pack.version,record.kind.name,
   record.language,record.canonicalText,record.source,record.sourceVersion,record.trust.name,record.validUntil,
   Json.encodeToString(record.evidenceRefs),Json.encodeToString(record.limitations),sha256(record.canonicalText)) }
  db.withTransaction {
   if(obsolete.isNotEmpty()){dao.deleteVectors(VectorNamespace.KNOWLEDGE.name,obsolete);dao.deleteQueued(VectorNamespace.KNOWLEDGE.name,obsolete)}
   if(rows.isNotEmpty())dao.putKnowledge(rows)
   dao.deleteObsoleteKnowledge(pack.version)
   dao.deleteObsoleteEdges(pack.version)
   if(pack.edges.isNotEmpty())dao.putEdges(pack.edges.map{ThreatEdgeRow(it.fromId,it.toId,it.relation,pack.version)})
   rows.forEach{dao.queue(SecurityIndexRow(VectorNamespace.KNOWLEDGE.name,it.id,it.revision,"UPSERT"))}
  }
  prefs.edit().putLong("knowledge_version",pack.version).putLong("knowledge_expires",pack.expiresAt).apply()
  obsolete.forEach{runCatching{index.remove(VectorNamespace.KNOWLEDGE.name,it)}}
  reconcile(pack.version)
 }

 suspend fun queueFinding(finding:Finding){
  dao.queue(SecurityIndexRow(VectorNamespace.FINDING.name,finding.id,finding.createdAt,"UPSERT"))
 }

 override suspend fun reconcile(packVersion:Long){
  if(!embedder.ready){mutableStatus.value=ThreatMemoryStatus.NotReady;return}
  val total=dao.knowledgeCount(packVersion)
  var indexed=dao.vectorCount(VectorNamespace.KNOWLEDGE.name,modelId,modelRevision,System.currentTimeMillis())
  mutableStatus.value=ThreatMemoryStatus.Indexing(indexed,total,packVersion)
  try{
   while(true){
    val pending=dao.pending(50);if(pending.isEmpty())break
    pending.forEach { item->
     try { reconcileOne(item);dao.indexDone(item.namespace,item.ownerId) }
     catch(e:Exception){dao.indexFailed(item.namespace,item.ownerId);throw e}
    }
    indexed=dao.vectorCount(VectorNamespace.KNOWLEDGE.name,modelId,modelRevision,System.currentTimeMillis())
    mutableStatus.value=ThreatMemoryStatus.Indexing(indexed,total,packVersion)
   }
   dao.expireVectors(System.currentTimeMillis())
   val expiry=prefs.getLong("knowledge_expires",0)
   mutableStatus.value=if(expiry in 1..System.currentTimeMillis())ThreatMemoryStatus.Stale(packVersion,expiry)
    else ThreatMemoryStatus.Ready(indexed,packVersion,System.currentTimeMillis(),if(index.available())"AppSearch exact" else "Room exact")
  }catch(e:Exception){mutableStatus.value=ThreatMemoryStatus.Failed(e.message?:"Threat memory indexing failed.");throw e}
 }

 private suspend fun reconcileOne(item:SecurityIndexRow){
  if(item.operation=="DELETE"){dao.deleteVectors(item.namespace,item.ownerId);index.remove(item.namespace,item.ownerId);return}
  when(item.namespace){
   VectorNamespace.KNOWLEDGE.name->{
    val row=dao.knowledge(item.ownerId)?:run{index.remove(item.namespace,item.ownerId);return}
    val vector=getOrCreate(item,row.canonicalText,row.contentHash,row.validUntil)
    index.put(item.namespace,row.id,row.kind,row.source,vector,modelRevision)
   }
   VectorNamespace.FINDING.name->{
    val row=dao.finding(item.ownerId)?:run{index.remove(item.namespace,item.ownerId);return}
    val finding=Json.decodeFromString<Finding>(row.json)
    val text=buildString { append(finding.kind);append(". ");append(finding.checks.joinToString(". "))
     finding.evidence.forEach{append(". ");append(it.detail)};finding.limitations.forEach{append(". ");append(it)} }
    val vector=getOrCreate(item,text,sha256(text),row.createdAt+FINDING_TTL)
    index.put(item.namespace,row.id,KnowledgeKind.CAMPAIGN.name,"local finding",vector,modelRevision)
   }
  }
 }

 private suspend fun getOrCreate(item:SecurityIndexRow,text:String,contentHash:String,expiresAt:Long):FloatArray {
  val cached=dao.vector(item.namespace,item.ownerId,item.ownerRevision,modelId,modelRevision)
  if(cached!=null&&cached.contentHash==contentHash)return decode(cached.vector,cached.dimension)
  val vector=embedder.embedDocument(text)
  validateVector(vector)
  dao.deleteVectors(item.namespace,item.ownerId)
  dao.putVector(SecurityVectorRow(item.namespace,item.ownerId,item.ownerRevision,modelId,modelRevision,contentHash,
   vector.size,encode(vector),System.currentTimeMillis(),expiresAt))
  return vector
 }

 override suspend fun retrieve(query:ThreatQuery):List<KnowledgeMatch>{
  if(!embedder.ready)return emptyList()
  val q=embedder.embedQuery(query.text);validateVector(q)
  val indexed=runCatching{index.search(VectorNamespace.KNOWLEDGE.name,q,modelRevision,query.limit*3)}.getOrDefault(emptyList())
  val scored=if(indexed.isNotEmpty())indexed else dao.vectors(VectorNamespace.KNOWLEDGE.name,System.currentTimeMillis())
   .map{it.ownerId to dot(q,decode(it.vector,it.dimension))}.sortedByDescending{it.second}.take(query.limit*3)
  val filterKind=query.kind?.name
  return scored.mapNotNull{(id,score)->dao.knowledge(id)?.takeIf{filterKind==null||it.kind==filterKind}?.match(score)}
   .sortedByDescending{it.similarity}.take(query.limit)
 }

 override suspend fun retrieveFindings(text:String,excludeId:String?,limit:Int):List<RelatedFinding>{
  require(text.isNotBlank()&&limit in 1..20)
  if(!embedder.ready)return emptyList()
  val q=embedder.embedQuery(text);validateVector(q)
  val indexed=runCatching{index.search(VectorNamespace.FINDING.name,q,modelRevision,limit*3)}.getOrDefault(emptyList())
  val scored=if(indexed.isNotEmpty())indexed else dao.vectors(VectorNamespace.FINDING.name,System.currentTimeMillis())
   .map{it.ownerId to dot(q,decode(it.vector,it.dimension))}.sortedByDescending{it.second}.take(limit*3)
  return scored.filter{it.first!=excludeId}.mapNotNull{(id,score)->
   dao.finding(id)?.let{row->Json.decodeFromString<Finding>(row.json).let{finding->
    RelatedFinding(finding.id,finding.createdAt,finding.kind,score,finding.checks,finding.limitations)
   }}
  }.sortedByDescending{it.similarity}.take(limit)
 }

 override suspend fun deleteOwner(namespace:VectorNamespace,ownerId:String){
  db.withTransaction {dao.deleteVectors(namespace.name,ownerId);dao.indexDone(namespace.name,ownerId)}
  runCatching{index.remove(namespace.name,ownerId)}
 }

 suspend fun revoke(versions:Collection<Long>){
  if(prefs.getLong("knowledge_version",0) !in versions)return
  val ids=db.withTransaction {
   val known=dao.knowledgeIds()
   dao.clearKnowledge();dao.clearEdges();dao.deleteVectors(VectorNamespace.KNOWLEDGE.name);dao.deleteQueued(VectorNamespace.KNOWLEDGE.name);known
  }
  ids.forEach{id->runCatching{index.remove(VectorNamespace.KNOWLEDGE.name,id)}}
  check(prefs.edit().remove("knowledge_version").remove("knowledge_expires").commit()){"Knowledge revocation checkpoint failed."}
  mutableStatus.value=ThreatMemoryStatus.NotReady
 }

 suspend fun clear(){
  val owners=db.withTransaction {
   val indexed=VectorNamespace.entries.associateWith{dao.vectorOwnerIds(it.name)}
   dao.clearKnowledge();dao.clearEdges();dao.clearVectors();dao.clearQueue();indexed
  }
  owners.forEach{(namespace,ids)->ids.forEach{id->runCatching{index.remove(namespace.name,id)}}}
  mutableStatus.value=ThreatMemoryStatus.NotReady
 }

 private fun KnowledgeRow.match(score:Float)=KnowledgeMatch(id,KnowledgeKind.valueOf(kind),source,sourceVersion,packVersion,
  TrustTier.valueOf(trust),score,Json.decodeFromString(evidenceRefsJson),Json.decodeFromString(limitationsJson))
 private fun validateVector(vector:FloatArray){
  require(vector.size==1024&&vector.all{it.isFinite()}){"Invalid Liquid security vector."}
  val norm=sqrt(vector.sumOf{(it*it).toDouble()}).toFloat()
  require(abs(norm-1f)<0.02f){"Security vector is not normalized."}
 }
 private fun encode(vector:FloatArray)=ByteBuffer.allocate(vector.size*4).apply{vector.forEach(::putFloat)}.array()
 private fun decode(bytes:ByteArray,dimension:Int):FloatArray {require(dimension==1024&&bytes.size==dimension*4);return ByteBuffer.wrap(bytes).asFloatBuffer().let{b->FloatArray(b.remaining()).also{b.get(it)}}}
 private fun dot(a:FloatArray,b:FloatArray):Float {require(a.size==b.size);var score=0f;for(i in a.indices)score+=a[i]*b[i];return score}
 private fun sha256(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
 companion object { const val PREFS="security_pack";const val FINDING_TTL=7L*24*60*60*1000 }
}
