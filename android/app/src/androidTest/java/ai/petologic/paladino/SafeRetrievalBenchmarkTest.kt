package ai.petologic.paladino

import ai.petologic.core.ModelCatalog
import ai.petologic.security.*
import ai.petologic.security.benchmark.*
import ai.petologic.skills.security.guard.*
import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Opt-in physical-device benchmark. The staged corpus is validated before the embedding model sees
 * any text, must contain only inert material, and is deleted after the run. No network call is made.
 */
@RunWith(AndroidJUnit4::class)
class SafeRetrievalBenchmarkTest {
 private val reportJson=Json{prettyPrint=true;encodeDefaults=true}
 @Test fun pixel7aSafeRetrievalReleaseGate()=runBlocking {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val args=InstrumentationRegistry.getArguments()
  assumeTrue("opt in with -e safeRetrievalBenchmark true",args.getString("safeRetrievalBenchmark")=="true")
  val app=instrumentation.targetContext.applicationContext as PaladinoApplication
  val stagingRoot=requireNotNull(app.getExternalFilesDir("safe-benchmark")).canonicalFile
  val corpusPath=requireNotNull(args.getString("safeBenchmarkCorpus")){"Pass -e safeBenchmarkCorpus with a staged app-external corpus path."}
  val corpusFile=File(corpusPath).canonicalFile
  require(corpusFile.toPath().startsWith(stagingRoot.toPath())){"Corpus must be staged under ${stagingRoot.path}."}
  require(corpusFile.isFile){"Safe benchmark corpus is missing."}

  val prefName="safe_retrieval_benchmark_${System.currentTimeMillis()}"
  val db=Room.inMemoryDatabaseBuilder(app,SecurityDatabase::class.java).build()
  val index=ThreatVectorIndex(app,"paladino-safe-benchmark-${System.currentTimeMillis()}")
  val repo=ThreatMemoryRepository(app,db,LiquidSecurityVectorEmbedder(app.vectorMemory),index,prefName)
  try {
   assertEquals("Physical release benchmark must run on Pixel 7a.","Pixel 7a",Build.MODEL)
   val memory=(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let{manager->ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem}
   assertTrue("Reference phone must expose at least 7 GiB usable RAM.",memory>=7L*1024*1024*1024)
   val connectivity=app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
   val caps=connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
   assertFalse("Disable Wi-Fi and mobile data before the safe benchmark.",caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true)
   withTimeout(4L*60*60*1000){
    val raw=corpusFile.readBytes()
    val registry=instrumentation.context.assets.open("source-registry.json").use{it.readBytes()}
    val validated=CorpusValidator.parseAndValidate(raw,registry,BenchmarkMode.RELEASE)
    app.vectorMemory.verify();assertTrue("Install the pinned Liquid Embedding-350M model first.",app.vectorMemory.ready.value)
    val now=System.currentTimeMillis()
    val records=validated.corpus.retrievalRecords.map{record->KnowledgeRecord(record.id,1,KnowledgeKind.CAMPAIGN,
     record.locale,record.text,"safe benchmark:${record.sourceId}",validated.corpus.corpusVersion,
     TrustTier.REVIEWED,now+6L*60*60*1000,limitations=listOf("Inert benchmark text; not a live threat indicator."))}
    repo.install(SecurityKnowledgePack(version=now,issuedAt=now,expiresAt=now+6L*60*60*1000,records=records))

    val queries=validated.corpus.retrievalQueries.sortedBy{it.id}
    suspend fun runPass(number:Int):List<RetrievalPass> = listOf("en","pt").map{locale->
     val localeQueries=queries.filter{it.locale==locale};var hits=0;val durations=ArrayList<Long>(localeQueries.size)
     localeQueries.forEach{query->
      val started=System.nanoTime()
      val result=repo.retrieve(ThreatQuery(query.text,locale=locale,limit=5))
      durations+=(System.nanoTime()-started)/1_000_000
      if(result.any{it.id in query.relevantRecordIds})hits++
     }
     RetrievalPass(number,locale,localeQueries.size,hits,hits.toDouble()/localeQueries.size,
      BenchmarkRunner.percentile(durations,0.95))
    }

    runPass(0) // complete warm-up; intentionally excluded from release metrics
    val passes=(1..3).flatMap{runPass(it)}
    val summaries=RetrievalScoring.summarize(passes)
    val report=RetrievalBenchmarkReport(generatedAt=Instant.now().toString(),disclaimer=BenchmarkRunner.DISCLAIMER,
     corpusVersion=validated.corpus.corpusVersion,corpusSha256=BenchmarkRunner.sha256(raw),
     gitRevision=args.getString("benchmarkGitRevision")?:"unknown",
     modelHash=ModelCatalog.get(ModelCatalog.EMBEDDING_ID).sha256,deviceFingerprint=Build.FINGERPRINT,
     deviceModel=Build.MODEL,networkDisabled=true,
     records=records.size,warmupQueries=queries.size,passes=passes,summaries=summaries,
     passed=summaries.map{it.locale}.toSet()==setOf("en","pt")&&summaries.all{it.recallPassed&&it.latencyPassed})
    File(requireNotNull(app.getExternalFilesDir("safe-benchmark-results")),"retrieval-report.json")
     .writeText(reportJson.encodeToString(report)+"\n")
    assertTrue("Retrieval release gates failed: $summaries",report.passed)
   }
  } finally {
   runCatching{repo.clear()}
   assertTrue(db.dao().knowledgeIds().isEmpty())
   assertTrue(db.dao().vectorOwnerIds(VectorNamespace.KNOWLEDGE.name).isEmpty())
   db.close()
   app.getSharedPreferences(prefName,Context.MODE_PRIVATE).edit().clear().commit()
   app.vectorMemory.unload()
   assertTrue("Staged safe corpus must be deleted after the benchmark.",!corpusFile.exists()||corpusFile.delete())
  }
 }
}
