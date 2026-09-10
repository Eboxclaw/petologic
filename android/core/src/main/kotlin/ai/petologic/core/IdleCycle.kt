package ai.petologic.core

/** One slot of the idle loop: which clip is on screen and which repetition of it. */
data class IdleSlot(val clip: Int, val play: Int)

/**
 * Idle looping: clip 0 plays once, clip 1 plays [IdleCycle.next]'s default two
 * cycles, then the pair repeats. Pure presentation state; it cannot grant tools
 * or trigger inference.
 */
object IdleCycle {
 fun first(): IdleSlot = IdleSlot(0, 0)
 fun next(slot: IdleSlot, secondClipPlays: Int = 2): IdleSlot = when {
  slot.clip == 0 -> IdleSlot(1, 0)
  slot.play + 1 < secondClipPlays -> IdleSlot(1, slot.play + 1)
  else -> first()
 }
}
