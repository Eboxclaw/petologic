package ai.petologic.paladino.inference

import ai.petologic.paladino.inference.llama.LlamaCppBackend
import ai.petologic.paladino.runtime.LocalModel
import ai.petologic.paladino.runtime.ModelLibrary
import android.content.Context

/** llama flavor: the app engine is always the llama.cpp runtime. */
object BackendFactory{
 fun create(context:Context,local:LocalModel,library:ModelLibrary):LocalInferenceBackend=LlamaCppBackend(local)
}
