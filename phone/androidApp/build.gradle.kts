// Plasma Remote for Android (brief 5.22): the shared app in an activity. No Google Play services,
// so F-Droid can build it.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ca.gureet.plasma.remote"
    compileSdk = 37

    defaultConfig {
        applicationId = "ca.gureet.plasma.remote"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
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
