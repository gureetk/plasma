plugins {
    id("plasma.android.library")
    id("plasma.android.compose")
    // The interface kit and the component galleries are checked like screens.
    id("plasma.screenshot.tests")
}

android {
    namespace = "ca.gureet.plasma.core.design"
}

dependencies {
    api(project(":core:model"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui.tooling.preview)
}
