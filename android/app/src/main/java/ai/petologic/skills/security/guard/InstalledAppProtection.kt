package ai.petologic.skills.security.guard

import ai.petologic.paladino.PaladinoApplication
import ai.petologic.security.MatchType
import ai.petologic.skills.security.android.AppInspector
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class PackageEventReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  if(!InstalledAppProtection.enabled(context))return
  val packageName=intent.data?.schemeSpecificPart?:return
  when(intent.action){
   Intent.ACTION_PACKAGE_ADDED,Intent.ACTION_PACKAGE_REPLACED->InstalledAppProtection.enqueue(context,packageName,"package-event",force=true)
   Intent.ACTION_PACKAGE_REMOVED->if(!intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))AppScanLedger(context).forget(packageName)
  }
 }
}

class InstalledAppScanWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result {
  val app=applicationContext as PaladinoApplication
  val packageName=inputData.getString(KEY_PACKAGE)
  val reason=inputData.getString(KEY_REASON)?:"reconciliation"
  val force=inputData.getBoolean(KEY_FORCE,false)&&runAttemptCount==0
  return try{
   val packages=if(packageName!=null)listOf(packageName) else AppInspector.visibleUserPackages(app)
   val ledger=AppScanLedger(app);var checked=0;var skipped=0;var attention=0;var failed=0;var incomplete=false
   val deadline=android.os.SystemClock.elapsedRealtime()+7*60_000
   for(name in packages){
    if(isStopped)break
    if(android.os.SystemClock.elapsedRealtime()>=deadline){incomplete=true;break}
    val profile=AppInspector.profile(app,name)?:continue
    if(!force&&!ledger.needsScan(profile)){skipped++;continue}
    try{
     val finding=app.guard.scanApp(name,reason)
     ledger.mark(profile);checked++
     if(finding.evidence.any{it.matchType==MatchType.EXACT_SHA256||it.matchType==MatchType.RULE}){
      attention++;InstalledAppProtection.notifyAttention(app,finding.id)
     }
    }catch(_:Exception){failed++}
   }
   ledger.noteRun(checked,skipped,attention,failed,System.currentTimeMillis())
   when{
    incomplete->Result.retry()
    failed>0&&runAttemptCount<2->Result.retry()
    else->Result.success(workDataOf("checked" to checked,"skipped" to skipped,"attention" to attention,"failed" to failed))
    }
  }catch(e:Exception){
   if(runAttemptCount<2)Result.retry() else Result.failure(workDataOf("error" to (e.message?:"Installed-app check failed.")))
  }
 }
 companion object { const val KEY_PACKAGE="package";const val KEY_REASON="reason";const val KEY_FORCE="force" }
}

class AppScanLedger(context:Context){
 private val prefs=context.getSharedPreferences("installed_app_protection",Context.MODE_PRIVATE)
 private fun key(packageName:String)=MessageDigest.getInstance("SHA-256").digest(packageName.encodeToByteArray()).joinToString(""){"%02x".format(it)}
 fun needsScan(profile:AppInspector.InstalledAppProfile)=prefs.getString("app:${key(profile.packageName)}",null)!=profile.versionFingerprint
 fun mark(profile:AppInspector.InstalledAppProfile){prefs.edit().putString("app:${key(profile.packageName)}",profile.versionFingerprint).apply()}
 fun forget(packageName:String){prefs.edit().remove("app:${key(packageName)}").apply()}
 fun noteRun(checked:Int,skipped:Int,attention:Int,failed:Int,at:Long){prefs.edit().putLong("last_run",at).putInt("last_checked",checked).putInt("last_skipped",skipped).putInt("last_attention",attention).putInt("last_failed",failed).apply()}
 data class Snapshot(val lastRun:Long,val checked:Int,val skipped:Int,val attention:Int,val failed:Int)
 fun snapshot()=Snapshot(prefs.getLong("last_run",0),prefs.getInt("last_checked",0),prefs.getInt("last_skipped",0),prefs.getInt("last_attention",0),prefs.getInt("last_failed",0))
}

object InstalledAppProtection {
 private const val PERIODIC="paladino-installed-app-reconciliation"
 private const val CHANNEL="paladino-security-attention"
 private const val PREFS="installed_app_protection"
 private const val ENABLED="enabled"
 fun enabled(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(ENABLED,false)
 fun setEnabled(context:Context,enabled:Boolean){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(ENABLED,enabled).apply()
  if(enabled)schedule(context) else {
   val work=WorkManager.getInstance(context);work.cancelUniqueWork(PERIODIC);work.cancelUniqueWork("$PERIODIC-initial");work.cancelUniqueWork("$PERIODIC-manual")
  }
 }
 fun schedule(context:Context){
  if(!enabled(context))return
  val constraints=Constraints.Builder().setRequiresBatteryNotLow(true).build()
  val periodic=PeriodicWorkRequestBuilder<InstalledAppScanWorker>(24,TimeUnit.HOURS,4,TimeUnit.HOURS).setConstraints(constraints).build()
  WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,periodic)
  WorkManager.getInstance(context).enqueueUniqueWork("$PERIODIC-initial",ExistingWorkPolicy.KEEP,
   OneTimeWorkRequestBuilder<InstalledAppScanWorker>().setConstraints(constraints).build())
 }
 fun enqueue(context:Context,packageName:String,reason:String,force:Boolean){
  val request=OneTimeWorkRequestBuilder<InstalledAppScanWorker>()
   .setInputData(workDataOf(InstalledAppScanWorker.KEY_PACKAGE to packageName,InstalledAppScanWorker.KEY_REASON to reason,InstalledAppScanWorker.KEY_FORCE to force))
   .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
  WorkManager.getInstance(context).enqueueUniqueWork("paladino-app-${packageName.hashCode()}",ExistingWorkPolicy.REPLACE,request)
 }
 fun checkAllNow(context:Context){
  WorkManager.getInstance(context).enqueueUniqueWork("$PERIODIC-manual",ExistingWorkPolicy.REPLACE,
   OneTimeWorkRequestBuilder<InstalledAppScanWorker>().setInputData(workDataOf(InstalledAppScanWorker.KEY_REASON to "user-requested",InstalledAppScanWorker.KEY_FORCE to true)).build())
 }
 internal fun notifyAttention(context:Context,findingId:String){
  if(android.os.Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val manager=context.getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel(CHANNEL,"Security attention",NotificationManager.IMPORTANCE_HIGH))
  val intent=Intent(context,SecurityActivity::class.java).putExtra("finding_id",findingId).putExtra("open_findings",true)
  val pending=PendingIntent.getActivity(context,findingId.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  manager.notify(findingId.hashCode(),NotificationCompat.Builder(context,CHANNEL)
   .setSmallIcon(ai.petologic.paladino.R.drawable.ic_paladino).setContentTitle("Paladino found an exact security match")
   .setContentText("Review the detector evidence and recommended next step.").setContentIntent(pending).setAutoCancel(true).build())
 }
}
