package ai.petologic.paladino

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

data class TinyPetDefinition(val id:String,val roleId:String,val name:String,val manifestAsset:String)
object TinyPetCatalog { val paladino=TinyPetDefinition("paladino","paladino","0xPaladino","paladino/manifest.yaml");val available=listOf(paladino) }

/** Presentation preferences never grant a role new tools or create another model instance. */
data class TinyPetPreferences(
 val visible:Boolean=true,
 val animate:Boolean=true,
 val sizeDp:Int=64,
 val widgetCaption:Boolean=true,
 val widgetSession:String="",
 val sound:Boolean=true,
 val haptics:Boolean=true
)
class TinyPetManager(private val context:Context){
 private val prefs=context.getSharedPreferences("tinypet_paladino",Context.MODE_PRIVATE)
 val state=MutableStateFlow(TinyPetPreferences(prefs.getBoolean("visible",true),prefs.getBoolean("animate",true),prefs.getInt("size",64).coerceIn(48,88),prefs.getBoolean("caption",true),prefs.getString("session","")?:"",prefs.getBoolean("sound",true),prefs.getBoolean("haptics",true)))
 fun update(value:TinyPetPreferences){
  require(value.sizeDp in 48..88)
  prefs.edit().putBoolean("visible",value.visible).putBoolean("animate",value.animate).putInt("size",value.sizeDp).putBoolean("caption",value.widgetCaption).putString("session",value.widgetSession).putBoolean("sound",value.sound).putBoolean("haptics",value.haptics).apply()
  state.value=value
  val manager=AppWidgetManager.getInstance(context)
  PaladinoWidget().onUpdate(context,manager,manager.getAppWidgetIds(ComponentName(context,PaladinoWidget::class.java)))
 }
}
