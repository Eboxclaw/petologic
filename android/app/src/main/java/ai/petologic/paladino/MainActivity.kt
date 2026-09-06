package ai.petologic.paladino

import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.petologic.core.*
import ai.petologic.paladino.data.NoteRow

internal val Ink=Color(0xFF101511)
internal val Panel=Color(0xFF1C241D)
internal val Lime=Color(0xFFC6F279)
internal val Muted=Color(0xFFADB7AA)
internal val Cream=Color(0xFFF2F4E8)

class MainActivity:ComponentActivity(){
 override fun onStop(){super.onStop();if(!isChangingConfigurations)(application as PaladinoApplication).sessionHub.onUiHidden()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));setContent{
  MaterialTheme(colorScheme=darkColorScheme(primary=Lime,onPrimary=Ink,background=Ink,surface=Panel,onSurface=Cream,onBackground=Cream,secondary=Lime)){
   PaladinoScreen()
  }
 }}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PaladinoScreen(vm:PaladinoViewModel=viewModel()){
 val state by vm.ui.collectAsStateWithLifecycle()
 val messages by vm.messages.collectAsStateWithLifecycle()
 val notes by vm.notes.collectAsStateWithLifecycle()
 val modelStatus by vm.modelStatus.collectAsStateWithLifecycle()
 val modelReady by vm.modelReady.collectAsStateWithLifecycle()
 val progress by vm.downloadProgress.collectAsStateWithLifecycle()
 var tab by rememberSaveable{mutableIntStateOf(0)}
 val drawer=rememberDrawerState(DrawerValue.Closed)
 val uiScope=rememberCoroutineScope()
 val session by vm.session.collectAsStateWithLifecycle()
 var draft by rememberSaveable(session.id){mutableStateOf("")}
 val scroll=rememberLazyListState()
 LaunchedEffect(messages.size,state.streaming){if(messages.isNotEmpty())scroll.animateScrollToItem(messages.size-1)}
 ModalNavigationDrawer(drawerState=drawer,drawerContent={ModalDrawerSheet(drawerContainerColor=Ink){
  TextButton(onClick={uiScope.launch{drawer.close()}}){Text("← Back to chat")}
  SessionsScreen(vm,onOpen={tab=0;uiScope.launch{drawer.close()}})
 }}){
 Scaffold(containerColor=Ink,bottomBar={
  NavigationBar(containerColor=Ink,tonalElevation=0.dp){
   listOf("Chat" to Icons.Outlined.ChatBubbleOutline,"Orchestration" to Icons.Outlined.Hub,"Console" to Icons.Outlined.Terminal,"Settings" to Icons.Outlined.Tune).forEachIndexed{i,item->
    NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(item.second,item.first)},label={Text(item.first)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Ink,indicatorColor=Lime,selectedTextColor=Lime,unselectedTextColor=Muted,unselectedIconColor=Muted))
   }
  }
 }){padding->
  Column(Modifier.fillMaxSize().padding(padding).imePadding()){
   Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
    Box(Modifier.size(9.dp).background(Lime,CircleShape));Spacer(Modifier.width(9.dp))
    Text("PETOLOGIC",fontSize=13.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
    Spacer(Modifier.weight(1f));Text("EARLY ACCESS",fontSize=10.sp,letterSpacing=1.sp,color=Muted)
   }
   when(tab){
    0->{
     Row(Modifier.padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically){
      IconButton(onClick={uiScope.launch{drawer.open()}}){Icon(Icons.Outlined.Menu,"Conversations")}
      Column(Modifier.weight(1f)){Text("Chat",fontSize=if(messages.isEmpty())30.sp else 24.sp,fontWeight=FontWeight.Bold);Text(session.title+" · "+state.status,color=Muted,fontSize=12.sp)}
      Surface(color=Panel,shape=RoundedCornerShape(24.dp)){Row(Modifier.padding(4.dp)){
       ExecutionMode.entries.forEach{mode->
        val selected=mode==state.mode
        TextButton(onClick={vm.mode(mode)},enabled=!state.busy&&state.action==null&&state.cloud==null,colors=ButtonDefaults.textButtonColors(containerColor=if(selected)Lime else Color.Transparent,contentColor=if(selected)Ink else Muted),contentPadding=PaddingValues(horizontal=12.dp)) {Text(if(mode==ExecutionMode.TINY)"Tiny" else "Maxx",fontWeight=FontWeight.Bold)}
       }
      }}
     }
     if(messages.isEmpty()){
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally){
       Spacer(Modifier.height(20.dp))
       Box(Modifier.fillMaxWidth().height(225.dp).background(Brush.radialGradient(listOf(Color(0xFF35432A),Ink))),contentAlignment=Alignment.Center){
        Image(painterResource(R.drawable.paladino),"0xPaladino, your blue and gold armored companion",Modifier.size(210.dp))
       }
       Text("Small companion.\nA little more possible.",fontSize=27.sp,lineHeight=33.sp,fontWeight=FontWeight.SemiBold)
       Spacer(Modifier.height(10.dp))
       Text("A thought to untangle. A detail to remember.\nI’m right here, on your phone.",fontSize=14.sp,lineHeight=21.sp,color=Muted)
       Spacer(Modifier.height(20.dp))
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        SuggestionChip(onClick={draft="Remember that "},label={Text("Remember something",fontSize=12.sp)},icon={Icon(Icons.Outlined.Add,"",Modifier.size(15.dp))})
        SuggestionChip(onClick={draft="Help me plan my day"},label={Text("Make a plan",fontSize=12.sp)})
       }
       if(!modelReady) TextButton(onClick={tab=3}){Text("Set up your local brain →",color=Lime)}
      }
     }else{
      LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
       items(messages,key={it.id}){message->
        val isUser=message.speaker=="user"
        Column(Modifier.fillMaxWidth(),horizontalAlignment=if(isUser)Alignment.End else Alignment.Start){
         Text(if(isUser)"YOU" else "PALADINO · ${message.mode}",color=Muted,fontSize=10.sp,letterSpacing=1.sp,modifier=Modifier.padding(bottom=6.dp))
         Surface(color=if(isUser)Color(0xFF2D3829) else Panel,shape=RoundedCornerShape(18.dp)){
          Text(message.text,Modifier.padding(16.dp),fontSize=15.sp,lineHeight=23.sp)
         }
        }
       }
       if(state.busy)item{Column{Text(if(state.streaming.isBlank())state.status else state.streaming,color=Cream,modifier=Modifier.padding(12.dp));LinearProgressIndicator(Modifier.fillMaxWidth(),color=Lime,trackColor=Panel)}}
      }
     }
     Column(Modifier.padding(horizontal=20.dp,vertical=8.dp)){
      if(state.mode==ExecutionMode.MAXX)Text("Cloud mode · you review every request before sending",fontSize=10.sp,color=Muted,modifier=Modifier.padding(bottom=8.dp))
      Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
       OutlinedTextField(value=draft,onValueChange={draft=it},placeholder={Text("What’s on your mind?",fontSize=14.sp)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(24.dp),maxLines=4,enabled=!state.busy,colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color(0xFF384134)))
       FilledIconButton(onClick={if(state.busy)vm.cancel() else if(draft.isNotBlank()){vm.send(draft);draft=""}},modifier=Modifier.size(50.dp),enabled=state.busy||draft.isNotBlank()){
        Icon(if(state.busy)Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,if(state.busy)"Stop response" else "Send message")
       }
      }
      Text(if(state.mode==ExecutionMode.TINY)"ON YOUR DEVICE  ·  PRIVATE BY DEFAULT" else "SAME PALADINO  ·  MORE CAPABILITY",fontSize=9.sp,letterSpacing=1.sp,color=Muted,modifier=Modifier.padding(top=10.dp).align(Alignment.CenterHorizontally))
     }
    }
    1->OrchestrationScreen(vm)
    2->ConsoleScreen(vm)
    3->Settings(state,modelStatus,modelReady,progress,vm)
   }
  }
 }
 }
 state.action?.let{action->AlertDialog(onDismissRequest=vm::denyAction,title={Text(if(action.tool=="notes.create")"Keep this in memory?" else "Delete this note?")},text={Column{
  Text(if(action.tool=="notes.create")action.argument else notes.find{it.id==action.argument}?.text?:"Selected note")
  Spacer(Modifier.height(12.dp));Text("This action happens only on your phone.",color=Muted,fontSize=12.sp)
 }},confirmButton={TextButton(onClick=vm::approveAction){Text(if(action.tool=="notes.create")"Save note" else "Delete note")}},dismissButton={TextButton(onClick=vm::denyAction){Text("Cancel")}})}
 state.cloud?.let{pending->AlertDialog(onDismissRequest=vm::denyCloud,title={Text("Let Paladino use Maxx?")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
  Text("OpenRouter · ${pending.model}",fontWeight=FontWeight.Bold)
  Text("This request and Paladino’s instructions will leave your phone. Your private notes and Tiny history are excluded. Up to 512 output tokens; your provider may charge for usage.",color=Muted,modifier=Modifier.padding(vertical=12.dp))
  Text("REQUEST",fontSize=10.sp,color=Lime);Text(pending.context.user,modifier=Modifier.padding(vertical=8.dp))
  Text("PALADINO INSTRUCTIONS",fontSize=10.sp,color=Lime);Text(pending.context.system,fontSize=12.sp,color=Muted)
 }},confirmButton={TextButton(onClick=vm::approveCloud){Text("Send to Maxx")}},dismissButton={TextButton(onClick=vm::denyCloud){Text("Keep local")}})}
 if(state.error!=null||state.notice!=null)AlertDialog(onDismissRequest=vm::clearError,title={Text(if(state.error!=null)"A quick heads-up" else "All set")},text={Text(state.error?:state.notice?:"")},confirmButton={TextButton(onClick=vm::clearError){Text("Got it")}})
}

