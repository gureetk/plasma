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
include(":core:network")
include(":core:database")
include(":core:data")
include(":source:xtream")
include(":source:stalker")
include(":source:m3u")
include(":epg")
include(":channels")
include(":stream")
include(":player:core")
include(":player:vlc")
include(":feature:home")
include(":feature:livetv")
include(":feature:guide")
include(":feature:player")
include(":feature:search")
include(":feature:vod")
include(":feature:apps")
include(":feature:profiles")
include(":feature:recordings")
include(":feature:catchup")
include(":feature:settings")
