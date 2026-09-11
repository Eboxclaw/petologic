package ai.petologic.paladino.skills
import org.junit.Test
import org.junit.Assert.*

class SkillActivationTest {
 private val memory=MemorySkill.definition
 private fun security()=SkillDefinition(
  id="security_guard",name="Security Guard",description="Spam, phishing, app and device checks",
  routerTerms=listOf("spam","scam","phishing","malware","suspicious link","virus"),
  promptStub="SECURITY GUARD: security_query inspects; security_scan analyses; security_action needs approval.",
  tools=listOf(
   SkillToolSpec("security_query","Security status"),
   SkillToolSpec("security_scan","Scan suspicious text/link"),
   SkillToolSpec("security_action","Approved security actions",androidPermission="android.permission.CAMERA")
  )
 )

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

 @Test fun tool_toggles_and_permissions_gate_registration(){
  val security=security()
  val pinnedAll=mapOf(memory.id to SkillState.PINNED,security.id to SkillState.PINNED)
  // Camera permission not granted: security_action must not exist in the registry.
  val withoutCamera=activateSkills(listOf(memory,security),pinnedAll,emptySet(),emptySet(),"anything")
  val sec=withoutCamera.first{it.skill.id=="security_guard"}
  assertEquals(listOf("security_query","security_scan"),sec.tools.map{it.id})
  // With the permission granted, all three tools appear.
  val withCamera=activateSkills(listOf(memory,security),pinnedAll,emptySet(),setOf("android.permission.CAMERA"),"anything")
  assertEquals(3,withCamera.first{it.skill.id=="security_guard"}.tools.size)
  // A disabled tool disappears from the registry, not just from the prompt.
  val scanned=activateSkills(listOf(security),mapOf(security.id to SkillState.AUTO),setOf("security_scan"),emptySet(),"is this a scam message")
  assertEquals(listOf("security_query"),scanned.first().tools.map{it.id})
 }

 @Test fun activation_never_leaks_other_context(){
  // The acceptance gate in plan 13: an unrelated topic wakes nothing on AUTO.
  val skills=listOf(memory,security())
  assertTrue(activateSkills(skills,mapOf(memory.id to SkillState.AUTO,security().id to SkillState.AUTO),emptySet(),emptySet(),"what is a black hole").isEmpty())
 }
}
