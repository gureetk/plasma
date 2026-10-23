# Testing Plasma on an emulator or a TV

The unit and screenshot tests run on every pull request. This page is for the checks they can't do: Plasma on a real Android TV, or on the Android TV emulator, driven with a remote.

## Getting Plasma Dev

Every merge to `main` publishes **Plasma Dev** as a pre-release on the Releases page. A pull request's build is on its Build run instead: open the run from the pull request's checks and download **plasma-dev** under Artifacts, a zip holding the same APKs. Plasma Dev installs as its own app (`ca.gureet.plasma.dev`), next to the release app.

There's one APK per kind of processor, and a universal one that works on both:

| Device | APK |
|---|---|
| Chromecast with Google TV, and most TVs (32-bit Android) | `app-armeabi-v7a-dev.apk` |
| The Android TV emulator on an Apple-silicon Mac | `app-arm64-v8a-dev.apk` |
| Either | `app-universal-dev.apk` (bigger) |

- **Emulator:** in Android Studio's Device Manager, add a TV device with a 1080p screen and a Google TV system image.
- **TV:** turn on Developer options and Network debugging (or Wireless debugging), then connect from a computer on the same network with `adb connect <tv-ip>:5555`.

Then install it:

```sh
adb install -r app-armeabi-v7a-dev.apk
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

## Milestone 1: live TV from your own server

M1 plays live TV from an Xtream Codes login or an M3U playlist, with its guide. Until you add a source Plasma still shows the sample world above; once a source is in, Live TV, the player, the Guide, Home's picture and Favorite Channels use your channels, and the clock is the real one. The rest of Home, Movies, Series, Search, Catch-up and Recordings stay on sample data until their milestones.

Type the server's address and your login on the TV itself. They never go into a file, an issue or a message, and Plasma keeps them encrypted.

### Adding the source (board 21)

- [ ] Settings → Portals & Playlists → Add Portal or Playlist → Xtream Codes. Type a name, the server address with its port (`http://…:port`), the username and the password; Connect → "Connecting…", then "Connected" with when the account ends, its connections and the number of channels. Done → Portals & Playlists lists it.
- [ ] Try a wrong password first → "Wrong username or password.", and nothing is added.
- [ ] OK on the source → Refresh Now and Delete Source. Delete asks first, with Cancel focused.
- [ ] M3U, as a second check: delete the Xtream source, then add the server's playlist link as an M3U Playlist (`http://…:port/get.php?username=…&password=…&type=m3u_plus&output=ts`). Its live channels and guide arrive; movies and series stay out of Live TV. Delete it and add the Xtream login back for the rest.

### Live TV, the player and the Guide (boards 07–11)

