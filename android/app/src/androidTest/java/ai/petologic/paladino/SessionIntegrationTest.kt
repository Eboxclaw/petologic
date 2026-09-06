package ai.petologic.paladino
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import ai.petologic.paladino.data.*
import ai.petologic.core.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class SessionIntegrationTest {
 @Test fun session_memory_and_approval_cannot_cross_boundary()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val db=Room.inMemoryDatabaseBuilder(context,PaladinoDatabase::class.java).build()
  try{
   val repo=MemoryRepository(context,db)
   val a=ApprovalPolicy().propose("notes.create","unique violet telescope")
   repo.propose(a,"alpha")
   assertTrue(runCatching{repo.executeNote(a,a.argumentHash,"beta")}.isFailure)
   assertTrue(repo.dao.notes("alpha").isEmpty())
   repo.executeNote(a,a.argumentHash,"alpha");repo.reconcile()
   assertEquals(a.id,repo.search("violet telescope","alpha").first().id)
   assertTrue(repo.search("violet telescope","beta").isEmpty())
   val delete=ApprovalPolicy().propose("notes.delete",a.id);repo.propose(delete,"beta")
   assertTrue(runCatching{repo.executeNote(delete,delete.argumentHash,"beta")}.isFailure)
   assertNotNull(repo.dao.note(a.id))
  }finally{db.close()}
 }
 @Test fun version_two_migration_preserves_notes_and_attaches_default_session()=runBlocking {
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  val name="migration-${System.nanoTime()}.db";val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
  val schema=JSONObject(instrumentation.context.assets.open("schema-v2.json").bufferedReader().use{it.readText()}).getJSONObject("database")
  SQLiteDatabase.openOrCreateDatabase(path,null).use{db->
   val entities=schema.getJSONArray("entities")
   for(i in 0 until entities.length()){
    val e=entities.getJSONObject(i);db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
    val indices=e.optJSONArray("indices")?:org.json.JSONArray();for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
   }
   db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
   db.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema.getString("identityHash")))
   db.execSQL("INSERT INTO notes VALUES('legacy','old saved memory','LOCAL_ONLY',123)")
   db.version=2
  }
  val migrated=Room.databaseBuilder(context,PaladinoDatabase::class.java,name).addMigrations(*DatabaseMigrations.ALL).build()
  try{
   assertEquals("default",migrated.dao().note("legacy")!!.sessionId)
   assertEquals("First conversation",migrated.dao().session("default")!!.title)
   assertEquals("legacy",migrated.dao().pendingIndex().single().noteId)
  }finally{migrated.close();context.deleteDatabase(name)}
 }
 @Test fun session_branch_archive_restore_persist_without_copying_memory()=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PaladinoApplication
  app.ready.await();val id="session-test-${System.nanoTime()}"
  app.memory.dao.session(SessionRow(id,"Lifecycle fixture"))
  app.memory.dao.message(MessageRow("msg-$id","user","only source context",sessionId=id))
  app.memory.dao.putNote(NoteRow("note-$id","private source memory",sessionId=id))
  app.sessionHub.branch(id)
  withTimeout(10000){while(app.sessionHub.active.value.sessionInfo.value.parentId!=id)delay(50)}
  val child=app.sessionHub.active.value.sessionId
  assertEquals("only source context",app.memory.dao.allMessages(child).single().text)
  assertTrue(app.memory.dao.notes(child).isEmpty())
  app.sessionHub.archive(child,true)
  withTimeout(10000){while(app.memory.dao.session(child)?.archived!=true)delay(50)}
  app.sessionHub.archive(child,false)
  withTimeout(10000){while(app.memory.dao.session(child)?.archived!=false)delay(50)}
  assertEquals(id,app.memory.dao.session(child)!!.parentId)
  app.sessionHub.open("default")
 }
}
