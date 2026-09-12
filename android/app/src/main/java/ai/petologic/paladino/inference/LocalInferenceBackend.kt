package ai.petologic.paladino.inference

import ai.petologic.core.ChatTurn
import ai.petologic.core.SessionOptions
import kotlinx.coroutines.flow.StateFlow

data class LocalGenerationRequest(
 val system:String,
 val user:String,
 val modelId:String="lfm350",
 val options:SessionOptions=SessionOptions(),
 val turns:List<ChatTurn> = emptyList()
)

/** What a runtime can honestly do; the agent layer never assumes. */
data class BackendCapabilities(val streaming:Boolean,val cancellation:Boolean,val multiModel:Boolean)

data class InferenceMetrics(val modelId:String,val contextTokens:Int,val loadMs:Long,val promptTokens:Long,val outputTokens:Long,val prefillMicros:Long,val decodeMicros:Long,val peakSampledPssKb:Long,val endPssKb:Long)

/**
 * The seam between the Koog agent layer and a local model runtime (plan 16). Implementations:
 * llama.cpp/JNI ([ai.petologic.paladino.inference.llama.LlamaCppBackend]) today, LEAP as the
 * experimental challenger. Koog keeps the agent loop, tools, approvals and history; a backend
 * only loads models, streams tokens, cancels and unloads.
 *
 * Contract notes preserved from the llama.cpp runtime so callers need no change:
 *  - [generate] streams "text so far" through [onText] (not deltas) and returns the final text;
 *  - generation is serialized inside the backend; a new call waits for the current one;
 *  - cooperative cancellation: cancelling the calling coroutine stops token generation.
 */
interface LocalInferenceBackend{
 val ready:StateFlow<Boolean>
 val metrics:StateFlow<InferenceMetrics?>
 val capabilities:BackendCapabilities
 suspend fun verify()
 suspend fun generate(request:LocalGenerationRequest,onText:(String)->Unit):String
 suspend fun unload()
}
