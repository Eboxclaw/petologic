package ai.petologic.skills.security.guard

import ai.petologic.security.Coverage
import ai.petologic.security.Evidence
import ai.petologic.security.Finding
import ai.petologic.security.MatchType
import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityPresentationTest {
 @Test fun protectionStateReflectsReadinessAndAttention(){
  val limited=SecurityPresentation.summary(ProtectionInputs(false,false,false,false,false))
  assertEquals(ProtectionStateUi.OFF,limited.state)
  val ready=SecurityPresentation.summary(ProtectionInputs(true,true,true,true,true,true,true))
  assertEquals(ProtectionStateUi.READY,ready.state)
  val exact=Finding(kind="file",coverage=Coverage.COMPLETE,limitations=emptyList(),checks=listOf("hash"),evidence=listOf(evidence(MatchType.EXACT_SHA256)))
  val attention=SecurityPresentation.summary(ProtectionInputs(true,true,true,true,true,true,true,exact))
  assertEquals(ProtectionStateUi.NEEDS_ATTENTION,attention.state)
  val installed=SecurityPresentation.summary(ProtectionInputs(validThreatPack=true,notificationAccess=false,vectorMemoryReady=false,chatReady=false,visionReady=false,latestFinding=null,installedAppProtection=true,reputationConfigured=true))
  assertEquals(CapabilityStateUi.READY,installed.capabilities.first{it.title=="Installed apps"}.state)
 }

 @Test fun findingLabelsKeepEvidenceAndCoverageAuthority(){
  val low=Finding(kind="text",coverage=Coverage.COMPLETE,limitations=listOf("bounded"),checks=listOf("patterns"))
  assertEquals(FindingLevelUi.NO_KNOWN_ISSUE,low.toUi().level)
  val partial=low.copy(coverage=Coverage.PARTIAL)
  assertEquals(FindingLevelUi.INCOMPLETE,partial.toUi().level)
  val heuristic=low.copy(evidence=listOf(evidence(MatchType.HEURISTIC)))
  assertEquals(FindingLevelUi.REVIEW,heuristic.toUi().level)
  val rule=low.copy(evidence=listOf(evidence(MatchType.RULE)))
  assertEquals(FindingLevelUi.HIGH_ATTENTION,rule.toUi().level)
 }

 private fun evidence(type:MatchType)=Evidence("test","1","id","safe fixture","first-party",type,"Synthetic detector signal")
}
