package ai.petologic.skills.security.guard

import ai.petologic.core.ModelCatalog
import ai.petologic.paladino.Gold
import ai.petologic.paladino.Muted
import ai.petologic.paladino.PaladinoApplication
import ai.petologic.paladino.Raised
import ai.petologic.paladino.tr
import ai.petologic.security.Finding
import ai.petologic.security.ThreatMemoryStatus
import ai.petologic.skills.security.android.NotificationListener
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

class SecurityActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val text=if(intent.action==Intent.ACTION_SEND)intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(16384) else ""
  val uri=if(intent.action==Intent.ACTION_SEND)intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.takeIf{it.scheme=="content"} else null
  val isImage=intent.type?.startsWith("image/")==true
  val destination=if(intent.getBooleanExtra("open_findings",false)||intent.getStringExtra("finding_id")!=null)SecurityDestination.ACTIVITY else if(intent.getBooleanExtra("open_check",false)||text.isNotBlank()||uri!=null)SecurityDestination.CHECK else SecurityDestination.PROTECTION
  setContent{MaterialTheme(colorScheme=ai.petologic.paladino.PetColors){Surface{
   SecurityScreen(text,if(isImage)null else uri,if(isImage)uri else null,intent.getStringExtra("finding_id"),destination,onOpenSettings={startActivity(Intent(this,ai.petologic.paladino.MainActivity::class.java).putExtra("destination","settings"))})
  }}}
 }
}

private enum class CheckKind { TEXT, FILE, IMAGE, APP }