@Composable private fun Settings(state:PaladinoUiState,status:String,ready:Boolean,progress:Float?,vm:PaladinoViewModel){
 val semanticReady by vm.semanticReady.collectAsStateWithLifecycle()
 val semanticInstalling by vm.semanticInstalling.collectAsStateWithLifecycle()
 var key by remember{mutableStateOf("")}
 var model by rememberSaveable(state.model){mutableStateOf(state.model)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
  Text("Make yourself at home.",fontSize=30.sp,fontWeight=FontWeight.Bold)
  Text("Your companion. Your boundaries.",color=Muted)
  ModelManager(vm)
  AdvancedSettings(vm)
  Surface(shape=RoundedCornerShape(20.dp),color=Panel){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Row{Icon(Icons.Outlined.Memory,null,tint=Lime);Spacer(Modifier.width(12.dp));Text("Tiny · your local brain",fontWeight=FontWeight.Bold)}
   Text(status,color=Muted)
   Text("LFM2.5-350M lives on your phone. Download once, then chat offline. Files are verified before installation.",fontSize=13.sp,color=Muted)
   if(progress!=null)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
   Button(onClick=vm::install,enabled=!ready&&progress==null,modifier=Modifier.fillMaxWidth()){Text(if(ready)"Ready for offline use" else if(progress!=null)"Downloading…" else "Download · 229 MB")}
  }}
  Surface(shape=RoundedCornerShape(20.dp),color=Panel){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("Memory that understands meaning",fontWeight=FontWeight.Bold)
   Text("A small, separate encoder helps find notes by paraphrase. English preview · latest 200 notes. Keyword search works without it.",fontSize=13.sp,color=Muted)
   Button(onClick=vm::installSemantic,enabled=!semanticReady&&!semanticInstalling){Text(if(semanticReady)"Semantic memory ready" else if(semanticInstalling)"Downloading…" else "Set up semantic memory · 23 MB")}
  }}
  Surface(shape=RoundedCornerShape(20.dp),color=Panel){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Row{Icon(Icons.Outlined.CloudQueue,null,tint=Lime);Spacer(Modifier.width(12.dp));Text("Maxx · a little extra reach",fontWeight=FontWeight.Bold)}
   Text("Connect your OpenRouter key for harder questions. Paladino asks before sending context. Cloud usage is billed by your provider.",fontSize=13.sp,color=Muted)
   if(state.connected){Text("Key stored securely on this device",color=Lime,fontSize=12.sp);Text(state.model,fontSize=13.sp);OutlinedButton(onClick=vm::disconnect){Text("Disconnect")}}
   else{
    OutlinedTextField(key,{key=it},label={Text("OpenRouter API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
    OutlinedTextField(model,{model=it},label={Text("Model ID")},placeholder={Text("provider/model")},singleLine=true,modifier=Modifier.fillMaxWidth())
    Button(onClick={vm.connect(key,model.trim());key=""},enabled=key.isNotBlank()&&model.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Save connection")}
   }
  }}
  Row(Modifier.padding(vertical=8.dp)){Icon(Icons.Outlined.Shield,null,tint=Muted);Spacer(Modifier.width(12.dp));Text("No account needed for Tiny. No hidden cloud fallback. Private memory stays here.",fontSize=12.sp,color=Muted)}
  Text("0xPaladino 0.1 · Native Android preview\nGoogle companion accelerators are not enabled yet.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(bottom=24.dp))
 }
}
