// The phone remote's protocol (brief 5.21), shared by the TV and the Plasma Remote apps (5.22, M9).
// Common Kotlin only, so it can become a Kotlin Multiplatform module when the apps come.
plugins {
    id("plasma.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
