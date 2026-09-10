package ai.petologic.paladino

import android.animation.ValueAnimator
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.ImageView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun PaladinoSprite(modifier:Modifier=Modifier,animated:Boolean=true,label:String="Paladino, your TinyPet",reaction:PetReaction=PetReaction.IDLE){
 val context=LocalContext.current
 val owner=LocalLifecycleOwner.current
 val scope=rememberCoroutineScope()
 val animator=remember{SpriteAnimator(context.resources,scope)}
 var view by remember{mutableStateOf<ImageView?>(null)}
 var resumed by remember{mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
 DisposableEffect(owner){
  val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_RESUME->resumed=true;Lifecycle.Event.ON_PAUSE->resumed=false;else->Unit}}
  owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer);animator.release()}
 }
 DisposableEffect(view,animated,reaction,resumed){
  animator.gate={animated&&resumed&&ValueAnimator.areAnimatorsEnabled()}
  view?.let{animator.attach(it)}
  if(reaction==PetReaction.RUNNING)animator.playLooping(reaction.animationResource())else animator.playIdle(idlePair.first,idlePair.second)
  onDispose{}
 }
 AndroidView(
  factory={ImageView(it).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setImageResource(R.drawable.paladino_static)}},
  modifier=modifier,
  update={it.contentDescription=context.uiText(label);view=it}
 )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TinyPetSettings(vm:PaladinoViewModel){
 val context=LocalContext.current
 val app=context.applicationContext as PaladinoApplication
 val overlayRunning by SpriteOverlayService.running.collectAsStateWithLifecycle()
 var overlayDenied by remember{mutableStateOf(false)}
 val notificationPermission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){_->context.startForegroundService(android.content.Intent(context,SpriteOverlayService::class.java))}
 fun startOverlay(){if(android.os.Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)else context.startForegroundService(android.content.Intent(context,SpriteOverlayService::class.java))}
 val overlayPermission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()){if(android.provider.Settings.canDrawOverlays(context)){overlayDenied=false;startOverlay()}else overlayDenied=true}
 val manager=app.tinyPets
 val prefs by manager.state.collectAsStateWithLifecycle()
 val sessions by vm.hub.sessions.collectAsStateWithLifecycle()
 val widgets=AppWidgetManager.getInstance(context)
 var message by remember{mutableStateOf<String?>(null)}
 var expanded by remember{mutableStateOf(false)}
 Card(Modifier.fillMaxWidth()){
  Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text(tr("Sprite & Widget"),style=MaterialTheme.typography.titleLarge)
   Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
    PaladinoSprite(Modifier.size(88.dp),prefs.animate)
    Column{Text(tr("0xPaladino"),style=MaterialTheme.typography.titleMedium);Text(tr("TinyPet 01 · Personal companion"),color=Muted);Text(tr("One role. Tiny or Maxx."),color=Muted)}
   }
   Text(tr("Paladino uses the current conversation’s tools, instructions and permissions. Changing its appearance never changes its access."),style=MaterialTheme.typography.bodySmall)
   TextButton(onClick={expanded=!expanded}){Text(tr(if(expanded)"Close Sprite controls" else "Manage Sprite & Widget"))}
   if(expanded){
    PetSwitch("Show in-app Sprite",prefs.visible){manager.update(prefs.copy(visible=it))}
    PetSwitch("Animate idle Sprite",prefs.animate){manager.update(prefs.copy(animate=it))}
    Text(tr("System reduced motion is respected. Animation pauses when this screen leaves the foreground."),style=MaterialTheme.typography.bodySmall,color=Muted)
    Text(tr("Sprite size"))
    FlowRow{listOf(48 to "Small",64 to "Default",88 to "Large").forEach{(size,name)->FilterChip(prefs.sizeDp==size,{manager.update(prefs.copy(sizeDp=size))},label={Text(tr(name))},modifier=Modifier.padding(end=4.dp))}}
    HorizontalDivider()
    Text(tr("Floating above other apps"),style=MaterialTheme.typography.titleMedium)
    OverlayHelpCard(overlayDenied)
    Text(tr("Tap Paladino to chat. Drag to move; the Sprite remembers its position. Allow display over other apps. Notifications provide a Stop control; you can also stop the Sprite here."),style=MaterialTheme.typography.bodySmall)
    Button(onClick={if(overlayRunning)context.stopService(android.content.Intent(context,SpriteOverlayService::class.java))else if(android.provider.Settings.canDrawOverlays(context))startOverlay()else overlayPermission.launch(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+context.packageName)))}){Text(tr(if(overlayRunning)"Stop floating Sprite" else "Enable floating Sprite"))}
    HorizontalDivider()
    Text(tr("Home-screen widget"),style=MaterialTheme.typography.titleMedium)
    Text(tr("A static Paladino opens Chat. Android launcher widgets do not play this GIF. No background model runs just because a widget is present."),style=MaterialTheme.typography.bodySmall)
    PetSwitch("Show widget caption",prefs.widgetCaption){manager.update(prefs.copy(widgetCaption=it))}
    Text(tr("Widget conversation"))
    FilterChip(prefs.widgetSession.isBlank(),{manager.update(prefs.copy(widgetSession=""))},label={Text(tr("Last active conversation"))})
    sessions.filter{!it.archived}.take(12).forEach{session->
     FilterChip(prefs.widgetSession==session.id,{manager.update(prefs.copy(widgetSession=session.id))},label={Text(sessionLabel(session.title))})
    }
    Text(tr("These settings apply to all Paladino home-screen widgets. An archived or missing target falls back to the active conversation."),style=MaterialTheme.typography.bodySmall,color=Muted)
    Button(onClick={
     message=if(widgets.isRequestPinAppWidgetSupported){
      if(widgets.requestPinAppWidget(ComponentName(context,PaladinoWidget::class.java),null,null))"Confirm placement in your launcher. The launcher decides where to place it." else "Open your launcher’s Widgets menu and choose 0xPaladino."
     }else "This launcher does not support pin requests. Long-press the home screen, open Widgets and choose 0xPaladino."
    }){Text(tr("Add to home screen"))}
    message?.let{Text(tr(it),style=MaterialTheme.typography.bodySmall)}


   }
  }
 }
}
@Composable private fun PetSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(tr(label),Modifier.weight(1f).padding(top=12.dp));Switch(checked,onChange)}
}

