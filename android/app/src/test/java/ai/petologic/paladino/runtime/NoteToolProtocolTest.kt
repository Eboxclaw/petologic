package ai.petologic.paladino.runtime
import org.junit.Test
import org.junit.Assert.*
class NoteToolProtocolTest {
 @Test fun structured_call_and_legacy_adapter_preserve_arguments(){
  assertEquals("notes_save" to "safira 742",parseNoteToolCall("""{"name":"notes_save","arguments":{"argument":"safira 742"}}"""))
  assertEquals("notes_search" to "bike",parseNoteToolCall("""{"tool":"notes_search","argument":"bike"}"""))
  assertEquals("notes_search" to "bike",parseNoteToolCall("""<|tool_call_start|>[{"name":"notes_search","arguments":{"argument":"bike"}}]<|tool_call_end|>I will check."""))
 }
 @Test fun documented_pythonic_format_is_parsed_as_data_only(){
  assertEquals("notes_save" to "safira 742",parseNoteToolCall("""<|tool_call_start|>[notes_save(argument="safira 742")]<|tool_call_end|>"""))
  assertEquals("notes_search" to "bike",parseNoteToolCall("""[notes_search(argument='bike')]"""))
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
  ).forEach{assertTrue("Must reject $it",runCatching{parseNoteToolCall(it)}.isFailure)}
 }
}
