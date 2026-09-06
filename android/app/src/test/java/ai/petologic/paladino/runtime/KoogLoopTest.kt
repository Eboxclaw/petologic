package ai.petologic.paladino.runtime
import ai.petologic.core.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
class KoogLoopTest{
 @Test fun `koog sends tool observations back and supports another hop`()=runTest{
  val observed=mutableListOf<String>();var calls=0
  val agent=PaladinoAgent(null,null,null){_,prompt->observed+=prompt;calls++;when(calls){1->"{\"tool\":\"notes_search\",\"argument\":\"bike\"}";2->"{\"tool\":\"notes_save\",\"argument\":\"Pack a helmet\"}";else->"Your bike is in the garage. I saved the helmet note."}}
  var saved=""
  val answer=agent.run(ContextBroker("Paladino").build("Help me prepare",ExecutionMode.TINY,emptyList()),ExecutionMode.TINY,"",tools=AgentTools(search={"Bike is in garage"},save={saved=it;"Saved after user approval"})){}
  assertEquals(3,calls);assertEquals("Pack a helmet",saved);assertTrue(observed[1].contains("Bike is in garage"));assertTrue(observed[2].contains("Saved after user approval"));assertTrue(answer.contains("helmet"))
 }
 @Test fun `repeated tool cannot execute twice`()=runTest{
  var searches=0
  val agent=PaladinoAgent(null,null,null){_,_->"{\"tool\":\"notes_search\",\"argument\":\"bike\"}"}
  runCatching{agent.run(ContextBroker("Paladino").build("Find bike",ExecutionMode.TINY,emptyList()),ExecutionMode.TINY,"",tools=AgentTools(search={searches++;"garage"})){} }
  assertEquals(1,searches)
 }
 @Test fun `disabled tools cannot execute model generated instructions`()=runTest{
  var writes=0
  val agent=PaladinoAgent(null,null,null){_,_->"{\"tool\":\"notes_save\",\"argument\":\"evil\"}"}
  agent.run(ContextBroker("Paladino").build("Hi",ExecutionMode.TINY,emptyList()),ExecutionMode.TINY,"",options=SessionOptions(toolCalls=false),tools=AgentTools(save={writes++;"saved"})){}
  assertEquals(0,writes)
 }
}
