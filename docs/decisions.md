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
| Focus tests | Each screen's D-pad paths are tested by pressing keys in the same Robolectric setup and checking what has focus. | "D-pad focus works everywhere" (M0) without a device. |
| Category column | Movies and Series both show twelve category rows, the twelfth at half strength when the list goes on (board 13). Board 15 cuts Series at eleven. | One rule for both screens. |
| Poster grid | The focused row stays about 30 % down while there's room to scroll (brief 6.6). Up from the top row goes to the current tab, or to Sort from the last column. | |
| Title art | Fitted within 800 × 188 px, keeping its shape. Without art, the title is set in large type. | Both boards' art fits exactly: The Last Lighthouse fills the width, Atlas Station the height. |
| Detail tabs and seasons | Moving onto a tab or a season shows it, as the Guide's days do. | |
| Sort | Yellow and OK on the "⌄" button step through the orders. A list to pick from comes with the shared options sheet. | |
| Durations | Minutes after hours are zero-padded everywhere ("1 h 02 min left"), as brief 2.10 writes them. | |
| TalkBack | Each focusable surface is one node, read as its label. | |
| Options sheets | Menu or long-press OK opens one shared sheet (the style of board 10) with the choices for the focused item. Focus stays in the sheet, Back closes it, and focus goes back to the item. | One pattern for Apps, posters, Multiview tiles and, later, channels and recordings. |
| All Apps | Lists the apps that aren't on Home, A to Z, as board 04 does (6 + 7 = 13 installed). | |
| Multiview toolbar | The current layout is white while focus is on the tiles (board 12); once the toolbar has focus, focus is white and the current layout is marked like a tab. Labels are Title Case, as brief 2.10 asks for buttons. | Two white buttons at once would be ambiguous. |
| Screensaver | The in-app idle screen shows after ten minutes without a key press, except while something plays. The key that wakes Plasma does nothing else. The DreamService comes with the launcher work (M5). | |
| Recordings order | Newest first sorts by when each recording aired, a series folder by its newest episode. Board 17's tiles don't follow their own dates (Sunday's film before Monday's and Tuesday's recordings), so the same tiles come in a different order. | Sorting by date is what the label says. |
| Recording dates | A recording's line says when it aired ("Yesterday", "Sun 18 Oct"); news adds the time ("Yesterday, 20:30"), since a news channel airs bulletins of the same name all day. | Board 17 shows the time only for Business Wrap. |
| Deleting recordings | Red asks first, with Cancel as the first choice, so a second OK can't delete by accident. Blue starts selecting several; OK ticks them, red deletes them, Back or blue stops. | |
| Coming Up cards | The channel's logo sits on the card, so focus lifts and lightens the card instead of filling it white. Scheduled and Series Rules use list rows with the logo on a plate, which can turn white. | A white logo would vanish on a white card. |
| Catch-up | Opens on yesterday when the archive has it, on the program that was on at this time of day (as the Guide opens other days). CH + goes to a newer day, CH − to an older one. Programs the archive can't play stay reachable, dimmed, and OK does nothing. | Board 18 opens on yesterday. |
| Channel names | Channels keep their full names from appendix A everywhere ("Arena Sports Extra", "Northline News"), where board 18's list shortens two of them. | One name per channel. |
| Settings pages | Every page of Settings works like board 19: the focused row's detail on the left, the rows on the right. OK opens the row's page over the current one (Back returns to that row), opens its choices in a sheet on the right with the current one ticked, or flips a switch. | One pattern for everything in brief 5.16. |
| Settings choices | What's chosen in Settings lasts while the app runs, in one store for the whole app. Saving it comes with the sources (M1). | |
| Launcher & Home rows | When the TV Turns Off and Keep Watching in a Small Window come right after When the TV Turns On, where brief 4.22 places them, so Screensaver ends the first screen and Clock is one row further down. Board 22 doesn't draw those two rows. | |
| Settings rows still to come | Rows that lead to a list, a sign-in or a sheet that comes with a later milestone (a source's actions, Apps, Jellyfin Server and others) show their detail, and OK does nothing yet. Add a Source, Duplicate Numbers, Home Screen setup, Phone Remote and the profile editor open a placeholder until their screens are built. | |
