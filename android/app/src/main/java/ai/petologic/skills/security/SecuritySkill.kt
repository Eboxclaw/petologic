package ai.petologic.skills.security

import ai.petologic.paladino.skills.SkillDefinition
import ai.petologic.paladino.skills.SkillState
import ai.petologic.paladino.skills.SkillToolSpec

/** Plan 13 Sprint B: the whole pack is three model-facing tools over deterministic local scanners. */
object SecuritySkill {
 const val ID="security_guard"
 val definition=SkillDefinition(
  id=ID,
  name="Security Guard",
  description="Spam, phishing, app and device security checks.",
  routerTerms=listOf("spam","scam","phishing","phish","malware","virus","suspicious","suspicious link","suspicious app","suspicious message","phone security","call blocking","is this app safe","safe link","seems fishy"),
  promptStub="SECURITY GUARD\n"+
   "Use security_query for inspection.\n"+
   "Use security_scan for analysis.\n"+
   "Use security_action only for approved actions.\n"+
   "Never claim malware is confirmed from heuristics alone.\n"+
   "Never claim an external application action succeeded unless Android provides confirmation.\n"+
   "Verdict meanings: LOW no signals, REVIEW higher-risk capabilities, HIGH_ATTENTION several signals, UNKNOWN nothing could be read.",
  tools=listOf(
   SkillToolSpec("security_query","Security status"),
   SkillToolSpec("security_scan","Scan suspicious text/link"),
   SkillToolSpec("security_action","Approved security actions"),
   SkillToolSpec("notification_query","Notification monitoring",
    androidPermission="cap.notification_listener",defaultEnabled=false)
  ),
  defaultState=SkillState.AUTO
 )
}
