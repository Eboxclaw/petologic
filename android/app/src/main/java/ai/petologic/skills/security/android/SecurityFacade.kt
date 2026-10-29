package ai.petologic.skills.security.android

import ai.petologic.skills.security.ToolResultEnvelope
import ai.petologic.skills.security.analysis.MessageAnalyzer
import ai.petologic.skills.security.analysis.UrlAnalyzer
import ai.petologic.skills.security.findingEnvelope
import ai.petologic.skills.security.parseSecurityCommand
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.first

/**
 * The only surface the three model-facing tools call. Query and scan are pure reads; actions are
 * handoffs (Android opens the screen, the user decides) and are always reached through the
 * existing ActionProposal approval flow in SessionController.
 */
object SecurityFacade {
 suspend fun query(context:Context,argument:String):String=try{
  val cmd=parseSecurityCommand(argument)
  when(cmd.subject){
   "protection"->ToolResultEnvelope.ok("security.query",(context.applicationContext as ai.petologic.paladino.PaladinoApplication).guard.protection().toString())
   "findings","finding"->{
    val guard=(context.applicationContext as ai.petologic.paladino.PaladinoApplication).guard
    guard.expire()
    val rows=guard.findings.first().filter{cmd.subject=="findings"||it.id==cmd.argument}.take(5)
    ToolResultEnvelope.ok("security.query",if(rows.isEmpty())"No recorded findings match. No scan was performed by this query." else rows.joinToString("\n"){it.json})
   }
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
 }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){ToolResultEnvelope.error("invalid_command",e.message?:"Could not read the command.")}

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

 /** Bind approval to a current package version or notification instance/content. */
 fun prepareAction(context:Context,argument:String):String {
  val cmd=parseSecurityCommand(argument)
  require(cmd.subject in setOf("open_app_settings","uninstall_handoff","dismiss_notification")){"Unsupported action."}
  return org.json.JSONObject().put("command",argument).put("target",targetFingerprint(context,argument)).toString()
 }
 fun validateAction(context:Context,bound:String){
  val data=org.json.JSONObject(bound)
  require(data.getString("target")==targetFingerprint(context,data.getString("command"))){"Target changed. Review a new proposal."}
 }
 fun actionDescription(bound:String):String=runCatching{
  val cmd=parseSecurityCommand(org.json.JSONObject(bound).getString("command"))
  when(cmd.subject){
   "open_app_settings"->"Open Android settings for ${cmd.argument}"
   "uninstall_handoff"->"Open Android uninstall confirmation for ${cmd.argument}"
   "dismiss_notification"->"Request dismissal of the selected notification"
   else->"Unsupported action"
  }
 }.getOrDefault("Invalid security action")
 private fun targetFingerprint(context:Context,argument:String):String {
  val cmd=parseSecurityCommand(argument)
  return if(cmd.subject=="dismiss_notification"){
   val entry=NotificationListener.recent(context,null,256).singleOrNull{it.key==cmd.argument}
   requireNotNull(entry){"Notification is unavailable."}
   ai.petologic.core.digest(entry.toString())
  }else{
   require(cmd.subject in setOf("open_app_settings","uninstall_handoff"))
   require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(cmd.argument)){"Invalid package name."}
   val info=context.packageManager.getPackageInfo(cmd.argument,0)
   ai.petologic.core.digest("${cmd.argument}:${info.longVersionCode}:${info.lastUpdateTime}:${info.firstInstallTime}")
  }
 }
 suspend fun executeBoundAction(context:Context,bound:String):String {
  validateAction(context,bound)
  val argument=org.json.JSONObject(bound).getString("command")
  val cmd=parseSecurityCommand(argument)
  val result=executeAction(context,argument)
  if(cmd.subject=="dismiss_notification"&&result.startsWith("OK|")){
   repeat(15){
    kotlinx.coroutines.delay(100)
    if(!NotificationListener.accessGranted(context)||!NotificationListener.connected())return result
    if(NotificationListener.snapshot.none{it.key==cmd.argument})return ToolResultEnvelope.ok("security.action","Confirmed notification removal. The underlying message is unchanged.")
   }
  }
  return result
 }
 /** Runs only after the user approved the ActionProposal. Android owns the outcome. */
 private fun executeAction(context:Context,argument:String):String{
  val cmd=runCatching{parseSecurityCommand(argument)}.getOrElse{
   return ToolResultEnvelope.error("invalid_command",it.message?:"Could not read the command.")
  }
  if(cmd.subject=="dismiss_notification"){
   val granted=runCatching{NotificationListener.dismiss(context,cmd.argument)}
   return when{
    granted.getOrDefault(false)->ToolResultEnvelope.ok("security.action","Requested notification dismissal. Removal is not yet confirmed; the underlying message is unchanged.")
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