@Composable fun SecurityScreen(
 initialText:String="",
 initialUri:Uri?=null,
 initialImage:Uri?=null,
 initialFinding:String?=null,
 initialDestination:SecurityDestination=SecurityDestination.PROTECTION,
 onAskPaladino:(()->Unit)?=null,
 onOpenSettings:(()->Unit)?=null,
 vm:SecurityViewModel=viewModel()
){
 val context=androidx.compose.ui.platform.LocalContext.current
 val app=context.applicationContext as PaladinoApplication
 var permissionRefresh by remember{mutableIntStateOf(0)}
 androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME){permissionRefresh++}
 val findings by vm.findings.collectAsStateWithLifecycle()
 val busy by vm.busy.collectAsStateWithLifecycle()
 val status by vm.status.collectAsStateWithLifecycle()
 val explanation by vm.explanation.collectAsStateWithLifecycle()
 val visualAnalysis by vm.visualAnalysis.collectAsStateWithLifecycle()
 val threatStatus by vm.threatStatus.collectAsStateWithLifecycle()
 val installed by app.modelLibrary.installed.collectAsStateWithLifecycle()
 var destinationName by rememberSaveable(initialDestination){mutableStateOf(initialDestination.name)}
 val destination=SecurityDestination.valueOf(destinationName)
 var text by rememberSaveable{mutableStateOf(initialText)}
 var packageName by rememberSaveable{mutableStateOf("")}
 var selected by rememberSaveable{mutableStateOf(initialUri?.toString())}
 var selectedImage by rememberSaveable{mutableStateOf(initialImage?.toString())}
 var checkKindName by rememberSaveable{mutableStateOf(when{initialText.isNotBlank()->CheckKind.TEXT.name;initialImage!=null->CheckKind.IMAGE.name;initialUri!=null->CheckKind.FILE.name;else->CheckKind.TEXT.name})}
 var clearConfirm by rememberSaveable{mutableStateOf(false)}
 val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->selected=uri?.toString()}
 val pickImage=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->selectedImage=uri?.toString()}
 val pack=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(vm::installPack)}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let(vm::export)}
 val protection=vm.repository.protection()
 val summary=SecurityPresentation.summary(ProtectionInputs(
  validThreatPack=!protection.signatures.startsWith("No valid")&&!protection.signatures.contains("expired",ignoreCase=true),
  notificationAccess=NotificationListener.accessGranted(context).also{permissionRefresh},
  vectorMemoryReady=threatStatus is ThreatMemoryStatus.Ready,
  chatReady=ModelCatalog.SPEAKER_ID in installed,
  visionReady=ModelCatalog.VISION_PROJECTOR_ID in installed,
  automaticUpdatesConfigured=ai.petologic.paladino.BuildConfig.THREAT_MANIFEST_URL.isNotBlank(),
  latestFinding=findings.maxByOrNull{it.createdAt},
  installedAppProtection=InstalledAppProtection.enabled(context),
  reputationConfigured=ai.petologic.paladino.BuildConfig.REPUTATION_URL.isNotBlank()
 ))

 Column(Modifier.fillMaxSize().systemBarsPadding()){
  SecurityHeader(destination,onBack={destinationName=SecurityDestination.PROTECTION.name})
  if(status.isNotBlank())Surface(color=Raised,modifier=Modifier.fillMaxWidth()){Text(tr(status),Modifier.padding(horizontal=20.dp,vertical=12.dp),style=MaterialTheme.typography.bodySmall)}
  if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=vm::cancel,modifier=Modifier.align(Alignment.End)){Text(tr("Cancel"))}}
  when(destination){
   SecurityDestination.PROTECTION->ProtectionDashboard(summary,findings,onCheck={destinationName=SecurityDestination.CHECK.name},onActivity={destinationName=SecurityDestination.ACTIVITY.name},onSetup={destinationName=SecurityDestination.SETUP.name},onAsk=onAskPaladino)
   SecurityDestination.CHECK->CheckScreen(CheckKind.valueOf(checkKindName),onKind={checkKindName=it.name},text=text,onText={text=it.take(16384)},packageName=packageName,onPackage={packageName=it.take(180)},selected=selected,selectedImage=selectedImage,visualAnalysis=visualAnalysis,visionReady=ModelCatalog.SPEAKER_ID in installed&&ModelCatalog.VISION_PROJECTOR_ID in installed,busy=busy,onPickFile={pick.launch(arrayOf("*/*"))},onPickImage={pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},onCheckText={vm.text(text);text="";destinationName=SecurityDestination.ACTIVITY.name},onCheckFile={selected?.let{vm.file(Uri.parse(it));selected=null;destinationName=SecurityDestination.ACTIVITY.name}},onCheckImage={selectedImage?.let{vm.image(Uri.parse(it));selectedImage=null}},onCheckApp={vm.app(packageName.trim());packageName="";destinationName=SecurityDestination.ACTIVITY.name},onCheckAllApps=vm::checkInstalledApps,onSettings=onOpenSettings)
   SecurityDestination.ACTIVITY->ActivityScreen(findings,initialFinding,explanation,busy,onExport={export.launch("paladino-security-report.json")},onClear={clearConfirm=true},onExplain=vm::explain,onCheck={destinationName=SecurityDestination.CHECK.name})
   SecurityDestination.SETUP->ProtectionSetup(summary,protection.signatures,protection.rules,protection.vectorMemory,permissionRefresh,app,pack,vm,busy,onSettings=onOpenSettings)
  }
 }
 if(clearConfirm)AlertDialog(onDismissRequest={clearConfirm=false},title={Text(tr("Clear all findings?"))},text={Text(tr("This removes the local findings and their vectors. Exported reports are not affected."))},confirmButton={TextButton(onClick={clearConfirm=false;vm.clear()}){Text(tr("Clear findings"))}},dismissButton={TextButton(onClick={clearConfirm=false}){Text(tr("Cancel"))}})
}

@Composable private fun SecurityHeader(destination:SecurityDestination,onBack:()->Unit){
 Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
  if(destination!=SecurityDestination.PROTECTION)IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,tr("Back to protection"))}
  Column(Modifier.weight(1f)){Text(tr(when(destination){SecurityDestination.PROTECTION->"Protection";SecurityDestination.CHECK->"Check something";SecurityDestination.ACTIVITY->"Activity";SecurityDestination.SETUP->"Protection setup"}),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(tr("Private AI security guardian"),style=MaterialTheme.typography.labelMedium,color=Muted)}
  Icon(Icons.Outlined.Shield,null,tint=Gold)
 }
}

