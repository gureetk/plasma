// Sources, channels and the guide as the app sees them: adding a source, importing it, and the
// flows the screens draw from (brief 6.4).
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
}

android {
    namespace = "ca.gureet.plasma.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:database"))
    api(project(":core:network"))
    api(project(":source:xtream"))
    api(project(":channels"))
    implementation(project(":epg"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
