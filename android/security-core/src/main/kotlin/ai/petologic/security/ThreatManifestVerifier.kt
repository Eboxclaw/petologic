package ai.petologic.security

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.bouncycastle.math.ec.rfc8032.Ed25519

object ThreatManifestVerifier {
 const val MAX_MANIFEST_BYTES=256*1024
 const val MAX_ARTIFACTS=16
 const val MAX_ARTIFACT_BYTES=64L*1024*1024
 fun verify(payload:ByteArray,signature:ByteArray,publicKey:ByteArray,highestVersion:Long,now:Long):ThreatManifest {
  require(payload.size<=MAX_MANIFEST_BYTES&&signature.size==64){"Invalid manifest size."}
  require(publicKey.size==32&&Ed25519.verify(signature,0,publicKey,0,payload,0,payload.size)){"Invalid manifest signature."}
  val manifest=Json.decodeFromString<ThreatManifest>(payload.decodeToString(throwOnInvalidSequence=true))
  require(manifest.schema==1&&manifest.minEngine==1){"Unsupported manifest format."}
  require(manifest.channel in setOf("stable","preview")){"Unsupported update channel."}
  require(manifest.version>highestVersion){"Manifest rollback or replay rejected."}
  require(manifest.issuedAt<=now&&manifest.expiresAt>now&&manifest.expiresAt>manifest.issuedAt){"Manifest is expired or not yet valid."}
  require(manifest.artifacts.size in 1..MAX_ARTIFACTS){"Invalid artifact count."}
  require(manifest.revokedVersions.size<=128&&manifest.revokedVersions.distinct().size==manifest.revokedVersions.size){"Invalid revocation set."}
  require(manifest.revokedVersions.all{it in 1 until manifest.version}){"Invalid revoked version."}
  require(manifest.artifacts.map{it.kind to it.url}.distinct().size==manifest.artifacts.size){"Duplicate artifacts."}
  manifest.artifacts.forEach { artifact->
   require(artifact.url.startsWith("https://")){"Artifact URL must use HTTPS."}
   require(artifact.sha256.matches(Regex("[0-9a-f]{64}"))){"Invalid artifact hash."}
   require(artifact.licenseManifestSha256.matches(Regex("[0-9a-f]{64}"))){"Invalid license manifest hash."}
   require(artifact.size in 1..MAX_ARTIFACT_BYTES&&artifact.recordCount in 0..1_000_000){"Invalid artifact bounds."}
   require(artifact.sourceSetVersion.length in 1..160){"Invalid source-set version."}
  }
  return manifest
 }
}

object SecurityKnowledgePackVerifier {
 const val MAX_RECORDS=10_000
 const val MAX_EDGES=30_000
 fun verify(payload:ByteArray,expectedVersion:Long,now:Long):SecurityKnowledgePack {
  require(payload.size<=ThreatManifestVerifier.MAX_ARTIFACT_BYTES){"Knowledge pack is too large."}
  val pack=Json.decodeFromString<SecurityKnowledgePack>(payload.decodeToString(throwOnInvalidSequence=true))
  require(pack.schema==1&&pack.version==expectedVersion){"Unsupported knowledge pack."}
  require(pack.issuedAt<=now&&pack.expiresAt>now&&pack.expiresAt>pack.issuedAt){"Knowledge pack is expired or not yet valid."}
  require(pack.records.size<=MAX_RECORDS&&pack.edges.size<=MAX_EDGES){"Knowledge pack exceeds limits."}
  require(pack.records.map{it.id}.distinct().size==pack.records.size){"Duplicate knowledge record."}
  val ids=pack.records.mapTo(hashSetOf()){it.id}
  pack.records.forEach { record->
   require(record.id.length in 1..160&&record.revision>=0){"Invalid knowledge identity."}
   require(record.canonicalText.length in 1..4096&&record.source.length in 1..512&&record.sourceVersion.length in 1..160){"Invalid knowledge record."}
   require(record.validUntil>now&&record.evidenceRefs.size<=20&&record.limitations.size<=20){"Invalid knowledge validity."}
   require(record.evidenceRefs.all{it.length<=512}&&record.limitations.all{it.length<=512}){"Knowledge metadata exceeds limits."}
  }
  pack.edges.forEach { edge->
   require(edge.fromId in ids&&edge.toId in ids&&edge.relation.length in 1..80){"Invalid knowledge edge."}
  }
  return pack
 }
}

object YaraRulesVerifier {
 const val MAX_RULE_SOURCES=256
 const val MAX_SOURCE_CHARS=512*1024
 const val MAX_TOTAL_CHARS=8*1024*1024
 fun verify(payload:ByteArray,expectedVersion:Long,now:Long):YaraRulesPack {
  require(payload.size<=ThreatManifestVerifier.MAX_ARTIFACT_BYTES){"YARA rule pack is too large."}
  val pack=Json.decodeFromString<YaraRulesPack>(payload.decodeToString(throwOnInvalidSequence=true))
  require(pack.schema==1&&pack.version==expectedVersion){"Unsupported YARA rule pack."}
  require(pack.minEngine==1){"YARA rule pack needs a newer engine."}
  require(pack.issuedAt<=now&&pack.expiresAt>now&&pack.expiresAt>pack.issuedAt){"YARA rule pack is expired or not yet valid."}
  require(pack.sources.isNotEmpty()&&pack.sources.size<=MAX_RULE_SOURCES){"YARA rule pack exceeds source limits."}
  require(pack.sources.map{it.id}.distinct().size==pack.sources.size){"Duplicate YARA rule source."}
  var total=0
  pack.sources.forEach { source->
   require(source.id.length in 1..160&&source.license.length in 1..80){"Invalid YARA rule identity."}
   require(source.text.length in 1..MAX_SOURCE_CHARS){"YARA rule source exceeds limits."}
   require(!source.text.contains('\u0000')){"YARA rule source contains invalid characters."}
   require(Regex("rule\\s+[A-Za-z_][A-Za-z0-9_]*\\s*[:{]").containsMatchIn(source.text)){"YARA rule source does not declare a rule."}
   total+=source.text.length
  }
  require(total<=MAX_TOTAL_CHARS){"YARA rule pack exceeds the compiled-source budget."}
  return pack
 }
}
