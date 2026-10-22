// The boards' made-up world (brief appendix A): channels, guide, movies, apps and their art.
// Everything here is fictional. Previews and tests use it for good; the app uses it until M1.
plugins {
    id("plasma.android.library")
    id("plasma.android.compose")
}

android {
    namespace = "ca.gureet.plasma.core.fixtures"
}

dependencies {
    api(project(":core:design"))
    api(project(":core:model"))
    implementation(project(":player:core"))
}
