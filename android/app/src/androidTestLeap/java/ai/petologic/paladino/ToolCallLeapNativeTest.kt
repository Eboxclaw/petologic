package ai.petologic.paladino

import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.LeapClient
import ai.liquid.leap.function.LFMFunctionCallParser
import ai.liquid.leap.function.LeapFunction
import ai.liquid.leap.function.LeapFunctionParameter
import ai.liquid.leap.function.LeapFunctionParameterType
import ai.liquid.leap.message.MessageResponse
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan 16 stage 4, variant C: LEAP native function calling with LFM's own call parser, same
 * fixtures as [ToolCallBenchmarkTest]. Only meaningful on the leap flavor; opt in with
 * -Pandroid.testInstrumentationRunnerArguments.toolBench=true.
 */
@RunWith(AndroidJUnit4::class)
class ToolCallLeapNativeTest{
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

 @Test fun leapNativeFunctionCalling()=runBlocking{
  val args=InstrumentationRegistry.getArguments()
  assumeTrue("opt in with -Pandroid.testInstrumentationRunnerArguments.toolBench=true",args.getString("toolBench")=="true")
  val dir="/data/local/tmp/petologic"
  val model=java.io.File("$dir/LFM2.5-350M-QAD-Q4_0.gguf")
  assumeTrue("push GGUFs first",model.exists())
  val runner=LeapClient.loadModel(model.absolutePath,ai.liquid.leap.ModelLoadingOptions(cpuThreads=4,contextSize=2048,useMmap=true))
  val results=JSONArray()
  try{
   val conversation=runner.createConversation("You are Paladino, a helpful assistant with private-notes tools.")
   conversation.registerFunction(LeapFunction("notes_search","Search this session's private notes.",listOf(LeapFunctionParameter("argument",LeapFunctionParameterType.LeapStr(),"concise search query",false))))
   conversation.registerFunction(LeapFunction("notes_save","Propose saving a note in this session (the user approves before writing).",listOf(LeapFunctionParameter("argument",LeapFunctionParameterType.LeapStr(),"the note text",false))))
   var correct=0
   for(f in fixtures){
    val begin=System.currentTimeMillis()
    var sawCalls:List<ai.liquid.leap.function.LeapFunctionCall>?=null
    val text=StringBuilder()
    withTimeout(120_000L){
     conversation.generateResponse(f.user,GenerationOptions(maxTokens=160,functionCallParser=LFMFunctionCallParser())).collect{r->
      when(r){
       is MessageResponse.Chunk->text.append(r.text)
       is MessageResponse.FunctionCalls->sawCalls=r.functionCalls
       else->{}
      }
     }
    }
    val verdict=when(val e=f.expect){
     is Expect.Tool->when{
      sawCalls==null->"malformed"
      sawCalls.firstOrNull()?.name==e.name&&(sawCalls.first().arguments.values.joinToString()).contains(e.argContains,ignoreCase=true)->"correct_tool"
      sawCalls.firstOrNull()?.name==e.name->"partial_arg"
      else->"wrong_tool"
     }
     is Expect.NoTool->if(sawCalls==null)"correct_no_tool" else "false_tool_call"
    }
    if(verdict.startsWith("correct"))correct++
    val row=JSONObject().put("protocol","leap_native").put("lang",f.lang).put("user",f.user)
     .put("verdict",verdict).put("wallMs",System.currentTimeMillis()-begin)
     .put("reply",(text.toString()+(sawCalls?.toString()?:"")).take(180))
    results.put(row);log(row.toString())
   }
   results.put(JSONObject().put("protocol","leap_native").put("summary","$correct/${fixtures.size} correct").put("engine","leap"))
  }finally{runner.unload()}
  val out=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)?:return@runBlocking
  java.io.File(out,"toolbench-leap-native-${System.currentTimeMillis()}.json").writeText(results.toString(1))
  log("leap-native done: ${results.length()} rows")
 }
}
