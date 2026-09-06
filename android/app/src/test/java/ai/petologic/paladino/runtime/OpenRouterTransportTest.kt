package ai.petologic.paladino.runtime
import ai.petologic.core.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

class OpenRouterTransportTest {
 @Test fun `streamed answer is assembled and payload is exact`()= runTest {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\" there\"}}]}\n\ndata: [DONE]\n\n"))
   server.start()
   val context=ContextBroker("Paladino").build("hi",ExecutionMode.MAXX,emptyList())
   val transport=OpenRouterTransport(endpoint=server.url("/chat").toString())
   val text=transport.generate(context,"test/model","test-key",CloudConsent(context.digest,"test/model",Instant.now().plusSeconds(60))){}
   assertEquals("Hello there",text)
   val request=server.takeRequest();assertEquals("Bearer test-key",request.getHeader("Authorization"))
   val body=request.body.readUtf8();assertTrue(body.contains("\"allow_fallbacks\":false"));assertFalse(body.contains("test-key"))
  }
 }
 @Test fun `changed consent makes no network request`()= runTest {
  MockWebServer().use { server ->
   server.start();val c=ContextBroker("Paladino").build("hello",ExecutionMode.MAXX,emptyList())
   try{OpenRouterTransport(endpoint=server.url("/chat").toString()).generate(c,"test/model","key",CloudConsent("wrong","test/model",Instant.now().plusSeconds(60))){};fail("Expected rejection")}
   catch(_:IllegalArgumentException){}
   assertEquals(0,server.requestCount)
  }
 }
 @Test fun `partial cloud response is not reported complete`()= runTest {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setBody("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"));server.start()
   val c=ContextBroker("Paladino").build("hello",ExecutionMode.MAXX,emptyList())
   try{OpenRouterTransport(endpoint=server.url("/chat").toString()).generate(c,"test/model","key",CloudConsent(c.digest,"test/model",Instant.now().plusSeconds(60))){};fail("Expected incomplete response")}
   catch(e:IllegalStateException){assertTrue(e.message!!.contains("before completion"))}
  }
 }
 @Test fun `private sources rejected even with a forged context envelope`()= runTest {
  MockWebServer().use { server ->
   server.start();val c=ContextEnvelope("Paladino","hello",listOf(MemoryNote("id","secret")),"hash")
   try{OpenRouterTransport(endpoint=server.url("/chat").toString()).generate(c,"test/model","key",CloudConsent(c.digest,"test/model",Instant.now().plusSeconds(60))){};fail("Expected private source rejection")}
   catch(_:IllegalArgumentException){}
   assertEquals(0,server.requestCount)
  }
 }
}
