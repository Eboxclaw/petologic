package ai.petologic.paladino.runtime.decision

import ai.petologic.core.ExecutionMode
import ai.petologic.core.SessionOptions

enum class DecisionTarget {
 CHAT_230M,
 ROUTER_350M,
 CLOUD,
 DIRECT
}

enum class DecisionSource {
 SYSTEM0,
 D1_REMOTE,
 LOCAL_FALLBACK
}

data class DecisionInput(
 val text:String,
 val mode:ExecutionMode,
 val options:SessionOptions,
 val installedModels:Set<String>,
 val remoteDecisionAllowed:Boolean=false
)

data class RouteDecision(
 val target:DecisionTarget,
 val modelId:String?,
 val confidence:Double,
 val needsTool:Boolean,
 val source:DecisionSource,
 val reason:String
)

interface DecisionProvider {
 suspend fun decide(input:DecisionInput):RouteDecision?
}
