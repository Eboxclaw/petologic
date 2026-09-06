package ai.petologic.core

import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class ExecutionMode { TINY, MAXX }
enum class Sensitivity { LOCAL_ONLY, CLOUD_ALLOWED }
enum class TaskStatus { ROUTING, AWAITING_APPROVAL, RUNNING, COMPLETED, CANCELLED, FAILED }
data class MemoryNote(val id: String, val text: String, val sensitivity: Sensitivity = Sensitivity.LOCAL_ONLY, val updatedAt: Long = 0)
data class ChatTurn(val speaker: String, val text: String)
data class ContextEnvelope(val system: String, val user: String, val sources: List<MemoryNote>, val digest: String)
sealed interface Route {
 data class Save(val text: String) : Route
 data class Search(val query: String) : Route
 data class Generate(val mode: ExecutionMode) : Route
 data class Clarify(val message: String) : Route
}

class RoutePolicy {
 fun route(input: String, mode: ExecutionMode): Route {
  val text = input.trim()
  if (text.isBlank()) return Route.Clarify("Write a message first.")
  if (text.length > 12_000) return Route.Clarify("Please shorten this request to 12,000 characters or fewer.")
  val save = Regex("^(?:remember(?: that)?|save note:?|lembra(?:-te)?(?: que)?|guardar nota:?)\\s+(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).matchEntire(text)
  if (save != null) return Route.Save(save.groupValues[1].trim())
  val search = Regex("^(?:find(?: notes?)?|search(?: notes?)?|procura(?: notas?)?|buscar(?: notas?)?)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(text)
  if (search != null) return Route.Search(search.groupValues[1].trim())
  return Route.Generate(mode)
 }
}

fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

/** Consent is bound to the exact final payload and provider, never merely a mode toggle. */
data class CloudConsent(val payloadDigest: String, val providerModel: String, val expiresAt: Instant) {
 fun permits(payload: ContextEnvelope, model: String, now: Instant): Boolean =
  payload.digest == payloadDigest && model == providerModel && now.isBefore(expiresAt)
}

class ContextBroker(private val persona: String, private val maxInputBytes: Int = 11_000) {
 fun build(request: String, mode: ExecutionMode, notes: List<MemoryNote>, history: List<ChatTurn> = emptyList()): ContextEnvelope {
  // UTF-8 bytes form a conservative input bound; local native tokenizer is the final authority.
  val system = persona.trim()
  val allowed = notes.filter { mode == ExecutionMode.TINY || it.sensitivity == Sensitivity.CLOUD_ALLOWED }
  require(system.toByteArray().size + request.toByteArray().size + 100 < maxInputBytes) { "Request is too long for this context budget. Please shorten it." }
  val selected = mutableListOf<MemoryNote>()
  var user = request
  for (note in allowed.take(5)) {
   val block = "\n\nUNTRUSTED MEMORY [${note.id}]\n${note.text}\nEND MEMORY"
   if ((system + user + block).toByteArray().size < maxInputBytes) { user += block; selected += note }
  }
  // History is omitted from Maxx unless separately classified: old Tiny turns may contain private notes.
  if (mode == ExecutionMode.TINY) for (turn in history.takeLast(4).asReversed()) {
   val block = "\n\nPrevious ${turn.speaker}: ${turn.text}"
   if ((system + user + block).toByteArray().size < maxInputBytes) user += block
  }
  return ContextEnvelope(system, user, selected, digest(system + "\u0000" + user))
 }
}

data class ActionProposal(val id: String = UUID.randomUUID().toString(), val tool: String, val argument: String, val expiresAt: Instant) {
 val argumentHash: String get() = digest(tool + "\u0000" + argument)
}
class ApprovalPolicy(private val clock: Clock = Clock.systemUTC()) {
 private val allowed = setOf("notes.create", "notes.delete", "reminders.create", "text.share", "calendar.prepare")
 fun validate(proposal: ActionProposal, approvedHash: String) {
  require(proposal.tool in allowed) { "Tool is not allowed." }
  require(clock.instant().isBefore(proposal.expiresAt)) { "This approval expired. Start the action again." }
  require(proposal.argumentHash == approvedHash) { "Action changed after approval." }
  require(proposal.argument.isNotBlank() && proposal.argument.length <= 12_000) { "Invalid action content." }
 }
 fun propose(tool: String, argument: String) = ActionProposal(tool = tool, argument = argument, expiresAt = clock.instant().plus(Duration.ofMinutes(5)))
}

/** Bounded graph traversal; only existing, source-backed edges are eligible. */
data class GraphEdge(val from: String, val to: String, val sourceId: String)
fun expandGraph(seed: Set<String>, edges: List<GraphEdge>, validSources: Set<String>, maxNodes: Int = 50): Set<String> {
 require(maxNodes > 0)
 val found = seed.take(maxNodes).toMutableSet()
 repeat(2) {
  val frontier = found.toSet()
  edges.asSequence().filter { it.sourceId in validSources && it.from in frontier }.take(100).forEach {
   if (found.size < maxNodes) found += it.to
  }
 }
 return found
}
