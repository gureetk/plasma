// Edit in a Browser: the TV's server for a computer's or phone's browser to organize favorites,
// categories, hidden channels, numbering and locks, and the page it serves (brief 5.15's server).
plugins {
    id("plasma.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    api(project(":pairing"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.kotlinx.coroutines.test)
}
