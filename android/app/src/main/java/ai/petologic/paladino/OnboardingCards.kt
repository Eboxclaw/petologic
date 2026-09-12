package ai.petologic.paladino

import ai.petologic.core.ModelCatalog
import ai.petologic.paladino.data.UserCopy
import ai.petologic.paladino.runtime.AppUpdateState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

internal const val PublicUpdatesUrl="https://github.com/Eboxclaw/petologic-downloads/releases"

/** Pixel emblem shared with the website; decorative, with the brand name beside it. */
@Composable internal fun BrandGem(modifier:Modifier=Modifier){
 Canvas(modifier){
  val unit=size.minDimension/8f
  drawRect(Color(0xFF4163D8),Offset(unit,unit),Size(unit*6,unit*6))
  drawRect(Color(0xFF67E8E4),Offset(unit*3,unit*3),Size(unit*2,unit*2))
  drawRect(Gold,Offset(unit,0f),Size(unit*6,unit))
  drawRect(Gold,Offset(unit,unit*7),Size(unit*6,unit))
  drawRect(Gold,Offset(0f,unit),Size(unit,unit*6))
  drawRect(Gold,Offset(unit*7,unit),Size(unit,unit*6))
 }
}

@Composable internal fun AppUpdateCard(){
 val context=androidx.compose.ui.platform.LocalContext.current
 val app=context.applicationContext as PaladinoApplication
 val updater=app.appUpdater
 val state by updater.state.collectAsStateWithLifecycle()
 val status by updater.status.collectAsStateWithLifecycle()
 val progress by updater.progress.collectAsStateWithLifecycle()
 val browser=LocalUriHandler.current
 val scope=rememberCoroutineScope()
 // One check when the card opens; the button forces a re-check. No background polling.
 androidx.compose.runtime.LaunchedEffect(Unit){updater.check()}
 Surface(color=Panel,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Gold.copy(alpha=.6f))){
  Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
    Icon(Icons.Outlined.SystemUpdate,null,tint=Gold)
    Text(tr("App updates"),style=MaterialTheme.typography.titleMedium)
   }
   Text("PETOLOGIC · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",fontFamily=FontFamily.Monospace,color=Gold)
   when(val s=state){
    is AppUpdateState.Checking->{
     Text(tr("Checking the official updates page…"),color=Muted)
     LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    is AppUpdateState.UpToDate->{
     Text(tr("You're on the latest release."),color=Muted)
     TextButton(onClick={browser.openUri(PublicUpdatesUrl)}){Text(tr("Open release history in browser"))}
    }
    is AppUpdateState.Available->{
     Text(tr("%1\$s is available",s.release.tag),fontFamily=FontFamily.Monospace,color=Gold,fontWeight=FontWeight.Bold)
     if(s.release.notes.isNotBlank())Text(s.release.notes,style=MaterialTheme.typography.bodyMedium,color=Muted)
     Text("${s.release.apkName} · ${s.release.apkSize/1_000_000} MB",fontFamily=FontFamily.Monospace,color=Muted)
     if(progress!=null){LinearProgressIndicator(progress={progress?:0f},modifier=Modifier.fillMaxWidth());Text(tr(status.ifEmpty{"Downloading…"}),color=Muted)}
     Button(onClick={scope.launch{updater.download(s.release)}},enabled=progress==null,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
      Text(tr(if(progress!=null)"Downloading…" else "Download update"))
     }
    }
    is AppUpdateState.Ready->{
     Text(tr("Update verified and ready to install."),color=Gold)
     Text(tr("Android will confirm the installation. Your conversations, notes and models stay — never uninstall first."),style=MaterialTheme.typography.bodyMedium,color=Muted)
     Button(onClick={
      if(updater.canInstall)context.startActivity(updater.installIntent(s.file))
      else context.startActivity(updater.permissionIntent())
     },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
      Text(tr(if(updater.canInstall)"Install update" else "Allow Paladino to install apps"))
     }
     if(!updater.canInstall)Text(tr("One-time system permission. After allowing it, tap install again."),style=MaterialTheme.typography.bodySmall,color=Muted)
    }
    is AppUpdateState.Failed->{
     Text(tr(s.message),color=MaterialTheme.colorScheme.error)
     TextButton(onClick={scope.launch{updater.check(true)}}){Text(tr("Try again"))}
    }
    else->Unit
   }
   Text(tr("Updates install over this app with the same signature; a check needs the internet, the download resumes on its own."),style=MaterialTheme.typography.bodySmall,color=Muted)
  }
 }
}

