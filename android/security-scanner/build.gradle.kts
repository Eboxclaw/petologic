plugins { kotlin("jvm"); kotlin("plugin.serialization") }
kotlin { jvmToolchain(17) }
dependencies {
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
 testImplementation(kotlin("test"))
 implementation(project(":security-core"))
}
tasks.test { useJUnitPlatform() }
