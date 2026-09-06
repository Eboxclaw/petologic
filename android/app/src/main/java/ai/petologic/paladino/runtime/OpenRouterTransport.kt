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
 private val endpoint:String="https://openrouter.ai/api/v1/chat/completions"
) {
 suspend fun generate(envelope:ContextEnvelope,model:String,key:String,consent:CloudConsent,onText:(String)->Unit):String=withContext(Dispatchers.IO) {
  require(consent.permits(envelope,model,Instant.now())){"Cloud consent expired or the request changed."}
  require(envelope.sources.none{it.sensitivity==Sensitivity.LOCAL_ONLY}){"Private memory cannot leave this device."}
  require(model.matches(Regex("[A-Za-z0-9._:-]+/[A-Za-z0-9._:/-]+"))){"Enter a provider/model identifier."}
  val payload=buildJsonObject {
   put("model",model);put("stream",true);put("max_tokens",512);put("temperature",0.2)
   putJsonObject("provider"){put("allow_fallbacks",false)}
   putJsonArray("messages"){
    addJsonObject{put("role","system");put("content",envelope.system)}
    addJsonObject{put("role","user");put("content",envelope.user)}
   }
  }
  val call=client.newCall(Request.Builder().url(endpoint).header("Authorization","Bearer $key").post(payload.toString().toRequestBody("application/json".toMediaType())).build())
  val job=currentCoroutineContext()[Job]!!
  val watcher=CoroutineScope(Dispatchers.Default).launch{while(isActive){if(!job.isActive){call.cancel();break};delay(25)}}
  try {
   call.execute().use { response ->
    when(response.code){401,403->error("OpenRouter rejected this key. Reconnect in Settings.");402->error("Your OpenRouter account needs credits.");429->error("OpenRouter is busy. Try again later.")}
    check(response.isSuccessful){"OpenRouter request failed (${response.code})."}
    val source=checkNotNull(response.body).source();val result=StringBuilder();var done=false;var total=0
    while(!source.exhausted()){
     ensureActive();val line=source.readUtf8Line()?:break;total+=line.length
     check(total<2_000_000){"Cloud response exceeded the size limit."}
     if(!line.startsWith("data:"))continue
     val data=line.removePrefix("data:").trim()
     if(data=="[DONE]"){done=true;break}
     if(data.isBlank())continue
     val event=Json.parseToJsonElement(data).jsonObject
     check("error" !in event){"OpenRouter interrupted this response."}
     val choice=event["choices"]?.jsonArray?.firstOrNull()?.jsonObject
     val delta=choice?.get("delta")?.jsonObject
     // Tool execution is not enabled in cloud transport: text only, no side effects.
     val text=delta?.get("content")?.jsonPrimitive?.contentOrNull
     if(text!=null){result.append(text);onText(result.toString())}
    }
    check(done){"Cloud connection ended before completion. No action was performed."}
    check(result.isNotBlank()){"OpenRouter returned an empty response."}
    result.toString()
   }
  } finally {watcher.cancel()}
 }
}
