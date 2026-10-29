package ai.petologic.skills.security.guard

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import ai.petologic.security.*
import ai.petologic.skills.security.analysis.MessageAnalyzer
import ai.petologic.skills.security.models.SecurityVerdict
import ai.petologic.skills.security.android.AppInspector
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GuardRepository(private val context:Context){
 val db=Room.databaseBuilder(context,SecurityDatabase::class.java,"security.db").addMigrations(SecurityMigrations.MIGRATION_1_2).build()
 val threatMemory=ThreatMemoryRepository(context,db,LiquidSecurityVectorEmbedder((context.applicationContext as ai.petologic.paladino.PaladinoApplication).vectorMemory))
 val findings=db.dao().observe()
 val latest=findings.map{rows->rows.mapNotNull{runCatching{Json.decodeFromString<Finding>(it.json)}.getOrNull()}}
  .stateIn((context.applicationContext as ai.petologic.paladino.PaladinoApplication).scope,SharingStarted.Eagerly,emptyList())
 val packs=ThreatPackStore(context)
 suspend fun expire(){
  val cutoff=System.currentTimeMillis()-ThreatMemoryRepository.FINDING_TTL
  val ids=db.dao().expiredFindingIds(cutoff)
  db.withTransaction {
   db.dao().expireFindings(cutoff)
   if(ids.isNotEmpty()){db.dao().deleteVectors(VectorNamespace.FINDING.name,ids);db.dao().deleteQueued(VectorNamespace.FINDING.name,ids)}
  }
  ids.forEach{threatMemory.deleteOwner(VectorNamespace.FINDING,it)}
 }
 suspend fun record(finding:Finding):Finding {
  expire()
  db.withTransaction {db.dao().insert(FindingRow(finding.id,finding.createdAt,Json.encodeToString(finding)));threatMemory.queueFinding(finding)}
  (context.applicationContext as ai.petologic.paladino.PaladinoApplication).scope.launch {runCatching{threatMemory.reconcile(packs.currentManifestVersion())}}
  return finding
 }
 suspend fun scanFile(uri:Uri):Finding {
  val pack=packs.snapshot()
  return try{record(IsolatedScanner.scan(context,uri,pack))}finally{pack?.delete()}
 }
 suspend fun scanText(raw:String):Finding=record(analyzeText(raw))
 private val reputation by lazy{ReputationClient(context)}
 suspend fun scanApp(packageName:String,reason:String="user-requested"):Finding {
  require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(packageName)){"Enter a valid Android package name."}
  val profile=AppInspector.profile(context,packageName)
  if(profile==null)return record(Finding(kind="app",coverage=Coverage.UNAVAILABLE,
   limitations=listOf("Android does not expose information about this app (visibility or name)."),
   checks=listOf("Installed-app visibility")))
  val result=ai.petologic.skills.security.analysis.AppRiskEngine.assess("App $packageName",profile.signals)
  val unavailable=result.verdict==SecurityVerdict.UNKNOWN
  val evidence=mutableListOf<Evidence>()
  if(result.verdict!=SecurityVerdict.LOW&&!unavailable)evidence+=result.reasons.mapIndexed{i,detail->
   Evidence("android-app-profile","2","app-signal-$i","Paladino","first-party",MatchType.HEURISTIC,detail)
  }
  val checks=mutableListOf("Android permissions, installer, signing and service profile")
  val limits=mutableListOf("Android package visibility and available metadata limit this check.","App capability signals are not proof of malware.")
  var coverage=if(unavailable)Coverage.UNAVAILABLE else Coverage.COMPLETE
  val packSnapshot=packs.snapshot()
  if(profile.sourceFiles.isEmpty()){
   coverage=Coverage.PARTIAL;limits+="Android did not expose the installed APK files."
  }else try{
   profile.sourceFiles.forEachIndexed { index,file->
    val local=runCatching{IsolatedScanner.scan(context,file,packSnapshot)}.getOrElse{
     coverage=Coverage.PARTIAL;limits+="An installed APK could not be fully scanned within the isolated limits.";null
    }
    if(local!=null){
     evidence+=local.evidence
     local.fingerprint?.let { hash->
      when(val remote=reputation.lookup(hash)){
       is ReputationResult.Match->evidence+=Evidence("private-sha256-reputation","1",remote.entry.indicatorId,remote.entry.source,remote.entry.license,MatchType.EXACT_SHA256,"Exact installed APK hash matched signed private reputation.")
       ReputationResult.NoMatch->Unit
       is ReputationResult.Unavailable->{coverage=Coverage.PARTIAL;limits+=remote.reason}
      }
     }
     if(local.coverage!=Coverage.COMPLETE){coverage=Coverage.PARTIAL;limits+=local.limitations}
    }
    checks+="Installed APK ${index+1} SHA-256 and bounded static archive structure in an isolated process"
   }
  }finally{packSnapshot?.delete()}
  checks+="Android signing certificate metadata"
  limits+="Broad malware-family rules and behavioral execution are not installed."
  return record(Finding(kind="app",fingerprint=profile.versionFingerprint,evidence=evidence.distinctBy{it.detector to it.indicatorId},coverage=coverage,
   limitations=limits.distinct(),checks=checks.distinct()+"Trigger: $reason"))
 }
 suspend fun analyzeText(raw:String):Finding=withContext(Dispatchers.Default){
  require(raw.isNotBlank()){"Enter a message or URL."}
  val analysis=MessageAnalyzer.scanMessage(raw)
  val limited=boundText(raw).truncated||Regex("(https?://|www\\.)\\S+",RegexOption.IGNORE_CASE).findAll(boundText(raw).text).take(21).count()>20
  // Persist detector reasons only, never the submitted text, links, sender or notification title.
  val evidence=if(analysis.verdict==SecurityVerdict.LOW)emptyList() else analysis.reasons.mapIndexed{i,reason->
   Evidence("text-heuristics","1","pattern-$i","Petologic","first-party",MatchType.HEURISTIC,reason)
  }
  Finding(kind="text",evidence=evidence,coverage=if(limited)Coverage.PARTIAL else Coverage.COMPLETE,
   limitations=listOf("Pattern checks only; no URL reputation feed is installed.","No match is not a guarantee of safety.")+if(limited)listOf("Input or link limit reached.")else emptyList(),checks=listOf("Local text and link patterns"))
 }
 fun protection():ProtectionStatus {
  val pack=packs.current()
  val vector=when(val state=threatMemory.status.value){
   ThreatMemoryStatus.NotReady->"Liquid vector memory is not installed"
   is ThreatMemoryStatus.Indexing->"Indexing ${state.indexed}/${state.total} reviewed records"
   is ThreatMemoryStatus.Ready->"Pack ${state.packVersion}: ${state.indexed} records · ${state.backend}"
   is ThreatMemoryStatus.Stale->"Threat memory pack ${state.packVersion} expired"
   is ThreatMemoryStatus.Failed->"Threat memory needs attention: ${state.message}"
  }
  return ProtectionStatus(vectorMemory=vector,rules="YARA 4.5.8 — harmless self-test rule only",signatures=when{pack==null->"No valid threat pack — missing, expired or invalid";pack.expiresAt<=System.currentTimeMillis()->"Threat pack expired";else->"Pack ${pack.version}: ${pack.indicators.size} exact indicators"})
 }
 suspend fun clear(){
  val ids=db.dao().observe().first().map{it.id}
  db.withTransaction {db.dao().clearFindings();if(ids.isNotEmpty()){db.dao().deleteVectors(VectorNamespace.FINDING.name,ids);db.dao().deleteQueued(VectorNamespace.FINDING.name,ids)}}
  ids.forEach{threatMemory.deleteOwner(VectorNamespace.FINDING,it)}
 }
 suspend fun delete(id:String){
  db.withTransaction {db.dao().deleteFinding(id);db.dao().deleteVectors(VectorNamespace.FINDING.name,id);db.dao().indexDone(VectorNamespace.FINDING.name,id)}
  threatMemory.deleteOwner(VectorNamespace.FINDING,id)
 }
 suspend fun export(uri:Uri)=withContext(Dispatchers.IO){
  // Use an explicit redacted export instead of the ordinary personal-memory backup.
  val rows=findings.first()
  requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use{out->
   out.write("{\"format\":1,\"findings\":[")
   out.write(rows.joinToString(","){it.json});out.write("]}")
  }
 }
}
