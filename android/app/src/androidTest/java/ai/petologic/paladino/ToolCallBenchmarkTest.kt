package ai.petologic.paladino

import ai.petologic.core.SessionOptions
import ai.petologic.paladino.inference.LocalGenerationRequest
import ai.petologic.paladino.runtime.LfmToolCallParser
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 16 stage 4: tool-calling quality across protocols and languages, same fixtures through
 * every variant. Variant A = forced JSON instruction (today's production prompt), variant B =
 * native LFM pythonic call syntax. Runs on the flavor's engine, so the llama and leap flavors
 * produce directly comparable tables. Opt in with
 * -Pandroid.testInstrumentationRunnerArguments.toolBench=true.
 */
@RunWith(AndroidJUnit4::class)
class ToolCallBenchmarkTest{
 private fun log(msg:String){Log.println(Log.WARN,"TOOLBENCH",msg)}

 private sealed interface Expect{
  data class Tool(val name:String,val argContains:String):Expect
  data object NoTool:Expect
 }
 private data class Fixture(val lang:String,val user:String,val expect:Expect)

 private val fixtures=listOf(
  Fixture("EN","Hey! What can you do for me?",Expect.NoTool),
  Fixture("EN","Search my notes for the bike repair shop.",Expect.Tool("notes_search","bike repair")),
  Fixture("EN","Remember that the wifi password is falcon-42.",Expect.Tool("notes_save","falcon-42")),
  Fixture("EN","How many legs does a spider have?",Expect.NoTool),
  Fixture("PT","Olá! Tudo bem contigo?",Expect.NoTool),
  Fixture("PT","Pesquisa nas minhas notas por receita de bacalhau.",Expect.Tool("notes_search","bacalhau")),
  Fixture("PT","Guarda que a reunião é sexta às 15h.",Expect.Tool("notes_save","sexta")),
  Fixture("PT","Procura nas notas o código do portão.",Expect.Tool("notes_search","portão")),
  Fixture("PT","Qual é a tua cor favorita?",Expect.NoTool),
  Fixture("MIXED","Pesquisa my notes for train tickets to Porto.",Expect.Tool("notes_search","train tickets")),
  Fixture("MIXED","Please guarda que o voo para Berlin é às 9h.",Expect.Tool("notes_save","Berlin")),
  Fixture("MIXED","Thanks! Me conta o que sabes sobre a ponte.",Expect.NoTool)
 )

 private val toolDocs="notes_search(argument: query) — Search this session's private notes.\nnotes_save(argument: text) — Propose saving a note in this session (the user approves before writing)."
 private val jsonInstruction="List of tools:\n$toolDocs\nOutput function calls as JSON. For a tool call use {\"name\":\"tool_name\",\"arguments\":{\"argument\":\"text\"}}. A greeting needs no tool: answer normally."
 private val nativeInstruction="List of tools:\n$toolDocs\nOutput function calls in native form: [tool_name(argument=\"value\")]. A greeting needs no tool: answer normally."

 private fun classify(reply:String,expect:Expect):String{
  val parsed=runCatching{LfmToolCallParser.parse(reply,allowed=setOf("notes_search","notes_save"))}.getOrNull()
  return when(expect){
   is Expect.Tool->when{
    parsed==null->"malformed"
    parsed.first==expect.name&&parsed.second.contains(expect.argContains,ignoreCase=true)->"correct_tool"
    parsed.first==expect.name->"partial_arg"
    else->"wrong_tool"
   }
   is Expect.NoTool->if(parsed==null)"correct_no_tool" else "false_tool_call"
  }
 }

 @Test fun toolCallProtocols()=runBlocking{
  val args=InstrumentationRegistry.getArguments()
  assumeTrue("opt in with -Pandroid.testInstrumentationRunnerArguments.toolBench=true",args.getString("toolBench")=="true")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PaladinoApplication
  app.local.verify()
  val backend=app.inference
  val engine=BuildConfig.FLAVOR
  val options=SessionOptions(contextTokens=2048,outputTokens=160,hopTimeoutSeconds=60,totalTimeoutSeconds=120)
  val results=JSONArray()
  for(protocol in listOf("json","native")){
   val instruction=if(protocol=="json")jsonInstruction else nativeInstruction
   val system="You are Paladino, a helpful assistant with private-notes tools. Follow the output format exactly.\nWhen the current request explicitly asks to search or save notes, you must call the tool now; do not apologize and do not claim you cannot search.\n$instruction"
   var correct=0
   for(f in fixtures){
    val begin=System.currentTimeMillis()
    val reply=withTimeout(120_000L){
     var reasoning=""
     val answer=backend.generate(LocalGenerationRequest(system,f.user,"lfm350",options)){}
     // Note: onText only sees content chunks; LEAP may emit separate reasoning chunks.
     answer
    }
    val verdict=classify(reply,f.expect)
    if(verdict.startsWith("correct"))correct++
    val row=JSONObject().put("protocol",protocol).put("lang",f.lang).put("user",f.user)
     .put("expect",if(f.expect is Expect.Tool)"${f.expect.name}:${f.expect.argContains}" else "no_tool")
     .put("verdict",verdict).put("wallMs",System.currentTimeMillis()-begin)
     .put("reply",reply.take(180))
    results.put(row);log(row.toString())
   }
   results.put(JSONObject().put("protocol",protocol).put("summary","$correct/${fixtures.size} correct").put("engine",engine))
  }
  val dir=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)?:return@runBlocking
  java.io.File(dir,"toolbench-$engine-${System.currentTimeMillis()}.json").writeText(results.toString(1))
  log("engine=$engine done: ${results.length()} rows")
 }
}
