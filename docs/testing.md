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

Type the server's address and your login on the TV, or on your phone with Add a Source's QR code ("Add a Source from your phone", under Milestone 3). They never go into a file, an issue or a message, and Plasma keeps them encrypted.

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
- [ ] The line under a focused poster has its year and category.
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

- [ ] Play a movie: the picture comes up with the overlay (title, year, badges, the bar); it hides after 5 seconds.
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
- [ ] Home Button Override reads Off. OK → a sheet says what Plasma sees and never reads. Open Accessibility Settings → the TV's Accessibility settings. Turn Plasma Dev on there and allow it. (If the switch is greyed out, see below.)
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

If the switch in Accessibility is greyed out (Android 13 and later, after installing the APK from a file): the sheet says so, and `adb shell appops set ca.gureet.plasma.dev ACCESS_RESTRICTED_SETTINGS allow` from a computer releases it.

With Wireless debugging on and Plasma paired (see "Replace Google TV Home from the TV"), Home Button Override can also be turned on without Accessibility, greyed out or not:

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
- [ ] Rewind Buffer → Off: Play/Pause does nothing on a channel without an archive. On a channel with one, pausing holds the picture, and Play plays the archive from that moment, "behind live" in the overlay. Go Live goes back to live TV.
- [ ] Catch-up: while a program from the archive plays, Left and Right on the timeline move within it.
- [ ] Switch Player while behind live: the other player plays live TV. Pause and rewind on it too (VLC, mpv and the Android player).
- [ ] In the small window (picture-in-picture), Play/Pause in its menu pauses live TV into the buffer, and Play carries on from there.

### Recording a program (board 17)

- [ ] In the Guide, OK on a program coming up → Record: a red dot on it, in the Guide, on Live TV and in Search. Recordings › Coming Up and Scheduled list it, with its time.
- [ ] Record a program that starts in a few minutes, then leave Plasma, or turn the TV off: it records anyway. Recordings shows it as REC while it does, and the screensaver's line says so.
- [ ] Afterwards, OK on it in Recordings plays it from the start; leave halfway and OK again carries on from there. Near its end, it's marked watched.
- [ ] Record the channel you're watching: it keeps playing on the same connection (a source with a connection limit of 1 shows it best).
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

## Reporting a problem

Open an issue with the bug form. Say what you pressed, what you expected and what happened, and add a photo of the screen if it helps.
