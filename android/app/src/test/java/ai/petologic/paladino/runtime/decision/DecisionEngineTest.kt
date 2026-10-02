package ai.petologic.paladino.runtime.decision

import ai.petologic.core.ExecutionMode
import ai.petologic.core.SessionOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {
 private val models=setOf("lfm350","lfm230-qad")

 @Test fun greeting_uses_230m()=runBlocking{
  val decision=DecisionEngine().decide(DecisionInput("Olá, tudo bem?",ExecutionMode.TINY,SessionOptions(),models))
  assertEquals(DecisionTarget.CHAT_230M,decision.target)
  assertEquals("lfm230-qad",decision.modelId)
  assertFalse(decision.needsTool)
 }

 @Test fun explicit_tool_intent_uses_350m()=runBlocking{
  val decision=DecisionEngine().decide(DecisionInput("Procura as minhas notas sobre AVAC",ExecutionMode.TINY,SessionOptions(),models))
  assertEquals(DecisionTarget.ROUTER_350M,decision.target)
  assertEquals("lfm350",decision.modelId)
  assertTrue(decision.needsTool)
 }

 @Test fun maxx_stays_cloud()=runBlocking{
  val decision=DecisionEngine().decide(DecisionInput("Explica isto",ExecutionMode.MAXX,SessionOptions(),models))
  assertEquals(DecisionTarget.CLOUD,decision.target)
 }
}
