package ai.petologic.skills.security.guard

import android.content.Context
import android.util.Base64
import ai.petologic.security.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest

sealed class ThreatUpdateResult {
 data class Updated(val version:Long,val artifacts:Int):ThreatUpdateResult()
 data class Unchanged(val version:Long):ThreatUpdateResult()
 data object NotConfigured:ThreatUpdateResult()
}

class ThreatUpdateClient(private val context:Context,private val repository:GuardRepository,
 private val client:OkHttpClient=OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(45)).followSslRedirects(false).build()) {
 private val prefs=context.getSharedPreferences("security_updates",Context.MODE_PRIVATE)

 suspend fun check(manifestUrl:String):ThreatUpdateResult {
 if(manifestUrl.isBlank())return ThreatUpdateResult.NotConfigured
  require(manifestUrl.startsWith("https://")||isLocalTest(manifestUrl)){"Threat manifest must use HTTPS."}
  val now=System.currentTimeMillis()
  val request=Request.Builder().url(manifestUrl).header("Accept","application/json")
   .apply{if(repository.packs.currentManifestExpiry()>now)prefs.getString("etag",null)?.let{header("If-None-Match",it)}}.build()
  val response=client.newCall(request).execute()
  response.use {
   require(it.request.url.isHttps||isLocalTest(it.request.url.toString())){"Threat manifest redirect left HTTPS."}
   if(it.code==304){
    val current=repository.packs.currentManifestVersion()
    if(current<=0||repository.packs.currentManifestExpiry()<=now)throw IOException("Cached threat manifest is missing or expired.")
    return ThreatUpdateResult.Unchanged(current)
   }
   if(!it.isSuccessful)throw IOException("Threat update HTTP ${it.code}.")
   val bytes=it.body.byteStream().readAtMost(512*1024+1);require(bytes.size<=512*1024){"Manifest envelope too large."}
   val signed=Json.decodeFromString<SignedPayload>(bytes.decodeToString())
   val payload=Base64.decode(signed.payload,Base64.NO_WRAP);val signature=Base64.decode(signed.signature,Base64.NO_WRAP)
   val current=repository.packs.currentManifestVersion()
   val manifest=ThreatManifestVerifier.verify(payload,signature,repository.packs.root(),current-1,now)
   if(manifest.version==current){
    val stored=repository.packs.manifestBytes()
    require(stored==null||stored.contentEquals(bytes)){"Manifest content changed without a version change."}
    if(stored==null)repository.packs.noteManifest(bytes,manifest.version,manifest.expiresAt)
    it.header("ETag")?.let{tag->prefs.edit().putString("etag",tag).apply()};return ThreatUpdateResult.Unchanged(current)
   }
   repository.packs.noteRevocations(manifest.revokedVersions)
   repository.threatMemory.revoke(manifest.revokedVersions)
   val staged=manifest.artifacts.associateWith{download(it)}
   // Validate every artifact before changing active state.
   staged.forEach{(artifact,data)->validateArtifact(manifest,artifact,data)}
   staged.forEach{(artifact,data)->activate(manifest,artifact,data)}
   repository.packs.noteManifest(bytes,manifest.version,manifest.expiresAt)
   it.header("ETag")?.let{tag->prefs.edit().putString("etag",tag).apply()}
   prefs.edit().putLong("last_success",System.currentTimeMillis()).putString("last_error","").apply()
   return ThreatUpdateResult.Updated(manifest.version,staged.size)
  }
 }

 private fun download(artifact:ThreatArtifact):ByteArray {
  val response=client.newCall(Request.Builder().url(artifact.url).build()).execute()
  response.use {
   require(it.request.url.isHttps){"Artifact redirect left HTTPS."}
   if(!it.isSuccessful)throw IOException("Artifact ${artifact.kind} HTTP ${it.code}.")
   val bytes=it.body.byteStream().readAtMost((artifact.size+1).toInt())
   require(bytes.size.toLong()==artifact.size){"Artifact size mismatch."}
   require(sha256(bytes)==artifact.sha256){"Artifact hash mismatch."}
   return bytes
  }
 }
 private fun validateArtifact(manifest:ThreatManifest,artifact:ThreatArtifact,bytes:ByteArray){
  when(artifact.kind){
   ThreatArtifactKind.HASH_SET->{
    require(repository.packs.validate(bytes).version==manifest.version){"Hash pack version does not match manifest."}
   }
   ThreatArtifactKind.KNOWLEDGE->SecurityKnowledgePackVerifier.verify(bytes,manifest.version,System.currentTimeMillis())
   else->require(!artifact.required){"Required artifact ${artifact.kind} is not supported by this engine."}
  }
 }
 private suspend fun activate(manifest:ThreatManifest,artifact:ThreatArtifact,bytes:ByteArray){
  when(artifact.kind){
   ThreatArtifactKind.HASH_SET->repository.packs.install(bytes)
   ThreatArtifactKind.KNOWLEDGE->repository.threatMemory.install(SecurityKnowledgePackVerifier.verify(bytes,manifest.version,System.currentTimeMillis()))
   else->Unit
  }
 }
 fun recordFailure(message:String){prefs.edit().putString("last_error",message.take(300)).putLong("last_failure",System.currentTimeMillis()).apply()}
 private fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
 private fun isLocalTest(url:String)=context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0&&url.startsWith("http://127.0.0.1")
}
