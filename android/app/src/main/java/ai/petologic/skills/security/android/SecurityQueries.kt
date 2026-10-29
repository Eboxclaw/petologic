package ai.petologic.skills.security.android

import android.app.KeyguardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telecom.TelecomManager
import android.app.role.RoleManager

/** Read-only device state for security_query. Nothing here changes the phone. */
object SecurityQueries {
 fun deviceStatus(context:Context):String{
  val keyguard=context.getSystemService(KeyguardManager::class.java)
  return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · screen lock: "+
   if(keyguard!=null&&keyguard.isDeviceSecure)"on" else "off"
 }
 fun vpnStatus(context:Context):String{
  val connectivity=context.getSystemService(ConnectivityManager::class.java)
  val active=connectivity?.activeNetwork?:return "No active network."
  val caps=connectivity.getNetworkCapabilities(active)?:return "No active network."
  val vpn=caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
  return if(vpn)"A VPN is currently active." else "No VPN is active right now."
 }
 fun notificationAccess(context:Context):String{
  val listeners=android.provider.Settings.Secure.getString(context.contentResolver,"enabled_notification_listeners")?:""
  return if(listeners.contains(context.packageName))"Paladino has notification access."
  else "Paladino does not have notification access. It can be granted in Android notification settings; the app never asks silently."
 }
 fun callScreeningStatus(context:Context):String{
  val roles=context.getSystemService(RoleManager::class.java)?:return "Call screening role is unavailable on this Android."
  val available=roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
  return when{
   !available->"Call screening is not available on this device."
   roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)->"Paladino holds the call screening role."
   else->"Call screening is available but held by another app (or nobody)."
  }
 }
 fun appScreeningIntro():String="Call screening can be requested from this app's UI in a later release; Android requires an explicit user-granted role."
}
