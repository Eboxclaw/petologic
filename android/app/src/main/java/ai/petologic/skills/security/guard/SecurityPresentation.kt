package ai.petologic.skills.security.guard

import ai.petologic.security.Coverage
import ai.petologic.security.Finding
import ai.petologic.security.MatchType

enum class SecurityDestination { PROTECTION, CHECK, ACTIVITY, SETUP }
enum class ProtectionStateUi { READY, LIMITED, NEEDS_ATTENTION, OFF }
enum class CapabilityStateUi { READY, NEEDS_SETUP, LIMITED, NOT_AVAILABLE_YET }
enum class FindingLevelUi { NO_KNOWN_ISSUE, REVIEW, HIGH_ATTENTION, INCOMPLETE }

data class CapabilityStatusUi(
 val title:String,
 val state:CapabilityStateUi,
 val detail:String,
 val action:String?=null
)

data class ProtectionSummaryUi(
 val state:ProtectionStateUi,
 val reason:String,
 val lastCheckedAt:Long?,
 val capabilities:List<CapabilityStatusUi>
)

data class FindingUi(
 val finding:Finding,
 val level:FindingLevelUi,
 val title:String,
 val whatWasChecked:String,
 val whatWasFound:List<String>,
 val skippedOrUnavailable:List<String>,
 val nextStep:String
)

data class ProtectionInputs(
 val validThreatPack:Boolean,
 val notificationAccess:Boolean,
 val vectorMemoryReady:Boolean,
 val chatReady:Boolean,
 val visionReady:Boolean,
 val broadRuleCoverage:Boolean=false,
 val automaticUpdatesConfigured:Boolean=false,
 val latestFinding:Finding?=null,
 val installedAppProtection:Boolean=false,
 val reputationConfigured:Boolean=false
)

object SecurityPresentation {
 fun summary(input:ProtectionInputs):ProtectionSummaryUi {
  val latest=input.latestFinding?.toUi()
  val state=when{
   latest?.level==FindingLevelUi.HIGH_ATTENTION->ProtectionStateUi.NEEDS_ATTENTION
   !input.validThreatPack&&!input.notificationAccess&&!input.chatReady&&!input.vectorMemoryReady->ProtectionStateUi.OFF
   input.validThreatPack&&input.notificationAccess&&input.vectorMemoryReady&&input.chatReady&&input.broadRuleCoverage&&input.automaticUpdatesConfigured->ProtectionStateUi.READY
   else->ProtectionStateUi.LIMITED
  }
  val reason=when(state){
   ProtectionStateUi.NEEDS_ATTENTION->"A recent finding needs your attention."
   ProtectionStateUi.OFF->"Manual text checks are available, but protection features still need setup."
   ProtectionStateUi.READY->"Configured protection features are ready."
   ProtectionStateUi.LIMITED->"Manual checks are available. Some protection features still need setup."
  }
  val capabilities=listOf(
   CapabilityStatusUi("Messages and links",CapabilityStateUi.READY,"Local pattern checks are ready."),
   CapabilityStatusUi("Files and APKs",if(input.validThreatPack)CapabilityStateUi.LIMITED else CapabilityStateUi.NEEDS_SETUP,if(input.validThreatPack)"Signed indicators are ready; broad malware-family rules are not installed." else "Add a valid signed threat pack for exact indicator matching.",if(input.validThreatPack)null else "Open protection setup"),
   CapabilityStatusUi("Installed apps",when{!input.installedAppProtection->CapabilityStateUi.NEEDS_SETUP;input.validThreatPack&&input.reputationConfigured->CapabilityStateUi.READY;else->CapabilityStateUi.LIMITED},when{!input.installedAppProtection->"Installed-app checks are not scheduled.";input.validThreatPack&&input.reputationConfigured->"New and updated apps are checked with local detectors and private hash-prefix reputation.";else->"New and updated apps are checked locally; threat data or private reputation still needs setup."},"Open protection setup"),
   CapabilityStatusUi("Notification observation",if(input.notificationAccess)CapabilityStateUi.READY else CapabilityStateUi.NEEDS_SETUP,if(input.notificationAccess)"Only the apps you choose are observed." else "Android notification access is off.",if(input.notificationAccess)null else "Manage notification access"),
   CapabilityStatusUi("Reviewed knowledge",if(input.vectorMemoryReady)CapabilityStateUi.READY else CapabilityStateUi.NEEDS_SETUP,if(input.vectorMemoryReady)"Private vector retrieval is ready." else "Install vector memory to retrieve related reviewed knowledge.",if(input.vectorMemoryReady)null else "Set up vector memory"),
   CapabilityStatusUi("Security chat",if(input.chatReady)CapabilityStateUi.READY else CapabilityStateUi.NEEDS_SETUP,if(input.chatReady)"Local explanations are ready." else "Install LFM2.5-VL-450M for local explanations.",if(input.chatReady)null else "Set up local chat"),
   CapabilityStatusUi("Visual checks",if(input.visionReady)CapabilityStateUi.READY else CapabilityStateUi.NEEDS_SETUP,if(input.visionReady)"Selected images can be inspected locally." else "Install the optional vision pack to inspect selected images.",if(input.visionReady)null else "Set up optional vision"),
   CapabilityStatusUi("Call and VPN protection",CapabilityStateUi.NOT_AVAILABLE_YET,"Call screening and VPN protection are not available yet.")
  )
  return ProtectionSummaryUi(state,reason,input.latestFinding?.createdAt,capabilities)
 }
}

fun Finding.toUi():FindingUi {
 val level=when{
  coverage==Coverage.UNAVAILABLE->FindingLevelUi.INCOMPLETE
  evidence.any{it.matchType==MatchType.EXACT_SHA256||it.matchType==MatchType.RULE}->FindingLevelUi.HIGH_ATTENTION
  evidence.isNotEmpty()->FindingLevelUi.REVIEW
  coverage==Coverage.PARTIAL->FindingLevelUi.INCOMPLETE
  else->FindingLevelUi.NO_KNOWN_ISSUE
 }
 val title=when(level){
  FindingLevelUi.NO_KNOWN_ISSUE->"No known issue found"
  FindingLevelUi.REVIEW->"Review recommended"
  FindingLevelUi.HIGH_ATTENTION->"High attention"
  FindingLevelUi.INCOMPLETE->"Check incomplete"
 }
 val found=evidence.map{it.detail}.distinct().ifEmpty{listOf("No suspicious signal was found by the checks that completed.")}
 val skipped=if(coverage==Coverage.COMPLETE)limitations else listOf(when(coverage){Coverage.PARTIAL->"Some checks were skipped or reached a limit.";Coverage.UNAVAILABLE->"The requested checks were unavailable.";else->""})+limitations
 val next=when(level){
  FindingLevelUi.HIGH_ATTENTION->"Do not open or install it until you have reviewed the source and evidence."
  FindingLevelUi.REVIEW->"Verify the sender, source and requested action before continuing."
  FindingLevelUi.INCOMPLETE->"Review the missing checks or setup, then check the item again."
  FindingLevelUi.NO_KNOWN_ISSUE->"Stay cautious: this result covers only the checks listed here."
 }
 return FindingUi(this,level,title,checks.joinToString().ifBlank{"Nothing completed"},found,skipped.filter{it.isNotBlank()}.distinct(),next)
}
