package ai.petologic.paladino
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaladinoUiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun save_requires_confirmation_and_memory_can_be_deleted(){
  val note="Emulator notebook ${System.currentTimeMillis()}"
  compose.onNodeWithText("What’s on your mind?").performTextInput("Remember that $note")
  compose.onNodeWithContentDescription("Send message").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("Keep this in memory?").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Save note").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("Saved to your private memory.").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Orchestration",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Memory").performClick()
  compose.onNodeWithText(note).assertIsDisplayed()
  compose.onNodeWithTag("delete:"+note).performScrollTo().performClick()
  compose.onNode(hasText("Delete note") and hasAnyAncestor(isDialog())).performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty()}
 }
 @Test fun settings_has_real_model_setup_and_provider_fields(){
  compose.onNodeWithText("Settings",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Model library").assertIsDisplayed()
  compose.onNodeWithText("Maxx · a little extra reach").performScrollTo().assertIsDisplayed()
 }
 @Test fun chat_drawer_creates_an_independent_conversation(){
  compose.onNodeWithContentDescription("Conversations").performClick()
  compose.onNodeWithText("New conversation").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("Small companion.\nA little more possible.").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("What’s on your mind?").assertIsDisplayed()
  compose.onNodeWithContentDescription("Conversations").assertIsDisplayed()
 }

}
