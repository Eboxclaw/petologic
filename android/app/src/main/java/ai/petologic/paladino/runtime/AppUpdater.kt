package ai.petologic.paladino.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ai.petologic.paladino.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Pure update metadata and comparisons; no Android objects, unit-testable on the JVM. */
object AppUpdate{
 /** "v0.2.1-preview" → [0,2,1]. Returns null when no numeric version can be read. */
 fun parseVersion(name:String):List<Int>?{
  val head=name.trim().trimStart('v','V').substringBefore('-').trim()
  val parts=head.split('.').map{it.toIntOrNull()?:return null}
  return if(parts.isEmpty())null else parts
 }

 /** -1/0/1 with missing components read as 0. */
 fun compareVersions(a:List<Int>,b:List<Int>):Int{
  for(i in 0 until maxOf(a.size,b.size)){
   val x=a.getOrElse(i){0};val y=b.getOrElse(i){0}
   if(x!=y)return if(x>y)1 else -1
  }
  return 0
 }

 /** Numeric triple comparison; "-preview" suffixes are ignored on both sides. */
 fun isNewer(remote:String,current:String):Boolean{
  val r=parseVersion(remote)?:return false
  val c=parseVersion(current)?:return false
  return compareVersions(r,c)>0
 }

 data class ReleaseInfo(
  val tag:String,val version:List<Int>,val notes:String,
  val apkName:String,val apkUrl:String,val apkSize:Long,val shaUrl:String?
 )

 /**
  * Picks the newest release carrying an .apk asset whose version beats [currentVersion].
  * The /releases list (not /releases/latest, which hides prereleases) is the source.
  */
 fun parseReleases(json:String,currentVersion:String):ReleaseInfo?{
  val releases=Json.parseToJsonElement(json).jsonArray
  val current=parseVersion(currentVersion)
  var best:ReleaseInfo?=null
  for(entry in releases){
   val obj=entry.jsonObject
   val tag=obj["tag_name"]?.jsonPrimitive?.contentOrNull?:continue
   val version=parseVersion(tag)?:continue
   if(current==null||compareVersions(version,current)<=0)continue
   if(best!=null&&compareVersions(version,best.version)<=0)continue
   val assets=obj["assets"]?.jsonArray?:continue
   val apk=assets.map{it.jsonObject}.firstOrNull{(it["name"]?.jsonPrimitive?.contentOrNull?:"").endsWith(".apk")}?:continue
   val name=apk["name"]?.jsonPrimitive?.contentOrNull?:continue
   val sha=assets.map{it.jsonObject}.firstOrNull{(it["name"]?.jsonPrimitive?.contentOrNull?:"")=="SHA256SUMS.txt"}
   best=ReleaseInfo(
    tag=tag,version=version,notes=obj["body"]?.jsonPrimitive?.contentOrNull?:"",
    apkName=name,apkUrl=apk["browser_download_url"]?.jsonPrimitive?.contentOrNull?:continue,
    apkSize=apk["size"]?.jsonPrimitive?.longOrNull?:0L,
    shaUrl=sha?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
   )
  }
  return best
 }

 /** First checksum line matching the APK filename in a SHA256SUMS.txt body. */
 fun checksumFor(sumFileBody:String,filename:String):String?{
  for(line in sumFileBody.lineSequence()){
   val parts=line.trim().split(Regex("\\s+"),limit=2)
   if(parts.size==2&&parts[1]==filename&&parts[0].matches(Regex("[0-9a-fA-F]{64}")))return parts[0].lowercase()
  }
  return null
 }

 fun hash(file:File):String{
  val md=MessageDigest.getInstance("SHA-256")
  file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;md.update(b,0,n)}}
  return md.digest().joinToString(""){"%02x".format(it)}
 }
}

sealed class AppUpdateState{
 data object Idle:AppUpdateState()
 data object Checking:AppUpdateState()
 data class UpToDate(val current:String):AppUpdateState()
 data class Available(val release:AppUpdate.ReleaseInfo):AppUpdateState()
 data class Ready(val release:AppUpdate.ReleaseInfo,val file:File):AppUpdateState()
 data class Failed(val message:String):AppUpdateState()
}

/**
 * In-app updates (plan 09 S2): check the public downloads repo on demand, download the newer APK
 * with the same stall-proof Range-resume downloader as the models, verify it against the release
 * SHA256SUMS, then hand it to the Android package installer. Same release signature, so installing
 * never requires uninstalling; conversations, notes and models stay.
 */
class AppUpdater(private val context:Context){
 val state=MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
 val status=MutableStateFlow("")
 val progress=MutableStateFlow<Float?>(null)
 private val downloader=ModelDownloader(status,progress)
 private val client by lazy{OkHttpClient.Builder()
  .callTimeout(30,TimeUnit.SECONDS)
  .readTimeout(30,TimeUnit.SECONDS)
  .build()}
 private val directory=File(context.cacheDir,"updates").apply{mkdirs()}

 init{
  // Update files are disposable; a fresh check re-downloads. Nothing here touches user data.
  directory.listFiles()?.forEach{it.delete()}
 }

 val canInstall:Boolean get()=context.packageManager.canRequestPackageInstalls()

 suspend fun check(force:Boolean=false)=withContext(Dispatchers.IO){
  val current=state.value
  if(!force&&(current is AppUpdateState.Available||current is AppUpdateState.Ready))return@withContext
  state.value=AppUpdateState.Checking
  try{
   val body=fetchJson("https://api.github.com/repos/Eboxclaw/petologic-downloads/releases?per_page=10")
   val release=AppUpdate.parseReleases(body,BuildConfig.VERSION_NAME)
   state.value=if(release!=null)AppUpdateState.Available(release) else AppUpdateState.UpToDate(BuildConfig.VERSION_NAME)
  }catch(e:Exception){
   state.value=AppUpdateState.Failed(e.message?:"Could not reach the updates page.")
  }
 }

 suspend fun download(release:AppUpdate.ReleaseInfo)=withContext(Dispatchers.IO){
  val partial=File(directory,"${release.apkName}.partial")
  try{
   progress.value=0f
   downloader.downloadTo(partial,release.apkUrl,release.apkSize,"update ${release.tag}")
   status.value="Verifying update…"
   val expected=release.shaUrl?.let{AppUpdate.checksumFor(fetchJson(it),release.apkName)}
   if(expected!=null){
    check(AppUpdate.hash(partial)==expected){"The downloaded update failed its checksum. Nothing was changed; try again."}
   }
   val final=File(directory,release.apkName)
   check(partial.renameTo(final)){"Could not stage the update file."}
   state.value=AppUpdateState.Ready(release,final)
  }catch(e:Exception){
   if(e is kotlinx.coroutines.CancellationException)throw e
   partial.delete()
   state.value=AppUpdateState.Failed(e.message?:"The update could not be downloaded.")
  }finally{progress.value=null}
 }

 /** The install intent for a verified file; run it from an Activity context. */
 fun installIntent(file:File):Intent{
  val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
  return Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
   .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
 }

 /** Deep link to the system page that lets Paladino install packages. */
 fun permissionIntent():Intent=Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))

 private fun fetchJson(url:String):String{
  client.newCall(Request.Builder().url(url)
   .header("Accept","application/vnd.github+json")
   .build()).execute().use{response->
   check(response.isSuccessful){"Could not reach the updates page (${response.code})."}
   return response.body.string()
  }
 }
}
