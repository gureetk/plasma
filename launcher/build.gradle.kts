// The launcher (brief 5.13 and 6.2): the Home Button Override service, what this TV's Home button
// does, and the Home role helpers. Apps, inputs and power-on join it as M5 goes on.
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
}

android {
    namespace = "ca.gureet.plasma.launcher"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
}
