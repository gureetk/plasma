import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import java.util.zip.ZipFile
import javax.inject.Inject

plugins {
    id("plasma.android.application")
    id("plasma.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

// The release key lives only on the owner's machine, in ~/.gradle/gradle.properties:
//   plasma.release.storeFile, plasma.release.storePassword, plasma.release.keyAlias, plasma.release.keyPassword
// CI signs the Plasma Dev build with the DEV_* secrets, passed in as PLASMA_DEV_* environment variables.
fun prop(name: String): String? = (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

// Many TVs run 32-bit Android (armeabi-v7a), the owner's emulator on an Apple-silicon Mac is
// arm64-v8a, and libVLC's x86 builds stay out (brief 5.18 and 6.8, decisions: Owner's setup).
val shippedAbis = listOf("armeabi-v7a", "arm64-v8a")

android {
    namespace = "ca.gureet.plasma"

    defaultConfig {
        applicationId = "ca.gureet.plasma"
        versionCode = env("PLASMA_VERSION_CODE")?.toInt() ?: 1
        versionName = "0.1.0"
        manifestPlaceholders["appLabel"] = "Plasma"
    }

    // One APK per ABI, plus a universal one for first installs (brief 5.18 and 6.8). The same as
    // shippedAbis, written out for lint, which reads the ABIs from here.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }
    // The universal APK would otherwise carry every ABI the libraries ship.
    packaging {
        jniLibs {
            excludes += listOf("lib/x86/**", "lib/x86_64/**")
            // libVLC and libmpv each bring the C++ runtime, from different NDKs. libVLC's 32-bit
            // copy lacks what libmpv needs, so libmpv's goes in: the app's own copy (below) comes
            // first, and has everything libVLC uses too.
            pickFirsts += shippedAbis.map { "lib/$it/libc++_shared.so" }
        }
    }

    signingConfigs {
        val devStore = env("PLASMA_DEV_KEYSTORE")
        if (devStore != null) {
            create("dev") {
                storeFile = file(devStore)
                storePassword = env("PLASMA_DEV_KEYSTORE_PASSWORD")
                keyAlias = env("PLASMA_DEV_KEY_ALIAS")
                keyPassword = env("PLASMA_DEV_KEY_PASSWORD")
            }
        }
        val releaseStore = prop("plasma.release.storeFile")
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = prop("plasma.release.storePassword")
                keyAlias = prop("plasma.release.keyAlias")
                keyPassword = prop("plasma.release.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Plasma Debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "Plasma Dev"
            signingConfig = signingConfigs.findByName("dev") ?: signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    lint {
        // Checks the library modules too, and fails the build on errors (CI runs it on every pull request).
        checkDependencies = true
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:model"))
    implementation(project(":core:fixtures"))
    implementation(project(":player:core"))
    implementation(project(":player:vlc"))
    implementation(project(":player:mpv"))
    implementation(project(":core:data"))
    implementation(project(":stream"))
    implementation(project(":pairing"))
    implementation(project(":feature:home"))
    implementation(project(":feature:livetv"))
    implementation(project(":feature:guide"))
    implementation(project(":feature:player"))
    implementation(project(":feature:search"))
    implementation(project(":feature:vod"))
    implementation(project(":feature:apps"))
    implementation(project(":feature:profiles"))
    implementation(project(":feature:recordings"))
    implementation(project(":feature:catchup"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// ---- The C++ runtime -------------------------------------------------------------------------
// libVLC 3.7.7 ships the C++ runtime from NDK r21e, libmpv 1.0.0 from r28. libmpv needs
// std::to_chars for floating point, which r21e's 32-bit copy lacks: with it, libmpv can't load on
// an armeabi-v7a TV. r28's copy has everything libVLC uses, so it's taken out of libmpv's AAR
// into the app's own native libraries, which win the pickFirst above, and every APK is checked.

/** libmpv's AAR, for its C++ runtime. */
val mpvAar: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
}

dependencies {
    add(mpvAar.name, libs.libmpv)
}

/** Takes `libc++_shared.so` for each of [abis] out of [aar] into [outputDir], as `<abi>/libc++_shared.so`. */
abstract class ExtractCxxRuntime : DefaultTask() {
    @get:InputFiles
    abstract val aar: ConfigurableFileCollection

    @get:Input
    abstract val abis: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @get:Inject
    abstract val archives: ArchiveOperations

    @TaskAction
    fun extract() {
        fileSystem.sync {
            from(aar.files.map { archives.zipTree(it) }) {
                include(abis.get().map { "jni/$it/libc++_shared.so" })
                eachFile { path = path.removePrefix("jni/") }
            }
            includeEmptyDirs = false
            into(outputDir)
        }
    }
}

/** Fails when an APK's C++ runtime lacks what libmpv needs, the symbol r21e's 32-bit copy doesn't have. */
abstract class VerifyCxxRuntime : DefaultTask() {
    @get:InputFiles
    abstract val apks: DirectoryProperty

    @get:Internal
    abstract val loader: Property<BuiltArtifactsLoader>

    @TaskAction
    fun verify() {
        val built = loader.get().load(apks.get()) ?: throw GradleException("No APKs in ${apks.get()}")
        val needed = "_ZNSt6__ndk18to_charsEPcS0_d".toByteArray()
        for (apk in built.elements) {
            ZipFile(apk.outputFile).use { zip ->
                val runtimes = zip.entries().asSequence().filter { it.name.startsWith("lib/") && it.name.endsWith("/libc++_shared.so") }.toList()
                for (entry in runtimes) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val found = (0..bytes.size - needed.size).any { start -> needed.indices.all { bytes[start + it] == needed[it] } }
                    if (!found) throw GradleException("${File(apk.outputFile).name}: ${entry.name} isn't libmpv's C++ runtime, so libmpv won't load")
                }
            }
        }
    }
}

val mpvCxxRuntime = tasks.register<ExtractCxxRuntime>("extractMpvCxxRuntime") {
    aar.from(mpvAar)
    abis.set(shippedAbis)
    outputDir.set(layout.buildDirectory.dir("generated/mpvCxxRuntime"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(mpvCxxRuntime, ExtractCxxRuntime::outputDir)
        tasks.register<VerifyCxxRuntime>("verify${variant.name.replaceFirstChar(Char::uppercase)}CxxRuntime") {
            apks.set(variant.artifacts.get(SingleArtifact.APK))
            loader.set(variant.artifacts.getBuiltArtifactsLoader())
        }
    }
}
