package ai.petologic.skills.security.android

import ai.petologic.skills.security.ToolResultEnvelope
import ai.petologic.skills.security.analysis.MessageAnalyzer
import ai.petologic.skills.security.analysis.UrlAnalyzer
import ai.petologic.skills.security.findingEnvelope
import ai.petologic.skills.security.parseSecurityCommand
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The only surface the three model-facing tools call. Query and scan are pure reads; actions are
 * handoffs (Android opens the screen, the user decides) and are always reached through the
 * existing ActionProposal approval flow in SessionController.
 */
object SecurityFacade {
 fun query(context:Context,argument:String):String=runCatching{
  val cmd=parseSecurityCommand(argument)
  when(cmd.subject){
   "device_status"->ToolResultEnvelope.ok("security.query",SecurityQueries.deviceStatus(context))
   "vpn_status"->ToolResultEnvelope.ok("security.query",SecurityQueries.vpnStatus(context))
   "notification_access"->ToolResultEnvelope.ok("security.query",SecurityQueries.notificationAccess(context))
   "notifications"->{
    if(!NotificationListener.accessGranted(context))return ToolResultEnvelope.error("permission_required",
     "Paladino does not have notification access. Grant it in Android notification settings; the app never asks silently.")
    val filter=cmd.argument
    val entries=try{NotificationListener.recent(context,if(filter=="all")null else filter,10)}
     catch(_:SecurityException){return ToolResultEnvelope.error("permission_required","notification_access")}
    ToolResultEnvelope.ok("security.query",if(entries.isEmpty())"No notifications match right now."
     else entries.joinToString(" || "){"${it.key} · ${it.packageName}: ${it.title} — ${it.text}"})
   }
   "call_screening_status"->ToolResultEnvelope.ok("security.query",SecurityQueries.callScreeningStatus(context))
   "app"->findingEnvelope("security.query",AppInspector.inspect(context,cmd.argument))
   "apps"->{
    val findings=AppInspector.suspiciousApps(context)
    ToolResultEnvelope.ok("security.query",if(findings.isEmpty())"No apps with higher-risk capabilities are visible to Paladino."
     else findings.joinToString(" || "){it.render()})
   }
   else->ToolResultEnvelope.error("unsupported_command","security_query subjects: device_status, app | pkg, apps | suspicious, vpn_status, notification_access, call_screening_status")
  }
 }.getOrElse{ToolResultEnvelope.error("invalid_command",it.message?:"Could not read the command.")}

 fun scan(context:Context,argument:String):String=runCatching{
  val cmd=parseSecurityCommand(argument)
  when(cmd.subject){
   "url"->findingEnvelope("security.scan",UrlAnalyzer.scanUrl(cmd.argument))
   "text"->findingEnvelope("security.scan",MessageAnalyzer.scanMessage(cmd.argument))
   "app"->findingEnvelope("security.scan",AppInspector.inspect(context,cmd.argument))
   "installed_apps"->{
    val findings=AppInspector.suspiciousApps(context)
    ToolResultEnvelope.ok("security.scan",if(findings.isEmpty())"No apps with higher-risk capabilities are visible to Paladino."
     else findings.joinToString(" || "){it.render()})
   }
   else->ToolResultEnvelope.error("unsupported_command","security_scan subjects: url | link, text | message, app | pkg, installed_apps")
  }
 }.getOrElse{ToolResultEnvelope.error("invalid_command",it.message?:"Could not read the command.")}

 /** Runs only after the user approved the ActionProposal. Android owns the outcome. */
 fun executeAction(context:Context,argument:String):String{
  val cmd=runCatching{parseSecurityCommand(argument)}.getOrElse{
   return ToolResultEnvelope.error("invalid_command",it.message?:"Could not read the command.")
  }
  if(cmd.subject=="dismiss_notification"){
   val granted=runCatching{NotificationListener.dismiss(context,cmd.argument)}
   return when{
    granted.getOrDefault(false)->ToolResultEnvelope.ok("security.action","Dismissed the notification.")
    runCatching{NotificationListener.accessGranted(context)}.getOrDefault(false)->ToolResultEnvelope.error("not_found","That notification is already gone or the id does not match.")
    else->ToolResultEnvelope.error("permission_required","Paladino does not have notification access; grant it in Android notification settings first.")
   }
  }
  if(cmd.subject!="open_app_settings"&&cmd.subject!="uninstall_handoff")
   return ToolResultEnvelope.error("unsupported_command","security_action supports open_app_settings | pkg, uninstall_handoff | pkg and dismiss_notification | id.")
  val action=if(cmd.subject=="open_app_settings")android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS else Intent.ACTION_DELETE
  return try{
   context.startActivity(Intent(action,Uri.parse("package:${cmd.argument}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   if(cmd.subject=="open_app_settings")
    ToolResultEnvelope.ok("security.action","Opened Android's app settings for ${cmd.argument}. Handoff: Android shows the screen; nothing was changed by Paladino.")
   else
    ToolResultEnvelope.ok("security.action","Opened Android's uninstall screen for ${cmd.argument}. Uninstall happens only if you confirm it in Android.")
  }catch(_:Exception){
   ToolResultEnvelope.error("handoff_failed","Android refused to open that screen for ${cmd.argument}.")
  }
 }
}
