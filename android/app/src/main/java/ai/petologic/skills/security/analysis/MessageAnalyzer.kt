package ai.petologic.skills.security.analysis

import ai.petologic.skills.security.models.SecurityFinding
import ai.petologic.skills.security.models.SecurityVerdict

/** Local scam-pattern checks for pasted text. Extracts links and folds the link verdict in. */
object MessageAnalyzer {
 private val urlRegex=Regex("(https?://|www\\.)\\S+",RegexOption.IGNORE_CASE)
 private val urgency=Regex("(urgent|immediately|within \\d+ (?:hours|minutes|days)|account will be (?:suspended|closed|locked)|your (?:account|device) (?:has been|is) (?:locked|compromised)|suspended|verify your account|confirm your identity|unusual activity|click here)",RegexOption.IGNORE_CASE)
 private val credentialBait=Regex("(one[- ]time code|\\bOTP\\b|\\bPIN code\\b|\\bCVV\\b|bank details|social security|password (?:is|here|reset))",RegexOption.IGNORE_CASE)
 private val prize=Regex("(you(?: have)? won|prize|lottery|gift card|claim your reward|free iphone)",RegexOption.IGNORE_CASE)
 private val paymentPressure=Regex("(pay (?:a )?(?:small )?fee|wire transfer|western union|send (?:us )?(?:bitcoin|crypto)|investment opportunity|double your)",RegexOption.IGNORE_CASE)

 fun scanMessage(raw:String):SecurityFinding{
  val text=raw.trim()
  val subject="Message (${text.take(60)}${if(text.length>60)"…" else ""})"
  if(text.isEmpty())return SecurityFinding(subject,SecurityVerdict.UNKNOWN,listOf("There is no text to analyze."))
  val reasons=buildList{
   if(urgency.containsMatchIn(text))add("Pressure language urges immediate action.")
   if(credentialBait.containsMatchIn(text))add("The message asks for credentials, codes or banking details.")
   if(prize.containsMatchIn(text))add("The message offers an unsolicited prize or reward.")
   if(paymentPressure.containsMatchIn(text))add("The message pushes an unusual payment or investment.")
   val links=urlRegex.findAll(text).toList()
   if(links.isNotEmpty()){
    add("The message contains ${if(links.size==1)"a link" else "${links.size} links"}.")
    val linkFinding=UrlAnalyzer.scanUrl(links.first().value)
    if(linkFinding.verdict!=SecurityVerdict.LOW)linkFinding.reasons.forEach{add("Link check: $it")}
   }
  }
  val verdict=when{
   reasons.isEmpty()->SecurityVerdict.LOW
   reasons.size>=3->SecurityVerdict.HIGH_ATTENTION
   else->SecurityVerdict.REVIEW
  }
  if(reasons.isEmpty())return SecurityFinding(subject,verdict,listOf("No scam patterns found in this text."))
  return SecurityFinding(subject,verdict,reasons)
 }
}
