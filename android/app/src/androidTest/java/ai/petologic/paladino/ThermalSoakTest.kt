package ai.petologic.paladino

import ai.petologic.core.SessionOptions
import ai.petologic.paladino.inference.LocalGenerationRequest
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 16 stage 4: 30-minute thermal/stability soak, one row per generation. Meaningful only on
 * real hardware — run on the phone over USB with
 * -Pandroid.testInstrumentationRunnerArguments.thermal=true. Emulator numbers prove stability
 * only. Results land in files/thermal-<engine>.json and logcat THERMAL.
 */
@RunWith(AndroidJUnit4::class)
class ThermalSoakTest{
 @Test fun thirtyMinuteSoak()=runBlocking{
  val args=InstrumentationRegistry.getArguments()
  assumeTrue("opt in with -Pandroid.testInstrumentationRunnerArguments.thermal=true",args.getString("thermal")=="true")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PaladinoApplication
  app.local.verify()
  val backend=app.inference
  val engine=BuildConfig.FLAVOR
  val deadline=System.currentTimeMillis()+30*60_000L
  val options=SessionOptions(contextTokens=4096,outputTokens=128,hopTimeoutSeconds=180,totalTimeoutSeconds=600)
  val rows=JSONArray();var n=0
  while(System.currentTimeMillis()<deadline){
   val begin=System.currentTimeMillis()
   val text=runCatching{withTimeout(600_000L){
    backend.generate(LocalGenerationRequest("You are a stability probe.",
     "Request $n: write four varied sentences about the sea.","lfm350",options)){}
   }}
   val row=JSONObject().put("n",n).put("wallMs",System.currentTimeMillis()-begin)
    .put("ok",text.isSuccess).put("err",text.exceptionOrNull()?.message?:"")
    .put("pssKb",Debug.getPss())
   // Battery temperature (tenths of °C) comes from the ACTION_BATTERY_CHANGED sticky broadcast.
   val intent=app.registerReceiver(null,android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
   val tenths=intent?.getIntExtra("temperature",Int.MIN_VALUE)?:Int.MIN_VALUE
   row.put("battTempC",if(tenths==Int.MIN_VALUE)null else tenths/10.0)
   rows.put(row);Log.println(Log.WARN,"THERMAL",row.toString())
   n++
  }
  val dir=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)?:return@runBlocking
  java.io.File(dir,"thermal-$engine-${System.currentTimeMillis()}.json").writeText(rows.toString(1))
  Log.println(Log.WARN,"THERMAL","done: $n generations over ${rows.length()} rows")
 }
}
