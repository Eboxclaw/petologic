package ai.petologic.paladino

import ai.petologic.paladino.runtime.CloudProvider

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

class MainActivity:ComponentActivity(){
 private var openChatRequest by mutableIntStateOf(0)
 override fun onStop(){super.onStop();if(!isChangingConfigurations)(application as PaladinoApplication).sessionHub.onUiHidden()}
 override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);setIntent(intent);openWidgetSession(intent)}
 private fun openWidgetSession(intent:android.content.Intent){if(intent.getBooleanExtra("open_chat",false))openChatRequest++;intent.getStringExtra("widget_session")?.takeIf{it.isNotBlank()}?.let{(application as PaladinoApplication).sessionHub.openFromWidget(it)}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);openWidgetSession(intent);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));setContent{
  MaterialTheme(colorScheme=PetColors){
   PaladinoScreen(openChatRequest=openChatRequest)
  }
 }}
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun PaladinoScreen(vm:PaladinoViewModel=viewModel(),openChatRequest:Int=0){
 val petApp=androidx.compose.ui.platform.LocalContext.current.applicationContext as PaladinoApplication
 val petPrefs by petApp.tinyPets.state.collectAsStateWithLifecycle()
 val state by vm.ui.collectAsStateWithLifecycle()
 val messages by vm.messages.collectAsStateWithLifecycle()
 val notes by vm.notes.collectAsStateWithLifecycle()
 val modelStatus by vm.modelStatus.collectAsStateWithLifecycle()
 val modelReady by vm.modelReady.collectAsStateWithLifecycle()
 val progress by vm.downloadProgress.collectAsStateWithLifecycle()
 var tab by rememberSaveable{mutableIntStateOf(0)}
 LaunchedEffect(openChatRequest){if(openChatRequest>0)tab=0}
 val drawer=rememberDrawerState(DrawerValue.Closed)
 val uiScope=rememberCoroutineScope()
 val session by vm.session.collectAsStateWithLifecycle()
 var draft by rememberSaveable(session.id){mutableStateOf("")}
 var spriteChat by rememberSaveable{mutableStateOf(false)}
 var spriteDraft by rememberSaveable{mutableStateOf("")}
 val modalOpen=state.action!=null||state.cloud!=null||state.error!=null||state.notice!=null
 val keyboardOpen=WindowInsets.isImeVisible
 val scroll=rememberLazyListState()
 LaunchedEffect(messages.size,state.streaming){if(messages.isNotEmpty())scroll.animateScrollToItem(messages.size-1)}
 ModalNavigationDrawer(drawerState=drawer,drawerContent={ModalDrawerSheet(drawerContainerColor=Ink){
  TextButton(onClick={uiScope.launch{drawer.close()}}){Text(tr("← Back to chat"))}
  SessionsScreen(vm,onOpen={tab=0;uiScope.launch{drawer.close()}})
 }}){
 Scaffold(containerColor=Ink,floatingActionButton={
  if(tab!=0&&petPrefs.visible&&!keyboardOpen&&!modalOpen&&!spriteChat)SpriteQuickActions(petPrefs.animate,petPrefs.sizeDp,
   onChat={spriteDraft="";spriteChat=true},onRemember={spriteDraft=petApp.uiText("Remember that ");spriteChat=true},onOpen={tab=0})
 },bottomBar={
  NavigationBar(modifier=Modifier.heightIn(min=104.dp),containerColor=Ink,tonalElevation=0.dp){
   listOf("Chat" to Icons.Outlined.ChatBubbleOutline,"Controls" to Icons.Outlined.Hub,"Console" to Icons.Outlined.Terminal,"Settings" to Icons.Outlined.Tune).forEachIndexed{i,item->
    NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(item.second,tr(item.first))},label={Text(tr(item.first),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Ink,indicatorColor=Gold,selectedTextColor=Gold,unselectedTextColor=Muted,unselectedIconColor=Muted))
   }
  }
 }){padding->
  Column(Modifier.fillMaxSize().padding(padding).imePadding()){
   Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
    Box(Modifier.size(9.dp).background(Gold,CircleShape));Spacer(Modifier.width(9.dp))
    Text(tr("PETOLOGIC"),fontSize=13.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
    Spacer(Modifier.weight(1f));Text(tr("EARLY ACCESS"),fontSize=10.sp,letterSpacing=1.sp,color=Muted)
   }
   when(tab){
    0->{
     Row(Modifier.padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically){
      IconButton(onClick={uiScope.launch{drawer.open()}}){Icon(Icons.Outlined.Menu,tr("Conversations"))}
      if(messages.isNotEmpty()&&petPrefs.visible)PaladinoSprite(Modifier.size(48.dp).clickable{spriteDraft="";spriteChat=true},petPrefs.animate,"Open Sprite chat",state.petReaction())
      Column(Modifier.weight(1f)){Text(tr("Chat"),fontSize=24.sp,fontWeight=FontWeight.Bold);Text(sessionLabel(session.title),color=Muted,fontSize=12.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
      IconButton(onClick={vm.hub.create()},enabled=!state.busy&&state.action==null&&state.cloud==null){Icon(painterResource(R.drawable.ic_pet_new),tr("New conversation"),tint=Gold)}
     }
     Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
      Text(tr(if(state.mode==ExecutionMode.TINY)"On your phone" else "Cloud · review before sending"),color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
      Surface(color=Panel,shape=RoundedCornerShape(24.dp)){Row(Modifier.padding(4.dp)){
       ExecutionMode.entries.forEach{mode->
        val selected=mode==state.mode
        TextButton(onClick={vm.mode(mode)},enabled=!state.busy&&state.action==null&&state.cloud==null,colors=ButtonDefaults.textButtonColors(containerColor=if(selected)Gold else Color.Transparent,contentColor=if(selected)Ink else Muted),contentPadding=PaddingValues(horizontal=12.dp)) {Text(tr(if(mode==ExecutionMode.TINY)"Tiny" else "Maxx"),fontWeight=FontWeight.Bold)}
       }
      }}
     }
     if(messages.isEmpty()){
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally){
       Spacer(Modifier.height(20.dp))
       Box(Modifier.fillMaxWidth().height(112.dp).background(Brush.radialGradient(listOf(Raised,Ink))),contentAlignment=Alignment.Center){
        if(petPrefs.visible)PaladinoSprite(Modifier.size(104.dp).clickable{spriteDraft="";spriteChat=true},petPrefs.animate,"Open Sprite chat",state.petReaction())
       }
       Text(tr("How can I help?"),fontSize=26.sp,lineHeight=32.sp,fontWeight=FontWeight.SemiBold)
       Spacer(Modifier.height(10.dp))
       Text(tr("Ask a question, make a plan, or save a thought."),fontSize=14.sp,lineHeight=21.sp,color=Muted)
       Spacer(Modifier.height(20.dp))
       FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        SuggestionChip(onClick={draft=petApp.uiText("Remember that ")},label={Text(tr("Remember something"),fontSize=12.sp)},icon={Icon(Icons.Outlined.Add,"",Modifier.size(15.dp))})
        SuggestionChip(onClick={draft=petApp.uiText("Help me plan my day")},label={Text(tr("Make a plan"),fontSize=12.sp)})
       }
       if(!modelReady) TextButton(onClick={tab=3}){Text(tr("Set up offline chat →"),color=Gold)}
      }
     }else{
      LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
       items(messages,key={it.id}){message->
        val isUser=message.speaker=="user"
        Column(Modifier.fillMaxWidth(),horizontalAlignment=if(isUser)Alignment.End else Alignment.Start){
         Text(tr(if(isUser)"YOU" else "PALADINO · ${message.mode}"),color=Muted,fontSize=10.sp,letterSpacing=1.sp,modifier=Modifier.padding(bottom=6.dp))
         Surface(color=if(isUser)Raised else Panel,shape=RoundedCornerShape(18.dp)){
          if(isUser)androidx.compose.foundation.text.selection.SelectionContainer{Text(message.text,Modifier.padding(16.dp),fontSize=16.sp,lineHeight=24.sp)}else MarkdownReply(message.text,Modifier.padding(16.dp))
         }
        }
       }
       if(state.busy)item{Column{Text(tr(if(state.streaming.isBlank())state.status else state.streaming),color=Cream,modifier=Modifier.padding(12.dp));LinearProgressIndicator(Modifier.fillMaxWidth(),color=Gold,trackColor=Panel)}}
      }
     }
     Column(Modifier.padding(horizontal=20.dp,vertical=8.dp)){
      if(state.mode==ExecutionMode.MAXX)Text(tr("Cloud mode · you review every request before sending"),fontSize=10.sp,color=Muted,modifier=Modifier.padding(bottom=8.dp))
      Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
       OutlinedTextField(value=draft,onValueChange={draft=it},placeholder={Text(tr("What’s on your mind?"),fontSize=14.sp)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(24.dp),maxLines=4,enabled=!state.busy,colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color(PetPalette.outline)))
       FilledIconButton(onClick={if(state.busy)vm.cancel() else if(draft.isNotBlank()){vm.send(draft);draft=""}},modifier=Modifier.size(50.dp),enabled=state.busy||draft.isNotBlank()){
        Icon(if(state.busy)Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,if(state.busy)tr("Stop response") else tr("Send message"))
       }
      }
      Text(tr(if(state.mode==ExecutionMode.TINY)"ON YOUR DEVICE  ·  PRIVATE BY DEFAULT" else "SAME PALADINO  ·  MORE CAPABILITY"),fontSize=9.sp,letterSpacing=1.sp,color=Muted,modifier=Modifier.padding(top=10.dp).align(Alignment.CenterHorizontally))
     }
    }
    1->OrchestrationScreen(vm)
    2->ConsoleScreen(vm)
    3->Settings(state,modelStatus,modelReady,progress,vm)
   }
  }
 }
 }
 if(spriteChat&&!modalOpen)SpriteChatBubble(vm,spriteDraft,onDismiss={spriteChat=false},onOpen={spriteChat=false;tab=0})
 state.action?.let{action->AlertDialog(onDismissRequest=vm::denyAction,title={Text(tr(if(action.tool=="notes.create")"Keep this in memory?" else "Delete this note?"))},text={Column{
  Text(if(action.tool=="notes.create")action.argument else notes.find{it.id==action.argument}?.text?:"Selected note")
  Spacer(Modifier.height(12.dp));Text(tr("This action happens only on your phone."),color=Muted,fontSize=12.sp)
 }},confirmButton={TextButton(onClick=vm::approveAction){Text(tr(if(action.tool=="notes.create")"Save note" else "Delete note"))}},dismissButton={TextButton(onClick=vm::denyAction){Text(tr("Cancel"))}})}
 state.cloud?.let{pending->AlertDialog(onDismissRequest=vm::denyCloud,title={Text(tr("Let Paladino use Maxx?"))},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
  Text(tr("${pending.provider.label} · ${pending.model}"),fontWeight=FontWeight.Bold)
  Text(tr("This request and Paladino’s instructions will leave your phone. Your private notes and Tiny history are excluded. Up to 512 output tokens; your provider may charge for usage."),color=Muted,modifier=Modifier.padding(vertical=12.dp))
  Text(tr("REQUEST"),fontSize=10.sp,color=Gold);Text(pending.context.user,modifier=Modifier.padding(vertical=8.dp))
  Text(tr("PALADINO INSTRUCTIONS"),fontSize=10.sp,color=Gold);Text(pending.context.system,fontSize=12.sp,color=Muted)
 }},confirmButton={TextButton(onClick=vm::approveCloud){Text(tr("Send to Maxx"))}},dismissButton={TextButton(onClick=vm::denyCloud){Text(tr("Keep local"))}})}
 if(state.error!=null||state.notice!=null)AlertDialog(onDismissRequest=vm::clearError,title={Text(tr(if(state.error!=null)"A quick heads-up" else "All set"))},text={Text(tr(state.error?:state.notice?:""))},confirmButton={TextButton(onClick=vm::clearError){Text(tr("Got it"))}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Settings(state:PaladinoUiState,status:String,ready:Boolean,progress:Float?,vm:PaladinoViewModel){
 val semanticReady by vm.semanticReady.collectAsStateWithLifecycle()
 val semanticInstalling by vm.semanticInstalling.collectAsStateWithLifecycle()
 val uriHandler=androidx.compose.ui.platform.LocalUriHandler.current
 var key by remember(state.provider){mutableStateOf("")}
 var model by rememberSaveable(state.provider,state.model){mutableStateOf(state.model)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
  Text(tr("Settings"),fontSize=30.sp,fontWeight=FontWeight.Bold)
  Text(tr("Appearance, models and connections."),color=Muted)
  LanguageSettings()
  TinyPetSettings(vm)
  ModelManager(vm)
  Surface(shape=RoundedCornerShape(20.dp),color=Panel){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Row{Icon(Icons.Outlined.Memory,null,tint=Gold);Spacer(Modifier.width(12.dp));Text(tr("Tiny · your local brain"),fontWeight=FontWeight.Bold)}
   Text(tr(status),color=Muted)
   Text(tr("LFM2.5-350M lives on your phone. Download once, then chat offline. Files are verified before installation."),fontSize=13.sp,color=Muted)
   if(progress!=null)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
   Button(onClick=vm::install,enabled=!ready&&progress==null,modifier=Modifier.fillMaxWidth()){Text(tr(if(ready)"Ready for offline use" else if(progress!=null)"Downloading…" else "Download · 229 MB"))}
  }}
  Surface(shape=RoundedCornerShape(20.dp),color=Panel){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text(tr("Memory that understands meaning"),fontWeight=FontWeight.Bold)
   Text(tr("A small, separate encoder helps find notes by paraphrase. English preview · latest 200 notes. Keyword search works without it."),fontSize=13.sp,color=Muted)
   Button(onClick=vm::installSemantic,enabled=!semanticReady&&!semanticInstalling){Text(tr(if(semanticReady)"Semantic memory ready" else if(semanticInstalling)"Downloading…" else "Set up semantic memory · 23 MB"))}
  }}
  Surface(shape=RoundedCornerShape(20.dp),color=Panel){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Row{Icon(Icons.Outlined.CloudQueue,null,tint=Gold);Spacer(Modifier.width(12.dp));Text(tr("Maxx · a little extra reach"),fontWeight=FontWeight.Bold)}
   Text(tr("Connect your own API key for harder questions. Paladino asks before sending context. Cloud usage is billed by your provider."),fontSize=13.sp,color=Muted)
   FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){CloudProvider.entries.forEach{provider->FilterChip(selected=state.provider==provider,onClick={vm.selectProvider(provider)},enabled=!state.busy&&state.cloud==null&&state.action==null,label={Text(tr(provider.label))})}}
   Text(tr("API key authentication. Browser sign-in is not connected yet."),fontSize=12.sp,color=Muted)
   TextButton(onClick={uriHandler.openUri(state.provider.keysUrl)}){Text(tr("Manage %1\$s API keys",state.provider.label))}
   if(state.provider==CloudProvider.ZAI)Text(tr("Uses the general Z.ai API, not the Coding Plan endpoint."),fontSize=12.sp,color=Muted)
   if(state.connected){Text(tr("Key stored securely on this device"),color=Gold,fontSize=12.sp);Text(state.model,fontSize=13.sp);OutlinedButton(onClick=vm::disconnect){Text(tr("Disconnect"))}}
   else{
    OutlinedTextField(key,{key=it},label={Text(tr("%1\$s API key",state.provider.label))},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
    OutlinedTextField(model,{model=it},label={Text(tr("Model ID"))},placeholder={Text(tr(if(state.provider==CloudProvider.OPENROUTER)"provider/model" else "Exact model ID from your provider"))},singleLine=true,modifier=Modifier.fillMaxWidth())
    Button(onClick={vm.connect(key,model.trim());key=""},enabled=key.isNotBlank()&&model.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(tr("Save connection"))}
   }
  }}
  AdvancedSettings(vm)
  Row(Modifier.padding(vertical=8.dp)){Icon(Icons.Outlined.Shield,null,tint=Muted);Spacer(Modifier.width(12.dp));Text(tr("No account needed for Tiny. No hidden cloud fallback. Private memory stays here."),fontSize=12.sp,color=Muted)}
  Text(tr("0xPaladino 0.1 · Native Android preview\nGoogle companion accelerators are not enabled yet."),fontSize=11.sp,color=Muted,modifier=Modifier.padding(bottom=24.dp))
 }
}
