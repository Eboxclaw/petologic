package ai.petologic.core
import kotlin.test.*

class DecisionRouterTest {
 @Test fun deterministic_routes_are_unchanged_without_a_backend() {
  val router=DecisionRouter(null)
  val (route,outcome)=router.decide("Explain quantum tunnelling in depth",ExecutionMode.TINY)
  assertTrue(route is Route.Generate)
  assertEquals(DecisionOutcome.NONE,outcome)
 }
 @Test fun explicit_rules_win_over_any_suggestion() {
  val always=DecisionRouter(object:DecisionBackend{override fun suggest(input:String)=DecisionOutcome(DecisionSuggestion.OFFER_MAXX,0.99f)})
  val (save,saveOutcome)=always.decide("Remember that my bicycle is in the blue garage",ExecutionMode.TINY)
  assertTrue(save is Route.Save)
  assertEquals(DecisionOutcome.NONE,saveOutcome)
  val (search,searchOutcome)=always.decide("Find bicycle",ExecutionMode.TINY)
  assertTrue(search is Route.Search)
  assertEquals(DecisionOutcome.NONE,searchOutcome)
 }
 @Test fun maxx_never_gets_suggestions() {
  val always=DecisionRouter(object:DecisionBackend{override fun suggest(input:String)=DecisionOutcome(DecisionSuggestion.OFFER_MAXX,0.99f)})
  val (_,outcome)=always.decide("A complex request",ExecutionMode.MAXX)
  assertEquals(DecisionOutcome.NONE,outcome)
 }
 @Test fun low_confidence_suggestions_are_discarded() {
  val router=DecisionRouter(object:DecisionBackend{override fun suggest(input:String)=DecisionOutcome(DecisionSuggestion.OFFER_MAXX,0.5f)})
  val (_,outcome)=router.decide("A complex request",ExecutionMode.TINY)
  assertEquals(DecisionOutcome.NONE,outcome)
 }
 @Test fun passing_suggestions_are_kept_with_their_confidence() {
  val router=DecisionRouter(object:DecisionBackend{override fun suggest(input:String)=DecisionOutcome(DecisionSuggestion.OFFER_MAXX,0.86f)})
  val (route,outcome)=router.decide("A complex request",ExecutionMode.TINY)
  assertTrue(route is Route.Generate)
  assertEquals(DecisionSuggestion.OFFER_MAXX,outcome.suggestion)
  assertEquals(0.86f,outcome.confidence)
 }
 @Test fun backend_failures_fail_open() {
  val router=DecisionRouter(object:DecisionBackend{override fun suggest(input:String):DecisionOutcome=throw IllegalStateException("model busy")})
  val (route,outcome)=router.decide("A complex request",ExecutionMode.TINY)
  assertTrue(route is Route.Generate)
  assertEquals(DecisionOutcome.NONE,outcome)
 }
}