@Composable private fun ProtectionDashboard(summary:ProtectionSummaryUi,findings:List<Finding>,onCheck:()->Unit,onActivity:()->Unit,onSetup:()->Unit,onAsk:(()->Unit)?){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  FirstRunGuide(onCheck,onSetup)
  ProtectionStateCard(summary)
  Button(onClick=onCheck,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("check_something")){Icon(Icons.Outlined.Search,null);Spacer(Modifier.width(10.dp));Text(tr("Check something"),fontWeight=FontWeight.Bold)}
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
   OutlinedButton(onClick=onActivity,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text(tr("View activity"))}
   OutlinedButton(onClick=onSetup,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text(tr("Protection setup"))}
  }
  Text(tr("Protection on this phone"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
  summary.capabilities.take(4).forEach{CapabilityCard(it,onClick=if(it.action!=null)onSetup else null)}
  Text(tr("Recent findings"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
  if(findings.isEmpty())EmptyCard("Nothing checked yet","Check a message, link, file or APK.",onCheck,"Check something")
  else findings.sortedByDescending{it.createdAt}.take(3).forEach{FindingSummaryCard(it.toUi(),onClick=onActivity)}
  if(onAsk!=null)OutlinedCard(onClick=onAsk,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.ChatBubbleOutline,null,tint=Gold);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(tr("Ask Paladino"),fontWeight=FontWeight.Bold);Text(tr("Ask about a finding, suspicious message or Android permission."),style=MaterialTheme.typography.bodySmall,color=Muted)};Icon(Icons.Outlined.ChevronRight,null)}}
  Text(tr("No result guarantees that a file, app or device is safe."),style=MaterialTheme.typography.bodySmall,color=Muted,modifier=Modifier.padding(bottom=24.dp))
 }
}

@Composable private fun FirstRunGuide(onCheck:()->Unit,onSetup:()->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 val prefs=remember{context.getSharedPreferences("paladino-ux",android.content.Context.MODE_PRIVATE)}
 var visible by rememberSaveable{mutableStateOf(!prefs.getBoolean("security-intro-complete",false))}
 var page by rememberSaveable{mutableIntStateOf(0)}
 if(!visible)return
 val title=listOf("Meet your Paladino","Choose your first protection","Add private AI when you want")[page]
 val body=listOf(
  "Paladino checks selected messages, links, files, APKs and screenshots locally. It shows evidence and the limits of every result.",
  "Start with a manual check or open Protection setup. Installed-app monitoring and notification observation begin only after you enable them.",
  "Local chat explains findings. Vector memory retrieves related reviewed knowledge. Both are optional and neither can create a security verdict."
 )[page]
 Surface(color=MaterialTheme.colorScheme.primary.copy(alpha=.12f),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().testTag("security_onboarding")){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(tr("Step %1\$s of 3",page+1),style=MaterialTheme.typography.labelMedium,color=Gold)
  Text(tr(title),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
  Text(tr(body),style=MaterialTheme.typography.bodyMedium)
  if(page==1)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onCheck){Text(tr("Check something"))};OutlinedButton(onClick=onSetup){Text(tr("Protection setup"))}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
   if(page>0)TextButton(onClick={page--}){Text(tr("Back"))}
   Button(onClick={if(page<2)page++ else {prefs.edit().putBoolean("security-intro-complete",true).apply();visible=false}}){Text(tr(if(page<2)"Next" else "Start using Paladino"))}
  }
 }}
}

@Composable private fun ProtectionStateCard(summary:ProtectionSummaryUi){
 val title=when(summary.state){ProtectionStateUi.READY->"Ready";ProtectionStateUi.LIMITED->"Limited";ProtectionStateUi.NEEDS_ATTENTION->"Needs attention";ProtectionStateUi.OFF->"Off"}
 val color=when(summary.state){ProtectionStateUi.READY->MaterialTheme.colorScheme.secondary;ProtectionStateUi.NEEDS_ATTENTION->MaterialTheme.colorScheme.error;else->Gold}
 Surface(color=Raised,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().testTag("protection_state")){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Security,null,tint=color);Spacer(Modifier.width(10.dp));Text(tr(title),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=color)}
  Text(tr(summary.reason),style=MaterialTheme.typography.bodyMedium)
  Text(if(summary.lastCheckedAt==null)tr("No completed checks yet") else tr("Last check: %1\$s",DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(summary.lastCheckedAt))),style=MaterialTheme.typography.bodySmall,color=Muted)
 }}
}

@Composable private fun CapabilityCard(item:CapabilityStatusUi,onClick:(()->Unit)?){
 val state=when(item.state){CapabilityStateUi.READY->"Ready";CapabilityStateUi.NEEDS_SETUP->"Needs setup";CapabilityStateUi.LIMITED->"Limited";CapabilityStateUi.NOT_AVAILABLE_YET->"Not available yet"}
 val content:@Composable ColumnScope.()->Unit={Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
  val color=when(item.state){CapabilityStateUi.READY->MaterialTheme.colorScheme.secondary;CapabilityStateUi.NOT_AVAILABLE_YET->Muted;else->Gold}
  Icon(when(item.state){CapabilityStateUi.READY->Icons.Outlined.CheckCircle;CapabilityStateUi.NOT_AVAILABLE_YET->Icons.Outlined.Schedule;else->Icons.Outlined.Info},null,tint=color)
  Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(tr(item.title),fontWeight=FontWeight.SemiBold);Text(tr(state),style=MaterialTheme.typography.labelMedium,color=color);Text(tr(item.detail),style=MaterialTheme.typography.bodySmall,color=Muted)}
  if(onClick!=null)Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
 }}
 if(onClick==null)Card(modifier=Modifier.fillMaxWidth(),content=content) else OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth(),content=content)
}

