package ai.petologic.security

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable enum class Coverage { COMPLETE, PARTIAL, UNAVAILABLE }
@Serializable enum class MatchType { EXACT_SHA256, RULE, HEURISTIC, SELF_TEST }
@Serializable enum class KnowledgeKind { MESSAGE_LURE, URL_PATTERN, APP_BEHAVIOR, CAMPAIGN, EXPLANATION }
@Serializable enum class TrustTier { VERIFIED, REVIEWED, LOCAL }
@Serializable enum class VectorNamespace { KNOWLEDGE, FINDING, USER_POLICY }
@Serializable enum class ThreatArtifactKind { HASH_SET, URL_SET, YARA_RULES, KNOWLEDGE, REVOCATION }
@Serializable data class Evidence(val detector:String, val version:String, val indicatorId:String,
 val source:String, val license:String, val matchType:MatchType, val detail:String)
@Serializable data class Finding(val id:String=UUID.randomUUID().toString(), val createdAt:Long=System.currentTimeMillis(),
 val kind:String, val fingerprint:String?=null, val evidence:List<Evidence> = emptyList(),
 val coverage:Coverage, val limitations:List<String>, val checks:List<String>) {
 val summary:String get()=when {
  evidence.any{it.matchType==MatchType.EXACT_SHA256}->"Known indicator matched"
  evidence.any{it.matchType==MatchType.RULE}->"Detection rule matched"
  evidence.any{it.matchType==MatchType.SELF_TEST}->"Harmless scanner self-test matched"
  evidence.isNotEmpty()->"Review suspicious patterns"
  else->"No match in performed checks"
 }
}
data class SecurityInput(val id:String, val kind:String, val observedAt:Long, val coverage:Coverage)
data class ScanBudget(val maxInput:Long=256L*1024*1024, val maxExpanded:Long=512L*1024*1024,
 val maxEntry:Long=64L*1024*1024, val maxEntries:Int=4096, val timeoutMillis:Long=10_000)
@Serializable data class HashIndicator(val sha256:String,val id:String,val source:String,val license:String) {
 init { require(Regex("[0-9a-f]{64}").matches(sha256));require(id.isNotBlank()&&source.isNotBlank()&&license.isNotBlank()) }
}
@Serializable data class ThreatPack(val schema:Int=1,val version:Long,val issuedAt:Long,val expiresAt:Long,
 val minEngine:Int=1,val indicators:List<HashIndicator>)
data class ProtectionStatus(val signatures:String,val vectorMemory:String="Not connected to security findings",
 val rules:String="Unavailable",val notifications:String="Off")
@Serializable data class ThreatQuery(val text:String,val kind:KnowledgeKind?=null,val locale:String?=null,val limit:Int=8) {
 init { require(text.isNotBlank());require(limit in 1..20) }
}
@Serializable data class KnowledgeMatch(val id:String,val kind:KnowledgeKind,val source:String,val sourceVersion:String,
 val packVersion:Long,val trust:TrustTier,val similarity:Float,val evidenceRefs:List<String> = emptyList(),
 val limitations:List<String> = emptyList())
@Serializable data class RelatedFinding(val id:String,val createdAt:Long,val kind:String,val similarity:Float,
 val checks:List<String>,val limitations:List<String>)
@Serializable sealed class ThreatMemoryStatus {
 @Serializable data object NotReady:ThreatMemoryStatus()
 @Serializable data class Indexing(val indexed:Int,val total:Int,val packVersion:Long):ThreatMemoryStatus()
 @Serializable data class Ready(val indexed:Int,val packVersion:Long,val updatedAt:Long,val backend:String):ThreatMemoryStatus()
 @Serializable data class Stale(val packVersion:Long,val expiredAt:Long):ThreatMemoryStatus()
 @Serializable data class Failed(val message:String):ThreatMemoryStatus()
}
interface ThreatMemory {
 suspend fun retrieve(query:ThreatQuery):List<KnowledgeMatch>
 suspend fun retrieveFindings(text:String,excludeId:String?=null,limit:Int=5):List<RelatedFinding>
 suspend fun reconcile(packVersion:Long)
 suspend fun deleteOwner(namespace:VectorNamespace,ownerId:String)
}

@Serializable data class KnowledgeRecord(val id:String,val revision:Long,val kind:KnowledgeKind,
 val language:String?=null,val canonicalText:String,val source:String,val sourceVersion:String,
 val trust:TrustTier,val validUntil:Long,val evidenceRefs:List<String> = emptyList(),
 val limitations:List<String> = emptyList())
@Serializable data class ThreatEdge(val fromId:String,val toId:String,val relation:String)
@Serializable data class SecurityKnowledgePack(val schema:Int=1,val version:Long,val issuedAt:Long,val expiresAt:Long,
 val records:List<KnowledgeRecord>,val edges:List<ThreatEdge> = emptyList())

@Serializable data class ThreatArtifact(val kind:ThreatArtifactKind,val url:String,val sha256:String,val size:Long,
 val recordCount:Int,val sourceSetVersion:String,val licenseManifestSha256:String,val required:Boolean=true)
@Serializable data class ThreatManifest(val schema:Int=1,val channel:String="stable",val version:Long,
 val issuedAt:Long,val expiresAt:Long,val minEngine:Int=1,val revokedVersions:List<Long> = emptyList(),
 val artifacts:List<ThreatArtifact>)

/** The model has no input to the authorization decision. */
object DecisionPolicy {
 fun canAutomaticallySuppress():Boolean=false
 fun allowedActions(finding:Finding):Set<String> = setOf("explain","dismiss_finding")
}

interface RuleEngine { fun scan(bytes:ByteArray):List<Evidence> }

/**
 * K-anonymous SHA-256 reputation contract. The client sends only [prefix]; the service returns
 * signed suffix candidates. A full file hash, package name, filename, or device identifier never
 * enters the request. Reputation is deterministic detector evidence and is never model-owned.
 */
@Serializable data class ReputationEntry(
 val suffix:String,
 val indicatorId:String,
 val source:String,
 val license:String
)

@Serializable data class ReputationPayload(
 val schema:Int=1,
 val prefix:String,
 val issuedAt:Long,
 val expiresAt:Long,
 val entries:List<ReputationEntry>
)

sealed interface ReputationResult {
 data class Match(val entry:ReputationEntry):ReputationResult
 data object NoMatch:ReputationResult
 data class Unavailable(val reason:String):ReputationResult
}

interface HashReputation {
 suspend fun lookup(sha256:String):ReputationResult
}
