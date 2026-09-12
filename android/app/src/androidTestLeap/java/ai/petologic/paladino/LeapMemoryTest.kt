package ai.petologic.paladino

import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.message.MessageResponse
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Plan 16 review: honest memory accounting for the PRODUCTION runner shape per the user's rule —
 * every model loaded ONCE at 8192 context (the max one-shot context all nano models handle),
 * both resident: 350M@8192 + 230M@8192. Opt in with
 * -Pandroid.testInstrumentationRunnerArguments.mem=true.
 */
@RunWith(AndroidJUnit4::class)
class LeapMemoryTest{
 private fun log(msg:String)=Log.println(Log.WARN,"LEAPMEM",msg)

 private suspend fun generate(runner:ModelRunner):Int{
  val conv=runner.createConversation("You are concise.")
  var chars=0
  conv.generateResponse("Reply with the single word: ok",GenerationOptions(maxTokens=8)).collect{r->
   if(r is MessageResponse.Chunk)chars+=r.text.length
  }
  return chars
 }

 @Test fun productionResidencyAccounting()=runBlocking{
  val args=InstrumentationRegistry.getArguments()
  assumeTrue("opt in with -Pandroid.testInstrumentationRunnerArguments.mem=true",args.getString("mem")=="true")
  val dir="/data/local/tmp/petologic"
  val m350=File("$dir/LFM2.5-350M-QAD-Q4_0.gguf");val m230=File("$dir/LFM2.5-230M-QAD-Q4_0.gguf")
  assumeTrue(m350.exists()&&m230.exists())
  val baseline=Debug.getPss();log("baseline app PSS (no model): $baseline kB")
  val main=LeapClient.loadModel(m350.absolutePath,ModelLoadingOptions(cpuThreads=4,contextSize=8192,useMmap=true))
  generate(main)
  val pss350=Debug.getPss();log("350M@8192 resident: $pss350 kB (model cost ~${pss350-baseline} kB)")
  val worker=LeapClient.loadModel(m230.absolutePath,ModelLoadingOptions(cpuThreads=4,contextSize=8192,useMmap=true))
  generate(worker)
  val pssBoth=Debug.getPss();log("BOTH resident (350M@8192 + 230M@8192): $pssBoth kB (worker cost ~${pssBoth-pss350} kB)")
  generate(main);generate(worker)
  val pssSteady=Debug.getPss();log("steady state after more generations: $pssSteady kB")
  val u=System.currentTimeMillis()
  worker.unload()
  log("worker unload: ${System.currentTimeMillis()-u} ms -> ${Debug.getPss()} kB")
  assertTrue("both models should fit well under 1.2 GB incl. app baseline",pssBoth<1_200_000)
 }
}
