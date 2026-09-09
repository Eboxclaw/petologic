package ai.petologic.paladino.runtime

import ai.petologic.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit

/** No fallback origins, embedded keys, model-selected URLs or hidden retries. */
class OpenRouterTransport(
 private val client:OkHttpClient=OkHttpClient.Builder().callTimeout(60,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
 private val endpoint:String?=null
) {
 suspend fun generate(envelope:ContextEnvelope,model:String,key:String,consent:CloudConsent,provider:CloudProvider=CloudProvider.OPENROUTER,onText:(String)->Unit):String=withContext(Dispatchers.IO) {
  require(consent.permits(envelope,model,Instant.now(),provider.id)){"Cloud consent expired or the request changed."}
  require(envelope.sources.none{it.sensitivity==Sensitivity.LOCAL_ONLY}){"Private memory cannot leave this device."}
  require(provider.validModel(model)){"Enter a valid ${provider.label} model identifier."}
  val payload=buildJsonObject {
   put("model",model);put("stream",true);put(if(provider==CloudProvider.OPENAI)"max_completion_tokens" else "max_tokens",512)
   if(provider==CloudProvider.OPENROUTER)putJsonObject("provider"){put("allow_fallbacks",false)}
   putJsonArray("messages"){
    addJsonObject{put("role","system");put("content",envelope.system)}
    addJsonObject{put("role","user");put("content",envelope.user)}
   }
  }
  val call=client.newCall(Request.Builder().url(endpoint?:provider.endpoint).header("Authorization","Bearer $key").post(payload.toString().toRequestBody("application/json".toMediaType())).build())
  val job=currentCoroutineContext()[Job]!!
  val watcher=CoroutineScope(Dispatchers.Default).launch{while(isActive){if(!job.isActive){call.cancel();break};delay(25)}}
  try {
   call.execute().use { response ->
    when(response.code){401,403->error("${provider.label} rejected this key. Reconnect in Settings.");402->error("Your ${provider.label} account needs credits.");429->error("${provider.label} is busy. Try again later.")}
    check(response.isSuccessful){"${provider.label} request failed (${response.code})."}
    val source=checkNotNull(response.body).source();val result=StringBuilder();var done=false;var total=0
    while(!source.exhausted()){
     ensureActive();val line=source.readUtf8Line()?:break;total+=line.length
     check(total<2_000_000){"Cloud response exceeded the size limit."}
     if(!line.startsWith("data:"))continue
     val data=line.removePrefix("data:").trim()
     if(data=="[DONE]"){done=true;break}
     if(data.isBlank())continue
     val event=Json.parseToJsonElement(data).jsonObject
     check("error" !in event){"${provider.label} interrupted this response."}
     val choice=event["choices"]?.jsonArray?.firstOrNull()?.jsonObject
     val delta=choice?.get("delta")?.jsonObject
     // Tool execution is not enabled in cloud transport: text only, no side effects.
     val text=delta?.get("content")?.jsonPrimitive?.contentOrNull
     if(text!=null){result.append(text);onText(result.toString())}
    }
    check(done){"Cloud connection ended before completion. No action was performed."}
    check(result.isNotBlank()){"${provider.label} returned an empty response."}
    result.toString()
   }
  } finally {watcher.cancel()}
 }
}
