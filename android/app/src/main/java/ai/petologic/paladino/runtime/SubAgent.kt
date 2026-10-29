package ai.petologic.paladino.runtime

import ai.petologic.core.SessionOptions
import ai.petologic.paladino.inference.LocalGenerationRequest
import ai.petologic.paladino.inference.LocalInferenceBackend

/**
 * The sub-agent: a bounded worker turn with no tools, for background jobs like titling.
 * Every speaking turn uses the same VL-450M model. Worker turns remain serialized with chat.
 */
object SubAgent{
 const val MODEL=ai.petologic.core.ModelCatalog.SPEAKER_ID
 const val TITLE_MAX=42

 fun modelId(installed:Set<String>):String=MODEL

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
