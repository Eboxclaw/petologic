package ai.petologic.paladino

import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.function.LeapFunction
import ai.liquid.leap.function.LeapFunctionParameter
import ai.liquid.leap.function.LeapFunctionParameterType
import ai.liquid.leap.message.MessageResponse
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Plan 16 stage 2 spike: can LEAP 0.10.9 sideload our QAD GGUFs, and can two runners stay
 * resident? Run: ./gradlew :app:connectedLeapDebugAndroidTest -PleapSpike=true …
 * after pushing the GGUFs to /data/local/tmp/petologic/. Everything is logged to logcat LEAPSPIKE.
 */
@RunWith(AndroidJUnit4::class)
class LeapSpikeTest{
 private fun arg(name:String)=InstrumentationRegistry.getArguments().getString(name)
 private fun log(msg:String)=Log.println(Log.WARN,"LEAPSPIKE",msg)

 private suspend fun collect(flow:Flow<MessageResponse>):Pair<String,String>{
  val out=StringBuilder();var stats="?"
  flow.collect{r->when(r){
   is MessageResponse.Chunk->out.append(r.text)
   is MessageResponse.ReasoningChunk->{}
   is MessageResponse.Complete->stats="tokS=${r.stats?.tokenPerSecond} prompt=${r.stats?.promptTokens} gen=${r.stats?.completionTokens} cached=${r.stats?.cachedPromptTokens} finish=${r.finishReason}"
   is MessageResponse.Error->log("GEN error: ${r.message} / ${r.throwable}")
   is MessageResponse.FunctionCalls->out.append("[FC ${r.functionCalls}]")
   else->log("GEN other: $r")
  }}
  return out.toString() to stats
 }

 @Test fun ggufSideloadAndDualRunner()=runBlocking{
  assumeTrue("run with -Pandroid.testInstrumentationRunnerArguments.leapSpike=true",arg("leapSpike")=="true")
  val dir="/data/local/tmp/petologic"
  val small=File("$dir/LFM2.5-230M-QAD-Q4_0.gguf")
  assumeTrue("push GGUFs to $dir first (adb push)",small.exists())
  val loadStart=System.currentTimeMillis()
  val smallRunner=try{LeapClient.loadModel(small.absolutePath,ModelLoadingOptions())}catch(e:Throwable){
   log("LOAD FAILED for path overload: ${e.javaClass.simpleName}: ${e.message}")
   throw e
  }
  log("LOAD cold ${small.name}: ${System.currentTimeMillis()-loadStart} ms modelId=${smallRunner.modelId} peakPssKb=${Debug.getPss()}")
  val conv=smallRunner.createConversation("You are Paladino, a concise assistant.")
  val t1=System.currentTimeMillis()
  val (first,stats1)=withTimeout(120_000){collect(conv.generateResponse("Say hello in exactly five words.",GenerationOptions()))}
  log("GEN 230M first: ${System.currentTimeMillis()-t1} ms reply='$first' $stats1")
  val warmStart=System.currentTimeMillis()
  val again=LeapClient.loadModel(small.absolutePath,ModelLoadingOptions())
  log("LOAD warm(same file again): ${System.currentTimeMillis()-warmStart} ms")
  again.unload()

  val big=File("$dir/LFM2.5-350M-QAD-Q4_0.gguf")
  assumeTrue(big.exists())
  val t2=System.currentTimeMillis()
  val bigRunner=LeapClient.loadModel(big.absolutePath,ModelLoadingOptions())
  log("LOAD 350M with 230M resident: ${System.currentTimeMillis()-t2} ms peakPssKb=${Debug.getPss()}")
  val bigConv=bigRunner.createConversation("You are Paladino, a concise assistant.")
  val (r1,s1)=withTimeout(120_000){collect(bigConv.generateResponse("Name three colors, comma separated.",GenerationOptions()))}
  log("GEN 350M: '$r1' $s1")
  val (r2,s2)=withTimeout(120_000){collect(conv.generateResponse("Name two animals, comma separated.",GenerationOptions()))}
  log("GEN 230M again: '$r2' $s2")
  val conc=runCatching{coroutineScope{
   val a=async{withTimeout(120_000){collect(bigConv.generateResponse("Count: one, two,",GenerationOptions()))}}
   val b=async{withTimeout(120_000){collect(conv.generateResponse("Count: A, B,",GenerationOptions()))}}
   a.await() to b.await()
  }}
  log("CONC concurrent dual-runner: ok=${conc.isSuccess} err=${conc.exceptionOrNull()?.message}")

  conv.registerFunction(LeapFunction("notes_search","Search the user's private notes.",listOf(LeapFunctionParameter("argument",LeapFunctionParameterType.LeapStr(),"concise search query",false))))
  val (fcRaw,fcStats)=withTimeout(120_000){collect(conv.generateResponse("Search my notes for bike repair.",GenerationOptions()))}
  log("FC raw='$fcRaw' $fcStats")

  val u=System.currentTimeMillis()
  bigRunner.unload();smallRunner.unload()
  log("LOAD unload both: ${System.currentTimeMillis()-u} ms endPssKb=${Debug.getPss()}")
  assertTrue(first.isNotBlank())
 }
}
