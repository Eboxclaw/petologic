package ai.petologic.skills.security.android

import ai.petologic.skills.security.analysis.AppRiskEngine
import ai.petologic.skills.security.analysis.AppSignals
import ai.petologic.skills.security.models.SecurityFinding
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/** Collects [AppSignals] from Android with package-visibility limits; assessment stays pure. */
object AppInspector {
 private const val ACCESSIBILITY="android.permission.BIND_ACCESSIBILITY_SERVICE"
 private const val DEVICE_ADMIN="android.permission.BIND_DEVICE_ADMIN"
 private const val VPN="android.permission.BIND_VPN_SERVICE"
 private const val NOTIFICATION_LISTENER="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"

 data class InstalledAppProfile(
  val packageName:String,
  val versionCode:Long,
  val firstInstallTime:Long,
  val lastUpdateTime:Long,
  val installer:String?,
  val sourceFiles:List<File>,
  val signerSha256:List<String>,
  val signals:AppSignals
 ) {
  val versionFingerprint:String get()="$versionCode:$lastUpdateTime"
 }

 fun profile(context:Context,packageName:String):InstalledAppProfile? {
  val pm=context.packageManager
  val info:PackageInfo=runCatching{
   pm.getPackageInfo(packageName,PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_SIGNING_CERTIFICATES)
  }.getOrNull()?:return null
  val requested=info.requestedPermissions?.toList()?:emptyList()
  val granted=requested.filter{runCatching{pm.checkPermission(it,packageName)==PackageManager.PERMISSION_GRANTED && pm.getPermissionInfo(it,0).protection==android.content.pm.PermissionInfo.PROTECTION_DANGEROUS}.getOrNull()==true}
  val services=info.services?.toList()?:emptyList()
  val installer=runCatching{pm.getInstallSourceInfo(packageName).installingPackageName}.getOrNull()
  val appInfo=info.applicationInfo
  val signals=AppSignals(
   installer=installer,
   requestedPermissions=requested,
   grantedDangerous=granted,
   overlay=requested.contains("android.permission.SYSTEM_ALERT_WINDOW"),
   accessibility=services.any{it.permission==ACCESSIBILITY},
   deviceAdmin=info.receivers.orEmpty().any{it.permission==DEVICE_ADMIN},
   vpnService=services.any{it.permission==VPN},
   notificationListener=services.any{it.permission==NOTIFICATION_LISTENER},
   debuggable=appInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE)!=0,
   targetSdk=appInfo?.targetSdkVersion,
   signatureMissing=info.signingInfo==null
  )
  val sources=buildList {
   appInfo?.sourceDir?.let{add(File(it))}
   appInfo?.splitSourceDirs.orEmpty().forEach{add(File(it))}
  }.distinctBy{it.absolutePath}
  val signers=info.signingInfo?.apkContentsSigners.orEmpty().map{certificate->
   sha256(certificate.toByteArray())
  }.sorted()
  return InstalledAppProfile(packageName,info.longVersionCode,info.firstInstallTime,info.lastUpdateTime,
   installer,sources,signers,signals)
 }

 fun inspect(context:Context,packageName:String):SecurityFinding{
  val profile=profile(context,packageName)?:return SecurityFinding("App $packageName",ai.petologic.skills.security.models.SecurityVerdict.UNKNOWN,
   listOf("Android does not expose information about this app (visibility or name)."))
  return AppRiskEngine.assess("App $packageName",profile.signals)
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

 fun visibleUserPackages(context:Context,limit:Int=500):List<String> = context.packageManager
  .getInstalledApplications(0).asSequence()
  .filter{it.flags and ApplicationInfo.FLAG_SYSTEM==0}
  .map{it.packageName}.sorted().take(limit).toList()

 fun sha256(file:File,maxBytes:Long=2L*1024*1024*1024):String {
  require(file.isFile&&file.length() in 1..maxBytes){"Installed APK is unavailable or exceeds the scan limit."}
  val digest=MessageDigest.getInstance("SHA-256");var total=0L
  file.inputStream().buffered(64*1024).use{input->
   val buffer=ByteArray(64*1024)
   while(true){
    if(Thread.currentThread().isInterrupted)throw InterruptedException("App scan cancelled.")
    val count=input.read(buffer);if(count<0)break
    total+=count;require(total<=maxBytes){"Installed APK exceeds the scan limit."};digest.update(buffer,0,count)
   }
  }
  return digest.digest().joinToString(""){"%02x".format(it)}
 }

 private fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
