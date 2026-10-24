// The phone remote on the TV (brief 5.21): its server, pairing tokens, and the web page phones open.
plugins {
    id("plasma.jvm.library")
}

dependencies {
    api(project(":remote-protocol"))
    api(project(":pairing"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.kotlinx.coroutines.test)
}
