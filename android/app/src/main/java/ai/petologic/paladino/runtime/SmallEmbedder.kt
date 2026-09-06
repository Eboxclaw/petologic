package ai.petologic.paladino.runtime

import android.content.Context
import ai.onnxruntime.*
import ai.petologic.core.WordPiece
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class SmallEmbedder(private val context:Context,private val library:ModelLibrary){
 companion object{
  const val ID="all-MiniLM-L6-v2-qint8-arm64-1110a243"
  const val REVISION="1110a243fdf4706b3f48f1d95db1a4f5529b4d41"
  const val SHA="4278337fd0ff3c68bfb6291042cad8ab363e1d9fbc43dcb499fe91c871902474"
  const val SIZE=23026053L
  const val URL="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/$REVISION/onnx/model_qint8_arm64.onnx"
 }
 private val file=File(context.filesDir,"models/minilm.onnx")
 private val lock=Mutex()
 private var session:OrtSession?=null
 val ready=MutableStateFlow(false)
 val installing=MutableStateFlow(false)
 private val tokenizer by lazy{WordPiece(context.assets.open("paladino/vocab.txt").bufferedReader().readLines().mapIndexed{index,s->s to index}.toMap())}
 suspend fun verify()=withContext(Dispatchers.IO){ready.value=file.exists()&&file.length()==SIZE&&hash(file)==SHA}
 suspend fun install(){installing.value=true;try{library.install("minilm");verify()}finally{installing.value=false}}
 private fun hash(file:File):String{val md=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;md.update(b,0,n)}};return md.digest().joinToString(""){"%02x".format(it)}}
 suspend fun embed(text:String):FloatArray=lock.withLock{withContext(Dispatchers.Default){
  check(ready.value){"Install semantic memory first."}
  val env=OrtEnvironment.getEnvironment()
  val s=session?:OrtSession.SessionOptions().use{options->options.setIntraOpNumThreads(2);env.createSession(file.absolutePath,options)}.also{session=it}
  val ids=tokenizer.encode(text)
  val input=OnnxTensor.createTensor(env,arrayOf(ids))
  val mask=OnnxTensor.createTensor(env,arrayOf(LongArray(ids.size){1}))
  val types=OnnxTensor.createTensor(env,arrayOf(LongArray(ids.size)))
  try{
   val inputs=mapOf("input_ids" to input,"attention_mask" to mask,"token_type_ids" to types).filterKeys{it in s.inputNames}
   s.run(inputs).use{result->
    @Suppress("UNCHECKED_CAST") val hidden=result[0].value as Array<Array<FloatArray>>
    val vector=FloatArray(hidden[0][0].size)
    for(token in hidden[0])for(i in vector.indices)vector[i]+=token[i]/ids.size
    val norm=sqrt(vector.sumOf{(it*it).toDouble()}).toFloat().coerceAtLeast(1e-12f)
    for(i in vector.indices)vector[i]/=norm
    vector
   }
  }finally{input.close();mask.close();types.close()}
 }}
 suspend fun unload()=lock.withLock{session?.close();session=null}
}
