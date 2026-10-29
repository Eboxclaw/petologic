package ai.petologic.paladino

import androidx.test.platform.app.InstrumentationRegistry
import ai.petologic.paladino.skills.DeviceStatusReader
import org.junit.Test
import org.junit.Assert.*

/** Real battery/network reads on the emulator; envelope shape is the contract. */
class DeviceIntegrationTest {
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

 @Test fun battery_and_connectivity_return_ok_envelopes(){
  val battery=DeviceStatusReader.query(context,"battery")
  assertTrue(battery,battery.startsWith("OK|device.query|Battery at ")||battery.startsWith("ERROR|unavailable|"))
  val connectivity=DeviceStatusReader.query(context,"connectivity")
  assertTrue(connectivity,connectivity.startsWith("OK|device.query|"))
  val phone=DeviceStatusReader.query(context,"device")
  assertTrue(phone,phone.contains("· Android "))
  val bad=DeviceStatusReader.query(context,"airplane_mode")
  assertTrue(bad,bad.startsWith("ERROR|invalid_command|"))
 }
}
