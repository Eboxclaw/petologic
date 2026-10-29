package ai.petologic.security.benchmark

import ai.petologic.skills.security.models.SecurityVerdict
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64
import kotlin.test.*

class BenchmarkTest {
 private val json=Json{encodeDefaults=true}
 private val raw by lazy{File(requireNotNull(System.getProperty("paladino.benchmark.smoke"))).readBytes()}
 private val registry by lazy{File(requireNotNull(System.getProperty("paladino.benchmark.registry"))).readBytes()}
 private val corpus by lazy{json.decodeFromString<SafeBenchmarkCorpus>(raw.decodeToString())}

 @Test fun safeSmokeCorpusPassesAllDeterministicGates(){
  val pinnedHash=File(requireNotNull(System.getProperty("paladino.benchmark.smokeHash"))).readText().substringBefore(' ')
  assertEquals(pinnedHash,BenchmarkRunner.sha256(raw))
  val validated=CorpusValidator.parseAndValidate(raw,registry,BenchmarkMode.SMOKE)
  val report=BenchmarkRunner.run(validated,BenchmarkMode.SMOKE,"test-revision","not-used","test-jvm")
  assertTrue(report.passed,report.gates.filterNot{it.passed}.joinToString())
  assertTrue(report.measurements.all{it.caseDigest.length==64})
  assertFalse(json.encodeToString(report).contains("Lunch tomorrow"))
  assertEquals(BenchmarkRunner.DISCLAIMER,report.disclaimer)
 }

 @Test fun validatorRejectsArchiveAndExecutableMagicWithoutOpeningAnything(){
  listOf(byteArrayOf(0x50,0x4b,0x03,0x04),byteArrayOf(0x4d,0x5a,0,0),byteArrayOf(0x7f,0x45,0x4c,0x46)).forEach{
   assertFailsWith<UnsafeCorpusException>{CorpusValidator.parseAndValidate(it,registry,BenchmarkMode.SMOKE)}
  }
 }

 @Test fun validatorRejectsEmbeddedBinaryActiveLinksSecretsPiiAndTraversal(){
  val zipHeader=Base64.getEncoder().encodeToString(byteArrayOf(0x50,0x4b)+ByteArray(40))
  val unsafeInputs=listOf(zipHeader,"https://openai.com/path","password=not-a-real-secret","person@gmail.com","../outside")
  unsafeInputs.forEachIndexed{index,value->
   val changed=corpus.copy(cases=corpus.cases.mapIndexed{i,c->if(i==0)c.copy(suite=BenchmarkSuite.MESSAGE,input=value,knownSha256=null,appSignals=null,expectedMinVerdict=SecurityVerdict.REVIEW)else c})
   assertFailsWith<UnsafeCorpusException>("unsafe input $index"){validate(changed)}
  }
 }

 @Test fun validatorRejectsDuplicateIdsCrossSplitFamiliesBadLabelsAndSources(){
  assertFailsWith<UnsafeCorpusException>{validate(corpus.copy(cases=corpus.cases+corpus.cases.first()))}
  val crossed=corpus.cases.first().copy(id="crossed-split",split=BenchmarkSplit.CALIBRATION)
  assertFailsWith<UnsafeCorpusException>{validate(corpus.copy(cases=corpus.cases+crossed))}
  val missing=corpus.cases.first().copy(id="missing-source",familyId="missing-source-family",sourceId="unknown-source")
  assertFailsWith<UnsafeCorpusException>{validate(corpus.copy(cases=corpus.cases+missing))}
  val mismatch=corpus.copy(sources=corpus.sources.map{it.copy(license="Unknown")})
  assertFailsWith<UnsafeCorpusException>{validate(mismatch)}
  val invalidLabel=raw.decodeToString().replaceFirst("\"label\":\"CRITICAL\"","\"label\":\"DANGEROUS\"")
  assertFailsWith<UnsafeCorpusException>{CorpusValidator.parseAndValidate(invalidLabel.encodeToByteArray(),registry,BenchmarkMode.SMOKE)}
 }

