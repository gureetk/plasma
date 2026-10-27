// VlcEngine: libVLC 3.7, the default engine (brief 5.4 and 6.2). Nothing outside this module
// imports libVLC.
plugins {
    id("plasma.android.library")
}

android {
    namespace = "ca.gureet.plasma.player.vlc"
}

dependencies {
    api(project(":player:core"))
    implementation(libs.libvlc)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
