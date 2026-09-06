package ai.petologic.paladino
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import ai.petologic.paladino.data.*
import ai.petologic.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class MemoryIntegrationTest {
 @Test fun approved_write_is_idempotent_and_deleted_note_is_not_retrieved()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val db=Room.inMemoryDatabaseBuilder(context,PaladinoDatabase::class.java).build()
  try{
   val repo=MemoryRepository(context,db);val policy=ApprovalPolicy();val create=policy.propose("notes.create","lavender notebook")
   repo.propose(create);repo.executeNote(create,create.argumentHash);repo.executeNote(create,create.argumentHash)
   assertEquals(1,repo.dao.notes().size)
   repo.reconcile();assertEquals(create.id,repo.search("lavender").first().id)
   val delete=policy.propose("notes.delete",create.id);repo.propose(delete);repo.executeNote(delete,delete.argumentHash)
   assertTrue(repo.search("lavender").isEmpty()) // deliberately before index reconciliation
   repo.reconcile();assertTrue(repo.search("lavender").isEmpty())
  }finally{db.close()}
 }
}
