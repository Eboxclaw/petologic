package ai.petologic.paladino.runtime

import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.ext.agent.HistoryCompressionConfig
import ai.koog.agents.ext.agent.singleRunStrategyWithHistoryCompression
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.*
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.*
import ai.koog.prompt.message.*
import ai.koog.prompt.streaming.*
import ai.koog.serialization.typeToken
import ai.koog.utils.time.KoogClock
import ai.petologic.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable data class NoteToolArgs(val argument:String)
data class AgentTools(val search:(suspend(String)->String)?=null,val save:(suspend(String)->String)?=null)

/** Koog's singleRunStrategy owns model → tool → observation → model transitions. */
class PaladinoAgent(private val local:LocalModel?,private val cloud:OpenRouterTransport?,private val credentials:CredentialStore?,
 private val testTurn:(suspend(String,String)->String)?=null) {
 suspend fun run(context:ContextEnvelope,mode:ExecutionMode,modelId:String,consent:CloudConsent?=null,
  options:SessionOptions=SessionOptions(),tools:AgentTools=AgentTools(),
  onEvent:suspend(String,String)->Unit={_,_->},onText:(String)->Unit):String=withTimeout(options.validate().totalTimeoutSeconds*1000L) {
  val budget=LoopBudget(options)
  val registry=ToolRegistry {
   if(mode==ExecutionMode.TINY&&options.toolCalls){
    if(options.memoryRead&&tools.search!=null)tool(object:SimpleTool<NoteToolArgs>(typeToken<NoteToolArgs>(),"notes_search","Search this session's private notes. argument is a concise search query."){
     override suspend fun execute(args:NoteToolArgs):String {
      budget.tool("notes_search",args.argument);onEvent("tool_call","notes_search #${budget.calls}")
      return tools.search.invoke(args.argument).take(4000).also{onEvent("tool_result","notes_search returned ${it.length} characters")}
     }
    })
    if(options.memoryWrite&&tools.save!=null)tool(object:SimpleTool<NoteToolArgs>(typeToken<NoteToolArgs>(),"notes_save","Propose saving a note in this session. The user must approve before writing. argument is the note text."){
     override suspend fun execute(args:NoteToolArgs):String {
      budget.tool("notes_save",args.argument);onEvent("tool_call","notes_save #${budget.calls}")
      return tools.save.invoke(args.argument).take(1000).also{onEvent("tool_result","notes_save completed with a bounded result")}
     }
    })
   }
  }
  var repairs=0
  val executor=object:PromptExecutor(){
   override suspend fun execute(prompt:Prompt,model:LLModel,tools:List<ToolDescriptor>):Message.Assistant {
    budget.hop();onEvent("hop","Model turn ${budget.hops}/${options.maxHops}")
    if(mode==ExecutionMode.MAXX){
     check(options.network){"Network is disabled for this session."}
     val text=checkNotNull(cloud).generate(context,modelId,checkNotNull(credentials?.read()){"Connect OpenRouter first."},checkNotNull(consent){"Review this cloud request first."},onText)
     return Message.Assistant(text,metaInfo=ResponseMetaInfo.create(KoogClock.System))
    }
    val toolInstructions=if(tools.isEmpty())"" else "\nAvailable tools: "+registry.tools.joinToString { "${it.name}: ${it.descriptor.description}" }+"\nTo call a tool, output ONLY JSON: {\"tool\":\"notes_search\",\"argument\":\"query\"} or {\"tool\":\"notes_save\",\"argument\":\"note text\"}. Otherwise answer normally. After a tool observation, use its result and either call a different necessary tool or give the final answer. Never repeat an identical call."
    val system=context.system+"\nSession instructions: "+options.instructions+toolInstructions
    val transcript=prompt.messages.filterNot{it is Message.System}.joinToString("\n\n"){message->
     val role=if(message is Message.Assistant)"ASSISTANT" else "USER / TOOL OBSERVATION"
     role+": "+message.parts.joinToString("\n"){part->when(part){
      is MessagePart.Text->part.text
      is MessagePart.Tool.Call->"TOOL CALL ${part.tool} ${part.args}"
      is MessagePart.Tool.Result->"UNTRUSTED TOOL OBSERVATION ${part.tool}: ${part.output}"
      else->"[Unsupported attachment]"
     }}
    }
    val hardBytes=(options.contextTokens-options.outputTokens-options.toolReserve)*3
    check((system+transcript).toByteArray().size<hardBytes){"Session context is full. Start a new session or compress its history before continuing."}
    suspend fun generate(user:String)=withTimeout(options.hopTimeoutSeconds*1000L){testTurn?.invoke(system,user)?:checkNotNull(local).generate(system,user,if(modelId.startsWith("lfm"))modelId else "lfm350",options){text->if(!text.trimStart().startsWith("{"))onText(text)}}
    var result=generate(transcript)
    if(result.trimStart().startsWith("{")&&tools.isNotEmpty()){
     while(true){
      val call=runCatching{Json.parseToJsonElement(result.trim().removePrefix("```json").removeSuffix("```").trim()).jsonObject}.getOrNull()
      val name=call?.get("tool")?.jsonPrimitive?.contentOrNull
      val argument=call?.get("argument")?.jsonPrimitive?.contentOrNull
      if(name!=null&&argument!=null&&argument.isNotBlank()&&argument.length<=12000&&call.keys==setOf("tool","argument")){
       check(registry.getToolOrNull(name)!=null){"The model requested a tool that this session cannot use."}
       return Message.Assistant(parts=listOf(MessagePart.Tool.Call(UUID.randomUUID().toString(),name,buildJsonObject{put("argument",argument)})),metaInfo=ResponseMetaInfo.create(KoogClock.System))
      }
      if(call?.containsKey("answer")==true){result=call["answer"]!!.jsonPrimitive.content;break}
      check(repairs++<options.maxRetries){"Malformed tool call. No action was performed."}
      budget.hop();onEvent("repair","Repairing malformed tool JSON ($repairs)")
      result=generate(transcript+"\nYour previous response was invalid. Return exactly a valid tool JSON object with tool and argument, or a normal final answer.")
      if(!result.trimStart().startsWith("{"))break
     }
    }
    return Message.Assistant(result,metaInfo=ResponseMetaInfo.create(KoogClock.System))
   }
   override fun executeStreaming(prompt:Prompt,model:LLModel,tools:List<ToolDescriptor>):Flow<StreamFrame> = flow {execute(prompt,model,tools).toStreamFrames().forEach{emit(it)}}
   override suspend fun moderate(prompt:Prompt,model:LLModel):ModerationResult=throw UnsupportedOperationException("No moderation capability configured.")
   override fun close(){}
  }
  val model=LLModel(LLMProvider(if(mode==ExecutionMode.TINY)"liquid-local" else "openrouter","Paladino"),if(mode==ExecutionMode.TINY)ModelCatalog.artifacts.find{it.id==modelId}?.model?:"LFM2.5-350M" else modelId,listOf(LLMCapability.Completion,LLMCapability.Tools),options.contextTokens.toLong(),options.outputTokens.toLong())
  fun promptBytes(prompt:Prompt)=prompt.messages.sumOf{it.parts.toString().toByteArray().size}
  var compressions=0
  val compression=object:HistoryCompressionStrategy(){
   override suspend fun compress(llmSession:AIAgentLLMWriteSession,memoryMessages:List<Message>){
    check(++compressions<=2){"Context remains full after compression. Start a new session; saved history is intact."}
    val before=promptBytes(llmSession.prompt)
    onEvent("compression_started","Bounded local summary; original history remains in Room")
    WholeHistory.compress(llmSession,memoryMessages)
    val after=promptBytes(llmSession.prompt)
    onEvent("compression_completed","Prompt bytes $before → $after; summary is derived context")
    check(after<before){"Compression did not free context. Start a new session; saved history is intact."}
   }
  }
  val strategy=singleRunStrategyWithHistoryCompression(HistoryCompressionConfig(
   isHistoryTooBig={mode==ExecutionMode.TINY&&promptBytes(it)>(options.contextTokens-options.outputTokens-options.toolReserve)*3*options.compressionPercent/100},
   compressionStrategy=compression),parallelTools=false)
  val agent=AIAgent(strategy=strategy,promptExecutor=executor,llmModel=model,toolRegistry=registry,systemPrompt=context.system,maxIterations=options.maxHops*3+6)
  try{agent.run(context.user)}finally{agent.close()}
 }
}
