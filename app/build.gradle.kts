import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// One opt-in for the whole module, so @OptIn(ExperimentalMaterial3Api::class) is not needed anywhere.
kotlin {
  compilerOptions {
    freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
  }
}

// ---- Build settings: Gradle property > environment variable (CI) > .env file > default ----
val dotEnv: Map<String, String> = rootProject.file(".env").takeIf { it.exists() }
  ?.readLines()
  ?.map { it.trim() }
  ?.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
  ?.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim().trim('"', '\'') }
  ?: emptyMap()

fun setting(name: String, default: String = ""): String =
  providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull
    ?: dotEnv[name]
    ?: default

android {
  namespace = "com.willykez.campconnect"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.willykez.campconnect"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    val supabaseUrl = setting("SUPABASE_URL")
    val supabaseAnonKey = setting("SUPABASE_ANON_KEY")

    buildConfigField(
      "String",
      "SUPABASE_URL",
      "\"${supabaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    )

    buildConfigField(
      "String",
      "SUPABASE_ANON_KEY",
      "\"${supabaseAnonKey.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    )
  }

  signingConfigs {
    val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("STORE_PASSWORD")
    val releaseKeyPassword = System.getenv("KEY_PASSWORD")
    val releaseKeyAlias = System.getenv("KEY_ALIAS") ?: "campus-connect-key"

    if (!releaseKeystorePath.isNullOrBlank() &&
      !releaseStorePassword.isNullOrBlank() &&
      !releaseKeyPassword.isNullOrBlank()
    ) {
      create("release") {
        storeFile = file(releaseKeystorePath)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
        storeType = "PKCS12"
      }
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
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )

      if (signingConfigs.findByName("release") != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }

    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE*"
      excludes += "META-INF/NOTICE*"
      excludes += "META-INF/*.kotlin_module"
    }
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  lint {
    abortOnError = false
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("SUPABASE_URL")
  ignoreList.add("SUPABASE_ANON_KEY")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)

  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

// Configuration-Cache compatible verification task
val liveUrlProvider = providers.gradleProperty("SUPABASE_URL")
  .orElse(providers.environmentVariable("SUPABASE_URL"))
  .orElse(provider { dotEnv["SUPABASE_URL"] ?: "" })

val liveKeyProvider = providers.gradleProperty("SUPABASE_ANON_KEY")
  .orElse(providers.environmentVariable("SUPABASE_ANON_KEY"))
  .orElse(provider { dotEnv["SUPABASE_ANON_KEY"] ?: "" })

tasks.register("verifyLiveConfig") {
  val urlProvider = liveUrlProvider
  val keyProvider = liveKeyProvider
  inputs.property("supabaseUrl", urlProvider)
  inputs.property("supabaseAnonKey", keyProvider)

  doLast {
    val url = urlProvider.get()
    val key = keyProvider.get()
    val isInvalidUrl = url.isBlank() || url.contains("YOUR_", ignoreCase = true)
    val isInvalidKey = key.isBlank() || key.contains("YOUR_", ignoreCase = true)

    if (isInvalidUrl || isInvalidKey) {
      throw GradleException(
        "SUPABASE_URL and SUPABASE_ANON_KEY are missing or still placeholders. " +
          "Set them in .env, or as GitHub repository secrets, before building a release. See README.md."
      )
    }
  }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn("verifyLiveConfig") }
