package ai.petologic.paladino.runtime

import ai.petologic.core.SessionOptions
import org.junit.Test
import org.junit.Assert.*

class SubAgentTest {
 @Test fun every_speaking_turn_uses_the_main_350m(){
  assertEquals(ai.petologic.core.ModelCatalog.SPEAKER_ID,SubAgent.MODEL)
  assertEquals(SubAgent.MODEL,SubAgent.modelId(emptySet()))
  assertEquals(SubAgent.MODEL,SubAgent.modelId(setOf("lfm25-embedding-350m")))
  assertEquals(SubAgent.MODEL,SubAgent.modelId(setOf(SubAgent.MODEL)))
 }
 @Test fun worker_options_are_valid_and_tool_free(){
  val options=SubAgent.workerOptions().validate()
  assertEquals(0,options.maxToolCalls);assertEquals(1,options.maxHops)
  assertEquals(2048,options.contextTokens)
 }
 @Test fun title_from_shapes_a_short_human_title(){
  assertEquals("What's a good photo spot in Porto?",SubAgent.titleFrom("What's a good photo spot in Porto?","  "))
  val long=SubAgent.titleFrom("  I   want   a   very   very   long   explanation   about   everything   today  ","x")
  assertTrue(long.length<=SubAgent.TITLE_MAX+1&&long.endsWith("…"))
  assertEquals("",SubAgent.titleFrom("  ","  "))
 }
}
