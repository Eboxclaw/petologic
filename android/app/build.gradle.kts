plugins {
 id("com.android.application"); kotlin("android"); kotlin("plugin.compose")
 kotlin("plugin.serialization"); id("com.google.devtools.ksp")
}
android {
 namespace = "ai.petologic.paladino"
 compileSdk = 36
 val releaseKeystore=providers.environmentVariable("PALADINO_KEYSTORE").orNull?.let(::file)?.takeIf{it.isFile}
 val releaseAlias=providers.environmentVariable("PALADINO_KEY_ALIAS").orNull
 val releaseStorePass=providers.environmentVariable("PALADINO_STOREPASS_FILE").orNull?.let(::file)?.takeIf{it.isFile}?.readText()?.trim()
 val releaseKeyPass=providers.environmentVariable("PALADINO_KEYPASS_FILE").orNull?.let(::file)?.takeIf{it.isFile}?.readText()?.trim()?:releaseStorePass
 val productionSigning=if(releaseKeystore!=null&&!releaseAlias.isNullOrBlank()&&!releaseStorePass.isNullOrBlank()&&!releaseKeyPass.isNullOrBlank())
  signingConfigs.create("production") { storeFile=releaseKeystore;keyAlias=releaseAlias;storePassword=releaseStorePass;keyPassword=releaseKeyPass }
 else null
 defaultConfig {
  applicationId = "ai.petologic.paladino"
  minSdk = 31; targetSdk = 36; versionCode = 13; versionName = "0.0.1"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  ndk { abiFilters += (providers.gradleProperty("paladinoAbi").orNull?.let { listOf(it) } ?: listOf("arm64-v8a", "x86_64")) }
  externalNativeBuild { cmake { cppFlags += "-std=c++17"; arguments += listOf("-DANDROID_STL=c++_shared") } }
  val threatManifestUrl=providers.gradleProperty("threatManifestUrl").orNull.orEmpty().replace("\\","\\\\").replace("\"","\\\"")
  val reputationUrl=providers.gradleProperty("reputationUrl").orNull.orEmpty().replace("\\","\\\\").replace("\"","\\\"")
  buildConfigField("String","THREAT_MANIFEST_URL","\"$threatManifestUrl\"")
  buildConfigField("String","REPUTATION_URL","\"$reputationUrl\"")
  buildConfigField("boolean","SELF_UPDATE_ENABLED","true")
  buildConfigField("String","DISTRIBUTION_CHANNEL","\"development\"")
 }
 ndkVersion = "28.2.13676358"
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/*.md") }
 buildTypes {
  debug {
   buildConfigField("boolean","SELF_UPDATE_ENABLED","true")
   buildConfigField("String","DISTRIBUTION_CHANNEL","\"development\"")
  }
  release {
   isMinifyEnabled = true
   isShrinkResources = true
   buildConfigField("boolean","SELF_UPDATE_ENABLED","true")
   buildConfigField("String","DISTRIBUTION_CHANNEL","\"direct\"")
   signingConfig=productionSigning
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
  }
  create("playRelease") {
   initWith(getByName("release"))
   matchingFallbacks += listOf("release")
   buildConfigField("boolean","SELF_UPDATE_ENABLED","false")
   buildConfigField("String","DISTRIBUTION_CHANNEL","\"play\"")
  }
 }
 testOptions { unitTests.isReturnDefaultValues = true }
 sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
 sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("security-data"))
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
 implementation(project(":core"))
 implementation(project(":security-core"))
 implementation(project(":security-scanner"))
 implementation("androidx.documentfile:documentfile:1.1.0")
 implementation(platform("androidx.compose:compose-bom:2025.12.01"))
 implementation("androidx.activity:activity-compose:1.12.2")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
 implementation("androidx.work:work-runtime-ktx:2.11.0")
 implementation("androidx.room:room-runtime:2.8.4")
 implementation("androidx.room:room-ktx:2.8.4")
 ksp("androidx.room:room-compiler:2.8.4")
 implementation("androidx.appsearch:appsearch:1.1.0")
 implementation("androidx.appsearch:appsearch-local-storage:1.1.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
 implementation("ai.koog:koog-agents:1.2.0")
 implementation("com.squareup.okhttp3:okhttp:5.3.2")
 implementation("io.noties.markwon:core:4.6.2")
 implementation("io.noties.markwon:ext-strikethrough:4.6.2")
 implementation("io.noties.markwon:ext-tables:4.6.2")
 implementation("io.noties.markwon:ext-tasklist:4.6.2")
 testImplementation(kotlin("test"))
 testImplementation("junit:junit:4.13.2")
 testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
 testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
 androidTestImplementation("androidx.room:room-testing:2.8.4")
 androidTestImplementation(project(":security-benchmark"))
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
