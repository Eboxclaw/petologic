package ai.petologic.paladino
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.*

class ProviderUiInspectionTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun provider_picker_and_sprite_fab_are_accessible(){
  val app=compose.activity.application as PaladinoApplication
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  compose.onNodeWithText("Settings",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("Quick actions").performClick()
  compose.onNodeWithText("Message Paladino").assertIsDisplayed()
  device.takeScreenshot(java.io.File(app.filesDir,"sprite-fab-menu.png"))
  compose.onNodeWithText("Message Paladino").performClick()
  compose.onNodeWithText("Message Paladino").assertIsDisplayed()
  device.takeScreenshot(java.io.File(app.filesDir,"sprite-inapp-composer.png"))
  device.pressBack()
  compose.onNodeWithText("OpenAI",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("Manage OpenAI API keys").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Z.ai",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("Manage Z.ai API keys").performScrollTo().assertIsDisplayed()
 }
}
