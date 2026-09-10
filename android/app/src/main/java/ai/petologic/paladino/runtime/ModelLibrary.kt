package ai.petologic.paladino.runtime
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import ai.petologic.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Only exact approved publisher/model/quantization hashes enter the runtime. */
class ModelLibrary(private val context:Context){
 val directory=File(context.filesDir,"models").apply{mkdirs()}
 val installed=MutableStateFlow<Set<String>>(emptySet())
 val status=MutableStateFlow("Checking installed models…")
 val progress=MutableStateFlow<Float?>(null)
 private val lock=Mutex()
 private val prefs=context.getSharedPreferences("model_library",Context.MODE_PRIVATE)
 fun file(id:String)=File(directory,ModelCatalog.get(id).filename)
 suspend fun verifyAll()=lock.withLock{withContext(Dispatchers.IO){
  installed.value=ModelCatalog.artifacts.filter{val f=file(it.id);f.exists()&&f.length()==it.size&&hash(f)==it.sha256}.map{it.id}.toSet()
  status.value="${installed.value.size} verified models available"
 }}
 suspend fun import(uri:Uri):ModelArtifact=lock.withLock{withContext(Dispatchers.IO){
  status.value="Verifying existing model…";progress.value=0f
  try{val artifact=copyVerified(context.contentResolver.openInputStream(uri)?:error("This file is no longer available."));status.value="Reused ${artifact.model} · ${artifact.quantization}. No download.";artifact}finally{progress.value=null}
 }}
 suspend fun rememberAndScan(uri:Uri):Int=lock.withLock{withContext(Dispatchers.IO){
  prefs.edit().putString("folder",uri.toString()).apply();status.value="Checking selected model folder…";progress.value=0f
  try{scan(uri).also{status.value="Reused $it matching model files. No download."}}finally{progress.value=null}
 }}
 private fun scan(uri:Uri):Int {
  val root=DocumentFile.fromTreeUri(context,uri)?:error("Folder is unavailable.")
  var checked=0;var found=0
  fun visit(folder:DocumentFile,depth:Int){
   for(entry in folder.listFiles()){
    if(++checked>128)return
    if(entry.isDirectory&&depth<2)visit(entry,depth+1)
    else if(entry.isFile&&ModelCatalog.artifacts.any{it.size==entry.length()&&it.id !in installed.value}){
     val stream=context.contentResolver.openInputStream(entry.uri)?:continue
     try{copyVerified(stream);found++}catch(_:IllegalArgumentException){/* Same size is only a candidate, never proof. */}
    }
   }
  }
  visit(root,0);return found
 }
 suspend fun install(id:String)=lock.withLock{withContext(Dispatchers.IO){
  val artifact=ModelCatalog.get(id)
  if(id in installed.value){status.value="Already installed · ${artifact.model} ${artifact.quantization}";return@withContext}
  prefs.getString("folder",null)?.let{folder->try{scan(Uri.parse(folder))}catch(_:SecurityException){}catch(_:IllegalArgumentException){}}
  if(id in installed.value){status.value="Reused ${artifact.model} from your selected folder";return@withContext}
  require(directory.usableSpace>artifact.size*2){"Free at least ${artifact.size*2/1_000_000} MB to install safely."}
  progress.value=0f;status.value="Downloading ${artifact.model} · ${artifact.quantization}"
  val partial=File(directory,"${artifact.filename}.partial")
  try{
   val offset=partial.takeIf{it.length()<artifact.size}?.length()?:0
   val call=OkHttpClient.Builder().callTimeout(30,TimeUnit.MINUTES).readTimeout(60,TimeUnit.SECONDS).build().newCall(Request.Builder().url(artifact.url).apply{if(offset>0)header("Range","bytes=$offset-")}.build())
   call.execute().use{response->
    check(response.isSuccessful){"Download failed (${response.code})."};val append=offset>0&&response.code==206;var count=if(append)offset else 0
    response.body.byteStream().use{input->java.io.FileOutputStream(partial,append).use{output->val buf=ByteArray(65536);while(true){ensureActive();val n=input.read(buf);if(n<0)break;count+=n;check(count<=artifact.size);output.write(buf,0,n);progress.value=count.toFloat()/artifact.size}}}
   }
   status.value="Verifying ${artifact.model} · ${artifact.quantization}"
   check(partial.length()==artifact.size&&hash(partial)==artifact.sha256){"Model checksum mismatch. Retry or select the official file."}
   check(partial.renameTo(file(id))){"Could not install verified model."};installed.update{it+id};status.value="Ready · ${artifact.model} ${artifact.quantization}"
  }finally{progress.value=null}
 }}
 private fun copyVerified(input:InputStream):ModelArtifact {
  val partial=File(directory,"import.partial");val md=MessageDigest.getInstance("SHA-256");var count=0L
  input.use{source->partial.outputStream().use{output->val buf=ByteArray(65536);while(true){val n=source.read(buf);if(n<0)break;count+=n;require(count<=ModelCatalog.artifacts.maxOf{it.size}){"Not a recognized model file."};md.update(buf,0,n);output.write(buf,0,n)}}}
  val matched=ModelCatalog.match(count,md.digest().joinToString(""){"%02x".format(it)})?:throw IllegalArgumentException("The file does not match an approved publisher, model and quantization. No model was replaced.")
  if(matched.id in installed.value){partial.delete();return matched}
  check(partial.renameTo(file(matched.id))){"Could not import verified model."};installed.update{it+matched.id};return matched
 }
 private fun hash(f:File):String{val md=MessageDigest.getInstance("SHA-256");f.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;md.update(b,0,n)}};return md.digest().joinToString(""){"%02x".format(it)}}
}
