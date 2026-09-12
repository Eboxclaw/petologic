package ai.petologic.paladino

import ai.petologic.core.SessionOptions
import ai.petologic.paladino.inference.LocalGenerationRequest
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 16 stage 4: identical runtime benchmark across engines (the code runs unmodified on the
 * llama and leap flavors; BuildConfig.FLAVOR names the engine in the output). Liquid's
 * hardware-evaluation methodology: separate TTFT/prefill/decode, prompt lengths 256/512/1024,
 * contexts 2048/4096/8192, memory sampled throughout. Opt in with
 * -Pandroid.testInstrumentationRunnerArguments.bench=true after models are installed.
 * Results land in logcat BENCH and files/bench-<engine>.json.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeBenchmarkTest{
 private fun log(msg:String){Log.println(Log.WARN,"BENCH",msg)}

 private suspend fun sample(
  backend:ai.petologic.paladino.inference.LocalInferenceBackend,
  modelId:String,contextTokens:Int,promptChars:Int,maxTokens:Int,label:String
 ):JSONObject{
  val options=SessionOptions(contextTokens=contextTokens,outputTokens=maxTokens,toolReserve=512,
   hopTimeoutSeconds=180,totalTimeoutSeconds=600)
  val prompt=buildString{
   append("Read the notes below, then follow the final instruction.\n")
   val line="Note: the quest log mentions a lantern, a bridge and a quiet river. "
   while(length<promptChars)append(line)
   append("\nFinal instruction: continue this story in exactly $maxTokens short words: Once upon a time")
  }
  val wall0=System.nanoTime();var ttft=0L
  val text=withTimeout(600_000L){
   backend.generate(LocalGenerationRequest("You are a precise benchmark assistant.",prompt,modelId,options)){if(ttft==0L)ttft=(System.nanoTime()-wall0)/1_000_000}
  }
  val wallMs=(System.nanoTime()-wall0)/1_000_000
  val words=text.trim().split(Regex("\\s+")).size
  return JSONObject().put("label",label).put("model",modelId).put("context",contextTokens)
   .put("promptChars",promptChars).put("wallMs",wallMs).put("ttftMs",ttft)
   .put("replyWords",words).put("peakPssKb",Debug.getPss())
 }

 @Test fun runtimeBenchmark()=runBlocking{
  val args=InstrumentationRegistry.getArguments()
  assumeTrue("opt in with -Pandroid.testInstrumentationRunnerArguments.bench=true",args.getString("bench")=="true")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PaladinoApplication
  app.local.verify()
  val backend=app.inference
  val engine=BuildConfig.FLAVOR
  val results=JSONArray()
  fun record(o:JSONObject){results.put(o);log(o.toString())}

  val coldStart=System.currentTimeMillis()
  record(sample(backend,"lfm350",4096,400,48,"cold_load_350M_4096"))
  val coldMs=System.currentTimeMillis()-coldStart
  record(JSONObject().put("label","cold_wall_total").put("model","lfm350").put("wallMs",coldMs).put("peakPssKb",Debug.getPss()))
  repeat(3){record(sample(backend,"lfm350",4096,400,100,"warm_$it"))}
  listOf(900,1800,3600).forEach{chars->record(sample(backend,"lfm350",4096,chars,100,"prompt_${chars}c"))}
  listOf(2048,8192).forEach{ctx->record(sample(backend,"lfm350",ctx,400,64,"context_$ctx"))}
  val t=System.currentTimeMillis()
  record(sample(backend,"lfm230-qad",2048,400,64,"switch_to_230M"))
  record(JSONObject().put("label","switch_350_to_230").put("wallMs",System.currentTimeMillis()-t).put("peakPssKb",Debug.getPss()))
  val t2=System.currentTimeMillis()
  record(sample(backend,"lfm350",4096,400,64,"switch_back_350M"))
  record(JSONObject().put("label","switch_230_to_350").put("wallMs",System.currentTimeMillis()-t2).put("peakPssKb",Debug.getPss()))
  val consec=System.currentTimeMillis()
  repeat(10){record(sample(backend,"lfm350",4096,200,48,"consecutive_$it"))}
  record(JSONObject().put("label","consecutive_10_total").put("wallMs",System.currentTimeMillis()-consec).put("peakPssKb",Debug.getPss()))

  // cancellation: abort mid-generation and time how long stopping takes
  val cancelSample=async{
   backend.generate(LocalGenerationRequest("You are verbose.","Write a very long story about the sea.","lfm350",
    SessionOptions(contextTokens=4096,outputTokens=512,hopTimeoutSeconds=180,totalTimeoutSeconds=600))){}
  }
  delay(2500)
  val tCancel=System.currentTimeMillis()
  cancelSample.cancel()
  val stopped=runCatching{cancelSample.await()}
  record(JSONObject().put("label","cancellation").put("wallMs",System.currentTimeMillis()-tCancel)
   .put("clean",stopped.exceptionOrNull()?.let{it is CancellationException}==true).put("peakPssKb",Debug.getPss()))

  val dir=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)?:return@runBlocking
  java.io.File(dir,"bench-$engine-${System.currentTimeMillis()}.json").writeText(results.toString(1))
  log("engine=$engine device=${Build.MODEL} results=${results.length()} entries")
 }
}
