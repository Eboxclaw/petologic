package ai.petologic.paladino
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaladinoUiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Before fun start_with_a_clean_conversation(){
  compose.onNodeWithContentDescription("Conversations").performClick()
  compose.onNodeWithText("New conversation").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("How can I help?").fetchSemanticsNodes().isNotEmpty()}
 }
 @Test fun save_requires_confirmation_and_memory_can_be_deleted(){
  val note="Emulator notebook ${System.currentTimeMillis()}"
  compose.onNodeWithText("What’s on your mind?").performTextInput("Remember that $note")
  compose.onNodeWithContentDescription("Send message").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("Keep this in memory?").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Save note").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("Saved to your private memory.").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Controls",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Memory").performClick()
  compose.onNodeWithText(note).assertIsDisplayed()
  compose.onNodeWithTag("delete:"+note).performScrollTo().performClick()
  compose.onNode(hasText("Delete note") and hasAnyAncestor(isDialog())).performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty()}
 }
 @Test fun settings_has_real_model_setup_and_provider_fields(){
  compose.onNodeWithText("Settings",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Sprite & Widget").assertIsDisplayed()
  compose.onNodeWithText("Model library").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Maxx · a little extra reach").performScrollTo().assertIsDisplayed()
 }
 @Test fun chat_drawer_creates_an_independent_conversation(){
  compose.onNodeWithContentDescription("Conversations").performClick()
  compose.onNodeWithText("New conversation").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("How can I help?").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("What’s on your mind?").assertIsDisplayed()
  compose.onNodeWithContentDescription("Conversations").assertIsDisplayed()
 }

 @Test fun sprite_manager_exposes_real_controls(){
  compose.onNodeWithText("Settings",useUnmergedTree=true).performClick()
  compose.onNodeWithText("Manage Sprite & Widget").performScrollTo().performClick()
  compose.onNodeWithText("Show in-app Sprite").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Add to home screen").performScrollTo().assertIsDisplayed()
 }

 @Test fun sprite_chat_is_saved_in_the_app_conversation(){
  compose.onNodeWithText("Settings",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("Quick actions").performClick()
  compose.onNodeWithText("Message Paladino").performClick()
  val request="Find bubblefixture${System.currentTimeMillis()}"
  compose.onNode(hasText("New conversation") and hasAnyAncestor(isDialog())).performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("I’m here. What would you like to do?").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Message Paladino").performTextInput(request)
  compose.onNodeWithText("Send to Paladino").performScrollTo().performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("No matching notes yet. Try a word from the note, or save one with ‘Remember that…’.").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Full conversation").performScrollTo().performClick()
  compose.onNodeWithText(request).assertExists()
 }

}
