# Testing Plasma on an emulator or a TV

The unit and screenshot tests run for every pull request: on GitHub for those into `main`, and on the developer's machine, before pushing, for those stacked on another branch. This page is for the checks they can't do: Plasma on a real Android TV, or on the Android TV emulator, driven with a remote.

## Getting Plasma Dev

Every merge to `main` publishes **Plasma Dev** as a pre-release on the Releases page. A pull request into `main` has its build on its Build run instead: open the run from the pull request's checks and download **plasma-dev** under Artifacts, a zip holding the same APKs, kept for a week while the free plan's 500 MB of artifact storage has room. For a branch's APKs, build it by hand under Actions › Build › Run workflow (or `gh workflow run build.yml --ref <branch>`): they go on a pre-release of their own, **Plasma Dev test build** with the build's number and the branch, which doesn't use the artifact storage. Settings › About › Plasma Update offers the newest build, whether it's from `main` or a test build, so a test build can be installed from the TV. The newest pull request in a stack has everything below it. Plasma Dev installs as its own app (`ca.gureet.plasma.dev`), next to the release app.

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

Type the server's address and your login on the TV, or on your phone with Add a Source's QR code ("Add a Source from your phone", under Milestone 3). They never go into a file, an issue or a message, and Plasma keeps them encrypted.

### Adding the source (board 21)

- [ ] Settings → Portals & Playlists → Add Portal or Playlist → Xtream Codes. Type a name, the server address with its port (`http://…:port`), the username and the password; Connect → "Connecting…", then "Connected" with when the account ends, its connections and the number of channels. Done → Portals & Playlists lists it.
- [ ] Try a wrong password first → "Wrong username or password.", and nothing is added.
- [ ] OK on the source → Refresh Now and Delete Source. Delete asks first, with Cancel focused.
- [ ] M3U, as a second check: delete the Xtream source, then add the server's playlist link as an M3U Playlist (`http://…:port/get.php?username=…&password=…&type=m3u_plus&output=ts`). Its live channels and guide arrive; movies and series stay out of Live TV. Delete it and add the Xtream login back for the rest.

### Live TV, the player and the Guide (boards 07–11)

- [ ] Live TV lists your groups with All Channels, Favorites and Recently Watched; resting on a channel plays it, with sound, in the preview after a moment.
- [ ] Lock a channel (its options) → with no PIN yet, Plasma has you choose one first (see Milestone 7); then its preview stays dark, and opening it asks for the PIN.
- [ ] Leave a channel playing full screen for longer than the TV's screensaver wait → it keeps playing.
- [ ] OK → full screen with sound, without the picture starting over.
- [ ] Up and Down zap; each channel starts within a second or two, with a ring turning while it opens. Zap again before one has started: Plasma carries on. Try the Small buffer (Settings → Playback → Buffer Size) and zap between three channels.
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
- [ ] Change channel and turn the TV off at once, then on within the minute → the channel you changed to plays, not the one before.
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

