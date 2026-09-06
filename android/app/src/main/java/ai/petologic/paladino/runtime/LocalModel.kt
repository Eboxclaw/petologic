package ai.petologic.paladino.runtime

import android.content.Context
import android.os.Debug
import ai.petologic.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream

class NativeLfm {
 companion object{init{System.loadLibrary("paladino")}}
 external fun load(path:String,context:Int,threads:Int,batch:Int,microBatch:Int,mmap:Boolean)
 external fun prepare()
 external fun generate(system:ByteArray,user:ByteArray,callback:TokenCallback,maxOutput:Int,temperature:Float,topK:Int,topP:Float,repeatPenalty:Float,seed:Int):LongArray
 external fun cancel()
 external fun unload()
}
fun interface TokenCallback{fun onToken(bytes:ByteArray)}
data class InferenceMetrics(val modelId:String,val contextTokens:Int,val loadMs:Long,val promptTokens:Long,val outputTokens:Long,val prefillMicros:Long,val decodeMicros:Long,val peakSampledPssKb:Long,val endPssKb:Long)

class LocalModel(private val context:Context,private val library:ModelLibrary){
 companion object {
  const val FILE_NAME="LFM2.5-350M-Q4_K_M.gguf"
  const val SHA256="7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4"
 }
 val file get()=library.file("lfm350")
 val status=library.status
 val progress=library.progress
 val ready=MutableStateFlow(false)
 val metrics=MutableStateFlow<InferenceMetrics?>(null)
 private val native by lazy{NativeLfm()}
 private val generationMutex=Mutex()
 private var loadedSpec:String?=null
 suspend fun verify(){library.verifyAll();refreshAvailability()}
 fun refreshAvailability(){ready.value="lfm350" in library.installed.value}
 suspend fun install(){library.install("lfm350");refreshAvailability()}
 suspend fun generate(system:String,user:String,modelId:String="lfm350",options:SessionOptions=SessionOptions(),onText:(String)->Unit):String=generationMutex.withLock{
  options.validate();check(modelId in library.installed.value){"Install or import ${ModelCatalog.get(modelId).model} in Models first."}
  check(modelId!="minilm"){"The encoder cannot generate responses."}
  withContext(Dispatchers.IO){
   native.prepare()
   val job=currentCoroutineContext()[Job]!!
   var peakPss=Debug.getPss()
   val watcher=CoroutineScope(Dispatchers.Default).launch{while(isActive){if(!job.isActive){native.cancel();break};peakPss=maxOf(peakPss,Debug.getPss());delay(50)}}
   try{
    val spec="$modelId:${options.contextTokens}:${options.threads}:${options.batch}:${options.microBatch}:${options.mmap}"
    val begin=System.nanoTime();var loadMs=0L
    if(loadedSpec!=spec){if(loadedSpec!=null)native.unload();loadedSpec=null;native.load(library.file(modelId).absolutePath,options.contextTokens,options.threads,options.batch,options.microBatch,options.mmap);loadedSpec=spec;loadMs=(System.nanoTime()-begin)/1_000_000}
    ensureActive();val output=ByteArrayOutputStream()
    val stats=native.generate(system.toByteArray(),user.toByteArray(),TokenCallback{bytes->if(!job.isActive)native.cancel()else{output.write(bytes);onText(output.toString("UTF-8").removeSuffix("\uFFFD"))}},options.outputTokens,options.temperature,options.topK,options.topP,options.repeatPenalty,options.seed)
    ensureActive()
    metrics.value=InferenceMetrics(modelId,options.contextTokens,loadMs,stats[0],stats[1],stats[2],stats[3],peakPss,Debug.getPss())
    output.toString("UTF-8")
   }finally{watcher.cancel()}
  }
 }
 suspend fun unload()=generationMutex.withLock{withContext(Dispatchers.IO){if(loadedSpec!=null){native.unload();loadedSpec=null}}}
}
