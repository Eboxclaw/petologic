package ai.petologic.paladino

import ai.petologic.core.SessionOptions
import ai.petologic.paladino.inference.LocalGenerationRequest
import ai.petologic.paladino.inference.llama.LlamaCppBackend
import ai.petologic.paladino.inference.leap.LeapBackend
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 16 stage 3: llama.cpp and LEAP linked in the SAME process, alternating real generations.
 * An upstream LEAP issue reports symbol collisions when two llama.cpp-based runtimes coexist on
 * iOS; this test checks whether Android suffers the same before any dual-engine shipping.
 */
@RunWith(AndroidJUnit4::class)
class EngineCollisionTest{
 @Test fun llama_and_leap_coexist_in_one_process()=runBlocking{
  val args=InstrumentationRegistry.getArguments()
  assumeTrue("opt in with -Pandroid.testInstrumentationRunnerArguments.collision=true",args.getString("collision")=="true")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PaladinoApplication
  app.local.verify()
  assertTrue("pinned 350M must be installed",app.local.ready.value)
  val llama=LlamaCppBackend(app.local)
  val leap=LeapBackend(app.modelLibrary)
  val options=SessionOptions(contextTokens=2048,outputTokens=32)
  repeat(10){cycle->
   val a=llama.generate(LocalGenerationRequest("You are concise.","Cycle $cycle: reply with the single word LLAMA.","lfm350",options)){}
   val b=leap.generate(LocalGenerationRequest("You are concise.","Cycle $cycle: reply with the single word LEAP.","lfm350",options)){}
   Log.println(Log.WARN,"COLLISION","cycle $cycle llama='${a.trim().take(30)}' leap='${b.trim().take(30)}' pssKb=${Debug.getPss()}")
   assertTrue(a.isNotBlank());assertTrue(b.isNotBlank())
  }
  val c=leap.generate(LocalGenerationRequest("You are concise.","Reply with the single word SMALL.","lfm230-qad",options)){}
  Log.println(Log.WARN,"COLLISION","leap 230M while llama 350M spec loaded: '${c.trim().take(30)}' pssKb=${Debug.getPss()}")
  assertTrue(c.isNotBlank())
 }
}
