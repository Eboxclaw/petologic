package ai.petologic.paladino
import ai.petologic.security.MatchType
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Launcher surface only: no inference, credentials, private messages or execution ownership. */
class PaladinoWidget:AppWidgetProvider(){
 override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){
  val prefs=(context.applicationContext as PaladinoApplication).tinyPets.state.value
  ids.forEach{id->
   val open=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("destination","protection").putExtra("widget_session",prefs.widgetSession)
   val ask=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_chat",true).putExtra("widget_session",prefs.widgetSession)
   val check=Intent(context,ai.petologic.skills.security.guard.SecurityActivity::class.java).putExtra("open_check",true)
   val views=RemoteViews(context.packageName,R.layout.paladino_widget)
   views.setViewVisibility(R.id.widget_caption,if(prefs.widgetCaption)android.view.View.VISIBLE else android.view.View.GONE)
   views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,id,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
   views.setOnClickPendingIntent(R.id.widget_open,PendingIntent.getActivity(context,id+100000,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
   views.setOnClickPendingIntent(R.id.widget_ask,PendingIntent.getActivity(context,id+200000,ask,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
   views.setOnClickPendingIntent(R.id.widget_check,PendingIntent.getActivity(context,id+300000,check,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
   val latest=(context.applicationContext as PaladinoApplication).guard.latest.value.maxByOrNull{it.createdAt}
   val attention=latest?.evidence?.any{it.matchType==MatchType.EXACT_SHA256||it.matchType==MatchType.RULE}==true
   views.setTextViewText(R.id.widget_state,context.uiText(if(attention)"Needs attention · review the latest finding" else "Limited · manual checks ready"))
   manager.updateAppWidget(id,views)
  }
 }
}
