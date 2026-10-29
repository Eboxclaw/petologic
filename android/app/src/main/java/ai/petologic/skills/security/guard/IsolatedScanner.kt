package ai.petologic.skills.security.guard

import android.content.*
import android.os.*
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ai.petologic.security.Finding
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File

object IsolatedScanner {
 private val mutex=Mutex()
 suspend fun scan(context:Context,uri:Uri,pack:File?):Finding=mutex.withLock{
  withContext(Dispatchers.IO){
   require(uri.scheme=="content"){"Choose a file with the Android file picker."}
   val fd=requireNotNull(context.contentResolver.openFileDescriptor(uri,"r")){"File cannot be opened."}
   scanDescriptor(context,fd,pack)
  }
 }
 suspend fun scan(context:Context,file:File,pack:File?):Finding=mutex.withLock{
  withContext(Dispatchers.IO){
   require(file.isFile){"Installed APK cannot be opened."}
   scanDescriptor(context,ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY),pack)
  }
 }
 private suspend fun scanDescriptor(context:Context,fd:ParcelFileDescriptor,pack:File?):Finding {
   val packFd=pack?.takeIf{it.exists()}?.let{ParcelFileDescriptor.open(it,ParcelFileDescriptor.MODE_READ_ONLY)}
   val result=CompletableDeferred<Finding>();var remote:Messenger?=null
   val response=Messenger(object:Handler(Looper.getMainLooper()){
    override fun handleMessage(msg:Message){
     val error=msg.data.getString("error")
     if(error!=null)result.completeExceptionally(IllegalStateException(error))
     else runCatching{Json.decodeFromString<Finding>(requireNotNull(msg.data.getString("finding")))}
      .onSuccess{result.complete(it)}.onFailure{result.completeExceptionally(it)}
    }
   })
   val connection=object:ServiceConnection{
    override fun onServiceConnected(name:ComponentName,binder:IBinder){
     remote=Messenger(binder)
     try{remote!!.send(Message.obtain(null,1).apply{replyTo=response;data=Bundle().apply{putParcelable("file",fd);putParcelable("pack",packFd)}})}
     catch(e:Exception){result.completeExceptionally(e)}
    }
    override fun onServiceDisconnected(name:ComponentName){result.completeExceptionally(IllegalStateException("Scanner stopped; scan coverage is incomplete."))}
    override fun onNullBinding(name:ComponentName){result.completeExceptionally(IllegalStateException("Scanner unavailable."))}
   }
   var bound=false
   return try{
    bound=context.bindIsolatedService(Intent(context,ScanWorkerService::class.java),Context.BIND_AUTO_CREATE,"scan"+java.util.UUID.randomUUID().toString().replace("-",""),context.mainExecutor,connection)
    check(bound){"Scanner unavailable."}
    withTimeout(15_000){result.await()}
   }finally{
    runCatching{remote?.send(Message.obtain(null,2))}
    if(bound)runCatching{context.unbindService(connection)}
    fd.close();packFd?.close()
   }
 }
}
