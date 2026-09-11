package ai.petologic.paladino
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import ai.petologic.paladino.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/** Round trip through real files: export → mutate → import restores; wipe clears. */
class UserCopyIntegrationTest {
 private fun build(name:String)=Room.databaseBuilder(
  InstrumentationRegistry.getInstrumentation().targetContext,PaladinoDatabase::class.java,name).build()

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
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val name="usercopy_wipe.db"
  val db=build(name)
  try{
   db.dao().putNote(NoteRow("gone","soon forgotten"))
   UserCopy.wipe(context,db,name)
   assertFalse(context.getDatabasePath(name).exists())
   assertFalse(java.io.File(context.filesDir,"models").exists())
   // A fresh database on the same name starts empty, like a clean install.
   val fresh=build(name)
   try{assertTrue(fresh.dao().notes().isEmpty())}finally{fresh.close()}
  }finally{/* database already closed by wipe */}
 }
}
