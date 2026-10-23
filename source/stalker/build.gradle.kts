// The Stalker / Ministra portal client: a MAG box's handshake, channels, guide and links (brief 6.2 and 7.1).
plugins {
    id("plasma.jvm.library")
}

dependencies {
    api(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
