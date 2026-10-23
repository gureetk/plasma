# Testing Plasma on an emulator or a TV

The unit and screenshot tests run on every pull request. This page is for the checks they can't do: Plasma on a real Android TV, or on the Android TV emulator, driven with a remote.

## Getting Plasma Dev

Every merge to `main` publishes **Plasma Dev** as a pre-release on the Releases page, as `app-dev.apk`. A pull request's build is on its Build run instead: open the run from the pull request's checks and download **plasma-dev** under Artifacts, a zip holding the same APK. Plasma Dev installs as its own app (`ca.gureet.plasma.dev`), next to the release app.

- **Emulator:** in Android Studio's Device Manager, add a TV device with a 1080p screen and a Google TV system image.
- **TV:** turn on Developer options and Network debugging (or Wireless debugging), then connect from a computer on the same network with `adb connect <tv-ip>:5555`.

Then install it:

```sh
adb install -r app-dev.apk
```

Open **Plasma Dev** from the TV's apps. Plasma doesn't take over the Home button until the launcher work (M5), so the system's Home leaves it.

## The remote

Everything in Plasma works with the D-pad, OK, long-press OK and Back alone, the keys every Google TV remote has (brief 3.6). The other keys are shortcuts. Some remotes have them but Google TV doesn't pass them on.

| Remote | Emulator | From a computer |
|---|---|---|
| D-pad | arrow keys | `adb shell input keyevent DPAD_UP` (`DOWN`, `LEFT`, `RIGHT`) |
| OK | Enter | `adb shell input keyevent DPAD_CENTER` |
| Long-press OK | hold Enter | `adb shell input keyevent --longpress DPAD_CENTER` |
| Back | the emulator's Back button | `adb shell input keyevent BACK` |
| Menu | | `adb shell input keyevent MENU` |
| Red, green, yellow, blue | | `adb shell input keyevent PROG_RED` (`PROG_GREEN`, `PROG_YELLOW`, `PROG_BLUE`) |
| CH + and − | | `adb shell input keyevent CHANNEL_UP` (`CHANNEL_DOWN`) |
| 0–9 | the number keys | `adb shell input keyevent 1` |

## Milestone 0: every screen on sample data

M0 is the design system and every screen of the brief, running on the made-up sample world (brief appendix A). Nothing plays yet: a channel shows a still picture, and a movie or recording opens a placeholder page. Settings remember what you choose only until the app closes.

Check that each screen looks like its board in `docs/design/screens` and that focus moves where you expect. For each item, the result to look for comes after the arrow.

### Home (boards 01, 02 and 06)

- [ ] Open Plasma Dev → Home, with the Live TV tile focused and Harbour City v Redmoor in the hero.
- [ ] Up → the hero; Left and Right step through its four items. Up again → the Home tab in the top bar.
- [ ] Down from the dock → Favorite Channels, then Continue Watching, New in Movies and Apps. Each row starts on its first item.
- [ ] Back from the rows → the top again, on the Live TV tile.
- [ ] OK on a favorite → the player on that channel. Long-press OK (or Menu) → Remove from Favorites and Move.
- [ ] Long-press OK on a Continue Watching item → Resume and Remove from Continue Watching; Kite Season, from Jellyfin, offers Open and Hide from Plasma instead.
- [ ] Leave the remote alone for ten minutes → the screensaver; any key wakes Plasma and does nothing else.

### Live TV (board 07)

- [ ] The list opens on Arena Sports 1 in Sports. Rest on another channel → the preview follows after a moment.
- [ ] Left → the groups; resting on one shows its channels. In Favorites the numbers count from 1.
- [ ] Right → what's coming up on the channel; OK on a program → Remind Me and Record. The bell or red dot follows.
- [ ] Long-press OK (or Menu) on a channel → its options. Try Add to (or Remove from) Favorites, Hide Channel and Lock Channel.
- [ ] Type a number such as 108 → the box at the top left names the channel and tunes it after a moment. 999 → "No channel 999". The number of a channel you hid → "Channel 105 is hidden" with Unhide.
- [ ] Yellow sorts, red adds or removes a favorite, and blue opens Multiview.

### Player, zap banner and Multiview (boards 09–12, 25)

- [ ] OK on a channel → the player with the zap banner. Up and Down change channel.
- [ ] OK → the overlay on Play/Pause; Right from nothing → the overlay on the timeline, where Left and Right seek.
- [ ] Menu (or long-press OK) → Player Options.
- [ ] Back hides what's showing, then leaves the player.
- [ ] Left → the channel list.
- [ ] Tune Combat Arena (110) → the PIN screen. The sample PIN is **1234**; a wrong one shakes.
- [ ] Multiview (blue on Live TV, or the Multiview button in the player's overlay) → four tiles. The focused one has the sound; the toolbar changes the layout.

### Guide (board 08)

- [ ] The grid opens on what's on now on Arena Sports 1. Up and Down keep to the time; Left and Right move by program.
- [ ] Up from the top row → the days; moving onto a day shows it, and OK goes back to the grid. The sample guide only has tonight.
- [ ] OK on what's on now → the player. OK on a program coming up → Remind Me and Record; the bell or red dot appears on the cell.
- [ ] Long-press OK (or Menu) on what's on now → Record; on a program coming up → Remind Me and Record.

### Movies and Series (boards 13–16)

- [ ] Left → the categories; resting on one shows it. Up from the top row → Sort or the tabs.
- [ ] OK on a poster → its detail page. The tabs, the seasons and the episodes all move with the D-pad. Play opens a placeholder.

### Search (board 03)

- [ ] Type with the keyboard bar → results as you type. Down → the results; Up → back to the key.

### Apps (board 04)

- [ ] Long-press OK (or Menu) on an app → its options; Move lets you move it with Left and Right, and OK drops it. All Apps lists the rest.

### Who's Watching and the PIN (boards 05 and 25)

- [ ] The avatar at the top right → Who's Watching. A profile with a PIN asks for it (1234).

### Recordings and Catch-up (boards 17 and 18)

- [ ] Recordings: the tiles, folders, Coming Up and Series Rules; red asks before deleting.
- [ ] Catch-up (green on Live TV): days, channels and programs; CH + and − change the day.

### Settings (boards 19–24, 27–34)

- [ ] The gear in the top bar → Settings. Each row shows its detail on the left; OK opens its page or its choices.
- [ ] Portals & Playlists → Add Portal or Playlist (board 21). Type with the on-screen keyboard or the number keys; Connect reports the sample portal.
- [ ] Channels → Duplicate Numbers (board 30). OK on a conflict → Keep, Choose Another Number and Swap; Menu → the fix rule.
- [ ] Launcher & Home → Use Plasma as Home Screen (board 23) → Replace Google TV Home (board 24). Open Developer Options opens the TV's developer settings.
- [ ] Remote & Buttons → Phone Remote (board 33).

### Not in M0

- Real sources, playback, the guide's data and saved settings: M1 and later (brief section 8).
- Replacing the Home button: M5.

## Reporting a problem

Open an issue with the bug form. Say what you pressed, what you expected and what happened, and add a photo of the screen if it helps.
