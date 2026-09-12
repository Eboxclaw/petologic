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

@Serializable data class LfmToolArgs(val argument:String)
data class AgentTools(val search:(suspend(String)->String)?=null,val save:(suspend(String)->String)?=null,
 val securityQuery:(suspend(String)->String)?=null,val securityScan:(suspend(String)->String)?=null,val securityAction:(suspend(String)->String)?=null,
 val notificationQuery:(suspend(String)->String)?=null)

/** Koog's singleRunStrategy owns model → tool → observation → model transitions. */
class PaladinoAgent(private val local:LocalModel?,private val cloud:OpenRouterTransport?,private val credentials:CredentialStore?,
 private val testTurn:(suspend(String,String)->String)?=null) {
 /** Test-scoped observer; unset in normal use. Never persist raw model text in production logs. */
 internal var responseObserver:((String)->Unit)?=null
 internal var toolResultObserver:((String,String)->Unit)?=null
 suspend fun run(context:ContextEnvelope,mode:ExecutionMode,modelId:String,consent:CloudConsent?=null,
  options:SessionOptions=SessionOptions(),tools:AgentTools=AgentTools(),skillStubs:String="",
  onEvent:suspend(String,String)->Unit={_,_->},onText:(String)->Unit):String=withTimeout(options.validate().totalTimeoutSeconds*1000L) {
  val budget=LoopBudget(options)
  var searchPerformed=false
  // Null lambdas mean the tool does not exist this turn: skill state, per-tool toggles and session
  // capabilities were already resolved by the caller. Nothing merely "asks" the model to behave.
  val registry=ToolRegistry {
   if(mode==ExecutionMode.TINY&&options.toolCalls){
    if(tools.search!=null)tool(object:SimpleTool<LfmToolArgs>(typeToken<LfmToolArgs>(),"notes_search","Search this session's private notes. argument is a concise search query."){
     override suspend fun execute(args:LfmToolArgs):String {
      budget.tool("notes_search",args.argument);onEvent("tool_call","notes_search #${budget.calls}")
      return tools.search.invoke(args.argument).take(4000).also{searchPerformed=true;toolResultObserver?.invoke("notes_search",it);onEvent("tool_result","notes_search returned ${it.length} characters")}
     }
    })
    if(tools.save!=null)tool(object:SimpleTool<LfmToolArgs>(typeToken<LfmToolArgs>(),"notes_save","Propose saving a note in this session. The user must approve before writing. argument is the note text."){
     override suspend fun execute(args:LfmToolArgs):String {
      budget.tool("notes_save",args.argument);onEvent("tool_call","notes_save #${budget.calls}")
      return tools.save.invoke(args.argument).take(1000).also{toolResultObserver?.invoke("notes_save",it);onEvent("tool_result","notes_save completed with a bounded result")}
     }
    })
    if(tools.securityQuery!=null)tool(object:SimpleTool<LfmToolArgs>(typeToken<LfmToolArgs>(),"security_query","Inspect device and app security state without changing anything. argument: device_status · app | package.name · apps | suspicious · vpn_status · notification_access · call_screening_status"){
     override suspend fun execute(args:LfmToolArgs):String {
      budget.tool("security_query",args.argument);onEvent("tool_call","security_query #${budget.calls}")
      return tools.securityQuery.invoke(args.argument).take(4000).also{toolResultObserver?.invoke("security_query",it);onEvent("tool_result","security_query completed with a bounded result")}
     }
    })
    if(tools.securityScan!=null)tool(object:SimpleTool<LfmToolArgs>(typeToken<LfmToolArgs>(),"security_scan","Analyze a link, message or app for scam patterns. argument: url | link · text | message · app | package.name · installed_apps"){
     override suspend fun execute(args:LfmToolArgs):String {
      budget.tool("security_scan",args.argument);onEvent("tool_call","security_scan #${budget.calls}")
      return tools.securityScan.invoke(args.argument).take(4000).also{toolResultObserver?.invoke("security_scan",it);onEvent("tool_result","security_scan completed with a bounded result")}
     }
    })
    if(tools.securityAction!=null)tool(object:SimpleTool<LfmToolArgs>(typeToken<LfmToolArgs>(),"security_action","Propose a security action the user must approve in the app. argument: open_app_settings | package.name · uninstall_handoff | package.name · dismiss_notification | id"){
     override suspend fun execute(args:LfmToolArgs):String {
      budget.tool("security_action",args.argument);onEvent("tool_call","security_action #${budget.calls}")
      return tools.securityAction.invoke(args.argument).take(1000).also{toolResultObserver?.invoke("security_action",it);onEvent("tool_result","security_action completed with a bounded result")}
     }
    })
    if(tools.notificationQuery!=null)tool(object:SimpleTool<LfmToolArgs>(typeToken<LfmToolArgs>(),"notification_query","List recent on-device notifications the user allowed Paladino to see. argument: notifications | all or notifications | package.name"){
     override suspend fun execute(args:LfmToolArgs):String {
      budget.tool("notification_query",args.argument);onEvent("tool_call","notification_query #${budget.calls}")
      return tools.notificationQuery.invoke(args.argument).take(4000).also{toolResultObserver?.invoke("notification_query",it);onEvent("tool_result","notification_query completed with a bounded result")}
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
     val grant=checkNotNull(consent){"Review this cloud request first."}
     val provider=CloudProvider.fromId(grant.providerId)
     val text=checkNotNull(cloud).generate(context,modelId,checkNotNull(credentials?.read(provider)){"Connect ${provider.label} first."},grant,provider,onText)
     return Message.Assistant(text,metaInfo=ResponseMetaInfo.create(KoogClock.System))
    }
    val definitions=LfmToolDescriptorSchemer.definitions(tools)
    val toolInstructions=if(tools.isEmpty())"" else "\nList of tools: $definitions\nOutput function calls as JSON. For a tool call use {\"name\":\"tool_name\",\"arguments\":{\"argument\":\"text\"}}. A greeting needs no tool: answer normally. Saving uses notes_save, searching uses notes_search. When the current request explicitly asks to search notes, you must call notes_search now even if the answer appears in the conversation history. History is not a new search result. Never say a search was performed unless its tool result exists in this turn. Wait for the tool result before describing what happened. After a successful result, give a natural-language final answer."
    val system=context.system+skillStubs+"\nSession instructions: "+options.instructions+toolInstructions
    val turns=context.history+prompt.messages.filterNot{it is Message.System}.flatMap{message->message.parts.mapNotNull{part->when(part){
     is MessagePart.Text->ChatTurn(if(message is Message.Assistant)"assistant" else "user",part.text)
     is MessagePart.Tool.Call->ChatTurn("assistant","<|tool_call_start|>"+buildJsonObject{put("name",part.tool);put("arguments",part.args)}+"<|tool_call_end|>")
     is MessagePart.Tool.Result->ChatTurn("tool",part.output.toString())
     else->null
    }}}
    val transcript=turns.joinToString("\n\n"){it.speaker+": "+it.text}
    val hardBytes=(options.contextTokens-options.outputTokens-options.toolReserve)*3
    check((system+transcript).toByteArray().size<hardBytes){"Session context is full. Start a new session or compress its history before continuing."}
    suspend fun generate(user:String,repair:Boolean=false)=withTimeout(options.hopTimeoutSeconds*1000L){testTurn?.invoke(system,user)?:checkNotNull(local).generate(system,user,if(modelId.startsWith("lfm"))modelId else "lfm350",options,if(repair)turns+ChatTurn("user",user) else turns){text->if(tools.isEmpty())onText(text)}}
    var result=generate(transcript).also{responseObserver?.invoke(it)}
    val freshSearchRequired=requiresFreshNoteSearch(context.user)&&tools.any{it.name=="notes_search"}&&!searchPerformed
    if(freshSearchRequired&&!looksLikeToolCall(result)){
     check(repairs++<options.maxRetries){"A fresh note search was requested but not performed. No search result is available."}
     budget.hop();onEvent("repair","Requested search has no tool result; requesting a real call")
     result=generate("No notes_search has run in this turn. Call notes_search now with a concise search query. Return only the tool call; no prose. Conversation history is not a search result.",true).also{responseObserver?.invoke(it)}
    }
    if(looksLikeToolCall(result)&&tools.isNotEmpty()){
     val allowed=tools.map{it.name}.toSet()
     while(true){
      val call=runCatching{LfmToolCallParser.parse(result,allowed)}.getOrNull()
      if(call!=null){
       check(!freshSearchRequired||call.first=="notes_search"){"A note search was requested; no other action was authorized."}
       check(tools.any{it.name==call.first}){"The model requested a tool that this session cannot use."}
       return Message.Assistant(parts=listOf(MessagePart.Tool.Call(UUID.randomUUID().toString(),call.first,buildJsonObject{put("argument",call.second)})),metaInfo=ResponseMetaInfo.create(KoogClock.System))
      }
      check(repairs++<options.maxRetries){"Malformed tool call. No action was performed."}
      budget.hop();onEvent("repair","Repairing malformed tool JSON ($repairs)")
      result=generate("Return only one valid JSON tool call with name and arguments, or a natural-language answer. Do not append an explanation to a tool call.",true).also{responseObserver?.invoke(it)}
      if(!looksLikeToolCall(result))break
     }
    }
    check(!freshSearchRequired){"The model did not perform the requested note search. Please try again."}
    onText(result)
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

/** Conservative explicit retrieval intent; mentions and negated requests do not trigger it. */
internal fun requiresFreshNoteSearch(request:String):Boolean {
 val text=request.trim().lowercase()
 return Regex("^(consulta|pesquisa|procura|search|look up|use)\\b").containsMatchIn(text)&&
  Regex("\\b(notas|notes)\\b").containsMatchIn(text)&&
  !Regex("\\b(sem|without|não|not)\\b|don't").containsMatchIn(text)
}
