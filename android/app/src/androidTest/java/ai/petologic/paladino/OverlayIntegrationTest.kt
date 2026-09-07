package ai.petologic.paladino
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*
import kotlinx.coroutines.*

class OverlayIntegrationTest {
 @Test fun floating_sprite_survives_home_and_saves_a_real_model_reply(){
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("overlay")=="true")
  val instrumentation=InstrumentationRegistry.getInstrumentation();val app=instrumentation.targetContext.applicationContext as PaladinoApplication
  val device=UiDevice.getInstance(instrumentation)
  runBlocking{app.ready.await();app.local.verify()};assertTrue(app.local.ready.value)
  // Explicit emulator-only test grants. Production uses Android's permission screens.
  android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("appops set ai.petologic.paladino SYSTEM_ALERT_WINDOW allow")).use{it.readBytes()}
  android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ai.petologic.paladino android.permission.POST_NOTIFICATIONS")).use{it.readBytes()}
  val activity=ActivityScenario.launch(MainActivity::class.java)
  try{
   activity.onActivity{it.startForegroundService(Intent(it,SpriteOverlayService::class.java))}
   device.pressHome()
   assertTrue(device.wait(Until.hasObject(By.descStartsWith("Floating Paladino. Tap to chat")),10000))
   val sprite=device.findObject(By.descStartsWith("Floating Paladino. Tap to chat"))
   val initial=sprite.visibleBounds
   device.drag(initial.centerX(),initial.centerY(),if(initial.centerX()<device.displayWidth/2)device.displayWidth*4/5 else device.displayWidth/5,if(initial.centerY()<device.displayHeight/2)device.displayHeight*2/3 else device.displayHeight/3,30)
   assertTrue(device.wait(Until.hasObject(By.descStartsWith("Floating Paladino. Tap to chat")),5000))
   val moved=device.findObject(By.descStartsWith("Floating Paladino. Tap to chat")).visibleBounds
   assertTrue("Drag must move the Sprite",kotlin.math.abs(moved.centerX()-initial.centerX())>20||kotlin.math.abs(moved.centerY()-initial.centerY())>20)
   assertTrue("Sprite stays inside the screen",moved.left>=0&&moved.top>=0&&moved.right<=device.displayWidth&&moved.bottom<=device.displayHeight)
   app.stopService(Intent(app,SpriteOverlayService::class.java))
   runBlocking{withTimeout(5000){while(SpriteOverlayService.running.value)delay(50)}}
   android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -n ai.petologic.paladino/.MainActivity")).use{it.readBytes()}
   assertTrue(device.wait(Until.hasObject(By.text("PETOLOGIC")),5000))
   activity.onActivity{it.startForegroundService(Intent(it,SpriteOverlayService::class.java))}
   device.pressHome()
   assertTrue(device.wait(Until.hasObject(By.descStartsWith("Floating Paladino. Tap to chat")),10000))
   val restored=device.findObject(By.descStartsWith("Floating Paladino. Tap to chat")).visibleBounds
   assertTrue("Position must survive service restart",kotlin.math.abs(restored.centerX()-moved.centerX())<8&&kotlin.math.abs(restored.centerY()-moved.centerY())<8)
   device.takeScreenshot(java.io.File(app.filesDir,"overlay-home.png"))
   device.findObject(By.descStartsWith("Floating Paladino. Tap to chat")).click()
   device.wait(Until.hasObject(By.text("New chat")),5000)
   val previous=app.sessionHub.active.value.sessionId
   device.findObject(By.text("New chat")).click()
   runBlocking{withTimeout(10000){while(app.sessionHub.active.value.sessionId==previous)delay(50)}}
   val id=app.sessionHub.active.value.sessionId
   device.findObject(By.clazz(android.widget.EditText::class.java)).text="Reply with exactly: Golden star."
   device.findObject(By.desc("Send")).click()
   runBlocking{withTimeout(90000){while(app.memory.dao.allMessages(id).none{it.speaker=="assistant"})delay(100)}}
   assertTrue("Real overlay model response must be visible",device.wait(Until.hasObject(By.textContains("Golden star")),5000))
   val transcript=runBlocking{app.memory.dao.allMessages(id)}
   assertTrue(transcript.any{it.speaker=="assistant"&&it.text.contains("Golden star",ignoreCase=true)})
   java.io.File(app.filesDir,"real-overlay-conversation.txt").writeText(transcript.joinToString("\n\n"){it.speaker+": "+it.text})
   device.takeScreenshot(java.io.File(app.filesDir,"overlay-reply.png"))
   device.findObject(By.desc("Collapse")).click()
   assertTrue(device.wait(Until.hasObject(By.descStartsWith("Floating Paladino. Tap to chat")),5000))
  }finally{app.stopService(Intent(app,SpriteOverlayService::class.java));activity.close()}
 }
}
