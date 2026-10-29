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
  const val ID=ai.petologic.core.ModelCatalog.EMBEDDING_ID
 }
 private val lock=Mutex()
 private val native by lazy{NativeVectorModel()}
 private var loaded=false
 val ready=MutableStateFlow(false)
 val installing=MutableStateFlow(false)
 suspend fun verify(){library.verifyAll();ready.value=ID in library.installed.value}
 suspend fun install(){installing.value=true;try{library.install(ID);verify()}finally{installing.value=false}}
 suspend fun embedQuery(text:String)=embed("query: "+text.take(4096))
 suspend fun embedDocument(text:String)=embed("document: "+text.take(8192))
 private suspend fun embed(text:String):FloatArray=lock.withLock{withContext(Dispatchers.IO){
  check(ready.value){"Install semantic memory first."}
  if(!loaded){native.load(library.file(ID).absolutePath,2);loaded=true}
  native.embed(text.toByteArray())
 }}
 suspend fun unload()=lock.withLock{if(loaded){native.unload();loaded=false}}
}
