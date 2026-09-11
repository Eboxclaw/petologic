package ai.petologic.paladino

import ai.petologic.core.ModelCatalog
import ai.petologic.paladino.data.UserCopy
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
 val browser=LocalUriHandler.current
 Surface(color=Panel,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Gold.copy(alpha=.6f))){
  Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
    Icon(Icons.Outlined.SystemUpdate,null,tint=Gold)
    Text(tr("App updates"),style=MaterialTheme.typography.titleMedium)
   }
   Text("PETOLOGIC · ${BuildConfig.VERSION_NAME}",fontFamily=FontFamily.Monospace,color=Gold)
   Text(tr("Updates are manual. Open the official downloads page and install the newer APK over this app. Keep your conversations and models: do not uninstall first."),style=MaterialTheme.typography.bodyMedium,color=Muted)
   Button(onClick={browser.openUri(PublicUpdatesUrl)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
    Text(tr("Update app"))
   }
   Text(tr("Opens GitHub in your browser. Android asks you to confirm installation."),style=MaterialTheme.typography.bodySmall,color=Muted)
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
