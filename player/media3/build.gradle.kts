// Media3Engine: the Android player (brief 5.4 and 6.2), for Dolby Vision and HDR10+ movies and as
// the last resort, made the first time it's needed. Nothing outside this module imports Media3.
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
}

android {
    namespace = "ca.gureet.plasma.player.media3"
}

dependencies {
    api(project(":player:core"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}
