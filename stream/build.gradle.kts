// The stream hub: Plasma opens live streams itself and serves them to the engine on 127.0.0.1
// (brief 5.4.2 and 6.2).
plugins {
    id("plasma.jvm.library")
}

dependencies {
    api(project(":core:network"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