@Composable private fun CheckScreen(kind:CheckKind,onKind:(CheckKind)->Unit,text:String,onText:(String)->Unit,packageName:String,onPackage:(String)->Unit,selected:String?,selectedImage:String?,visualAnalysis:String,visionReady:Boolean,busy:Boolean,onPickFile:()->Unit,onPickImage:()->Unit,onCheckText:()->Unit,onCheckFile:()->Unit,onCheckImage:()->Unit,onCheckApp:()->Unit,onCheckAllApps:()->Unit,onSettings:(()->Unit)?){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text(tr("What do you want to check?"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
  listOf(CheckKind.TEXT to "Message or link",CheckKind.FILE to "File or APK",CheckKind.IMAGE to "Screenshot or image",CheckKind.APP to "Installed app").forEach{(value,label)->FilterChip(selected=kind==value,onClick={onKind(value)},label={Text(tr(label))},leadingIcon=if(kind==value){{Icon(Icons.Outlined.Check,null)}}else null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp))}
  HorizontalDivider()
  when(kind){
   CheckKind.TEXT->{PrivacyNotice("Paladino reads this text for local pattern checks. The submitted text is not saved in the finding.");OutlinedTextField(value=text,onValueChange=onText,label={Text(tr("Message or URL"))},modifier=Modifier.fillMaxWidth(),minLines=4,maxLines=8);Button(onClick=onCheckText,enabled=!busy&&text.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(tr("Check text locally"))}}
   CheckKind.FILE->{PrivacyNotice("Paladino reads the selected file locally with strict size and time limits. It stores a fingerprint and redacted evidence, not the file.");OutlinedButton(onClick=onPickFile,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(tr("Choose APK or file"))};if(selected!=null){Text(tr("A file is selected. Its contents stay on this device."));Button(onClick=onCheckFile,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(tr("Check selected file"))}}}
   CheckKind.IMAGE->{PrivacyNotice("The selected image is resized in memory, inspected locally and not added to history. Visual observations are not a security verdict.");OutlinedButton(onClick=onPickImage,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(tr("Choose screenshot or image"))};if(selectedImage!=null)Button(onClick=onCheckImage,enabled=!busy&&visionReady,modifier=Modifier.fillMaxWidth()){Text(tr("Inspect image locally"))};if(!visionReady){Text(tr("Local chat and the optional vision pack are required."),color=Muted);if(onSettings!=null)TextButton(onClick=onSettings){Text(tr("Open model settings"))}};if(visualAnalysis.isNotBlank())AiExplanationCard(visualAnalysis)}
   CheckKind.APP->{PrivacyNotice("Paladino reads the app profile and installed APK locally without opening or running it. If private reputation is configured, only an 8-character SHA-256 prefix is sent.");OutlinedTextField(value=packageName,onValueChange=onPackage,label={Text(tr("Android package name"))},placeholder={Text("com.example.app")},singleLine=true,modifier=Modifier.fillMaxWidth());Button(onClick=onCheckApp,enabled=!busy&&packageName.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(tr("Check installed app"))};OutlinedButton(onClick=onCheckAllApps,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(tr("Check all visible installed apps"))}}
  }
  Text(tr("Checks run locally. You can cancel while Paladino is working."),style=MaterialTheme.typography.bodySmall,color=Muted,modifier=Modifier.padding(bottom=24.dp))
 }
}

@Composable private fun PrivacyNotice(text:String){Surface(color=MaterialTheme.colorScheme.secondary.copy(alpha=.08f),shape=RoundedCornerShape(14.dp)){Row(Modifier.padding(14.dp)){Icon(Icons.Outlined.Lock,null,tint=MaterialTheme.colorScheme.secondary);Spacer(Modifier.width(10.dp));Text(tr(text),style=MaterialTheme.typography.bodySmall)}}}

