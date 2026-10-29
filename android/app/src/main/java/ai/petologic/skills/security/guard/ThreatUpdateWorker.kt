package ai.petologic.skills.security.guard

import android.content.Context
import androidx.work.*
import ai.petologic.paladino.BuildConfig
import ai.petologic.paladino.PaladinoApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

class ThreatUpdateWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result=withContext(Dispatchers.IO){
  val app=applicationContext as PaladinoApplication
  try {
   when(val result=ThreatUpdateClient(app,app.guard).check(BuildConfig.THREAT_MANIFEST_URL)){
    ThreatUpdateResult.NotConfigured->Result.success(workDataOf("status" to "not_configured"))
    is ThreatUpdateResult.Unchanged->Result.success(workDataOf("status" to "unchanged","version" to result.version))
    is ThreatUpdateResult.Updated->Result.success(workDataOf("status" to "updated","version" to result.version))
   }
  }catch(e:IOException){ThreatUpdateClient(app,app.guard).recordFailure(e.message?:"Network update failed.");Result.retry()}
  catch(e:Exception){ThreatUpdateClient(app,app.guard).recordFailure(e.message?:"Threat update rejected.");Result.failure(workDataOf("error" to (e.message?:"Threat update rejected.")))}
 }
}

object ThreatUpdateScheduler {
 private const val PERIODIC="paladino-threat-update-daily"
 private const val IMMEDIATE="paladino-threat-update-now"
 fun schedule(context:Context){
  val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
  val request=PeriodicWorkRequestBuilder<ThreatUpdateWorker>(24,TimeUnit.HOURS,4,TimeUnit.HOURS).setConstraints(constraints).build()
  WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,request)
 }
 fun checkNow(context:Context)=WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE,ExistingWorkPolicy.REPLACE,
  OneTimeWorkRequestBuilder<ThreatUpdateWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build())
}
