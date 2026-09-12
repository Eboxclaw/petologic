package ai.petologic.paladino.runtime

import ai.petologic.core.SessionOptions
import ai.petologic.paladino.inference.LocalGenerationRequest
import ai.petologic.paladino.inference.LocalInferenceBackend

/**
 * The sub-agent: a bounded worker turn with no tools, for background jobs like titling.
 * Model selection follows the user's rule — the small 230M when downloaded, otherwise a
 * turn of the main 350M. True parallelism waits for the native two-slot refactor (plan 15);
 * today the worker's turn queues on the shared model between user turns.
 */
object SubAgent{
 const val PREFERRED="lfm230-qad"
 const val FALLBACK="lfm350"
 const val TITLE_MAX=42

 fun modelId(installed:Set<String>):String=if(PREFERRED in installed)PREFERRED else FALLBACK

 fun workerOptions()=SessionOptions(contextTokens=2048,outputTokens=128,toolReserve=128,
  maxHops=1,maxToolCalls=0,maxRetries=0,hopTimeoutSeconds=30,totalTimeoutSeconds=60)

 /** One bounded worker turn; throws when the model is missing (callers fail silent). */
 suspend fun worker(inference:LocalInferenceBackend,modelId:String,system:String,task:String):String=
  inference.generate(LocalGenerationRequest(system,task,modelId,workerOptions())){}

 /** A short human-usable title from the first exchange; empty input stays empty. */
 fun titleFrom(firstUser:String,firstAssistant:String):String{
  val source=sequenceOf(firstUser,firstAssistant).firstOrNull{it.isNotBlank()}?.trim()?:return ""
  val clean=source.replace(Regex("\\s+")," ")
  if(clean.length<=TITLE_MAX)return clean
  return clean.take(TITLE_MAX).trimEnd()+"…"
 }
}
