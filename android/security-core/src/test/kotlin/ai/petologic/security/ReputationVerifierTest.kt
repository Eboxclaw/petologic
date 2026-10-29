package ai.petologic.security

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.math.ec.rfc8032.Ed25519
import kotlin.test.*

class ReputationVerifierTest {
 @Test fun signedPrefixResponseMatchesOnlyTheFullHash(){
  val now=1_800_000_000_000
  val hash="01234567"+"a".repeat(56)
  val payload=Json.encodeToString(ReputationPayload(prefix="01234567",issuedAt=now-1,expiresAt=now+60_000,
   entries=listOf(ReputationEntry("a".repeat(56),"safe-inert-id","reviewed-fixture","first-party")))).encodeToByteArray()
  val privateKey=ByteArray(32){it.toByte()};val publicKey=ByteArray(32);Ed25519.generatePublicKey(privateKey,0,publicKey,0)
  val signature=ByteArray(64);Ed25519.sign(privateKey,0,payload,0,payload.size,signature,0)
  val verified=ReputationVerifier.verify(payload,signature,publicKey,"01234567",now)
  assertEquals("safe-inert-id",ReputationVerifier.match(hash,verified)?.indicatorId)
  assertNull(ReputationVerifier.match("01234567"+"b".repeat(56),verified))
 }

 @Test fun rejectsWrongPrefixExpiryAndDuplicateSuffixes(){
  assertFailsWith<IllegalArgumentException>{ReputationVerifier.prefix("A".repeat(64))}
  val bad=ReputationPayload(prefix="01234567",issuedAt=10,expiresAt=20,entries=listOf(
   ReputationEntry("a".repeat(56),"one","fixture","first-party"),
   ReputationEntry("a".repeat(56),"two","fixture","first-party")))
  val payload=Json.encodeToString(bad).encodeToByteArray()
  val privateKey=ByteArray(32){(it+1).toByte()};val publicKey=ByteArray(32);Ed25519.generatePublicKey(privateKey,0,publicKey,0)
  val signature=ByteArray(64);Ed25519.sign(privateKey,0,payload,0,payload.size,signature,0)
  assertFailsWith<IllegalArgumentException>{ReputationVerifier.verify(payload,signature,publicKey,"89abcdef",15)}
  assertFailsWith<IllegalArgumentException>{ReputationVerifier.verify(payload,signature,publicKey,"01234567",25)}
  assertFailsWith<IllegalArgumentException>{ReputationVerifier.verify(payload,signature,publicKey,"01234567",15)}
 }
}
