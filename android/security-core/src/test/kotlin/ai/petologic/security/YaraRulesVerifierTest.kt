package ai.petologic.security

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class YaraRulesVerifierTest {
 private val now=1_800_000_000_000L
 private val source=YaraRuleSource("first-party-scan-rules","first-party","rule paladino_signed_probe { condition: true }")
 private fun pack(version:Long=7,issuedAt:Long=now-1,expiresAt:Long=now+60_000,sources:List<YaraRuleSource>)=
  YaraRulesPack(version=version,issuedAt=issuedAt,expiresAt=expiresAt,sources=sources)

 @Test fun validPackVerifiesAndBindsToManifestVersion(){
  val bytes=Json.encodeToString(pack(version=7,sources=listOf(source))).encodeToByteArray()
  val verified=YaraRulesVerifier.verify(bytes,7,now)
  assertEquals(1,verified.sources.size)
  assertEquals("first-party-scan-rules",verified.sources.single().id)
 }
 @Test fun versionAndExpiryAreEnforced(){
  val good=Json.encodeToString(pack(sources=listOf(source))).encodeToByteArray()
  assertFailsWith<IllegalArgumentException>{YaraRulesVerifier.verify(good,8,now)}
  assertFailsWith<IllegalArgumentException>{YaraRulesVerifier.verify(Json.encodeToString(pack(expiresAt=now-1,sources=listOf(source))).encodeToByteArray(),7,now)}
  assertFailsWith<IllegalArgumentException>{YaraRulesVerifier.verify(Json.encodeToString(pack(issuedAt=now+1,sources=listOf(source))).encodeToByteArray(),7,now)}
 }
 @Test fun emptyDuplicateAndGarbageSourcesAreRejected(){
  assertFailsWith<IllegalArgumentException>{YaraRulesVerifier.verify(Json.encodeToString(pack(sources=emptyList())).encodeToByteArray(),7,now)}
  val duplicate=YaraRuleSource("dup","first-party","rule a { condition: true }")
  assertFailsWith<IllegalArgumentException>{YaraRulesVerifier.verify(Json.encodeToString(pack(sources=listOf(duplicate,duplicate))).encodeToByteArray(),7,now)}
  assertFailsWith<IllegalArgumentException>{YaraRulesVerifier.verify(Json.encodeToString(pack(sources=listOf(YaraRuleSource("bad","first-party","not a rule")))).encodeToByteArray(),7,now)}
 }
 @Test fun oversizedSourcesAreRejected(){
  val huge=YaraRuleSource("huge","first-party","rule big { condition: "+"true and ".repeat(120_000)+"true }")
  assertFailsWith<IllegalArgumentException>{YaraRulesVerifier.verify(Json.encodeToString(pack(sources=listOf(huge))).encodeToByteArray(),7,now)}
 }
}
