// The local ADB client (brief 5.13 and 6.2): pairing with the TV's wireless debugging, connecting,
// running shell commands, and replacing or restoring the TV's Home app. SPAKE2 comes from
// Flyfish233's spake2-java (GPL-3.0), kept unchanged under src/main/java, on the elisabeth curve.
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
}

android {
    namespace = "ca.gureet.plasma.adb"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.curve25519.elisabeth)
    implementation(libs.ed25519.elisabeth)
}
