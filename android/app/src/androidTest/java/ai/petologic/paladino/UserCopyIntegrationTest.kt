package ai.petologic.paladino
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import ai.petologic.paladino.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/** Round trip through real files: export → mutate → import restores; wipe clears. */
class UserCopyIntegrationTest {
 private fun build(name:String,context:android.content.Context=InstrumentationRegistry.getInstrumentation().targetContext)=
  Room.databaseBuilder(context,PaladinoDatabase::class.java,name).build()

 private fun wipeSandbox(base:android.content.Context):android.content.Context {
  val root=java.io.File(base.cacheDir,"usercopy-wipe-sandbox-${System.nanoTime()}").apply{mkdirs()}
  return object:android.content.ContextWrapper(base){
   override fun getApplicationContext():android.content.Context=this
   override fun getFilesDir()=java.io.File(root,"files").apply{mkdirs()}
   override fun getCacheDir()=java.io.File(root,"cache").apply{mkdirs()}
   override fun getNoBackupFilesDir()=java.io.File(root,"no_backup").apply{mkdirs()}
   override fun getExternalFilesDir(type:String?)=java.io.File(root,"external").apply{mkdirs()}
   override fun getDatabasePath(name:String)=java.io.File(root,"databases/$name").apply{parentFile?.mkdirs()}
   override fun getDir(name:String,mode:Int)=java.io.File(root,"app_$name").apply{mkdirs()}
   override fun getApplicationInfo()=android.content.pm.ApplicationInfo(base.applicationInfo).apply{
    dataDir=java.io.File(root,"data").apply{mkdirs()}.absolutePath
   }
  }
 }

 @Test fun export_then_import_restores_data()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="usercopy_rt.db"
  val databasesDir=context.getDatabasePath(name).parentFile
  java.io.File(databasesDir,name).delete();java.io.File(databasesDir,name+"-wal").delete();java.io.File(databasesDir,name+"-shm").delete()
  val db=build(name)
  try{
   db.dao().session(SessionRow("s1","Carry me"))
   db.dao().message(MessageRow("m1","user","remember the keys","TINY",sessionId="s1"))
   db.dao().putNote(NoteRow("n1","lavender notebook"))
   val zip=java.io.File(context.cacheDir,"usercopy_rt.zip")
   val copy=UserCopy.export(context,db,name,android.net.Uri.fromFile(zip),"0.1.6-preview")
   assertEquals(1,copy.counts["sessions"]);assertEquals(1,copy.counts["notes"])
   assertTrue(zip.length()>0)
   val manifest=UserCopy.readManifest(context,android.net.Uri.fromFile(zip))!!
   assertTrue(UserCopy.loadable(manifest,3))
   // Destructive change after the snapshot.
   db.dao().deleteNote("n1");assertTrue(db.dao().notes().isEmpty())
   UserCopy.import(context,db,name,android.net.Uri.fromFile(zip))
   val reopened=build(name)
   try{
    assertEquals("Carry me",reopened.dao().session("s1")?.title)
    assertEquals("remember the keys",reopened.dao().allMessages("s1").first().text)
    assertEquals("lavender notebook",reopened.dao().note("n1")?.text)
   }finally{reopened.close()}
  }finally{db.close();context.cacheDir.resolve("usercopy_rt.zip").delete()}
 }

 @Test fun wipe_leaves_no_trace()=runBlocking {
  val context=wipeSandbox(InstrumentationRegistry.getInstrumentation().targetContext)
  val name="usercopy_wipe.db"
  val db=build(name,context)
  try{
   db.dao().putNote(NoteRow("gone","soon forgotten"))
   java.io.File(context.filesDir,"models/example.gguf").apply{parentFile?.mkdirs();writeText("test")}
   UserCopy.wipe(context,db,name)
   assertFalse(context.getDatabasePath(name).exists())
   assertFalse(java.io.File(context.filesDir,"models").exists())
   // A fresh database on the same name starts empty, like a clean install.
   val fresh=build(name,context)
   try{assertTrue(fresh.dao().notes().isEmpty())}finally{fresh.close()}
  }finally{/* database already closed by wipe */}
 }
}
