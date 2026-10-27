// Plasma's own updates (brief 5.18 and 6.2, update/): the update file, the APK downloaded and
// checked against its SHA-256 and Plasma's signing certificate, and Android's installer.
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
}

android {
    namespace = "ca.gureet.plasma.update"
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
