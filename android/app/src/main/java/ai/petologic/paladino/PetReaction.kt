package ai.petologic.paladino

/** Presentation of actual controller state; it cannot grant tools or trigger inference. */
enum class PetReaction(val label: String, val preferredAnimation: String) {
 IDLE("Idle", "idle"), RUNNING("Working", "review"),
 NEEDS_INPUT("Needs input", "waiting"), BLOCKED("Error", "failed");
 fun animation(available: Set<String>): String {
  require("idle" in available) { "A pet must provide an idle fallback" }
  return preferredAnimation.takeIf { it in available } ?: "idle"
 }
}
fun petReaction(busy: Boolean, needsInput: Boolean, error: Boolean): PetReaction = when {
 needsInput -> PetReaction.NEEDS_INPUT
 error -> PetReaction.BLOCKED
 busy -> PetReaction.RUNNING
 else -> PetReaction.IDLE
}
fun PaladinoUiState.petReaction() = petReaction(busy, action != null || cloud != null, error != null)
