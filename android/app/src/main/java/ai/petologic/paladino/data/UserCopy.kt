package ai.petologic.paladino.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * A user copy is a zip the user stores outside the app: the Room database plus the
 * non-secret preferences. Models and provider keys are excluded by design — models
 * re-download, and provider keys never leave the device's AndroidKeyStore encryption.
 */
object UserCopy {
 const val FORMAT=1
 /** Prefs carried in a user copy. Order is presentation-only. */
 val PREFS=listOf("preferences","session_hub","tinypet_paladino","phone_reads","sprite_position")
 const val DB_ENTRY="paladino.db"
 const val MANIFEST_ENTRY="manifest.json"

 class CopyFormat(val format:Int,val schemaVersion:Int,val createdAt:String,val appVersion:String,val counts:Map<String,Int>)

 /** Row counts per table, for the manifest and for tests. */
 suspend fun counts(database:PaladinoDatabase):Map<String,Int>{
  val db=database.openHelper.readableDatabase
  return buildMap{
   for(table in listOf("sessions","messages","notes","actions","tasks","reminders")){
    db.query("SELECT COUNT(*) FROM $table").use{c->if(c.moveToFirst())put(table,c.getInt(0))}
   }
  }
 }

 /** True when a copy with [format] and [schemaVersion] can load into an app running [runningSchemaVersion]. */
 fun loadable(format:Int,schemaVersion:Int,runningSchemaVersion:Int):Boolean=
  format==FORMAT&&schemaVersion in 1..runningSchemaVersion
 /** True when this copy can be loaded into an app running [runningSchemaVersion]. */
 fun loadable(copy:JSONObject,runningSchemaVersion:Int):Boolean=
  loadable(copy.optInt("format",-1),copy.optInt("schemaVersion",-1),runningSchemaVersion)

 suspend fun export(context:Context,database:PaladinoDatabase,dbName:String,target:Uri,appVersion:String):CopyFormat{
  // WAL checkpoint first: the main db file becomes a consistent snapshot.
  database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use{it.moveToFirst()}
  val counts=counts(database)
  val schemaVersion=database.openHelper.readableDatabase.version
  val manifest=JSONObject().apply{
   put("format",FORMAT);put("schemaVersion",schemaVersion)
   put("createdAt",java.time.Instant.now().toString())
   put("appVersion",appVersion);put("counts",JSONObject(counts))
  }
  val out=context.contentResolver.openOutputStream(target)?:throw IllegalStateException("Cannot open target.")
  out.use{stream->ZipOutputStream(stream.buffered()).use{zip->
   zip.putNextEntry(ZipEntry(MANIFEST_ENTRY));zip.write(manifest.toString().toByteArray(Charsets.UTF_8));zip.closeEntry()
   zip.putNextEntry(ZipEntry(DB_ENTRY))
   context.getDatabasePath(dbName).inputStream().use{it.copyTo(zip)}
   zip.closeEntry()
   for(name in PREFS){
    val file=File(context.applicationInfo.dataDir,"shared_prefs/$name.xml")
    if(!file.exists())continue
    zip.putNextEntry(ZipEntry("prefs/$name.xml"));file.inputStream().use{it.copyTo(zip)};zip.closeEntry()
   }
  }}
  return CopyFormat(FORMAT,schemaVersion,manifest.getString("createdAt"),manifest.getString("appVersion"),counts)
 }

 /** Reads only the manifest of a copy at [source]; null if the file is not a user copy. */
 fun readManifest(context:Context,source:Uri):JSONObject?{
  val stream=context.contentResolver.openInputStream(source)?:return null
  stream.use{input->ZipInputStream(input.buffered()).use{zip->
   while(true){
    val entry=zip.nextEntry?:return null
    if(entry.name==MANIFEST_ENTRY)return JSONObject(zip.readBytes().toString(Charsets.UTF_8))
    zip.closeEntry()
   }
  }}
  return null
 }

 /**
  * Closes [database], replaces its file with the copy at [source] and restores the
  * carried prefs. The caller must restart the process afterwards.
  */
 suspend fun import(context:Context,database:PaladinoDatabase,dbName:String,source:Uri):CopyFormat{
  val staging=File(context.cacheDir,"user_copy_${System.currentTimeMillis()}").apply{mkdirs()}
  try{
   val stream=context.contentResolver.openInputStream(source)?:throw IllegalStateException("Cannot open copy.")
   var manifest:JSONObject?=null;var dbFile:File?=null;val prefFiles=mutableMapOf<String,File>()
   stream.use{input->ZipInputStream(input.buffered()).use{zip->
    while(true){
     val entry=zip.nextEntry?:break
     val sink=when{
      entry.name==MANIFEST_ENTRY->File(staging,"manifest.json").also{manifest=JSONObject(zip.readBytes().toString(Charsets.UTF_8))}
      entry.name==DB_ENTRY->File(staging,dbName).also{dbFile=it}
      entry.name.startsWith("prefs/")&&entry.name.endsWith(".xml")&&PREFS.contains(entry.name.removePrefix("prefs/").removeSuffix(".xml"))->
       File(staging,entry.name).also{prefFiles[entry.name.removePrefix("prefs/")]=it}
      else->null
     }
     if(sink!=null)sink.apply{parentFile?.mkdirs()}.outputStream().use{zip.copyTo(it)}
     zip.closeEntry()
    }
   }}
   val copy=manifest?:throw IllegalArgumentException("Not a Paladino user copy.")
   if(!loadable(copy,database.openHelper.writableDatabase.version))throw IllegalArgumentException("Newer app version required.")
   val restoredDb=dbFile?:throw IllegalArgumentException("Not a Paladino user copy.")
   database.close()
   val targetDb=context.getDatabasePath(dbName)
   targetDb.parentFile?.mkdirs()
   listOf("-wal","-shm","").forEach{suffix->File(targetDb.parentFile,targetDb.name+suffix).delete()}
   restoredDb.copyTo(targetDb,overwrite=true)
   val prefsDir=File(context.applicationInfo.dataDir,"shared_prefs").apply{mkdirs()}
   for((name,file) in prefFiles)file.copyTo(File(prefsDir,"$name.xml"),overwrite=true)
   return CopyFormat(FORMAT,copy.optInt("schemaVersion"),copy.optString("createdAt"),copy.optString("appVersion"),emptyMap())
  }finally{staging.deleteRecursively()}
 }

 /** Closes [database] and erases everything app-private. The caller must restart the process. */
 fun wipe(context:Context,database:PaladinoDatabase,dbName:String){
  database.close()
  context.getDatabasePath(dbName).parentFile?.deleteRecursively()
  context.filesDir.deleteRecursively()
  context.cacheDir.deleteRecursively()
  context.getExternalFilesDir(null)?.deleteRecursively()
  File(context.applicationInfo.dataDir,"shared_prefs").deleteRecursively()
  context.getDir("app_search",Context.MODE_PRIVATE).deleteRecursively()
  try{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
   for(alias in ks.aliases().toList())if(alias.startsWith("paladino."))ks.deleteEntry(alias)
  }catch(_:Exception){}
 }

 /** Relaunches the app in a clean task, then ends this process. */
 fun restart(context:Context){
  val intent=android.content.Intent(context,ai.petologic.paladino.MainActivity::class.java).addFlags(
   Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
  context.startActivity(intent)
  Runtime.getRuntime().exit(0)
 }
}
