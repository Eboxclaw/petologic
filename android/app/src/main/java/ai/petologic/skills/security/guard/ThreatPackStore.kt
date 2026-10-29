package ai.petologic.skills.security.guard

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Base64
import ai.petologic.security.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

@Serializable data class SignedPayload(val payload:String,val signature:String)
/** The trust root is bundled separately; a downloaded pack cannot nominate its own key. */
class ThreatPackStore(private val context:Context){
 private val file=AtomicFile(File(context.noBackupFilesDir,"security-signed-pack.json"))
 private val manifestFile=AtomicFile(File(context.noBackupFilesDir,"security-signed-manifest.json"))
 private val prefs=context.getSharedPreferences("security_pack",Context.MODE_PRIVATE)
 internal fun root()=context.assets.open("security/trust-root.bin").use{it.readBytes()}
 private fun readBytes()=if(file.baseFile.exists())file.openRead().use{it.readAtMost(3*1024*1024+1)}
  else context.assets.open("security/bundled-pack.json").use{it.readAtMost(3*1024*1024+1)}
 internal fun signedPackBytes()=readBytes()
 private fun verify(bytes:ByteArray,floor:Long,now:Long):ThreatPack {
  require(bytes.size<=3*1024*1024){"Pack too large."}
  val signed=Json.decodeFromString<SignedPayload>(bytes.decodeToString())
  return ThreatPackVerifier.verify(Base64.decode(signed.payload,Base64.NO_WRAP),Base64.decode(signed.signature,Base64.NO_WRAP),root(),floor,now)
 }
 private fun revoked()=prefs.getString("revoked_versions","").orEmpty().split(',').mapNotNull{it.toLongOrNull()}.toSet()
 @Synchronized fun current():ThreatPack?=runCatching{verify(readBytes(),prefs.getLong("version",0)-1,System.currentTimeMillis())}.getOrNull()?.takeIf{it.version !in revoked()}
 @Synchronized fun install(uri:Uri):ThreatPack {
  require(uri.scheme=="content")
  val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use{it.readAtMost(3*1024*1024+1)}
  return install(bytes)
 }
 @Synchronized fun install(bytes:ByteArray):ThreatPack {
  current()?.takeIf{bytes.contentEquals(readBytes())}?.let{return it}
  // Even an expired installed pack retains its rollback floor; verify signature at its issue time.
  val prior=runCatching{
   val signed=Json.decodeFromString<SignedPayload>(readBytes().decodeToString())
   val payload=Base64.decode(signed.payload,Base64.NO_WRAP)
   val metadata=Json.decodeFromString<ThreatPack>(payload.decodeToString())
   verify(readBytes(),-1,metadata.issuedAt)
  }.getOrNull()
  val floor=maxOf(prefs.getLong("version",0),prior?.version?:0)
  val pack=verify(bytes,floor,System.currentTimeMillis())
  var out:java.io.FileOutputStream?=null
  try{out=file.startWrite();out.write(bytes);file.finishWrite(out)}catch(e:Exception){out?.let{file.failWrite(it)};throw e}
  check(prefs.edit().putLong("version",pack.version).commit()){"Pack installed; version checkpoint will recover from signed metadata."}
  return pack
 }
 @Synchronized fun validate(bytes:ByteArray):ThreatPack {
  current()?.takeIf{bytes.contentEquals(readBytes())}?.let{return it}
  val floor=maxOf(prefs.getLong("version",0),current()?.version?:0)
  return verify(bytes,floor,System.currentTimeMillis())
 }
 fun currentManifestVersion()=prefs.getLong("manifest_version",0)
 fun currentManifestExpiry()=prefs.getLong("manifest_expires",0)
 internal fun manifestBytes():ByteArray?=manifestFile.baseFile.takeIf{it.exists()}?.inputStream()?.use{it.readAtMost(512*1024+1)}
 fun noteManifest(bytes:ByteArray,version:Long,expiresAt:Long){
  val current=currentManifestVersion();require(version>=current){"Manifest rollback rejected."}
  require(bytes.size<=512*1024){"Manifest envelope too large."}
  var out:java.io.FileOutputStream?=null
  try{out=manifestFile.startWrite();out.write(bytes);manifestFile.finishWrite(out)}catch(e:Exception){out?.let{manifestFile.failWrite(it)};throw e}
  check(prefs.edit().putLong("manifest_version",version).putLong("manifest_expires",expiresAt).commit()){"Manifest checkpoint failed."}
 }
 fun noteRevocations(versions:List<Long>){
  val all=(revoked()+versions).sorted()
  check(prefs.edit().putString("revoked_versions",all.joinToString(",")).commit()){"Revocation checkpoint failed."}
 }
 fun snapshot():File?=current()?.let{pack->File.createTempFile("security-pack-",".json",context.cacheDir).apply{writeText(Json.encodeToString(pack))}}
 fun manifestSnapshot():File?=manifestFile.baseFile.takeIf{it.exists()}?.let{stored->
  File.createTempFile("security-manifest-",".json",context.cacheDir).apply{stored.inputStream().use{input->outputStream().use{output->input.copyTo(output)}}}
 }
}
