package ai.petologic.skills.security

import ai.petologic.skills.security.models.SecurityFinding
import ai.petologic.skills.security.models.SecurityVerdict

/** Stable tool-result grammar from plan 12; the model reads it, tests assert on it. */
object ToolResultEnvelope{
 fun ok(domain:String,detail:String)="OK|$domain|$detail"
 fun error(code:String,detail:String)="ERROR|$code|$detail"
}

/** argument grammar: "subject | payload" for two-part commands, bare subject for status ones. */
data class SecurityCommand(val verb:String,val subject:String,val argument:String)

fun parseSecurityCommand(argument:String):SecurityCommand{
 val parts=argument.split("|",limit=2).map{it.trim()}
 val subject=parts[0].lowercase()
 val payload=parts.getOrNull(1)?:""
 return when(subject){
  "device_status","vpn_status","notification_access","call_screening_status"->SecurityCommand("query",subject,"")
  "notifications"->SecurityCommand("query","notifications",payload.also{require(it.isNotBlank()){"Use notifications | all or notifications | package.name"}})
  "app"->SecurityCommand("query","app",payload.also{require(it.isNotBlank()){"Name the app package, e.g. app | com.example"} })
  "apps"->SecurityCommand("query","apps",payload.also{require(it=="suspicious"){"Supported: apps | suspicious"}})
  "url"->SecurityCommand("scan","url",payload.also{require(it.isNotBlank()){"Provide the link, e.g. url | https://example.com"}})
  "text"->SecurityCommand("scan","text",payload.also{require(it.isNotBlank()){"Provide the message text after text |"}})
  "installed_apps"->SecurityCommand("scan","installed_apps","")
  "open_app_settings","uninstall_handoff","dismiss_notification"->SecurityCommand("action",subject,payload.also{require(it.isNotBlank()){"Name the target: a package or notification id"}})
  else->throw IllegalArgumentException("Unknown security command '$subject'. Supported: device_status, app | pkg, apps | suspicious, vpn_status, notification_access, call_screening_status, url | link, text | message, installed_apps, open_app_settings | pkg, uninstall_handoff | pkg")
 }
}

fun findingEnvelope(domain:String,finding:SecurityFinding):String=
 if(finding.verdict==SecurityVerdict.UNKNOWN)ToolResultEnvelope.error("no_data",finding.render())
 else ToolResultEnvelope.ok(domain,finding.render())
