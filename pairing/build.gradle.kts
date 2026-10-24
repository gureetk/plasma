// The local web server and the phone pages: Add a Source (brief 5.15) and the phone remote (5.21).
plugins {
    id("plasma.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.kotlinx.coroutines.test)
}
