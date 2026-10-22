// Shared screenshot-test setup: Robolectric at 1920 × 1080 (TV, xhdpi) and Roborazzi.
plugins {
    id("plasma.android.library")
    id("plasma.android.compose")
}

android {
    namespace = "ca.gureet.plasma.core.testing"
}

dependencies {
    api(project(":core:fixtures"))
    api(libs.junit)
    api(libs.robolectric)
    api(libs.androidx.test.ext.junit)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
}
