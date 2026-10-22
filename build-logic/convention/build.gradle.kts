import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "ca.gureet.plasma.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "plasma.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "plasma.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "plasma.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("screenshotTests") {
            id = "plasma.screenshot.tests"
            implementationClass = "ScreenshotTestsConventionPlugin"
        }
        register("androidFeature") {
            id = "plasma.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "plasma.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
