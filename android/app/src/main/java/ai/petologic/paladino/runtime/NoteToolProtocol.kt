package ai.petologic.paladino.runtime
import kotlinx.serialization.json.*

internal fun looksLikeToolCall(raw:String):Boolean = raw.trimStart().let{it.startsWith("{")||it.startsWith("[")||it.startsWith("```")||it.contains("<|tool_call_start|>")}
/** Strict data parser; never evaluates Python, arbitrary functions or trailing prose. */
internal fun parseNoteToolCall(raw:String):Pair<String,String>{
 var text=raw.trim()
 if(text.startsWith("<|tool_call_start|>")){
  require(text.indexOf("<|tool_call_start|>",1)<0)
  val end=text.indexOf("<|tool_call_end|>");require(end>0)
  text=text.substring("<|tool_call_start|>".length,end).trim()
 }
 if(text.startsWith("```json\n")&&text.endsWith("```"))text=text.removePrefix("```json\n").removeSuffix("```").trim()
 // LFM's documented default is a Pythonic list. Parse only our two typed
 // functions with one string literal; never evaluate expressions or Python code.
 val pythonCall=Regex("""^\[(notes_save|notes_search)\(argument\s*=\s*("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')\)\]$""",RegexOption.DOT_MATCHES_ALL).matchEntire(text)
 if(pythonCall!=null){
  val value=decodeToolString(pythonCall.groupValues[2])
  require(value.isNotBlank()&&value.length<=12000)
  return pythonCall.groupValues[1] to value
 }
 val element=Json.parseToJsonElement(text)
 val obj=if(element is JsonArray){require(element.size==1);element.single().jsonObject}else element.jsonObject
 val name:String;val arg:JsonElement
 if(obj.keys==setOf("name","arguments")){
  name=obj.getValue("name").jsonPrimitive.also{require(it.isString)}.content
  val args=obj.getValue("arguments").jsonObject;require(args.keys==setOf("argument"));arg=args.getValue("argument")
 }else{
  require(obj.keys==setOf("tool","argument"))
  name=obj.getValue("tool").jsonPrimitive.also{require(it.isString)}.content;arg=obj.getValue("argument")
 }
 require(name in setOf("notes_save","notes_search"))
 val value=arg.jsonPrimitive.also{require(it.isString)}.content
 require(value.isNotBlank()&&value.length<=12000)
 return name to value
}

private fun decodeToolString(literal:String):String {
 if(literal.startsWith('"'))return Json.parseToJsonElement(literal).jsonPrimitive.content
 val body=literal.substring(1,literal.length-1)
 val result=StringBuilder();var i=0
 while(i<body.length){
  val c=body[i++]
  if(c!='\\'){require(c.code>=32);result.append(c);continue}
  require(i<body.length)
  when(val escaped=body[i++]){
   '\''->result.append('\'');'"'->result.append('"');'\\'->result.append('\\')
   'n'->result.append('\n');'r'->result.append('\r');'t'->result.append('\t')
   'u'->{require(i+4<=body.length);result.append(body.substring(i,i+4).toInt(16).toChar());i+=4}
   else->error("Unsupported escape in tool argument")
  }
 }
 return result.toString()
}
