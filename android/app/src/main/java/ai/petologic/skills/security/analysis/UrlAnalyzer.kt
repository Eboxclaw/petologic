package ai.petologic.skills.security.analysis

import ai.petologic.skills.security.models.SecurityFinding
import ai.petologic.skills.security.models.SecurityVerdict

/** Structural, fully local link checks. No network request is made; verdicts name what was observed. */
object UrlAnalyzer {
 private val shorteners=setOf("bit.ly","tinyurl.com","t.co","is.gd","cutt.ly","rb.gy","shorturl.at","tiny.cc")
 private val lookalikes=Regex("(paypa1|g00gle|goog1e|amaz0n|micr0s0ft|faceb00k|app1e|netf1ix|gmai1|gooogle|arnazon)")
 private val ipHost=Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")

 fun scanUrl(rawUrl:String):SecurityFinding{
  val subject="Link ${rawUrl.take(80)}"
  val url=rawUrl.trim()
  if(url.isBlank()||url.contains(' ')&&!url.startsWith("http"))return SecurityFinding(subject,SecurityVerdict.UNKNOWN,listOf("This does not look like a link."))
  val uri=runCatching{java.net.URI(url)}.getOrNull()
  val host=uri?.host?.lowercase()
  if(uri==null||host.isNullOrBlank())return SecurityFinding(subject,SecurityVerdict.UNKNOWN,listOf("Could not read the address structure."))
  val reasons=buildList{
   if(uri.scheme?.lowercase()=="http")add("The link does not use HTTPS.")
   if(ipHost.matches(host))add("The link uses a raw IP address instead of a domain name.")
   if(!uri.userInfo.isNullOrBlank())add("The link hides its real destination before the @ sign.")
   if(host in shorteners)add("The link is a shortener that hides the final destination.")
   if(lookalikes.containsMatchIn(host))add("The address imitates a well-known brand with look-alike characters.")
   if(host.count{it=='.'}>=4)add("The address has an unusual number of subdomains.")
   val path=uri.rawPath?.lowercase()?:""
   if(path.endsWith(".exe")||Regex("\\.(pdf|jpg|docx?|xlsx?)\\.exe$").containsMatchIn(path))add("The file name hides an executable at the end.")
  }
  val verdict=when{
   reasons.isEmpty()->SecurityVerdict.LOW
   reasons.size==1->SecurityVerdict.REVIEW
   else->SecurityVerdict.HIGH_ATTENTION
  }
  if(reasons.isEmpty())return SecurityFinding(subject,verdict,listOf("No suspicious signals found in this link's structure."))
  return SecurityFinding(subject,verdict,reasons)
 }
}
