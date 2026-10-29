package ai.petologic.security

/** Exact inert-hash matching used by both streaming scans and the safe benchmark. */
object HashIndicatorMatcher {
 fun match(candidateSha256:String,indicators:Collection<HashIndicator>):HashIndicator? {
  require(Regex("[0-9a-f]{64}").matches(candidateSha256)){"Candidate SHA-256 must be lowercase hexadecimal."}
  return indicators.firstOrNull{it.sha256==candidateSha256}
 }
}
