package ai.petologic.paladino.runtime

import ai.petologic.core.DecisionBackend
import ai.petologic.core.DecisionOutcome
import ai.petologic.core.DecisionSuggestion
import ai.petologic.core.ModelCatalog

/**
 * d1-2B backed suggestions. Callers run it off the response path and keep it off the verdict
 * and authorization path: a suggestion can only surface as a user-visible Maxx offer.
 */
class LfmDecisionBackend(private val model:LocalModel,private val library:ModelLibrary):DecisionBackend {
 companion object{
  const val SYSTEM="You are a decision model. Answer with the single most likely word and no other text."
  private const val MAX_STATE_CHARS=1500

  /** p(yes) at or above this offers Maxx; the uncertain band abstains. Recalibrate after NEXT-02. */
  const val OFFER_THRESHOLD=0.60f
  const val ABSTAIN_BOUND=0.40f

  /** Pure decision rule, separated for unit testing without the native runtime. */
  fun interpret(probabilities:FloatArray):DecisionOutcome?{
   if(probabilities.size<2)return null
   val pYes=probabilities[0]
   if(!pYes.isFinite())return null
   if(pYes>=OFFER_THRESHOLD)return DecisionOutcome(DecisionSuggestion.OFFER_MAXX,pYes)
   return DecisionOutcome(DecisionSuggestion.NONE,pYes)
  }
 }

 override fun suggest(input:String):DecisionOutcome?{
  if(ModelCatalog.DECISION_ID !in library.installed.value)return null
  val state=if(input.length>MAX_STATE_CHARS)input.take(MAX_STATE_CHARS)+"…" else input
  val user="Request:\n$state\n\nQuestion: Would a larger cloud model give a clearly better answer to this request?\nAnswer with one word: yes or no."
  // Blocking bridge: suggest() is only ever invoked off the main thread, wrapped in Dispatchers.Default.
  val scores=runCatching{kotlinx.coroutines.runBlocking{model.decisionScores(SYSTEM,user,listOf("yes","no"))}}.getOrNull()?:return null
  return interpret(scores)
 }
}
