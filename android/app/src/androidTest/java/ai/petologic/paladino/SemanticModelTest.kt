package ai.petologic.paladino
import androidx.test.platform.app.InstrumentationRegistry
import ai.petologic.core.*
import ai.petologic.paladino.data.*
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

class SemanticModelTest {
 @Test fun real_encoder_finds_paraphrase_and_separates_unrelated_text()=runBlocking {
  assumeTrue(InstrumentationRegistry.getArguments().getString("realModel")=="true")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PaladinoApplication
  app.embedder.verify();assertTrue(app.embedder.ready.value)
  val a=app.embedder.embed("Where did I park my car?")
  val b=app.embedder.embed("My vehicle is in the parking garage.")
  val c=app.embedder.embed("Banana cake needs flour and eggs.")
  fun dot(x:FloatArray,y:FloatArray)=x.indices.sumOf{(x[it]*y[it]).toDouble()}
  assertEquals(384,a.size);assertEquals(1.0,dot(a,a),0.001)
  assertTrue("Paraphrase should outrank unrelated text",dot(a,b)>dot(a,c)+0.15)
  val db=Room.inMemoryDatabaseBuilder(app,PaladinoDatabase::class.java).build()
  try{
   val repo=MemoryRepository(app,db,app.embedder);val p=ApprovalPolicy().propose("notes.create","My vehicle is in the parking garage.")
   repo.propose(p);repo.executeNote(p,p.argumentHash);repo.reconcile()
   assertEquals(p.id,repo.search("Where did I park my car?").first().id)
  }finally{db.close()}
 }
}
