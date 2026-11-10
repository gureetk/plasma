// SPDX-License-Identifier: GPL-3.0-or-later
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Macrobenchmarks for brief 6.7's budgets (#127): cold start, the Guide's scrolling and key presses,
// measured against the app's benchmark build on a device or an emulator, which brings its own
// made-up source. Run them with ./gradlew :benchmark:connectedBenchmarkAndroidTest.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "ca.gureet.plasma.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        // Frame timing needs Android 10.
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // An emulator's numbers only show changes (docs/testing.md), but they're allowed.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        // Pairs with the app's benchmark build type.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}

// Only the benchmark build: a debug one would measure a debuggable app.
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}
