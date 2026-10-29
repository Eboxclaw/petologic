package ai.petologic.paladino.runtime

import android.content.Context
import android.os.Debug
import ai.petologic.core.*
import ai.petologic.paladino.inference.InferenceMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream

class NativeLfm {
 companion object{init{System.loadLibrary("paladino")}}
 external fun load(path:String,context:Int,threads:Int,batch:Int,microBatch:Int,mmap:Boolean)
 external fun prepare()
 external fun generate(system:ByteArray,user:ByteArray,roles:Array<String>,contents:Array<ByteArray>,callback:TokenCallback,maxOutput:Int,temperature:Float,topK:Int,topP:Float,repeatPenalty:Float,seed:Int):LongArray
 external fun generateVisual(projectorPath:String,system:ByteArray,user:ByteArray,rgb:ByteArray,width:Int,height:Int,callback:TokenCallback,maxOutput:Int,temperature:Float,topK:Int,topP:Float,repeatPenalty:Float,seed:Int,threads:Int):LongArray
 external fun cancel()
 external fun unload()
}
fun interface TokenCallback{fun onToken(bytes:ByteArray)}

class LocalModel(private val context:Context,private val library:ModelLibrary){
 companion object {
  const val FILE_NAME="LFM2.5-VL-450M-Q4_0.gguf"
  const val SHA256="6d2757dd0f0b98aea7dc90477bb5b3a0df1089be85ef92943f8cecb05121ccbf"
 }
 val file get()=library.file(ModelCatalog.SPEAKER_ID)
 val status=library.status
 val progress=library.progress
 val ready=MutableStateFlow(false)
 val metrics=MutableStateFlow<InferenceMetrics?>(null)
 private val native by lazy{NativeLfm()}
 private val generationMutex=Mutex()
 private var loadedSpec:String?=null
 suspend fun verify(){library.verifyAll();refreshAvailability()}
 fun refreshAvailability(){ready.value=ModelCatalog.SPEAKER_ID in library.installed.value}
 suspend fun install(){library.install(ModelCatalog.SPEAKER_ID);refreshAvailability()}
 suspend fun generate(system:String,user:String,modelId:String=ModelCatalog.SPEAKER_ID,options:SessionOptions=SessionOptions(),turns:List<ChatTurn> = emptyList(),onText:(String)->Unit):String=generationMutex.withLock{
  options.validate();check(modelId in library.installed.value){"Install or import ${ModelCatalog.get(modelId).model} in Models first."}
  check(ModelCatalog.get(modelId).kind==ModelKind.GENERATIVE){"The vector model cannot generate responses."}
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
    val stats=native.generate(system.toByteArray(),user.toByteArray(),turns.map{it.speaker}.toTypedArray(),turns.map{it.text.toByteArray()}.toTypedArray(),TokenCallback{bytes->if(!job.isActive)native.cancel()else{output.write(bytes);onText(output.toString("UTF-8").removeSuffix("\uFFFD"))}},options.outputTokens,options.temperature,options.topK,options.topP,options.repeatPenalty,options.seed)
    ensureActive()
    metrics.value=InferenceMetrics(modelId,options.contextTokens,loadMs,stats[0],stats[1],stats[2],stats[3],peakPss,Debug.getPss())
    output.toString("UTF-8")
   }finally{watcher.cancel()}
  }
 }
 suspend fun generateVisual(system:String,user:String,rgb:ByteArray,width:Int,height:Int,options:SessionOptions=SessionOptions(outputTokens=192,maxHops=1,maxToolCalls=0,maxRetries=0),onText:(String)->Unit):String=generationMutex.withLock{
  options.validate();check(ModelCatalog.SPEAKER_ID in library.installed.value){"Install LFM2.5-VL-450M first."};check(ModelCatalog.VISION_PROJECTOR_ID in library.installed.value){"Install the Paladino vision pack first."}
  withContext(Dispatchers.IO){
   native.prepare();val job=currentCoroutineContext()[Job]!!;var peakPss=Debug.getPss();val watcher=CoroutineScope(Dispatchers.Default).launch{while(isActive){if(!job.isActive){native.cancel();break};peakPss=maxOf(peakPss,Debug.getPss());delay(50)}}
   try{
    val spec="${ModelCatalog.SPEAKER_ID}:${options.contextTokens}:${options.threads}:${options.batch}:${options.microBatch}:${options.mmap}"
    val begin=System.nanoTime();var loadMs=0L
    if(loadedSpec!=spec){if(loadedSpec!=null)native.unload();loadedSpec=null;native.load(file.absolutePath,options.contextTokens,options.threads,options.batch,options.microBatch,options.mmap);loadedSpec=spec;loadMs=(System.nanoTime()-begin)/1_000_000}
    ensureActive();val output=ByteArrayOutputStream()
    val stats=native.generateVisual(library.file(ModelCatalog.VISION_PROJECTOR_ID).absolutePath,system.toByteArray(),user.toByteArray(),rgb,width,height,TokenCallback{bytes->if(!job.isActive)native.cancel()else{output.write(bytes);onText(output.toString("UTF-8").removeSuffix("\uFFFD"))}},options.outputTokens,options.temperature,options.topK,options.topP,options.repeatPenalty,options.seed,options.threads)
    ensureActive();metrics.value=InferenceMetrics(ModelCatalog.SPEAKER_ID,options.contextTokens,loadMs,stats[0],stats[1],stats[2],stats[3],peakPss,Debug.getPss());output.toString("UTF-8")
   }finally{watcher.cancel()}
  }
 }
 suspend fun unload()=generationMutex.withLock{withContext(Dispatchers.IO){if(loadedSpec!=null){native.unload();loadedSpec=null}}}
}
