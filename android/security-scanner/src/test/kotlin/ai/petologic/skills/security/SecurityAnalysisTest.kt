package ai.petologic.skills.security

import ai.petologic.skills.security.analysis.AppRiskEngine
import ai.petologic.skills.security.analysis.AppSignals
import ai.petologic.skills.security.analysis.MessageAnalyzer
import ai.petologic.skills.security.analysis.UrlAnalyzer
import ai.petologic.skills.security.models.SecurityVerdict
import kotlin.test.*

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
 @Test fun unparseable_link_never_scores_low(){
  assertNotEquals(SecurityVerdict.LOW,UrlAnalyzer.scanUrl("not a link at all").verdict)
 }
 @Test fun unicode_domains_are_canonicalized_without_flagging_all_international_names(){
  assertEquals(SecurityVerdict.LOW,UrlAnalyzer.scanUrl("https://café.example/path").verdict)
  assertEquals(SecurityVerdict.LOW,UrlAnalyzer.scanUrl("https://例え.テスト/path").verdict)
  val mixed=UrlAnalyzer.scanUrl("https://раypal.com/login")
  assertEquals(SecurityVerdict.HIGH_ATTENTION,mixed.verdict)
  assertTrue(mixed.reasons.any{it.contains("writing systems")})
  assertTrue(mixed.reasons.any{it.contains("well-known brand")})
 }
 @Test fun ambiguous_and_non_web_addresses_never_score_low(){
  assertNotEquals(SecurityVerdict.LOW,UrlAnalyzer.scanUrl("javascript:alert(1)").verdict)
  assertNotEquals(SecurityVerdict.LOW,UrlAnalyzer.scanUrl("https://example.com\\@evil.test/login").verdict)
  assertNotEquals(SecurityVerdict.LOW,UrlAnalyzer.scanUrl("www.example.com/login").verdict)
  assertTrue(UrlAnalyzer.scanUrl("https://example.com./login").reasons.any{it.contains("trailing dot")})
  assertTrue(UrlAnalyzer.scanUrl("https://2130706433/login").reasons.any{it.contains("numeric host")})
  assertTrue(UrlAnalyzer.scanUrl("https://[::1]/login").reasons.any{it.contains("IPv6")})
  assertTrue(UrlAnalyzer.scanUrl("https://example.com:/login").reasons.any{it.contains("invalid network port")})
  assertTrue(UrlAnalyzer.scanUrl("https://example.com\u202E.evil/login").reasons.any{it.contains("direction characters")})
 }
 @Test fun encoded_executable_and_message_punctuation_are_handled(){
  val encoded=UrlAnalyzer.scanUrl("https://example.com/invoice.pdf%2Eexe")
  assertEquals("Link",encoded.subject)
  assertTrue(encoded.reasons.any{it.contains("executable")})
  assertEquals(SecurityVerdict.LOW,MessageAnalyzer.scanMessage("Read https://example.com/docs.").verdict)
 }
 @Test fun scam_message_combines_pressure_links_and_codes(){
  val finding=MessageAnalyzer.scanMessage("URGENT: your account will be suspended within 24 hours. Verify your account: http://193.42.11.7/login")
  assertEquals(SecurityVerdict.HIGH_ATTENTION,finding.verdict)
  assertTrue(finding.reasons.any{it.contains("Pressure")})
  assertTrue(finding.reasons.any{it.contains("Link check")})
 }
 @Test fun everyLinkIsInspectedAndBenignLinksAreNotSpam(){
  assertEquals(SecurityVerdict.LOW,MessageAnalyzer.scanMessage("Read https://example.com/docs").verdict)
  assertTrue(MessageAnalyzer.scanMessage("https://example.com and http://193.42.11.7/login").reasons.any{it.contains("IP address")})
 }
 @Test fun portuguesePatternsAndUtf8Limits(){
  assertEquals(SecurityVerdict.HIGH_ATTENTION,MessageAnalyzer.scanMessage("URGENTE: conta suspensa. Verifique a sua conta e dados bancários em http://193.42.11.7").verdict)
  assertTrue(MessageAnalyzer.scanMessage("á".repeat(5000)).reasons.any{it.contains("truncated")})
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
