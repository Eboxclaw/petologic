package ai.petologic.paladino
import org.junit.Test
import org.junit.Assert.*
class PetReactionTest {
 @Test fun approval_is_visible_even_when_working_or_failed(){
  assertEquals(PetReaction.NEEDS_INPUT,petReaction(true,true,true))
  assertEquals(PetReaction.BLOCKED,petReaction(true,false,true))
  assertEquals(PetReaction.RUNNING,petReaction(true,false,false))
  assertEquals(PetReaction.IDLE,petReaction(false,false,false))
 }
 @Test fun absent_cycles_fall_back_without_hiding_status(){
  PetReaction.entries.forEach{assertEquals("idle",it.animation(setOf("idle")))}
  assertEquals("waiting",PetReaction.NEEDS_INPUT.animation(setOf("idle","waiting")))
 }
 @Test(expected=IllegalArgumentException::class) fun missing_idle_is_invalid(){PetReaction.RUNNING.animation(emptySet())}
}
