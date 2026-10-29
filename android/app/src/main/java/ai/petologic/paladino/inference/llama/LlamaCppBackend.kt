package ai.petologic.paladino.inference.llama

import ai.petologic.paladino.inference.BackendCapabilities
import ai.petologic.paladino.inference.InferenceMetrics
import ai.petologic.paladino.inference.LocalGenerationRequest
import ai.petologic.paladino.inference.LocalInferenceBackend
import ai.petologic.paladino.runtime.LocalModel
import kotlinx.coroutines.flow.StateFlow

/** The existing llama.cpp/JNI runtime behind the backend interface; behavior is delegated verbatim. */
class LlamaCppBackend(private val local:LocalModel):LocalInferenceBackend{
 override val ready:StateFlow<Boolean> get()=local.ready
 override val metrics:StateFlow<InferenceMetrics?> get()=local.metrics
 override val capabilities=BackendCapabilities(streaming=true,cancellation=true,multiModel=false,nativeFunctionCalling=false)
 override suspend fun verify()=local.verify()
 override suspend fun generate(request:LocalGenerationRequest,onText:(String)->Unit):String=
  local.generate(request.system,request.user,request.modelId,request.options,request.turns,onText)
 override suspend fun unload()=local.unload()
}
