package ai.petologic.security

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.bouncycastle.math.ec.rfc8032.Ed25519

object ReputationVerifier {
 const val PREFIX_HEX_LENGTH=8
 const val MAX_RESPONSE_BYTES=512*1024
 const val MAX_ENTRIES=8_192

 fun prefix(sha256:String):String {
  require(Regex("[0-9a-f]{64}").matches(sha256)){"SHA-256 must be lowercase hexadecimal."}
  return sha256.take(PREFIX_HEX_LENGTH)
 }

 fun verify(
  payload:ByteArray,
  signature:ByteArray,
  publicKey:ByteArray,
  requestedPrefix:String,
  now:Long
 ):ReputationPayload {
  require(payload.size<=MAX_RESPONSE_BYTES&&signature.size==64){"Invalid reputation response size."}
  require(publicKey.size==32&&Ed25519.verify(signature,0,publicKey,0,payload,0,payload.size)){"Invalid reputation signature."}
  val response=Json.decodeFromString<ReputationPayload>(payload.decodeToString(throwOnInvalidSequence=true))
  require(response.schema==1){"Unsupported reputation response."}
  require(requestedPrefix.matches(Regex("[0-9a-f]{8}"))&&response.prefix==requestedPrefix){"Reputation prefix mismatch."}
  require(response.issuedAt<=now&&response.expiresAt>now&&response.expiresAt>response.issuedAt){"Reputation response is expired or not yet valid."}
  require(response.entries.size<=MAX_ENTRIES){"Reputation response exceeds entry limit."}
  val suffixLength=64-PREFIX_HEX_LENGTH
  response.entries.forEach { entry->
   require(entry.suffix.matches(Regex("[0-9a-f]{$suffixLength}"))){"Invalid reputation suffix."}
   require(entry.indicatorId.length in 1..160&&entry.source.length in 1..512&&entry.license.length in 1..80){"Invalid reputation metadata."}
  }
  require(response.entries.map{it.suffix}.distinct().size==response.entries.size){"Duplicate reputation suffix."}
  return response
 }

 fun match(sha256:String,response:ReputationPayload):ReputationEntry? {
  val requestedPrefix=prefix(sha256)
  require(response.prefix==requestedPrefix){"Reputation prefix mismatch."}
  return response.entries.firstOrNull{requestedPrefix+it.suffix==sha256}
 }
}
