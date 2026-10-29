package ai.petologic.skills.security.guard

import ai.petologic.security.*

/** Only loaded inside the isolated scanner. This first rule proves wiring, not malware coverage. */
class NativeRules:RuleEngine {
 init{System.loadLibrary("paladino_security")}
 private external fun scanNative(source:String,bytes:ByteArray):String
 override fun scan(bytes:ByteArray):List<Evidence>{
  val result=scanNative("rule petologic_self_test { strings: \$a = \"PETOLOGIC_SECURITY_SELF_TEST\" condition: \$a }",bytes)
  check(!result.startsWith("ERROR:")){"Native rule scan failed."}
  return if(result.lineSequence().any{it=="petologic_self_test"})listOf(Evidence("yara","4.5.8","petologic_self_test","Petologic","first-party",MatchType.SELF_TEST,"Harmless pipeline test marker matched; this is not malware.")) else emptyList()
 }
}
