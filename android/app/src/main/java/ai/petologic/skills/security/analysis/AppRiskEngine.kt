package ai.petologic.skills.security.analysis

import ai.petologic.skills.security.models.SecurityFinding
import ai.petologic.skills.security.models.SecurityVerdict

/** Signals collected from Android by [ai.petologic.skills.security.android.AppInspector]. */
data class AppSignals(
 val installer:String?,
 val requestedPermissions:List<String>,
 val grantedDangerous:List<String>,
 val overlay:Boolean,
 val accessibility:Boolean,
 val deviceAdmin:Boolean,
 val vpnService:Boolean,
 val notificationListener:Boolean
){
 val collected:Boolean get()=installer!=null||requestedPermissions.isNotEmpty()
}

/**
 * Deterministic verdicts only. REVIEW is the default serious bucket ("higher-risk capabilities",
 * plan 13's own example); HIGH_ATTENTION is reserved for a powerful capability combined with an
 * origin signal. The model explains findings; it never upgrades or invents them.
 */
object AppRiskEngine {
 fun assess(subject:String,signals:AppSignals):SecurityFinding{
  if(!signals.collected)return SecurityFinding(subject,SecurityVerdict.UNKNOWN,listOf("Android does not expose information about this app."))
  val reasons=buildList{
   if(signals.accessibility)add("Declares an accessibility service that can read the screen and perform touches.")
   if(signals.deviceAdmin)add("Can act as a device administrator.")
   if(signals.vpnService)add("Declares a VPN service that can route network traffic.")
   if(signals.overlay)add("Can draw over other apps.")
   if(signals.notificationListener)add("Can read notifications.")
   if(signals.installer!=null&&signals.installer !in recognizedStores)add("Installed outside a recognized app store (${signals.installer}).")
   if(signals.installer==null)add("Installer information is unavailable.")
   if(signals.grantedDangerous.size>=3)add("Holds ${signals.grantedDangerous.size} granted sensitive permissions.")
  }
  // HIGH_ATTENTION needs a powerful capability combined with an origin signal (plan 13's scale:
  // even accessibility + overlay + outside store stays REVIEW when installed from a store).
  val outsideStore=signals.installer!=null&&signals.installer !in recognizedStores
  val high=(signals.accessibility&&outsideStore)||
   (signals.deviceAdmin&&signals.accessibility)||
   (signals.vpnService&&outsideStore)
  val verdict=when{
   reasons.isEmpty()->SecurityVerdict.LOW
   high->SecurityVerdict.HIGH_ATTENTION
   else->SecurityVerdict.REVIEW
  }
  if(reasons.isEmpty())return SecurityFinding(subject,verdict,listOf("No higher-risk capabilities found."))
  return SecurityFinding(subject,verdict,reasons)
 }

 private val recognizedStores=setOf("com.android.vending","com.amazon.venezia","com.huawei.appmarket","org.fdroid.fdroid","com.github.android")
}
