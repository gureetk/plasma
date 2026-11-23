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
        // Plasma's own libmpv build (#102), from the releases of github.com/gureetk/plasma-libmpv:
        // v<version>/libmpv-<version>.aar, with the Ivy file beside it. Gradle reads the Ivy file
        // first, so it fetches with GET alone: looking for the AAR itself would start with a HEAD
        // request, which GitHub's file host has refused for those assets. app/build.gradle.kts
        // checks the AAR's SHA-256.
        exclusiveContent {
            forRepository {
                ivy {
                    name = "plasmaLibmpv"
                    url = uri("https://github.com/gureetk/plasma-libmpv/releases/download/")
                    patternLayout {
                        artifact("v[revision]/[module]-[revision].[ext]")
                        ivy("v[revision]/ivy-[revision].xml")
                    }
                    metadataSources { ivyDescriptor() }
                }
            }
            filter { includeModule("ca.gureet.plasma", "libmpv") }
        }
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
include(":pairing")
include(":remote")
include(":remote-protocol")
include(":editor")
include(":launcher")
include(":integrations:watchnext")
include(":integrations:jellyfin")
include(":adb")
include(":update")
include(":recording")
include(":player:core")
include(":player:vlc")
include(":player:mpv")
include(":player:media3")
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