- [ ] In the Guide, Left onto a program from earlier today with ↺ → OK → it plays from its start, marked Catch-up instead of LIVE, with the overlay's controls up. Past programs on a channel without an archive have no ↺.
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
- [ ] Player Engine → mpv: this source's channels start in mpv (Player Options shows it); another source's still start in VLC. Back to Same as Playback settings.
- [ ] Use Stream Hub → Off: a channel from this source still plays. Back to Same as Playback settings.
- [ ] User Agent: OK opens a page with the field, the keyboard and Save. Type something, Save: the row shows it, and the source's channels still play (or don't, if the server checks it). Empty it and Save: back to VLC (a MAG box for a portal).
- [ ] Referrer and Extra Headers: two fields. Back leaves without saving.
- [ ] A playlist with multicast (udp://) channels, if you have one: udpxy Proxy → your udpxy's address and port → those channels play over Wi-Fi.

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
- [ ] Remember What Works: Switch Source → choose the playlist. Zap away and back: the channel starts on the playlist. Switch Source lists the playlist first, with "Works best" and "Remembered today" under it.
- [ ] Forget What Works, at the end of that list → the next tune starts on your Xtream source again.
- [ ] Settings › Portals & Playlists › Remember What Works per Channel → Off: choosing the playlist in Switch Source plays it, but the next tune starts on your Xtream source, and no "Works best" shows. Turn it back On.

### Channel numbers and Duplicate Numbers (boards 29 and 30)

Clashes come from two sources that give different channels the same number, or one source that numbers two channels alike. The same channel on both sources is merged instead, so your Xtream server and its own playlist may have none.

- [ ] Settings › Channels: Duplicate Numbers reads "N fixed", Merge Duplicate Channels "On · N merged", and Hidden Channels and Hidden Groups count what you've hidden.
- [ ] Duplicate Numbers: each row shows the number, the channel that kept it, and the channel that moved, "Now 1108". With no clashes: "No two channels share a number."
- [ ] OK on a row → Swap Numbers: the two trade numbers. Typing the old number on Live TV tunes the other channel now.
- [ ] Choose Another Number: a number that's taken says "… is taken by …"; a free one → Use. Live TV lists the channel under it.
- [ ] Portals & Playlists › Refresh Now on both sources: the numbers you chose stay.
- [ ] Menu → Next Free Number: the moved channels get the next free numbers (108 → 110 when 109 is taken). Menu → Add 1000 again.
- [ ] Merged tab: each merged channel with its sources in play order. Unmerge one: it leaves the list, and Live TV lists it from both sources, the second under its own number. Merge Duplicate Channels off and on: it's merged again.
- [ ] Channel Numbers → In playlist order: the channels number 1, 2, 3… across your sources. Back to From your playlists: the playlists' numbers again.
- [ ] After a refresh that finds a new clash, "1 channel got a new number · Settings › Channels" shows at the top right.

### The backup player, mpv (board 10)

- [ ] Player Options (long-press OK) › Switch Player reads "VLC → mpv". OK: the channel plays on in mpv within a second or two, with picture and sound and no "Reconnecting…". The row then reads "mpv → VLC". Check this on the TV itself: the emulator is 64-bit, the TV 32-bit.
- [ ] Zap away and back: that channel starts in mpv, with its picture. Switch it back to VLC.
- [ ] Hold CH+ through a few channels, one of them in mpv: each one you stop on has a picture.
- [ ] Settings › Playback › Buffer Size → Extra Large: channels still play in VLC, with no "VLC couldn't play…". Set it back.
- [ ] In mpv, check the picture's shape, a 50 fps sports channel and Dolby Digital passthrough.
- [ ] Settings › Playback › Player Engine → mpv: every channel starts in mpv, with its picture, however many times you zap. Set it back to VLC.
- [ ] If a channel won't play in VLC (an error, or no picture after 15 s), Plasma plays it in mpv and says "VLC couldn't play … Playing it with mpv." Note which channel it was.
- [ ] Settings › Playback › Forget Players Chosen per Channel → Forget: "Forgot the player chosen for 1 channel." That channel starts in VLC again.

### Channel volume levels (brief 5.4.6)

For the passthrough steps you need a receiver or soundbar, and a channel with Dolby Digital or DTS.

- [ ] Live TV: Menu on a quiet channel › Volume Level (it reads "0 dB"): a bar at the bottom, "Volume level · …" with the steps from −6 to +6. ▶ three times: the channel gets louder with each step and the bar reads "+3 dB". OK keeps it.
- [ ] Zap away and back: the channel comes back at +3 dB, with no jump in loudness at the start.
- [ ] Player Options › Switch Player to mpv: the channel is as loud as it was in VLC. Switch back. Do the same at +6 dB and −6 dB, and note any channel that sounds distorted at +6 dB.
- [ ] Volume Level again, ◀ twice, then Back: the level goes back to +3 dB.
- [ ] Settings › Playback › Audio: Channel Volume Levels reads "1 channel". OK lists it with "+3 dB"; OK on it › Reset: it's back to 0 dB, at once if it's playing. Reset All asks first.
- [ ] With Audio Output on Passthrough and a Dolby Digital or DTS channel playing to the receiver: Volume Level says "Your receiver sets the volume while audio passes through…", with Decode This Channel. OK: the channel starts again, decoded, and the bar shows its steps. Settings › Audio › Channel Volume Levels lists it as "decoded"; Reset sends its sound through to the receiver again.

### Channel options: Switch Source, Change Number and Channel Info (brief 4.35)

For Switch Source you need a channel on both sources (see "Two sources" above).

- [ ] Live TV: Menu on the channel playing in the preview › Switch Source reads the source it plays from. OK: both sources, that one ticked. Choose the other: the preview plays from it, and Player Options › Switch Source shows it ticked.
- [ ] Menu on another channel on both sources, one that isn't playing › Switch Source → the playlist. Move onto the channel: it plays from the playlist.
- [ ] Switch Source on a channel that remembers a source lists that one first, with "Works best". Forget What Works, at the end → the next tune starts on your Xtream source again.
- [ ] A channel from one source only has no Switch Source.
- [ ] Change Number on Arena Sports 1 (or any channel): the number pad reads "… · now 105". Type a number another channel has → "… is taken by …". A free one → Use: Live TV lists the channel under it, and typing that number tunes it.
- [ ] Portals & Playlists › Refresh Now: the channel keeps the number you gave it.
- [ ] Settings › Channels › Channel Numbers → In playlist order, then From your playlists: the channel is back on its own number.
- [ ] Channel Info: the channel's number, group, sources, guide id and catch-up days. OK or Back closes it.

### Audio and Subtitles, Picture Size and the stream info (board 10)

- [ ] On a channel with more than one sound track (two languages, or 5.1 and stereo): Player Options › Audio and Subtitles lists them, the one playing ticked, named like "English 5.1" with their format. Pick another: the sound changes within a second or two. Zap away and back: the channel starts with the one you picked.
- [ ] Settings › Audio › Preferred Language → Original, then a channel whose first track isn't English: it keeps that track. Back to "English, then original": it starts in English.
- [ ] A channel with an audio description track, if you have one: it starts on the other track (Skip Audio Description).
- [ ] On a channel with DVB or teletext subtitles: pick them in Audio and Subtitles. They show over the picture, in VLC and, after Switch Player, in mpv. Subtitles Off hides them. Settings › Playback › Subtitles and Teletext → On: they show by themselves on channels that have them. Note any channel whose subtitles don't show.
- [ ] OK in the player, then the sound or subtitle pill: Audio and Subtitles opens.
- [ ] Picture Size → Zoom on a 4:3 or letterboxed channel: the picture fills the screen. Try Fill, 16:9, 4:3 and Fit. Zap away and back: the channel keeps its size, other channels fit, and Live TV's preview always fits.
- [ ] The line at the bottom of Player Options shows the engine, the picture's size, frame rate and format, the sound's format with "passthrough" or "decoded", and the bitrate on the right.
- [ ] If your remote has Audio or Subtitles keys: Audio moves to the next sound track with a notice ("English 5.1 · passthrough"), and Subtitles turns them on and off.
- [ ] The dot at the bottom left of Player Options is green while the channel plays, amber while it starts, and red with an error.
- [ ] If VLC keeps stopping to buffer on a channel that plays smoothly on its own source elsewhere: after the third time within a minute, a notice says mpv has taken over, and Player Options' dot stays amber for 30 s. Note any channel where this happens.

### Sleep Timer (board 10)

- [ ] Player Options › Sleep Timer: Off is ticked, and the list has 30, 60, 90 and 120 Minutes and End of Program, with what's left of the program beside it. Pick 30 Minutes: the row reads "30 min left".
- [ ] Pick End of Program on a program that ends in a few minutes (or wait out most of a 30-minute timer): a minute before, a notice at the top right offers Keep Watching and Stop Now. Keep Watching: the row reads Off again.
- [ ] Let one run out: the picture and sound stop, and the screen goes dark with "Sleep Timer stopped the channel", which fades after ten seconds. If the TV is set to turn itself off after a while (on Google TV, Settings › System › Power & energy), it does.
- [ ] Press any button: the channel plays again.
- [ ] Set a timer, then go to Home: the notice and the dark screen come there too.

### Moving favorites, and removing them on Home (board 04's move mode)

You need four or more favorites: Red on a channel in Live TV adds it.

- [ ] Live TV › Favorites › Menu on a channel › Move: it lifts, with arrows above and below its number, and the other channels dim. Down twice: it moves two places, the numbers follow, and the list stays still around it. OK: it stays there.
- [ ] Home's Favorite Channels row has the new order, and so does Live TV's Favorites after a restart. Up and Down in the player, after tuning from Favorites, go in the new order.
- [ ] Move again, Down a few times, then Back: the channel goes back where it was, with focus on it.
- [ ] With more favorites than fit on the screen: Move one near the bottom and press Down until the end. The list scrolls only to keep two channels below it in view.
- [ ] Home › Down to Favorite Channels › Menu on a favorite › Move: the heading reads "Moving … · Press OK to drop it here", the favorite lifts with arrows beside it, and the others dim. Right twice, OK: it stays there, and Live TV's Favorites has the same order. Back during a move puts it back.
- [ ] Home › Menu on a favorite › Remove from Favorites: it leaves the row and focus moves on to the next one. It's gone from Live TV's Favorites too, and stays gone after a restart.
- [ ] With two sources merged (see "Two sources" above): Red on a favorite that's on both sources removes it, and Red again adds it back once.
- [ ] Apps › Menu on an app in "On Home" › Move, with more apps on Home than fit: Right carries it to the end, and the row scrolls only to keep the next app in view. Back puts it back, in view.

### Add a Source from your phone (board 21)

Your phone needs to be on the same Wi-Fi as the TV.

- [ ] Settings › Portals & Playlists › Add Portal or Playlist: the card on the right shows a QR code, the TV's address with `:8484`, and a six-character code.
- [ ] Scan the QR code with the phone's camera: a form opens in the phone's browser, with the TV's name at the top, on the kind of source the TV's form is on. The tabs switch kinds.
- [ ] Fill in your Xtream server on the phone and send it: the phone says "Sent to …". The TV's form fills in, the card says "Received from your phone", and Connect has focus. Connect works.
- [ ] Open the address in the phone's browser instead and type the code: the same form. Five wrong codes in a row → the TV shows a new code.
- [ ] Leave Add a Source, then reload the page on the phone: it doesn't load.

### Add a Source › Advanced (brief 4.21)

- [ ] Add Portal or Playlist › Stalker Portal › Advanced (OK): "Advanced · Stalker Portal" with STB model, Time zone, Serial number, Device ID 1 and 2, Signature, User agent, Login and Password. Back returns to the portal's fields, on Advanced.
- [ ] OK on STB model: a list on the right; pick one, and the Advanced row's summary starts with it.
- [ ] On the keyboard, the key before Z switches to capitals (ABC), then symbols (#+=), then back (abc). Type a password with capitals and a symbol.
- [ ] Xtream Codes › Advanced: Stream format and Extra guide URL. With HLS picked, Connect adds the server playing HLS (Playback for This Source shows it).
- [ ] M3U Playlist › Advanced: User agent and Refresh channels ("Same as Portals & Playlists", or one of its choices).
- [ ] Portals & Playlists › Refresh → Every 6 Hours: a source last refreshed more than 6 hours ago refreshes within half an hour: "Last updated" on the source says when.
- [ ] Edit on a source shows its Advanced as it was saved, and Save keeps it.
- [ ] If you have a portal that wants its box's device IDs, signature or a login, type them here and Connect.

### Stalker portals (skip without one)

- [ ] Settings › Portals & Playlists › Add Portal or Playlist → Stalker Portal: the MAC address shows this TV's own, "Made for this TV · change". Register it with your provider (or type the one they registered), type the portal's address and Connect → "Connected to …" with the end date and channels.
- [ ] Live TV: the portal's channels, in its genres. Zapping plays each one within a second or two.
- [ ] Guide: the portal's programmes for the next three days.
- [ ] A channel with catch-up: ↺ on a programme from earlier today plays it from its start.
- [ ] Leave a channel playing for 15 minutes → it keeps playing.
- [ ] A MAC address the portal doesn't know → "The portal rejected this MAC address…".
- [ ] OK on the portal → Account Info shows its status, end date and MAC address.

## Milestone 4: movies and series

### Movies and Series (boards 13 and 15)

- [ ] Add your Xtream server, or use Settings › Portals & Playlists › your source › Refresh Now. Within a few minutes, Movies lists Recently Added and your provider's categories, with counts, and Series lists New Episodes and its categories.
- [ ] Move down the categories: each one's posters show. OK goes into the grid, and yellow sorts it.
- [ ] The line under a focused poster has its year and category, under its name; a name too long for the line ends in "…".
- [ ] A movie your provider has no poster for shows its name on the plate instead. A server that gives every movie the same placeholder picture (yours does) shows names, not the placeholder.
- [ ] Search: type part of a movie's or a series' name: it's listed under the programs, and OK opens its page.
- [ ] With only a playlist, Movies says "No movies yet".
- [ ] Long-press OK (or Menu) on a poster: Add to My List puts it in My List, under Recently Added; the same sheet takes it off again. Mark as Watched and Mark as Unwatched work there too.

### Movie and series pages (boards 14 and 16)

- [ ] Open a movie: its name, poster (blurred, behind) and year show at once, then its genres, running time, badges, plot and cast as its details arrive. Play has focus.
- [ ] Trailer shows only when the movie has a trailer and the TV has the YouTube app; it opens the trailer in YouTube.
- [ ] + adds the movie to My List and turns into ✓; open Movies › My List to see it.
- [ ] More Like This lists others from its category; OK opens one. Cast & Crew, Audio & Subtitles and File Info show what the provider says about the file (for example "HEVC Main 10 · 3840 × 2160").
- [ ] Open a series: "Loading episodes…" shows briefly the first time, then its seasons and episodes, with any specials last. Resume (or Play) has focus once they're in.
- [ ] Long-press OK (or Menu) on an episode: Mark as Watched puts a check on it; green marks the whole season, and again unmarks it.
- [ ] With the network off, a series you haven't opened before says its episodes can't load; one you have opened shows them as they were.

### Playing movies and episodes

- [ ] Play a movie: a ring turns in the middle while it starts, then the picture comes up with the overlay (title, year, badges, the bar); it hides after 5 seconds.
- [ ] Left and Right jump 10 seconds; hold them and the jumps grow; the picture moves once you let go. ⏩ and ⏪ jump 30 seconds; the Play/Pause key pauses.
- [ ] Back after a few minutes, then Resume on its page: it carries on where you left it, and the page says what's left. From the Beginning starts it again.
- [ ] Menu: Audio and Subtitles, Picture Size, Switch Player (it carries on in mpv where it was) and Sleep Timer.
- [ ] A 24 fps movie switches the TV to 24 Hz, with Match Frame Rate on.
- [ ] Turn the TV off during a movie, then on: it carries on where it was.
- [ ] An episode near its end shows Up Next at the bottom right; let it end and it counts down from 10 and plays the next one. Back on the card stops that. Settings › Playback › Auto-Play Next Episode off: the card stays, without counting down.
- [ ] Watch past 92 % of a movie: its poster and page say it's watched, and Play starts it from the beginning.

### Continue Watching (boards 02, 13 and 15)

- [ ] Start a movie and an episode, and stop part of the way: both show at the start of Home's Continue Watching, the latest first, with what's left. OK carries on playing.
- [ ] Movies and Series have a Continue Watching category after Recently Added (or New Episodes); a series says "Resume S2, E4 · 18 min left".
- [ ] Finish an episode: its series shows the next one, "Up next". Settings › Launcher & Home › Home Rows › Continue Watching › Show Next Episodes off: it doesn't.
- [ ] Menu on an item › Remove from Continue Watching: it goes, and stays gone until you play it again.
- [ ] With nothing started, the row says what goes there.
- [ ] Home's New in Movies lists your provider's newest movies; See All opens Movies.

### A portal's movies and series (skip without a Stalker portal)

- [ ] Add the portal, or Refresh Now on it: Movies and Series list its categories. A portal hands out 14 titles at a time, so a big library takes a while.
- [ ] Open a movie: its plot, cast, running time and rating show at once. Play: it plays, and Resume carries on where you left it.
- [ ] A series whose episodes the portal lists with it opens on one season; one whose seasons the portal gives apart shows "Loading episodes…", then its seasons. Each episode plays.
- [ ] Leave a movie playing for 15 minutes: it keeps playing.

### The Android player (Media3)

- [ ] A Dolby Vision or HDR10+ movie (one your server probed as such): File Info's first line reads "Plays with the Android player · Dolby Vision". Play it: the TV switches to Dolby Vision or HDR, and the colors are right, not pink and green.
- [ ] An ordinary movie's File Info reads "Plays with VLC". Settings › Playback › HDR and Dolby Vision Movies → Same as Player Engine: the Dolby Vision movie reads "Plays with VLC" too. Set it back.
- [ ] In any movie, Player Options › Switch Player until it reads "mpv → Android player", then OK: it carries on where it was. Seeking, pausing, Picture Size and Audio and Subtitles work; turn subtitles on and they show at the bottom of the picture, white with a shadow.
- [ ] On a live channel, Switch Player to the Android player: the channel plays on with picture and sound. Zap away and back: it starts in the Android player. Forget Players Chosen per Channel puts it back on VLC.
- [ ] Settings › Playback › Player Engine → Android player: live channels start in it, however many times you zap. Set it back to VLC.
- [ ] Volume Level on a channel playing in the Android player: −6 dB is quieter, +6 dB louder.
- [ ] Settings › Playback › Advanced › Tunneled Playback on: a movie in the Android player still plays, with the sound in step. Turn it off again.

## Milestone 5: the launcher

### The Home button (boards 22 and 23)

Settings › Launcher & Home › Use Plasma as Home Screen opens Home Screen setup. This one is for the TV itself; the emulator's launcher may behave differently.

- [ ] This TV shows the TV's model and Android version, and "Home button opens Google TV Home".
- [ ] Home Button Override reads Off. OK → a sheet says what Plasma sees and never reads. Open Accessibility Settings → the TV's Accessibility settings. Turn Plasma Dev on there and allow it. (If the switch is grayed out, see below.)
- [ ] Back in Plasma, Home Button Override reads On, and This TV "Home button opens Plasma with Home Button Override".
- [ ] Open another app (YouTube, say) and press Home → Plasma opens on Home, the channel playing behind it. Google TV Home may show for a moment first.
- [ ] Settings › Launcher & Home › Home Button → Live TV, last channel. Press Home in another app → the last channel plays full screen. Try Channel list and TV Guide, then set it back to Plasma Home.
- [ ] In Plasma, on the Guide or a movie's page, press Home → Plasma Home.
- [ ] Turn the TV off and on → When the TV Turns On decides (the last channel by default), not Home Button.
- [ ] Restart the TV (Settings › System › Restart) → Plasma opens by itself as the TV starts, on the last channel.
- [ ] Holding Home still does what the TV does; Long-Press Home reads "System default".
- [ ] With Projectivy's override on too, This TV says so in amber. Turn one of them off.
- [ ] OK on Home Button Override while it's on → Turn Off → it reads Off, and Home opens Google TV Home again.
- [ ] Default Home App reads "Not available on this TV" on Google TV. (On a box that asks which app Home opens, it reads Set Up: OK, pick Plasma Dev, then Always → Home opens Plasma with no flash.)
- [ ] Replace Google TV Home's "Prefer a computer?" commands name `ca.gureet.plasma.dev/ca.gureet.plasma.HomeActivity` and the TV's launchers (on Google TV, `com.google.android.apps.tv.launcherx` and `com.google.android.tungsten.setupwraith`).

If the switch in Accessibility is grayed out (Android 13 and later, after installing the APK from a file): the sheet says it may be, and `adb shell appops set ca.gureet.plasma.dev ACCESS_RESTRICTED_SETTINGS allow` from a computer releases it.

With Wireless debugging on and Plasma paired (see "Replace Google TV Home from the TV"), Home Button Override can also be turned on without Accessibility, grayed out or not:

- [ ] Turn the override off, then OK on Home Button Override → Turn On over Wireless Debugging → "Home Button Override is on", and the row reads On. Other Accessibility services that were on stay on.

### Replace Google TV Home from the TV (board 24)

This one changes the TV's Home app. Restore Google TV Home undoes it, and so do the commands under "Prefer a computer?" (`pm enable` for the same packages). The TV needs Wi-Fi for wireless debugging.

- [ ] Home Screen setup › Replace Google TV Home: step 1 is current, with "Waiting for wireless debugging". Open Developer Options opens the TV's developer settings (turn on Developer options first: Settings › System › About, OK on Android TV OS build seven times).
- [ ] Turn on Wireless debugging: back in Plasma, step 1 shows Done and step 2 is current.
- [ ] Scan the QR code on Plasma's screen with your phone: its page asks for a pairing code and a port. Then, on the TV, in Wireless debugging, choose Pair device with pairing code, and type what it shows on the phone: the six-digit code, and the port (the number after the colon). The TV lists "Plasma" under Paired devices; back in Plasma, step 2 shows Done.
- [ ] A wrong code says "That code didn't work. Pair again with the new one."
- [ ] Replace Google TV Home: "Done. Press Home to try it." Press Home → Plasma, with no Google TV Home first. Turn the TV off and on, and restart it → Plasma.
- [ ] Turn Off Home Button Override, offered once it's done, turns the override off.
- [ ] Home Screen setup now reads Replace Google TV Home ● On, and This TV "Home button opens Plasma. Google TV Home is turned off."
- [ ] After Replace, turn Google TV Home back on from a computer (the `pm enable` commands under "Prefer a computer?"), then restart the TV and open Plasma: it says "Google TV Home Is Back". Replace It Again opens these steps again; Back leaves it until the next restart.
- [ ] With Wireless debugging still on: Restore Google TV Home → it asks first → Restore → "Google TV Home is the Home app again." Press Home → Google TV Home.

### Your apps (board 04)

Home's Apps row and Apps show the TV's own apps, in the sample world too.

- [ ] Home's Apps row has the first six of your apps made for TV, A to Z, with their banners. Apps (the Apps tab, or All Apps at the end of the row) lists the rest: the TV apps A to Z, then the phone apps. Plasma isn't among them.
- [ ] An app without a banner shows its icon on grey, with its name under it.
- [ ] OK on an app opens it, from Home and from Apps.
- [ ] On Apps, Menu on an app in On Home → Move: Left and Right carry it, OK drops it. Home's Apps row has the new order, and keeps it after a restart.
- [ ] Remove from Home and Add to Home move an app between On Home and All Apps.
- [ ] Hide: the app goes, and focus moves to the next one. Settings › Launcher & Home › Hidden Apps (after Clock) reads "1 app"; OK lists it, and choosing it shows it again.
- [ ] App Info opens the TV's page for the app. Uninstall asks the TV's own question (choose Cancel).
- [ ] Install an app from the Play Store, then come back to Apps: it's in All Apps. Uninstall one: it's gone.
- [ ] Inputs shows only on a TV with HDMI inputs (not on the Chromecast or the emulator). There, OK lists them by name, and choosing one switches the TV to it.
- [ ] Device Settings opens the TV's settings. Get More Apps opens the Play Store.
- [ ] Settings › Launcher & Home › Show Android Apps → Off: the Apps tab and Home's Apps row go. Turn it back on.

### The screensaver (board 06)

- [ ] Settings › Launcher & Home › Screensaver → After 5 Minutes. Leave Plasma on Home without touching the remote: after five minutes the screensaver shows, its picture slowly moving, then fading to the next every three minutes. Any key brings Home back, and does nothing else.
- [ ] It never shows while a channel or a movie plays. Never turns it off. Set it back to After 10 Minutes.
- [ ] Use as the TV's Screensaver reads Off. With Wireless debugging on and Plasma paired (from Replace Google TV Home): OK → it explains → Set It Now → "Plasma's screensaver is the TV's now", and the row reads On.
- [ ] Open another app and leave the TV idle for the TV's own wait (Settings › System › Ambient mode on Google TV): Plasma's screensaver shows, with the real time. Any key ends it.
- [ ] Use as the TV's Screensaver → Use the TV's Own Screensaver → "The TV's own screensaver is back."
- [ ] With Wireless debugging off, Set It Now says "Couldn't reach the TV…".

### Continue Watching from other apps (board 32)

This needs a source added (the sample world keeps the board's row) and an app that adds to Android's Play Next row; YouTube and many TV apps do. Watch something in it for a minute first.

- [ ] Settings › Launcher & Home › Home Rows › Continue Watching: Include Other Apps reads Off. OK → it explains → Turn On → Android asks to let Plasma read TV listings → Allow. The sources card reads "● Allowed" and counts the apps and items.
- [ ] Home's Continue Watching has the other app's item, with the app's name on it, among Plasma's own by when each was watched. OK opens it in its app.
- [ ] Menu on it: Hide from Plasma takes it away for good; Hide All from This App takes the app's items away. Continue Watching › Apps lists the app as Hidden; choosing it shows it again.
- [ ] If Android never asks (or you said no), Include Other Apps offers Ask Again and Allow over Wireless Debugging (with Wireless debugging on and Plasma paired): it reads "● Allowed" after.

### Jellyfin in Continue Watching (board 32)

This needs a source added and your Jellyfin server on the same network, with something part-watched on it.

- [ ] Settings › Launcher & Home › Home Rows › Continue Watching › Jellyfin Server reads Not Connected. OK → "Looking for Jellyfin servers…", then your server, with its address. (If it isn't found, Enter Address and type its address, with or without `http://` and the port.)
- [ ] Choose it: a six-digit Quick Connect code. In the Jellyfin app on your phone (or Jellyfin in a browser), open your profile, then Quick Connect, and enter the code. Within a few seconds, "Signed in to … as …", and the row reads Connected with the server and your name. The sources card counts the items.
- [ ] Forget Server → confirm: the row reads Not Connected. Sign in again with Use Password Instead, and try a wrong password first: it says so and offers Try Again with the username kept.
- [ ] Home's Continue Watching has your Jellyfin items, tagged JELLYFIN, among the rest by when each was watched: a movie reads "Jellyfin · 52 min left", an episode "S2, E4 · 18 min left", and with Show Next Episodes on, the episode after one you finished reads "Up next".
- [ ] OK on a movie plays it in Plasma from where you left it, with the backdrop while it opens. The player's bar, seeking, Switch Player and Audio and Subtitles work as for your provider's movies; a Dolby Vision or HDR10+ file plays in the Android player.
- [ ] While it plays, the Jellyfin app on your phone (or Jellyfin in a browser) shows Plasma playing it on this TV. Stop after a few minutes: Jellyfin's own resume point has moved to where you stopped, and Home's row says so.
- [ ] An episode's end counts down to the next one, as for your provider's series.
- [ ] Settings › … › Continue Watching › Play Jellyfin Items In → The Jellyfin app: with the app on the TV, OK opens the item's page there instead; without it, the sheet says items play in Plasma.
- [ ] Menu on a Jellyfin item: Resume (Open with the Jellyfin app chosen), and Hide from Plasma, which takes it away until you watch it again (anywhere). There's no Hide All on Jellyfin items.
- [ ] With the Jellyfin app on the TV and Include Other Apps on, an item from the Jellyfin app's own Play Next entries shows once, as Jellyfin's.
- [ ] Turn the server off, then OK on a Jellyfin item: the player says Jellyfin is offline, and back on Home the items say "Jellyfin is offline". They stay for an hour, then go. Turn the server back on and open Home: they come back.

### Reminders over other apps

This needs a source added, and Wireless debugging on with Plasma paired (from Replace Google TV Home), unless the TV has its own "display over other apps" page.

- [ ] Settings › TV Guide › Show over Other Apps reads "Not allowed yet". OK → it explains → Allow over Wireless Debugging → "Reminders can show over other apps now", and the row reads Allowed.
- [ ] Set a reminder for a programme starting in a few minutes, then open another app (YouTube, say). A minute before the start, the reminder shows at the top right over it.
- [ ] Switch Now opens Plasma on that channel, full screen. Try again with Back: the reminder goes, and the app underneath has the remote again.
- [ ] With Plasma in front, the reminder shows as before.

### Keep watching in a small window

This needs a TV with picture-in-picture, and a source added. Try it with Home Button Override, and again with Plasma as the TV's Home app.

- [ ] Settings › Launcher & Home › Keep Watching in a Small Window reads "When opening another app". On a TV without picture-in-picture the row isn't there, and opening an app stops the channel as before.
- [ ] With a channel in Home's preview, OK on an app in the Apps row: the channel moves to a small window in a corner, with its sound, and the app opens. It doesn't stop or reconnect on the way.
- [ ] The same from Apps, from Device Settings, from an Inputs tile, and from another app's item in Continue Watching.
- [ ] The window's menu (on most TVs, hold Home, or the TV's picture-in-picture button): Previous Channel and Next Channel change the channel, through the list you last watched, past hidden channels. Play/Pause stops it and plays it again at the live edge.
- [ ] Play a video in the app in front: the small window goes quiet but keeps playing. Pause, then Play, in its menu brings the sound back.
- [ ] Press Home: the window closes, and the channel carries on in Home's preview, muted as before. If you changed the channel in the window, Home's preview has the new one.
- [ ] Open the window from its menu (Full Screen): Plasma's player opens full screen on the channel, with its sound.
- [ ] Close the window from its menu: the channel stops. Opening Plasma plays it again.
- [ ] Turn the TV off and on with the window open: When the TV Turns Off applies, as when Plasma's in front.
- [ ] Keep Watching in a Small Window → Never: opening an app stops the channel, as before. Set it back.

### Plasma updates (Settings › About)

This needs `update.json` where the TV can read it: Plasma Dev reads the repository's `dev` pre-release, which works once the repository is public (see the progress notes).

- [ ] Settings › About › Plasma Update reads "Not checked yet" or "Up to date", with when Plasma last looked. OK looks now: "Checking…", then "Up to date".
- [ ] Once a newer Plasma Dev is published from `main`, OK finds it: "… is ready", with a green dot on About and on the top bar's Settings button.
- [ ] OK on it: the notes, then Install Now. If Android doesn't let Plasma install apps yet, the sheet explains first: Allow over Wireless Debugging (with Plasma paired), or the TV's own page. Then Install Now again.
- [ ] It downloads ("Downloading · 42%"), then the TV asks to confirm. Confirm: Plasma closes, updates, opens again and says "Plasma is updated to …". Your sources, favorites and settings are all still there.
- [ ] Try again with Cancel at the TV's question: the row reads "Not installed", and Install Now asks again without downloading again.
- [ ] With Check for Updates on, Plasma looks by itself once a day; Off, it doesn't.

## Milestone 6: recording, rewind and local catch-up

### Pausing and rewinding live TV (boards 09 and 31)

- [ ] Settings › Playback › Rewind and Local Catch-up: Rewind Buffer reads "When I pause · 1 hour". The Storage card shows This TV (or your USB drive, with Save To on USB drive) and its free space.
- [ ] On a channel, OK → Play/Pause: the picture stops, and the timeline's bubble says how far behind live it is, counting up. Wait 10 minutes, then Play: it carries on from exactly where it stopped, with nothing missing and no reconnecting.
- [ ] Behind live, Left and Right on the timeline move 10 seconds a press; a run of presses opens the stream once, where it stops. Go Live goes back to live TV.
- [ ] Before any pause, Left on the timeline goes back a few seconds at most; after a pause, as far back as the pause.
- [ ] Rewind Buffer → Always · 1 hour: tune a channel, watch for 5 minutes, then Left on the timeline goes back as far as when you tuned in. Change channel: the buffer starts again, and Storage's Rewind figure drops.
- [ ] Paused longer than the buffer keeps (an hour with When I pause · 1 hour): Play carries on from about an hour behind live.
- [ ] With a USB drive: Save To → USB drive, then pause; Storage shows the drive. Take the drive out while paused: Play carries on as far as it can, and Plasma doesn't stop.
- [ ] Storage Limit offers 10 to 500 GB with a USB drive (50 GB unless chosen), and 2, 4, 8 or 16 GB with Save To › This TV or no drive (8 GB unless chosen); each keeps its own choice.
- [ ] Rewind Buffer → Off: Play/Pause does nothing on a channel without an archive. On a channel with one, pausing holds the picture, and Play plays the archive from that moment, "behind live" in the overlay. Go Live goes back to live TV.
- [ ] Catch-up: while a program from the archive plays, Left and Right on the timeline move within it.
- [ ] Switch Player while behind live: the other player plays live TV. Pause and rewind on it too (VLC, mpv and the Android player).
- [ ] In the small window (picture-in-picture), Play/Pause in its menu pauses live TV into the buffer, and Play carries on from there.

### Recording a program (board 17)

- [ ] In the Guide, OK on a program coming up → Record: a red dot on it, in the Guide, on Live TV and in Search. Recordings › Coming Up and Scheduled list it, with its time.
- [ ] Record a program that starts in a few minutes, then leave Plasma, or turn the TV off: it records anyway. Recordings shows it as REC while it does, and the screensaver's line says so.
- [ ] Afterwards, OK on it in Recordings plays it from the start; leave halfway and OK again carries on from there. Near its end, it's marked watched.
- [ ] While one is being written, OK on its REC tile plays it from the start, and the timeline's end moves on as it records. Hold Right to the end: it plays a few seconds behind live. Left and Right jump as in a movie, each time from a keyframe; Back, then OK again, carries on where you were. Watching up to where it has got doesn't mark it watched.
- [ ] Watch one being written to its end: when the recording finishes, the picture plays to the end and the player closes, as after a movie; OK again plays it from its file.
- [ ] Watching one being written a few seconds behind live, pull the network cable for a minute and a half: the picture waits, then carries on from where it was once the recording does, rather than from where it opened.
- [ ] Record the channel you're watching: it keeps playing on the same connection (a source with a connection limit of 1 shows it best).
- [ ] Record an HLS channel (a channel of an Xtream source with Playback for This Source › Stream Format › HLS, or an M3U channel whose address ends in `.m3u8`): it records, and plays back like any other.
- [ ] With a USB drive connected, recordings go on it (Storage says USB drive); Settings › Recording › Record To → This TV puts the next one on the TV.
- [ ] Cancel Recording on one coming up takes it away; on one being written, it stops and keeps what it has.
- [ ] Delete in Recordings (red) removes it and its file. Pull the network cable or turn Wi-Fi off during a recording for a minute: it carries on afterwards, marked Incomplete.
- [ ] Restart the TV while a recording is being written: it carries on in the same file once Plasma's back.

### Record Series, conflicts and clean-up (board 17)

- [ ] In the Guide, OK on an episode of a series coming up → Record Series: the next airings in the guide get red dots, and Recordings › Series Rules lists the rule. A repeat of an episode already set isn't set again.
- [ ] Cancel Series takes the rule away, with what it set that's still to come; what it recorded stays.
- [ ] With a source that allows one connection, record two programs that overlap: Coming Up says "1 conflict" in amber, and both cards say Conflict. OK on one: "You can record 1 thing at once. Choose what to skip." Skip one, and the conflict goes.
- [ ] In the player, Player Options › Record Program records what's on; Stop Recording stops it, keeping what it has.
- [ ] Settings › Recording › Delete Watched Recordings → After 7 days: a recording you watched long ago isn't deleted straight away; five days later its tile says "Deletes in 2 days" in amber, and two days after that it goes. One kept with green stays.
- [ ] A rule's options in Recordings › Series Rules: Keep Last moves through all, 3, 5 and 10. Lowering it deletes nothing until the rule's next recording is done; then the oldest beyond it go.
- [ ] Once Plasma updates can be read (see "Plasma updates"): Install Now while a recording is being written says to install it once the recording ends, and downloads nothing.

### Local catch-up (board 31)

Your server has no archive, so this is how its channels get catch-up. A USB drive is best: an hour of HD takes about 2–4 GB.

- [ ] Settings › Playback › Rewind and Local Catch-up › Local Catch-up Channels lists the channels without catch-up of their own. On one, the line under the title says what it costs: "Uses 1 of your 2 … connections all the time." On a source with one connection, it says there's none to spare and doesn't tick.
- [ ] Tick a channel (or, in Live TV, a channel's options › Add to Local Catch-up: a notice says what it costs). The notification "Local catch-up on 1 channel" shows, and a few minutes later the page's Storage shows Local catch-up growing.
- [ ] An hour later, the Guide shows that channel's past programs with ↺, and OK on one plays it from its start; Left and Right move within it. Catch-up lists the channel ("Local · 1 h"), and Search finds its past programs.
- [ ] Watch the channel while it's in local catch-up: it uses the same connection (a source with two connections can still record something else).
- [ ] Turn the TV off for half an hour (While the TV Is Off: Keep recording), then on: what aired meanwhile plays. With Pause until the TV is on, that half hour is skipped.
- [ ] Restart the TV: local catch-up carries on by itself, and what it held before is still there.
- [ ] Clear Local Catch-up asks first, then the Storage shows almost nothing. Untick the channel: the notification goes once nothing's recording.
- [ ] In Catch-up, Menu on a local catch-up channel's past program › Keep as Recording: a notice says it's in Recordings, and it plays from there. The same from the Guide: Menu on a past program with ↺ on that channel.
- [ ] Play a past program that the TV-off half hour cut into: it plays on past the gap, and a notice says how many minutes were skipped.

### Copying recordings to the NAS for Jellyfin (board 34)

You'll need a shared folder on the NAS that Plasma's account can write to, and Jellyfin's TV Recordings added as a Shows library and Movie Recordings as a Movies library once the first copies are there.

- [ ] Settings › Recording › Copy to Network Folder (Off): the NAS shows up within a few seconds (Synology, QNAP, TrueNAS and Samba announce themselves; a Windows PC doesn't, so Enter Address with `smb://pc-name/Share`).
- [ ] Choose it, type the username and password (Next goes to the password, then down to Sign In): its shares are listed. Open one, then a folder: Use This Folder says "Checking that Plasma can write to…", and a notice says recordings will be copied there. The row now reads "On · <your NAS>", and the card shows the folder and its free space.
- [ ] A wrong password says so, with Try Again on the sign-in page. A read-only folder (or an account that can only read) is turned down at Use This Folder, not later.
- [ ] Record a short programme: when it ends, its tile says "Copying…", then "On <your NAS>". On the NAS: `TV Recordings/<Show>/Season 01/<Show> S01E03.ts` with its `.nfo` and `-thumb.jpg`, the show's `tvshow.nfo` and `poster.jpg`; a film under `Movie Recordings/<Title>/`; sport or news under `Other Recordings/<Channel>/`. No `.partial` file is left.
- [ ] A series without episode numbers in the guide, recorded by Record Series: `Season 2026/<Show> S2026E001.ts`, then `…E002` for the next.
- [ ] With Jellyfin signed in as an administrator (Continue Watching › Jellyfin Server), the episode shows up in Jellyfin within a minute or so, with its title, episode number and picture. Signed in as someone who isn't, the card's note says Jellyfin finds them on its next scan.
- [ ] Turn the NAS off (or unplug it) and record something: a notice says "Can't reach … Recordings will wait on the USB drive and copy when it's back", once, and the tile says "Waiting to copy". Turn it back on: within half an hour it's copied.
- [ ] Name Files for Jellyfin off: the next copy is "Channel – Title – date.ts" in the folder itself, with no info files.
- [ ] Copy to Network Folder › Turn Off asks first, then the row reads Off; what's on the NAS stays.
- [ ] Watch a channel while a copy runs: the picture shouldn't stutter (on Wi-Fi especially, say if it does).
- [ ] After Copying › Delete from the Drive, then record something: once it's copied, Settings › Playback › Rewind and Local Catch-up's Storage no longer counts it, and it still plays from Recordings, from the NAS; seek forward and back in it. With the NAS off, it can't play.
- [ ] Keep on the Drive for 7 Days (the default): a copied recording plays from the drive for a week, then from the NAS.
- [ ] Settings › Recording › Record To → Network folder: its detail says recordings go straight to the NAS. Record something short: while it records, its `.ts.partial` grows on the NAS; at the end it takes its name with its `.nfo` and picture, and the tile says "On <your NAS>". Nothing of it stays on the TV.
- [ ] Watch one while it's written straight to the NAS: from the start, then hold Right to a few seconds behind live.
- [ ] Turn the NAS off for a few minutes during one: a notice says it can't be reached, and the recording carries on. Turn it back on before the end: the file on the NAS ends up whole. Leave it off past the end: the tile says "Waiting to copy", and once the NAS is back the rest goes within half an hour.
- [ ] With the NAS off as a recording starts, it's recorded on the TV and copied once the NAS is back.
- [ ] Delete a copied recording in Recordings: it asks Delete from This TV or Delete Everywhere. Everywhere takes the `.ts`, its `.nfo` and picture off the NAS too (a film's folder is left empty), and Jellyfin drops it at its next scan; from This TV leaves the NAS as it is.

## Milestone 7: profiles, parental controls, Multiview and the phone remote

### Parental controls (board 25, brief 5.12)

- [ ] With no PIN, Settings says Parental Controls Off, and Settings → Parental Controls opens straight away with PIN Not set.
- [ ] PIN → Choose a PIN: four digits, then the same four again → back on the page, PIN says Set, and Settings says On · 18+ locked. Two different ones → "The two didn’t match. Choose the PIN again."
- [ ] Back to Settings, then Parental Controls again → it asks for the PIN first. A wrong one shakes and counts down; after five, "Too many tries. Try again in a minute."
- [ ] Live TV → a channel's options → Lock Channel → it dims with a lock and its preview stays dark. Opening it asks for the PIN, with Unlock for on what Settings › Parental Controls › Unlock For says.
- [ ] In the player, zap onto the locked channel → the PIN screen, its header naming the channel before it; Back goes back to that one. The right PIN plays it, and zapping away and back during the program doesn't ask again.
- [ ] Unlock for: Until the TV sleeps → turn the TV off and on again → the channel asks again.
- [ ] Live TV → the locked channel's options → Unlock Channel asks for the PIN, then it's an ordinary channel again.
- [ ] Settings → Parental Controls → Locked Channels lists what's locked; OK unlocks one, OK again locks it again.
- [ ] Lock Settings on → Home → Settings asks for the PIN. With it, Settings stays open until Home, or until the TV sleeps.
- [ ] PIN → Change PIN → a new one, twice; the old one doesn't open anything any more.
- [ ] PIN → Turn Off Parental Controls → Turn Off → Settings says Off, and locked channels play without the PIN. Choose a PIN again → they're locked again.
- [ ] Turn parental controls off, then Lock Channel in Live TV → Plasma has you choose a PIN first, then the channel is locked.
- [ ] Locked Groups → tick a group → its channels show the lock in Live TV and ask for the PIN, and their options don't offer Unlock Channel. Untick it → they're open again.
- [ ] A group with "XXX" or "Adult" in its name is locked already, tagged Adult in Locked Groups. Adult Words → add a word from another group's name → that group is locked too.
- [ ] If your server has a movie or series category with an adult word in its name: Movies shows it with a lock, and selecting it says it's locked instead of showing posters; its titles aren't in Recently Added, Search or Home's rows. Enter PIN → its posters show, for an hour (or until the TV sleeps, as Unlock For says).
- [ ] Age Limit 12+ (if your guide has ratings) → a channel showing a program rated 12 or more shows no preview, and opening it asks for the PIN with the rating's badge.

### Profiles (boards 05 and 25)

- [ ] The avatar at the top right → Who's Watching with one profile, "Me", and Add Profile. Add Profile → type a name → it's on Settings › Profiles and Who's Watching, with a picture of its own.
- [ ] Pick the new profile → its Home, its initial in the top bar. Favorite a channel and watch one → Favorites and Recently Watched have only those; pick "Me" again → its own are back. Who's Watching says what each watched last.
- [ ] Settings › Profiles › a profile → change its Name and Picture; PIN → choose one twice → Who's Watching shows PIN under it and asks for it.
- [ ] Profile Type → Kids, Ages 7 and under → pick it: locked channels are gone, a program rated over 7 asks for the parental PIN, and Settings asks for the parental PIN.
- [ ] Profile Type → Guest → watch and search as the guest → nothing shows in Recently Watched or recent searches.
- [ ] As the guest: Menu on a program in the Guide → no Record or Record Series, and "Guests can’t record."; Player Options has no Record; Recordings plays recordings, but red, green and blue do nothing and their options only play.
- [ ] Settings › Parental Controls › Kid-Safe Groups → your kids groups are ticked (by their names) → tick another, untick one. Pick the kids profile → Live TV, the Guide and Search have only those groups' channels, and no other groups show. Kids Words → add a word → groups with it in their name join. Movies and Series show only categories like Kids or Family, and Search finds only their titles.
- [ ] As the kids profile: Recordings shows only recordings of those channels, and none of a program rated over its ages (recordings made from now on keep their rating).
- [ ] Settings › Audio › Preferred Language and Settings › Playback › Subtitles and Teletext → change them on one profile → switch profiles → the other has its own; switch back → yours are back.
- [ ] Settings › Launcher & Home › Continue Watching, with Include Other Apps on: as the kids profile, Home's Continue Watching has no other apps' items; Kids Profiles See → Everything → they show.
- [ ] Ask Who's Watching on → turn the TV off and on → Who's Watching. Off, with Default Profile set → that profile watches after the TV wakes.
- [ ] Delete Profile asks first, then the profile is gone from Who's Watching; "Me" can't be deleted.

### Multiview (board 12, brief 4.12 and 5.8)

- [ ] In the player, Multiview (or the blue key in Live TV) → up to four tiles: the channel you were on, with the sound, and the next ones in its list. Note how many tiles the TV gives (two sit side by side across the middle).
- [ ] Move between tiles → the white ring, the Sound badge and the sound move together, at once.
- [ ] The toolbar's 2 Tiles, 3 Tiles and 4 Tiles → that many tiles: side by side, two over one, then 2 × 2. Going down keeps the tile you're hearing; going back up brings back the ones that went. Focus + 3 and Picture in Picture put the tile you're hearing in the big place; in Picture in Picture the small tile sits over the big one.
- [ ] Leave Multiview and open it again → it opens in the layout you picked last.
- [ ] Menu on a tile → Change Channel → the lists down the left and their channels; channels already in a tile are dimmed. Pick one → the tile changes to it.
- [ ] Menu → Remove → the tile goes; the others carry on.
- [ ] OK on a tile → the player on that channel, straight away, without the usual wait to connect. Back → where you opened Multiview from (Live TV, or what was before the player), not the channel before.
- [ ] If your account has a connection limit: more tiles than it allows → "Your subscription allows 2 streams at a time." on the extra ones.
- [ ] Two 4K channels → the second says "Multiview shows one 4K channel at a time."
- [ ] Turn the TV off and on in Multiview → the tiles play again.

### Phone remote (board 33, brief 5.21)

Use a phone on the same Wi-Fi as the TV, and try it with an iPhone and with an Android phone.

- [ ] Settings → Remote & Buttons → Phone Remote → a QR code, the TV's address and port, and a code like "7KQ 4MZ", with the note about reserving the address.
- [ ] Scan the QR code with the phone's camera → the page asks for the code (already filled in) and a name ("iPhone" or "Android phone"). Change the name, Pair → the TV says "Maya’s iPhone is now a remote", and the phone shows the remote with the TV's name and a green dot.
- [ ] Settings → Phone Remote lists the phone as Connected. Close the page on the phone → it says Today instead.
- [ ] The D-pad, OK and Back move around Plasma as the remote does. Hold a direction → focus keeps moving; hold OK on a channel → its options open.
- [ ] Home on the phone → what Plasma's Home button does (Settings › Launcher & Home › Home Button).
- [ ] In Live TV, the colour keys on the phone read Favorite · Catch-up · Sort · Multiview, and each does what the TV's own does. On Home they just say the colours.
- [ ] 123 → type a channel number → it tunes as from the remote. CH up and down zap in the player.
- [ ] While a channel plays, the phone shows it with its program and timeline. Pause with Play/Pause, wait, then the card says how far behind live; Go Live goes back to live.
- [ ] Channels tab → Favorites first, numbered from 1, then the groups and All Channels. Search "news" → only matching channels. Tap one → the TV plays it, and the row gets the speaker.
- [ ] Open Search on the TV → the phone switches to the Type tab ("Typing into Search on the TV"). Type → the TV's results follow, and the phone says what was found. Done → the TV moves to the first result.
- [ ] On an iPhone the field says "Tap to type"; on Android the keyboard should open by itself (tell me if it doesn't).
- [ ] Open another app on the TV (YouTube, say) → Menu, 123, CH and the colour keys grey out on the phone. Play/Pause pauses the other app. With Home Button Override on, Back and Home work there, and on Android 13 or later the D-pad and OK too.
- [ ] Add the page to the phone's home screen → a Plasma icon that opens the remote straight away.
- [ ] Settings → Phone Remote → the phone → Rename → a new name; Forget → the phone page asks for a code again.
- [ ] Turn Phone Remote off → the page can't reach the TV ("Can’t reach Living Room TV. Open Plasma on the TV."). Turn it on → the page reconnects by itself.

## Fixes from the Chromecast (28 October)

- [ ] Live TV, in a category: type a channel's number as the list shows it → that channel, and Up and Down then zap through the category.
- [ ] Guide: Up and Down move from channel to channel, rows saying "No information" too (white when focused), and OK on one plays the channel.
- [ ] Settings → Launcher & Home → Show TV Guide off → the Guide tab goes from the top bar. On → it's back.
- [ ] Live TV: under the live view, what's on now and the channel's schedule after it. Right, then Down → the schedule scrolls as far as the guide goes, fading at the bottom while there's more. OK on a program → Remind Me and Record.
- [ ] A channel with no guide in Live TV or the player: after a moment it should show what's on and next, asked of your server. If the Guide and Live TV show nothing for most channels, tell me what Settings → Portals & Playlists → your source says on its Guide line.
- [ ] Settings → Appearance → Text Size → Large → the text grows everywhere (smaller text the most). Try Accent → Blue (progress bars), Clock → 12-Hour ("8:41 PM") and Reduce Motion.
- [ ] Home: Down to the Apps row → Home moves up so the row sits higher, Continue Watching fading above it.
- [ ] Search: the QWERTY keyboard, focus starting on A; hold Delete to clear. Type one of your channels' names → a Channels section first, with the channel; OK plays it.
- [ ] Multiview: 2 Tiles, 3 Tiles and the rest (see Multiview above).
- [ ] Player: OK → beside the program, the stream's resolution, frame rate and sound ("1920×1080 · 50 FPS · AC-3 5.1"), and CC when it has subtitles. On a channel without a guide, its name and the same.
- [ ] Player: OK, then Down → a row under the controls: Add to/Remove from Favorites, Channel List, Search, Guide, Picture Size, Sleep Timer and Player Options. Up or Back closes it.

## Fixes from development build 104 (29 October)

- [ ] Live TV: move up and down the list, and hold Down → the picture stays on the channel that was playing; its number is white in the list. (#76)
- [ ] OK on another channel → it plays in the picture, and its number turns white. OK on it again → full screen. Back → Live TV, with the picture on that channel.
- [ ] Under the picture: with Settings → TV Guide → Live TV Details on Focused Channel (the default), what's on and coming up follows the channel you're on once you stop for a moment. Switch it to Playing Channel → they stay with the channel in the picture as you move.
- [ ] If you've a locked channel: OK on it → straight to the PIN screen, full screen.
- [ ] Guide: move around the grid → the picture stays on the channel that was playing, and that row stays marked. OK on a program on now → its channel plays in the picture; OK on it again → full screen. A "No information" row does the same. (#76)
- [ ] Open the Guide while a channel from another group plays → the picture keeps that channel.
- [ ] Live TV, in a category: type a channel's number as the list shows it → after a moment (or OK straight away), focus jumps to that channel and nothing plays. OK → it plays in the picture; OK again → full screen. A number that isn't in the category → All Channels, with that channel focused. (#79)
- [ ] In the player, a typed number still changes the channel.
- [ ] Home: Favorites shows each channel's logo, large, with what's on and its progress, not blank squares. (#77)
- [ ] Home: New in Movies shows posters; a movie without one shows its name. (#77)
- [ ] Settings → Launcher & Home → Show TV Guide off → TV Guide goes from Home's row of tiles as well as the top bar. On → it's back in both. (#78)
- [ ] After installing this build, open Plasma and give it a minute or two: it reads your movies and series again by itself. Movies → a category whose posters were names → within a few seconds of showing, each takes its own poster, the same as TiviMate's. Scroll on → the new ones fill in too. Search, Home's New in Movies and the movie's page show the same posters. (#81)
- [ ] Settings → Portals & Playlists → Replace Placeholder Posters off → Movies and Series show your provider's pictures as it gives them, the placeholder included. On → the posters found come back.
- [ ] Series still show their posters as before.

## Fixes from development build 111 (30 October)

- [ ] Settings › Remote & Buttons › Phone Remote: on your phone, type the address the TV shows under the code (`http://` and the numbers with `:8484`) → the Plasma Remote page opens and asks for the code; type the one on the TV → the phone pairs. Scanning the QR code works as before. (The page is plain `http` on your home network, so `https://` can't open it.)

## Home's rows (#80)

Settings → Launcher & Home → Home Rows. Hold OK means holding the centre button until the row lifts; Menu does the same on a remote that has it.

- [ ] Launcher & Home's Home Rows row says "5 rows". OK → the Home Rows page: Favorite Channels, Continue Watching, New in Movies, New in Series and Apps On; Upcoming Events and Recently Recorded Off. "‹ Launcher & Home" at the top.
- [ ] OK on Upcoming Events and on Recently Recorded → On. Home → the two rows are there, in their places in the order.
- [ ] Back on Home Rows: hold OK on New in Series → it lifts, with arrows above and below; the other rows dim, and the label reads "Moving New in Series · Press OK to drop it here". Up twice, OK → it stays there and its switch hasn't changed. Home → New in Series comes right after Favorite Channels.
- [ ] Hold OK on a row, Down a few times, then Back → it goes back where it was, and Home hasn't changed.
- [ ] Move Upcoming Events to the top → on Home it sits under the row of tiles, where Favorite Channels was. Down to it → Home moves up a little so the whole row shows. Try Continue Watching at the top too: Home moves up further, the big title fading, and the row shows whole.
- [ ] Turn a row off → it's gone from Home, with no gap: Down goes straight to the next. Back from any row → the Live TV tile.
- [ ] Down through every row → each comes up to the second place on the screen with the row before it above, the ones further up fading; Up goes back the same way.
- [ ] Show Android Apps off → the Apps row and the Apps tab go, whatever Home Rows says, and Home Rows shows Apps as "Hidden while Show Android Apps is off". On → both back.
- [ ] New in Series: series with new episodes, posters as in Series. OK → the series' page; See All at the end → Series.
- [ ] Upcoming Events: set Remind Me on something tonight and Record on something else (in the Guide or Live TV) → both show on Home with a bell or a red dot, soonest first. What starts within the hour on your favorites shows too, and, if your guide gives programs categories, sport on now with ● LIVE, when it ends and a line for how far it's got.
- [ ] OK on a program that's on → its channel, full screen. OK on one later → Remind Me and Record (Cancel Reminder or Cancel Recording when set); the bell or dot follows at once. Cancel the only reminder or recording that brought one in → it leaves the row and focus moves on. Menu on one that's on → Record alone.
- [ ] Home Rows → Upcoming Events → its three switches. Turn Live Sports and Events off → Home's sport goes (but a program you set a reminder on stays). The same for the other two.
- [ ] Recently Recorded: your newest finished recordings, a dot before the ones you haven't watched, a bar on one you stopped part way. OK plays it; See All at the end → Recordings.
- [ ] Home Rows → Continue Watching → board 32's page, now "‹ Home Rows" at the top; Back → Home Rows.
- [ ] Restart Plasma (or turn the TV off and on) → Home keeps the order and the switches.
- [ ] On a kids profile, Upcoming Events and Recently Recorded show only the programs of its own channels.

## First-time setup (#85)

Setup opens instead of Home on a new install, until a source is added. Your TV has a source, so Plasma opens on Home there as before; to go through setup on it, use Settings › About › Set Up Plasma Again. To see setup as a new install does, clear Plasma Dev's storage: the TV's Settings › Apps › Plasma Dev › Clear storage (Clear data on some TVs), or `adb shell pm clear ca.gureet.plasma.dev` from a computer. That forgets the sources, profiles and settings too, so the emulator is the place for it.

- [ ] Install this build over the last one on the TV → Plasma opens on Home, not setup.
- [ ] On the emulator, clear Plasma Dev's storage and open it → the welcome: Plasma's wordmark, large, "Live TV, the guide and your apps, all on one Home screen.", Set Up Plasma with focus and Skip for Now beside it, and a line saying Skip for Now keeps the sample channels.
- [ ] Skip for Now → Home, on the sample channels. Force stop Plasma Dev (Settings › Apps) and open it again → Home, not setup.
- [ ] Clear the storage again and open Plasma → Set Up Plasma → Your TV Service: "‹ Welcome" on the left, "Step 1 of 6" where the clock usually is, Add Portal or Playlist with focus, and Continue under it saying "With the sample channels". Back → the welcome; Set Up Plasma → the step again.
- [ ] Add Portal or Playlist → Add a Source on the Stalker tab, its header saying "‹ Your TV Service", with the QR code for a phone. Back → the step.
- [ ] Add Portal or Playlist again and add your server (on the Xtream tab, or from your phone with the QR code). Once it's connected and you leave Add a Source → Your TV Service lists it, with focus on Continue. While the rest loads, Continue's detail and your source's show its channels ticked and "Guide · loading…"; then the guide ticked and "Movies and series · loading…". Once everything's in, Continue's detail says what's next again.
- [ ] Continue → Pause and Rewind, "Step 2 of 6": Rewind Buffer with focus, on When I pause · 1 hour. Continue → Home Screen, "Step 3 of 6". If Plasma isn't your Home screen yet, the way that suits the TV says Recommended and has focus (Home Button Override, on a TV that doesn't let an app be its Home app). Set it up → back on the step. With Plasma already Home, focus starts on Continue.
- [ ] With Plasma your Home screen: press Home on a step, and on Add a Source opened from one → you stay where you were.
- [ ] Profiles and Parental PIN, "Step 4 of 6": Add Profile has focus. Add a profile → it's listed. Set PIN → choose four digits twice → back on the step, and the row says Change PIN. Change PIN → it asks for the PIN you chose first, then a new one twice.
- [ ] Look, "Step 5 of 6": focus on your accent, ticked. Move down through Blue, Orange and Green → the two tiles on the left show each color in their progress bars, without choosing it. Down to Large → the tiles' text grows. OK on Blue → it's ticked, and Plasma's progress bars are blue from now on. OK on Large → all of Plasma's text grows at once. Back to Default and White if you like them better.
- [ ] Guide and Channel Numbers, "Step 6 of 6": your source says "From the server". OK on it → its Edit, the header saying "‹ Guide and Channel Numbers"; Back → the step. Channel Numbers offers what Settings › Channels does. If some of your channels share a number, Duplicate Numbers is there too and opens its screen.
- [ ] Continue → All Set: a tick for each thing you set up (your source and its channels, Plasma as Home, the profiles, parental controls, the accent, Large text), and nothing you didn't. Go to Home → Home, with the first channel playing, muted. Back doesn't go back into setup.
- [ ] Force stop Plasma Dev and open it again → Home.
- [ ] On the emulator, from cleared storage: Set Up Plasma, then Continue through every step without changing anything → All Set says you're on the sample channels. Go to Home → Home on the sample channels.
- [ ] Settings › About › Set Up Plasma Again (the last row) → the welcome, with Back to Settings in place of Skip for Now. Back to Settings, or Back → About. Set Up Plasma Again, then the steps → each shows what's set now: your source, your rewind buffer, your profiles, Change PIN, your accent.
- [ ] All of setup works with the D-pad, OK and Back alone.

## Frozen streams (#91)

A freeze can't be made to happen on purpose, so keep these for when the channel that froze before freezes again. Plasma only restarts a channel whose stream is still arriving (Player Options' bitrate moving), as yours was.

- [ ] Settings → Playback → down to Restart Frozen Streams, under Connection Timeout → it says After 5 Seconds, and its detail says what it does. OK → After 5 Seconds (ticked), After 10 Seconds and Off.
- [ ] Watch the channel that froze before, as you usually do. When the picture and sound stop: about five seconds later the ring turns and the channel plays again from live, with "(the channel) froze, so Plasma restarted it." at the top right. You're still on the channel, in the same list: Up and Down zap as before, and Back goes where it went before.
- [ ] If it freezes and doesn't come back within ten seconds or so: open Player Options and look at the bitrate. Tell me the time, the channel, whether the bitrate was moving, and the stream info line (VLC, mpv or the Android player).
- [ ] If it freezes a third time within ten minutes: "VLC couldn't play (the channel). Playing it with mpv." instead, and the stream info says mpv. After half a minute of play the channel keeps mpv; Settings → Playback → Forget Players Chosen per Channel puts it back on VLC.
- [ ] Nothing restarts while live TV is paused, while you watch behind live after rewinding, while the ring turns on a slow channel, or on a radio channel that's playing.
- [ ] After 10 Seconds, then change channel → a freeze lasts about ten seconds before the channel comes back.
- [ ] Off, then change channel → a freeze stays until you change channel yourself, as before. Set it back to After 5 Seconds.

## Snappier (performance)

Plasma should keep up with the remote now. Install this build over the last one and open Plasma once. Android compiles Plasma for the TV the next time the TV is left idle (overnight in standby does it), so for the first while after installing, a screen can be a little slower the first time it opens. To compile it at once from a computer instead: `adb shell cmd package compile -f -m speed-profile ca.gureet.plasma.dev`.

- [ ] Live TV: hold Down through the categories on the left → focus keeps up with the remote. Stop on one → its channels show after a moment, as before. OK on a category → its channels at once.
- [ ] Live TV, in All Channels and in your biggest category: hold Down through the channels → focus keeps up; the schedule under the picture follows once you stop. Keep moving as the clock turns to the next minute → no stutter.
- [ ] Movies and Series: hold Down through the categories → focus keeps up; stop on one → its posters fill in quickly. Scroll through the posters → smooth, and they look as sharp as before, the focused one too.
- [ ] Settings, from the top bar → the page shows at once with your own sources and profiles, not other values first. Open a few of its pages and come back → each opens at once.
- [ ] Guide: Yellow → a group → the grid shows it after a moment, and moving through the grid stays smooth, as the clock turns to the next minute too.
- [ ] Player: zap up and down through All Channels and through a category → each banner shows at once.
- [ ] Channel logos in Live TV, the Guide and the player look as sharp as before.

If something still lags, say where (the screen and the list), how many channels or titles that list has, whether it lags every time or only the first time after installing, and about how long it takes.

## Animations (#84)

Every animation lasts 200 ms or less, and none holds a key back: press as quickly as you like and each press acts at once. Go through the list with Settings › Appearance › Reduce Motion off, then again with it on: on, nothing moves or bounces, things only fade, and focus grows less.

- [ ] Home → Live TV, the Guide, Movies, Search and Settings, and Back again → each screen fades in as it settles up a little into place. Reduce Motion: a fade, with no movement.
- [ ] OK on a channel, full screen → the player fades in without moving. The picture itself never fades or moves, in the player or anywhere else.
- [ ] In the player, OK → the overlay fades in as it rises a little; Back → it fades out as it sinks. Menu → Player Options takes its place the same way, and the picture behind darkens smoothly. Reduce Motion: fades only.
- [ ] OK, then Down → the overlay lifts smoothly and the row under the controls fades in; Up → back down. Reduce Motion: the overlay goes up at once and the row fades in.
- [ ] Up or Down in the player → the zap banner fades in as it rises with the first press, then stays put while you keep zapping, its words changing at once. Its progress line starts where the program is, rather than sweeping from the left.
- [ ] Type a channel number in the player, Live TV or the Guide → the number box fades in as it rises; once the channel tunes, it fades out with the number still in it.
- [ ] Live TV: Menu on a channel → its options fade in as they rise. Menu, then OK straight away → the first choice works at once. Menu, then Back and Down straight away → focus moves down the list while the options fade away.
- [ ] Live TV: rest on another category → its channels fade in rather than jump; hold Down on the categories → they follow without flickering. Rest on another channel → what's on under the picture fades in.
- [ ] Live TV › Favorites: Menu → Move on a channel, then Down a few times → the others slide out of its way and step back; OK → it settles there, with focus. Reduce Motion: the rows jump to their places. Menu → Hide Channel on a channel → the rows below slide up to close the gap.
- [ ] Guide: Up to the days, then Right → each day's grid fades in. Yellow → another group → its channels fade in.
- [ ] Movies and Series: rest on another category, or change Sort → the posters fade in. Search: type → the results fade in as they change, and follow without flickering when you type quickly.
- [ ] Settings: move down a page's rows → the detail on the left fades in for each; hold Down → it follows without flickering. Settings › Profiles: add a profile → its row fades in and the rows below slide down; delete it → it fades out and the rest slide up.
- [ ] Home: Menu → Move on a favorite → it lifts and the others step back; Right twice, then OK → it settles in its new place and the others come back. Remove a favorite, or hide an item from Continue Watching → it fades out and the rest slide along. Apps' On Home row moves the same way. Reduce Motion: the item lifts at once and nothing slides.
- [ ] Settings › Launcher & Home › Home Rows: move a row → the others slide out of its way.
- [ ] Home: Up to the hero, then Right → the words and picture crossfade quickly.
- [ ] Focus anything → it grows (tiles, buttons) or turns white (rows) smoothly. Reduce Motion: tiles and buttons grow less (4 % at most).
- [ ] A wrong parental PIN → the dots shake (Reduce Motion: they fade and come back). Type the right PIN straight away, while they shake → it's taken.
- [ ] Leave the remote alone until the screensaver comes → it fades in; any key → it fades away, and the key does nothing else.
- [ ] Set a reminder on a program a few minutes away → when it's due, the reminder fades in as it rises, in Plasma and over another app. Switch or Dismiss → it fades out in Plasma, and goes at once over another app.
- [ ] On the Chromecast with Google TV: hold Down through Live TV's channels and a Settings page, and change categories in Live TV and Movies → no slower than the build before.

## Held keys, categories and Favorites (1 November)

- [ ] Live TV: hold Down through All Channels → focus moves one channel at a time at first, then faster and faster the longer you hold, and the list keeps up without easing. Let go → it stops at once.
- [ ] Hold Up to the first channel → focus stops there and stays in the list, however long you hold. Let go, then press Up → the top bar.
- [ ] Hold Up and Down through Live TV's, Movies' and Series' categories → the same: faster the longer you hold, stopping at the first and last categories, never jumping to the top bar.
- [ ] Movies or Series: hold Down through the posters → several rows at a time after a moment; hold Left at the first poster of a row → focus stays in the grid.
- [ ] Guide: hold Down through the channels → faster the longer you hold; hold Up to the first channel → it stays in the grid. A press Up from there goes to the days.
- [ ] Live TV: rest on another category, then Right → its first channel, with its list at the top. OK on a category → the same. Left, then Right again without changing category → the channel you were on.
- [ ] Movies: rest on 4K UHD, then Right → its first poster, Neon Tide, even though The Last Lighthouse is in it too. Yellow (Sort) → focus stays on the same title, as before.
- [ ] Guide: Yellow → another group → the grid starts at its first channel.
- [ ] Live TV › Favorites: on a channel, Red (or Menu → Remove from Favorites) → it stays where it is, with its star off and its own number. Move to another channel → it goes, and the rows below slide up. Do it on the channel playing in the picture → it stays until another channel plays there.
- [ ] Home or Live TV, with a channel playing in the picture → open Settings from the top bar → Settings fades in without the picture showing through it. Back → the picture is there again at once.

## Favorites, the end of the channel list and compiled updates (1 November)

- [ ] Live TV › Favorites: on a channel, Red (or long-press OK → Remove from Favorites) → it stays, its star off. Down to the next channel → it goes, and doesn't come back.
- [ ] The same on the channel playing in the picture: it stays while it plays. OK on another channel → it goes, and doesn't come back with its star. Try it again just after Plasma starts, while it refreshes its sources and the guide.
- [ ] Take a channel out of Favorites, then Red again while it's still there → its star is back, and it keeps its place in Favorites, not at the end.
- [ ] Live TV › All Channels, or a long category: hold Down to the last channel → it shows whole, above the fade at the bottom of the list, not under it.
- [ ] Plasma Update: once this build is installed, install the next build with Plasma Update (the next build on `main`, or another test build) → before opening Plasma, `adb shell dumpsys package ca.gureet.plasma.dev | grep -A4 "Dexopt state"` shows `speed-profile` compiled at install (`reason=install-dm` on Android 14, `install` before), not `verify`. Plasma is quick from its first start.

## Settings that do what they say (2 November)

- [ ] Settings › Remote & Buttons › Number Entry Delay → 3 s. In the player, type 1, 0 → the number waits about 3 seconds before it tunes. Back to 1.5 s → as before.
- [ ] Long-Press OK → Nothing. Live TV: hold OK on a channel → it plays, as OK does, and no options open; Menu still opens them. The Guide and the player → the same. Back to Options → holding OK opens the options again.
- [ ] Channel Up Goes To → The previous number. In the player, CH + → the channel before; CH − → the one after; Up → the channel before too. The zap banner's card above is the channel CH + goes to.
- [ ] Settings › Playback › Up/Down in Player → Show Channel List. In the player, Up or Down → the channel list opens over the picture, as Left does; CH + and CH − still change channel.
- [ ] Settings › Appearance › Show Channel Numbers → Off. Live TV, the Guide, Home's picture, the zap banner, the player's controls (OK), Multiview, Catch-up and Search → names without numbers. Type a number in the player → the digits still show.
- [ ] Settings › Playback › Advanced › Show Stream Stats → On. Play a channel → a panel at the top right: VLC and its version with "mediacodec, hardware", the picture's size and rate, the bitrate and buffer, dropped frames, the source. Switch Player to mpv, then the Android player → the panel follows. Play a movie → it shows there too. Off → it goes.
- [ ] Settings › Playback › Audio › Stereo Downmix → On (with the receiver on). A channel with Dolby 5.1 → the receiver shows two-channel PCM, not Dolby. Channel options › Volume Level → it has its steps. Off → Dolby again on the next channel.
- [ ] Night Mode → On, on the TV's speakers or with Audio Output on Decode to PCM. A film or a channel with loud ads → loud passages are clearly quieter and quiet speech a little louder. Off → as before, at once.
- [ ] Audio Delay → +100 ms → the sound is a little behind the picture, at once, on VLC and mpv. −100 ms → a little ahead. Back to 0 ms.
- [ ] Settings › Launcher & Home › Live Preview on Home → With Sound → Home: the last channel behind it is heard. Off → nothing plays behind Home, and the programme's picture shows when the guide has one; OK on it plays the channel. Muted → as before.
- [ ] Settings › TV Guide › Keep Past Days → 1 day, Load Ahead → 3 days, then Portals & Playlists › a source › Refresh Now → the Guide goes back to yesterday only, and ahead three days. Back to 7 and 7 and refresh → a week each way again.
- [ ] Channel Matching → By guide ID only → refresh → channels whose guide came by their name have none. Back to Automatic and refresh → they have it again.
- [ ] Update Every Day At → 06:00. The next morning, with the TV asleep overnight, the source's guide in Portals & Playlists says it was updated after 06:00 that morning.
- [ ] Settings › Portals & Playlists › Channel Logos → From the Guide, then refresh a source with an XMLTV guide → channels show the guide's logos. From Portal → the source's again, and the guide's only where the source has none.
- [ ] Live TV: hide a channel (Menu → Hide). Settings › Channels › Hidden Channels → it's listed, ticked. OK → unticked; back in Live TV it's in its lists again.
- [ ] Settings › Channels › Hidden Groups → every group. OK on one → ticked. Live TV, the Guide and Multiview → the group's gone, and its channels are gone from All Channels; CH + skips them; Search doesn't find them. OK on it again → all back.
- [ ] Settings › About › Open-Source Licenses → Plasma, libVLC, mpv and FFmpeg and the rest, each with its license. OK on libVLC → its license in full and where its source is.

## A source's categories (2 November)

- [ ] Settings › Portals & Playlists › your source → OK → Categories → Live TV, Movies and Series (a playlist with only channels opens its Live TV categories straight away) → Live TV → Order, Show or Hide All, New Category, then every category with On.
- [ ] OK on a category → Off, and dimmed. Live TV, the Guide and Multiview → it's gone, its channels are gone from All Channels, CH + skips them and Search doesn't find them. OK on it again → all back.
- [ ] Show or Hide All → Hide All → every category Off, "0 of … shown". Show All → all back.
- [ ] Order → A to Z → Live TV's categories (under All Channels, Favorites and Recently Watched) and the Guide's groups go A to Z, "Sports 2" before "Sports 10". Provider's Order → as your provider lists them.
- [ ] Hold OK on a category (or Menu) → Rename, Move. Move → it lifts; Down twice, OK → it stays there, and Order says My Order. Live TV and the Guide → that order. Order → A to Z, then My Order again → your order is back.
- [ ] Order → My Order → a Reorder row shows under Order. OK on it → Done Reordering, and the categories have a move mark. OK on a category → it lifts; Up or Down carries it, and held, it goes faster the longer you hold; OK → it stays there. OK on another → it lifts at once. Back (or OK on Done Reordering) → OK shows or hides categories again. Order → A to Z → Reorder goes away.
- [ ] Rename → type a name → Save → the row shows it, with "Provider's name: …" under it; Live TV, the Guide and a channel's Channel Info → the new name. Rename again, delete every letter → Save → the provider's name is back.
- [ ] New Category → "Game Day" → Create → it's first, "Yours · 0 channels". Live TV: Menu on a channel → Add to Category → Game Day → OK → Live TV now has Game Day, with that channel. Add two more, from different categories → Game Day lists all three, by number, and CH +/− in the player goes through them.
- [ ] Back in Categories, hold OK on Game Day → Channels → ticked: the three. OK on one → unticked; Back → it's out of Game Day in Live TV. Add Channels → a category → tick two → Back, Back, Back → they're in Game Day.
- [ ] Hide one of your provider's categories, but put one of its channels in Game Day → that channel stays: in Game Day, All Channels and Search; the others of its category are gone.
- [ ] Refresh Now on the source → the order, the names, Game Day and what's in it are all still there; a category the provider adds comes last in My Order.
- [ ] Movies: Categories › Movies → hide a category → Movies doesn't list it, its movies are gone from Recently Added, Search and Home's New in Movies. New Category → "Weekend" → in Movies, Menu on a poster → Add to Category → Weekend → Movies lists Weekend with it. The same for Series.
- [ ] A category of yours → Delete Category → Delete → it's gone; its channels or titles are still where they were.
- [ ] Parental controls: an adult category renamed is still locked; Kids profile → your own categories show only the channels it can see.

## Freeze Watch, and frozen streams on VLC (2 November)

VLC now counts only the pictures it keeps and the sound it plays, so a freeze like yours on VLC should restart after five seconds. Show Freeze Watch shows what the check sees, to know for sure.

- [ ] Settings → Playback → down to Advanced → Show Freeze Watch, under Show Stream Stats: Off, with its detail. OK → On. A card shows at the top left: "Freeze Watch · restarts after 5 s", what's playing, and "Since (the time Plasma started): no restarts". It takes no keys: everything works as before under it.
- [ ] Play a channel full screen → the card says "(channel) · VLC · moving", "Each second: 25 decoded · 25 shown · 0 dropped · sound 47 played · sound 0 dropped" (about; the numbers follow the channel), "So far: 0 breaks in the stream · 0 damaged packets", "Picture on screen: changing" and "Coming in: (the bitrate)".
- [ ] Pause → "paused"; play on → "moving". Rewind → "behind live, in the rewind buffer"; Go Live → "moving". Change channel → "opening", then "moving".
- [ ] Home, Live TV and the Guide: the card stays, about the picture behind them.
- [ ] When the channel freezes: the card says "still for 1.0 s, … came in", counting up. At five seconds the channel restarts, with its notice at the top right; the card's "Since" line says "1 restart (VLC 1)", and its newest line "(time) · (channel) · VLC · restarted after 5.0 s still, … came in".
- [ ] If it freezes and doesn't restart: take a photo of the card while it's frozen, or note its "Each second", "So far" and "Picture on screen" lines, and the time. They say what VLC was doing.
- [ ] On mpv, when the ring turns and the picture comes back by itself: the card's newest line says "buffered for (how long)" (mpv filling its cache) or "lost the stream: reconnecting", not "restarted", so there's no notice. A restart says "restarted after …" and shows its notice.
- [ ] If "Picture on screen" says "this TV won't copy it", or "hasn't changed since it opened" while the channel plays normally, tell me: then the TV doesn't let Plasma copy its video, and that line can't help there.
- [ ] Settings → Show Freeze Watch → Off: the card goes. On again: the restarts it counted are still there (until Plasma restarts).

## Clock jumps and the picture alone (2 November)

Keep Show Freeze Watch on for these.

- [ ] On mpv: when the picture jumps (the stream "restarting"), the card's newest lines say "the stream’s clock jumped back …" (or forward), then "buffered for …" if mpv started again by itself, with no notice. The "Since" line counts the clock jumps.
- [ ] On VLC (Switch Player): the same jumps are listed. If VLC freezes, look for "the stream’s clock jumped …" just before, and note the times.
- [ ] On mpv or the Android player, if the picture freezes while the sound plays on: "picture still for … while the sound plays" counts up, and at five seconds the channel restarts with its notice; the card's newest line says "restarted after 5.0 s with its picture still".
- [ ] A music channel that shows a still picture with its sound, on mpv: it plays on; nothing restarts.
- [ ] Settings → Playback → Restart Frozen Streams: its detail says "When a channel’s picture stops while its stream still comes in, Plasma starts it again, … With VLC, its sound has to stop too. …"

## Export Diagnostics and Save Player Logs (2 November)

- [ ] Play a few channels, then Settings → About → Export Diagnostics → Send to Your Phone. Scan the code: the phone shows "Diagnostics from …" with Download and Report a Problem on GitHub. Download gives a ZIP.
- [ ] In the ZIP, `plasma.log` lists what played (Opening, Playing … on Vlc …), and `device.txt` the TV, Plasma and the sound system. Search every file for your server's address, your username and password: none is there. Servers show as `server-1`, `server-2`…, also inside another link's `url=` (as `https%3A%2F%2Fserver-2…`).
- [ ] Make a channel fail (one your provider has dropped), export again: `plasma.log` has a FAILED block for it, with the engine, the picture and sound and the reconnects.
- [ ] Report a Problem on GitHub opens the issue forms on the phone. Attaching the ZIP to an issue works.
- [ ] Back on the TV closes the sheet, and with it the TV's page: reloading it on the phone no longer works.
- [ ] Settings → Playback → Advanced → Save Player Logs on. Play a channel in mpv (Switch Player), export: `players.log` has mpv's lines. Force-stop Plasma or restart the TV, play a channel in VLC, export: VLC's lines are there too.
- [ ] Save Player Logs off: no new lines come into `players.log`.
- [ ] With a USB drive connected: Export Diagnostics → Save to USB Drive. A notice names the file and the folder (Android/data/…/files/Diagnostics), and the ZIP is there on a computer. Without a drive, the notice asks for one.

## Fast Zap (2 November)

Your server allows more than one connection, so it suits this.

- [ ] Settings → Playback → Fast Zap: Off, One Ahead and Both Ways, with Off chosen.
- [ ] One Ahead: zap up twice. The second change starts at once, without the usual wait. Zap down twice: the first may wait, then the second starts at once.
- [ ] Both Ways: after a zap either way, the next zap either way starts at once.
- [ ] With Show Freeze Watch on, nothing restarts or freezes after a fast zap, and the picture isn't far behind the channel's live edge.
- [ ] Off: zapping is as before.
- [ ] A recording starting while a channel is kept ready still records.

## Live TV's list (3 November)

- [ ] Settings → TV Guide → Live TV → Channel List Shows: What's On and Channel Names, with What's On chosen.
- [ ] What's On: Live TV's rows are as before, with what's on, its times and its progress.
- [ ] Channel Names: each row shows the channel's name, with what's on and its progress under it. A channel with no guide shows just its name. A long title ends in "…" before the progress bar.
- [ ] The choice stays after Plasma restarts.

## A clock jump restarts VLC (3 November)

With Show Freeze Watch on and VLC playing (Settings → Playback → Player Engine → VLC), on the channel whose clock jumps:

- [ ] When Freeze Watch lists "the stream’s clock jumped …", the next line is "restarted as the stream’s clock jumped", and the notice says the channel jumped in time. The picture is back within a few seconds, without the 5 or 10 seconds of still picture first.
- [ ] The third jump within 10 minutes hands the channel to mpv instead of restarting it, with the "Playing it with mpv" notice.
- [ ] Paused or gone back in the rewind buffer, a jump doesn't restart anything.
- [ ] With Restart Frozen Streams off, jumps are listed and nothing restarts.
- [ ] With mpv, jumps are listed and nothing restarts.

## Back Up Settings and Restore from Backup (3 November)

- [ ] Settings → About → Back Up Settings → Save to USB Drive → With Logins: type a passphrase twice. It says it saved the file in Download/Plasma on the USB drive, where it stays if Plasma is uninstalled or its storage is cleared. On a computer (or a file manager app), the drive has it in Download/Plasma, and a copy in Plasma's own folder, Android/data/ca.gureet.plasma.dev/files/Backups for Plasma Dev.
- [ ] Send to Your Phone: scan the code, download the backup on the phone. Try Without Logins too.
- [ ] Save on This TV, with or without logins: it asks where, Downloads or Documents (and Choose a Folder, naming the app, only where an app you installed saves files; on most TVs it isn't there). It says the file name and folder, and that it stays if Plasma is uninstalled or its storage is cleared. Restore from Backup lists it as "Downloads" or "Documents", and restoring it works as from the drive.
- [ ] The backup is there in a file manager app, in Download/Plasma or Documents/Plasma.
- [ ] Clear Plasma's storage (or uninstall and install it again), then Restore from Backup: with a file manager app you installed on the TV, Choose a File shows with the app's name, and opens that app. Choose the backup in Download/Plasma, and it restores. Without one, Choose a File doesn't show, and the backup needs a USB drive or the phone. It never opens an app that came with the TV, as froze the Chromecast (6 November); Export Diagnostics' device.txt lists the apps that answer it.
- [ ] With Continue Watching's Include Other Apps on in the backup, restore it on a TV (or after clearing storage) where Plasma hasn't got the permission: after Plasma starts again, Android asks. Allow: other apps' items show in Continue Watching. Don't allow: a notice says Include Other Apps is off, and it is.
- [ ] Restore from Backup lists the backup on the drive, once. Choosing it shows what it holds: the sources, profiles, favorites, and reminders and recordings. It also says it replaces this TV's.
- [ ] Restore with Logins with a wrong passphrase: it says so, and nothing has changed.
- [ ] With the right one: Plasma starts again, and your sources load. Favorites, hidden and locked channels, categories and their order, your own numbers, profiles and their PINs, the parental PIN and the settings are as they were.
- [ ] The real test: back up with logins to the drive, unplug it, then clear Plasma's storage. Plug the drive back in. On the welcome, choose Restore from Backup and restore it. Channels play.
- [ ] Clear Plasma's storage with the drive plugged in: the backup is still in Download/Plasma on the drive (Android empties only Plasma's own folder there). Restore from Backup doesn't list it then; with a file manager app on the TV, Choose a File opens it from Download/Plasma on the drive.
- [ ] Without logins on a fresh Plasma: the sources come back, and Restore names them as needing their logins. Edit in Portals & Playlists adds them.
- [ ] From Your Phone: send the backup from the phone's page, with or without the passphrase. The TV shows what it holds before anything changes.
- [ ] Plasma Update: after the next update, Restore from Backup also lists a backup "Before an update".

## A channel that sends HLS from a ".ts" address (3 November)

- [ ] The channel that played only in VLC: with Switch Player, it plays in mpv past its first few seconds, and keeps playing.
- [ ] It plays in the Android player too.
- [ ] A channel that plays in mpv already (a plain one) still does, and when its server drops it, it comes back by itself.

## Close on the error card (3 November)

- [ ] A channel that can't play (one your provider has dropped): the card says why, with Try Again and Close, and Try Again has focus.
- [ ] Right, then OK on Close: the player closes, back where you opened it from. Back does the same.
- [ ] A movie that can't play: the same.

## The error card's buttons (4 November)

- [ ] A channel that can't play: the card has Try Again, Switch Player, Report and Close, and Try Again has focus. Left and Right move between them.
- [ ] A channel with more than one source: Switch Source too, after Try Again. It lists the channel's sources; Back goes back to the card, on Switch Source, and picking another source plays the channel from it.
- [ ] Switch Player: the channel plays with the next player (VLC, then mpv, then the Android player). If that one can't play it either, the card comes back with Switch Player focused, to try the next.
- [ ] Report: Report a Problem's QR code, as in Settings → About. The phone page's diagnostics have the failure in `plasma.log` (search for FAILED). Back goes back to the card, on Report.
- [ ] A movie that can't play: Try Again, Switch Player, Report and Close.
- [ ] Too many connections at once (a second TV or app on the same account playing): no Switch Player, since another player gets the same answer.
- [ ] Close and Back still leave the player.

## Small items: the clock, Home's banner and the keyboards (4 November)

- [ ] Settings → Appearance → Clock → 12-Hour: the clock on screen shows "8:41 PM" at once. Back in Live TV and the Guide, their times are in the 12-hour clock straight away; 24-Hour turns them back as quickly.
- [ ] Play a channel from Live TV's Favorites, go Home, and press OK on the banner: the channel opens full screen, and channel up and down go through Favorites. Played from a group, it goes through that group. Played from All Channels, through every channel.
- [ ] Played from Favorites, the banner shows the channel's number in Favorites (2 for the second favorite), as the player does. From All Channels, its own number.
- [ ] Back from the channel the banner opened: Live TV, on the list it was played in, with the channel picked and playing in the corner. Back again: Home.
- [ ] On a keyboard (Settings → Portals & Playlists → Add a Source, say): Left on a row's first key goes to its last key, and Right on the last key to the first, in every row. Holding Left stops on the first key, and one more press goes round.
- [ ] Search with nothing typed: the same. With results showing, Right from a row's last key goes on to the results, as before.

## Pause and Rewind in first-time setup (4 November)

Settings → About → Set Up Plasma Again, then Set Up Plasma:

- [ ] After Your TV Service comes Pause and Rewind, "Step 2 of 6", with focus on Rewind Buffer, When I pause · 1 hour (or what you chose before).
- [ ] OK on Rewind Buffer offers Off, When I pause and Always, as in Settings → Playback → Rewind and Local Catch-up. What you choose shows there too, and the rewind buffer works that way.
- [ ] Continue goes on to Home Screen, "Step 3 of 6"; Back from there returns to Pause and Rewind.

## mpv as live TV's default engine (4 November)

- [ ] Settings → Playback → Player Engine: on a TV where it was never changed, mpv is chosen, and the cards show mpv as Default and VLC as Backup. Your TV has VLC chosen, so pick mpv there.
- [ ] Channels play with mpv: zapping, the first picture, Freeze Watch.
- [ ] If a Stream Won't Play reads "Try VLC, then Android player". A channel mpv can't play goes to VLC, then to the Android player.
- [ ] With VLC chosen, it reads "Try mpv, then Android player", as before.
- [ ] Multiview's tiles still play with VLC.

## A recovery code for the PIN (4 November)

- [ ] Settings → Parental Controls → PIN → Change PIN: after the new PIN twice, "Your Recovery Code" shows a code like 7KQX-M4TR-9WCH, with Done. Write it down.
- [ ] Lock a channel and open it: the PIN screen has Forgot PIN. Choose it, type the code on the keys (or a keyboard), then a new PIN twice: a new code shows, and back on the PIN screen the new PIN opens the channel.
- [ ] A wrong code says how many tries are left; after five wrong tries (PINs and codes together), it waits a minute.
- [ ] Settings → Parental Controls → PIN → New Recovery Code shows a new one; the old one no longer works.
- [ ] A profile with its own PIN (Settings → Profiles → the profile → PIN): its code shows when the PIN's set, and Forgot PIN on Who's Watching takes it.
- [ ] Turn parental controls off and on again with a new PIN: the old code doesn't work.

## Check Movies for Dolby Vision (4 November)

- [ ] Settings → Playback → Advanced → Check Movies for Dolby Vision: With two connections or more, Always and Never, with the first chosen.
- [ ] On your server (no connection limit), a movie whose page shows no picture details (no 4K, HDR or codec badges) and that's in Dolby Vision: it plays in the Android player (Player Options says so), after a moment's loading.
- [ ] The same movie with the setting at Never: it plays in VLC or mpv, as before.
- [ ] A movie without details that isn't in Dolby Vision: it plays in your usual player, with no more than a short wait first.
- [ ] Play the same movie again without leaving Plasma: it starts without the extra wait.

## Audio Delay's slider (4 November)

With VLC or mpv playing:

- [ ] Settings → Playback → Audio → Audio Delay: a bar at the bottom, "Audio delay · Every channel" with the delay. ◀ ▶ move it 10 ms at a time, and holding them moves it on. OK keeps it, and the row says it ("+120 ms"); a channel played next has it. Back instead puts back what it was.
- [ ] If you'd chosen +50 ms or the like before this build, the slider starts there.
- [ ] Bluetooth headphones, or a soundbar that's late: around +150 to +250 ms, lips and sound line up, and the next channel keeps it.
- [ ] In the player, Menu → Audio Delay ("0 ms"). OK → the bar over the picture, with the channel's name. ◀ ▶ → the sound moves at once. OK → the picture alone; Menu again → the row says the delay. Zap away and back → that channel has it, the others don't.
- [ ] Open the bar, move it, then Back → Player Options, and the sound is where it was. Open it, move it, then CH + → the next channel plays, and the first one's delay is as it was.
- [ ] Switch Player to the Android player: the row says "Not with the Android player"; OK → the bar says why, with Switch Player, which goes to the next player.

## A number pad in the player (4 November)

- [ ] Settings → Remote & Buttons → Long-Press OK: Options, Number Pad and Nothing, with Options chosen. Choose Number Pad.
- [ ] Full screen on a channel, hold OK: the number pad opens over the blurred picture, with focus on 1.
- [ ] Type a channel's number on the pad: it shows the channel's logo and name, and nothing tunes until you choose Watch. Watch tunes it.
- [ ] A number no channel has says "No channel …", with no Watch.
- [ ] The remote's number keys type on the pad too, if they reach Plasma. Back closes the pad and nothing changes.
- [ ] Watching from Favorites, the pad counts from 1 as Favorites does.
- [ ] Menu still opens Player Options, and so does the row under the controls.
- [ ] Long-Press OK on Options: holding OK opens Player Options, as before. On Nothing: a held OK is an OK.
- [ ] On Number Pad, holding OK elsewhere (a channel in Live TV's list, a program in the Guide) still opens its options.

## Plasma's start (6 November)

- [ ] Force stop Plasma (Settings → Apps → Plasma), then open it: a plain dark screen, then the plasma wordmark comes together and its wave starts to move, then Home fades in. No icon on a splash, and no blank screen between.
- [ ] On a TV where loading takes a while: "Loading channels…" shows under the word after a couple of seconds, and goes with it.
- [ ] Settings → Appearance → Reduce Motion on, then force stop and open again: the word fades in still, and Home fades in over it.
- [ ] When the TV Turns On on Last Channel, with Plasma force stopped: turn the TV off and on. The channel comes up without waiting for the word.
- [ ] Press Home in Plasma, or go to another app and back: no launch screen.

## The logo in the app (6 November)

- [ ] The TV's apps row and Settings → Apps show Plasma's new banner and icon: the round mark with three waves, beside "plasma" cut by a wave. (Some launchers keep the old ones until Plasma is updated again or the TV restarts.)
- [ ] The top bar, Who's Watching and first-time setup's welcome show the wave wordmark; so does Plasma's screensaver, faintly, at the bottom right.

## Recordings outlast Plasma (6 November)

- [ ] Settings → Recording → Record To: the text under it says recordings are kept in Download/Plasma/Recordings, where they stay if Plasma is uninstalled. (On Android 10 it says they're in Plasma's own folder.)
- [ ] Recordings made before this build: half a minute after Plasma starts, they move to Download/Plasma/Recordings on their drive, and still play from Recordings. A file manager shows them there, each with its .json and .idx.
- [ ] Record a program onto the USB drive: it's written to Download/Plasma/Recordings on the drive, and plays as it records and afterwards.
- [ ] With Record To → Network folder, a recording still goes straight to the share, and the drive's Download/Plasma/Recordings gets no `.spool` folder.
- [ ] Settings → Apps → Plasma → Clear Storage, then set Plasma up again (or Restore from Backup): Recordings is empty and offers Find Recordings. Choose it: no question from Android, and a notice says how many it found. They're back, with their pictures and details, and play.
- [ ] Uninstall Plasma with the USB drive plugged in, then install it again: the recordings are still on the drive. Find Recordings asks whether Plasma may see videos (allow it), then finds them, named as their files say, and they play.
- [ ] Delete one of those in Recordings: Android asks whether Plasma may delete it. Allow: it's gone from the drive. Don't allow: it's gone from Recordings but stays on the drive, and Find Recordings finds it again.

## Android TV Settings (6 November)

- [ ] Settings: Down past About, **Android TV Settings** is the last row. OK opens the TV's own settings; Back comes back to Plasma's Settings, on that row.
- [ ] With a channel playing behind Home, it goes to the small window, and comes back when you return.
- [ ] With Lock Settings on, Settings asks for the PIN first, as before.

## Encode Surround to Dolby Digital (6 November)

On a TV that takes only PCM, like the Chromecast on yours:

- [ ] Settings → Playback → Audio → Encode Surround to Dolby Digital on: a notice says the TV's sound output doesn't take Dolby Digital, so it changes nothing here. With Stereo Downmix on, it says that instead.
- [ ] With it on, channels play as before: the same player, and Player Options' stream info as before ("decoded").
- [ ] Home's picture is silent with mpv, and full screen the sound comes up, as before.

With a soundbar or receiver that takes Dolby Digital on optical or ARC, if you have one:

- [ ] An AAC 5.1 channel: the soundbar shows Dolby Digital, and Player Options' stream info says "AAC 5.1 as Dolby Digital". With VLC playing, mpv takes over once the sound is known. Back on Home its picture is silent; full screen, Dolby Digital again after a moment.
- [ ] A Dolby Digital channel passes through as before ("AC-3 5.1 passthrough"), and a stereo channel stays PCM.
- [ ] Encode Surround off: the AAC 5.1 channel plays decoded again, in stereo.

## The providers' adult marks (6 November)

With a parental PIN set and Settings → Parental Controls → Lock Adult Groups on, on a source whose provider marks adult channels or movies:

- [ ] Settings → Portals & Playlists → the source → Refresh Now. Groups with channels the provider marks adult need the PIN, even where their names don't say. Parental Controls → Locked Groups tags them Adult; OK on one unlocks it.
- [ ] Movies and Series: categories with titles the provider marks adult are locked like those with adult words, and their titles stay out of Search.
- [ ] A kids profile doesn't see those groups or categories.
- [ ] Lock Adult Groups off: they're open, as before.

## A portal's past days (6 November)

With a Stalker portal whose channels have catch-up (skip without one):

- [ ] Settings → Portals & Playlists → the portal → Refresh Now, or the guide's daily update. In the Guide, a channel with catch-up shows its programmes back as far as Keep Past Days and its archive allow, and Catch-up lists them; they play from the archive.
- [ ] A channel without catch-up has no past programmes, as before.

## Crashes and ANRs in the log (7 November)

- [ ] Use Plasma for a day as usual: zapping, the Guide, Multiview, recordings. Then Settings → About → Export Diagnostics. If Plasma stopped or froze at any point, the log has a "[crash]" line saying how, with whether it was on screen.
- [ ] If Plasma ever says it isn't responding and you choose Close, the next start's log has "Not responding (ANR)" with its first lines of trace.

## The device matrix (8 November)

Brief 6.9's devices, remotes and sound systems (#129), what's on hand to test them, and what nothing covers yet.

**On hand:**

| Device | What it covers | What it doesn't |
|---|---|---|
| Chromecast with Google TV 4K (Google TV, Android 14, 32-bit) | The Google TV remote, with no Menu, colour, number or CH keys. Plasma as the TV's Home, and its two Home Screen methods. Its hardware decoders, 4K and HDR. A TV that takes PCM only. | Sound passing through to a sound system. A remote with every key. |
| Android TV emulator | Android 8.0 (Plasma's oldest) to 16. Low RAM. First-time setup on a fresh TV. Every key, with `tools/remote.sh`. | Hardware decoding, HDR and passthrough. |
| Fire OS device | App-only mode (#126). | Plasma as the Home screen. |
| eARC receiver | Dolby, DTS and TrueHD passing through. Decoded surround as PCM. A receiver switched on and off mid-stream. | A soundbar on ARC and optical, where Encode Surround to Dolby Digital does its work. |

**6.9's list:**

| 6.9 asks for | Covered by |
|---|---|
| Google TV Streamer | Nothing yet |
| Chromecast with Google TV, HD and 4K | The 4K. For the HD's 1.5 GB, the low-RAM emulator. |
| Nvidia Shield TV | Nothing yet |
| A Formuler or MyTVOnline-class box | Nothing yet |
| A generic S905X4 box (6.7's budgets are for one) | Nothing yet. The Chromecast 4K is the nearest. |
| A Google TV television | Nothing yet |
| A low-RAM device, 1 to 1.5 GB | The low-RAM emulator |
| Fire TV, as an app only | The Fire OS device |
| A Google TV remote | The Chromecast's |
| A MAG-style IR remote with every key | `tools/remote.sh` on the emulator or over adb; Remote Key Test with any remote the TV takes |
| The TV's speakers (PCM) | The Chromecast's TV |
| A soundbar on ARC (Dolby Digital and DD+) | Nothing yet |
| An AV receiver on eARC (TrueHD and DTS-HD MA) | The eARC receiver |
| Optical only (Dolby Digital and DTS) | Nothing yet |
| Bluetooth headphones (stereo PCM, audio delay) | Any pair the Chromecast takes |
| A receiver switched on and off mid-stream | The eARC receiver |
| A receiver that claims DTS-HD but stays silent | Nothing yet |

**Emulators for what's missing.** With Android Studio's command-line tools (`sdkmanager` and `avdmanager`, in the SDK's `cmdline-tools/latest/bin`):

```
sdkmanager "system-images;android-26;android-tv;x86" "system-images;android-30;android-tv;x86" \
  "system-images;android-34;google-tv;x86" "system-images;android-36;android-tv;x86_64"
avdmanager create avd -n tv-oldest -k "system-images;android-26;android-tv;x86" -d tv_1080p
avdmanager create avd -n tv-low-ram -k "system-images;android-30;android-tv;x86" -d tv_1080p
avdmanager create avd -n google-tv-14 -k "system-images;android-34;google-tv;x86" -d tv_1080p
avdmanager create avd -n tv-newest -k "system-images;android-36;android-tv;x86_64" -d tv_1080p
```

For the low-RAM one, set `hw.ramSize=1024` in `~/.android/avd/tv-low-ram.avd/config.ini`. On Apple silicon, use the `arm64-v8a` images in place of `x86` and `x86_64`.

**Keys a Google TV remote doesn't have:** `tools/remote.sh` sends them over adb, to the emulator or to a TV with network debugging on. `tools/remote.sh red`, `tools/remote.sh 1 0 5 ok`, `tools/remote.sh ch+`, and `-s <serial>` for one of several. Running it with no keys lists the rest.

**6.9's scenarios, on each device.** Tick each where it's been checked:

| Scenario | Chromecast | Emulator, low RAM | Emulator, Android 8.0 | Fire OS |
|---|---|---|---|---|
| 1,500 channels and 200k programmes (`big.m3u` from `tools/torture`, #130) | | | | |
| Provider errors and an expired account; HTML in place of JSON | | | | |
| A bad network (slow, dropping) | | | | |
| A time-zone change, and a change to or from daylight saving | | | | |
| A USB drive removed while recording | | | | |
| The connection limit reached | | | | |
| A locked channel in Favorites | | | | |
| A reboot with a recording scheduled | | | | |
| Projectivy's override and Plasma's both on | | | | — |
| The accessibility switch restricted after a file-manager install | | | | — |
| The wireless-debugging port changing after a reboot | | | | — |
| A system update turning Google TV Home on again | | — | — | — |
| Restore Google TV Home, on the TV and from a computer | | — | — | — |

## TalkBack (8 November)

Turn on TalkBack in the TV's Settings → Accessibility (or with the TV's accessibility shortcut, holding Back and Down, where it has one). Turn it off again afterwards.

- [ ] Home, Live TV, the Guide's toolbar, Movies, Series and Settings: as focus moves, TalkBack reads each thing once. A row is read by its words, a tile or poster by its title, and a channel by its number, name and what's on, then marks such as Favorite.
- [ ] Settings: a switch row says On or Off. In a list of options, the chosen one says "Selected", and the others don't say "Not selected".
- [ ] The Guide: moving through the grid, TalkBack reads each program with its channel and time.
- [ ] Full screen: TalkBack says the channel and what's on. CH+ or CH− says the new channel.
- [ ] The player's controls: each button says its name. The timeline says where it is, and again after ◀ or ▶, but not while the program plays.
- [ ] Player Options → Audio Delay: the slider says its value at each step.
- [ ] Settings → Remote & Buttons → Learn a Key and Remote Key Test say what to do, then each key.

## Voice search (8 November)

- [ ] Search: Down to the bottom row and Left to the mic key at its end, then OK. Android's speech screen opens; say a channel's name. Back on Search, what you said is the query, and the channel is among the results.
- [ ] Back on the speech screen without saying anything leaves the query as it was.
- [ ] The remote's own mic button works as before: it belongs to Google.

## Advanced and a playlist file on the phone (6 November)

- [ ] Settings → Portals & Playlists → Add Portal or Playlist, and scan the QR code with a phone. Under each kind's fields there's **Advanced**, folded; tapping it shows the same fields as the TV's Advanced, with lists for the choices.
- [ ] M3U Playlist on the phone: type a playlist address, then under Advanced a user agent and Refresh channels → Every 6 hours, and send. On the TV, Advanced shows them, and the playlist's channels play after Add Source.
- [ ] Set something on the TV's Advanced first, then send from the phone without touching Advanced: the TV's Advanced keeps it.
- [ ] On the phone's M3U Playlist, choose an .m3u file (Or a playlist file) instead of typing an address, and send. The TV's Playlist field reads "name.m3u · from your phone"; Add Source, and its channels show.
- [ ] Refresh Now on that playlist: the same channels. Edit it, send a changed file from the phone, and Save: the changes show.
- [ ] A file that isn't a playlist, like a photo, is refused on the phone.
- [ ] Send a source from the phone: on the TV a sheet comes up, "Received from Your Phone", with the source's name, kind and address. Add Source connects with it.
- [ ] Send again and choose Dismiss: the sheet goes, the form keeps what came, and focus is on Name. Send once more and press Back: the same.
- [ ] The phone, once it has sent: "Check your TV: it shows what it received."

## A source's Categories (8 November)

- [ ] Settings → Portals & Playlists, with focus on your server: the Library line gives its channels, movies and series.
- [ ] OK on it → Categories: Live TV, Movies and Series, and each opens its own page.
- [ ] Hide a movie category, rename another and move one: Movies shows the changes. The same with a series category and Series.
- [ ] An M3U playlist with channels only, if you have one: Categories opens its Live TV categories straight away.

## Live TV's lists, Favorites first (8 November)

- [ ] Live TV: the lists go Favorites, Recently Watched, All Channels, then the groups.
- [ ] Play a channel from Favorites, one from Recently Watched and one from a group: each time, Live TV opens on that list again.
- [ ] Take the channel playing out of Favorites, go Home and open Live TV again: it opens on the channel's own group.
- [ ] On the emulator with a new install, before playing anything: Live TV opens on Favorites when it has channels, otherwise on All Channels.
- [ ] The Guide's Group button: Favorites, All Channels, then the groups. The Guide still opens on All Channels.
- [ ] Multiview → Change Channel: Favorites, All Channels, then the groups. It opens on the tile's channel in Favorites, or else in its group.
- [ ] The phone remote's Channels tab: Favorites, All Channels, then the groups.

## Reset Settings (6 November)

- [ ] Change a few settings: Text Size, Player Engine, Live TV's Channel List Shows, and a Home row turned off. Then Settings → About → Reset Settings: it says what goes back and what stays, and Cancel has focus.
- [ ] Cancel changes nothing.
- [ ] Reset Settings: Plasma starts again, and those settings are back to their defaults. Home's rows are in their first order.
- [ ] Sources, favorites, profiles and PINs, reminders and recordings, and hidden apps are all still there, and channels play.
- [ ] Back Up Settings First: it asks where (USB drive, this TV or your phone), saves the backup, then asks about the reset again.
- [ ] With a parental PIN and Lock Settings off: Reset Settings asks for the PIN first. Back on the PIN screen goes back to About, and nothing has changed.
- [ ] While a recording is being made: Reset Settings changes nothing, and a notice says to try once it has finished.

## Loading (6 November)

- [ ] Movies or Series on a big catalog: a category that takes a moment shows grey poster shapes with a band of light moving across them, then its posters.
- [ ] The Guide on a day not loaded yet: grey program shapes in each row, then the programs.
- [ ] Posters and logos fade in as they arrive, rather than popping in. Scrolling back to ones already seen, they're there at once.
- [ ] With Reduce Motion on, the shapes fade gently instead of the moving band.
- [ ] Nothing that loads quickly flashes grey shapes.

## Adding a source (6 November)

- [ ] Settings → Portals & Playlists → Add Portal or Playlist. Fill in a source and choose Connect. A screen shows the moving plasma wordmark, "Adding …" and a checklist. Signing in ticks, then Channels counts up (a playlist shows how many so far), then the guide, then movies and series.
- [ ] Load in Background while the channels load: you're back in Portals & Playlists, where the source says Loading…. When the channels are in, a notice says "Added …: N channels.", and they're in Live TV.
- [ ] Back while the channels load: the form again, on Connect, and the source isn't in Portals & Playlists.
- [ ] Wait until the channels are in: "Connected to …" with the account, and Done goes back while the guide, movies and series keep loading.
- [ ] A wrong password: Couldn't Connect with the reason, on the form, as before.
- [ ] In first-time setup, Load in Background goes back to Your TV Service, where the source's checklist says Channels · loading….

## Number Pad button (6 November)

- [ ] Full screen on a channel, OK, then Down: the row under the controls has Number Pad after Channel List.
- [ ] Number Pad opens the pad over the blurred picture, with focus on 1, whatever Long-Press OK is set to. Type a number and choose Watch: it tunes.
- [ ] Back on the pad closes it, and nothing changes.

## Reminders switch by themselves, and catch-up carries on (4 November)

Switch Automatically (#115):

- [ ] Settings → TV Guide → Switch Automatically, under Show over Other Apps: Off. Turn it on.
- [ ] Set a reminder for a program starting in a few minutes, and watch another channel. A minute before, the reminder says "Switches at" its start time. Ten seconds before, it counts down, "Switching in 10 s"; at zero the channel changes, full screen.
- [ ] Again, on Home or in Settings: at zero the channel opens full screen.
- [ ] Dismiss, or Back, while it counts: nothing changes.
- [ ] During a movie or Multiview: the reminder shows as before, with no count, and doesn't switch.
- [ ] In another app, with Show over Other Apps allowed: the reminder shows as before and doesn't switch.
- [ ] Turn on **Also Over Other Apps** (under Switch Automatically, once that's on). In another app, the reminder over it counts down too, and at zero Plasma opens on the channel, full screen. Dismiss or Back while it counts: nothing changes.
- [ ] Turned off: the reminder is as before.

Catch-up after leaving Plasma (#117):

- [ ] Play a program from catch-up and go 20 minutes in. Press Home or open another app, wait a couple of minutes, then come back to Plasma: it carries on at about 20 minutes, and the timeline says so.
- [ ] The same while it's paused: back in Plasma it plays on from where it was paused.
- [ ] Sleep Timer during catch-up: once it has stopped the picture, press OK, and it carries on where it stopped.
- [ ] Live TV is as before: back in Plasma, it plays from the live edge.

## Where Upcoming Events looks (6 November)

- [ ] Settings → Launcher & Home → Home Rows → Upcoming Events: Starting Soon (not "on Favorites"), and under "Where it looks", From: Favorites.
- [ ] With Upcoming Events on Home: only programs on favorites start soon there, and only favorites' sport shows.
- [ ] From → Groups: a Groups row shows, "0 picked". Pick a sports group: what's about to start in it, and its sport, show on Home; nothing from other groups.
- [ ] From → Channels: pick two channels. Everything starting within the hour on them shows, and their sport.
- [ ] Reminders and recordings still show whatever From says.
- [ ] A hidden group isn't in the Groups sheet, nor a hidden channel in Channels.

## Recording HLS in fMP4 (9 November)

For an HLS channel whose segments are fMP4. One whose recordings ended at once with Couldn't record is likely one.

- [ ] Record a program on it: Recordings shows it as REC, and it plays while it's recorded, with sound, from the start and from the live edge.
- [ ] Once it's done, it plays from Recordings with picture and sound together, and seeks.
- [ ] Its sound's language, under Audio and Subtitles, is the one the channel has.
- [ ] With Record To › Network folder, or Copy to Network Folder: the copy plays, and Jellyfin finds it.

## Screensaver Pictures (9 November)

Settings → Launcher & Home → Screensaver at its shortest wait makes these quicker.

- [ ] Launcher & Home → Screensaver Pictures → Your Photos: it asks where your photos are. On This TV or a USB Drive, with photos on a USB drive: Android asks to let Plasma see photos, then the drive's folders show. Use This Folder on one with photos.
- [ ] Let the screensaver come: your photos show one after another, each panning slowly, with the clock, the date and what's recording.
- [ ] A photo taller than it's wide shows whole, on a blurred copy of itself.
- [ ] Each Picture For → 1 Minute: a new photo every minute. Order → Shuffled: they come in another order.
- [ ] Unplug the drive: the screensaver goes back to Plasma's pictures.
- [ ] Photos From → On Your Network: sign in to your network storage, choose a folder with photos, and the screensaver shows them.
- [ ] With Use as the TV's Screensaver on, the TV's own screensaver in another app shows your photos too.
- [ ] Plasma's Pictures: the screensaver's four scenes come back.

## Photos on Home (9 November)

- [ ] Settings → Launcher & Home → Live Preview on Home → Photos: Photos on Home shows under it. On Home, Plasma's pictures show in the banner, a new one every 20 seconds, and no channel plays: no sound, and nothing from the source.
- [ ] Up from the dock goes to the top bar, and Down from there to the dock's Live TV, which opens Live TV.
- [ ] The dock and the rows read on a bright photo and on a dark one.
- [ ] Photos on Home → Your Photos, with a folder chosen in Screensaver Pictures: the banner shows your photos. One taller than it's wide shows whole.
- [ ] Let the screensaver come, then wake Plasma: the banner shows the photo it had, then carries on.
- [ ] With Reduce Motion on, the photos don't pan.
- [ ] Live Preview on Home → Muted: the last channel plays behind Home again.

## Performance budgets (8 November)

With Settings → Playback → Advanced → Show Stream Stats on:

- [ ] Zap through 20 or so channels with CH+ and CH−. The stats' last line says how long the last zap took to its first frame, and from the second zap on, the 95th percentile so far.
- [ ] Player Options → Switch Player: the line says how long the picture took in the other engine.
- [ ] On a channel VLC can't play, after the notice that mpv plays it: the line says how long the fallback took, from the fault to mpv's picture.
- [ ] Settings → About → Export Diagnostics: the log has a "Zap to picture" line for each zap, with the channel, the engine and the time.

Brief 6.7's budgets, and what's been measured. The test machine's times are for the readers alone, and a TV box is slower.

| Measure | Budget | How it's measured | On the TV | Test machine |
|---|---|---|---|---|
| Zap to first frame | 1 s at the 95th percentile, with the Small buffer | Show Stream Stats, the diagnostics log | | |
| Engine fallback, fault to first frame | 3 s | Show Stream Stats, the diagnostics log | | |
| Switch Player to first frame | 2 s | Show Stream Stats, the diagnostics log | | |
| M3U of 100,000 lines | 15 s | `M3uBudgetTest` | | 0.5 s |
| XMLTV of 150 MB | 90 s | `XmltvBudgetTest` | | 2.4 s |
| Numbering, 1,500 channels from 3 sources | 300 ms | `ChannelNumbererTest` | | 36 ms |
| Cold start, live preview, key to focus, the Guide at 1,500 channels, memory, CPU | 6.7's table | On the TV, and macrobenchmarks still to come | | |

## Nothing plays under the screensaver (9 November)

- [ ] Settings → Launcher & Home → Screensaver → After 5 Minutes, and Live Preview on Home → With Sound. Wait on Home: when the screensaver shows, the sound stops.
- [ ] A key: Home is back, and the banner's channel plays again after a moment, with sound.
- [ ] The same from Live TV, with a channel playing in its preview, and from the Guide.
- [ ] Leave the screensaver up for longer than the TV's own sleep or screensaver wait: the TV goes to sleep, or to its own screensaver.
- [ ] With a recording under way: the screensaver shows, and afterwards Recordings still shows it recording.
- [ ] Full screen on a channel: the screensaver doesn't come, as before.

## Search's ring (10 November)

- [ ] Search on a big guide: type a word quickly. The magnifier at the start of the query line stays as it is while you type.
- [ ] Stop typing on something that takes a moment to find: the magnifier turns into a turning ring until the results come, then it's the magnifier again.
- [ ] A channel opening full screen: the ring in the middle after a moment looks as before. With Reduce Motion on, it stands still instead of turning.

## Home's first look as Plasma starts (10 November)

- [ ] Stop Plasma Dev (the TV's Settings › Apps, then Plasma Dev, Force stop), then open it: Home comes up sooner than before, with what's on under the channel at the top and under each favorite, as before.
- [ ] Open it again, stopped, a few times: the launch screen goes as soon as Home is ready, the word whole or not, and fades as before; on a slow start it stays, and "Loading channels…" comes under it after 2.5 s, as before.
- [ ] Right after it comes up, open Live TV, then the Guide: if the guide is still loading, the wave wordmark flows for a moment, as on the launch screen; then every channel has its guide, as before, and nothing shows empty and then fills in.
- [ ] With Home Button set to Live TV or the Guide, press Home with Plasma stopped: that screen opens with every channel's guide, as before.
- [ ] With Upcoming Events on in Home Rows, stop Plasma and open it: the row is there from the start, with the same programs as a minute later.
- [ ] With Live Preview on Home on, stop Plasma and open it: Home comes up, and the channel's picture starts behind its banner a moment later, as before.
- [ ] With When the TV Turns On set to the last channel, stop Plasma, then turn the TV off and on: the channel plays as before.

## Fewer compositions at each move (11 November)

- [ ] Live TV: move up and down the list, a step at a time and holding the key: the focused row grows and the others settle as before, and what's on and coming up follows once focus rests.
- [ ] Live TV: Right from a channel goes to its coming-up list, and Left comes back to the same row; OK plays a channel in the picture, and its row is marked as playing.
- [ ] Live TV, Favorites: take a channel out of Favorites (long press or Menu): it stays until focus moves off it, then goes.
- [ ] The Guide: move around the grid, and across the time edges: the focused programme and row show as before, and the time header and the now line move with the window.
- [ ] Another category in Live TV, another day or group in the Guide: the list fades in as before.
- [ ] Settings: move up and down a list, and through a sheet's options (long-press OK on a channel): the focused row grows as before.
- [ ] Home: from the dock down to the first row, on down the rows and back up: the hero, the top and the rows above the top one fade as before, and the blurred backdrop behind the rows comes and goes as before. The dock tiles brighten with focus as before.
- [ ] With Large Text, or Android's largest font size: channel numbers in Live TV, the Guide and Settings › Channels show whole.
- [ ] Movies and Series: move around the grid, then open a title and come Back: focus is on the same poster. Another category starts at its first poster, and Yellow (Sort) keeps the focused title in focus.
- [ ] Recordings: move around the grid; delete a recording: focus goes to the one beside it.
- [ ] Home: along Continue Watching and the other rows, the blurred backdrop follows the focused item as before.

## A channel opening in a picture (11 November)

- [ ] Live TV: OK on another channel, so it plays in the picture. Until its picture comes, the picture is dark right to its rounded corners, with nothing of the last channel in them.
- [ ] A channel that takes a moment to open: after about half a second a ring turns in the middle of the picture, and it goes as the picture comes. A channel that opens at once shows no ring.
- [ ] The same in the Guide's picture: OK on a program on now on another channel.
- [ ] A channel that won't play: the ring goes, and the picture stays dark, as before.
- [ ] Full screen, a channel opening has the player's own ring in the middle, as before, and only that one.
- [ ] Home's picture behind the hero, while the last channel opens as Plasma starts: as before, with no ring.

## Less drawn at each frame (11 November)

Nothing should look different anywhere:

- [ ] Live TV: move up and down the list, with single presses and with the key held, then rest. The top bar, the categories, the key hints and what's on under the picture look as before, and what's on follows focus once it rests, fading in as before.
- [ ] Live TV: Left into the categories and down them: the focused category lifts with its shadow, whole, as before, and the one at the bottom edge is faded until focus reaches it. Back to the list: the categories look as before.
- [ ] Live TV: Right into what's coming up and down it, then back.
- [ ] Live TV and the Guide, with single presses and with the key held: each row looks exactly as before, sharp throughout, as focus or the cursor reaches it and as it leaves; the focused row grows and lifts, and the one it left settles back.
- [ ] The picture's rounded corners in Live TV and the Guide, over a playing channel: as before, with no line or step around the picture.
- [ ] The Guide: move around the grid and across the days. The selected program at the top, the days, the group button and the times look as before, and follow the cursor and the window.
- [ ] Movies and Series: move around the posters and through the categories; Yellow (Sort) changes the order, and the header over the posters says so.
- [ ] Up to the top bar from any of these screens, and along its tabs: the focused tab lifts as before.
- [ ] Settings, and a sheet's options (long-press OK on a channel): move up and down; the focused row grows, and its words sit where they did.
- [ ] With Large Text (Settings › Appearance › Text Size): the same rows, and Live TV's list, look as before.

## Text set before the first screen (11 November)

- [ ] Restart the TV, then open Plasma: the launch screen, then Home, as before, with Home's words in Plasma's own type.
- [ ] A channel or group whose name has an emoji or a flag in it shows it as before.
- [ ] With the speed-test apps, the cold start's time to full display, against the run before.

## Live TV's schedule rows (12 November)

Nothing should look or act different.

- [ ] In Live TV, move down the channels one at a time, resting on each: each channel's details fade in under the picture as before, with what's on now, its description and its schedule from its first program.
- [ ] Rest on a channel with a long schedule, then on one with a short one, then on one with none: each shows its own programs and none of another channel's.
- [ ] Right from a channel goes to the first program in its schedule. Go down two programs, Left, then Right again: focus comes back to the program it was on.
- [ ] Left, rest on another channel, then Right: focus is on that channel's first program.
- [ ] Hold Down: the details don't change until focus stops on a channel, as before.

## Blurred pictures kept as layers (12 November)

Nothing should look different anywhere:

- [ ] Home: down from the dock into the rows, then along New in Movies and New in Series, a press at a time and with the key held. The blurred picture behind the rows follows the focused poster as before, each fading into the next, and a poster's picture seen for the first time comes in softly, not at once.
- [ ] Home: back along the same posters. Their pictures come back behind the rows, one fading into the next as before.
- [ ] Home: down to Apps and along it, then up to the first row and the dock. The picture behind the rows goes as the top of Home comes back, as before.
- [ ] Home, Continue Watching with a movie that has only a poster: the poster stands sharp over its blurred enlargement as before, as the row scrolls and as the tile grows with focus.
- [ ] Home with Live Preview on Home off (Settings › Launcher & Home): the hero's picture shows as before.
- [ ] Movies or Series: open a title that has only a poster. Its page has the poster blurred and darkened behind it as before, coming in softly; move through its buttons and More Like This, then Back.

## Apps' pictures held by the GPU (12 November)

- [ ] Home's Apps row, and Apps: every app's banner, or its icon on graphite, shows as before, as the row scrolls and as a tile takes focus.
- [ ] Install or update an app, then open Apps: its tile shows its banner or icon as before.

## Lighter Effects (12 November)

- [ ] Settings › Appearance: Lighter Effects is under Reduce Motion, and off. Its description says what it does.
- [ ] With it off, Home's rows are as before: along New in Movies, the blurred picture behind the rows follows each poster as focus reaches it.
- [ ] Turn it on, then Home's rows, a press at a time: the picture behind the rows changes a moment after focus lands on each poster.
- [ ] With it on, hold Right along New in Movies: the picture behind the rows stays as it was while the row moves, and changes to the poster focus stops on. The row moves at least as smoothly as with it off.
- [ ] With it on, the picture also follows focus from one row to the next, and into Continue Watching and Apps, a moment after focus lands.
- [ ] Turn it off again: the picture follows focus at once, as before.

## Home's live picture, in reach (12 November)

With Live Preview on Home on Muted (Settings › Launcher & Home), and a channel playing at the top of Home:

- [ ] Down to the first row, then the second, and wait: back Up, the live picture is there at once, still playing.
- [ ] Down to the third row and straight back up to the top: the picture is still playing, as before.
- [ ] Down to the third row or lower and stay there for ten seconds, moving along the rows: they move at least as smoothly as before. Then Up, row by row: by the time the top shows, the picture is playing again, or comes within a moment.
- [ ] From far down, after ten seconds: Back. Home goes to the top, and the picture opens as it does when Home first appears.
- [ ] Set Live Preview on Home to With Sound: its sound carries on all the way down Home, as before, and the picture is there at once on the way back.
- [ ] Far down Home for a while, then open a movie from a row and come back: Home is as it was left, and the picture at the top plays again once the top is in reach.

## Blurred pictures from a smaller copy (12 November)

Nothing should look different: each blurred picture is as soft as before, with the same colors in the same places, and no blocks, bands or shimmer in it.

- [ ] Home: down into the rows, then along New in Movies and New in Series. The blurred picture behind the rows looks as before, standing still and as one fades into the next.
- [ ] Home, Continue Watching with a movie that has only a poster: the blurred enlargement behind the poster looks as before, as the tile grows with focus.
- [ ] Home with Live Preview on Home off: where the hero's picture is a blurred poster, it looks as before.
- [ ] Movies or Series: open a title that has only a poster. The blurred poster behind its page looks as before.

## Home's backdrop painted only where it shows (12 November)

Nothing should look different.

- [ ] Home: down into the rows, then on down through them and along them. The blurred picture behind the top of the rows and the dark it fades into look as before, with no line across the screen where the picture ends.
- [ ] Back, which goes to the top, then down into the rows again: Home's top and its rows fade into each other as before.

## No plate under a poster that covers it (12 November)

Nothing should look different.

- [ ] Home's rows, Movies, Series and Search: posters look as before, at rest, with focus and as a screen opens, with the same rounded corners and no dark or light line around them.
- [ ] As a screen of posters slides in and as Home moves down a row: no flicker along the posters' edges while they move.
- [ ] Movies or Series, in a category not opened before, so that its posters come from the network: each poster fades in over its gray plate as before, with nothing of the screen behind showing through it as it comes.
- [ ] Move quickly down a long grid and back up: the posters already seen are there at once, as before.
- [ ] A title with no poster has its name on the gray plate, as before.
- [ ] With a computer, on an Android TV emulator or a TV at 1080p: `./gradlew :core:design:connectedDebugAndroidTest` passes. It draws a poster's tile with its plate whole and with the plate's frame alone, at rest and between pixels, and holds the two the same to the pixel as the device's GPU draws them.

## Reporting a problem

Open an issue with the bug form. Say what you pressed, what you expected and what happened, and add a photo of the screen if it helps.
