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
 override fun onNotificationPosted(sbn:StatusBarNotification){
  if(sbn.packageName==packageName)return
  snapshot=(listOf(entry(sbn))+snapshot.filterNot{it.key==sbn.key}).take(256)
  (application as ai.petologic.paladino.PaladinoApplication).notificationGuard.post(sbn.key,sbn.packageName,
   (sbn.notification.extras?.getCharSequence("android.title")?.toString().orEmpty()+"\n"+sbn.notification.extras?.getCharSequence("android.text")?.toString().orEmpty()).take(8193))
 }
 override fun onNotificationRemoved(sbn:StatusBarNotification){snapshot=snapshot.filterNot{it.key==sbn.key}}
 override fun onListenerDisconnected(){clear();super.onListenerDisconnected()}
 override fun onDestroy(){clear();super.onDestroy()}
 private fun clear(){if(instance===this){instance=null;snapshot=emptyList();(application as ai.petologic.paladino.PaladinoApplication).notificationGuard.clear()}}
 private fun entry(sbn:StatusBarNotification)=Entry(sbn.key,sbn.packageName,extrude(sbn.notification,"android.title"),extrude(sbn.notification,"android.text"),sbn.postTime)


 private fun refreshSnapshot(){
  val active=try{activeNotifications?:emptyArray()}catch(_:Exception){emptyArray()}
  snapshot=active.filter{it.packageName!=packageName}.map(::entry).sortedByDescending{it.whenMs}.take(256)
 }

 private fun extrude(notification:Notification,key:String):String=(notification.extras?.get(key))?.toString()?.take(140)?:""

 companion object{
  @Volatile private var instance:NotificationListener?=null
  @Volatile var snapshot:List<Entry> = emptyList();private set

  fun connected()=instance!=null

  fun accessGranted(context:Context):Boolean=
   androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

  fun requestAccess(context:Context){
   context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  fun recent(context:Context,packageFilter:String?,limit:Int=10):List<Entry>{
   if(!accessGranted(context)){snapshot=emptyList();throw SecurityException("notification_access")}
   return snapshot.filter{packageFilter==null||it.packageName==packageFilter}.take(limit)
  }

  fun dismiss(context:Context,key:String):Boolean{
   if(!accessGranted(context)){snapshot=emptyList();throw SecurityException("notification_access")}
   val listener=instance?:return false
   if(snapshot.none{it.key==key})return false
   return try{listener.cancelNotification(key);true}catch(_:Exception){false}
  }
 }
}
