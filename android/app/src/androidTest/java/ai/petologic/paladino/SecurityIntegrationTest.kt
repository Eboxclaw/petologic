package ai.petologic.paladino

import androidx.test.platform.app.InstrumentationRegistry
import ai.petologic.paladino.data.*
import ai.petologic.skills.security.android.AppInspector
import ai.petologic.skills.security.android.SecurityFacade
import ai.petologic.skills.security.models.SecurityVerdict
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/** Real PackageManager queries on the emulator; verdicts are asserted on shape, not fixed app inventories. */
class SecurityIntegrationTest {
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

 @Test fun own_app_inspects_with_real_signals(){
  val finding=AppInspector.inspect(context,context.packageName)
  assertEquals(context.packageName,finding.subject.removePrefix("App "))
  assertTrue(finding.verdict!=SecurityVerdict.UNKNOWN) // our own package must be visible to us
 }
 @Test fun unknown_package_is_unknown_never_low(){
  val finding=AppInspector.inspect(context,"ai.petologic.does.not.exist")
  assertEquals(SecurityVerdict.UNKNOWN,finding.verdict)
  assertTrue(finding.render().startsWith("App ai.petologic.does.not.exist verdict: UNKNOWN"))
 }
 @Test fun query_and_scan_facades_return_envelopes(){
  val device=SecurityFacade.query(context,"device_status")
  assertTrue(device,device.startsWith("OK|security.query|"))
  val url=SecurityFacade.scan(context,"url | http://193.42.11.7/login")
  assertTrue(url,url.contains("OK|security.scan|")&&url.contains("HIGH_ATTENTION"))
  val bad=SecurityFacade.query(context,"format_device")
  assertTrue(bad,bad.startsWith("ERROR|invalid_command|"))
 }
}
