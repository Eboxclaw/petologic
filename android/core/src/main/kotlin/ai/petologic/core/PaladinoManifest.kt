package ai.petologic.core

/** Restricted bundled YAML subset. External manifests, aliases and executable tags are not supported. */
data class PaladinoManifest(val roleId:String,val personaRef:String,val allowedTools:Set<String>){
 companion object {
  fun parse(text:String):PaladinoManifest {
   val values=linkedMapOf<String,String>()
   for(line in text.lines().filter{it.isNotBlank()&&!it.trimStart().startsWith("#")}){
    val parts=line.split(":",limit=2)
    require(parts.size==2&&parts[0] !in values){"Invalid or duplicate manifest field."}
    values[parts[0].trim()]=parts[1].trim()
   }
   require(values.keys==setOf("schemaVersion","roleId","personaRef","memoryNamespace","trigger","allowedTools","cloudPurposes","confirmationPolicy")){"Unknown or missing manifest fields."}
   require(values["schemaVersion"]=="1"&&values["roleId"]=="paladino"&&values["memoryNamespace"]=="paladino")
   require(values["personaRef"]=="paladino/persona.md"&&values["trigger"]=="chat"&&values["confirmationPolicy"]=="app_minimum")
   require(values["cloudPurposes"]=="[reason, summarize_context, plan]")
   val list=values.getValue("allowedTools")
   require(list.startsWith("[")&&list.endsWith("]"))
   val tools=list.drop(1).dropLast(1).split(',').map{it.trim()}.toSet()
   val supported=setOf("notes.search","notes.create","notes.delete","clock.read","alarm.next","calendar.today","weather.current","security.query","security.scan","security.action","device.query")
   require(tools.containsAll(setOf("notes.search","notes.create","notes.delete"))&&tools.all{it in supported}){"Unsupported tool or privilege expansion."}
   return PaladinoManifest("paladino","paladino/persona.md",tools)
  }
 }
}
