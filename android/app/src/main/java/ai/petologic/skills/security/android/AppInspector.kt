package ai.petologic.skills.security.android

import ai.petologic.skills.security.analysis.AppRiskEngine
import ai.petologic.skills.security.analysis.AppSignals
import ai.petologic.skills.security.models.SecurityFinding
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/** Collects [AppSignals] from Android with package-visibility limits; assessment stays pure. */
object AppInspector {
 private const val ACCESSIBILITY="android.permission.BIND_ACCESSIBILITY_SERVICE"
 private const val DEVICE_ADMIN="android.permission.BIND_DEVICE_ADMIN"
 private const val VPN="android.permission.BIND_VPN_SERVICE"
 private const val NOTIFICATION_LISTENER="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"

 fun inspect(context:Context,packageName:String):SecurityFinding{
  val pm=context.packageManager
  val info:PackageInfo=runCatching{
   pm.getPackageInfo(packageName,PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
  }.getOrElse{return SecurityFinding("App $packageName",ai.petologic.skills.security.models.SecurityVerdict.UNKNOWN,
   listOf("Android does not expose information about this app (visibility or name)."))}
  val requested=info.requestedPermissions?.toList()?:emptyList()
  val granted=requested.filter{runCatching{pm.checkPermission(it,packageName)}.getOrNull()==PackageManager.PERMISSION_GRANTED}
  val services=info.services?.toList()?:emptyList()
  val signals=AppSignals(
   installer=runCatching{pm.getInstallSourceInfo(packageName).installingPackageName}.getOrNull(),
   requestedPermissions=requested,
   grantedDangerous=granted,
   overlay=requested.contains("android.permission.SYSTEM_ALERT_WINDOW"),
   accessibility=services.any{it.permission==ACCESSIBILITY},
   deviceAdmin=services.any{it.permission==DEVICE_ADMIN},
   vpnService=services.any{it.permission==VPN},
   notificationListener=services.any{it.permission==NOTIFICATION_LISTENER}
  )
  return AppRiskEngine.assess("App $packageName",signals)
 }

 /** Sweep of visible non-system apps, strongest verdicts first; bounded because visibility is bounded. */
 fun suspiciousApps(context:Context,limit:Int=20):List<SecurityFinding>{
  val pm=context.packageManager
  return pm.getInstalledApplications(0).asSequence()
   .filter{it.flags and ApplicationInfo.FLAG_SYSTEM==0}
   .mapNotNull{runCatching{inspect(context,it.packageName)}.getOrNull()}
   .filter{it.verdict==ai.petologic.skills.security.models.SecurityVerdict.REVIEW||it.verdict==ai.petologic.skills.security.models.SecurityVerdict.HIGH_ATTENTION}
   .sortedByDescending{it.verdict.ordinal}
   .take(limit)
   .toList()
 }
}
