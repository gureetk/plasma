// The phone remote's protocol (brief 5.21), shared by the TV and the Plasma Remote apps (5.22, M9).
// Kotlin Multiplatform: the JVM target is what the TV's `remote` module and the Android app use.
// The iPhone targets are declared only where they can be built, on macOS (or with
// -Pplasma.ios=true), so a build on Linux, as the TV's are, configures nothing more than before.
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val iosTargets = System.getProperty("os.name").startsWith("Mac") ||
    providers.gradleProperty("plasma.ios").orNull == "true"

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    if (iosTargets) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Java 17 bytecode, as the TV's other plain Kotlin modules (plasma.jvm.library).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// So `./gradlew testDebugUnitTest` runs these tests along with the Android modules' ones.
tasks.register("testDebugUnitTest") { dependsOn("allTests") }
