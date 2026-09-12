package ai.petologic.paladino

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.petologic.core.*

@Composable private fun ControlPage(title:String,content:@Composable ColumnScope.()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(tr(title),fontSize=28.sp,fontWeight=FontWeight.Bold);content();Spacer(Modifier.height(24.dp))
 }
}
@Composable fun SessionsScreen(vm:PaladinoViewModel,onOpen:()->Unit){
 val sessions by vm.hub.sessions.collectAsStateWithLifecycle()
 val active by vm.session.collectAsStateWithLifecycle()
 var archived by rememberSaveable{mutableStateOf(false)}
 ControlPage("Conversations"){
  Text(tr("Separate conversations and private memory. One shared local runtime."),color=Muted)
  Button(onClick={vm.hub.create();onOpen()}){Text(tr("New conversation"))}
  Row{FilterChip(archived,{archived=!archived},label={Text(tr("Show archived"))})}
  sessions.filter{it.archived==archived}.forEach{session->
   Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text(sessionLabel(session.title)+(if(session.id==active.id)tr(" · current") else ""),fontWeight=FontWeight.Bold)
    Text("${session.mode} · ${ModelCatalog.artifacts.find{it.id==session.modelId}?.let{it.model+" "+it.quantization}?:session.modelId}",fontSize=12.sp)
    session.parentId?.let{Text(tr("Branched conversation · independent memory"),fontSize=12.sp,color=Muted)}
    if(archived)TextButton(onClick={vm.hub.archive(session.id,false)}){Text(tr("Restore"))}
    else {
     Row{TextButton(onClick={vm.hub.open(session.id);onOpen()}){Text(tr("Open"))};TextButton(onClick={vm.hub.branch(session.id);onOpen()}){Text(tr("Branch"))};TextButton(onClick={vm.hub.archive(session.id,true)}){Text(tr("Archive"))}}
    }
   }}
  }
 }
}
@Composable fun OrchestrationScreen(vm:PaladinoViewModel){
 val options by vm.options.collectAsStateWithLifecycle()
 val notes by vm.notes.collectAsStateWithLifecycle()
 val session by vm.session.collectAsStateWithLifecycle()
 var section by rememberSaveable{mutableStateOf("Overview")}
 var instructions by rememberSaveable(session.id,options.instructions){mutableStateOf(options.instructions)}
 ControlPage("Orchestration"){
  Text(sessionLabel(session.title)+" · 0xPaladino",color=Muted)
  if(section!="Overview")TextButton(onClick={section="Overview"}){Text(tr("← All controls"))}
  when(section){
   "Overview"->listOf("Memory","Tools","Permissions","Rules & instructions","Skills & MCPs","Integrations").forEach{label->OutlinedCard(onClick={section=label},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(20.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(tr(label),modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text("›",color=Gold,fontSize=24.sp)}}}
   "Memory"->{
    Text(tr("What Paladino can know"),fontSize=20.sp)
    PermissionSwitch("Read this session’s memory",options.memoryRead){vm.updateOptions(options.copy(memoryRead=it))}
    PermissionSwitch("Write memory after approval",options.memoryWrite){vm.updateOptions(options.copy(memoryWrite=it))}
    if(notes.isEmpty())Text(tr("No notes yet. Ask Paladino to ‘Remember that…’ and approve the note."),color=Muted)
    notes.forEach{note->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(note.text);Text(tr("Private · this session only"),fontSize=12.sp,color=Muted);TextButton(onClick={vm.deleteNote(note)},modifier=Modifier.testTag("delete:"+note.text)){Text(tr("Delete note"))}}}}
   }
   "Tools"->{
    PermissionSwitch("Tool calling",options.toolCalls){vm.updateOptions(options.copy(toolCalls=it))}
    Text(tr("Available: search private notes, propose a note, delete a selected note. Every write requires approval. Model tool loops run in Tiny; Maxx is text-only in this preview."))
    val context=androidx.compose.ui.platform.LocalContext.current
    Text(tr("Tools skills can register"),fontSize=20.sp)
    ai.petologic.paladino.skills.SkillRegistry.definitions.forEach{skill->
     val state=ai.petologic.paladino.skills.SkillRegistry.states(context)[skill.id]?:ai.petologic.paladino.skills.SkillState.OFF
     Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      Text(tr(skill.name),fontWeight=FontWeight.Bold)
      skill.tools.forEach{spec->Text("· "+tr(spec.label),fontSize=13.sp)}
      Text(tr(when(state){
       ai.petologic.paladino.skills.SkillState.OFF->"Never registered"
       ai.petologic.paladino.skills.SkillState.AUTO->"Activates automatically when a message matches"
       ai.petologic.paladino.skills.SkillState.PINNED->"Always available in Tiny sessions"
      }),fontSize=12.sp,color=Muted)
     }}
    }
    Text(tr("Maximum %1\$s tools across %2\$s model turns. Identical tool calls are stopped.",options.maxToolCalls,options.maxHops),color=Muted)
   }
   "Permissions"->{
    Text(tr("Session access"),fontSize=20.sp)
    PermissionSwitch("Allow cloud requests after review",options.network){vm.updateOptions(options.copy(network=it))}
    PermissionSwitch("Read memory",options.memoryRead){vm.updateOptions(options.copy(memoryRead=it))}
    PermissionSwitch("Write memory after approval",options.memoryWrite){vm.updateOptions(options.copy(memoryWrite=it))}
    PermissionSwitch("Use tools",options.toolCalls){vm.updateOptions(options.copy(toolCalls=it))}
    Text(tr("Model setup uses the files/folder you select in Settings. The agent cannot browse those files. No camera, microphone, contacts, calendar, location, accessibility or overlay access is requested."),color=Muted)
    Text(tr("Background execution is not enabled in this build. Leaving the app stops active requests. Android may end the process; saved conversations remain available."),color=Muted)
   }
   "Rules & instructions"->{
    Text(tr("Paladino’s safety rules remain enforced by code. These instructions guide this session’s local responses."))
    OutlinedTextField(instructions,{instructions=it.take(2000)},label={Text(tr("Session instructions"))},modifier=Modifier.fillMaxWidth(),minLines=4)
    Button(onClick={vm.updateOptions(options.copy(instructions=instructions))}){Text(tr("Save instructions"))}
    Text(tr("Private notes and Tiny history are excluded from Maxx. Unsupported tool calls cannot grant access."),color=Muted)
   }
   "Skills & MCPs"->SkillsAndMcpSection()
   "Integrations"->Text(tr("OpenRouter, OpenAI and Z.ai are available in Settings with your API key. Web search, other app integrations, vision and Google/Pixel companion accelerators are planned, with no access granted by default."))
  }
 }
}
@Composable private fun PermissionSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(tr(label),Modifier.weight(1f).padding(top=14.dp));Switch(checked,onChange)}
}
private fun skillStateLabel(state:ai.petologic.paladino.skills.SkillState)=when(state){
 ai.petologic.paladino.skills.SkillState.OFF->"Off"
 ai.petologic.paladino.skills.SkillState.AUTO->"Auto"
 ai.petologic.paladino.skills.SkillState.PINNED->"Always"
}
/** Skills live in Orchestration: OFF removes everything, AUTO wakes per message, PINNED is always on. */
@Composable private fun SkillsAndMcpSection(){
 val context=androidx.compose.ui.platform.LocalContext.current
 var revision by remember{mutableIntStateOf(0)}
 var openSkillId by rememberSaveable{mutableStateOf("")}
 val open=ai.petologic.paladino.skills.SkillRegistry.definitions.firstOrNull{it.id==openSkillId}
 if(open!=null){
  TextButton(onClick={openSkillId=""}){Text(tr("← All skills"))}
  key(revision){
   val state=ai.petologic.paladino.skills.SkillRegistry.states(context)[open.id]?:ai.petologic.paladino.skills.SkillState.OFF
   Text(tr(open.name),fontSize=20.sp,fontWeight=FontWeight.Bold)
   Text(tr(open.description),color=Muted)
   Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
    listOf(ai.petologic.paladino.skills.SkillState.OFF,ai.petologic.paladino.skills.SkillState.AUTO,ai.petologic.paladino.skills.SkillState.PINNED).forEach{value->
     FilterChip(selected=state==value,onClick={ai.petologic.paladino.skills.SkillRegistry.setSkillState(context,open.id,value);revision++},label={Text(tr(skillStateLabel(value)),fontSize=11.sp)})
    }
   }
   if(state!=ai.petologic.paladino.skills.SkillState.OFF){
    val toolStates=open.tools.map{it to ai.petologic.paladino.skills.SkillRegistry.toolEnabled(context,open,it)}
    for((spec,enabled) in toolStates){
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
      Column(Modifier.weight(1f)){
       Text("· "+tr(spec.label),style=MaterialTheme.typography.bodyMedium)
       spec.androidPermission?.let{perm->
        val granted=context.checkSelfPermission(perm)==android.content.pm.PackageManager.PERMISSION_GRANTED
        Text(tr(if(granted)"Granted" else "Not granted"),fontSize=12.sp,color=if(granted)Gold else Muted)
       }
      }
      Switch(checked=enabled,onCheckedChange={on->ai.petologic.paladino.skills.SkillRegistry.setToolEnabled(context,open.id,spec.id,on);revision++})
     }
    }
    val enabledCount=toolStates.count{it.second}
    Text(tr("Idle ~0 tokens · active ~%1\$s tokens · %2\$s tools exposed",(open.promptStub.length/4+enabledCount*15).toString(),enabledCount.toString()),style=MaterialTheme.typography.bodySmall,color=Gold)
   }
  }
 }else{
  Text(tr("Skills teach Paladino what it can do. Auto wakes a skill only when a message needs it."),color=Muted)
  key(revision){
   ai.petologic.paladino.skills.SkillRegistry.definitions.forEach{skill->
    val state=ai.petologic.paladino.skills.SkillRegistry.states(context)[skill.id]?:ai.petologic.paladino.skills.SkillState.OFF
    OutlinedCard(onClick={openSkillId=skill.id},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(20.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
     Column(Modifier.weight(1f)){Text(tr(skill.name),style=MaterialTheme.typography.titleMedium);Text(tr(skill.description),fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=4.dp))}
     Text(tr(skillStateLabel(state)),fontSize=12.sp,color=Gold);Text("›",color=Gold,fontSize=24.sp)
    }}
   }
  }
 }
 Text(tr("External skills and MCP servers are not connected in this preview. The bundled Paladino manifest allows only the implemented note tools. Future packages must declare their tools and permissions before activation."),color=Muted)
}
@Composable fun ConsoleScreen(vm:PaladinoViewModel){
 val events by vm.events.collectAsStateWithLifecycle()
 val session by vm.session.collectAsStateWithLifecycle()
 ControlPage("Console"){
  Text(sessionLabel(session.title)+tr(" · execution history"),color=Muted)
  Text(tr("Metadata only. API keys and prompt contents are excluded."),fontSize=12.sp,color=Muted)
  if(events.isEmpty())Text(tr("Run a request to see its steps here."))
  events.forEach{event->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
   Text(event.type,fontWeight=FontWeight.Bold,color=Gold)
   Text(event.detail,fontSize=12.sp)
   Text(java.time.Instant.ofEpochMilli(event.createdAt).toString(),fontSize=10.sp,color=Muted)
  }}}
 }
}
@Composable fun ModelManager(vm:PaladinoViewModel){
 val installed by vm.library.installed.collectAsStateWithLifecycle()
 val status by vm.library.status.collectAsStateWithLifecycle()
 val progress by vm.library.progress.collectAsStateWithLifecycle()
 val session by vm.session.collectAsStateWithLifecycle()
 val state by vm.ui.collectAsStateWithLifecycle()
 val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importModel)}
 val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let(vm::scanFolder)}
 Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text(tr("Model library"),fontSize=20.sp,fontWeight=FontWeight.Bold)
  Text(tr("Already downloaded? Select a file or folder. Exact publisher, model and quantization are verified before reuse. Android hides other apps’ private downloads."),fontSize=13.sp)
  Text(tr("Reuse copies the verified file into Paladino’s storage; it avoids another network download."),fontSize=12.sp,color=Muted)
  Row{TextButton(onClick={filePicker.launch(arrayOf("*/*"))},enabled=progress==null){Text(tr("Use existing file"))};TextButton(onClick={folderPicker.launch(null)},enabled=progress==null){Text(tr("Choose folder"))}}
  Text(tr(status),fontSize=12.sp,color=Gold)
  ModelCatalog.artifacts.filter{it.id!="minilm"}.forEach{artifact->
   HorizontalDivider()
   Text(artifact.model+" · "+artifact.quantization,fontWeight=FontWeight.Bold)
   Text(tr("${artifact.size/1_000_000} MB"+(if(artifact.id!="lfm350")tr(" · experimental; phone validation pending") else tr(" · baseline"))),fontSize=12.sp,color=Muted)
   if(artifact.id in installed)OutlinedButton(onClick={vm.selectModel(artifact.id)},enabled=!state.busy&&session.modelId!=artifact.id){Text(tr(if(session.modelId==artifact.id)"Selected for this session" else "Use in this session"))}
   else OutlinedButton(onClick={vm.downloadModel(artifact.id)},enabled=progress==null){Text(tr("Download %1\$s %2\$s",artifact.model,artifact.quantization))}
  }
 }}
}
@Composable fun AdvancedSettings(vm:PaladinoViewModel){
 val saved by vm.options.collectAsStateWithLifecycle()
 val state by vm.ui.collectAsStateWithLifecycle()
 val session by vm.session.collectAsStateWithLifecycle()
 var expanded by rememberSaveable{mutableStateOf(false)}
 var edit by remember(session.id,saved){mutableStateOf(saved)}
 var error by remember{mutableStateOf<String?>(null)}
 Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  TextButton(onClick={expanded=!expanded}){Text(tr(if(expanded)"Close advanced settings" else "Advanced model settings"))}
  if(expanded){
   Text(tr("%1\$s · CPU runtime. Defaults are the safe starting point. Changes apply to the next request.",sessionLabel(session.title)),fontSize=12.sp,color=Muted)
   IntSetting("Context tokens",edit.contextTokens){edit=edit.copy(contextTokens=it)}
   IntSetting("Maximum output tokens",edit.outputTokens){edit=edit.copy(outputTokens=it)}
   IntSetting("Reserved tool tokens",edit.toolReserve){edit=edit.copy(toolReserve=it)}
   IntSetting("Maximum model turns",edit.maxHops){edit=edit.copy(maxHops=it)}
   IntSetting("Maximum tool calls",edit.maxToolCalls){edit=edit.copy(maxToolCalls=it)}
   IntSetting("Malformed-call retries",edit.maxRetries){edit=edit.copy(maxRetries=it)}
   IntSetting("Seconds per model turn",edit.hopTimeoutSeconds){edit=edit.copy(hopTimeoutSeconds=it)}
   IntSetting("Total execution seconds",edit.totalTimeoutSeconds){edit=edit.copy(totalTimeoutSeconds=it)}
   FloatSetting("Temperature",edit.temperature){edit=edit.copy(temperature=it)}
   FloatSetting("Top-p",edit.topP){edit=edit.copy(topP=it)}
   IntSetting("Top-k",edit.topK){edit=edit.copy(topK=it)}
   FloatSetting("Repeat penalty",edit.repeatPenalty){edit=edit.copy(repeatPenalty=it)}
   IntSetting("Seed",edit.seed){edit=edit.copy(seed=it)}
   IntSetting("CPU threads",edit.threads){edit=edit.copy(threads=it)}
   IntSetting("Batch size",edit.batch){edit=edit.copy(batch=it)}
   IntSetting("Micro-batch size",edit.microBatch){edit=edit.copy(microBatch=it)}
   PermissionSwitch("Memory-map model",edit.mmap){edit=edit.copy(mmap=it)}
   Text(tr("Automatic summarization, reasoning mode, GPU offload and KV quantization are not exposed until implemented and tested."),fontSize=12.sp,color=Muted)
   error?.let{Text(tr(it),color=MaterialTheme.colorScheme.error)}
   Row{Button(onClick={try{edit.validate();vm.updateOptions(edit);error=null}catch(_:IllegalArgumentException){error="Values are outside supported limits. Context 1024–8192; output 16–1024; reserve 128–2048. Check timeouts, batch sizes and sampling values."}},enabled=!state.busy){Text(tr("Apply"))};TextButton(onClick={edit=SessionOptions(instructions=saved.instructions,memoryRead=saved.memoryRead,memoryWrite=saved.memoryWrite,toolCalls=saved.toolCalls,network=saved.network);error=null}){Text(tr("Reset defaults"))}}
  }
 }}
}
@Composable private fun IntSetting(label:String,value:Int,onChange:(Int)->Unit){
 var text by remember(value){mutableStateOf(value.toString())}
 OutlinedTextField(text,{text=it;it.toIntOrNull()?.let(onChange)},label={Text(tr(label))},singleLine=true,modifier=Modifier.fillMaxWidth())
}
@Composable private fun FloatSetting(label:String,value:Float,onChange:(Float)->Unit){
 var text by remember(value){mutableStateOf(value.toString())}
 OutlinedTextField(text,{text=it;it.toFloatOrNull()?.let(onChange)},label={Text(tr(label))},singleLine=true,modifier=Modifier.fillMaxWidth())
}
