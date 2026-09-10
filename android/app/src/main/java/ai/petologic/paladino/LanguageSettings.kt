package ai.petologic.paladino

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable internal fun LanguageSettings(){
 val context=LocalContext.current
 Card(Modifier.fillMaxWidth()){
  Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(tr("Language"),style=MaterialTheme.typography.titleMedium)
   Text("Português · English")
   if(Build.VERSION.SDK_INT>=33)TextButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,Uri.parse("package:"+context.packageName)))}){Text(tr("Change app language"))}
   else Text(tr("On Android 12, change the phone language in system settings."),style=MaterialTheme.typography.bodySmall)
  }
 }
}
