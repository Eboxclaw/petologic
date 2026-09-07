package ai.petologic.paladino
import ai.petologic.paladino.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.room.withTransaction
import java.util.UUID

/** Application-owned sessions. UI and future Sprite/service clients attach to this controller hub. */
class SessionHub(private val app:PaladinoApplication){
 private val prefs=app.getSharedPreferences("session_hub",android.content.Context.MODE_PRIVATE)
 private val controllers=mutableMapOf<String,SessionController>()
 fun controller(id:String):SessionController=synchronized(controllers){controllers.getOrPut(id){SessionController(app,id).also{it.start()}}}
 val active=MutableStateFlow(controller("default"))
 private val restored=CompletableDeferred<Unit>()
 init { app.scope.launch { app.ready.await();val saved=prefs.getString("active","default")?:"default";if(app.memory.dao.session(saved)?.archived==false)activate(saved);restored.complete(Unit) } }
 private fun activate(id:String){active.value=controller(id);prefs.edit().putString("active",id).apply()}
 val sessions=app.memory.dao.sessions().stateIn(app.scope,SharingStarted.Eagerly,emptyList())
 fun openFromWidget(id:String){app.scope.launch{app.ready.await();restored.await();if(app.memory.dao.session(id)?.archived==false)activate(id)}}
 fun open(id:String){app.scope.launch{app.ready.await();if(app.memory.dao.session(id)!=null)activate(id)}}
 fun create(){app.scope.launch{app.ready.await();val id=UUID.randomUUID().toString();app.memory.dao.session(SessionRow(id,"Conversation ${sessions.value.size+1}"));activate(id);controller(id).log("session_created","New isolated session")}}
 fun archive(id:String,archived:Boolean){app.scope.launch{app.ready.await();controller(id).cancel();val row=app.memory.dao.session(id)?:return@launch;app.memory.dao.session(row.copy(archived=archived));controller(id).log(if(archived)"archived" else "restored","User action");if(archived&&active.value.sessionId==id){val other=sessions.value.firstOrNull{!it.archived&&it.id!=id};if(other!=null)open(other.id)else create()}}}
 fun branch(id:String){app.scope.launch{app.ready.await();val source=app.memory.dao.session(id)?:return@launch;val child=UUID.randomUUID().toString();app.memory.db.withTransaction{
  app.memory.dao.session(source.copy(id=child,title=source.title+" · branch",createdAt=System.currentTimeMillis(),parentId=id,archived=false))
  for(message in app.memory.dao.allMessages(id))app.memory.dao.message(message.copy(id=UUID.randomUUID().toString(),sessionId=child))
 };activate(child);controller(child).log("branched","Copied conversation from $id; separate future context and memory")}}
 fun onUiHidden(){val keep=if(SpriteOverlayService.running.value)active.value.sessionId else null;synchronized(controllers){controllers.values.toList()}.forEach{if(it.sessionId!=keep)it.cancel()}}
}
