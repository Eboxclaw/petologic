package ai.petologic.skills.security.android

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Opt-in notification awareness (plan 13 Sprint C). Android binds this service only after the
 * user grants notification access; until then the snapshot stays empty and every read fails
 * closed with a permission error. Keys, not contents, are the stable handle for dismissal.
 */
class NotificationListener:NotificationListenerService(){
 data class Entry(val key:String,val packageName:String,val title:String,val text:String,val whenMs:Long)

 override fun onListenerConnected(){instance=this;refreshSnapshot()}
 override fun onNotificationPosted(sbn:StatusBarNotification){refreshSnapshot()}
 override fun onNotificationRemoved(sbn:StatusBarNotification){refreshSnapshot()}
 override fun onDestroy(){if(instance===this)instance=null;super.onDestroy()}

 private fun refreshSnapshot(){
  val active=try{activeNotifications?:emptyArray()}catch(_:Exception){emptyArray()}
  snapshot=active.map{sbn->Entry(sbn.key,sbn.packageName,extrude(sbn.notification,"android.title"),
   extrude(sbn.notification,"android.text"),sbn.notification.`when`)}.sortedByDescending{it.whenMs}
 }

 private fun extrude(notification:Notification,key:String):String=(notification.extras?.get(key))?.toString()?.take(140)?:""

 companion object{
  @Volatile private var instance:NotificationListener?=null
  @Volatile var snapshot:List<Entry> = emptyList();private set

  fun accessGranted(context:Context):Boolean=
   androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

  fun requestAccess(context:Context){
   context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  fun recent(context:Context,packageFilter:String?,limit:Int=10):List<Entry>{
   if(!accessGranted(context))throw SecurityException("notification_access")
   return snapshot.filter{packageFilter==null||it.packageName==packageFilter}.take(limit)
  }

  fun dismiss(context:Context,key:String):Boolean{
   if(!accessGranted(context))throw SecurityException("notification_access")
   val listener=instance?:return false
   return try{listener.cancelNotification(key);true}catch(_:Exception){false}
  }
 }
}
