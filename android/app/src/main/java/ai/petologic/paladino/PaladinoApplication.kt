package ai.petologic.paladino
import android.app.Application
import androidx.room.Room
import ai.petologic.paladino.data.*
import ai.petologic.paladino.runtime.*
import kotlinx.coroutines.*

class PaladinoApplication:Application(){
 lateinit var tinyPets:TinyPetManager;private set
 lateinit var memory:MemoryRepository;private set
 lateinit var local:LocalModel;private set
 lateinit var modelLibrary:ModelLibrary;private set
 lateinit var embedder:SmallEmbedder;private set
 lateinit var credentials:CredentialStore;private set
 lateinit var agent:PaladinoAgent;private set
 lateinit var sessionHub:SessionHub;private set
 val inference:ai.petologic.paladino.inference.LocalInferenceBackend by lazy{ai.petologic.paladino.inference.BackendFactory.create(this,local,modelLibrary)}
 val appUpdater by lazy{AppUpdater(this)}
 val ready=CompletableDeferred<Unit>()
 val modelsReady=CompletableDeferred<Unit>()
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 override fun onCreate(){
  super.onCreate()
  tinyPets=TinyPetManager(this)
  val db=Room.databaseBuilder(this,PaladinoDatabase::class.java,"paladino.db").addMigrations(*DatabaseMigrations.ALL).build()
  modelLibrary=ModelLibrary(this)
  embedder=SmallEmbedder(this,modelLibrary)
  memory=MemoryRepository(this,db,embedder);local=LocalModel(this,modelLibrary);credentials=CredentialStore(this)
  agent=PaladinoAgent(inference,OpenRouterTransport(),credentials)
  sessionHub=SessionHub(this)
  scope.launch{try{memory.dao.recoverTasks();memory.dao.cancelPending();if(memory.dao.session("default")==null)memory.dao.session(SessionRow("default","First conversation"));ready.complete(Unit);try{memory.reconcile()}catch(_:Exception){}}catch(e:Exception){ready.completeExceptionally(e)}}
  scope.launch{try{modelLibrary.verifyAll();modelsReady.complete(Unit);local.verify();embedder.verify()}catch(e:Exception){modelsReady.completeExceptionally(e)}}
 }
 override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level>=TRIM_MEMORY_UI_HIDDEN)scope.launch{inference.unload();embedder.unload()}}
}
