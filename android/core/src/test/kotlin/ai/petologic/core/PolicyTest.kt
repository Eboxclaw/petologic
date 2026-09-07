package ai.petologic.core
import kotlin.test.*
import java.time.*

class PolicyTest {
 @Test fun `only standalone greetings can prune tools`() {
  val router=RoutePolicy()
  listOf("Olá, Gents, como é que estás hoje?","Bom dia, Paladino! Tudo bem?","Hello!").forEach{kotlin.test.assertTrue(router.isSimpleGreeting(it))}
  listOf("Olá, guarda uma nota", "Bom dia, pesquisa as notas", "Olá! Qual é o código?", "Hi, delete this file").forEach{kotlin.test.assertFalse(router.isSimpleGreeting(it))}
 }
 @Test fun `history stays chronological and separate from the current request`() {
  val history=listOf(ChatTurn("user","old request"),ChatTurn("assistant","old reply"))
  val context=ContextBroker("Paladino").build("current request",ExecutionMode.TINY,emptyList(),history)
  kotlin.test.assertEquals(history,context.history)
  kotlin.test.assertEquals("current request",context.user)
  kotlin.test.assertTrue(ContextBroker("Paladino").build("cloud",ExecutionMode.MAXX,emptyList(),history).history.isEmpty())
 }
 @Test fun `save is anchored and multilingual`() {
  val policy = RoutePolicy()
  assertEquals(Route.Save("bring a coat"), policy.route("Remember that bring a coat", ExecutionMode.TINY))
  assertEquals(Route.Save("levar um casaco"), policy.route("guardar nota: levar um casaco", ExecutionMode.TINY))
  assertIs<Route.Generate>(policy.route("Explain why I should remember things", ExecutionMode.TINY))
 }
 @Test fun `maxx excludes private notes and local history`() {
  val envelope = ContextBroker("Paladino").build("hello", ExecutionMode.MAXX, listOf(MemoryNote("secret", "private password"), MemoryNote("public", "packing list", Sensitivity.CLOUD_ALLOWED)), listOf(ChatTurn("user", "another secret")))
  assertFalse(envelope.user.contains("secret")); assertFalse(envelope.user.contains("password"))
  assertEquals(listOf("public"), envelope.sources.map { it.id })
 }
 @Test fun `consent cannot be reused for changed content or provider`() {
  val c = ContextBroker("Paladino"); val now = Instant.parse("2026-09-06T12:00:00Z")
  val a = c.build("A", ExecutionMode.MAXX, emptyList()); val b = c.build("B", ExecutionMode.MAXX, emptyList())
  val grant = CloudConsent(a.digest, "provider/model", now.plusSeconds(60))
  assertTrue(grant.permits(a, "provider/model", now)); assertFalse(grant.permits(b, "provider/model", now))
  assertFalse(grant.permits(a, "other/model", now)); assertFalse(grant.permits(a, "provider/model", now.plusSeconds(60)))
 }
 @Test fun `tool policy blocks unknown expired and changed actions`() {
  val now = Instant.parse("2026-09-06T12:00:00Z"); val policy = ApprovalPolicy(Clock.fixed(now, ZoneOffset.UTC))
  val p = policy.propose("notes.create", "hello")
  policy.validate(p, p.argumentHash)
  assertFailsWith<IllegalArgumentException> { policy.validate(p.copy(argument="changed"), p.argumentHash) }
  assertFailsWith<IllegalArgumentException> { val q=policy.propose("shell", "execute"); policy.validate(q,q.argumentHash) }
  assertFailsWith<IllegalArgumentException> { policy.validate(p.copy(expiresAt=now),p.argumentHash) }
 }
 @Test fun `context is bounded even for multilingual inputs`() {
  val c = ContextBroker("persona", 1000)
  val e = c.build("request", ExecutionMode.TINY, (1..100).map { MemoryNote("$it", "猫".repeat(100)) })
  assertTrue((e.system+e.user).toByteArray().size < 1000)
  assertFailsWith<IllegalArgumentException> { c.build("猫".repeat(1000), ExecutionMode.TINY, emptyList()) }
 }
 @Test fun `graph respects hops provenance and node cap`() {
  val edges = listOf(GraphEdge("a","b","ok"),GraphEdge("b","c","ok"),GraphEdge("c","d","ok"),GraphEdge("a","secret","deleted"))
  assertEquals(setOf("a","b","c"), expandGraph(setOf("a"), edges, setOf("ok")))
  assertEquals(2, expandGraph(setOf("a"), edges, setOf("ok"), 2).size)
 }
}
