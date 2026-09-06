package ai.petologic.paladino.data

import android.content.Context
import androidx.appsearch.app.*
import androidx.appsearch.localstorage.LocalStorage
import androidx.room.withTransaction
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ai.petologic.core.*
import java.time.Instant

/** Room is authoritative. AppSearch is a rebuildable lexical index, not an embedder. */
class MemoryRepository(context:Context, val db:PaladinoDatabase, private val embedder:ai.petologic.paladino.runtime.SmallEmbedder?=null) {
 private val sessionFuture=LocalStorage.createSearchSessionAsync(LocalStorage.SearchContext.Builder(context,"paladino").build())
 private val indexMutex=Mutex()
 private var initialized=false
 val dao=db.dao()
 val notes=dao.observeNotes()
 private suspend fun session():AppSearchSession {
  val s=sessionFuture.await()
  if(!initialized){
   val schema=AppSearchSchema.Builder("Note").addProperty(AppSearchSchema.StringPropertyConfig.Builder("text").setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL).setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES).setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN).build()).build()
   s.setSchemaAsync(SetSchemaRequest.Builder().addSchemas(schema).build()).await();initialized=true
  }
  return s
 }
 suspend fun reconcile()=indexMutex.withLock {
  val s=session()
  for(p in dao.pendingIndex()){
   val n=dao.note(p.noteId)
   if(n==null){s.removeAsync(RemoveByDocumentIdRequest.Builder(p.sessionId).addIds(p.noteId).build()).await()}
   else {
    val doc=GenericDocument.Builder<GenericDocument.Builder<*>>(n.sessionId,n.id,"Note").setPropertyString("text",n.text).setCreationTimestampMillis(n.updatedAt).build()
    check(s.putAsync(PutDocumentsRequest.Builder().addGenericDocuments(doc).build()).await().isSuccess){"Memory index update failed."}
   }
   dao.indexDone(p.noteId,p.version)
  }
 }
 suspend fun search(query:String,sessionId:String="default"):List<MemoryNote> {
  // Query grammar is never accepted from a model/user. Use safe plain tokens.
  val terms=Regex("[\\p{L}\\p{N}]+").findAll(query).map { it.value }.take(12).toList()
  if(terms.isEmpty())return emptyList()
  val ids=try { indexMutex.withLock {
   val s=session();val result=s.search(terms.joinToString(" OR "),SearchSpec.Builder().addFilterNamespaces(sessionId).setTermMatch(SearchSpec.TERM_MATCH_PREFIX).setResultCountPerPage(10).build())
   try{result.nextPageAsync.await().map{it.genericDocument.id}}finally{result.close()}
  }}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){emptyList()}
  // Canonical validation prevents deleted results from escaping a stale index.
  val indexed=ids.mapNotNull{dao.note(it)?.takeIf{n->n.sessionId==sessionId}?.domain()}
  val fallback=dao.notes(sessionId).filter { row -> terms.any{row.text.contains(it,ignoreCase=true)} }.take(10).map{it.domain()}
  val lexical=(indexed+fallback).distinctBy{it.id}
  val encoder=embedder
  if(encoder==null||!encoder.ready.value)return lexical.take(10)
  val q=encoder.embed(query)
  // Scaffold cap is explicit. Chunking/10k-corpus performance remains a release gate.
  val semantic=dao.notes(sessionId).take(200).map{row->
   val cached=dao.embedding(row.id,row.updatedAt,ai.petologic.paladino.runtime.SmallEmbedder.ID)
   val v=if(cached!=null){java.nio.ByteBuffer.wrap(cached.vector).asFloatBuffer().let{b->FloatArray(b.remaining()).also{b.get(it)}}}else{
    encoder.embed(row.text).also{vector->val b=java.nio.ByteBuffer.allocate(vector.size*4);vector.forEach{b.putFloat(it)};dao.embedding(EmbeddingRow(row.id,row.updatedAt,ai.petologic.paladino.runtime.SmallEmbedder.ID,b.array()))}
   }
   row.domain() to q.indices.sumOf{(q[it]*v[it]).toDouble()}
  }.sortedByDescending{it.second}.filter{it.second>=0.25}.take(10).map{it.first}
  // Reciprocal rank fusion keeps lexical matches while admitting true paraphrases.
  return (lexical+semantic).distinctBy{it.id}.sortedByDescending{note->
   listOf(lexical,semantic).sumOf{list->val rank=list.indexOfFirst{it.id==note.id};if(rank<0)0.0 else 1.0/(60+rank)}
  }.take(10).mapNotNull{hit->dao.note(hit.id)?.takeIf{it.updatedAt==hit.updatedAt}?.domain()}
 }
 suspend fun propose(p:ActionProposal,sessionId:String="default"){dao.putAction(ActionRow(p.id,p.tool,p.argument,p.argumentHash,p.expiresAt.toEpochMilli(),sessionId=sessionId))}
 suspend fun executeNote(p:ActionProposal,approvedHash:String,sessionId:String="default"):String=db.withTransaction {
  ApprovalPolicy().validate(p,approvedHash)
  val row=checkNotNull(dao.action(p.id)){"Action no longer exists."}
  check(row.sessionId==sessionId){"Action belongs to another session."}
  check(row.argumentHash==approvedHash){"Stored action changed."}
  if(row.status=="COMPLETED")return@withTransaction "This action was already completed."
  check(row.status=="PENDING"){"Action is no longer pending."}
  when(p.tool){
   "notes.create"->{dao.putNote(NoteRow(p.id,p.argument,sessionId=sessionId));dao.outbox(IndexRow(p.id,System.currentTimeMillis(),sessionId));dao.edge(EdgeRow(p.id,"paladino",p.id,"owns_note",p.id,System.currentTimeMillis()))}
   "notes.delete"->{check(dao.note(p.argument)?.sessionId==sessionId){"Note belongs to another session."};dao.deleteNote(p.argument);dao.deleteEmbedding(p.argument);dao.deleteEdges(p.argument);dao.outbox(IndexRow(p.argument,System.currentTimeMillis(),sessionId))}
   else->error("Unsupported memory action.")
  }
  dao.actionStatus(p.id,"COMPLETED")
  if(p.tool=="notes.create")"Saved to your private memory." else "Deleted from your memory."
 }
 suspend fun cancel(id:String){dao.actionStatus(id,"CANCELLED")}
}
