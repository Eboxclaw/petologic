package ai.petologic.paladino.inference.leap

import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.MessageResponse
import ai.petologic.core.ModelCatalog
import ai.petologic.core.SessionOptions
import ai.petologic.paladino.inference.BackendCapabilities
import ai.petologic.paladino.inference.InferenceMetrics
import ai.petologic.paladino.inference.LocalGenerationRequest
import ai.petologic.paladino.inference.LocalInferenceBackend
import ai.petologic.paladino.runtime.ModelLibrary
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

/**
 * Experimental LEAP 0.10.9 backend (plan 16). Proven by the stage-2 spike: our QAD GGUFs
 * sideload directly, and several runners stay resident at once — so, unlike llama.cpp, the
 * sub-agent's 230M no longer has to evict the main 350M. Generation is still serialized at
 * this layer to keep SessionPolicy/LoopBudget semantics identical across backends.
 */
class LeapBackend(private val library:ModelLibrary):LocalInferenceBackend{
 override val ready=MutableStateFlow(false)
 override val metrics=MutableStateFlow<InferenceMetrics?>(null)
 override val capabilities=BackendCapabilities(streaming=true,cancellation=true,multiModel=true)
 private val lock=Mutex()
 private val runners=mutableMapOf<String,ModelRunner>()

 override suspend fun verify(){library.verifyAll();ready.value="lfm350" in library.installed.value}

 private suspend fun load(modelId:String,options:SessionOptions):ModelRunner=LeapClient.loadModel(
  library.file(modelId).absolutePath,
  ModelLoadingOptions(cpuThreads=options.threads,contextSize=options.contextTokens,useMmap=options.mmap))

 suspend fun preload(modelId:String,options:SessionOptions=SessionOptions())=lock.withLock{
  key(modelId,options)?.let{key->runCatching{runners.getOrPut(key){load(modelId,options)}}}
 }

 private fun key(modelId:String,options:SessionOptions):String?{
  if(modelId=="minilm")return null
  if(modelId !in library.installed.value)throw IllegalStateException("Install or import ${ModelCatalog.get(modelId).model} in Models first.")
  return "$modelId:${options.contextTokens}:${options.threads}:${options.mmap}"
 }

 override suspend fun generate(request:LocalGenerationRequest,onText:(String)->Unit):String=lock.withLock{
  request.options.validate()
  val modelId=if(request.modelId.startsWith("lfm"))request.modelId else "lfm350"
  withContext(Dispatchers.IO){
   val spec=key(modelId,request.options)?:throw IllegalArgumentException("The encoder cannot generate responses.")
   var loadMs=0L
   val runner=runners.getOrPut(spec){
    val begin=System.nanoTime();val r=load(modelId,request.options);loadMs=(System.nanoTime()-begin)/1_000_000;r
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
    val options=GenerationOptions(
     temperature=request.options.temperature,topP=request.options.topP,topK=request.options.topK,
     repetitionPenalty=request.options.repeatPenalty,rngSeed=request.options.seed.toLong(),
     maxTokens=request.options.outputTokens)
    val out=StringBuilder();var ttftMicros=0L
    conversation.generateResponse(request.user,options).collect{response->
     if(!job.isActive)return@collect
     when(response){
      is MessageResponse.Chunk->{if(ttftMicros==0L)ttftMicros=(System.nanoTime()-begin)/1000;out.append(response.text);onText(out.toString())}
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
    out.toString()
   }finally{watcher.cancel()}
  }
 }

 suspend fun unload(modelId:String?=null)=lock.withLock{withContext(Dispatchers.IO){
  val targets=if(modelId==null)runners.keys.toList() else runners.keys.filter{it.startsWith("$modelId:")}
  targets.forEach{key->runners.remove(key)?.unload()}
 }}

 override suspend fun unload()=unload(null)
}
