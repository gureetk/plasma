// Android's Play Next row (brief 5.19 and 6.2, integrations/watchnext): other apps' items for
// Continue Watching, and opening them in their apps.
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
}

android {
    namespace = "ca.gureet.plasma.integrations.watchnext"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
