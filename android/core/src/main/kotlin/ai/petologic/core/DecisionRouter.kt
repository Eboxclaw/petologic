package ai.petologic.core

/**
 * The d1 decision stage. It may only add one bounded suggestion on top of the deterministic
 * [RoutePolicy]; it never routes to the cloud by itself, never executes anything and never
 * touches detector findings or approvals. Cloud stays behind the explicit consent flow.
 */
enum class DecisionSuggestion { NONE, OFFER_MAXX }

data class DecisionOutcome(val suggestion: DecisionSuggestion, val confidence: Float) {
 companion object { val NONE = DecisionOutcome(DecisionSuggestion.NONE, 0f) }
}

interface DecisionBackend {
 /** Returns null when unavailable (model missing, busy, timed out, failed) so callers fail open. */
 fun suggest(input: String): DecisionOutcome?
}

class DecisionRouter(private val backend: DecisionBackend? = null) {
 companion object {
  /** Below this calibrated confidence the suggestion is discarded; thresholds move after the NEXT-02 evaluation. */
  const val MIN_CONFIDENCE = 0.70f
 }

 /** Deterministic rules own the route; the decision model may only suggest. */
 fun decide(input: String, mode: ExecutionMode): Pair<Route, DecisionOutcome> {
  val route = RoutePolicy().route(input, mode)
  if (mode != ExecutionMode.TINY || route !is Route.Generate) return route to DecisionOutcome.NONE
  val outcome = runCatching { backend?.suggest(input) }.getOrNull() ?: return route to DecisionOutcome.NONE
  if (outcome.suggestion == DecisionSuggestion.NONE || outcome.confidence < MIN_CONFIDENCE) return route to DecisionOutcome.NONE
  return route to outcome
 }
}
