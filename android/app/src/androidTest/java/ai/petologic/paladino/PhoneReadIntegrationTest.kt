package ai.petologic.paladino
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PhoneReadIntegrationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun read_actual_phone_services_and_weather_opt_in(){
  val app=compose.activity.application as PaladinoApplication
  val reads=PhoneReads(app)
  assertTrue(runBlocking{reads.read(PhoneRead.CLOCK,false,false)}.startsWith("Phone clock:"))
  val alarm=runBlocking{reads.read(PhoneRead.ALARM,false,false)}
  assertTrue(alarm.contains("alarm"))
  val calendar=runBlocking{reads.read(PhoneRead.CALENDAR,false,false)}
  assertTrue(calendar.contains("Calendar access is off")||calendar.contains("device calendar"))
  val alreadyAllowed=app.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)==android.content.pm.PackageManager.PERMISSION_GRANTED
  try{
   InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName,android.Manifest.permission.READ_CALENDAR)
   assertTrue(runBlocking{reads.read(PhoneRead.CALENDAR,false,false)}.contains("device calendar"))
  }finally{/* Emulator grant restored by the test runner after instrumentation finishes. */}
  assertTrue(runBlocking{reads.read(PhoneRead.WEATHER,false,false)}.startsWith("Set your weather city"))
  assertTrue(runBlocking{reads.read(PhoneRead.EMAIL,false,false)}.contains("not connected"))
  if(InstrumentationRegistry.getArguments().getString("liveWeather")=="true"){
   val prefs=app.getSharedPreferences("phone_reads",0);val city=prefs.getString("city",null);val allowed=prefs.getBoolean("weather",false)
   try{prefs.edit().putString("city","Lisbon").putBoolean("weather",true).commit()
    val result=runBlocking{reads.read(PhoneRead.WEATHER,false,true)}
    assertTrue(result.contains("°C"));assertTrue(result.contains("Open-Meteo"));File(app.filesDir,"weather-read.txt").writeText(result)
   }finally{prefs.edit().putString("city",city).putBoolean("weather",allowed).commit()}
  }
 }
 @Test fun bubble_actions_remain_readable_and_permission_help_opens(){
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  compose.onNodeWithContentDescription("Settings",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("Quick actions").performClick()
  compose.onNodeWithText("Message Paladino").performClick()
  compose.onNodeWithText("Full conversation").assertIsDisplayed()
  compose.onNode(hasText("New conversation") and hasAnyAncestor(isDialog())).assertIsDisplayed()
  val app=compose.activity.application as PaladinoApplication
  device.takeScreenshot(File(app.filesDir,"bubble-actions.png"))
  compose.onNodeWithText("Close bubble").performClick()
  compose.onNodeWithText("Manage Sprite & Widget").performScrollTo().performClick()
  compose.onNodeWithText("Permission blocked?").performScrollTo().performClick()
  compose.onNodeWithText("Open Android app info").performScrollTo().assertIsDisplayed()
  device.takeScreenshot(File(app.filesDir,"overlay-permission-help.png"))
 }
 @Test fun clock_request_in_chat_runs_a_read_tool(){
  compose.onNodeWithContentDescription("New conversation").performClick()
  compose.onNodeWithText("What’s on your mind?").performTextInput("What time is it?")
  compose.onNodeWithContentDescription("Send message").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("Phone clock:",substring=true).fetchSemanticsNodes().isNotEmpty()}
  val app=compose.activity.application as PaladinoApplication
  UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(app.filesDir,"phone-clock-chat.png"))
 }
}
