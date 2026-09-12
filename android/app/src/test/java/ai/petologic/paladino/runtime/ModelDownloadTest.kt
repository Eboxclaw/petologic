package ai.petologic.paladino.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest

class ModelDownloadTest {
 private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
 private fun payload(size:Int,seed:Byte)=ByteArray(size){seed}
 // One-second idle timeout keeps stall tests fast; production defaults to 20 s.
 private fun downloader()=ModelDownloader(MutableStateFlow(""),MutableStateFlow(null),idleTimeoutSeconds=1)

 @Test fun `full body downloads verifies size and completes`(){
  val body=payload(1_000_000,1)
  MockWebServer().use{server->
   server.enqueue(MockResponse().setBody(okio.Buffer().write(body)));server.start()
   val partial=File.createTempFile("mdl",".partial").apply{deleteOnExit()}
   runTest{downloader().downloadTo(partial,server.url("/m.gguf").toString(),body.size.toLong(),"test")}
   assertEquals(body.size.toLong(),partial.length())
   assertEquals(sha(body),sha(partial.readBytes()))
  }
 }
 @Test fun `partial file resumes with range header and appends`(){
  val body=payload(1_000_000,2);val half=body.copyOfRange(0,500_000);val rest=body.copyOfRange(500_000,body.size)
  MockWebServer().use{server->
   server.enqueue(MockResponse().setResponseCode(206).setBody(okio.Buffer().write(rest)));server.start()
   val partial=File.createTempFile("mdl",".partial");partial.writeBytes(half);partial.deleteOnExit()
   runTest{downloader().downloadTo(partial,server.url("/m.gguf").toString(),body.size.toLong(),"test")}
   val recorded=server.takeRequest()
   assertEquals("bytes=500000-",recorded.getHeader("Range"))
   assertEquals(body.size.toLong(),partial.length());assertEquals(sha(body),sha(partial.readBytes()))
  }
 }
 @Test fun `server ignoring range truncates and restarts cleanly`(){
  val body=payload(1_000_000,3);val garbage=payload(500_000,9)
  MockWebServer().use{server->
   server.enqueue(MockResponse().setBody(okio.Buffer().write(body)));server.start()
   val partial=File.createTempFile("mdl",".partial");partial.writeBytes(garbage);partial.deleteOnExit()
   runTest{downloader().downloadTo(partial,server.url("/m.gguf").toString(),body.size.toLong(),"test")}
   assertEquals(body.size.toLong(),partial.length());assertEquals(sha(body),sha(partial.readBytes()))
  }
 }
 @Test fun `mid-body disconnect auto resumes instead of failing at 99 percent`(){
  val body=payload(900_000,4)
  MockWebServer().use{server->
   // Attempt 1 dies mid-body after most bytes (the classic 99% stall); later attempts serve
   // exactly the remaining range, so the retry always completes no matter where the cut was.
   var first=0
   server.dispatcher=object:Dispatcher(){
    override fun dispatch(request:RecordedRequest):MockResponse{
     val range=request.getHeader("Range")
     return if(first++==0)MockResponse().setBody(okio.Buffer().write(body))
      .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
     else{
      val start=range!!.removePrefix("bytes=").removeSuffix("-").toInt()
      MockResponse().setResponseCode(206).setBody(okio.Buffer().write(body.copyOfRange(start,body.size)))
     }
    }
   }
   server.start()
   val partial=File.createTempFile("mdl",".partial").apply{deleteOnExit()}
   val status=MutableStateFlow("")
   runTest{ModelDownloader(status,MutableStateFlow(null),idleTimeoutSeconds=1)
    .downloadTo(partial,server.url("/m.gguf").toString(),body.size.toLong(),"test")}
   assertEquals(body.size.toLong(),partial.length());assertEquals(sha(body),sha(partial.readBytes()))
   assertTrue("Status must say it continued, not failed",status.value.contains("continuing"))
  }
 }
 @Test fun `exhausted attempts fail with a continue-not-restart message`(){
  MockWebServer().use{server->
   repeat(ModelDownloader.MAX_ATTEMPTS){server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))}
   server.start()
   val partial=File.createTempFile("mdl",".partial").apply{deleteOnExit()}
   val outcome=runCatching{runTest{downloader().downloadTo(partial,server.url("/m.gguf").toString(),1_000L,"test")}}
   assertTrue(outcome.isFailure)
   assertTrue(outcome.exceptionOrNull()!!.message!!.contains("continues from there"))
  }
 }
 @Test fun `size complete partial never touches the network`(){
  val body=payload(64_000,5)
  MockWebServer().use{server->
   server.start() // nothing enqueued: any request would fail the test
   val partial=File.createTempFile("mdl",".partial");partial.writeBytes(body);partial.deleteOnExit()
   runTest{downloader().downloadTo(partial,server.url("/m.gguf").toString(),body.size.toLong(),"test")}
   assertEquals(0,server.requestCount)
  }
 }
}
