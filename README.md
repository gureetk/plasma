# Plasma

Plasma is an IPTV player for Android TV that is also the TV's home screen. Press Home and your last channel keeps playing behind your favorites, the guide, movies, series, recordings and your Android apps.

> **Plasma comes with no channels, playlists or subscriptions.** It plays a service you already have: a Stalker (MAG) portal, an Xtream Codes login, or an M3U playlist with an XMLTV guide. Issues and pull requests that add, ask for or link to streams, playlists or providers will be closed.

## Status

Early development. Every screen runs on sample data (milestone 0); live TV plays from an Xtream Codes login or an M3U playlist, through VLC, with its guide (milestone 1); the Guide goes by day, with catch-up, Start Over, reminders and search (milestone 2); and sources can be edited, tested, put in order, turned off and given their own playback settings (the start of milestone 3). Stalker portals, failover between sources, the backup player, movies, series, recordings and the launcher come next. What to try is in [docs/testing.md](docs/testing.md).

## What's planned

- Live TV with a channel list and live preview, a seven-day guide, channel numbers you can type, favorites and quick channel changes
- Catch-up and Start Over where your provider keeps an archive, plus a rewind buffer and local catch-up for channels without one
- Recording to a USB drive or a network folder, with files named the way Jellyfin expects
- Movies and series with resume and Continue Watching
- A home screen with your apps, inputs, a screensaver, and the Home button on Google TV
- A phone remote and keyboard served by the TV, with nothing to install
- Playback through libVLC, with mpv and Android's own player as fallbacks

The full design and build plan is in [docs/DESIGN.md](docs/DESIGN.md).

## Building

You need JDK 21 and the Android SDK (platform 37).

```sh
./gradlew :app:assembleDebug                              # debug build: ca.gureet.plasma.debug
./gradlew :app:assembleDev                                # test build:  ca.gureet.plasma.dev ("Plasma Dev")
./gradlew testDebugUnitTest -Proborazzi.test.verify=true  # unit tests, and screens compared with their reference images
./gradlew recordRoborazziDebug                            # update the reference images after a deliberate UI change
```

Every screen has a reference image at 1920 × 1080 in its module's `src/test/screenshots`, named after its design board (`feature/home/src/test/screenshots/01-home.png`). CI fails a pull request whose screens no longer match them.

The code is split into modules following section 6.2 of the brief: `core/design` holds the design system, `core/fixtures` the made-up sample world the screens run on until real sources arrive, and `feature/*` the screens.

## Installing on a TV

Plasma isn't on Google Play. Each build on `main` is published as a pre-release on this repository's Releases page, as `app-dev.apk`. To install one:

1. On the TV, turn on Developer options and Network debugging (or Wireless debugging).
2. From a computer on the same network:

   ```sh
   adb connect <tv-ip>:5555
   adb install -r app-dev.apk
   ```

Plasma Dev (`ca.gureet.plasma.dev`) and the release app (`ca.gureet.plasma`) are signed with different keys and install side by side.

## License

Plasma is free software, released under the [GNU General Public License v3.0 or later](LICENSE). The phone remote protocol and the phone apps, when they arrive, will be under the Apache License 2.0, as noted in their folders. The bundled fonts are under the SIL Open Font License 1.1. The sample art in `docs/design` and in the test fixtures is made up and isn't real channel or film branding.
