// Settings (brief 4.19–4.34): the settings list and its pages.
plugins {
    id("plasma.android.feature")
}

android {
    namespace = "ca.gureet.plasma.feature.settings"
}

dependencies {
    // Jellyfin's sign-in steps, on a test clock.
    testImplementation(libs.kotlinx.coroutines.test)
}
