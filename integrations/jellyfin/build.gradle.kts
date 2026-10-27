// Jellyfin (brief 5.19 and 6.2, integrations/jellyfin): finding the server, signing in with Quick
// Connect or a password, resume items and Next Up for Continue Watching, and playback reporting.
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
}

android {
    namespace = "ca.gureet.plasma.integrations.jellyfin"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(libs.jellyfin.core)
    // The SDK talks over Plasma's own HTTP client, and reads with the app's kotlinx.serialization.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
