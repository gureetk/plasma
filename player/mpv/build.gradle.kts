// MpvEngine: libmpv, the backup engine (brief 5.4 and 6.2), loaded the first time it's needed.
// Nothing outside this module imports libmpv.
plugins {
    id("plasma.android.library")
}

android {
    namespace = "ca.gureet.plasma.player.mpv"
}

dependencies {
    api(project(":player:core"))
    implementation(libs.libmpv)
    implementation(libs.kotlinx.coroutines.core)
}
