package ai.petologic.skills.security.guard

import android.app.Service
import android.content.Intent
import android.os.*
import ai.petologic.security.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Isolated UID has no network/storage grants. All inputs are read-only descriptors. */
class ScanWorkerService:Service(){
 private val executor=Executors.newSingleThreadExecutor()
 private val deadline=Executors.newSingleThreadScheduledExecutor()
 private var started=false
 private val messenger=Messenger(object:Handler(Looper.getMainLooper()){
  override fun handleMessage(msg:Message){
   if(msg.what==2){Process.killProcess(Process.myPid());return}
   if(msg.what!=1||started)return
   started=true
   val fd=msg.data.getParcelable<ParcelFileDescriptor>("file")?:return
   val packFd=msg.data.getParcelable<ParcelFileDescriptor>("pack")
   val rulesFd=msg.data.getParcelable<ParcelFileDescriptor>("rules")
   val reply=msg.replyTo
   // Enforce a wall-clock deadline even if a malicious provider blocks read().
   val timer=deadline.schedule({Process.killProcess(Process.myPid())},12,TimeUnit.SECONDS)
   executor.execute{
    try{
     val pack=packFd?.let{ParcelFileDescriptor.AutoCloseInputStream(it).use{source->
      val bytes=source.readAtMost(ThreatPackVerifier.MAX_PACK_BYTES+1)
      require(bytes.size<=ThreatPackVerifier.MAX_PACK_BYTES)
      Json.decodeFromString<ThreatPack>(bytes.decodeToString())
     }}
     val active=pack?.takeIf{it.expiresAt>System.currentTimeMillis()}
     val rules=runCatching{
      val sources=rulesFd?.let{rules->ParcelFileDescriptor.AutoCloseInputStream(rules).use{source->
       val bytes=source.readAtMost(YaraRulesVerifier.MAX_TOTAL_CHARS+1)
       require(bytes.size<=YaraRulesVerifier.MAX_TOTAL_CHARS)
       Json.decodeFromString<YaraRulesPack>(bytes.decodeToString(throwOnInvalidSequence=true)).sources
      }}
      NativeRules(sources.orEmpty())
     }.getOrNull()
     val finding=ParcelFileDescriptor.AutoCloseInputStream(fd).use{FileScanner(rules=rules).scan(it,active?.indicators.orEmpty(),active?.version?.toString()?:"none")}
     reply.send(Message.obtain(null,1).apply{data=Bundle().apply{putString("finding",Json.encodeToString(finding))}})
    }catch(_:Exception){runCatching{reply.send(Message.obtain(null,1).apply{data=Bundle().apply{putString("error","Scanner failed; no complete result is available.")}})}}
    finally{timer.cancel(false);runCatching{fd.close()};runCatching{packFd?.close()};runCatching{rulesFd?.close()}}
   }
  }
 })
 override fun onBind(intent:Intent)=messenger.binder
 override fun onDestroy(){executor.shutdownNow();deadline.shutdownNow();super.onDestroy()}
}