@Composable private fun ActivityScreen(findings:List<Finding>,initialFinding:String?,explanation:String,busy:Boolean,onExport:()->Unit,onClear:()->Unit,onExplain:(Finding)->Unit,onCheck:()->Unit){
 var menu by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(tr("Findings stay on this phone until you clear them."),style=MaterialTheme.typography.bodySmall,color=Muted,modifier=Modifier.weight(1f));Box{IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreVert,tr("Activity actions"))};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){DropdownMenuItem(text={Text(tr("Export redacted report"))},onClick={menu=false;onExport()},leadingIcon={Icon(Icons.Outlined.FileDownload,null)});DropdownMenuItem(text={Text(tr("Clear findings"))},onClick={menu=false;onClear()},leadingIcon={Icon(Icons.Outlined.DeleteOutline,null)})}}}
  if(explanation.isNotBlank())AiExplanationCard(explanation)
  if(findings.isEmpty())EmptyCard("Nothing checked yet","Check a message, link, file or APK.",onCheck,"Check something")
  findings.sortedWith(compareByDescending<Finding>{it.id==initialFinding}.thenByDescending{it.createdAt}).forEach{FindingDetailCard(it.toUi(),busy,onExplain)}
  Spacer(Modifier.height(24.dp))
 }
}

@Composable private fun FindingSummaryCard(item:FindingUi,onClick:()->Unit){OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){FindingIcon(item.level);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(tr(item.title),fontWeight=FontWeight.Bold);Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(item.finding.createdAt)),style=MaterialTheme.typography.bodySmall,color=Muted)};Icon(Icons.Outlined.ChevronRight,null,tint=Muted)}}}

@Composable private fun FindingDetailCard(item:FindingUi,busy:Boolean,onExplain:(Finding)->Unit){
 var evidenceOpen by rememberSaveable(item.finding.id){mutableStateOf(false)}
 Card(Modifier.fillMaxWidth().testTag("finding_${item.level.name}")){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){FindingIcon(item.level);Spacer(Modifier.width(10.dp));Column{Text(tr(item.title),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(item.finding.createdAt)),style=MaterialTheme.typography.bodySmall,color=Muted)}}
  FindingSection("What was checked",listOf(item.whatWasChecked))
  FindingSection("What was found",item.whatWasFound)
  if(item.skippedOrUnavailable.isNotEmpty())FindingSection("Limits and unavailable checks",item.skippedOrUnavailable)
  FindingSection("Recommended next step",listOf(item.nextStep))
  if(item.finding.evidence.isNotEmpty()){TextButton(onClick={evidenceOpen=!evidenceOpen}){Text(tr(if(evidenceOpen)"Hide technical evidence" else "Show technical evidence"))};if(evidenceOpen)item.finding.evidence.distinctBy{it.detector to it.source}.forEach{Text("${it.detector} ${it.version} · ${it.source}",style=MaterialTheme.typography.bodySmall,color=Muted)}}
  OutlinedButton(onClick={onExplain(item.finding)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.ChatBubbleOutline,null);Spacer(Modifier.width(8.dp));Text(tr("Ask Paladino to explain"))}
 }}
}

@Composable private fun FindingIcon(level:FindingLevelUi){val icon=when(level){FindingLevelUi.NO_KNOWN_ISSUE->Icons.Outlined.CheckCircle;FindingLevelUi.REVIEW->Icons.Outlined.Visibility;FindingLevelUi.HIGH_ATTENTION->Icons.Outlined.Warning;FindingLevelUi.INCOMPLETE->Icons.Outlined.ErrorOutline};val tint=when(level){FindingLevelUi.NO_KNOWN_ISSUE->MaterialTheme.colorScheme.secondary;FindingLevelUi.HIGH_ATTENTION->MaterialTheme.colorScheme.error;else->Gold};Icon(icon,null,tint=tint)}
@Composable private fun FindingSection(title:String,lines:List<String>){Text(tr(title),style=MaterialTheme.typography.labelLarge,color=Gold);lines.forEach{Text(tr(it),style=MaterialTheme.typography.bodyMedium)}}

@Composable private fun AiExplanationCard(text:String){Surface(color=MaterialTheme.colorScheme.secondary.copy(alpha=.08f),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AutoAwesome,null,tint=MaterialTheme.colorScheme.secondary);Spacer(Modifier.width(8.dp));Text(tr("AI explanation"),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.secondary)};Text(text);Text(tr("This explanation cannot change the detector result or approve an action."),style=MaterialTheme.typography.bodySmall,color=Muted)}}}