- [ ] Live TV lists your groups with All Channels, Favorites and Recently Watched; resting on a channel plays it, with sound, in the preview after a moment.
- [ ] Lock a channel (its options) → its preview stays dark, and opening it asks for the PIN (1234 for now).
- [ ] Leave a channel playing full screen for longer than the TV's screensaver wait → it keeps playing.
- [ ] OK → full screen with sound, without the picture starting over.
- [ ] Up and Down zap; each channel starts within a second or two. Try the Small buffer (Settings → Playback → Buffer Size) and zap between three channels.
- [ ] Type a channel number → the number box, then the channel. In Favorites, 1 is the first favorite.
- [ ] Red (or the channel's options) adds a favorite; it shows in Favorites and on Home.
- [ ] The zap banner and the overlay show what's on now and next from your guide. The Guide shows the hours around now, and OK on what's on plays it.
- [ ] Pull the network cable or turn Wi-Fi off for a moment → "Reconnecting…", then the picture comes back by itself. Leave it off → a message with Try Again.

### Home (board 01)

- [ ] Home plays the last channel you watched, muted, behind the hero; Favorite Channels are yours.
- [ ] Close Plasma Dev and open it again → it picks up on the last channel. Restart the TV → the same, and your favorites, hidden channels and settings are still there.

### Picture and sound (boards 27 and 28)

- [ ] An interlaced sports channel (1080i, 50 Hz) moves smoothly, with Deinterlacing on Auto.
- [ ] Match Frame Rate on "On · seamless only": a 25 fps channel switches the TV to 50 Hz when it can do so without blanking. "On · always" switches even when the screen blanks for a moment. Neither happens on Home's muted picture.
- [ ] Settings → Audio shows how the sound leaves the TV and the formats it takes. A channel with Dolby Digital (AC-3) plays with sound; with Audio Output on Passthrough it reaches the TV as Dolby Digital (the TV's sound settings or info show it), and Decode to PCM sends it as PCM.
- [ ] Video Decoding on Software or "Hardware, copy back" still plays (used when hardware decoding misbehaves).

### Turning the TV off and on (brief 5.20)

- [ ] Leave a channel playing and turn the TV off. Within about a minute the server shows no connection from Plasma (your server's panel lists active connections).
- [ ] Turn it back on → the last channel plays again (When the TV Turns On: Last Channel). Try Plasma Home and TV Guide too.
- [ ] Turn it off and straight back on (within the minute) → it carries on without reconnecting.
- [ ] Open another app from the TV's home, then come back → the channel plays again.

## Milestone 2: the Guide, catch-up, reminders and search

M2 builds on M1, so test M1 first.

### Your remote (Settings › Remote & Buttons)

Try these first: they show which of your remote's keys reach Plasma.

- [ ] Remote Key Test → press each key on your remote, including any number, color and CH ± keys → each one that reaches Plasma shows with its name and codes, and whether Plasma uses it. Write down the ones that never show: Google TV keeps those, and Plasma can't see them. Back leaves.
- [ ] Learn a Key → press a key that showed "Plasma doesn't use it" → choose Channel Up → in the player, that key now changes channel. Back in Learn a Key, OK on it → Forget.

### The Guide (board 08)

- [ ] Settings → Portals & Playlists → your source → Refresh Now, and time it: the guide should be back within about a minute and a half (the Guide's amber line goes, or Settings shows it updated just now).

- [ ] Up to the days, then Right → each day shows its programs from your guide, with placeholders for a moment while they load. Back on Today, Down returns to what's on now.
- [ ] Yellow (or Group in the header) → All Channels, Favorites and your groups; picking one shows its channels, and Favorites are numbered from 1.
- [ ] Type a channel number → the Guide jumps to that channel's row, at the same time.

### Reminders (boards 07, 08 and 11)

- [ ] In the Guide, Right onto a program starting in a few minutes, OK → Remind Me → a bell on it. Live TV's Coming Up list shows the bell too, and OK there offers Cancel Reminder.
- [ ] A minute before it starts, the reminder comes up at the top right with Switch Now focused, on whatever Plasma screen is showing. OK switches to the channel.
- [ ] Another one, with Dismiss or Back → it goes, and focus is back where it was.
- [ ] One while watching full screen → the toast shows over the picture; Up, Down and the number keys still change channel; OK switches.
- [ ] Set a reminder, then restart the TV → the bell is still there.
- [ ] Set one, then open another app (or turn the TV off) until after it starts → nothing shows later, and the bell is gone.

### Catch-up and Start Over (boards 08–11 and 18)

These need a channel whose provider keeps an archive: it has ↺ on its past programs in the Guide.

- [ ] In the Guide, Left onto a program from earlier today with ↺ → OK → it plays from its start, marked Catch-up instead of LIVE. Past programs on a channel without an archive have no ↺.
- [ ] While it plays, OK → the overlay: Play/Pause pauses and plays on; Go Live goes back to live TV.
- [ ] Pause it for more than a minute, then play → it carries on from where it stopped.
- [ ] Let one play to its end (pick one that's nearly over) → live TV plays.
- [ ] Green on Live TV → Catch-up: your channels with an archive, and their programs → OK plays one from its start.
- [ ] Zap onto a channel with an archive whose program started more than 5 minutes ago → the zap banner says "Started … ago · OK to watch from the start". OK, then OK on Start Over → it plays from the start. Green does it in one press.
- [ ] Settings › Playback › Offer Watch from the Start → Off → the hint doesn't show any more.
- [ ] Search for a past program (marked CATCH-UP) → OK → it plays from its start.
- [ ] Note any channel whose catch-up starts at the wrong time, and by how much.

### Search (board 03)

- [ ] Search, then type part of the title of something on now → the results change as you type; what's on now comes first, with LIVE and its progress. OK plays the channel.
- [ ] Type a channel's name → what's on that channel now.
- [ ] Something coming up later → OK → Remind Me → the card says "Reminder set", and the Guide shows the bell.
- [ ] Open a result, then come back to Search → that search is under Suggestions, marked recent.
- [ ] A word that isn't in your guide → "No results for …".

## Milestone 3, first part: your sources

These build on M1 and M2. A second source (another login, or a playlist) helps with Set as Primary and Disable.

### A source's options (Settings › Portals & Playlists, board 20)

- [ ] OK on your source → Edit, Refresh Now, Test Connection, Account Info, Playback for This Source, Disable and Delete Source. A source that isn't first also has Set as Primary.
- [ ] Test Connection → the source says "Testing…", then "Connected · 42 ms" (your time) on the left. With the TV's network off → "Can't connect" and why; turn it back on.
- [ ] Account Info → your account's status, when it ends, its connections, the stream formats it offers and the server's time zone. A playlist says it has no account.
- [ ] Edit → the form is filled in, without the password, and can't change kind. Change only the name → Save → "Saved …"; favorites and channel numbers stay.
- [ ] Edit again with a wrong password → Save → "Couldn't Connect" and why. Back out: the source still plays as before.
- [ ] With two sources, Set as Primary on the second → it moves to the top.
- [ ] Disable → the source is dimmed and says "Off"; its channels leave Live TV, the Guide and Search. Enable → they're back.

### Playback for This Source

- [ ] Xtream: Stream Format → HLS → a channel from this source still plays (some providers only send one of the two). Back to MPEG-TS.
- [ ] Buffer Size → Extra Large · 12 s → this source's channels take longer to start; another source's don't. Same as Playback settings puts it back.
- [ ] Audio Output → Decode to PCM, on a channel with Dolby sound → your sound system shows PCM or stereo instead of Dolby; other sources still pass Dolby through.
- [ ] A playlist with catch-up that starts at the wrong time → Catch-up Time Offset by the difference → it starts on time.

### The guide's time offset (Settings › TV Guide)

- [ ] Time Offset for your source → +1 h → the Guide's programs move an hour later at once, without loading. Back to 0 h.
- [ ] The list of offsets scrolls past +5 h and −5 h.

### Two sources: merging, Switch Source and failover

Add your server a second time, as the playlist it hands out (`http://your-server:port/get.php?username=…&password=…&type=m3u_plus&output=ts`), as an M3U playlist.

- [ ] Live TV still lists each channel once, with your Xtream numbers.
- [ ] Player Options (long-press OK) › Switch Source → both sources, the Xtream one checked. Choose the playlist → the channel plays from it. Switch Source now shows the playlist checked; Back goes back to Player Options.
- [ ] Settings › Channels › Merge Duplicate Channels reads "On · N merged", N being the channels on both sources. Off: every channel shows twice, the playlist's copies moved up by 1000 (105 becomes 1105). Back On: once again.
- [ ] Settings › Portals & Playlists › your Xtream source → Disable: the channels play from the playlist. Enable again.
- [ ] If a channel stops on one source while the other still works, it carries on from the other, and "Switched to …" shows at the top right for a few seconds.

### Stalker portals (skip without one)

- [ ] Settings › Portals & Playlists › Add Portal or Playlist → Stalker Portal: the MAC address shows this TV's own, "Made for this TV · change". Register it with your provider (or type the one they registered), type the portal's address and Connect → "Connected to …" with the end date and channels.
- [ ] Live TV: the portal's channels, in its genres. Zapping plays each one within a second or two.
- [ ] Guide: the portal's programmes for the next three days.
- [ ] A channel with catch-up: ↺ on a programme from earlier today plays it from its start.
- [ ] Leave a channel playing for 15 minutes → it keeps playing.
- [ ] A MAC address the portal doesn't know → "The portal rejected this MAC address…".
- [ ] OK on the portal → Account Info shows its status, end date and MAC address.

## Reporting a problem

Open an issue with the bug form. Say what you pressed, what you expected and what happened, and add a photo of the screen if it helps.
