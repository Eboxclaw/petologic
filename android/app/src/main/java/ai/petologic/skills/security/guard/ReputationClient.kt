package ai.petologic.skills.security.guard

import ai.petologic.paladino.BuildConfig
import ai.petologic.security.*
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Optional k-anonymous reputation. Requests contain one 8-hex hash prefix and no app metadata. */
class ReputationClient(
 private val context:Context,
 private val endpoint:String=BuildConfig.REPUTATION_URL,
 private val http:OkHttpClient=OkHttpClient.Builder().callTimeout(Duration.ofSeconds(12)).followRedirects(false).build()
):HashReputation {
 private data class Cached(val payload:ReputationPayload,val expiresAt:Long)
 private val cache=ConcurrentHashMap<String,Cached>()
 @Volatile private var unavailableUntil=0L

 override suspend fun lookup(sha256:String):ReputationResult=withContext(Dispatchers.IO){
  val prefix=ReputationVerifier.prefix(sha256)
  if(endpoint.isBlank())return@withContext ReputationResult.Unavailable("Private reputation is not configured.")
  if(!endpoint.startsWith("https://")&&!isLocalTest(endpoint))return@withContext ReputationResult.Unavailable("Private reputation endpoint is invalid.")
  val now=System.currentTimeMillis()
  if(now<unavailableUntil)return@withContext ReputationResult.Unavailable("Private reputation is temporarily unavailable.")
  val response=cache[prefix]?.takeIf{it.expiresAt>now}?.payload?:runCatching{fetch(prefix,now)}
   .getOrElse{unavailableUntil=now+5*60_000;return@withContext ReputationResult.Unavailable("Private reputation could not be reached.")}
  ReputationVerifier.match(sha256,response)?.let{ReputationResult.Match(it)}?:ReputationResult.NoMatch
 }

 private fun fetch(prefix:String,now:Long):ReputationPayload {
  val base=endpoint.trimEnd('/')
  val request=Request.Builder().url("$base/v1/sha256/$prefix").header("Accept","application/json")
   .header("Cache-Control","no-store").build()
  http.newCall(request).execute().use { response->
   require(response.code==200){"Reputation HTTP ${response.code}."}
   require(response.request.url.isHttps||isLocalTest(response.request.url.toString())){"Reputation redirect left HTTPS."}
   val envelopeBytes=response.body.byteStream().readAtMost(ReputationVerifier.MAX_RESPONSE_BYTES+1)
   require(envelopeBytes.size<=ReputationVerifier.MAX_RESPONSE_BYTES){"Reputation response is too large."}
   val envelope=Json.decodeFromString<SignedPayload>(envelopeBytes.decodeToString())
   val payload=Base64.getDecoder().decode(envelope.payload);val signature=Base64.getDecoder().decode(envelope.signature)
   val verified=ReputationVerifier.verify(payload,signature,ThreatPackStore(context).root(),prefix,now)
   cache[prefix]=Cached(verified,verified.expiresAt);return verified
  }
 }

 private fun isLocalTest(url:String)=context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0&&url.startsWith("http://127.0.0.1")
}
