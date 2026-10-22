pluginManagement {
    includeBuild("build-logic")
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
}

rootProject.name = "plasma"

// Modules follow the brief, section 6.2.
include(":app")
include(":core:model")
include(":core:design")
include(":core:fixtures")
include(":core:testing")
include(":player:core")
include(":feature:home")
