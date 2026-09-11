package ai.petologic.paladino.runtime
import org.junit.Test
import org.junit.Assert.*
class LfmToolCallParserTest {
 private val allowed=setOf("notes_search","notes_save")
 @Test fun structured_call_and_legacy_adapter_preserve_arguments(){
  assertEquals("notes_save" to "safira 742",LfmToolCallParser.parse("""{"name":"notes_save","arguments":{"argument":"safira 742"}}""",allowed))
  assertEquals("notes_search" to "bike",LfmToolCallParser.parse("""{"tool":"notes_search","argument":"bike"}""",allowed))
  assertEquals("notes_search" to "bike",LfmToolCallParser.parse("""<|tool_call_start|>[{"name":"notes_search","arguments":{"argument":"bike"}}]<|tool_call_end|>I will check.""",allowed))
 }
 @Test fun documented_pythonic_format_is_parsed_as_data_only(){
  assertEquals("notes_save" to "safira 742",LfmToolCallParser.parse("""<|tool_call_start|>[notes_save(argument="safira 742")]<|tool_call_end|>""",allowed))
  assertEquals("notes_search" to "bike",LfmToolCallParser.parse("""[notes_search(argument='bike')]""",allowed))
  assertEquals("notes_search" to "safira 742",LfmToolCallParser.parse("""notes_search("safira 742")""",allowed))
 }
 @Test fun allowlist_is_derived_from_the_registry_not_a_literal(){
  val wider=allowed+"security_scan"
  assertEquals("security_scan" to "url | https://x.test",LfmToolCallParser.parse("""security_scan(argument="url | https://x.test")""",wider))
  assertTrue(looksLikeToolCall("security_scan(\"url | https://x.test\")"))
  // The same syntax fails the moment the tool is not registered this turn.
  assertTrue(runCatching{LfmToolCallParser.parse("""security_scan(argument="url")""",allowed)}.isFailure)
  assertTrue(runCatching{LfmToolCallParser.parse("""notes_search(argument="bike")""",emptySet())}.isFailure)
 }
 @Test fun bare_typed_call_is_data_not_executable_code(){
  assertTrue(looksLikeToolCall("notes_search(\"safira 742\")"))
  listOf("notes_search(other())","notes_search(\"x\"); notes_save(\"y\")","notes_search(\"x\", \"y\")","__import__(\"os\")").forEach{
   assertTrue("Must reject $it",runCatching{LfmToolCallParser.parse(it,allowed)}.isFailure)
  }
 }
 @Test fun malformed_or_unauthorized_calls_never_parse(){
  listOf(
   """{"name":"shell","arguments":{"argument":"id"}}""",
   """{"name":"notes_save","arguments":{"argument":"x","extra":"y"}}""",
   """{"name":"notes_save","arguments":{"argument":7}}""",
   """{"name":"notes_save","arguments":{"argument":""}}""",
   """{"tool":{"tool":"notes_save"},"argument":"x"}""",
   """{"tool":"notes_save","argument":"x"} Already saved.""",
   """[{"tool":"notes_save","argument":"x"},{"tool":"notes_save","argument":"y"}]""",
   """<|tool_call_start|>[notes_save(argument=__import__("os").system("id"))]<|tool_call_end|>"""
  ).forEach{assertTrue("Must reject $it",runCatching{LfmToolCallParser.parse(it,allowed)}.isFailure)}
 }
}
