package ai.petologic.paladino
import androidx.test.platform.app.InstrumentationRegistry
import ai.petologic.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

class RealModelTest {
 @Test fun exact_lfm_runs_offline_through_koog()=runBlocking {
  assumeTrue("Opt in with -Pandroid.testInstrumentationRunnerArguments.realModel=true",InstrumentationRegistry.getArguments().getString("realModel")=="true")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PaladinoApplication
  app.local.verify();assertTrue("Install pinned model before this test",app.local.ready.value)
  val context=ContextBroker("You are Paladino. Follow the user's instructions concisely.").build("Reply with exactly: Hello Paladino",ExecutionMode.TINY,emptyList())
  val answer=app.agent.run(context,ExecutionMode.TINY,""){}
  assertTrue("Expected actual model response, received: $answer",answer.contains("Hello Paladino",ignoreCase=true))
 }
}
