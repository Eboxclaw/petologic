package ai.petologic.core
import kotlin.test.*
class TokenizerTest {
 @Test fun `wordpiece uses greedy subwords and special boundaries`() {
  val t=WordPiece(mapOf("hello" to 9,"play" to 10,"##ing" to 11,"!" to 12))
  assertContentEquals(longArrayOf(101,9,10,11,12,102),t.encode("Héllo playing!"))
 }
 @Test fun `unknown word collapses and sequence is bounded`() {
  val t=WordPiece(mapOf("a" to 1),4)
  assertContentEquals(longArrayOf(101,100,102),t.encode("unknown"))
  assertContentEquals(longArrayOf(101,1,1,102),t.encode("a a a a a"))
 }
 @Test fun `manifest cannot weaken application security`() {
  val base="""schemaVersion: 1
roleId: paladino
personaRef: paladino/persona.md
memoryNamespace: paladino
trigger: chat
allowedTools: [notes.search, notes.create, notes.delete]
cloudPurposes: [reason, summarize_context, plan]
confirmationPolicy: app_minimum"""
  assertEquals("paladino",PaladinoManifest.parse(base).roleId)
  assertFailsWith<IllegalArgumentException>{PaladinoManifest.parse(base.replace("notes.delete","shell.execute"))}
  assertFailsWith<IllegalArgumentException>{PaladinoManifest.parse(base.replace("app_minimum","none"))}
 }
}