 @Test fun releaseModeRejectsSmokeAndMissingScale(){
  assertFailsWith<UnsafeCorpusException>{CorpusValidator.parseAndValidate(raw,registry,BenchmarkMode.RELEASE)}
  assertFailsWith<UnsafeCorpusException>{validate(corpus.copy(profile="release"),BenchmarkMode.RELEASE)}
 }

 @Test fun recallAndFalsePositiveThresholdBoundariesAreExact(){
  fun urlCase(index:Int,label:BenchmarkLabel,input:String)=BenchmarkCase("boundary-$label-$index",BenchmarkSuite.URL,"und",BenchmarkSplit.HOLDOUT,
   "boundary-$label-family-$index","paladino-safe-synthetic",label,
   if(label==BenchmarkLabel.BENIGN)SecurityVerdict.LOW else SecurityVerdict.REVIEW,input=input)
  val source=corpus.sources
  val passingCases=(0 until 97).map{urlCase(it,BenchmarkLabel.SUSPICIOUS,"http://login-$it.example.invalid") }+
   (97 until 100).map{urlCase(it,BenchmarkLabel.SUSPICIOUS,"https://clean-$it.example.invalid") }+
   (0 until 100).map{urlCase(it,BenchmarkLabel.BENIGN,"https://benign-$it.example.invalid")}
  val passing=run(corpus.copy(cases=passingCases,retrievalRecords=emptyList(),retrievalQueries=emptyList()))
  assertTrue(passing.gates.first{it.name=="url suspicious recall"}.passed)
  val recallFail=run(corpus.copy(cases=passingCases.map{if(it.id=="boundary-SUSPICIOUS-96")it.copy(input="https://clean-96.example.invalid")else it},retrievalRecords=emptyList(),retrievalQueries=emptyList()))
  assertFalse(recallFail.gates.first{it.name=="url suspicious recall"}.passed)
  val oneHigh=passingCases.map{if(it.id=="boundary-BENIGN-0")it.copy(input="http://192.0.2.44/login",expectedMinVerdict=SecurityVerdict.HIGH_ATTENTION)else it}
  val highFail=run(corpus.copy(cases=oneHigh,retrievalRecords=emptyList(),retrievalQueries=emptyList()))
  assertFalse(highFail.gates.first{it.name=="url benign high-attention rate"}.passed)
 }

 @Test fun percentileUsesNearestRank(){
  assertEquals(5,BenchmarkRunner.percentile(listOf(1,2,3,4,5),0.95))
  assertEquals(1,BenchmarkRunner.percentile(listOf(1,2,3,4,5),0.01))
 }

 @Test fun retrievalSummaryUsesMedianRecallAndWorstLatency(){
  val passes=listOf(
   RetrievalPass(1,"en",100,91,0.91,150),RetrievalPass(2,"en",100,89,0.89,199),RetrievalPass(3,"en",100,95,0.95,175),
   RetrievalPass(1,"pt",100,90,0.90,180),RetrievalPass(2,"pt",100,92,0.92,201),RetrievalPass(3,"pt",100,93,0.93,190))
  val result=RetrievalScoring.summarize(passes)
  assertEquals(0.91,result.first{it.locale=="en"}.medianRecallAt5)
  assertTrue(result.first{it.locale=="en"}.latencyPassed)
  assertFalse(result.first{it.locale=="pt"}.latencyPassed)
 }

 private fun validate(value:SafeBenchmarkCorpus,mode:BenchmarkMode=BenchmarkMode.SMOKE)=
  CorpusValidator.parseAndValidate(json.encodeToString(value).encodeToByteArray(),registry,mode)
 private fun run(value:SafeBenchmarkCorpus)=BenchmarkRunner.run(validate(value),BenchmarkMode.SMOKE,"test","none","jvm")
}
