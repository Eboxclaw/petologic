package ai.petologic.paladino

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*

class VisualNavigationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun new_chat_header_and_overlay_return_to_correct_chat(){
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("overlay")=="true")
  val app=compose.activity.application as PaladinoApplication
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  val previous=app.sessionHub.active.value.sessionId
  compose.onNodeWithContentDescription("New conversation").performClick()
  compose.waitUntil(10000){app.sessionHub.active.value.sessionId!=previous}
  compose.onNodeWithText("How can I help?").assertIsDisplayed()
  device.takeScreenshot(java.io.File(app.filesDir,"visual-chat-empty.png"))
  val session=app.sessionHub.active.value.sessionId
  compose.onNodeWithText("Settings",useUnmergedTree=true).performClick()
  device.takeScreenshot(java.io.File(app.filesDir,"visual-settings.png"))
  compose.activity.startForegroundService(Intent(compose.activity,SpriteOverlayService::class.java))
  try{
   device.pressHome()
   assertTrue(device.wait(Until.hasObject(By.descStartsWith("Floating Paladino. Tap to chat")),10000))
   device.findObject(By.descStartsWith("Floating Paladino. Tap to chat")).click()
   assertTrue(device.wait(Until.hasObject(By.text("Full chat")),5000))
   device.findObject(By.text("Full chat")).click()
   compose.waitUntil(10000){compose.onAllNodesWithText("How can I help?").fetchSemanticsNodes().isNotEmpty()}
   assertEquals(session,app.sessionHub.active.value.sessionId)
  }finally{app.stopService(Intent(app,SpriteOverlayService::class.java))}
 }
}
