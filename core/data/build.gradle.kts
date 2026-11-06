// Sources, channels and the guide as the app sees them: adding a source, importing it, and the
// flows the screens draw from (brief 6.4).
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
    // Back Up Settings' file (brief 5.17, #108).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ca.gureet.plasma.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:database"))
    api(project(":core:network"))
    api(project(":source:xtream"))
    api(project(":source:stalker"))
    api(project(":source:m3u"))
    api(project(":channels"))
    implementation(project(":epg"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
