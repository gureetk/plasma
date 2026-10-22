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

android {
    namespace = "ca.gureet.plasma"

    defaultConfig {
        applicationId = "ca.gureet.plasma"
        versionCode = env("PLASMA_VERSION_CODE")?.toInt() ?: 1
        versionName = "0.1.0"
        manifestPlaceholders["appLabel"] = "Plasma"
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
    implementation(project(":feature:home"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
}
