// The local web server and the phone pages for Add a Source (brief 5.15 and 6.2).
plugins {
    id("plasma.jvm.library")
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.kotlinx.coroutines.test)
}
