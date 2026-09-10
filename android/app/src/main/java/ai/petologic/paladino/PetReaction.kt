package ai.petologic.paladino

import ai.petologic.core.IdleCycle

/** Presentation of actual controller state; it cannot grant tools or trigger inference. */
enum class PetReaction(val label: String, val preferredAnimation: String) {
 IDLE("Idle", "idle"), RUNNING("Working", "thinking"),
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
fun PaladinoUiState.petReaction() = petReaction(busy, action != null || cloud != null || pendingModelMessage != null, error != null)

/** The idle pair chains two clips (one + two cycles, see [IdleCycle]) for a continuous loop. */
val idlePair: Pair<Int, Int> = R.raw.paladino_idle1 to R.raw.paladino_idle2

/** Missing reaction assets deliberately fall back to the idle pair. */
fun PetReaction.animationResource(): Int = if (this == PetReaction.RUNNING) R.raw.paladino_thinking else R.raw.paladino_idle1
