package ai.petologic.skills.security.guard

import ai.petologic.paladino.PaladinoApplication
import ai.petologic.security.Coverage
import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

/** Opt-in, bounded, coalescing observation. Captured text is never used as an agent request. */
class NotificationGuard(private val app:PaladinoApplication){
 private data class Input(val packageName:String,val text:String)
 private val pending=linkedMapOf<String,Input>()
 private val seen=linkedMapOf<String,String>()
 private var generation=0L
 private val signal=Channel<Unit>(Channel.CONFLATED)
 val dropped=MutableStateFlow(0)
 private val prefs get()=app.getSharedPreferences("security_notifications",Context.MODE_PRIVATE)
 fun sources():Set<String> = prefs.getStringSet("sources",emptySet()).orEmpty().toSet()
 fun enabled(packageName:String)=packageName in sources()
 fun setEnabled(packageName:String,value:Boolean){
  val set=sources().toMutableSet();if(value)set.add(packageName)else set.remove(packageName)
  prefs.edit().putStringSet("sources",set).apply()
  if(!value)synchronized(pending){pending.entries.removeAll{it.value.packageName==packageName};generation++}
 }
 fun clear(){synchronized(pending){pending.clear();seen.clear();generation++}}
 fun post(key:String,packageName:String,text:String){
  if(packageName==app.packageName||!enabled(packageName))return
  synchronized(pending){
   val fingerprint=ai.petologic.core.digest(text)
   if(seen[key]==fingerprint)return
   seen[key]=fingerprint
   if(seen.size>256)seen.remove(seen.keys.first())
   if(key !in pending && pending.size>=32){dropped.value++;return}
   pending[key]=Input(packageName,text.take(8193))
  }
  signal.trySend(Unit)
 }
 init{app.scope.launch{
  for(unused in signal){
   while(true){
    val observedGeneration=synchronized(pending){generation}
    val next=synchronized(pending){pending.entries.firstOrNull()?.let{pending.remove(it.key)}}?:break
    if(!enabled(next.packageName)||!ai.petologic.skills.security.android.NotificationListener.accessGranted(app)||!ai.petologic.skills.security.android.NotificationListener.connected())continue
    try{
     // Record only evidence and missing-coverage reasons; source app and raw content are not persisted.
     val finding=app.guard.analyzeText(next.text).copy(kind="notification",coverage=Coverage.PARTIAL,
      limitations=listOf("Android notification text may be redacted or incomplete.","Pattern observation only; no notification was blocked."))
     if(finding.evidence.isNotEmpty()&&enabled(next.packageName)&&ai.petologic.skills.security.android.NotificationListener.accessGranted(app)&&synchronized(pending){generation==observedGeneration})app.guard.record(finding)
    }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){dropped.value++}
   }
  }
 }}
}
