plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// VERSION is the single source: displayed version and Android versionCode (docs/releasing.md).
val appVersion = rootProject.file("VERSION").readText().trim()
val calendar = Regex("(20[0-9]{2})\\.([1-9]|[1-4][0-9]|5[0-3])\\.([1-9][0-9]{0,3})")
val appVersionCode =
    if (appVersion == "0.1.0") {
        1 // Development version, never released.
    } else {
        val (year, week, number) =
            requireNotNull(calendar.matchEntire(appVersion)) { "VERSION must follow YYYY.W.N (week 1–53, number 1–9999)." }
                .groupValues
                .drop(1)
                .map(String::toInt)
        year * 1_000_000 + week * 10_000 + number
    }

// Distribution signing: CI provides the key; locally, the release build stays debug-signed.
val keystorePath: String? = System.getenv("RETROHUB_KEYSTORE_PATH")
val requireSigning = System.getenv("RETROHUB_REQUIRE_RELEASE_SIGNING") == "true"
if (requireSigning) require(!keystorePath.isNullOrBlank()) { "A release signing key is required." }

// Packaged ABIs: both by default, a single one with -Pretrohub.abis=x86_64 (emulator tests).
val abis =
    (findProperty("retrohub.abis") as String? ?: "arm64-v8a,x86_64")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

android {
    namespace = "ovh.verbois.retrohub"
    compileSdk = 37

    defaultConfig {
        applicationId = "ovh.verbois.retrohub"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Test server: adb reverse tcp:8011 tcp:8011 (scripts/test.sh e2e).
        testInstrumentationRunnerArguments["retrohubServer"] = "http://127.0.0.1:8011"
        ndk { abiFilters += abis }
    }

    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("distribution") {
                storeFile = file(keystorePath)
                storePassword = requireNotNull(System.getenv("RETROHUB_KEYSTORE_PASSWORD"))
                keyAlias = requireNotNull(System.getenv("RETROHUB_KEY_ALIAS"))
                keyPassword = requireNotNull(System.getenv("RETROHUB_KEY_PASSWORD"))
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // CI signs with the distribution key and never publishes a debug-signed APK.
            signingConfig = signingConfigs.getByName(if (keystorePath.isNullOrBlank()) "debug" else "distribution")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // libretro cores are extracted to disk: LibretroDroid opens them by path.
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*.md")
    }

    testOptions {
        animationsDisabled = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        // Versions checked by hand (docs/decisions.md); ABIs chosen with -Pretrohub.abis.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi", "ChromeOsAbiSupport")
        warningsAsErrors = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.work.runtime)
    implementation(libs.coroutines.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.libretrodroid)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.sqlite.bundled)
}
