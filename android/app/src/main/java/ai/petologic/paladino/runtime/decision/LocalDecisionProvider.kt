package ai.petologic.paladino.runtime.decision

import ai.petologic.core.ExecutionMode

/**
 * System 0 / offline fallback.
 *
 * It deliberately handles only cheap, high-precision cases. Ambiguous requests
 * stay on the 350M router rather than pretending a regex is an intelligent router.
 */
class LocalDecisionProvider:DecisionProvider {
 override suspend fun decide(input:DecisionInput):RouteDecision {
  val text=input.text.trim()
  val lower=text.lowercase()

  if(input.mode==ExecutionMode.MAXX){
   return RouteDecision(DecisionTarget.CLOUD,null,1.0,false,DecisionSource.SYSTEM0,"Maxx mode is explicitly cloud.")
  }

  val toolish=TOOL_HINTS.any{it.containsMatchIn(lower)}
  if(toolish){
   return RouteDecision(
    DecisionTarget.ROUTER_350M,
    preferred(input.installedModels,"lfm350","lfm230-qad"),
    0.98,
    true,
    DecisionSource.SYSTEM0,
    "Explicit action/tool intent."
   )
  }

  val shortChat=text.length<=180 && SIMPLE_CHAT.any{it.containsMatchIn(lower)}
  if(shortChat){
   return RouteDecision(
    DecisionTarget.CHAT_230M,
    preferred(input.installedModels,"lfm230-qad","lfm350"),
    0.96,
    false,
    DecisionSource.SYSTEM0,
    "Short conversational request."
   )
  }

  return RouteDecision(
   DecisionTarget.ROUTER_350M,
   preferred(input.installedModels,"lfm350","lfm230-qad"),
   0.55,
   false,
   DecisionSource.LOCAL_FALLBACK,
   "Ambiguous local request: prefer the stronger router."
  )
 }

 private fun preferred(installed:Set<String>,vararg ids:String)=ids.firstOrNull{it in installed}

 companion object {
  private val SIMPLE_CHAT=listOf(
   Regex("^(hi|hello|hey|olá|ola|boas|bom dia|boa tarde|boa noite)\\b",RegexOption.IGNORE_CASE),
   Regex("^(obrigad[oa]|thanks|thank you|valeu)\\b",RegexOption.IGNORE_CASE),
   Regex("^(quem és|quem es|who are you|como estás|como estas|how are you)\\b",RegexOption.IGNORE_CASE)
  )
  private val TOOL_HINTS=listOf(
   Regex("\\b(abre|open|liga|desliga|set|define|cria|create|apaga|delete|remove|guarda|save|memoriza|remember)\\b",RegexOption.IGNORE_CASE),
   Regex("\\b(scan|analisa|analyze|verifica|check|procura|search|pesquisa|notifica|notification|alarme|alarm|timer)\\b",RegexOption.IGNORE_CASE)
  )
 }
}
