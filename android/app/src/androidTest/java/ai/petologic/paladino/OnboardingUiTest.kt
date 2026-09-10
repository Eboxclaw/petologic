package ai.petologic.paladino

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.File

class OnboardingUiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun missing_model_keeps_message_until_explicit_continue(){
  val app=compose.activity.application as PaladinoApplication
  runBlocking{withTimeout(30000){app.modelsReady.await()}}
  val original=app.modelLibrary.installed.value
  Assume.assumeTrue("Uses an existing verified local model to exercise resume", "lfm350" in original)
  compose.onNodeWithContentDescription("New conversation").performClick()
  compose.waitForIdle()
  val controller=app.sessionHub.active.value
  val request="Hello, Paladino! How are you today?"
  try{
   // Hide catalog availability only. No user model files are removed.
   app.modelLibrary.installed.value=original-"lfm350"
   compose.onNodeWithText("What’s on your mind?").performTextInput(request)
   compose.onNodeWithContentDescription("Send message").performClick()
   compose.waitUntil(10000){controller.ui.value.pendingModelMessage==request}
   compose.onNodeWithText("Give Paladino a local brain").assertIsDisplayed()
   compose.onNodeWithText("Install model").assertIsDisplayed()
   assertTrue(runBlocking{app.memory.dao.allMessages(controller.sessionId)}.isEmpty())
   UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(app.filesDir,"model-setup-card.png"))
   compose.onNodeWithText("Already have it? Import a model").performClick()
   compose.onNodeWithText("App updates").assertIsDisplayed()
   compose.onNodeWithContentDescription("Chat",useUnmergedTree=true).performClick()
   compose.onNodeWithText("Give Paladino a local brain").assertIsDisplayed()
   app.modelLibrary.installed.value=original
   compose.waitUntil(10000){compose.onAllNodesWithText("Continue conversation").fetchSemanticsNodes().isNotEmpty()}
   assertTrue(runBlocking{app.memory.dao.allMessages(controller.sessionId)}.isEmpty())
   compose.onNodeWithText("Continue conversation").performClick()
   compose.waitUntil(60000){runBlocking{app.memory.dao.allMessages(controller.sessionId)}.any{it.speaker=="assistant"}}
   assertEquals(1,runBlocking{app.memory.dao.allMessages(controller.sessionId)}.count{it.speaker=="user"&&it.text==request})
   assertNull(controller.ui.value.pendingModelMessage)
  }finally{app.modelLibrary.installed.value=original;controller.discardModelMessage()}
 }
 @Test fun floating_sprite_opens_the_same_model_setup(){
  val app=compose.activity.application as PaladinoApplication
  runBlocking{withTimeout(30000){app.modelsReady.await()}}
  val original=app.modelLibrary.installed.value
  compose.onNodeWithContentDescription("New conversation").performClick()
  compose.waitForIdle()
  val controller=app.sessionHub.active.value
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  Assume.assumeTrue(android.provider.Settings.canDrawOverlays(app))
  try{
   app.modelLibrary.installed.value=original-"lfm350"
   compose.activity.startForegroundService(android.content.Intent(app,SpriteOverlayService::class.java))
   device.pressHome()
   assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.descStartsWith("Floating Paladino. Tap to chat")),10000))
   device.findObject(androidx.test.uiautomator.By.descStartsWith("Floating Paladino. Tap to chat")).click()
   assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.clazz(android.widget.EditText::class.java)),5000))
   device.findObject(androidx.test.uiautomator.By.clazz(android.widget.EditText::class.java)).text="Hello from the Sprite"
   device.findObject(androidx.test.uiautomator.By.desc("Send")).click()
   assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Set up model")),10000))
   device.findObject(androidx.test.uiautomator.By.text("Set up model")).click()
   compose.waitUntil(10000){compose.onAllNodesWithText("Give Paladino a local brain").fetchSemanticsNodes().isNotEmpty()}
   assertEquals("Hello from the Sprite",controller.ui.value.pendingModelMessage)
   assertTrue(runBlocking{app.memory.dao.allMessages(controller.sessionId)}.isEmpty())
  }finally{app.modelLibrary.installed.value=original;controller.discardModelMessage();app.stopService(android.content.Intent(app,SpriteOverlayService::class.java))}
 }
 @Test fun update_button_opens_only_official_downloads(){
  var opened:String?=null
  compose.runOnUiThread{compose.activity.setContent{
   CompositionLocalProvider(LocalUriHandler provides object:UriHandler{override fun openUri(uri:String){opened=uri}}){MaterialTheme(colorScheme=PetColors){AppUpdateCard()}}
  }}
  compose.onNodeWithText("PETOLOGIC · ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
  compose.onNodeWithText("Update app").performClick()
  assertEquals("https://github.com/Eboxclaw/petologic-downloads/releases",opened)
 }
}
