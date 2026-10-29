# JNI uses exported Java_* names and looks up TokenCallback.onToken by name.
-keep class ai.petologic.paladino.runtime.NativeLfm { *; }
-keep class ai.petologic.paladino.runtime.NativeVectorModel { *; }
-keep class ai.petologic.paladino.runtime.TokenCallback { *; }
-keep class ai.petologic.skills.security.guard.NativeRules { *; }

# Retain native method names in future JNI bridges added outside the classes above.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Ktor probes these desktop-only management APIs behind a runtime check.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
