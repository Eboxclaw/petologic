package ai.petologic.paladino.runtime

import ai.petologic.core.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

class CloudProviderTest {
 @Test fun provider_consent_and_wire_format_are_isolated()=runTest {
  for(provider in CloudProvider.entries)MockWebServer().use{server->
   server.start()
   val c=ContextBroker("Paladino").build("Olá / hello",ExecutionMode.MAXX,emptyList())
   val model=if(provider==CloudProvider.OPENROUTER)"vendor/model" else "test-model"
   val consent=CloudConsent(c.digest,model,Instant.now().plusSeconds(60),provider.id)
   val transport=OpenRouterTransport(endpoint=server.url("/chat/completions").toString())
   val other=CloudProvider.entries.first{it!=provider}
   try{transport.generate(c,model,"synthetic",consent,other){};fail("Provider substitution must fail")}catch(_:IllegalArgumentException){}
   assertEquals(0,server.requestCount)
   server.enqueue(MockResponse().setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Olá! Hello!\"}}]}\n\ndata: [DONE]\n\n"))
   assertEquals("Olá! Hello!",transport.generate(c,model,"synthetic",consent,provider){})
   val req=server.takeRequest();assertEquals("Bearer synthetic",req.getHeader("Authorization"))
   val body=Json.parseToJsonElement(req.body.readUtf8()).jsonObject
   assertEquals(provider==CloudProvider.OPENROUTER,"provider" in body)
   assertEquals(provider==CloudProvider.OPENAI,"max_completion_tokens" in body)
   assertFalse("temperature" in body)
   assertFalse(body.toString().contains("synthetic"))
  }
 }
 @Test fun redirects_never_receive_credentials()=runTest {
  MockWebServer().use{first->MockWebServer().use{second->
   first.start();second.start();first.enqueue(MockResponse().setResponseCode(307).setHeader("Location",second.url("/steal")))
   val c=ContextBroker("Paladino").build("hi",ExecutionMode.MAXX,emptyList())
   try{OpenRouterTransport(endpoint=first.url("/chat").toString()).generate(c,"test-model","synthetic",CloudConsent(c.digest,"test-model",Instant.now().plusSeconds(60),"openai"),CloudProvider.OPENAI){};fail("Redirect must fail")}catch(_:IllegalStateException){}
   assertEquals(0,second.requestCount)
  }}
 }
 @Test fun provider_errors_do_not_expose_response_or_key()=runTest {
  for(code in listOf(401,403,402,429,500))MockWebServer().use{server->
   server.start();server.enqueue(MockResponse().setResponseCode(code).setBody("private-credential-detail"))
   val c=ContextBroker("Paladino").build("hi",ExecutionMode.MAXX,emptyList())
   try{OpenRouterTransport(endpoint=server.url("/").toString()).generate(c,"test-model","synthetic",CloudConsent(c.digest,"test-model",Instant.now().plusSeconds(60),"zai"),CloudProvider.ZAI){};fail("Must reject HTTP error")}catch(e:IllegalStateException){assertFalse(e.message!!.contains("private-credential-detail"));assertFalse(e.message!!.contains("synthetic"))}
  }
 }
 @Test fun model_ids_cannot_be_urls_or_change_providers(){
  assertTrue(CloudProvider.OPENAI.validModel("gpt-4.1-mini"));assertTrue(CloudProvider.ZAI.validModel("glm-4.5-flash"))
  assertFalse(CloudProvider.OPENAI.validModel("https://example.com"));assertFalse(CloudProvider.ZAI.validModel("../secret"))
  assertFalse(CloudProvider.OPENROUTER.validModel("model"))
 }
}
