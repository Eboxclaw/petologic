package ai.petologic.security

import org.bouncycastle.math.ec.rfc8032.Ed25519
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/** Signature covers exact bytes. The caller supplies a separately trusted, bundled public key. */
object ThreatPackVerifier {
 const val MAX_PACK_BYTES=2*1024*1024
 fun verify(payload:ByteArray,signature:ByteArray,publicKey:ByteArray,highestVersion:Long,now:Long):ThreatPack {
  require(payload.size<=MAX_PACK_BYTES && signature.size==64){"Invalid pack size."}
  // Raw 32-byte public key avoids Android-version-specific JCA providers.
  require(publicKey.size==32 && Ed25519.verify(signature,0,publicKey,0,payload,0,payload.size)){"Invalid pack signature."}
  val pack=Json.decodeFromString<ThreatPack>(payload.decodeToString(throwOnInvalidSequence=true))
  require(pack.schema==1 && pack.minEngine==1){"Unsupported pack format."}
  require(pack.version>highestVersion){"Pack rollback or replay rejected."}
  require(pack.issuedAt<=now && pack.expiresAt>now && pack.expiresAt>pack.issuedAt){"Pack is expired or not yet valid."}
  require(pack.indicators.size<=10000 && pack.indicators.map{it.sha256}.distinct().size==pack.indicators.size){"Invalid indicator set."}
  require(pack.indicators.all{it.id.length<=160&&it.source.length<=512&&it.license.length<=80}){"Indicator metadata exceeds limits."}
  return pack
 }
}
