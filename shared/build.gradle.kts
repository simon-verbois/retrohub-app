// Shared Android / iOS code: network, sync, local database, emulation profiles and console UI.
// The JVM target runs fast unit tests (MockWebServer); iOS is compiled on a Mac.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "ovh.verbois.retrohub.shared"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
    }
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "RetroHubShared"
            isStatic = true
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            api(libs.serialization.json)
            api(libs.okio)
            api(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.mp.ui)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.coroutines.android)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
        jvmTest.dependencies {
            implementation(libs.okhttp.mockwebserver)
            implementation(libs.junit)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }
}

// Compose resources + the repository CHANGELOG, bundled as is ("What's new" page).
val changelogResources =
    tasks.register<Sync>("changelogResources") {
        from("src/commonMain/composeResources")
        from(rootProject.file("CHANGELOG.md")) { into("files") }
        into(layout.buildDirectory.dir("generated/changelog"))
    }

compose.resources {
    packageOfResClass = "ovh.verbois.retrohub.resources"
    publicResClass = true
    generateResClass = always
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = changelogResources.map { layout.buildDirectory.dir("generated/changelog").get() },
    )
}
