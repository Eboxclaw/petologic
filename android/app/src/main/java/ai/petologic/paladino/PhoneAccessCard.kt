package ai.petologic.paladino

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable internal fun PhoneAccessCard(){
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("phone_reads",0)}
 var expanded by remember{mutableStateOf(false)}
 var calendar by remember{mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){calendar=it}
 var weather by remember{mutableStateOf(prefs.getBoolean("weather",false))}
 var city by remember{mutableStateOf(prefs.getString("city","").orEmpty())}
 Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(tr("Phone access"),style=MaterialTheme.typography.titleLarge)
  Text(tr("Clock, next alarm, calendar and weather. Read-only; no messages sent or events changed."),color=Muted)
  TextButton(onClick={expanded=!expanded}){Text(tr(if(expanded)"Close" else "Manage phone access"))}
  if(expanded){
   Text(tr("Ask: What time is it? · Next alarm · My calendar today · Weather"))
   Button(onClick={permission.launch(Manifest.permission.READ_CALENDAR)},enabled=!calendar,modifier=Modifier.fillMaxWidth()){Text(tr(if(calendar)"Calendar reading allowed" else "Allow calendar reading"))}
   Text(tr("Only calendars synchronized to Android are visible. Up to 20 events for today. Android exposes the next alarm, not every alarm."),style=MaterialTheme.typography.bodySmall,color=Muted)
   OutlinedTextField(city,{city=it.take(100);prefs.edit().putString("city",city).apply()},label={Text(tr("Weather city"))},modifier=Modifier.fillMaxWidth(),singleLine=true)
   Row{Text(tr("Allow Open-Meteo weather"),modifier=Modifier.weight(1f));Switch(weather,{weather=it;prefs.edit().putBoolean("weather",it).apply()})}
   Text(tr("Weather sends this city and its coordinates to Open-Meteo only when requested. No GPS, calendar or conversation is shared. Non-commercial preview API."),style=MaterialTheme.typography.bodySmall,color=Muted)
   Text(tr("Email account: not connected. Gmail and Outlook need an authorized provider connection; Android does not expose their inboxes."),style=MaterialTheme.typography.bodySmall)
  }
 }}
}
@Composable internal fun OverlayHelpCard(blocked:Boolean=false){
 val context=LocalContext.current
 var help by remember{mutableStateOf(false)}
 LaunchedEffect(blocked){if(blocked)help=true}
 TextButton(onClick={help=!help}){Text(tr("Permission blocked?"))}
 if(help){
  Text(tr("If Android says access was denied, open App info → ⋮ → Allow restricted settings, if available and you trust this installation. Then return and enable Display over other apps. Work/supervised devices may prohibit it."),style=MaterialTheme.typography.bodySmall)
  Text(tr("This is an Android restriction. Paladino cannot grant it itself. Do not disable Play Protect. The in-app bubble remains available."),style=MaterialTheme.typography.bodySmall,color=Muted)
  OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+context.packageName)))},modifier=Modifier.fillMaxWidth()){Text(tr("Open Android app info"))}
 }
}
