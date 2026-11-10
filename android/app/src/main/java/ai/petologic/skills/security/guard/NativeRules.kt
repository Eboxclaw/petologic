package ai.petologic.skills.security.guard

import ai.petologic.security.*

/**
 * Only loaded inside the isolated scanner. The self-test rule proves wiring; the optional signed
 * rule sources arrive through the threat manifest channel and carry their own license metadata.
 */
class NativeRules(private val extraSources:List<YaraRuleSource> = emptyList()):RuleEngine {
 init{System.loadLibrary("paladino_security")}
 private external fun scanNative(source:String,bytes:ByteArray):String
 private val selfTest="rule petologic_self_test { strings: \$a = \"PETOLOGIC_SECURITY_SELF_TEST\" condition: \$a }"
 override fun scan(bytes:ByteArray):List<Evidence>{
  val findings=mutableListOf<Evidence>()
  val selfTestResult=scanNative(selfTest,bytes)
  check(!selfTestResult.startsWith("ERROR:")){"Native rule scan failed."}
  if(selfTestResult.lineSequence().any{it=="petologic_self_test"})findings+=Evidence("yara","4.5.8","petologic_self_test","Petologic","first-party",MatchType.SELF_TEST,"Harmless pipeline test marker matched; this is not malware.")
  extraSources.forEach{source->
   val result=runCatching{scanNative(source.text,bytes)}.getOrNull()?:return@forEach
   if(result.startsWith("ERROR:"))return@forEach
   result.lineSequence().filter{it.isNotBlank()}.forEach{identifier->
    if(identifier!="petologic_self_test")findings+=Evidence("yara","4.5.8",identifier.take(160),source.id,source.license,MatchType.RULE,"Signed rule pack match from ${source.id}.")
   }
  }
  return findings
 }
}
