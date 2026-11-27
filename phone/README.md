# Plasma Remote

The phone remote apps for Android and iPhone (brief 5.22, M9): Plasma's phone remote, the three tabs of board 33, as an app that finds the TV by itself, opens the keyboard by itself and talks to the TV over a TLS connection pinned to the TV's own certificate. The web page the TV serves stays, and works on any phone.

Apache-2.0 (`LICENSE`), like `remote-protocol/`, which the apps share with the TV.

## What's here

| | |
|---|---|
| `shared/` | The apps' code and screens, in Kotlin with Compose Multiplatform: the tabs, the connection to the TV, and the demo TV. |
| `androidApp/` | Plasma Remote for Android (`ca.gureet.plasma.remote`): the shared app in an activity. No Google Play services, so F-Droid can build it. |
| `iosApp/` | Plasma Remote for iPhone (`ca.gureet.plasma.remote`): a SwiftUI shell around the shared app. `project.yml` is its XcodeGen project. |

## Building

It's a Gradle build of its own, run with the repository's wrapper from the repository's root:

- `./gradlew -p phone :androidApp:assembleDebug`: the Android app.
- `./gradlew -p phone :shared:desktopTest :shared:testAndroidHostTest`: the tests. `desktopTest` also draws the screens from the demo TV into `shared/build/screens/`, at the board's size, to compare with `docs/design/screens/33-phone-remote.jpg`.
- The iPhone app needs a Mac with Xcode: `brew install xcodegen`, then `xcodegen` in `iosApp/`, and open `PlasmaRemote.xcodeproj`. Its build runs Gradle for the shared framework. The iPhone targets are declared only on macOS, so a build on Linux has none.

The `Plasma Remote` workflow (`.github/workflows/phone.yml`) builds both apps for every change to `phone/` or `remote-protocol/`.