@Composable private fun ProtectionSetup(summary:ProtectionSummaryUi,signatures:String,rules:String,vectors:String,permissionRefresh:Int,app:PaladinoApplication,pack:androidx.activity.result.ActivityResultLauncher<Array<String>>,vm:SecurityViewModel,busy:Boolean,onSettings:(()->Unit)?){
 val context=androidx.compose.ui.platform.LocalContext.current
 val dropped by app.notificationGuard.dropped.collectAsStateWithLifecycle()
 var enabled by remember(permissionRefresh){mutableStateOf(app.notificationGuard.sources())}
 var installedProtection by remember{mutableStateOf(InstalledAppProtection.enabled(context))}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text(tr("Turn on only the protection you want. Android permissions are requested when you choose a related feature."),style=MaterialTheme.typography.bodyMedium)
  summary.capabilities.forEach{item->CapabilityCard(item,onClick=when(item.title){"Notification observation"->{ {NotificationListener.requestAccess(context)} };"Reviewed knowledge","Security chat","Visual checks"->onSettings;else->null})}
  HorizontalDivider()
  Text(tr("Threat knowledge"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
 Text(tr(signatures));Text(tr(rules));Text(tr(vectors));Text(tr("Historical ESET Android indicator subset; not comprehensive protection. BSD-2-Clause."),style=MaterialTheme.typography.bodySmall,color=Muted)
  val appScan=remember{AppScanLedger(context)}.snapshot()
  Text(tr("Installed-app protection"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
  Text(tr("When enabled, Paladino reads the list of installed apps, app security metadata and installed APK bytes after installs or updates and once a day. The inventory stays on this phone. Optional reputation sends only an 8-character hash prefix."),style=MaterialTheme.typography.bodySmall,color=Muted)
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Switch(checked=installedProtection,onCheckedChange={value->InstalledAppProtection.setEnabled(context,value);installedProtection=value});Spacer(Modifier.width(12.dp));Text(tr(if(installedProtection)"Installed-app protection enabled" else "Enable installed-app protection"),fontWeight=FontWeight.SemiBold)}
  Text(tr(when{!installedProtection->"Background installed-app checks are off.";appScan.lastRun==0L->"Initial installed-app check is scheduled.";else->"Last installed-app run checked %1\$s apps; %2\$s unchanged apps were skipped."},appScan.checked,appScan.skipped),style=MaterialTheme.typography.bodySmall,color=Muted)
  if(appScan.failed>0)Text(tr("%1\$s installed apps could not be checked and will be retried.",appScan.failed),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
  Text(tr(if(ai.petologic.paladino.BuildConfig.REPUTATION_URL.isBlank())"Private hash-prefix reputation is not configured." else "Private hash-prefix reputation is configured. Full hashes and package names are not sent."),style=MaterialTheme.typography.bodySmall,color=Muted)
  OutlinedButton(onClick=vm::checkInstalledApps,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(tr("Check all visible installed apps"))}
  OutlinedButton(onClick={pack.launch(arrayOf("application/json"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(tr("Import signed threat pack"))}
  if(ai.petologic.paladino.BuildConfig.THREAT_MANIFEST_URL.isBlank())Text(tr("Automatic online updates are not configured."),color=Muted) else OutlinedButton(onClick=vm::checkUpdates,enabled=!busy){Text(tr("Check threat updates now"))}
  Text("${ai.petologic.paladino.BuildConfig.DISTRIBUTION_CHANNEL} · ${ai.petologic.paladino.BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.labelSmall,color=Muted)
  HorizontalDivider()
  Text(tr("Observed notification apps"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
  Text(tr("Only selected apps are observed. Messages are never blocked or dismissed."),style=MaterialTheme.typography.bodySmall,color=Muted)
  Text(tr("Skipped events: %1\$s",dropped),style=MaterialTheme.typography.bodySmall,color=Muted)
  val sources=(NotificationListener.snapshot.map{it.packageName}+enabled).distinct().sorted()
  if(sources.isEmpty())Text(tr("Grant notification access, then return after another app posts a notification."),color=Muted)
  sources.forEach{source->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=source in enabled,onCheckedChange={value->app.notificationGuard.setEnabled(source,value);enabled=app.notificationGuard.sources()});Text(runCatching{context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(source,0)).toString()}.getOrDefault(source),modifier=Modifier.weight(1f))}}
  Spacer(Modifier.height(24.dp))
 }
}

@Composable private fun EmptyCard(title:String,body:String,onAction:()->Unit,action:String){OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Outlined.Shield,null,tint=Gold);Text(tr(title),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(tr(body),color=Muted);Button(onClick=onAction){Text(tr(action))}}}}
