plugins {
    id("plasma.android.library")
    id("plasma.android.compose")
    // The interface kit and the component galleries are checked like screens.
    id("plasma.screenshot.tests")
}

android {
    namespace = "ca.gureet.plasma.core.design"
    // For this check's branch only: a look at what a device's GPU draws.
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
    api(project(":core:model"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui.tooling.preview)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
