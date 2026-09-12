package ai.petologic.paladino.runtime

import ai.petologic.core.SessionOptions
import org.junit.Test
import org.junit.Assert.*

class SubAgentTest {
 @Test fun fallback_prefers_small_model_only_when_installed(){
  val small=SubAgent.PREFERRED
  assertTrue(small.startsWith("lfm"))
  assertEquals(small,SubAgent.modelId(setOf(small)))
  assertEquals(small,SubAgent.modelId(setOf(small,SubAgent.FALLBACK,"minilm")))
  assertEquals("Small missing → a turn of the main 350M",SubAgent.FALLBACK,SubAgent.modelId(setOf()))
  assertEquals(SubAgent.FALLBACK,SubAgent.modelId(setOf("minilm")))
  assertEquals(SubAgent.FALLBACK,SubAgent.modelId(setOf(SubAgent.FALLBACK)))
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
