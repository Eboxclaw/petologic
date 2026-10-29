package ai.petologic.paladino.runtime
import ai.petologic.core.*
import ai.petologic.paladino.inference.BackendCapabilities
import ai.petologic.paladino.inference.InferenceMetrics
import ai.petologic.paladino.inference.LocalGenerationRequest
import ai.petologic.paladino.inference.LocalInferenceBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/** Records every request so tests can assert what the agent layer actually handed the backend. */
private class RecordingBackend(private val native:Boolean,private val reply:(LocalGenerationRequest)->String):LocalInferenceBackend{
 override val ready=MutableStateFlow(true)
 override val metrics=MutableStateFlow<InferenceMetrics?>(null)
 override val capabilities=BackendCapabilities(streaming=true,cancellation=true,multiModel=native,nativeFunctionCalling=native)
 val requests=mutableListOf<LocalGenerationRequest>()
 override suspend fun verify(){}
 override suspend fun generate(request:LocalGenerationRequest,onText:(String)->Unit):String{requests+=request;return reply(request)}
 override suspend fun unload(){}
}

class NativeFunctionCallingTest{
 @Test fun `native backend receives tool specs without the forced json instruction`()=runTest{
  var calls=0
  val backend=RecordingBackend(true){ _ -> if(++calls==1)"""[notes_search(argument="bike")]""" else "Your bike is in the garage." }
  val answer=PaladinoAgent(backend,null,null).run(
   ContextBroker("Paladino").build("Search my notes for the bike repair shop",ExecutionMode.TINY,emptyList()),
   ExecutionMode.TINY,"",tools=AgentTools(search={"Bike is in the garage"})){}
  val request=backend.requests.first()
  assertEquals("notes_search",request.tools.single().name)
  assertTrue(request.tools.single().description.isNotEmpty())
  assertFalse("LEAP injects schemas itself",request.system.contains("Output function calls as JSON"))
  assertTrue("behavioral rules stay",request.system.contains("Wait for the tool result"))
  assertTrue("materialized native call parsed and executed",answer.contains("garage"))
 }
 @Test fun `prompt protocol backend still gets the json instruction and no tool specs`()=runTest{
  var calls=0
  val backend=RecordingBackend(false){ _ -> if(++calls==1)"""{"tool":"notes_search","argument":"bike"}""" else "Your bike is in the garage." }
  val answer=PaladinoAgent(backend,null,null).run(
   ContextBroker("Paladino").build("Search my notes for the bike repair shop",ExecutionMode.TINY,emptyList()),
   ExecutionMode.TINY,"",tools=AgentTools(search={"Bike is in the garage"})){}
  val request=backend.requests.first()
  assertTrue(request.tools.isEmpty())
  assertTrue(request.system.contains("Output function calls as JSON"))
  assertTrue(answer.contains("garage"))
 }
}
