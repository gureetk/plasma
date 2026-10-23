// HTTP for every provider request: one OkHttp setup, the user agent and log redaction (brief 6.2).
plugins {
    id("plasma.jvm.library")
}

dependencies {
    api(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
