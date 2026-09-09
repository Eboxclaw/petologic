package ai.petologic.paladino

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import ai.petologic.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Exercises real model decisions through the production Chat surface, never testTurn. */
class RealToolConversationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @After fun clearObserver(){(compose.activity.application as PaladinoApplication).agent.let{it.responseObserver=null;it.toolResultObserver=null}}
 @Test fun greeting_and_model_selected_tools_produce_natural_language(){
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("realModel")=="true")
  val app=compose.activity.application as PaladinoApplication
  app.stopService(Intent(app,SpriteOverlayService::class.java))
  runBlocking{app.ready.await();app.local.verify();if(!app.local.ready.value)app.local.install()}
  assertTrue(app.local.ready.value)
  val evidence=java.io.File(app.filesDir,"real-tool-conversation.txt")
  evidence.writeText("LFM2.5-350M Q4_K_M; real UI; fresh sessions; no cloud.\n")
  val toolResults=mutableListOf<Pair<String,String>>()
  app.agent.responseObserver={evidence.appendText("\nRAW MODEL: $it\n")}
  app.agent.toolResultObserver={name,result->toolResults+=name to result;evidence.appendText("\nACTUAL TOOL RESULT $name: $result\n")}
  fun fresh():String{
   compose.onNodeWithContentDescription("Conversations").performClick()
   compose.onNodeWithText("New conversation").performClick()
   compose.waitUntil(10000){compose.onAllNodesWithText("How can I help?").fetchSemanticsNodes().isNotEmpty()}
   return app.sessionHub.active.value.sessionId
  }
  fun turn(id:String,request:String,approve:Boolean=false):Pair<String,List<ai.petologic.paladino.data.ExecutionEventRow>>{
   assertTrue("Must exercise model, not deterministic shortcut",RoutePolicy().route(request,ExecutionMode.TINY) is Route.Generate)
   val before=runBlocking{app.memory.dao.allMessages(id).count{it.speaker=="assistant"}}
   compose.onNodeWithText("What’s on your mind?").performTextInput(request)
   compose.onNodeWithContentDescription("Send message").performClick()
   var approved=false
   try{
    compose.waitUntil(90000){
     val state=app.sessionHub.active.value.ui.value
     if(approve&&!approved&&state.action!=null){
      assertTrue("No write before approval",runBlocking{app.memory.dao.notes(id)}.isEmpty())
      compose.onNodeWithText("Save note").performClick();approved=true
     }
     state.error!=null||(!approve&&state.action!=null)||runBlocking{app.memory.dao.allMessages(id).count{it.speaker=="assistant"}>before}
    }
   }finally{
    val messages=runBlocking{app.memory.dao.allMessages(id)}
    val events=runBlocking{app.memory.dao.events(id).first()}
    evidence.appendText("\nSESSION $id\n"+messages.joinToString("\n"){it.speaker+": "+it.text}+"\nEVENTS\n"+events.reversed().joinToString("\n"){it.type+": "+it.detail}+"\nERROR: "+app.sessionHub.active.value.ui.value.error+"\n")
   }
   assertNull("Unexpected write approval for a read-only or greeting request",if(approve)null else app.sessionHub.active.value.ui.value.action)
   assertNull(app.sessionHub.active.value.ui.value.error)
   val events=runBlocking{app.memory.dao.events(id).first()}
   val answer=runBlocking{app.memory.dao.allMessages(id).last{it.speaker=="assistant"}.text}
   assertTrue("Expected natural language: $answer",answer.length>15&&!answer.trimStart().startsWith("{"))
   if(approve)assertTrue("Model must request approval",approved)
   return answer to events
  }
  for(prompt in listOf("Olá, Gents, como é que estás hoje?","Bom dia, Paladino! Tudo bem?")){
   val greeting=turn(fresh(),prompt)
   assertFalse("Greeting must not call tools",greeting.second.any{it.type=="tool_call"})
   assertTrue(greeting.second.any{it.type=="inference"})
   assertNotEquals("Must answer, not echo the greeting",prompt,greeting.first)
   assertFalse("Regression: invented personal activities",listOf("estou em casa","uma refeição","pouco de sono","preparando").any{greeting.first.contains(it,ignoreCase=true)})
  }
  val id=fresh()
  val write=turn(id,"Podes registar numa nota que o meu código de teste é safira 742? Depois explica em português o que guardaste.",true)
  assertTrue("Model must choose notes_save",write.second.any{it.type=="tool_call"&&it.detail.startsWith("notes_save")})
  assertTrue("Write result must be observed",write.second.any{it.type=="tool_result"})
  assertTrue(runBlocking{app.memory.dao.notes(id)}.any{it.text.contains("742")})
  assertTrue("Final reply must use saved fact",write.first.contains("742"))
  val read=turn(id,"Consulta as minhas notas com a ferramenta de pesquisa e diz-me qual é o código de teste que ficou guardado.")
  assertTrue("Model must choose notes_search",read.second.any{it.type=="tool_call"&&it.detail.startsWith("notes_search")})
  assertTrue("Search must actually retrieve the saved fact",toolResults.any{it.first=="notes_search"&&it.second.contains("742")})
  assertTrue("Final reply must use retrieved fact",read.first.contains("742")&&!read.first.contains("não encontr",ignoreCase=true))
 }
}
