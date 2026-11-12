import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.process.ExecOperations
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject

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
    ndkVersion = "27.2.12479018"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

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

    testBuildType = providers.gradleProperty("retrohub.testVariant").getOrElse("debug")

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // CI signs with the distribution key and never publishes a debug-signed APK.
            signingConfig = signingConfigs.getByName(if (keystorePath.isNullOrBlank()) "debug" else "distribution")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            testProguardFiles("test-proguard-rules.pro")
        }
    }

    sourceSets.getByName("benchmark").apply {
        manifest.srcFile("src/debug/AndroidManifest.xml")
        res.directories += "src/debug/res"
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
    implementation(libs.zxing.embedded)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    add("benchmarkImplementation", libs.compose.ui.test.manifest)

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

// Trace external test references before shrinking, so instrumentation can call optimized code.
abstract class BenchmarkTestKeepRules : DefaultTask() {
    @get:Classpath abstract val targetJars: ListProperty<RegularFile>

    @get:Classpath abstract val targetDirectories: ListProperty<Directory>

    @get:Classpath abstract val sourceJars: ListProperty<RegularFile>

    @get:Classpath abstract val sourceDirectories: ListProperty<Directory>

    @get:Classpath abstract val tracer: RegularFileProperty

    @get:Classpath abstract val bootClasspath: ConfigurableFileCollection

    @get:OutputFile abstract val rules: RegularFileProperty

    @get:Inject abstract val execution: ExecOperations

    private fun archive(
        jars: List<RegularFile>,
        directories: List<Directory>,
        output: File,
        excluded: Set<String>,
    ): Set<String> {
        val names = mutableSetOf<String>()
        ZipOutputStream(output.outputStream()).use { zip ->
            for (file in jars) {
                ZipFile(file.asFile).use { jar ->
                    for (entry in jar.entries().asSequence()) {
                        if (!entry.name.endsWith(".class") || entry.name in excluded || !names.add(entry.name)) continue
                        zip.putNextEntry(ZipEntry(entry.name))
                        jar.getInputStream(entry).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            for (directory in directories) {
                for (file in directory.asFile.walkTopDown().filter { it.isFile && it.extension == "class" }) {
                    val name = file.relativeTo(directory.asFile).invariantSeparatorsPath
                    if (name in excluded || !names.add(name)) continue
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return names
    }

    @TaskAction
    fun generate() {
        val target = temporaryDir.resolve("target.jar")
        val source = temporaryDir.resolve("source.jar")
        val targets = archive(targetJars.get(), targetDirectories.get(), target, emptySet())
        archive(sourceJars.get(), sourceDirectories.get(), source, targets)
        val output = rules.get().asFile
        output.parentFile.mkdirs()
        execution.javaexec {
            classpath(tracer.get().asFile)
            mainClass.set("com.android.tools.r8.tracereferences.TraceReferences")
            args(
                "--keep-rules",
                "--target",
                target,
                "--source",
                source,
                "--output",
                output,
                "--map-diagnostics",
                "error",
                "warning",
            )
            bootClasspath.files.forEach { args("--lib", it) }
        }
        check(output.length() > 0) { "No instrumentation references were found in the benchmark app." }
        // Keep package-private inheritance valid around APIs retained for the test APK.
        output.writeText("-keeppackagenames **\n" + output.readText())
    }
}

androidComponents {
    onVariants(selector().withBuildType("benchmark")) { variant ->
        variant.androidTest?.let { tests ->
            val keep =
                tasks.register<BenchmarkTestKeepRules>("benchmarkTestKeepRules") {
                    tracer.set(sdkComponents.sdkDirectory.map { it.file("build-tools/37.0.0/lib/d8.jar") })
                    bootClasspath.from(sdkComponents.bootClasspath)
                    rules.set(layout.buildDirectory.file("generated/benchmark/test-keep.pro"))
                }
            variant.artifacts.forScope(ScopedArtifacts.Scope.ALL).use(keep).toGet(
                ScopedArtifact.CLASSES,
                BenchmarkTestKeepRules::targetJars,
                BenchmarkTestKeepRules::targetDirectories,
            )
            tests.artifacts.forScope(ScopedArtifacts.Scope.ALL).use(keep).toGet(
                ScopedArtifact.CLASSES,
                BenchmarkTestKeepRules::sourceJars,
                BenchmarkTestKeepRules::sourceDirectories,
            )
            variant.proguardFiles.add(keep.flatMap { it.rules })
        }
    }
}
