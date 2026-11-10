package ai.petologic.paladino.runtime

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NativeVectorModel {
 companion object{init{System.loadLibrary("paladino")}}
 external fun load(path:String,threads:Int)
 external fun embed(text:ByteArray):FloatArray
 external fun unload()
}

/** Dense local vectors for memory and security retrieval. This model never speaks or calls tools. */
class LocalVectorMemory(private val context:Context,private val library:ModelLibrary){
 companion object{
  /** Kept as a literal cache-versioning key; the embedding artifact was retired from the catalog. */
  const val ID="lfm25-embedding-350m"
  /** True only while a pinned embedding artifact exists in the catalog. */
  val available:Boolean get()=ai.petologic.core.ModelCatalog.artifacts.any{it.id==ID}
 }
 private val lock=Mutex()
 private val native by lazy{NativeVectorModel()}
 private var loaded=false
 val ready=MutableStateFlow(false)
 val installing=MutableStateFlow(false)
 suspend fun verify(){library.verifyAll();ready.value=available&&ID in library.installed.value}
 suspend fun install(){check(available){"Semantic memory is not available in this build."};installing.value=true;try{library.install(ID);verify()}finally{installing.value=false}}
 suspend fun embedQuery(text:String)=embed("query: "+text.take(4096))
 suspend fun embedDocument(text:String)=embed("document: "+text.take(8192))
 private suspend fun embed(text:String):FloatArray=lock.withLock{withContext(Dispatchers.IO){
  check(ready.value){"Install semantic memory first."}
  if(!loaded){native.load(library.file(ID).absolutePath,2);loaded=true}
  native.embed(text.toByteArray())
 }}
 suspend fun unload()=lock.withLock{if(loaded){native.unload();loaded=false}}
}
