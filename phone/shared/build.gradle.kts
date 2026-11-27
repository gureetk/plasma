// Plasma Remote's code and interface, shared by the Android and iPhone apps (brief 5.22): the
// screens in Compose Multiplatform, the connection to the TV and the demo TV. The iPhone targets
// are declared only where they can be built, on macOS (or with -Pplasma.ios=true).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val iosTargets = System.getProperty("os.name").startsWith("Mac") ||
    providers.gradleProperty("plasma.ios").orNull == "true"

kotlin {
    jvmToolchain(21)

    android {
        namespace = "ca.gureet.plasma.remote.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }

    // Only for the tests that draw the screens to pictures, here and in CI: there's no desktop app.
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    if (iosTargets) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "Shared"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":remote-protocol"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        named("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            implementation(libs.okhttp)
        }
        named("androidHostTest").dependencies {
            implementation(libs.okhttp.mockwebserver)
            implementation(libs.okhttp.tls)
        }
    }
}

compose.resources {
    packageOfResClass = "ca.gureet.plasma.remote.app.resources"
}
