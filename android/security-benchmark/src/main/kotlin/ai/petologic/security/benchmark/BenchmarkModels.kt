package ai.petologic.security.benchmark

import ai.petologic.skills.security.models.SecurityVerdict
import kotlinx.serialization.Serializable

@Serializable enum class BenchmarkSuite { HASH, URL, MESSAGE, APP_PROFILE }
@Serializable enum class BenchmarkLabel { BENIGN, SUSPICIOUS, CRITICAL }
@Serializable enum class BenchmarkSplit { CALIBRATION, VALIDATION, HOLDOUT }
@Serializable enum class BenchmarkMode { SMOKE, RELEASE }

@Serializable data class BenchmarkSource(val id:String,val license:String,val purpose:String)
@Serializable data class BenchmarkAppSignals(val installer:String?=null,val requestedPermissions:List<String> = emptyList(),
 val grantedDangerous:List<String> = emptyList(),val overlay:Boolean=false,val accessibility:Boolean=false,
 val deviceAdmin:Boolean=false,val vpnService:Boolean=false,val notificationListener:Boolean=false)

@Serializable data class BenchmarkCase(
 val id:String,val suite:BenchmarkSuite,val locale:String="und",val split:BenchmarkSplit,
 val familyId:String,val sourceId:String,val label:BenchmarkLabel,val expectedMinVerdict:SecurityVerdict,
 val input:String?=null,val knownSha256:String?=null,val appSignals:BenchmarkAppSignals?=null)

@Serializable data class RetrievalRecord(val id:String,val locale:String,val familyId:String,val sourceId:String,val text:String)
@Serializable data class RetrievalQuery(val id:String,val locale:String,val familyId:String,val sourceId:String,
 val text:String,val relevantRecordIds:List<String>)

@Serializable data class SafeBenchmarkCorpus(val schema:Int=1,val profile:String,val corpusVersion:String,
 val sources:List<BenchmarkSource>,val cases:List<BenchmarkCase>,
 val retrievalRecords:List<RetrievalRecord> = emptyList(),val retrievalQueries:List<RetrievalQuery> = emptyList())

@Serializable data class CaseMeasurement(val caseDigest:String,val suite:BenchmarkSuite,val locale:String,
 val label:BenchmarkLabel,val verdict:SecurityVerdict,val elapsedMicros:Long,val passedExpectedVerdict:Boolean)
@Serializable data class MetricGroup(val name:String,val suspicious:Int,val detected:Int,val recall:Double?,
 val critical:Int,val criticalHigh:Int,val criticalHighRate:Double?,val benign:Int,val benignHigh:Int,
 val benignHighRate:Double?,val benignAlerted:Int,val benignAlertRate:Double?)
@Serializable data class GateResult(val name:String,val passed:Boolean,val actual:String,val requirement:String)
@Serializable data class BenchmarkReport(val schema:Int=1,val generatedAt:String,val disclaimer:String,
 val mode:BenchmarkMode,val corpusVersion:String,val corpusSha256:String,val gitRevision:String,
 val modelHash:String,val deviceFingerprint:String,val measurements:List<CaseMeasurement>,
 val metrics:List<MetricGroup>,val gates:List<GateResult>,val passed:Boolean)

@Serializable data class RetrievalPass(val run:Int,val locale:String,val queries:Int,val hitsAt5:Int,
 val recallAt5:Double,val p95Millis:Long)
@Serializable data class RetrievalLocaleSummary(val locale:String,val medianRecallAt5:Double,val worstP95Millis:Long,
 val recallPassed:Boolean,val latencyPassed:Boolean)
@Serializable data class RetrievalBenchmarkReport(val schema:Int=2,val generatedAt:String,val disclaimer:String,
 val corpusVersion:String,val corpusSha256:String,val gitRevision:String,val modelHash:String,val deviceFingerprint:String,
 val deviceModel:String,val networkDisabled:Boolean,
 val records:Int,val warmupQueries:Int,val passes:List<RetrievalPass>,val summaries:List<RetrievalLocaleSummary>,val passed:Boolean)
