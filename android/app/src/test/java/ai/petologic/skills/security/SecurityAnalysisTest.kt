package ai.petologic.skills.security

import ai.petologic.skills.security.analysis.AppRiskEngine
import ai.petologic.skills.security.analysis.AppSignals
import ai.petologic.skills.security.analysis.MessageAnalyzer
import ai.petologic.skills.security.analysis.UrlAnalyzer
import ai.petologic.skills.security.models.SecurityVerdict
import org.junit.Test
import org.junit.Assert.*

class SecurityAnalysisTest {
 @Test fun clean_https_link_scores_low(){
  val finding=UrlAnalyzer.scanUrl("https://example.com/download")
  assertEquals(SecurityVerdict.LOW,finding.verdict)
 }
 @Test fun structural_signals_escalate_verdicts(){
  assertEquals(SecurityVerdict.REVIEW,UrlAnalyzer.scanUrl("http://example.com").verdict)
  val httpIp=UrlAnalyzer.scanUrl("http://193.42.11.7/login.zip")
  assertEquals(SecurityVerdict.HIGH_ATTENTION,httpIp.verdict)
  assertTrue(httpIp.reasons.any{it.contains("IP address")})
  assertTrue(UrlAnalyzer.scanUrl("https://user:pass@bank.example.com").reasons.any{it.contains("@ sign")})
  assertTrue(UrlAnalyzer.scanUrl("https://bit.ly/x1").reasons.any{it.contains("shortener")})
  assertTrue(UrlAnalyzer.scanUrl("https://paypa1.login.example.com").reasons.any{it.contains("imitates")})
 }
 @Test fun unparseable_link_is_unknown_never_low(){
  assertEquals(SecurityVerdict.UNKNOWN,UrlAnalyzer.scanUrl("not a link at all").verdict)
 }
 @Test fun scam_message_combines_pressure_links_and_codes(){
  val finding=MessageAnalyzer.scanMessage("URGENT: your account will be suspended within 24 hours. Verify your account: http://193.42.11.7/login")
  assertEquals(SecurityVerdict.HIGH_ATTENTION,finding.verdict)
  assertTrue(finding.reasons.any{it.contains("Pressure")})
  assertTrue(finding.reasons.any{it.contains("Link check")})
 }
 @Test fun ordinary_message_stays_low(){
  assertEquals(SecurityVerdict.LOW,MessageAnalyzer.scanMessage("Lunch tomorrow at the usual place?").verdict)
 }
 @Test fun app_risk_matches_plan_examples(){
  val powerfulOrigin=AppSignals(installer="com.android.vending",requestedPermissions=listOf(),
   grantedDangerous=listOf("x","y"),overlay=true,accessibility=true,deviceAdmin=false,vpnService=false,notificationListener=false)
  // Accessibility alone with store origin stays REVIEW (plan 13's example), never malware.
  assertEquals(SecurityVerdict.REVIEW,AppRiskEngine.assess("App com.a",powerfulOrigin).verdict)
  val originRisk=powerfulOrigin.copy(installer="com.unknown.sideload",grantedDangerous=listOf("x","y","z"))
  assertEquals(SecurityVerdict.HIGH_ATTENTION,AppRiskEngine.assess("App com.b",originRisk).verdict)
  val clean=AppSignals(installer="com.android.vending",requestedPermissions=listOf(),grantedDangerous=listOf(),
   overlay=false,accessibility=false,deviceAdmin=false,vpnService=false,notificationListener=false)
  assertEquals(SecurityVerdict.LOW,AppRiskEngine.assess("App com.c",clean).verdict)
  val invisible=AppSignals(installer=null,requestedPermissions=listOf(),grantedDangerous=listOf(),
   overlay=false,accessibility=false,deviceAdmin=false,vpnService=false,notificationListener=false)
  assertEquals(SecurityVerdict.UNKNOWN,AppRiskEngine.assess("App com.d",invisible).verdict)
 }
}
