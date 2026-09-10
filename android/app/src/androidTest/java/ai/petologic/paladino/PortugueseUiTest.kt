package ai.petologic.paladino
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*

class PortugueseUiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun portuguese_chat_settings_and_overlay_are_usable(){
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("ptUi")=="true")
  val app=compose.activity.application as PaladinoApplication
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  compose.onNodeWithContentDescription("Nova conversa").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("Como posso ajudar?").fetchSemanticsNodes().isNotEmpty()}
  device.takeScreenshot(java.io.File(app.filesDir,"pt-chat.png"))
  compose.onNodeWithContentDescription("Definições",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Idioma").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Alterar idioma da app").performScrollTo().assertIsDisplayed()
  compose.onNodeWithContentDescription("Ações rápidas").performClick()
  compose.onNodeWithText("Mensagem ao Paladino").assertIsDisplayed()
  device.takeScreenshot(java.io.File(app.filesDir,"pt-menu.png"))
  device.pressBack()
  compose.activity.startForegroundService(Intent(compose.activity,SpriteOverlayService::class.java))
  try{
   device.pressHome()
   assertTrue(device.wait(Until.hasObject(By.descStartsWith("Paladino flutuante. Toca para conversar")),10000))
   device.findObject(By.descStartsWith("Paladino flutuante. Toca para conversar")).click()
   assertTrue(device.wait(Until.hasObject(By.text("Nova conversa")),5000))
   device.takeScreenshot(java.io.File(app.filesDir,"pt-overlay.png"))
   val full=device.findObject(By.text("Conversa completa"))?:device.findObject(By.scrollable(true)).scrollUntil(Direction.DOWN,Until.findObject(By.text("Conversa completa")))
   assertNotNull(full);full!!.click()
   compose.waitUntil(10000){compose.onAllNodesWithText("Como posso ajudar?").fetchSemanticsNodes().isNotEmpty()}
  }finally{app.stopService(Intent(app,SpriteOverlayService::class.java))}
 }
}
