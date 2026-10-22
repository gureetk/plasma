// The player's shared parts (brief 6.2). In M0: the video layer every screen draws over.
plugins {
    id("plasma.android.library")
    id("plasma.android.compose")
}

android {
    namespace = "ca.gureet.plasma.player.core"
}

dependencies {
    api(project(":core:design"))
}
