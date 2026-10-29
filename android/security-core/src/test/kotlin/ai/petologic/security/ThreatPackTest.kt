package ai.petologic.security

import kotlin.test.*
import java.security.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
class ThreatPackTest {
 private val keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
 private val now=1000L
 private fun data(version:Long=2,expires:Long=2000)=Json.encodeToString(ThreatPack(version=version,issuedAt=500,expiresAt=expires,indicators=emptyList())).toByteArray()
 private fun sign(bytes:ByteArray)=Signature.getInstance("Ed25519").run{initSign(keys.private);update(bytes);sign()}
 @Test fun validPackAccepted(){val bytes=data();assertEquals(2,ThreatPackVerifier.verify(bytes,sign(bytes),keys.public.encoded.takeLast(32).toByteArray(),1,now).version)}
 @Test fun badSignatureRejected(){val bytes=data();assertFails{ThreatPackVerifier.verify(bytes,ByteArray(64),keys.public.encoded.takeLast(32).toByteArray(),1,now)}}
 @Test fun rollbackAndExpiryRejected(){for(bytes in listOf(data(version=1),data(expires=999))){assertFails{ThreatPackVerifier.verify(bytes,sign(bytes),keys.public.encoded.takeLast(32).toByteArray(),1,now)}}}
 @Test fun wrongTrustRootRejected(){val bytes=data();val other=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();assertFails{ThreatPackVerifier.verify(bytes,sign(bytes),other.public.encoded.takeLast(32).toByteArray(),1,now)}}
 @Test fun modelCannotAuthorizeSuppression(){assertFalse(DecisionPolicy.canAutomaticallySuppress())}
 @Test fun signedManifestEnforcesTransportBoundsAndRollback(){
  val artifact=ThreatArtifact(ThreatArtifactKind.KNOWLEDGE,"https://updates.example/knowledge.json","a".repeat(64),123,1,"daily-1","b".repeat(64))
  val bytes=Json.encodeToString(ThreatManifest(version=3,issuedAt=500,expiresAt=2000,artifacts=listOf(artifact))).toByteArray()
  val root=keys.public.encoded.takeLast(32).toByteArray()
  assertEquals(3,ThreatManifestVerifier.verify(bytes,sign(bytes),root,2,now).version)
  assertFails{ThreatManifestVerifier.verify(bytes,sign(bytes),root,3,now)}
  val insecure=Json.encodeToString(ThreatManifest(version=4,issuedAt=500,expiresAt=2000,artifacts=listOf(artifact.copy(url="http://updates.example/x")))).toByteArray()
  assertFails{ThreatManifestVerifier.verify(insecure,sign(insecure),root,3,now)}
  val selfRevoked=Json.encodeToString(ThreatManifest(version=4,issuedAt=500,expiresAt=2000,revokedVersions=listOf(4),artifacts=listOf(artifact))).toByteArray()
  assertFails{ThreatManifestVerifier.verify(selfRevoked,sign(selfRevoked),root,3,now)}
 }
 @Test fun knowledgePackRejectsDanglingEdgesAndDuplicateRecords(){
  val record=KnowledgeRecord("campaign-1",1,KnowledgeKind.CAMPAIGN,"en","Bank impersonation asks for a one-time code.","lab","1",TrustTier.REVIEWED,2000)
  val valid=Json.encodeToString(SecurityKnowledgePack(version=7,issuedAt=500,expiresAt=2000,records=listOf(record))).toByteArray()
  assertEquals(1,SecurityKnowledgePackVerifier.verify(valid,7,now).records.size)
  val duplicate=Json.encodeToString(SecurityKnowledgePack(version=7,issuedAt=500,expiresAt=2000,records=listOf(record,record))).toByteArray()
  assertFails{SecurityKnowledgePackVerifier.verify(duplicate,7,now)}
  val dangling=Json.encodeToString(SecurityKnowledgePack(version=7,issuedAt=500,expiresAt=2000,records=listOf(record),edges=listOf(ThreatEdge("campaign-1","missing","related")))).toByteArray()
  assertFails{SecurityKnowledgePackVerifier.verify(dangling,7,now)}
 }
}
