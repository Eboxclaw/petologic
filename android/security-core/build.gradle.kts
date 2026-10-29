plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(17) }
dependencies {
 implementation("org.bouncycastle:bcprov-jdk18on:1.84")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
 testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
