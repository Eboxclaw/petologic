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
 lateinit var vectorMemory:LocalVectorMemory;private set
 lateinit var credentials:CredentialStore;private set
 lateinit var agent:PaladinoAgent;private set
 lateinit var sessionHub:SessionHub;private set
 val inference:ai.petologic.paladino.inference.LocalInferenceBackend by lazy{ai.petologic.paladino.inference.BackendFactory.create(this,local,modelLibrary)}
 val appUpdater by lazy{AppUpdater(this)}
 val notificationGuard by lazy{ai.petologic.skills.security.guard.NotificationGuard(this)}
 val guard by lazy{ai.petologic.skills.security.guard.GuardRepository(this)}
 val ready=CompletableDeferred<Unit>()
 val modelsReady=CompletableDeferred<Unit>()
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 override fun onCreate(){
  super.onCreate()
  if(Application.getProcessName().startsWith("$packageName:security_scanner"))return
  tinyPets=TinyPetManager(this)
  val db=Room.databaseBuilder(this,PaladinoDatabase::class.java,"paladino.db").addMigrations(*DatabaseMigrations.ALL).build()
  modelLibrary=ModelLibrary(this)
  vectorMemory=LocalVectorMemory(this,modelLibrary)
  memory=MemoryRepository(this,db,vectorMemory);local=LocalModel(this,modelLibrary);credentials=CredentialStore(this)
  agent=PaladinoAgent(inference,OpenRouterTransport(),credentials)
  sessionHub=SessionHub(this)
  if(BuildConfig.THREAT_MANIFEST_URL.isNotBlank())ai.petologic.skills.security.guard.ThreatUpdateScheduler.schedule(this)
  if(ai.petologic.skills.security.guard.InstalledAppProtection.enabled(this))ai.petologic.skills.security.guard.InstalledAppProtection.schedule(this)
  scope.launch{try{memory.dao.recoverTasks();memory.dao.recoverActions();memory.dao.cancelPending();if(memory.dao.session("default")==null)memory.dao.session(SessionRow("default","First conversation"));ready.complete(Unit);try{memory.reconcile()}catch(_:Exception){}}catch(e:Exception){ready.completeExceptionally(e)}}
  scope.launch{try{modelLibrary.verifyAll();modelsReady.complete(Unit);local.verify();vectorMemory.verify();runCatching{guard.threatMemory.reconcile(guard.packs.currentManifestVersion())}}catch(e:Exception){modelsReady.completeExceptionally(e)}}
 }
 override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(Application.getProcessName().startsWith("$packageName:security_scanner"))return;if(level>=TRIM_MEMORY_UI_HIDDEN)scope.launch{inference.unload();vectorMemory.unload()}}
}
