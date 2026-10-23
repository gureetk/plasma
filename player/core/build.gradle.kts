// The player's shared parts (brief 6.2): the video layer every screen draws over, the engine
// interface, and the manager that plays channels through the stream hub (6.5).
plugins {
    id("plasma.android.library")
    id("plasma.android.compose")
}

android {
    namespace = "ca.gureet.plasma.player.core"
}

dependencies {
    api(project(":core:design"))
    api(project(":stream"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
