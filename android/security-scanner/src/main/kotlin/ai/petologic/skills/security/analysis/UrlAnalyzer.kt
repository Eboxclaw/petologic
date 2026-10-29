package ai.petologic.skills.security.analysis

import ai.petologic.skills.security.models.SecurityFinding
import ai.petologic.skills.security.models.SecurityVerdict
import java.net.IDN
import java.text.Normalizer

/** Structural, fully local link checks. No DNS lookup or network request is made. */
object UrlAnalyzer {
 private val shorteners=setOf("bit.ly","tinyurl.com","t.co","is.gd","cutt.ly","rb.gy","shorturl.at","tiny.cc")
 private val asciiLookalikes=Regex("(paypa1|g00gle|goog1e|amaz0n|micr0s0ft|faceb00k|app1e|netf1ix|gmai1|gooogle|arnazon)")
 private val brands=setOf("paypal","google","amazon","microsoft","facebook","apple","netflix","gmail")
 private val scheme=Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
 private val ipv4=Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
 private val suspiciousNumericHost=Regex("^(?:0x[0-9a-f]+|[0-9]{7,})$",RegexOption.IGNORE_CASE)
 private val percentByte=Regex("%([0-9a-fA-F]{2})")

 private data class Address(
  val protocol:String,
  val host:String,
  val unicodeHost:String,
  val path:String,
  val userInfo:Boolean,
  val port:String?,
  val omittedScheme:Boolean,
  val trailingDot:Boolean,
  val normalizedCharacters:Boolean
 )

 fun scanUrl(rawUrl:String):SecurityFinding{
  val subject="Link"
  val input=rawUrl.trim()
  if(input.isBlank())return SecurityFinding(subject,SecurityVerdict.UNKNOWN,listOf("This does not look like a link."))
  val reasons=mutableListOf<String>()
  if(input.any{it.isISOControl()}||input.any{Character.getType(it)==Character.FORMAT.toInt()})
   reasons.add("The address contains hidden control or direction characters.")
  if('\\' in input)reasons.add("The address uses a backslash, which link parsers can interpret differently.")

  val address=parse(input)
  if(address==null){
   if(scheme.containsMatchIn(input)&&!input.startsWith("http://",true)&&!input.startsWith("https://",true))
    reasons.add("The address uses a non-web link scheme instead of HTTP or HTTPS.")
   else reasons.add("Could not read the address structure.")
   return SecurityFinding(subject,if(reasons.any{it.contains("control")||it.contains("backslash")})SecurityVerdict.HIGH_ATTENTION else SecurityVerdict.REVIEW,reasons.distinct())
  }

  if(address.protocol=="http")reasons.add("The link does not use HTTPS.")
  if(address.protocol !in setOf("http","https"))reasons.add("The address uses a non-web link scheme instead of HTTP or HTTPS.")
  if(address.omittedScheme)reasons.add("The address omits its protocol, so HTTPS is not established.")
  if(address.userInfo)reasons.add("The link hides its real destination before the @ sign.")
  if(address.trailingDot)reasons.add("The domain has a trailing dot that can bypass exact text comparisons.")
  if(address.normalizedCharacters)reasons.add("The domain uses Unicode characters that normalize to a different address spelling.")
  if(address.port!=null&&(address.port.toIntOrNull() !in 1..65535))reasons.add("The address contains an invalid network port.")

  when{
   validIpv4(address.host)->reasons.add("The link uses a raw IP address instead of a domain name.")
   ipv4.matches(address.host)->reasons.add("The address resembles an invalid IP address.")
   ':' in address.host->reasons.add("The link uses a raw IPv6 address instead of a domain name.")
   suspiciousNumericHost.matches(address.host)->reasons.add("The link uses an encoded or numeric host instead of a domain name.")
  }
  if(address.host in shorteners)reasons.add("The link is a shortener that hides the final destination.")
  if(asciiLookalikes.containsMatchIn(address.host))reasons.add("The address imitates a well-known brand with look-alike characters.")
  if(hasMixedScriptLabel(address.unicodeHost))reasons.add("A domain label mixes writing systems, which can conceal look-alike characters.")
  if(confusableBrand(address.unicodeHost))reasons.add("The internationalized domain visually imitates a well-known brand.")
  if(address.host.count{it=='.'}>=4)reasons.add("The address has an unusual number of subdomains.")

  val decodedPath=percentDecode(address.path).lowercase()
  if(decodedPath.endsWith(".exe")||Regex("\\.(pdf|jpg|docx?|xlsx?)\\.exe$").containsMatchIn(decodedPath))
   reasons.add("The file name hides an executable at the end.")

  val unique=reasons.distinct()
  val verdict=when{
   unique.isEmpty()->SecurityVerdict.LOW
   unique.size==1->SecurityVerdict.REVIEW
   else->SecurityVerdict.HIGH_ATTENTION
  }
  return SecurityFinding(subject,verdict,unique.ifEmpty{listOf("No suspicious signals found in this link's structure.")})
 }

