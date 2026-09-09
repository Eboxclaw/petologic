package ai.petologic.paladino

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import ai.petologic.core.ExecutionMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Identical prompts and isolated session history on both real production surfaces. */
class PairedChatSpriteTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun compare_five_prompts_in_chat_and_overlay(){
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("pairedUi")=="true")
  val inst=InstrumentationRegistry.getInstrumentation()
  val app=compose.activity.application as PaladinoApplication
  val device=UiDevice.getInstance(inst)
  val evidence=java.io.File(app.filesDir,"paired-chat-sprite.txt")
  evidence.writeText("TINY LFM2.5-350M Q4_K_M; same 5 prompts; one fresh session per surface; history preserved within each group.\n")
  runBlocking{app.ready.await();app.local.verify()};assertTrue(app.local.ready.value)
  fun shell(cmd:String){android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}}
  fun openSprite(){assertTrue(device.wait(Until.hasObject(By.descStartsWith("Floating Paladino. Tap to chat")),10000));device.findObject(By.descStartsWith("Floating Paladino. Tap to chat")).click();assertTrue(device.wait(Until.hasObject(By.text("New chat")),5000))}
  val issues=mutableListOf<String>()
  val prompts=listOf("Olá, Gents, como é que estás hoje?","Hello, Paladino! How are you today?","Resume numa frase: A reunião começa às dez e termina às onze.","Podes registar numa nota que o meu código de teste é safira 742? Depois explica em português o que guardaste.","Consulta as minhas notas com a ferramenta de pesquisa e diz-me qual é o código de teste que ficou guardado.")
  app.agent.responseObserver={evidence.appendText("RAW MODEL: $it\n")}
  app.agent.toolResultObserver={name,result->evidence.appendText("ACTUAL TOOL $name: $result\n")}
  try{
   app.stopService(Intent(app,SpriteOverlayService::class.java))
   for(surface in listOf("app","sprite")){
    if(surface=="app"){
     compose.onNodeWithContentDescription("Conversations").performClick();compose.onNodeWithText("New conversation").performClick()
     compose.waitUntil(10000){compose.onAllNodesWithText("How can I help?").fetchSemanticsNodes().isNotEmpty()}
    }else{
     shell("appops set ai.petologic.paladino SYSTEM_ALERT_WINDOW allow")
     shell("pm grant ai.petologic.paladino android.permission.POST_NOTIFICATIONS")
     compose.runOnUiThread{compose.activity.startForegroundService(Intent(compose.activity,SpriteOverlayService::class.java))}
     device.pressHome();openSprite()
     val previous=app.sessionHub.active.value.sessionId
     device.findObject(By.text("New chat")).click()
     runBlocking{withTimeout(10000){while(app.sessionHub.active.value.sessionId==previous)delay(50)}}
    }
    val controller=app.sessionHub.active.value
    compose.runOnUiThread{controller.mode(ExecutionMode.TINY)}
    val id=controller.sessionId
    evidence.appendText("\nSURFACE $surface SESSION $id\n")
    for((index,prompt) in prompts.withIndex()){
     val before=runBlocking{app.memory.dao.allMessages(id).size}
     val started=System.currentTimeMillis();var approved=false;var unexpectedApproval=false
     evidence.appendText("\nQUESTION ${index+1}: $prompt\n")
     if(surface=="app"){
      compose.onNodeWithText("What’s on your mind?").performTextInput(prompt);compose.onNodeWithContentDescription("Send message").performClick()
     }else{
      device.findObject(By.clazz(android.widget.EditText::class.java)).text=prompt
      device.findObject(By.desc("Send")).click()
     }
     runBlocking{withTimeout(180000){
      while(true){
       val state=controller.ui.value
       if(state.action!=null&&!approved&&!unexpectedApproval){
        if(surface=="sprite"){device.findObject(By.text("Open to approve")).click();assertTrue(device.wait(Until.hasObject(By.text("Save note")),10000))}
        if(index==3){compose.onNodeWithText("Save note").performClick();approved=true}
        else{unexpectedApproval=true;compose.runOnUiThread{controller.denyAction()}}
       }
       val rows=app.memory.dao.allMessages(id)
       if(state.error!=null||(!state.busy&&rows.drop(before).any{it.speaker=="assistant"}))break
       delay(100)
      }
     }}
     if(surface=="sprite"&&(approved||unexpectedApproval)){device.pressHome();openSprite()}
     val rows=runBlocking{app.memory.dao.allMessages(id)}
     val answer=rows.drop(before).lastOrNull{it.speaker=="assistant"}?.text
     evidence.appendText("ANSWER: $answer\nERROR: ${controller.ui.value.error}\nAPPROVED: $approved\nUNEXPECTED_APPROVAL: $unexpectedApproval\nELAPSED_MS: ${System.currentTimeMillis()-started}\n")
     val turnEvents=runBlocking{app.memory.dao.events(id).first()}.filter{it.createdAt>=started}
     evidence.appendText("EVENTS: "+turnEvents.joinToString(" | "){it.type+":"+it.detail}+"\n")
     if(controller.ui.value.error!=null)issues+="$surface Q${index+1}: ${controller.ui.value.error}"
     if(index<3&&turnEvents.any{it.type=="tool_call"})issues+="$surface Q${index+1}: unnecessary tool"
     if(index==1&&answer?.startsWith("Hello")==false)issues+="$surface Q2: English reply missing"
     if(index==3&&(!approved||turnEvents.none{it.type=="tool_call"&&it.detail.startsWith("notes_save")}))issues+="$surface Q4: actual approved save missing"
     if(index==4&&turnEvents.none{it.type=="tool_call"&&it.detail.startsWith("notes_search")})issues+="$surface Q5: actual search missing"

     if(surface=="app")compose.waitForIdle() else {
      device.wait(Until.hasObject(By.desc("Send")),5000)
      answer?.takeIf{it.length>=20}?.let{device.wait(Until.hasObject(By.textContains(it.take(20))),5000)}
     }
     device.waitForIdle(2000)
     device.takeScreenshot(java.io.File(app.filesDir,"paired-$surface-${index+1}.png"))
     if(controller.ui.value.error!=null)compose.runOnUiThread{controller.clearError()}
    }
    assertEquals("All five turns must persist",5,runBlocking{app.memory.dao.allMessages(id)}.count{it.speaker=="user"})
   }
   evidence.appendText("\nISSUES: $issues\n")
   assertTrue(issues.joinToString("; "),issues.isEmpty())
  }finally{app.agent.responseObserver=null;app.agent.toolResultObserver=null;app.stopService(Intent(app,SpriteOverlayService::class.java))}
 }
}
