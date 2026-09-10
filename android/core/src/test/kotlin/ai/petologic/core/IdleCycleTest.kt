package ai.petologic.core
import kotlin.test.*

class IdleCycleTest {
 @Test fun `idle pair repeats one first clip and two second clips`() {
  val clips = generateSequence(IdleCycle.first()) { IdleCycle.next(it) }.take(7).map { it.clip }.toList()
  assertEquals(listOf(0, 1, 1, 0, 1, 1, 0), clips)
 }
 @Test fun `second clip counts its cycles before handing back`() {
  val slots = generateSequence(IdleCycle.first()) { IdleCycle.next(it) }.take(4).toList()
  assertEquals(IdleSlot(1, 0), slots[1])
  assertEquals(IdleSlot(1, 1), slots[2])
  assertEquals(IdleSlot(0, 0), slots[3])
 }
 @Test fun `configured repeat count is respected`() {
  val clips = generateSequence(IdleCycle.first()) { IdleCycle.next(it, secondClipPlays = 3) }.take(5).map { it.clip }.toList()
  assertEquals(listOf(0, 1, 1, 1, 0), clips)
 }
 @Test fun `a single clip pair collapses to constant looping`() {
  val clips = generateSequence(IdleCycle.first()) { IdleCycle.next(it, secondClipPlays = 1) }.take(5).map { it.clip }.toList()
  assertEquals(listOf(0, 1, 0, 1, 0), clips)
 }
}
