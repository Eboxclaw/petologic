package ai.petologic.paladino.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Range-resuming downloader for pinned model files.
 *
 * Why this exists: large CDNs stall near the end of long transfers; a single silent stop used to
 * leave the app "stuck at 99%" until the user retried from zero. Rules here:
 *  - a complete-looking leftover file is verified first by [ModelLibrary.install] (never re-downloaded);
 *  - reads time out after [IDLE_TIMEOUT_SECONDS] of silence and the download automatically
 *    reconnects with `Range: bytes=<len>-` (up to [MAX_ATTEMPTS] attempts), never failing the
 *    whole install while bytes made progress;
 *  - if the server ignores Range and answers 200, the partial file is truncated and restarted.
 */
internal class ModelDownloader(
 private val status:MutableStateFlow<String>,
 private val progress:MutableStateFlow<Float?>,
 private val idleTimeoutSeconds:Long=IDLE_TIMEOUT_SECONDS
){
 private val client by lazy{OkHttpClient.Builder()
  .callTimeout(30,TimeUnit.MINUTES)
  .readTimeout(idleTimeoutSeconds,TimeUnit.SECONDS)
  .build()}

 /** Streams the body into [partial], resuming and reconnecting. Verifies size only; the caller hashes. */
 suspend fun downloadTo(partial:File,url:String,size:Long,label:String){
  // A size-complete partial means the caller verified it before calling; nothing to fetch.
  if(partial.length()==size){progress.value=1f;return}
  var attempt=0
  while(true){
   attempt++
   val offset=partial.length().coerceAtMost(size)
   progress.value=offset.toFloat()/size
   status.value=if(attempt==1)"Downloading $label"
    else "Connection dropped near ${(offset*100/size)}% — continuing from there (attempt $attempt of $MAX_ATTEMPTS)"
   val call=client.newCall(Request.Builder().url(url).apply{if(offset>0)header("Range","bytes=$offset-")}.build())
   try{
    call.execute().use{response->
     check(response.isSuccessful){"Download failed (${response.code})."}
     val append=offset>0&&response.code==206
     var count=if(append)offset else 0
     response.body.byteStream().use{input->java.io.FileOutputStream(partial,append).use{output->
      val buf=ByteArray(65536)
      while(true){
       currentCoroutineContext().ensureActive()
       val n=input.read(buf)
       if(n<0)break
       count+=n;check(count<=size){"The server sent more bytes than the verified model size."}
       output.write(buf,0,n)
       progress.value=count.toFloat()/size
      }
     }}
    }
    check(partial.length()==size){"Download stopped early at ${(partial.length()*100/size)}%."}
    return
   }catch(e:Exception){
    if(e is kotlinx.coroutines.CancellationException)throw e
    // Keep the partial bytes; the next attempt resumes from wherever reality stopped.
    if(attempt>=MAX_ATTEMPTS)throw RuntimeException("The connection kept failing at ${(partial.length()*100/maxOf(size,1))}%. Retry — the download continues from there, not from zero.",e)
    delay(RETRY_BACKOFF_MS*attempt)
   }
  }
 }

 companion object{
  const val IDLE_TIMEOUT_SECONDS=20L
  const val MAX_ATTEMPTS=4
  const val RETRY_BACKOFF_MS=1500L
 }
}
