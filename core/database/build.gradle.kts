// Room: sources, channels, the guide, favorites, recent channels, reminders and search (brief 6.2 and 6.3).
plugins {
    id("plasma.android.library")
    id("plasma.robolectric.tests")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "ca.gureet.plasma.core.database"
}

// Each version's schema, for the migration tests, including the one this build exports.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}
tasks.matching { it.name.matches(Regex("merge\\w*UnitTestAssets")) }.configureEach { dependsOn("copyRoomSchemas") }

room {
    // Kept in the repository, so each version's schema is there to write migrations against.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}
