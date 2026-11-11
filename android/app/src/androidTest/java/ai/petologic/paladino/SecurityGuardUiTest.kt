package ai.petologic.paladino

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import ai.petologic.skills.security.guard.SecurityActivity
import org.junit.Rule
import org.junit.Test

class SecurityGuardUiTest {
 @get:Rule val ui=createAndroidComposeRule<SecurityActivity>()
 @Test fun manualScanNeedsNoModelAndShowsEvidence(){
  ui.onNodeWithTag("check_something").performScrollTo().performClick()
  ui.onNodeWithText("Message or URL").performTextInput("URGENT verify your account and bank details http://193.42.11.7")
  ui.onNodeWithText("Check text locally").performClick()
  // The scan lands on Activity, where the deterministic REVIEW title renders; the status banner
  // confirms the scan completed and was recorded without retaining the submitted text.
  ui.waitUntil(10000){ui.onAllNodesWithText("Review recommended").fetchSemanticsNodes().isNotEmpty()}
  ui.onAllNodesWithText("Review recommended").onFirst().assertExists()
  ui.onNodeWithText("Scan recorded. Review its evidence and limitations.").assertExists()
  ui.onAllNodesWithText("Local text and link patterns").onFirst().assertExists()
 }
 @Test fun visualScanExplainsItsLocalAndNonVerdictBoundary(){
  ui.onNodeWithTag("check_something").performScrollTo().performClick()
  ui.onNodeWithText("Screenshot or image").performClick()
  ui.onNodeWithText("Choose screenshot or image").assertExists().assertHasClickAction()
  ui.onNodeWithText("The selected image is resized in memory, inspected locally and not added to history. Visual observations are not a security verdict.").assertExists()
 }
}