/** User copy: export/import app data as one file, and a full reset without uninstalling. */
@Composable internal fun UserCopyCard(){
 val context=androidx.compose.ui.platform.LocalContext.current
 val app=context.applicationContext as PaladinoApplication
 val scope=rememberCoroutineScope()
 var busy by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf<String?>(null)}
 var pendingLoad by remember{mutableStateOf<android.net.Uri?>(null)}
 var confirmWipe by remember{mutableStateOf(false)}
 val saveLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
  if(uri==null)return@rememberLauncherForActivityResult
  busy=true;status=null
  scope.launch{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
   try{UserCopy.export(context,app.memory.db,"paladino.db",uri,BuildConfig.VERSION_NAME);context.uiText("User copy saved.")}
   catch(_:Exception){context.uiText("Could not save the user copy.")}
  }.let{status=it;busy=false}}
 }
 val loadLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri==null)return@rememberLauncherForActivityResult
  busy=true;status=null
  scope.launch{
   val runningSchema=app.memory.db.openHelper.writableDatabase.version
   val copy=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){UserCopy.readManifest(context,uri)}
   status=when{
    copy==null->context.uiText("This file is not a Paladino user copy.")
    !UserCopy.loadable(copy,runningSchema)->context.uiText("This user copy comes from a newer app version. Update the app first.")
    else->{pendingLoad=uri;null}
   }
   busy=false
  }
 }
 Surface(color=Panel,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Gold.copy(alpha=.6f))){
  Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
    Icon(Icons.Outlined.SettingsBackupRestore,null,tint=Gold)
    Text(tr("User copy"),style=MaterialTheme.typography.titleMedium)
   }
   Text(tr("Save your conversations, notes and settings as one file in the place you choose. Downloaded models and API keys are not included."),style=MaterialTheme.typography.bodyMedium,color=Muted)
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
    Button(onClick={saveLauncher.launch("paladino-user-copy-${java.time.LocalDate.now()}.zip")},enabled=!busy,shape=RoundedCornerShape(12.dp)){Text(tr("Save user copy"))}
    OutlinedButton(onClick={loadLauncher.launch(arrayOf("application/zip","application/octet-stream"))},enabled=!busy,shape=RoundedCornerShape(12.dp)){Text(tr("Load user copy"))}
   }
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Icon(Icons.Outlined.DeleteForever,null,tint=Muted,modifier=Modifier.size(18.dp))
    TextButton(onClick={confirmWipe=true},enabled=!busy,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text(tr("Delete everything and start over"))}
   }
   if(busy)Text(tr("Working…"),style=MaterialTheme.typography.bodySmall,color=Muted)
   status?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=Gold)}
  }
 }
 pendingLoad?.let{uri->AlertDialog(
  onDismissRequest={if(!busy)pendingLoad=null},
  title={Text(tr("Replace app content with this copy?"))},
  text={Text(tr("Loading a user copy replaces everything now in the app, then Paladino restarts."))},
  confirmButton={TextButton(enabled=!busy,onClick={
   busy=true
   scope.launch{
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){UserCopy.import(context,app.memory.db,"paladino.db",uri)}
    UserCopy.restart(context)
   }
  }){Text(tr("Load and restart"))}},
  dismissButton={TextButton(enabled=!busy,onClick={pendingLoad=null}){Text(tr("Cancel"))}}
 )}
 if(confirmWipe)AlertDialog(
  onDismissRequest={if(!busy)confirmWipe=false},
  title={Text(tr("Start from zero?"))},
  text={Text(tr("Everything on this phone is erased: conversations, notes, settings, models and keys. Nothing leaves the device. This cannot be undone."))},
  confirmButton={TextButton(enabled=!busy,onClick={
   confirmWipe=false;busy=true
   scope.launch{
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){UserCopy.wipe(context,app.memory.db,"paladino.db")}
    UserCopy.restart(context)
   }
  }){Text(tr("Delete everything"))}},
  dismissButton={TextButton(enabled=!busy,onClick={confirmWipe=false}){Text(tr("Cancel"))}}
 )
}

/** The original message waits here until the user explicitly resumes after verified installation. */
@Composable internal fun ModelSetupCard(vm:PaladinoViewModel,onManage:()->Unit){
 val state by vm.ui.collectAsStateWithLifecycle()
 val session by vm.session.collectAsStateWithLifecycle()
 val installed by vm.library.installed.collectAsStateWithLifecycle()
 val progress by vm.library.progress.collectAsStateWithLifecycle()
 val status by vm.library.status.collectAsStateWithLifecycle()
 val model=ModelCatalog.get(session.modelId)
 val ready=model.id in installed
 var attempted by androidx.compose.runtime.saveable.rememberSaveable(session.id){mutableStateOf(false)}
 Surface(color=Panel,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Gold)){
  Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
    BrandGem(Modifier.size(32.dp))
    Text(tr(if(ready)"Paladino is ready" else "Give Paladino a local brain"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   }
   Text(tr("Your message is waiting. Install the model once, then chat on your phone without an account."),color=Muted)
   Text("${model.model} · ${model.quantization} · ${model.size/1_000_000} MB",color=Gold,fontFamily=FontFamily.Monospace)
   state.pendingModelMessage?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
   if(progress!=null){LinearProgressIndicator(progress={progress?:0f},modifier=Modifier.fillMaxWidth());Text(tr(status),color=Muted)}
   Button(onClick={if(ready)vm.resumeModelMessage()else {attempted=true;vm.downloadModel(model.id)}},enabled=progress==null,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
    Text(tr(if(ready)"Continue conversation" else if(progress!=null)"Downloading…" else "Install model"))
   }
   TextButton(onClick=onManage){Text(tr("Already have it? Import a model"))}
   TextButton(onClick=vm::discardModelMessage){Text(tr("Discard this message"))}
   if(attempted&&!ready&&progress==null)Text(tr(status),style=MaterialTheme.typography.bodySmall,color=Muted)
  }
 }
}
