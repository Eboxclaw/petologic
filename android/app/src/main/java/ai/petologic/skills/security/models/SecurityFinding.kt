package ai.petologic.skills.security.models

/** Deterministic scale. Heuristics never claim malware — HIGH_ATTENTION still means "look at this". */
enum class SecurityVerdict{ LOW, REVIEW, HIGH_ATTENTION, UNKNOWN }

data class SecurityFinding(val subject:String, val verdict:SecurityVerdict, val reasons:List<String>){
 fun render():String=buildString{
  append(subject);append(" verdict: ");append(verdict.name)
  if(reasons.isNotEmpty()){append(" — ");reasons.forEachIndexed{i,r->if(i>0)append("; ");append(r)}}
 }
}
