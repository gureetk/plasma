// Plasma Remote for Android (brief 5.22): the shared app in an activity. No Google Play services,
// so F-Droid can build it.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** Plasma Remote's version, for both apps: a `remote-v` tag of the same builds a release (.github/workflows/remote-release.yml). */
val remoteVersion = "0.1.0"

/** 0.1.0 is 100, 1.2.3 is 10203: each part below 100. */
fun versionCodeOf(version: String): Int = version.split('.').map(String::toInt).fold(0) { code, part -> code * 100 + part }

/** The release key, given by the Release workflow only (remote-release.yml); without it, a release build is unsigned. */
val releaseStore: String? = providers.gradleProperty("remote.release.storeFile").orNull

android {
    namespace = "ca.gureet.plasma.remote"
    compileSdk = 37

    defaultConfig {
        applicationId = "ca.gureet.plasma.remote"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOf(remoteVersion)
        versionName = remoteVersion
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("remote.release.storePassword").get()
                keyAlias = providers.gradleProperty("remote.release.keyAlias").get()
                keyPassword = providers.gradleProperty("remote.release.keyPassword").get()
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    dependenciesInfo {
        // F-Droid and other stores that build from source don't want Google's encrypted block.
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
