package ai.petologic.paladino.inference

import ai.petologic.paladino.inference.leap.LeapBackend
import ai.petologic.paladino.runtime.LocalModel
import ai.petologic.paladino.runtime.ModelLibrary
import android.content.Context

/**
 * leap flavor: the experimental LEAP runtime is the default engine of this flavor. `local`
 * (llama.cpp) stays available for the app's model lifecycle UI and for the dual-engine
 * collision experiment — it is NOT wired into the agent here.
 */
object BackendFactory{
 fun create(context:Context,local:LocalModel,library:ModelLibrary):LocalInferenceBackend=LeapBackend(context,library)
}
