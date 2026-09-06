package ai.petologic.paladino

import android.app.Application
import android.content.Context


import ai.petologic.core.*
import ai.petologic.paladino.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.UUID

data class PendingCloud(val context:ContextEnvelope,val model:String)
data class PaladinoUiState(
 val mode:ExecutionMode=ExecutionMode.TINY,val busy:Boolean=false,val status:String="At your side.",
 val streaming:String="",val error:String?=null,val action:ActionProposal?=null,
 val cloud:PendingCloud?=null,val connected:Boolean=false,val model:String="",val notice:String?=null
)
class SessionController(private val app:PaladinoApplication,val sessionId:String){
 private val scope=CoroutineScope(SupervisorJob(app.scope.coroutineContext[Job])+Dispatchers.Main.immediate)
 val options=MutableStateFlow(SessionOptions())
 val sessionInfo=MutableStateFlow(SessionRow(sessionId,"Conversation"))
 val events=app.memory.dao.events(sessionId).stateIn(scope,SharingStarted.WhileSubscribed(5000),emptyList())
 private val initialized=scope.async(start=CoroutineStart.LAZY) { app.ready.await();app.memory.dao.session(sessionId)?.let{row->sessionInfo.value=row;options.value=kotlinx.serialization.json.Json.decodeFromString<SessionOptions>(row.optionsJson);ui.update{it.copy(mode=ExecutionMode.valueOf(row.mode))}} }
 private val prefs=app.getSharedPreferences("preferences",Context.MODE_PRIVATE)
 private val policy=ApprovalPolicy()
 private val manifest=PaladinoManifest.parse(app.assets.open("paladino/manifest.yaml").bufferedReader().use{it.readText()})
 private val broker=ContextBroker(app.assets.open(manifest.personaRef).bufferedReader().use{it.readText()})
 val ui=MutableStateFlow(PaladinoUiState(connected=app.credentials.read()!=null,model=prefs.getString("model","")?:""))
 val messages=app.memory.dao.messages(sessionId).stateIn(scope,SharingStarted.WhileSubscribed(5000),emptyList())
 val notes=app.memory.dao.observeNotes(sessionId).stateIn(scope,SharingStarted.WhileSubscribed(5000),emptyList())
 val modelStatus=app.local.status
 val modelReady=app.local.ready
 val downloadProgress=app.local.progress
 val semanticReady=app.embedder.ready
 val semanticInstalling=app.embedder.installing
 fun installSemantic(){scope.launch{try{app.embedder.install()}catch(e:CancellationException){throw e}catch(e:Exception){ui.update{it.copy(error=e.message?:"Semantic model setup failed.")}}}}
 private var toolApproval:CompletableDeferred<Boolean>?=null
 private var task:Job?=null
 fun start(){scope.launch{initialized.await()}}
 private var activeTaskId:String?=null
 private suspend fun finishTask(status:String){activeTaskId?.let{app.memory.dao.taskStatus(it,status)}}
 fun mode(mode:ExecutionMode){if(!ui.value.busy && ui.value.cloud==null && ui.value.action==null){ui.update{it.copy(mode=mode,error=null)};scope.launch{initialized.await();val row=sessionInfo.value.copy(mode=mode.name);app.memory.dao.session(row);sessionInfo.value=row}}}
 fun updateOptions(value:SessionOptions){try{value.validate();if(ui.value.busy){cancel()};options.value=value;scope.launch{initialized.await();options.value=value;val row=sessionInfo.value.copy(optionsJson=kotlinx.serialization.json.Json.encodeToString(value));app.memory.dao.session(row);sessionInfo.value=row;log("settings","Session settings updated")}}catch(e:Exception){ui.update{it.copy(error=e.message?:"Invalid settings")}}}
 fun selectModel(id:String){if(ui.value.busy)return;require(ModelCatalog.artifacts.any{it.id==id&&it.id!="minilm"});scope.launch{initialized.await();val row=sessionInfo.value.copy(modelId=id);app.memory.dao.session(row);sessionInfo.value=row}}
 suspend fun log(type:String,detail:String){app.memory.dao.event(ExecutionEventRow(UUID.randomUUID().toString(),sessionId,activeTaskId?:"session",type,detail.take(1000)))}
 fun clearError(){ui.update{it.copy(error=null,notice=null)}}
 fun install(){if(downloadProgress.value!=null)return;scope.launch{try{app.local.install()}catch(e:CancellationException){throw e}catch(e:Exception){ui.update{it.copy(error=e.message?:"Download failed. Retry in Settings.")}}}}
 fun connect(key:String,model:String){
  try{require(model.matches(Regex("[A-Za-z0-9._:-]+/[A-Za-z0-9._:/-]+"))){"Enter an OpenRouter model ID, such as provider/model."};app.credentials.save(key.trim());prefs.edit().putString("model",model).apply();ui.update{it.copy(connected=true,model=model,notice="Key saved securely. The first Maxx request will validate access.")}}
  catch(e:Exception){ui.update{it.copy(error=e.message?:"Could not save the connection.")}}
 }
 fun disconnect(){cancel();app.credentials.disconnect();ui.update{it.copy(connected=false,cloud=null,notice="Disconnected on this device. You can revoke the key in OpenRouter.")}}
 fun send(input:String){
  if(ui.value.busy || ui.value.action!=null || ui.value.cloud!=null)return
  val request=input.trim();if(request.isEmpty())return
  ui.update{it.copy(busy=true,error=null)}
  task=scope.launch {
   initialized.await()
   val mode=ui.value.mode
   val id=UUID.randomUUID().toString()
   activeTaskId=id
   log("request","Mode ${mode.name}; model ${sessionInfo.value.modelId}")
   try{
    ui.update{it.copy(busy=true,error=null,notice=null,status="Understanding your request…")}
    app.memory.dao.task(TaskRow(id,request,mode.name,"ROUTING",sessionId=sessionId))
    app.memory.dao.message(MessageRow(UUID.randomUUID().toString(),"user",request,mode.name,sessionId=sessionId))
    when(val route=RoutePolicy().route(request,mode)){
     is Route.Clarify->answer(route.message,mode)
     is Route.Save->{check(options.value.memoryWrite&&options.value.toolCalls){"Memory writes are disabled for this session."};val p=policy.propose("notes.create",route.text);app.memory.propose(p,sessionId);ui.update{it.copy(action=p,status="Your approval is needed.")}}
     is Route.Search->{check(options.value.memoryRead&&options.value.toolCalls){"Memory reads are disabled for this session."};val found=app.memory.search(route.query,sessionId);answer(if(found.isEmpty())"No matching notes yet. Try a word from the note, or save one with ‘Remember that…’." else found.joinToString("\n\n"){"${it.text}\n[Memory · ${it.id.take(8)}]"},ExecutionMode.TINY)}
     is Route.Generate->{
      val found=if(options.value.memoryRead)app.memory.search(request,sessionId)else emptyList()
      val history=app.memory.dao.recentMessages(sessionId).reversed().dropLast(1).map{ChatTurn(it.speaker,it.text)}
      val context=broker.build(request,mode,found,history)
      if(mode==ExecutionMode.MAXX){
       check(options.value.network){"Network is disabled for this session."}
       check(app.credentials.read()!=null){"Connect OpenRouter in Settings to use Maxx. Tiny remains available offline."}
       ui.update{it.copy(cloud=PendingCloud(context,prefs.getString("model","")?:""),status="Review what leaves your phone.")}
      }else generate(context,mode,ui.value.model,null)
     }
    }
    app.memory.dao.task(TaskRow(id,request,mode.name,if(ui.value.action!=null||ui.value.cloud!=null)"AWAITING_APPROVAL" else "COMPLETED",sessionId=sessionId))
   }catch(e:CancellationException){withContext(NonCancellable){app.memory.dao.task(TaskRow(id,request,mode.name,"CANCELLED",sessionId=sessionId))};throw e}
   catch(e:Exception){ui.update{it.copy(error=e.message?:"Something went wrong. Please try again.",status="Let’s try that again.")};app.memory.dao.task(TaskRow(id,request,mode.name,"FAILED",sessionId=sessionId))}
   finally{ui.update{it.copy(busy=false,streaming="")}}
  }
 }
 private suspend fun generate(context:ContextEnvelope,mode:ExecutionMode,model:String,consent:CloudConsent?){
  ui.update{it.copy(busy=true,status=if(mode==ExecutionMode.TINY)"Thinking on your phone…" else "Thinking with Maxx…",streaming="")}
  val result=app.agent.run(context,mode,if(mode==ExecutionMode.TINY)sessionInfo.value.modelId else model,consent,options.value,
   ai.petologic.paladino.runtime.AgentTools(
    search={query->check(options.value.memoryRead&&options.value.toolCalls){"Memory read permission was revoked."};app.memory.search(query,sessionId).joinToString("\n"){"[${it.id.take(8)}] ${it.text}"}.ifBlank{"No matching notes."}},
    save={text->
     check(options.value.memoryWrite&&options.value.toolCalls){"Memory write permission was revoked."}
     val proposal=policy.propose("notes.create",text);app.memory.propose(proposal,sessionId)
     val approval=CompletableDeferred<Boolean>();toolApproval=approval;ui.update{it.copy(action=proposal,status="Approve this tool action.")}
     try{if(approval.await()){check(options.value.memoryWrite){"Memory write permission was revoked."};app.memory.executeNote(proposal,proposal.argumentHash,sessionId)}else{app.memory.cancel(proposal.id);"User declined. No note was saved."}}finally{toolApproval=null;ui.update{it.copy(action=null)}}
    }
   ),onEvent={type,detail->log(type,detail)}){text->ui.update{it.copy(streaming=text)}}
  app.local.metrics.value?.takeIf{mode==ExecutionMode.TINY}?.let{log("inference","model=${it.modelId}; prompt=${it.promptTokens}; output=${it.outputTokens}; context=${it.contextTokens}; loadMs=${it.loadMs}; decodeUs=${it.decodeMicros}; peakPssKb=${it.peakSampledPssKb}")}
  answer(result,mode)
 }
 private suspend fun answer(text:String,mode:ExecutionMode){log("response","${text.length} characters; ${mode.name}");app.memory.dao.message(MessageRow(UUID.randomUUID().toString(),"assistant",text,mode.name,sessionId=sessionId));ui.update{it.copy(status="At your side.",streaming="")}}
 fun approveCloud(){
  val pending=ui.value.cloud?:return
  ui.update{it.copy(cloud=null,busy=true,error=null)}
  task=scope.launch{try{check(options.value.network){"Network permission was revoked."};generate(pending.context,ExecutionMode.MAXX,pending.model,CloudConsent(pending.context.digest,pending.model,Instant.now().plusSeconds(300)));finishTask("COMPLETED")}catch(e:CancellationException){withContext(NonCancellable){finishTask("CANCELLED")};throw e}catch(e:Exception){finishTask("FAILED");ui.update{it.copy(error=e.message?:"Cloud request failed.")}}finally{ui.update{it.copy(busy=false,streaming="",status="At your side.")}}}
 }
 fun denyCloud(){val id=activeTaskId;scope.launch{id?.let{app.memory.dao.taskStatus(it,"CANCELLED")}};ui.update{it.copy(cloud=null,status="Nothing was sent to the cloud.")}}
 fun deleteNote(note:NoteRow){if(ui.value.busy||ui.value.action!=null||ui.value.cloud!=null)return;activeTaskId=null;scope.launch{val p=policy.propose("notes.delete",note.id);app.memory.propose(p,sessionId);ui.update{it.copy(action=p)}}}
 fun approveAction(){
  toolApproval?.let{it.complete(true);ui.update{state->state.copy(action=null,status="Continuing…")};return}
  val p=ui.value.action?:return
  ui.update{it.copy(action=null,busy=true)}
  task=scope.launch{try{check(options.value.memoryWrite&&options.value.toolCalls){"Memory writes are disabled for this session."};val result=app.memory.executeNote(p,p.argumentHash,sessionId);answer(result,ExecutionMode.TINY);finishTask("COMPLETED");try{app.memory.reconcile()}catch(_:Exception){ui.update{it.copy(notice="Note saved. Search index will retry on next launch.")}}}catch(e:CancellationException){withContext(NonCancellable){finishTask("CANCELLED")};throw e}catch(e:Exception){finishTask("FAILED");ui.update{it.copy(error=e.message?:"Action failed.")}}finally{ui.update{it.copy(busy=false)}}}
 }
 fun denyAction(){toolApproval?.let{it.complete(false);ui.update{state->state.copy(action=null)};return};val p=ui.value.action?:return;val id=activeTaskId;ui.update{it.copy(action=null,status="Action cancelled.")};scope.launch{app.memory.cancel(p.id);id?.let{app.memory.dao.taskStatus(it,"CANCELLED")}}}
 fun cancel(){val wasRunning=task?.isActive==true;task?.cancel();toolApproval?.cancel();ui.value.action?.let{scope.launch{app.memory.cancel(it.id)}};ui.update{it.copy(busy=wasRunning,streaming="",cloud=null,action=null,status=if(wasRunning)"Stopping…" else "Stopped. You’re in control.")}}
}
