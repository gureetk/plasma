// Room: sources, channels, the guide, favorites and recent channels (brief 6.2 and 6.3).
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "ca.gureet.plasma.core.database"
}

room {
    // Kept in the repository, so each version's schema is there to write migrations against.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
