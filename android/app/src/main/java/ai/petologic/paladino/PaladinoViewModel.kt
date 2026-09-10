package ai.petologic.paladino
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.petologic.core.*
import ai.petologic.paladino.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Activity adapter only. Execution and session state live in the application-owned hub. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaladinoViewModel(application:Application):AndroidViewModel(application){
 private val app=application as PaladinoApplication
 val hub=app.sessionHub
 private val active=hub.active
 private val current get()=active.value
 val ui=active.flatMapLatest{it.ui}.stateIn(viewModelScope,SharingStarted.Eagerly,current.ui.value)
 val messages=active.flatMapLatest{it.messages}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val notes=active.flatMapLatest{it.notes}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val options=active.flatMapLatest{it.options}.stateIn(viewModelScope,SharingStarted.Eagerly,SessionOptions())
 val session=active.flatMapLatest{it.sessionInfo}.stateIn(viewModelScope,SharingStarted.Eagerly,current.sessionInfo.value)
 val events=active.flatMapLatest{it.events}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val library=app.modelLibrary
 val modelStatus=app.local.status;val modelReady=app.local.ready;val downloadProgress=app.local.progress
 val semanticReady=app.embedder.ready;val semanticInstalling=app.embedder.installing
 fun importModel(uri:android.net.Uri)=viewModelScope.launch { try { library.import(uri);app.local.verify();app.embedder.verify() } catch(e:Exception) { if(e is CancellationException)throw e;library.status.value=e.message?:"Import failed" } }
 fun scanFolder(uri:android.net.Uri)=viewModelScope.launch { try { app.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);library.rememberAndScan(uri);app.local.verify();app.embedder.verify() } catch(e:Exception) { if(e is CancellationException)throw e;library.status.value=e.message?:"Folder unavailable" } }
 fun downloadModel(id:String)=viewModelScope.launch { try { library.install(id);app.local.verify();app.embedder.verify() } catch(e:Exception) { if(e is CancellationException)throw e;library.status.value=e.message?:"Download failed" } }
 fun discardModelMessage()=current.discardModelMessage()
 fun resumeModelMessage()=current.resumeModelMessage()
 fun send(text:String)=current.send(text)
 fun mode(mode:ExecutionMode)=current.mode(mode)
 fun cancel()=current.cancel()
 fun clearError()=current.clearError()
 fun install()=current.install()
 fun installSemantic()=current.installSemantic()
 fun selectProvider(provider:ai.petologic.paladino.runtime.CloudProvider)=current.selectProvider(provider)
 fun connect(key:String,model:String)=current.connect(key,model)
 fun disconnect()=current.disconnect()
 fun approveCloud()=current.approveCloud()
 fun denyCloud()=current.denyCloud()
 fun approveAction()=current.approveAction()
 fun denyAction()=current.denyAction()
 fun deleteNote(note:NoteRow)=current.deleteNote(note)
 fun updateOptions(value:SessionOptions)=current.updateOptions(value)
 fun selectModel(id:String)=current.selectModel(id)
}
