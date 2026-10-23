// The Xtream Codes player_api client and its URL builders (brief 6.2 and 7.2).
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
