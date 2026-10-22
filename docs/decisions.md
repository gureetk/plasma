# Decisions

What the owner has decided since the brief (`docs/DESIGN.md`), and choices made while building. These take precedence over the matching open items in the brief's section 9.2.

## From the owner (22 October 2026)

| Topic | Decision |
|---|---|
| Repository | A new private repository. It goes public once the public name is settled (brief 9.2, item 1). Code from the owner's earlier `iptv-player` project is reviewed piece by piece and ported only where it holds up (most likely the Xtream, Stalker, M3U and XMLTV clients and their tests); the app layer is built fresh to the boards. |
| LAN TV-server sources (Tvheadend, Enigma2, HDHomeRun, Jellyfin Live TV, Plex, DVBViewer, DVBLink) | Not in 1.0. The source layer is designed so they slot in; they come in 1.1. |
| Owner's setup | Chromecast with Google TV 4K, a third-party remote, Wi-Fi, sound through the TV's speakers. Builds include `armeabi-v7a` (the TV) and `arm64-v8a` (the owner's Android TV emulator on an Apple-silicon Mac). |
| Owner's remote | Its extra keys (numbers, colours, CH ±) don't reach apps on Google TV. Plasma must work fully with the D-pad, OK, long-press OK, Back and Home (as the brief already requires), reads every standard key code when a remote does send it, and gets a Diagnostics page that shows what each key sends, plus "learn a key" in Settings › Remote & Buttons. |
| Owner's source | A self-hosted Xtream server with no connection limit. It has a guide (XMLTV) but no catch-up at the moment. M1 starts with Xtream instead of M3U. |
| Application id (9.2, item 1) | `ca.gureet.plasma` for release builds; `ca.gureet.plasma.dev` ("Plasma Dev") for CI builds and `ca.gureet.plasma.debug` for local debug builds, so they install side by side. |
| Licence (9.2, items 11 and 19) | GPL-3.0-or-later for the TV app; Apache-2.0 for `remote-protocol/` and `phone/`. |
| Language (9.2, item 3) | American English is the default (`values/`). A British English variant may come later. |
| Signing | A dev key in GitHub Actions secrets (`DEV_KEYSTORE_BASE64`, `DEV_KEYSTORE_PASSWORD`, `DEV_KEY_ALIAS`, `DEV_KEY_PASSWORD`) signs every CI build. The release key is held only by the owner, and release builds are made on the owner's machine. |
| Commits and pull requests | Claude pushes branches and opens pull requests; CI builds a Plasma Dev APK for each. Commit author `gureet <noreply@git.gureet.ca>` with a `Co-Authored-By: Claude` trailer. No links to Claude sessions or projects in commits or pull requests. Squash merges keep every commit on `main` created and signed by GitHub. |
| Merging | Claude merges its own pull request when CI is green and the work checks out against the boards. When something needs trying on the emulator or the TV, the pull request stays open with a short test list for the owner. |
| Testing | Screens are rendered to images in tests and compared with the boards; logic gets unit tests. The owner tries builds on the Android TV emulator at the end of each milestone, and on the TV later for playback, frame-rate switching, the Home button and performance. |

## Build choices

| Topic | Choice | Why |
|---|---|---|
| Compose for TV | Compose foundation only, without `androidx.tv:tv-material` | Every component on the boards is custom (focus is lift, shadow and a white fill, never a ring), so Plasma's own `PlasmaSurface` and components in `core/design` draw them; tv-material's components would all be restyled anyway. The standard foundation lazy layouts are what the brief asks for (6.1). |
| SDK levels | compile 37, target 36, minimum 26 | Current AndroidX libraries need compile SDK 37. Target 36 until the Android 17 local network permission is handled (brief 6.1). |
| Modules | As in the brief (6.2), added as they're needed. M0 has `core/model`, `core/design`, `core/fixtures` (the appendix A sample world), `core/testing`, `player/core` (the video layer) and one module per feature. Shared Gradle setup is in `build-logic/`. | |
| Home rows | Horizontal rows keep the focused tile at the left edge (x 96) until they reach their end (brief 4.2), rather than at 30 % (6.6). | The boards' rows start at the left margin and peek past the right edge. |
| Home scrolling | Home rests in three positions: the top (board 01), a little lower when Favorite Channels has focus (so the whole row shows), and the rows (board 02). | |
| Screenshot tests | Robolectric with native graphics at 1920 × 1080, Roborazzi images in each module's `src/test/screenshots`, named after their boards. CI fails when a screen changes without its images being updated. | Lets every screen be checked against its board without a device. |
