plugins { kotlin("jvm"); kotlin("plugin.serialization"); application }

kotlin { jvmToolchain(17) }

dependencies {
 implementation(project(":security-core"))
 implementation(project(":security-scanner"))
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
 testImplementation(kotlin("test"))
}

application { mainClass.set("ai.petologic.security.benchmark.MainKt") }
tasks.test {
 useJUnitPlatform()
 systemProperty("paladino.benchmark.smoke",rootProject.file("security-data/benchmark-smoke.json").absolutePath)
 systemProperty("paladino.benchmark.smokeHash",rootProject.file("security-data/benchmark-smoke.sha256").absolutePath)
 systemProperty("paladino.benchmark.registry",rootProject.file("security-data/source-registry.json").absolutePath)
}

tasks.register<JavaExec>("benchmarkSmoke") {
 group="verification"
 description="Runs the inert Paladino security benchmark smoke corpus."
 classpath=sourceSets.main.get().runtimeClasspath
 mainClass.set(application.mainClass)
 args("--corpus",rootProject.file("security-data/benchmark-smoke.json").absolutePath,
  "--source-registry",rootProject.file("security-data/source-registry.json").absolutePath,
  "--output-dir",layout.buildDirectory.dir("reports/security-benchmark").get().asFile.absolutePath,
  "--mode","smoke")
}

tasks.register<JavaExec>("securityBenchmark") {
 group="verification"
 description="Runs a validated external inert benchmark corpus. Use -PbenchmarkCorpus=/path or '-' for stdin."
 classpath=sourceSets.main.get().runtimeClasspath
 mainClass.set(application.mainClass)
 standardInput=System.`in`
 val corpus=providers.gradleProperty("benchmarkCorpus").orNull
  ?:throw GradleException("Set -PbenchmarkCorpus=/path/to/safe-corpus.json or - for stdin.")
 val output=providers.gradleProperty("benchmarkOutput").orNull
  ?:layout.buildDirectory.dir("reports/security-benchmark-full").get().asFile.absolutePath
 args("--corpus",corpus,"--source-registry",rootProject.file("security-data/source-registry.json").absolutePath,
  "--output-dir",output,"--mode","release",
  "--git-revision",providers.gradleProperty("benchmarkGitRevision").orNull?:System.getenv("GITHUB_SHA")?:"unknown",
  "--model-hash",providers.gradleProperty("benchmarkModelHash").orNull?:"not-used")
}
