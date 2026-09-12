package ai.petologic.paladino.skills
import org.junit.Test
import org.junit.Assert.*

class SkillActivationTest {
 private val memory=MemorySkill.definition
 private val security=ai.petologic.skills.security.SecuritySkill.definition

 @Test fun off_removes_prompt_and_tools_even_when_pinned_by_history(){
  val states=mapOf(memory.id to SkillState.OFF)
  assertTrue(activateSkills(listOf(memory),states,emptySet(),emptySet(),"remember my bike size").isEmpty())
 }

 @Test fun pinned_activates_without_router_match_and_auto_needs_a_match(){
  val pinned=activateSkills(listOf(memory),mapOf(memory.id to SkillState.PINNED),emptySet(),emptySet(),"explain quantum entanglement")
  assertEquals(1,pinned.size);assertEquals(2,pinned.first().tools.size)
  assertTrue(activateSkills(listOf(memory),mapOf(memory.id to SkillState.AUTO),emptySet(),emptySet(),"explain quantum entanglement").isEmpty())
  val woken=activateSkills(listOf(memory),mapOf(memory.id to SkillState.AUTO),emptySet(),emptySet(),"please remember that my bike lock code is 4321")
  assertEquals(1,woken.size)
 }

 @Test fun router_match_is_case_and_diacritic_insensitive_word_bounded(){
  val auto=mapOf(memory.id to SkillState.AUTO)
  assertTrue(activateSkills(listOf(memory),auto,emptySet(),emptySet(),"Minha NOTA de hoje").isNotEmpty())
  assertTrue(activateSkills(listOf(memory),auto,emptySet(),emptySet(),"denoted symmetrical design").isEmpty())
 }

 @Test fun security_guard_auto_default_wakes_on_security_questions_only(){
  // Fresh installs start from the definition's default state: AUTO for Security Guard.
  assertEquals(SkillState.AUTO,security.defaultState)
  val on=activateSkills(listOf(security),mapOf(security.id to security.defaultState),emptySet(),emptySet(),"Is this link suspicious? http://193.42.11.7")
  assertEquals(listOf("security_query","security_scan","security_action"),on.first().tools.map{it.id})
  // Plan 13 acceptance gate: an unrelated topic must wake nothing.
  assertTrue(activateSkills(listOf(security),mapOf(security.id to SkillState.AUTO),emptySet(),emptySet(),"what is a black hole").isEmpty())
 }

 @Test fun notification_monitoring_needs_both_toggle_and_capability(){
  // The user toggle defaults to off (registry pref); the pure layer proves the capability gate.
  assertFalse(security.tools.first{it.id=="notification_query"}.defaultEnabled)
  val auto=mapOf(security.id to SkillState.AUTO)
  // Capability missing (listener not granted): never registers even when not disabled.
  val noCapability=activateSkills(listOf(security),auto,emptySet(),emptySet(),"any suspicious message?")
  assertFalse(noCapability.first().tools.any{it.id=="notification_query"})
  // Capability granted and everything else disabled: only notification_query registers.
  val withCapability=activateSkills(listOf(security),auto,
   setOf("security_query","security_scan","security_action"),setOf("cap.notification_listener"),"any suspicious message?")
  assertEquals(listOf("notification_query"),withCapability.first().tools.map{it.id})
 }

 @Test fun tool_toggles_gate_registration(){
  val pinnedAll=mapOf(memory.id to SkillState.PINNED,security.id to SkillState.PINNED)
  val withoutAction=activateSkills(listOf(memory,security),pinnedAll,setOf("security_action"),emptySet(),"anything at all")
  assertEquals(listOf("security_query","security_scan"),withoutAction.first{it.skill.id=="security_guard"}.tools.map{it.id})
  val scanned=activateSkills(listOf(security),mapOf(security.id to SkillState.AUTO),setOf("security_scan"),emptySet(),"is this message a scam")
  assertEquals(listOf("security_query","security_action"),scanned.first().tools.map{it.id})
 }

 @Test fun activation_never_leaks_other_context(){
  val skills=listOf(memory,security)
  assertTrue(activateSkills(skills,mapOf(memory.id to SkillState.AUTO,security.id to SkillState.AUTO),emptySet(),emptySet(),"what is a black hole").isEmpty())
 }
}
