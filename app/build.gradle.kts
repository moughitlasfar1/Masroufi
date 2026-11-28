import com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.Duration

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services) apply false
}

// Phase 7C: Apply Google Services ONLY when Play flavor tasks are requested.
// Pure FOSS / F-Droid builds never load the plugin or require google-services.json.
val requestedTasksSummary = gradle.startParameter.taskNames.joinToString(" ")
val isPureFossBuild = requestedTasksSummary.contains("foss", ignoreCase = true) &&
  !requestedTasksSummary.contains("play", ignoreCase = true)

if (!isPureFossBuild) {
  apply(plugin = "com.google.gms.google-services")
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.masroufi.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 8
    versionName = "1.5.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  flavorDimensions += "distribution"
  productFlavors {
    create("play") {
      dimension = "distribution"
      isDefault = true
      buildConfigField("Boolean", "IS_FOSS", "false")
      buildConfigField("String", "DISTRIBUTION_FLAVOR", "\"play\"")
    }
    create("foss") {
      dimension = "distribution"
      buildConfigField("Boolean", "IS_FOSS", "true")
      buildConfigField("String", "DISTRIBUTION_FLAVOR", "\"foss\"")
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  sourceSets {
    maybeCreate("playDebug")
    getByName("debug").assets.srcDir("$projectDir/schemas")
    getByName("test").assets.srcDir("$projectDir/schemas")
    getByName("test").resources.srcDir("$projectDir/schemas")
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  packaging {
    resources {
      excludes += setOf(
        "META-INF/INDEX.LIST",
        "META-INF/io.netty.versions.properties"
      )
    }
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

extensions.findByType(GoogleServicesPluginConfig::class.java)?.apply {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

val googleServicesText =
  providers.fileContents(layout.projectDirectory.file("google-services.json")).asText.orNull
val hasMatchingGoogleServicesClient =
  googleServicesText?.contains("\"com.masroufi.app\"") == true

// Always disable Google Services tasks for FOSS variants; also skip Play Google Services when
// google-services.json is absent or lacks the com.masroufi.app client.
tasks.configureEach {
  val isGoogleServicesTask = name.startsWith("process") && name.endsWith("GoogleServices")
  if (isGoogleServicesTask) {
    val isFossTask = name.contains("Foss", ignoreCase = true)
    if (isFossTask || !hasMatchingGoogleServicesClient) {
      enabled = false
    }
  }
}

if (!isPureFossBuild && !hasMatchingGoogleServicesClient) {
  logger.warn(
    "WARNING: app/google-services.json does not yet contain a client for 'com.masroufi.app'. " +
      "Skipping processPlay*GoogleServices tasks until an updated google-services.json is downloaded from Firebase Console."
  )
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation("net.zetetic:sqlcipher-android:4.20.0@aar")
  implementation(libs.androidx.sqlite)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.pdfbox.android)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)

  // Phase 7C: Proprietary Google Play / Firebase / ML Kit dependencies strictly scoped to `play` flavor.
  "playImplementation"(platform(libs.firebase.bom))
  "playImplementation"(libs.firebase.firestore)
  "playImplementation"(libs.firebase.auth)
  "playImplementation"(libs.androidx.credentials)
  "playImplementation"(libs.androidx.credentials.play.services)
  "playImplementation"(libs.googleid)
  "playImplementation"(libs.firebase.appcheck.playintegrity)
  configurations.maybeCreate("playDebugImplementation")
  "playDebugImplementation"(libs.firebase.appcheck.debug)
  "playImplementation"(libs.mlkit.text.recognition)

  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<Test> {
  failFast = false
  timeout.set(Duration.ofMinutes(10))
  maxHeapSize = "1536m"
  maxParallelForks = 1
  val preCachedRobolectricDir =
    file("/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7")
  if (preCachedRobolectricDir.exists()) {
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", preCachedRobolectricDir.absolutePath)
  }
  testLogging {
    events("passed", "failed", "skipped")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
