package ai.petologic.paladino.runtime

import ai.petologic.core.DecisionOutcome
import ai.petologic.core.DecisionSuggestion
import org.junit.Test
import org.junit.Assert.*

class LfmDecisionBackendTest {
 @Test fun strong_yes_offers_maxx_with_calibrated_confidence(){
  val outcome=LfmDecisionBackend.interpret(floatArrayOf(0.83f,0.17f))!!
  assertEquals(DecisionSuggestion.OFFER_MAXX,outcome.suggestion)
  assertEquals(0.83f,outcome.confidence,1e-6f)
 }
 @Test fun uncertain_and_negative_answers_abstain(){
  assertEquals(DecisionSuggestion.NONE,LfmDecisionBackend.interpret(floatArrayOf(0.5f,0.5f))!!.suggestion)
  assertEquals(DecisionSuggestion.NONE,LfmDecisionBackend.interpret(floatArrayOf(0.2f,0.8f))!!.suggestion)
 }
 @Test fun malformed_scores_return_null(){
  assertNull(LfmDecisionBackend.interpret(floatArrayOf(0.9f)))
  assertNull(LfmDecisionBackend.interpret(floatArrayOf(Float.NaN,0.1f)))
  assertNull(LfmDecisionBackend.interpret(floatArrayOf()))
 }
 @Test fun decision_router_minimum_confidence_discards_weak_offers(){
  val router=ai.petologic.core.DecisionRouter(object:ai.petologic.core.DecisionBackend{
   override fun suggest(input:String)=LfmDecisionBackend.interpret(floatArrayOf(0.61f,0.39f))
  })
  val (_,outcome)=router.decide("A complex request",ai.petologic.core.ExecutionMode.TINY)
  assertEquals(DecisionOutcome.NONE,outcome)
 }
}
