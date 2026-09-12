package ai.petologic.paladino.skills

import android.content.Context
import android.content.pm.PackageManager

/** The first skill: the existing notes tools, wrapped so the registry has a real resident. */
object MemorySkill {
 const val ID="memory"
 val definition=SkillDefinition(
  id=ID,
  name="Memory",
  description="Search this session's private notes and propose new ones.",
  routerTerms=listOf("note","notes","remember","memo","nota","notas","anota","anotar","lembra","memoria"),
  promptStub="MEMORY SKILL: notes_search reads this session's private notes; notes_save proposes a note the user must approve. Never say a search was performed unless its tool result exists in this turn.",
  tools=listOf(
   SkillToolSpec("notes_search","Search private notes"),
   SkillToolSpec("notes_save","Propose a note")
  )
 )
}

/** Definitions plus persisted states and per-tool toggles. Pure activation lives in [activateSkills]. */
object SkillRegistry {
 val definitions:List<SkillDefinition> = listOf(MemorySkill.definition,ai.petologic.skills.security.SecuritySkill.definition)
 private const val PREFS="skills"

 fun states(context:Context):Map<String,SkillState>{
  val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  return definitions.associate{skill->
   skill.id to runCatching{SkillState.valueOf(prefs.getString("state.${skill.id}",skill.defaultState.name)?:skill.defaultState.name)}.getOrDefault(skill.defaultState)
  }
 }

 fun setSkillState(context:Context,id:String,state:SkillState){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("state.$id",state.name).apply()
 }

 fun toolEnabled(context:Context,skill:SkillDefinition,tool:SkillToolSpec):Boolean=
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("tool.${skill.id}.${tool.id}",tool.defaultEnabled)

 fun setToolEnabled(context:Context,skillId:String,toolId:String,enabled:Boolean){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("tool.$skillId.$toolId",enabled).apply()
 }

 fun activationsFor(context:Context,request:String):List<SkillActivation>{
  val disabled=definitions.flatMap{skill->skill.tools.filter{!toolEnabled(context,skill,it)}.map{it.id}}.toSet()
  val granted=definitions.flatMap{it.tools}.mapNotNull{it.androidPermission}.toSet().filter{capabilityGranted(context,it)}.toSet()
  return activateSkills(definitions,states(context),disabled,granted,request)
 }

 /** Real Android permissions use checkSelfPermission; "cap.*" entries map to app-level grants. */
 internal fun capabilityGranted(context:Context,permission:String):Boolean=when{
  permission.startsWith("cap.")->when(permission){
   "cap.notification_listener"->ai.petologic.skills.security.android.NotificationListener.accessGranted(context)
   else->false
  }
  else->context.checkSelfPermission(permission)==android.content.pm.PackageManager.PERMISSION_GRANTED
 }
}