 private fun parse(input:String):Address?{
  val omitted=input.startsWith("www.",true)
  val prepared=if(omitted)"https://$input" else input
  val separator=prepared.indexOf("://")
  if(separator<=0)return null
  val protocol=prepared.substring(0,separator).lowercase()
  if(!protocol.matches(Regex("[a-z][a-z0-9+.-]*")))return null
  val authorityStart=separator+3
  val authorityEnd=prepared.indexOfAny(charArrayOf('/','?','#'),authorityStart).let{if(it<0)prepared.length else it}
  val authority=prepared.substring(authorityStart,authorityEnd)
  if(authority.isBlank()||authority.any{it.isWhitespace()})return null
  val userInfo='@' in authority
  val hostPort=authority.substringAfterLast('@')
  val rawHost:String
  val port:String?
  if(hostPort.startsWith("[")){
   val close=hostPort.indexOf(']');if(close<=1)return null
   rawHost=hostPort.substring(1,close)
   val suffix=hostPort.substring(close+1)
   if(suffix.isNotEmpty()&&!suffix.startsWith(':'))return null
   port=if(suffix.startsWith(':'))suffix.removePrefix(":") else null
  }else{
   val colon=hostPort.lastIndexOf(':')
   if(colon>=0&&hostPort.indexOf(':')==colon){rawHost=hostPort.substring(0,colon);port=hostPort.substring(colon+1)}
   else{rawHost=hostPort;port=null}
  }
  if(rawHost.isBlank()||'%' in rawHost)return null
  val trailing=rawHost.endsWith('.')
  val withoutDot=rawHost.removeSuffix(".")
  val normalized=Normalizer.normalize(withoutDot,Normalizer.Form.NFKC)
  val ascii=if(':' in normalized)normalized.lowercase() else runCatching{normalized.split('.').joinToString("."){label->
   require(label.isNotEmpty());IDN.toASCII(label,IDN.USE_STD3_ASCII_RULES)
  }.lowercase()}.getOrNull()?:return null
  if(ascii.length>253)return null
  val unicode=runCatching{IDN.toUnicode(ascii)}.getOrDefault(normalized).lowercase()
  val path=prepared.substring(authorityEnd).substringBefore('?').substringBefore('#')
  return Address(protocol,ascii,unicode,path,userInfo,port,omitted,trailing,normalized!=withoutDot)
 }

 private fun validIpv4(host:String)=ipv4.matches(host)&&host.split('.').all{it.toIntOrNull() in 0..255}

 private fun hasMixedScriptLabel(host:String)=host.split('.').any{label->
  val scripts=label.codePoints().toArray().asSequence().mapNotNull{cp->
   val script=Character.UnicodeScript.of(cp)
   script.takeUnless{it==Character.UnicodeScript.COMMON||it==Character.UnicodeScript.INHERITED}
  }.toSet()
  val ordinaryJapanese=scripts.all{it in setOf(Character.UnicodeScript.HAN,Character.UnicodeScript.HIRAGANA,Character.UnicodeScript.KATAKANA)}
  val ordinaryKorean=scripts.all{it in setOf(Character.UnicodeScript.HAN,Character.UnicodeScript.HANGUL)}
  scripts.size>1&&!ordinaryJapanese&&!ordinaryKorean
 }

 private fun confusableBrand(host:String)=host.split('.').any{label->
  val skeleton=buildString{label.lowercase().codePoints().forEach{append(confusable(it))}}
  skeleton in brands&&label !in brands
 }

 private fun confusable(cp:Int):String=when(cp){
  0x0430,0x03B1->"a"
  0x0435,0x03B5->"e"
  0x0456,0x03B9->"i"
  0x043E,0x03BF->"o"
  0x0440,0x03C1->"p"
  0x0441,0x03F2->"c"
  0x0445,0x03C7->"x"
  0x0443,0x03C5->"y"
  0x0458->"j"
  0x04CF->"l"
  else->String(Character.toChars(cp))
 }

 private fun percentDecode(value:String)=percentByte.replace(value){match->match.groupValues[1].toInt(16).toChar().toString()}
}
