package ai.petologic.skills.security

import org.junit.Test
import org.junit.Assert.*

class SecurityCommandTest {
 @Test fun documented_grammars_parse(){
  assertEquals(SecurityCommand("query","device_status",""),parseSecurityCommand("device_status"))
  assertEquals(SecurityCommand("query","app","com.example"),parseSecurityCommand("app | com.example"))
  assertEquals(SecurityCommand("query","apps","suspicious"),parseSecurityCommand("apps | suspicious"))
  assertEquals(SecurityCommand("scan","url","https://a.b/c"),parseSecurityCommand("url | https://a.b/c"))
  assertEquals(SecurityCommand("scan","text","URGENT click here"),parseSecurityCommand("text | URGENT click here"))
  assertEquals(SecurityCommand("scan","installed_apps",""),parseSecurityCommand("installed_apps"))
  assertEquals(SecurityCommand("action","open_app_settings","com.example"),parseSecurityCommand("open_app_settings | com.example"))
  assertEquals(SecurityCommand("action","uninstall_handoff","com.example"),parseSecurityCommand("uninstall_handoff | com.example"))
 }
 @Test fun unknown_or_malformed_commands_are_rejected(){
  assertTrue(runCatching{parseSecurityCommand("format_device")}.isFailure)
  assertTrue(runCatching{parseSecurityCommand("app |  ")}.isFailure)
  assertTrue(runCatching{parseSecurityCommand("apps | everything")}.isFailure)
  assertTrue(runCatching{parseSecurityCommand("url |")}.isFailure)
 }
 @Test fun envelope_has_one_stable_shape(){
  assertTrue(ToolResultEnvelope.ok("security.scan","v").startsWith("OK|security.scan|"))
  assertTrue(ToolResultEnvelope.error("no_data","x").startsWith("ERROR|no_data|"))
 }
}
