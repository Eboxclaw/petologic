package ai.petologic.paladino
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

class RealAppConversationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun real_local_model_replies_through_chat_and_followup(){
  assumeTrue(InstrumentationRegistry.getArguments().getString("realModel")=="true")
  val app=compose.activity.application as PaladinoApplication
  runBlocking{app.ready.await();app.local.verify()}
  assertTrue(app.local.ready.value)
  compose.onNodeWithContentDescription("Conversations").performClick()
  compose.onNodeWithText("New conversation").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("How can I help?").fetchSemanticsNodes().isNotEmpty()}
  val id=app.sessionHub.active.value.sessionId
  val first="Reply with exactly: Blue shield."
  compose.onNodeWithText("What’s on your mind?").performTextInput(first)
  compose.onNodeWithContentDescription("Send message").performClick()
  compose.waitUntil(90000){runBlocking{app.memory.dao.allMessages(id).any{it.speaker=="assistant"}}}
  val answer=runBlocking{app.memory.dao.allMessages(id).last{it.speaker=="assistant"}.text}
  assertTrue("Actual model response: $answer",answer.contains("Blue shield",ignoreCase=true))
  val followup="What two words did I ask you to say?"
  compose.onNodeWithText("What’s on your mind?").performTextInput(followup)
  compose.onNodeWithContentDescription("Send message").performClick()
  compose.waitUntil(90000){runBlocking{app.memory.dao.allMessages(id).count{it.speaker=="assistant"}==2}}
  val transcript=runBlocking{app.memory.dao.allMessages(id)}
  val second=transcript.last().text
  assertTrue("Follow-up: $second",second.contains("Blue shield",ignoreCase=true))
  java.io.File(app.filesDir,"real-app-conversation.txt").writeText(transcript.joinToString("\n\n"){it.speaker+": "+it.text})
 }
}