private data class SpriteAction(val id:String,val label:String,val run:()->Unit)
@Composable fun SpriteQuickActions(animated:Boolean,size:Int,onChat:()->Unit,onRemember:()->Unit,onOpen:()->Unit){
 val quickActionsLabel=tr("Quick actions")
 var expanded by remember{mutableStateOf(false)}
 Column(horizontalAlignment=androidx.compose.ui.Alignment.End){
  androidx.compose.material3.IconButton(onClick=onChat,modifier=Modifier.size(size.dp)){
   PaladinoSprite(Modifier.fillMaxSize(),animated,"Open Sprite chat")
  }
  Box{
   FloatingActionButton(onClick={expanded=!expanded},containerColor=Gold,contentColor=Ink,shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),modifier=Modifier.size(56.dp).semantics{contentDescription=quickActionsLabel}){Icon(painterResource(if(expanded)R.drawable.ic_pet_collapse else R.drawable.ic_pet_chat),null,Modifier.size(26.dp))}
   DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},containerColor=Panel,shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)){
    listOf(SpriteAction("ask","Message Paladino",onChat),SpriteAction("remember","Save a note",onRemember),SpriteAction("open","Full conversation",onOpen)).forEach{action->
     DropdownMenuItem(leadingIcon={Icon(painterResource(when(action.id){"ask"->R.drawable.ic_pet_chat;"remember"->R.drawable.ic_pet_new;else->R.drawable.ic_pet_open}),null)},text={Text(tr(action.label))},onClick={expanded=false;action.run()})
    }
   }
  }
 }
}

/** A compact projection of the active session, never a second transcript or agent. */
@Composable fun SpriteChatBubble(vm:PaladinoViewModel,initialDraft:String="",onDismiss:()->Unit,onOpen:()->Unit){
 val state by vm.ui.collectAsStateWithLifecycle()
 val session by vm.session.collectAsStateWithLifecycle()
 val messages by vm.messages.collectAsStateWithLifecycle()
 var draft by androidx.compose.runtime.saveable.rememberSaveable(session.id){mutableStateOf(initialDraft)}
 LaunchedEffect(state.pendingModelMessage){if(state.pendingModelMessage!=null)onOpen()}
 androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss){
  Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=Panel){
   Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Row{Text(tr("Paladino"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));TextButton(onClick=onDismiss){Text(tr("Close bubble"))}}
    Text(sessionLabel(session.title)+" · "+(if(state.mode==ai.petologic.core.ExecutionMode.TINY)"Tiny" else "Maxx")+" · "+tr(state.petReaction().label),style=MaterialTheme.typography.labelSmall,color=Gold)
    val reply=if(state.busy)state.streaming.ifBlank{tr(state.status)}else messages.lastOrNull{it.sessionId==session.id&&it.speaker=="assistant"}?.text?:tr("I’m here. What would you like to do?")
    MarkdownReply(reply,maxLines=6)
    Column(Modifier.fillMaxWidth()){OutlinedButton(onClick=onOpen,modifier=Modifier.fillMaxWidth()){Text(tr(if(state.action!=null||state.cloud!=null)"Open to approve" else "Full conversation"))};TextButton(onClick={vm.hub.create()},modifier=Modifier.fillMaxWidth(),enabled=!state.busy&&state.action==null&&state.cloud==null){Text(tr("New conversation"))}}
    OutlinedTextField(draft,{draft=it},label={Text(tr("Message Paladino"))},modifier=Modifier.fillMaxWidth(),maxLines=3,enabled=!state.busy)
    Button(onClick={if(state.busy)vm.cancel()else{vm.send(draft);draft=""}},enabled=state.busy||draft.isNotBlank(),modifier=Modifier.fillMaxWidth()){
     Text(tr(if(state.busy)"Stop response" else "Send to Paladino"))
    }
    Text(tr("Messages are saved in this app conversation."),style=MaterialTheme.typography.labelSmall,color=Muted)
   }
  }
 }
}
