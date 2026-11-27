// Plasma Remote, the phone remote apps for Android and iPhone (brief 5.22, M9): a Gradle build of
// its own, run with the repository's wrapper (`./gradlew -p phone …`). It takes the shared
// protocol module and the version catalog from the TV app's build, and nothing else from it.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "plasma-remote"

include(":shared")
include(":androidApp")

// The protocol the TV speaks, shared with it (Apache-2.0).
include(":remote-protocol")
project(":remote-protocol").projectDir = file("../remote-protocol")
