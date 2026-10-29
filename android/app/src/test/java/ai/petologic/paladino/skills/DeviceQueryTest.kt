package ai.petologic.paladino.skills
import org.junit.Test
import org.junit.Assert.*

class DeviceQueryTest {
 @Test fun documented_subjects_parse(){
  assertEquals(DeviceQuery("battery"),parseDeviceQuery("battery"))
  assertEquals(DeviceQuery("connectivity"),parseDeviceQuery(" Connectivity "))
  assertEquals(DeviceQuery("device"),parseDeviceQuery("device"))
 }
 @Test fun unknown_subjects_are_rejected(){
  assertTrue(runCatching{parseDeviceQuery("airplane_mode")}.isFailure)
  assertTrue(runCatching{parseDeviceQuery("")}.isFailure)
 }
 @Test fun battery_formatter_reports_level_and_charging(){
  val charging=android.os.BatteryManager.BATTERY_STATUS_CHARGING
  val discharging=android.os.BatteryManager.BATTERY_STATUS_DISCHARGING
  val full=android.os.BatteryManager.BATTERY_STATUS_FULL
  assertTrue(DeviceStatusFormat.battery(76,charging).contains("OK|device.query|Battery at 76% and charging."))
  assertTrue(DeviceStatusFormat.battery(90,full).contains("Battery at 90% and charging."))
  val dischargingText=DeviceStatusFormat.battery(40,discharging)
  assertTrue(dischargingText.contains("Battery at 40%"));assertFalse(dischargingText.contains("charging"))
  assertTrue(DeviceStatusFormat.battery(-1,1).startsWith("ERROR|unavailable|"))
 }
 @Test fun connectivity_formatter_names_transport_and_flags(){
  assertTrue(DeviceStatusFormat.connectivity(false,false,false,false,false).contains("No active network"))
  val wifi=DeviceStatusFormat.connectivity(true,true,false,false,false)
  assertTrue(wifi.contains("Online via Wi-Fi")&&!wifi.contains("metered")&&!wifi.contains("VPN"))
  val cell=DeviceStatusFormat.connectivity(true,false,true,true,true)
  assertTrue(cell.contains("mobile data (metered)")&&cell.contains("a VPN is active"))
 }
 @Test fun phone_formatter_identifies_the_device(){
  assertTrue(DeviceStatusFormat.phone("Google","Pixel","16","2026-09-01").contains("Google Pixel · Android 16 · security patch 2026-09-01"))
 }
}
