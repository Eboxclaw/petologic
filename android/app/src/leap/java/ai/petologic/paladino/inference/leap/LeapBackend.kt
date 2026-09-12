package ai.petologic.paladino.inference.leap

import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.inferenceengine.EngineOptions
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.MessageResponse
import ai.petologic.core.ModelCatalog
import ai.petologic.core.SessionOptions
import ai.petologic.paladino.inference.BackendCapabilities
import ai.petologic.paladino.inference.InferenceMetrics
import ai.petologic.paladino.inference.LocalGenerationRequest
import ai.petologic.paladino.inference.LocalInferenceBackend
import ai.petologic.paladino.runtime.ModelLibrary
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Experimental LEAP 0.10.9 backend (plan 16). Policy follows the docs and the user's rule:
 *  - ONE runner per model, always at [RUN_CONTEXT] (8192 — the max one-shot context all our nano
 *    models handle; measured ~1.0 GB PSS with 350M+230M both resident, stable across turns);
 *  - sampling comes from the model manifest (LEAP default GenerationOptions) unless the session
 *    actually overrides it — the docs warn that overriding manifest sampling degrades quality;
 *  - persistent KV-prefix reuse is enabled per model under cacheDir (docs: prefill avoidance for
 *    multi-turn and agent loops; survives restarts, Android may reclaim the dir under pressure).
 * Generation stays serialized to keep SessionPolicy/LoopBudget semantics identical across engines.
 */
class LeapBackend(private val context:Context,private val library:ModelLibrary):LocalInferenceBackend{
 override val ready=MutableStateFlow(false)
 override val metrics=MutableStateFlow<InferenceMetrics?>(null)
 override val capabilities=BackendCapabilities(streaming=true,cancellation=true,multiModel=true)
 private val lock=Mutex()
 private val runners=mutableMapOf<String,ModelRunner>()

 override suspend fun verify(){library.verifyAll();ready.value="lfm350" in library.installed.value}

 private fun checkInstalled(modelId:String){
  check(modelId!="minilm"){"The encoder cannot generate responses."}
  check(modelId in library.installed.value){"Install or import ${ModelCatalog.get(modelId).model} in Models first."}
 }

 private suspend fun load(modelId:String):ModelRunner=LeapClient.loadModel(
  library.file(modelId).absolutePath,
  ModelLoadingOptions(
   contextSize=RUN_CONTEXT,
   useMmap=true,
   cacheOptions=EngineOptions.CacheOptions(
    path=File(context.cacheDir,"leap-kv/$modelId").apply{mkdirs()}.absolutePath,
    enabled=true)))

 suspend fun preload(modelId:String)=lock.withLock{
  checkInstalled(modelId)
  runners.getOrPut(modelId){load(modelId)}
  Unit
 }

 override suspend fun generate(request:LocalGenerationRequest,onText:(String)->Unit):String=lock.withLock{
  request.options.validate()
  val modelId=if(request.modelId.startsWith("lfm"))request.modelId else "lfm350"
  checkInstalled(modelId)
  withContext(Dispatchers.IO){
   var loadMs=0L
   val runner=runners.getOrPut(modelId){
    val begin=System.nanoTime();val r=load(modelId);loadMs=(System.nanoTime()-begin)/1_000_000;r
   }
   val history=ArrayList<ChatMessage>(request.turns.size+1)
   history.add(ChatMessage(ChatMessage.Role.SYSTEM,request.system))
   request.turns.forEach{turn->history.add(ChatMessage(when(turn.speaker){"assistant"->ChatMessage.Role.ASSISTANT;"tool"->ChatMessage.Role.TOOL;else->ChatMessage.Role.USER},turn.text))}
   val conversation=runner.createConversationFromHistory(history)
   val job=currentCoroutineContext()[Job]!!
   var peakPss=Debug.getPss()
   val watcher=launch{while(true){if(!job.isActive)break;peakPss=maxOf(peakPss,Debug.getPss());delay(50)}}
   val begin=System.nanoTime()
   try{
    val options=generationOptions(request.options)
    val out=StringBuilder();val reasoning=StringBuilder();var ttftMicros=0L
    conversation.generateResponse(request.user,options).collect{response->
     if(!job.isActive)return@collect
     when(response){
      is MessageResponse.Chunk->{if(ttftMicros==0L)ttftMicros=(System.nanoTime()-begin)/1000;out.append(response.text);onText(out.toString())}
      is MessageResponse.ReasoningChunk->reasoning.append(response.reasoning)
      is MessageResponse.FunctionCalls->
       // LEAP extracts native tool calls when the model emits LFM call tokens; materialize
       // them back into our pythonic form so the agent-layer parser sees one protocol.
       response.functionCalls.forEach{call->
        val args=call.arguments.entries.joinToString(", "){(k,v)->
         val rendered=if(v is String)'"'+v.replace("\\","\\\\").replace("\"","\\\"")+'"' else v.toString()
         "$k=$rendered"}
        if(ttftMicros==0L)ttftMicros=(System.nanoTime()-begin)/1000
        out.append("[${call.name}($args)]");onText(out.toString())
       }
      is MessageResponse.Complete->{
       val s=response.stats
       metrics.value=InferenceMetrics(modelId,request.options.contextTokens,loadMs,
        s?.promptTokens?:0,s?.completionTokens?:0,ttftMicros,
        if(s!=null&&s.tokenPerSecond>0)(s.completionTokens*1_000_000L/s.tokenPerSecond.toDouble()).toLong() else (System.nanoTime()-begin)/1000-ttftMicros,
        peakPss,Debug.getPss())
      }
      is MessageResponse.Error->throw RuntimeException(response.message,response.throwable)
      else->{}
     }
    }
    currentCoroutineContext().ensureActive()
    // LFM2.5 thinking runs in separate reasoning chunks; when a reply is all reasoning and no
    // content, the reasoning IS the answer — surface it instead of returning an empty string.
    if(out.isBlank()&&reasoning.isNotBlank()){onText(reasoning.toString());reasoning.toString().trim()}else out.toString()
   }finally{watcher.cancel()}
  }
 }

 /** Manifest sampling by default; explicit overrides only when the session changed them.
  * Thinking stays off: LEAP's default-on reasoning consumed the output budget and produced
  * empty replies on the nano models (llama.cpp has no thinking either — parity until we
  * deliberately design a thinking UX). */
 private fun generationOptions(options:SessionOptions):GenerationOptions=if(
  options.temperature==SessionOptions().temperature&&options.topK==SessionOptions().topK&&
  options.topP==SessionOptions().topP&&options.repeatPenalty==SessionOptions().repeatPenalty&&
  options.seed==SessionOptions().seed
 )GenerationOptions(maxTokens=options.outputTokens,enableThinking=false)
 else GenerationOptions(
  temperature=options.temperature,topP=options.topP,topK=options.topK,
  repetitionPenalty=options.repeatPenalty,rngSeed=options.seed.toLong(),
  maxTokens=options.outputTokens,enableThinking=false)

 suspend fun unload(modelId:String?=null)=lock.withLock{withContext(Dispatchers.IO){
  val targets=if(modelId==null)runners.keys.toList() else listOfNotNull(modelId)
  targets.forEach{key->runners.remove(key)?.unload()}
 }}

 override suspend fun unload()=unload(null)

 companion object{
  /** The max one-shot context every Petologic nano model handles (SessionOptions cap as well). */
  const val RUN_CONTEXT=8192
 }
}
