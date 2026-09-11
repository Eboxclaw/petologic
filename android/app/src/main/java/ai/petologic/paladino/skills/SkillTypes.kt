package ai.petologic.paladino.skills

/** OFF keeps a skill out of routing, prompts and registries; AUTO needs the router to match; PINNED is always on. */
enum class SkillState{ OFF, AUTO, PINNED }

/** One tool a skill can expose. [androidPermission] gates registration on a granted Android permission. */
data class SkillToolSpec(val id:String,val label:String,val androidPermission:String?=null)

data class SkillDefinition(
 val id:String,
 val name:String,
 val description:String,
 /** Lowercase, diacritic-free router terms; any word-boundary match activates an AUTO skill. */
 val routerTerms:List<String>,
 /** The entire skill manual injected only while active — no pages of instructions. */
 val promptStub:String,
 val tools:List<SkillToolSpec>
)

data class SkillActivation(val skill:SkillDefinition,val tools:List<SkillToolSpec>)

/** Pure per-turn activation: PINNED always, AUTO only on a router-term match, OFF never. */
fun activateSkills(
 definitions:List<SkillDefinition>,
 states:Map<String,SkillState>,
 disabledTools:Set<String>,
 grantedPermissions:Set<String>,
 request:String
):List<SkillActivation>{
 val text=normalize(request)
 return definitions.mapNotNull{skill->
  val state=states[skill.id]?:SkillState.OFF
  val active=when(state){
   SkillState.OFF->false
   SkillState.PINNED->true
   SkillState.AUTO->skill.routerTerms.any{term->Regex("\\b${Regex.escape(term)}\\b").containsMatchIn(text)}
  }
  if(!active)return@mapNotNull null
  val tools=skill.tools.filter{spec->spec.id !in disabledTools&&(spec.androidPermission==null||spec.androidPermission in grantedPermissions)}
  if(tools.isEmpty())null else SkillActivation(skill,tools)
 }
}

internal fun normalize(text:String)=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD)
 .replace(Regex("\\p{Mn}"),"").lowercase()
