package ai.petologic.paladino.runtime.decision

/**
 * Decision cascade:
 * 1. deterministic System 0 for obvious cases;
 * 2. optional d1 for ambiguous bounded routing;
 * 3. local 350M fallback.
 *
 * d1 is advisory only. Tool permissions, approval policy and capability
 * validation remain authoritative elsewhere in the app.
 */
class DecisionEngine(
 private val system0:DecisionProvider=LocalDecisionProvider(),
 private val d1:DecisionProvider?=null
){
 suspend fun decide(input:DecisionInput):RouteDecision {
  val first=system0.decide(input)
  if(first!=null && first.source==DecisionSource.SYSTEM0)return first

  val remote=d1?.decide(input)
  if(remote!=null && remote.confidence>=D1_MIN_CONFIDENCE)return remote

  return first?:RouteDecision(
   DecisionTarget.ROUTER_350M,
   input.installedModels.firstOrNull{it=="lfm350"}?:input.installedModels.firstOrNull{it=="lfm230-qad"},
   0.0,
   false,
   DecisionSource.LOCAL_FALLBACK,
   "No decision provider returned a usable route."
  )
 }

 companion object {
  const val D1_MIN_CONFIDENCE=0.65
 }
}
