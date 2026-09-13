package ai.petologic.paladino.inference

import ai.petologic.paladino.inference.llama.LlamaCppBackend
import ai.petologic.paladino.runtime.LocalModel
import ai.petologic.paladino.runtime.ModelLibrary
import android.content.Context

/** The app engine: llama.cpp (LEAP was evaluated and removed after its 2026-09 deprecation). */
object BackendFactory{
 fun create(context:Context,local:LocalModel,library:ModelLibrary):LocalInferenceBackend=LlamaCppBackend(local)
}
