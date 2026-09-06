package ai.petologic.paladino
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Launcher surface only: no inference, credentials, private messages or execution ownership. */
class PaladinoWidget:AppWidgetProvider(){
 override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){
  ids.forEach{id->
   val intent=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
   val pending=PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   val views=RemoteViews(context.packageName,R.layout.paladino_widget)
   views.setOnClickPendingIntent(R.id.widget_open,pending)
   manager.updateAppWidget(id,views)
  }
 }
}
