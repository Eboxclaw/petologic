package ai.petologic.security.benchmark

import ai.petologic.security.HashIndicator
import ai.petologic.security.HashIndicatorMatcher
import ai.petologic.skills.security.analysis.*
import ai.petologic.skills.security.models.SecurityVerdict
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.ceil

object BenchmarkRunner {
 const val DISCLAIMER="Inert behavioral benchmark only. It does not execute malware or certify live-malware detection."

 fun run(validated:CorpusValidator.Validated,mode:BenchmarkMode,gitRevision:String,modelHash:String,deviceFingerprint:String):BenchmarkReport {
  val measurements=validated.corpus.cases.sortedBy{it.id}.map(::measure)
  val metrics=buildList {
   group("url",measurements.filter{it.suite==BenchmarkSuite.URL})?.let(::add)
   group("app_profile",measurements.filter{it.suite==BenchmarkSuite.APP_PROFILE})?.let(::add)
   for(locale in listOf("en","pt"))group("message_$locale",measurements.filter{it.suite==BenchmarkSuite.MESSAGE&&it.locale==locale})?.let(::add)
  }
  val gates=buildList {
   val expectedFailures=measurements.count{!it.passedExpectedVerdict}
   add(GateResult("case expectations",expectedFailures==0,"$expectedFailures failures","0 failures"))
   metrics.forEach{metric->
    metric.recall?.let{add(GateResult("${metric.name} suspicious recall",it>=0.97,format(it),">= 0.9700"))}
    metric.criticalHighRate?.let{add(GateResult("${metric.name} critical high-attention rate",it>=0.90,format(it),">= 0.9000"))}
    metric.benignHighRate?.let{add(GateResult("${metric.name} benign high-attention rate",it<0.01,format(it),"< 0.0100"))}
    metric.benignAlertRate?.let{add(GateResult("${metric.name} benign alert rate",it<=0.15,format(it),"<= 0.1500"))}
   }
   val hashes=measurements.filter{it.suite==BenchmarkSuite.HASH}
   val positive=hashes.filter{it.label!=BenchmarkLabel.BENIGN};val negative=hashes.filter{it.label==BenchmarkLabel.BENIGN}
   if(positive.isNotEmpty())add(GateResult("known hash recall",positive.all{it.verdict==SecurityVerdict.HIGH_ATTENTION},"${positive.count{it.verdict==SecurityVerdict.HIGH_ATTENTION}}/${positive.size}","100%"))
   if(negative.isNotEmpty())add(GateResult("mutated hash matches",negative.none{it.verdict!=SecurityVerdict.LOW},"${negative.count{it.verdict!=SecurityVerdict.LOW}} matches","0 matches"))
  }
  return BenchmarkReport(generatedAt=Instant.now().toString(),disclaimer=DISCLAIMER,mode=mode,
   corpusVersion=validated.corpus.corpusVersion,corpusSha256=sha256(validated.raw),gitRevision=gitRevision,
   modelHash=modelHash,deviceFingerprint=deviceFingerprint,measurements=measurements,metrics=metrics,gates=gates,passed=gates.all{it.passed})
 }

 private fun measure(case:BenchmarkCase):CaseMeasurement {
  val started=System.nanoTime()
  val verdict=when(case.suite){
   BenchmarkSuite.URL->SecurityAnalyzers.url.analyze(requireNotNull(case.input)).verdict
   BenchmarkSuite.MESSAGE->SecurityAnalyzers.message.analyze(requireNotNull(case.input)).verdict
   BenchmarkSuite.APP_PROFILE->{val a=requireNotNull(case.appSignals);SecurityAnalyzers.app.analyze(AppAssessment("Benchmark app",
    AppSignals(a.installer,a.requestedPermissions,a.grantedDangerous,a.overlay,a.accessibility,a.deviceAdmin,a.vpnService,a.notificationListener))).verdict}
   BenchmarkSuite.HASH->{
    val indicator=HashIndicator(requireNotNull(case.knownSha256),"benchmark-indicator","inert benchmark","test-only")
    if(HashIndicatorMatcher.match(requireNotNull(case.input),listOf(indicator))!=null)SecurityVerdict.HIGH_ATTENTION else SecurityVerdict.LOW
   }
  }
  val expectation=if(case.label==BenchmarkLabel.BENIGN)verdict==case.expectedMinVerdict else rank(verdict)>=rank(case.expectedMinVerdict)
  return CaseMeasurement(sha256(case.id.encodeToByteArray()),case.suite,case.locale,case.label,verdict,
   (System.nanoTime()-started)/1_000,expectation)
 }

 private fun group(name:String,items:List<CaseMeasurement>):MetricGroup? {
  if(items.isEmpty())return null
  val suspicious=items.filter{it.label!=BenchmarkLabel.BENIGN};val critical=items.filter{it.label==BenchmarkLabel.CRITICAL};val benign=items.filter{it.label==BenchmarkLabel.BENIGN}
  val detected=suspicious.count{rank(it.verdict)>=rank(SecurityVerdict.REVIEW)};val criticalHigh=critical.count{it.verdict==SecurityVerdict.HIGH_ATTENTION}
  val benignHigh=benign.count{it.verdict==SecurityVerdict.HIGH_ATTENTION};val benignAlert=benign.count{rank(it.verdict)>=rank(SecurityVerdict.REVIEW)}
  return MetricGroup(name,suspicious.size,detected,rate(detected,suspicious.size),critical.size,criticalHigh,rate(criticalHigh,critical.size),
   benign.size,benignHigh,rate(benignHigh,benign.size),benignAlert,rate(benignAlert,benign.size))
 }
 private fun rate(n:Int,d:Int)=if(d==0)null else n.toDouble()/d
 private fun rank(v:SecurityVerdict)=when(v){SecurityVerdict.UNKNOWN->-1;SecurityVerdict.LOW->0;SecurityVerdict.REVIEW->1;SecurityVerdict.HIGH_ATTENTION->2}
 fun percentile(values:List<Long>,percentile:Double):Long {require(values.isNotEmpty()&&percentile in 0.0..1.0);val sorted=values.sorted();return sorted[(ceil(percentile*sorted.size).toInt()-1).coerceAtLeast(0)]}
 private fun format(value:Double)="%.4f".format(java.util.Locale.ROOT,value)
 fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
