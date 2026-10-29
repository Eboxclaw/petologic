package ai.petologic.paladino

import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import ai.petologic.core.*
import ai.petologic.paladino.data.*
import ai.petologic.security.*
import ai.petologic.skills.security.guard.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import ai.petologic.paladino.runtime.VisualSecurityAnalyzer

class SecurityGuardIntegrationTest {
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 @Test fun realLocalExplanationHasNoTools()=runBlocking{
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realGuardModel")=="true")
  val app=context.applicationContext as PaladinoApplication
  app.modelLibrary.verifyAll()
  assertTrue("Install the pinned VL-450M model", ModelCatalog.SPEAKER_ID in app.modelLibrary.installed.value)
  val start=android.os.SystemClock.elapsedRealtime()
  val response=kotlinx.coroutines.withTimeout(60000){ai.petologic.paladino.runtime.SubAgent.worker(app.inference,ModelCatalog.SPEAKER_ID,
   "Explain these security detector results briefly. Never claim safety or removal. State uncertainty.",
   "Finding: suspicious patterns. Evidence: urgent request for banking details and an HTTP link. Coverage: local heuristic checks only; no malware confirmed.")}
  assertTrue(response.isNotBlank())
  java.io.File(context.filesDir,"security-explanation-evidence.txt").writeText("elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}\n$response")
 }
 @Test fun signedBundledPackVerifiesOnAndroid(){
  val store=ThreatPackStore(context);val pack=store.current()
  assertNotNull("Bundled preview pack must validate on Android",pack)
  assertEquals(28,pack!!.indicators.size)
  assertEquals(pack.version,store.install(store.signedPackBytes()).version)
 }
 @Test fun realVisualModelInspectsEphemeralImageWithoutTools()=runBlocking{
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realVision")=="true")
  val app=context.applicationContext as PaladinoApplication
  app.modelLibrary.verifyAll()
  assertTrue(ModelCatalog.SPEAKER_ID in app.modelLibrary.installed.value)
  assertTrue(ModelCatalog.VISION_PROJECTOR_ID in app.modelLibrary.installed.value)
  val file=File(context.cacheDir,"updates/visual-${UUID.randomUUID()}.png").apply{parentFile!!.mkdirs()}
  val bitmap=android.graphics.Bitmap.createBitmap(320,180,android.graphics.Bitmap.Config.ARGB_8888)
  val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE)
  val paint=android.graphics.Paint().apply{color=android.graphics.Color.RED;textSize=26f;isAntiAlias=true}
  canvas.drawText("URGENT ACCOUNT LOCKED",16f,58f,paint);paint.color=android.graphics.Color.BLACK;paint.textSize=20f
  canvas.drawText("Enter password at 193.42.11.7",16f,105f,paint)
  file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
  try{
   val start=android.os.SystemClock.elapsedRealtime()
   val response=kotlinx.coroutines.withTimeout(120000){VisualSecurityAnalyzer(app,app.local).analyze(uri,"English")}
   assertTrue(response.isNotBlank());assertFalse(response.contains("<|tool_call",ignoreCase=true))
   File(context.filesDir,"visual-security-evidence.txt").writeText("elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}\n$response")
  }finally{file.delete()}
 }
 @Test fun isolatedScannerReadsOnlyPassedDescriptor()=runBlocking{
  val file=File(context.cacheDir,"updates/security-${UUID.randomUUID()}.txt").apply{parentFile!!.mkdirs();writeText("PETOLOGIC_SECURITY_SELF_TEST")}
  val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
  val pack=ThreatPackStore(context).snapshot()
  try{
   val result=IsolatedScanner.scan(context,uri,pack)
   val repeated=IsolatedScanner.scan(context,uri,pack)
   assertEquals(result.fingerprint,repeated.fingerprint)
   assertNotNull(result.fingerprint)
   assertTrue(result.evidence.any{it.matchType==MatchType.SELF_TEST})
   assertFalse(result.evidence.any{it.matchType==MatchType.EXACT_SHA256})
   assertEquals("Harmless scanner self-test matched",result.summary)
  }finally{file.delete();pack?.delete()}
 }
 @Test fun externalActionsAreClaimedOnceAndRevocationPreventsDispatch()=runBlocking{
  val db=Room.inMemoryDatabaseBuilder(context,PaladinoDatabase::class.java).build()
  try{
   val repo=MemoryRepository(context,db);val p=ApprovalPolicy().propose("security.action","bound-target")
   repo.propose(p,"guard-test");var callbacks=0
   assertTrue(runCatching{repo.executeApprovedAction(p,p.argumentHash,"guard-test",authorize={error("revoked")},dispatch={callbacks++;"OK|security.action|handoff"})}.isFailure)
   assertEquals(0,callbacks)
   repo.executeApprovedAction(p,p.argumentHash,"guard-test",authorize={},dispatch={callbacks++;"OK|security.action|handoff"})
   assertTrue(runCatching{repo.executeApprovedAction(p,p.argumentHash,"guard-test",authorize={},dispatch={callbacks++;"OK|security.action|handoff"})}.isFailure)
   assertEquals(1,callbacks)
   assertEquals("HANDED_OFF",repo.dao.action(p.id)!!.status)
  }finally{db.close()}
 }
 @Test fun textFindingsDoNotRetainPrivateInput()=runBlocking{
  val repo=(context.applicationContext as PaladinoApplication).guard
  var id:String?=null
  try{
   val finding=repo.scanText("URGENT password reset for SECRET_PERSON_123 http://193.42.11.7/SECRET_TOKEN")
   id=finding.id
   assertFalse(finding.toString().contains("SECRET_PERSON_123"))
   assertFalse(finding.toString().contains("SECRET_TOKEN"))
   val stored=requireNotNull(repo.db.dao().finding(finding.id)).json
   assertFalse(stored.contains("SECRET_PERSON_123"));assertFalse(stored.contains("SECRET_TOKEN"))
  }finally{id?.let{repo.delete(it)}}
 }
 @Test fun threatMemoryIndexesRetrievesAndDeletesReviewedKnowledge()=runBlocking{
  val db=Room.inMemoryDatabaseBuilder(context,SecurityDatabase::class.java).build()
 val embedder=object:SecurityVectorEmbedder{
   override val ready=true;override val modelId="test-security-embedder";override val modelRevision="1"
   private fun vector(text:String)=FloatArray(1024).also{v->if(text.contains("bank",true))v[0]=1f else v[1]=1f}
   override suspend fun embedQuery(text:String)=vector(text)
   override suspend fun embedDocument(text:String)=vector(text)
  }
  val prefsName="security-test-${UUID.randomUUID()}"
  var memory:ThreatMemoryRepository?=null
  try{
   val threatMemory=ThreatMemoryRepository(context,db,embedder,prefsName=prefsName);memory=threatMemory
   val now=System.currentTimeMillis()
   val bank=KnowledgeRecord("bank-lure",1,KnowledgeKind.MESSAGE_LURE,"en","Urgent bank request for a one-time code.","test lab","1",TrustTier.REVIEWED,now+60_000)
   val packageRisk=KnowledgeRecord("package-risk",1,KnowledgeKind.APP_BEHAVIOR,"en","Sideloaded package requests accessibility.","test lab","1",TrustTier.REVIEWED,now+60_000)
   threatMemory.install(SecurityKnowledgePack(version=1,issuedAt=now-1000,expiresAt=now+60_000,records=listOf(bank,packageRisk)))
   val match=threatMemory.retrieve(ThreatQuery("bank verification code",KnowledgeKind.MESSAGE_LURE,limit=3))
   assertEquals("bank-lure",match.first().id);assertTrue(match.first().similarity>0.99f)
   val prior=Finding(id="prior-bank",createdAt=now,kind="text",coverage=Coverage.COMPLETE,
    checks=listOf("Local bank-message patterns"),limitations=listOf("No reputation feed"))
   db.dao().insert(FindingRow(prior.id,prior.createdAt,Json.encodeToString(prior)));threatMemory.queueFinding(prior);threatMemory.reconcile(1)
   assertEquals("prior-bank",threatMemory.retrieveFindings("bank message",limit=3).first().id)
   threatMemory.deleteOwner(VectorNamespace.FINDING,prior.id)
   assertTrue(threatMemory.retrieveFindings("bank message",limit=3).none{it.id==prior.id})
   threatMemory.install(SecurityKnowledgePack(version=2,issuedAt=now,expiresAt=now+60_000,records=listOf(packageRisk.copy(revision=2))))
   assertTrue(threatMemory.retrieve(ThreatQuery("bank verification code",limit=3)).none{it.id=="bank-lure"})
   assertNull(db.dao().knowledge("bank-lure"))
   assertTrue(runCatching{threatMemory.install(SecurityKnowledgePack(version=1,issuedAt=now,expiresAt=now+60_000,records=listOf(bank)))}.isFailure)
   threatMemory.revoke(listOf(2));assertNull(db.dao().knowledge("package-risk"));assertTrue(threatMemory.status.value is ThreatMemoryStatus.NotReady)
  }finally{memory?.clear();db.close();context.getSharedPreferences(prefsName,android.content.Context.MODE_PRIVATE).edit().clear().commit()}
 }
 @Test fun securityDatabaseMigratesFromFindingsOnlySchema()=runBlocking{
  val name="security-migration-${UUID.randomUUID()}.db"
  val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),SecurityDatabase::class.java)
  helper.createDatabase(name,1).apply{execSQL("INSERT INTO findings(id,createdAt,json) VALUES('kept',1,'{}')");close()}
  val migrated=Room.databaseBuilder(context,SecurityDatabase::class.java,name).addMigrations(SecurityMigrations.MIGRATION_1_2).build()
  try{
   migrated.openHelper.writableDatabase
   assertNotNull(migrated.dao().finding("kept"))
   assertEquals(0,migrated.dao().knowledgeCount(1))
  }finally{migrated.close();context.deleteDatabase(name)}
 }
}
