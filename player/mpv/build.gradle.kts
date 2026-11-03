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
    // A GitHub release has no metadata to say it's an AAR (settings.gradle.kts)
    implementation(variantOf(libs.libmpv) { artifactType("aar") })
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
