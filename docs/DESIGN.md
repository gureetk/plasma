<!--
This is the design and build brief as handed over, version 1.5. In this repository the hand-off
pack lives in docs/design/: the approved boards as screens/NN-name.jpg, the mock art in assets/
and the fonts in fonts/. Decisions the owner has made since are in docs/decisions.md, and they
take precedence over section 9.2.
-->

# Plasma: IPTV launcher for Android TV

**Design and build brief for the implementation phase** · Version 1.5 · 22 October 2026

> **Decided by the owner**
> - The app is called **Plasma** (it was "Aster" in earlier drafts).
> - **Plasma will be open source on GitHub** (section 6.11).
> - Plasma is distributed as a **sideloaded APK** (no Google Play for now). On Google TV, the **accessibility Home Button Override** (the Projectivy technique) is the main way Plasma takes over the Home button; it works on the owner's TV. An **ADB option that replaces Google TV Home** must also be offered, both from a computer and from the TV itself (sections 4.23, 4.24 and 5.13).
> - **libVLC is the default player engine**, because it handles IPTV streams better than ExoPlayer in the owner's experience. **mpv is the backup.** The Android player (Media3) stays for Dolby Vision and HDR10+ movies and as the last resort. TiviMate-style playback settings include audio passthrough per format, decoding or re-encoding the sound, and per-source overrides (sections 4.27, 4.28, 5.4 and 6.5).
> - **The engines are invisible.** They are libraries inside Plasma, so number entry, the zap banner, the guide and every overlay are Plasma's own whichever engine plays. VLC's and mpv's apps never open.
> - **Channel numbers** come from the playlists, duplicates are fixed and shown for review, and Favorites and groups can count from 1 or keep the full-list numbers (4.29, 4.30, 5.2).
> - **Local catch-up:** a rewind buffer for the channel being watched, plus chosen channels recorded all the time into a rolling window, with limits on time and storage (4.31, 5.5).
> - **Continue Watching can include other apps**, opt-in: Android's Play Next row, as Projectivy does, and a Jellyfin server (4.32, 5.19).
> - **From the idea backlog, approved for v1:**
>   - a **phone remote and keyboard**: a web page Plasma serves on the home network, not a separate app (4.33, 5.21);
>   - **stop streaming when the TV goes to standby** (5.20);
>   - **picture-in-picture** when the user leaves Plasma, on TVs that support it (5.20);
>   - a **"Started 20 min ago" hint** that offers Watch from the Start (4.11);
>   - **per-channel volume levels** (4.28, 5.4.6);
>   - **remembering which source works best for each channel** (5.1);
>   - **recordings copied to a network folder (NAS)**, named so Jellyfin picks them up (4.34, 5.6).
> - **Phone remote apps as well as the web page:** the web page stays, and optional **Plasma Remote apps for Android and iPhone** are added for people who prefer an app. They use the same protocol and come in M9, after the TV app's first release (5.22).
>
> **New in 1.5:** the Plasma Remote apps (5.22, M9), the remote protocol written down for the web page and the apps to share (5.21), and a note on Android 17's new local network permission, which matters for the TV app too (6.1).
>
> **New in 1.4:** the approved backlog items above, two new boards (Phone Remote and Settings › Recording), and a Channel Volume Levels row on the Audio board.
>
> **New in 1.3:** everything in the last four points, the open-source project setup (6.11), and an idea backlog the owner hasn't approved yet (appendix E).
>
> **New in 1.2:** the rename; section 5.4 rewritten around three engines and a local **stream hub**; the Playback and Audio settings screens (4.27, 4.28); a local pause buffer design (5.5); and updates to the settings inventory, stack, modules, budgets, tests, build plan and decisions.

---

## 0. Read this first

You are picking up a project whose visual design is finished and approved. This file is the single source of truth for **what to build and how it should behave**. The companion folder `plasma-design-handoff/` holds a screenshot and an HTML board for every screen.

- When this brief and a board disagree on **visuals** (sizes, colours, spacing), the board wins.
- When they disagree on **behaviour**, this brief wins.
- Anything marked **Open decision** needs the project owner's answer before you build it (section 9.2). Decisions already made are in 9.1.

### 0.1 What's in the package

| Path | Contents |
|---|---|
| `PLASMA_BRIEF.md` | This document |
| `index.html` | A gallery of every screen. Open it in Chrome. |
| `screens/NN-name.jpg` | A 1920 × 1080 screenshot of each refined screen. |
| `boards/NN-name.html` | The same screen as plain HTML/CSS. Links click through like a prototype. Read the source for exact pixel values. |
| `assets/` | Fictional mock art: channel logos (transparent PNG), app banners (16:9), movie and series posters (2:3), 16:9 scenes and backdrops, and the three pairing QR codes (Add a Source, ADB and Phone Remote). Use these as preview fixtures. |
| `fonts/` | Instrument Sans 400–700, Cormorant Garamond 600, Unbounded 300 and Space Mono 400 (for command blocks), all under SIL OFL 1.1 (licences included). |

The live design canvas is private to the owner. Its **Refined** page is the spec. The **Original** page is the first exploration (more colour, a busier look) and has been superseded, so don't build from it.

### 0.2 Units

Boards are drawn at **1920 × 1080 px**. Android TV lays out a 1080p screen as **960 × 540 dp**, so:

> **dp = px ÷ 2** and **sp = px ÷ 2**.
> For example, a 96 px side margin is 48 dp, 25 px body text is 12.5 sp, and a 14 px radius is 7 dp.

### 0.3 Mock data

Every channel, programme, movie, series, app, person and domain in the boards is a fictional placeholder (for example `portal.example.net` or "Harbour City v Redmoor"). Appendix A lists them all so you can reuse them as consistent preview and test fixtures. Real logos and posters come from the user's provider at runtime.

### 0.4 Suggested order of work

1. Read sections 1–3.
2. Build the design system (section 2) as Compose components with `@Preview`s.
3. Rebuild every screen against its screenshot using mock data, with no networking yet. Compare them side by side.
4. In parallel, spike the video layer and the VLC engine (5.4, 6.5) on the owner's TV: one `SurfaceView`, a stream from the owner's provider, and moving between preview and full screen without a rebuffer. Everything on Home, Live TV and the Guide sits on top of it.
5. Wire real data milestone by milestone (sections 5–8).

---

## 1. Product

### 1.1 What Plasma is

Plasma is an IPTV player that is also the TV's home screen. Pressing **Home** on the remote opens Plasma Home instead of the stock launcher. The last channel keeps playing behind the interface, and live TV, the guide, catch-up, recordings, movies, series and the user's Android apps are each one or two presses away.

It behaves like a **MAG / Stalker portal set-top box**: portal URL and MAC login, channel numbers, typing a number to tune, CH ±, and colour keys. It has the feature depth of **TiviMate** and **Sparkle TV**, and the restraint and finish of **Apple TV (tvOS)** and **Formuler MyTVOnline 3/4**.

### 1.2 Reference products: what we take, what we leave

| Reference | Take | Leave |
|---|---|---|
| MAG box with a Stalker portal | MAC-based portal login, channel numbers, number entry, CH ±, colour keys, the feeling of "it's my TV box" | dated skeuomorphic UI, cramped info bars |
| TiviMate | guide quality, catch-up, recording, several playlists at once, favourites management, fast zapping, playback settings (buffer size, decoders, match frame rate, passthrough) | dense settings exposed everywhere |
| VLC for Android | libVLC as the playback engine: its tolerance of messy IPTV streams, interlaced sports, MPEG-2, teletext and multicast | its interface |
| Sparkle TV | channel list with a live preview, modern list layouts | — |
| Apple TV (tvOS) | focus model (lift, shadow, glint), calm type, settings layout (big icon left, list right), centred sheets | iOS-specific chrome |
| Formuler MyTVOnline 3/4 | launcher and IPTV in one, live TV on Home next to apps, portal management | heavy colour, busy backgrounds |

### 1.3 Design principles

1. **Content carries the colour.** The chrome is neutral graphite; artwork, logos and video supply the colour. Red is reserved for live and recording.
2. **One focused thing at a time.** Focus is unmistakable (lift, shadow, white fill) and is never a coloured outline.
3. **Ten-foot legibility.** Nothing is smaller than 19 px (9.5 sp), except uppercase bold badges at 14–15 px. Body text is 23–25 px, read from 3 m away.
4. **Two presses to anything.** Everything works with D-pad, OK, Back and Home alone. Colour keys, CH ±, 0–9 and Menu are shortcuts, never the only way in.
5. **Live TV never waits.** The last channel plays behind Home, and the zap banner appears instantly while the next stream loads.
6. **Honest status.** Show connection state, guide freshness, recording conflicts and connection limits in plain words with a green, amber, grey or red dot.
7. **Calm motion.** Short, eased and purposeful, with no bounce and no parallax tricks.
8. **Real, not generic.** Photographic art, real-looking channel wordmarks on plates, posters with their titles baked in, and specific copy ("Ends 22:00 · Next: Match of the Week Extra"). Avoid emoji and decorative gradients, and use translucent "glass" only for player overlays.
9. **The safe area always applies:** 96 px at the sides and 54 px top and bottom (48 dp and 27 dp, matching Android TV's 5 % overscan guidance).
10. **The same pattern everywhere:** list on the left and detail on the right, posters 2:3, thumbnails 16:9, logos on plates.

### 1.4 Scope

**v1 (build now)**
- **Sources:** Stalker portal (MAC), Xtream Codes, and M3U playlist with XMLTV guide. Several sources at once, merged into one list, with a primary source and a backup to fail over to.
- **Launcher:** Home, Apps row, All Apps (reorder and hide), Inputs, Device Settings shortcut, Home-button behaviour, power-on behaviour, screensaver.
- **Live TV:** groups, channel list with live preview, favourites, recently watched, number entry, zap banner, player overlay, audio and subtitle tracks, picture size, match frame rate.
- **Channel numbers:** taken from the playlists, with duplicates fixed automatically and listed for review. Favorites and groups can count from 1 or keep the full-list numbers (5.2).
- **Rewind and local catch-up:** a rewind buffer for the channel being watched, and chosen channels recorded all the time into a rolling window that plays like catch-up (5.5).
- **Continue Watching from other apps**, opt-in: Android's Play Next row and a Jellyfin server (5.19).
- **Phone remote and keyboard:** a web page served by Plasma, opened by scanning a QR code; no app to install (5.21).
- **Leaving Plasma:** live TV stops when the TV goes to standby, and keeps playing in a small window when another app opens, on TVs with picture-in-picture (5.20).
- **Small playback helpers:** the "Started 20 min ago" hint (4.11), per-channel volume levels (5.4.6), and a per-channel memory of which source works best (5.1).
- **Recordings for Jellyfin:** finished recordings copied to a network folder with Jellyfin-style names, info files and artwork (5.6).
- **Playback engines:** libVLC by default, mpv as the backup, and Media3 for Dolby Vision and HDR10+ movies, with automatic fallback and a per-channel memory of what worked. Sound goes out as passthrough per format, decoded PCM, or surround re-encoded to Dolby Digital. Buffer size, decoding, deinterlacing and match frame rate are settings, with per-source overrides (5.4).
- **Guide:** a seven-day grid (provider-dependent) with past days for catch-up, plus reminders.
- **Catch-up and Start Over** on channels whose provider keeps an archive.
- **Recording:** one-off and series rules, scheduled list, storage view, retention.
- **VOD:** Movies and Series from Xtream or Stalker, detail pages, resume, My List, Continue Watching.
- **Search** across channels, programmes (live, upcoming and catch-up), movies and series, including voice.
- **Profiles** (including Kids and Guest) and **parental controls** with a PIN.
- **Multiview** for 2–4 streams, device permitting.
- **Add a source** on the TV, or from a phone on the same Wi-Fi by scanning a QR code.
- **Home Screen setup** with three methods: the accessibility Home Button Override, Replace Google TV Home over ADB (from the TV or a computer), and the system's default Home app.
- **Sideloaded distribution** with in-app updates (section 5.18), as an **open-source project on GitHub** (6.11).

**Later (v1.x)**
- **Plasma Remote apps** for Android and iPhone, using the same protocol as the web page: milestone M9, right after the TV app's first release (5.22).
- Plex and Emby accounts for Continue Watching, after Jellyfin (5.19).
- A full teletext page viewer. v1 shows teletext subtitles only.
- Opening movies and series in an external player app (5.4).
- Metadata enrichment (for example TMDB) for VOD that has no art.
- Settings backup to the cloud or sync between TVs.
- Publishing to Google TV's own rows (Watch Next) for when Plasma isn't the launcher.
- Themes beyond the accent colour, and more languages.

**Non-goals**
- Providing, selling, recommending or bundling content or subscriptions, including "free channel" lists.
- Portal discovery, MAC scanning or brute-forcing, and credential sharing: never.
- A phone or tablet UI. The phone web page exists only for typing setup details.
- Ads, tracking, and accounts on our own servers.
- Google Play distribution, for now. Nothing in v1 may depend on Play services.

### 1.5 Content and legal stance

- Plasma plays subscriptions the user already has and ships with **no sources**.
- Store listings, the README and in-app copy must never suggest piracy.
- Respect the provider's connection limits; section 5.1 describes the connection budget.
- **Open source:** the repository holds no playlists, portal addresses, provider names or credentials, not even as test data, and never accepts issues or pull requests that add them. Test fixtures use the fictional data in appendix A (6.11).

---

## 2. Design system

Build this first, as `core/design`. Every screen is made from these parts.

### 2.1 Canvas, safe area and grid

- The canvas is 1920 × 1080 px (960 × 540 dp). The **safe area** is 96 px at the sides and 54 px at the top and bottom (48 / 27 dp), leaving a content width of 1728 px (864 dp).
- Full-bleed art and video ignore the safe area. Text and focusable items never go outside it, except that a row's last item may peek past the right edge to show that it scrolls.
- **Vertical rhythm:**
  - The top bar sits at y 40 (72 px tall), or a secondary header at y 46 (56 px tall).
  - Content starts at y 148.
  - A section heading is followed by 20–22 px before its row, and sections are 40–60 px apart.
  - Footer key hints sit at y 992 (36 px tall).
- **Recurring layouts** (px):

| Layout | Geometry |
|---|---|
| Top bar | wordmark in a 300 px slot on the left · centred tabs · gear, clock and avatar in a 300 px slot on the right |
| List and detail | left list 290–350 px · gap 40–54 · content on the right |
| Settings (tvOS style) | detail panel x 96, w 760 · list x 964, w 860 |
| Poster grid | 6 columns, gap 28: 209 × 314 posters in 1394 px, with a 96 px row gap that leaves room for the focused caption |
| Thumbnail grid | 4 columns, gap 28: about 327 × 184 (16:9) |
| App grid | 6 columns, gap 28: 264 × 149 banners |
| Home dock | 6 columns, gap 29: about 264 × 157 tiles |

### 2.2 Colour tokens

| Token | Value | Use |
|---|---|---|
| `bg` | `#0B0B0D` | app background (pure black only behind video) |
| `surface1` | `#161618` | settings rows, cards, input fields, storage card, sheet rows |
| `surface2` | `#1B1B1E` | keys, tiles, image placeholders |
| `surface3` | `#232327` | raised elements, the guide's "now" cells, top of graphite tiles |
| `plate` | `#18181B` | channel-logo plates |
| `guidePast` | `#131315` | past guide cells |
| `guideFuture` | `#19191C` | future guide cells |
| `text` | `#F5F5F7` | primary text; also the **focus fill** |
| `text2` | `#A1A1A8` | secondary text (7.6:1 on `bg`) |
| `text3` | `#8A8A91` | tertiary text and small captions (5.7:1) |
| `text4` | `#6E6E75` | large text, dividers and disabled items only (3.9:1) |
| `textSoft` | `#C7C7CC` / `#D1D1D6` / `#E5E5EA` | unfocused titles on imagery, key labels |
| `onFocus` | `#0B0B0D`; secondary `#3A3A40`–`#4A4A50` | text on white (focused) surfaces |
| `selected` | `rgba(255,255,255,.12)` | current tab, selected segment, unfocused buttons |
| `selectedSoft` | `rgba(255,255,255,.08)` | selected list item that doesn't have focus |
| `divider` | `rgba(255,255,255,.08–.10)` | separators |
| `live` | `#FF453A` | LIVE dot, REC, recording progress, live-edge marker |
| `liveText` | `#FF6B61` | the word "Recording" on dark backgrounds |
| `ok` | `#3DD68C` (text `#7FE3AE`) | connected, no conflicts, switch on |
| `warn` | `#F5A623` (text `#F5C26B`) | stale guide, auto-delete warning |
| `keyRed` / `keyGreen` / `keyYellow` / `keyBlue` | `#E5484D` / `#3DD68C` / `#FFD60A` / `#3B82F6` | colour-key hint squares **only** |
| `accent` (user setting) | default `#F5F5F7`; options `#4C8DFF`, `#FF9F45`, `#3DD68C` | progress bars and the guide's now-line |
| scrims | gradients to `rgba(0,0,0,.6–.94)` | over video and art, to keep text legible |

The accent is a user setting (Settings › Appearance). The boards expose it as a tweak, and white is the default.

### 2.3 Typography

**Family:** Instrument Sans (Google Fonts, OFL) at weights 400, 500, 600 and 700. Use **tabular numerals** (`fontFeatureSettings = "tnum"`) for every time, number, count and duration.

VOD detail pages show the title as art. The mocks imitate provider title art with Cormorant Garamond 600 (movie) and Unbounded 300 (series). In production, use the provider's title or logo image when there is one, and otherwise the title in Instrument Sans 600.

| Style | px | sp | Weight | Tracking | Used for |
|---|---|---|---|---|---|
| Display | 68 | 34 | 600 | −1.6 px | Home hero title |
| Title L | 52–56 | 26–28 | 600 | −1.2 px | player title, "Who's watching?" |
| Title M | 46–50 | 23–25 | 600 | −1 px | zap banner title, PIN title |
| Page title | 34 | 17 | 600 | −0.4 px | secondary-screen header |
| Section | 30–32 | 15–16 | 600 | −0.3 px | row and section headings |
| List title | 25–26 | 12.5–13 | 500 (600 when focused or selected) | 0 | list rows, settings rows, nav tabs |
| Body | 23–25 | 11.5–12.5 | 400 | 0 | synopses, descriptions |
| Caption | 20–22 | 10–11 | 500 | 0 | meta lines, counts, hints |
| Small (minimum, badges excepted) | 19 | 9.5 | 500–600 | 0 | guide times, footnotes |
| Badge | 14–15 | 7–7.5 | 700, uppercase | +0.4 to +1.4 px | LIVE, REC, 4K, NEW EPISODE |
| Clock | 30 | 15 | 500 | −0.4 px | clock at top right |
| Wordmark | 32 | 16 | 600 | −1 px | "plasma", always lower case |

Line height is about 1.06 for large titles and 1.3–1.4 for body text.

### 2.4 Shape

| Element | Radius px | dp |
|---|---|---|
| Home dock tiles | 16 | 8 |
| List rows, thumbnails, settings rows, cards | 14 | 7 |
| Buttons, posters, tabs, input fields | 12 | 6 |
| Keyboard keys | 10 (88 px PIN keys: 18) | 5 (9) |
| Logo plates | 10 (small plates 9) | 5 |
| Guide cells | 8 | 4 |
| Badges | 5–7 | 3 |
| Sheets, dialogs, toasts | 22–28 | 11–14 |
| Settings hero icon tile (220 px) | 48 | 24 |
| Avatars, dots, round transport buttons | full | full |

### 2.5 Focus and selection (the most important rule)

| Kind | Focused treatment |
|---|---|
| **Tiles and cards** (posters, thumbnails, app banners, dock tiles, avatars) | scale **1.08** (avatars and an app being moved: 1.12) · shadow `0 26px 54px rgba(0,0,0,.62–.7)` (dp: y 13, blur 27) · specular glint overlay `linear-gradient(135deg, rgba(255,255,255,.16–.22) 0%, transparent 40–45%)` |
| **Rows** (list items, settings rows, catch-up rows, sheet rows) | fill turns **white** `#F5F5F7`, text `#0B0B0D`, secondary text `#3A3A40`–`#4A4A50` · scale **1.02** (wide input fields 1.015) · shadow `0 20px 44px rgba(0,0,0,.55)` |
| **Buttons** | white fill, dark text, scale **1.05**. Unfocused buttons are `rgba(255,255,255,.12)` with white text. There is no separate "primary colour": the primary action is simply the button that has focus by default. |
| **Tabs and segments** | selected: `rgba(255,255,255,.12)` pill at weight 600 · focused: white pill with dark text |
| **Selected but not focused** (current group, category, channel or day) | `rgba(255,255,255,.08)` fill |
| **Guide cells** | white fill, **no scale**, so the grid stays aligned |
| **Multiview tiles** | they can't scale because they fill the screen, so the focused tile gets a **4 px white ring** and a "Sound" badge |

- **Never** use a coloured focus ring, a glow in the accent colour, or underline-only focus.
- Neighbours of the focused item never dim, except in modal states: in app move mode the others drop to 60 % opacity, and locked or unavailable items sit at 45–55 %.
- Captions appear under the focused item only in the Movies and Series grids. Rows such as Continue Watching always show their captions.

**Compose:** implement focus once, as a modifier or wrapper such as `Modifier.plasmaFocus(kind = Tile | Row | Button | Cell)`, or by configuring tv-material `Surface` / `Card` scale, border, glow and colours. Animate with `graphicsLayer` scale and `tween(150, FastOutSlowInEasing)`.

### 2.6 Motion

| What | Spec |
|---|---|
| Focus scale and fill | 150 ms ease-out; colour 120 ms |
| Screen change | 200 ms crossfade, with incoming content settling 16 dp upward; no horizontal slides between tabs |
| Overlays (player overlay, sheets, toasts) | fade plus an 8 dp rise; 200 ms in, 150 ms out |
| Player overlay auto-hide | 5 s after the last key |
| Zap banner auto-hide | 4 s |
| Number entry | tunes 1.5 s after the last digit, or immediately on OK |
| Reminder toast | stays until acted on; switches automatically at start time if that option is on |
| Home hero carousel | doesn't auto-advance while the live preview is on; otherwise 10 s per item |
| Reduce motion (setting) | scale capped at 1.04, no rises, crossfades only |

### 2.7 Imagery

- **Channel logos** are transparent PNG or SVG files placed on a `plate` (`#18181B`, radius 10). Plate sizes used:

  | Plate (px) | Where |
  |---|---|
  | 150 × 66 | Live TV list |
  | 120 × 62 | sheet header |
  | 108 × 52 | Catch-up list |
  | 104 × 52 | zap neighbours |

  Size each logo by optical weight: wide wordmarks render shorter (16–28 px) and boxy marks taller (32–44 px). A simple heuristic is to fit the logo within 80 % of the plate's width and 55 % of its height, and cap the height further when the aspect ratio is above 4.
- **Over video or art** (top left of thumbnails), the logo sits directly on a top scrim that runs from `rgba(0,0,0,.6)` to transparent over the top half.
- **Logo fallback:** the channel name in Instrument Sans 600 on the plate.
- **Programme art fallback** (EPG without images): a graphite card, `linear-gradient(#232327, #18181B)`, with the channel logo centred. See "Fight Night" in Search and the rows in Catch-up.
- **Posters are 2:3** (209 × 314, 168 × 252, 150 × 225). **Thumbnails and backdrops are 16:9** (400 × 225, 352 × 198, 327 × 184, 192 × 108). Crop with a focal point.
- **Live thumbnails:** use the provider's snapshot image when there is one, then programme art, then the logo fallback.
- **Backdrops** get left-to-right and bottom scrims so text over art stays at 4.5:1 or better.
- **Avatars** are circular photo crops, or an icon on a graphite circle.

### 2.8 Icons

Line icons on a 24-unit grid, with a 1.6–2.4 px stroke at 22–26 px and round caps and joins (Lucide-style). Only play/pause, the favourite star and the record dot are filled. Keep one icon set as vector drawables in `core/design`.

### 2.9 Badges, status and progress

| Element | Spec |
|---|---|
| LIVE / REC | a 9 px red dot plus "LIVE" or "REC" at 15–19 px, weight 600–700, tracking 1.2–1.4. On images, it sits in a `rgba(0,0,0,.6)` pill with radius 7. |
| Technical badges | 1.5 px outline `rgba(255,255,255,.45)`, radius 5, 15 px weight 700: **4K, HDR, HD, 5.1, CC, 50 FPS, 13+, 16+, 18+** |
| Poster badges | "NEW EPISODE" / "NEW SEASON", filled `rgba(11,11,13,.82)`, 14 px weight 700, tracking 1, at bottom left |
| Catch-up marker | ↺ icon plus "CATCH-UP" (on images), or the ↺ icon alone in lists |
| Progress | 5 px bar on a `rgba(255,255,255,.22)` track, filled with the accent; red while recording; in list rows, a 3 px bar 110 px wide |
| Status dot | a 10 px dot followed by words: green "Connected" / "No conflicts", grey "Standby", amber "Guide is 2 days old" / "Deletes in 2 days", red for errors |
| Unwatched | a 9 px white dot before the title |
| Watched | a check in a 30 px dark circle |
| Series folder (recordings) | two stacked layers peeking above the thumbnail, plus a "3 EPISODES" pill |
| Series-rule icon | two overlapping red dots (a single red dot means a one-off recording) |
| Lock | a padlock line icon next to the title; the row drops to 45 % opacity |

### 2.10 Copy and formatting

- **Title Case** for screen titles, section headings, tabs, buttons, menu items and settings rows, for example "Watch from the Start", "Add Portal or Playlist" and "Coming Up".
- **Sentence case** for descriptions, meta lines, hints and small section labels, for example "Channels with catch-up · 214".
- The clock is **24-hour by default** (a setting). Write times as "20:41" and ranges with an en dash, "19:45–22:00".
- Separate meta with " · ".
- Write durations as "2 h 14 min", "58 min" and "1 h 02 min left".
- Write dates as "Wed 21 Oct" and "Thu 22 Oct, 20:00", using "Today", "Yesterday" and "Tonight" where they fit. Use thousands separators ("1,284").
- **Spelling:** the mocks mostly use en-GB ("programme", "colour") but say "Favorites". Keep all copy in string resources and ship en-GB ("Favourites") and en-US variants (**Open decision**: which locale is the default).
- **Voice:** plain, specific and helpful, never blaming. For example: "Can't reach Home Portal. Trying Backup Line…" or "Portal rejected this MAC address. Check it's registered with your provider."

### 2.11 What "not AI-looking" means here

- **Do:** keep the chrome neutral, and build hierarchy from size and weight rather than colour. Use tabular numerals and specific copy, and keep real-world density (channel numbers, times, counts). Keep radii consistent, shadows restrained, and use photographic art with logos on plates.
- **Don't:** use rainbow or neon gradients or glows, glass everywhere, emoji, or icons inside coloured circles. Avoid placeholder copy such as "Channel 1" or "Lorem ipsum", centring everything, more than one accent, and text shadows (except faint legibility shadows over video).

---

## 3. Navigation

### 3.1 Top bar

The top bar appears on Home, Search, Live TV, Guide, Movies, Series and Apps.

- **Left:** the "plasma" wordmark, which goes to Home.
- **Centre:** the Search icon, then Home, Live TV, Guide, Movies, Series and Apps.
- **Right:** the Settings gear, the clock, and the profile avatar, which opens Who's Watching.
- The current tab gets a `.12` pill at weight 600.
- The bar stays pinned. Up from the first content row moves focus into the bar, and Down returns to the last-focused content item.

### 3.2 Secondary header

Recordings, Catch-up, Settings pages, Add a Source and the PIN screen use a secondary header instead of the top bar:

- a "‹ Back target" text link on the left (22 px, weight 500, `#A1A1A8`),
- the page title centred (34 px, weight 600),
- the clock on the right.

The Movie and Series detail pages use a translucent back pill (`rgba(255,255,255,.1)`) over the backdrop instead.

### 3.3 Screen map

```
Home (launcher root)
├── Search
├── Live TV ── Player ── Player Options (Menu) · Zap Banner + Reminder · Multiview
├── Guide ── Player / catch-up playback / programme sheet (Remind, Record)
├── Movies ── Movie Detail ── Player (VOD)
├── Series ── Series Detail ── Player (VOD)
├── Apps (All Apps, move mode) → Android apps · Inputs · Device Settings · Get More Apps
├── Dock: Live TV · TV Guide · Movies · Series · Catch-up · Recordings
├── Home rows (scroll down): Continue Watching · New in Movies · Apps
├── Settings ── Portals & Playlists ── Add a Source (+ phone pairing)
│           ├── Channels ── Duplicate Numbers
│           ├── Playback ── Audio
│           │            └─ Rewind and Local Catch-up
│           ├── Recording (network folder for Jellyfin)
│           ├── Launcher & Home ── Home Screen ── Replace Google TV Home (ADB)
│           │                   ├─ Home Rows ── Continue Watching
│           │                   └─ Screensaver
│           ├── Remote & Buttons ── Phone Remote (QR code, paired phones)
│           └── TV Guide · Parental Controls · Profiles ·
│               Appearance · Network · About   (not drawn; same pattern)
├── Who's Watching (profile picker) ── Parental PIN (for PIN-protected profiles)
└── Screensaver (system DreamService)

The Parental PIN screen can interrupt anything that would play locked content.

On the phone (a web page served by Plasma, 5.21):
Phone Remote ── Remote · Channels · Type

Plasma Remote app for Android and iPhone (M9, 5.22; not drawn):
Find your TV ── Pairing ── Remote · Channels · Type ── Settings
```

### 3.4 Key flows

- **A. First run**
  1. Welcome screen (not drawn): the wordmark, "Add your TV service", and **Add a Source** / **Restore from Backup**.
  2. Add a Source, on the Stalker tab by default; the user can also scan the QR code with a phone.
  3. **Connect** validates the login and shows an account summary (expiry, connections, counts).
  4. Import progress.
  5. Home Screen setup (4.23), shown as a step. It recommends the right method for this TV.
  6. Home, with the first channel playing muted.
- **B. Watch live**
  1. On Home, **Live TV** has focus by default; pressing OK opens Live TV with the current channel focused and the preview playing.
  2. OK goes full screen, with the overlay showing for 5 s.
  3. Up/Down or CH ± zaps channels and shows the zap banner.
  4. **Left** opens the channel list over the video.
  5. Back returns.
- **C. Guide to catch-up:** move to a past programme marked ↺ and press OK. It plays from its start using the archive. The overlay shows the programme timeline, and **Go Live** returns to the live edge.
- **D. Record**
  - In the player, Menu offers **Stop Recording** / **Record Every City League Match**.
  - In the Guide, OK on a future programme offers Remind Me / Record / Record Series.
  - A Search result can also be recorded.
  - If there aren't enough connections, a conflict sheet asks what to keep.
- **E. Start over:** the player's Start Over button, or Menu › Watch from the Start. This needs a provider archive.
- **F. VOD:** Movies › poster › detail page › **Resume** or **From the Beginning** › VOD player, which has a scrubber and no live edge.
- **G. Search:** type on the linear keyboard or hold the mic button. Results update with every key: live, upcoming and catch-up programmes, then movies and series, then suggestions.
- **H. Parental lock:** tuning to a locked channel opens the PIN screen; the correct PIN plays it. "Unlock for" offers This programme, 1 hour, or Until the TV sleeps.
- **I. Profiles:** Who's Watching appears on wake (if that setting is on) or from the avatar. The user picks a profile, enters its PIN if it has one, and lands on that profile's Home.
- **J. Add a source from a phone:**
  1. The phone scans the QR code shown on Add a Source.
  2. A local web form opens on the phone; the user submits it.
  3. The TV fills in the fields and shows "Received from your phone".
  4. Connect gets focus.
- **K. Reminder:** a toast appears one minute before the start time, over anything in Plasma, offering **Switch Now** or Dismiss. Switching automatically at the start time is optional.
- **L. Move an app:** on Apps, long-press OK (or Menu › Move). The app lifts with arrows beside it; use the D-pad to move it, then OK to drop or Back to cancel.
- **M. Replace Google TV Home (ADB, on the TV):**
  1. Settings › Launcher & Home › Use Plasma as Home Screen › Replace Google TV Home › Set Up.
  2. Turn on Developer options and Wireless debugging (Plasma checks and ticks step 1).
  3. Open "Pair device with pairing code". While the code shows, the user types it and the pairing port on their phone through Plasma's QR page.
  4. Plasma pairs with itself, runs the commands, checks that Home now opens Plasma, and shows "Done. Press Home to try it."
  5. Restore Google TV Home undoes it at any time.
- **N. A stream won't play:**
  1. VLC opens the channel, but no picture arrives within the connection timeout, or the decoder fails.
  2. If the server or network is at fault, Plasma retries once, then fails over to the next source (5.1). If data is arriving but VLC can't play it, Plasma reopens the same stream with mpv, then with the Android player (5.4).
  3. A toast says what happened, for example "VLC couldn't play Arena Sports 2. Playing it with mpv."
  4. Plasma remembers mpv for that channel. Player Options › Switch Player changes it back.
- **O. Numbers in Favorites:** with Favorites counting from 1, the user tunes from Favorites and types 2. Plasma plays the second favorite, and the zap banner shows "Favorites 2 · 107 Velocity Motor". Typing 105, which isn't a Favorites number, tunes channel 105 from the full list. CH ± then moves through the list the user tuned from (5.2).
- **P. Local catch-up:** Courtside+ is a local catch-up channel. In the Guide, its programmes from the last 24 hours show ↺ like provider catch-up, and OK plays one from its start, from the drive. Menu offers Keep as Recording (5.5).
- **Q. Continue Watching from another app:** a Jellyfin movie appears in Continue Watching with a JELLYFIN tag. OK plays it in Plasma and reports progress back to the server; with Play Jellyfin Items In set to the Jellyfin app, OK opens that app instead. An item from Android's Play Next row opens its own app (5.19).
- **R. Phone remote:**
  1. On the TV, Settings › Remote & Buttons › Phone Remote shows a QR code.
  2. The phone's camera opens the page, which pairs and remembers the phone.
  3. The phone shows what's playing, a D-pad, the keys, and the colour keys labelled with what they do on the current TV screen.
  4. When a text field gets focus on the TV, such as Search, the phone switches to its Type tab with the keyboard up (on an iPhone, after one tap).
  5. The Channels tab tunes with a tap (5.21).
- **R2. Phone remote app (M9):**
  1. The user installs Plasma Remote and opens it. It lists "Living Room TV", which it found on the Wi-Fi.
  2. They pick it and type the code the TV shows, or scan the TV's QR code inside the app, then name the phone.
  3. From then on, opening the app connects straight away, even after the TV gets a new network address. The keyboard pops up by itself when the TV wants text (5.22).
- **S. Leaving Plasma:** watching Arena Sports 1, the user opens another app from the Apps row. On a TV with picture-in-picture, the match keeps playing in a small window in a corner; on other TVs the stream stops and frees its connection. When the TV goes to standby, the stream stops a minute later unless something is recording (5.20).
- **T. Tuning in late:** the user tunes to a match that started 20 minutes ago on a channel with catch-up. The zap banner adds "Started 20 min ago · OK to watch from the start". OK opens the overlay with Start Over already focused (4.11).

### 3.5 Back and Home rules

- **Back** always goes up one level.
  - On Home it does nothing visible: it returns focus to the dock's first tile and scrolls to the top. Plasma is the root.
  - In the full-screen player, Back first hides any visible overlay or banner, then leaves the player for the screen you came from.
- **Home** opens Plasma Home from anywhere, or the target chosen in Settings › Launcher & Home.
  - **Long-press Home** shows the system's recent apps where the system allows it; on many devices the system handles this press itself.
- **Number keys** work on every screen. Typing a channel number tunes it.

### 3.6 Remote key map

| Key | Everywhere | Full-screen player (live) | Lists, grids, Guide |
|---|---|---|---|
| D-pad | move focus | **Up/Down** change channel · **Left** opens the channel list · **Right** shows the overlay with the timeline focused | move; in the Guide, Left/Right moves by programme |
| OK | select | shows the overlay; inside the overlay, activates the focused control | open or play |
| Long-press OK | context menu (same as Menu) | Player Options | item options (move, hide, favourite…) |
| Back | up one level | hide overlay, then leave the player | up one level |
| Home | Plasma Home (configurable) | | |
| Menu ≡ | options for the focused item | Player Options sheet | options |
| CH + / − | | next / previous channel | page up / down |
| 0–9 | number entry, then tune; in a list that counts from 1, that list's numbers come first (5.2) | number entry | Guide: jump to that channel |
| Info | info overlay | info overlay | |
| Guide (EPG key) | open the Guide | open the Guide | |
| **Red** | toggle favourite | toggle favourite | Recordings: Delete |
| **Green** | catch-up for the focused channel or programme | Start Over / catch-up | Recordings: Keep |
| **Yellow** | sort / group | | Sort (everywhere) |
| **Blue** | Multiview | Multiview | Recordings: Select |
| Play/Pause | | pause live (timeshift) / resume | |
| FF / REW | | seek ±10 s; holding accelerates to 30 s, then 2 min | |
| Record (if present) | record the focused programme | record now | |
| Audio / Subtitles (if present) | | next audio track (toast "English 5.1 · passthrough") / subtitles on and off | |
| Mic / Voice | voice search | | |
| Last channel (if present) | previous channel | previous channel | |

- **Colour keys are contextual.** The table shows the default mapping; each screen's footer shows its own. Recordings uses Delete / Keep / Sort / Select, and on Series Detail green is Mark Season as Watched. Yellow always means Sort.
- **Android keycodes:**
  - colour keys: `KEYCODE_PROG_RED`, `KEYCODE_PROG_GREEN`, `KEYCODE_PROG_YELLOW`, `KEYCODE_PROG_BLUE`
  - channels and numbers: `KEYCODE_CHANNEL_UP`, `KEYCODE_CHANNEL_DOWN`, `KEYCODE_0`–`KEYCODE_9`, `KEYCODE_LAST_CHANNEL`
  - navigation and info: `KEYCODE_MENU`, `KEYCODE_INFO`, `KEYCODE_GUIDE`, `KEYCODE_TV_INPUT`
  - media: `KEYCODE_MEDIA_PLAY_PAUSE` (also `MEDIA_PLAY` and `MEDIA_PAUSE`), `KEYCODE_MEDIA_FAST_FORWARD`, `KEYCODE_MEDIA_REWIND`, `KEYCODE_MEDIA_RECORD`, `KEYCODE_DVR`, `KEYCODE_CAPTIONS`, `KEYCODE_MEDIA_AUDIO_TRACK`
  - voice: `KEYCODE_SEARCH` and `KEYCODE_VOICE_ASSIST`, which the system often consumes.
- **Google TV remotes have no colour, number, channel or Menu keys.** Every action must also be reachable with the D-pad, OK, long-press OK and Back.
- **Settings › Remote & Buttons** lets users remap the colour keys, long-press OK, what Up/Down do in the player, and the number-entry delay.

---

## 4. Screens

Each screen below lists its board, purpose, layout (px; divide by 2 for dp), content, focus and keys, and states. Use the matching `screens/NN-*.jpg` and `boards/NN-*.html` alongside it.

### 4.1 Home (`01-home`)

**Purpose:** the launcher root, and what the Home button opens.

**Layout**
- **Background:** the last channel's live video, full bleed. It is muted by default; Settings › Launcher & Home › Live Preview on Home offers Muted, Sound, or Off (artwork). Scrims: a dark band behind the top bar, a left-to-right gradient behind the hero text, and a fade to `bg` from about y 560.
- **Hero** (x 96, y 300, w 940):
  - Channel logo (height about 50) · "● LIVE" · "Channel 105".
  - Title at 68 px, weight 600: "Harbour City v Redmoor".
  - Meta: "2–1 64′ · City League, Matchweek 9". Show a score only if the EPG or sports data provides one; otherwise show the programme subtitle.
  - A 280 px progress bar, then "Ends 22:00 · Next: Match of the Week Extra".
  - Carousel dots, right-aligned at about y 563, for 4 items.
- **Dock** (y 640, 6 columns, gap 29): graphite tiles of about 264 × 157 with a 44 px line icon. Labels sit below at 24 px, weight 500 (600 and white when focused). Tiles: **Live TV** (default focus), TV Guide, Movies, Series, Catch-up, Recordings.
- **Favorite Channels** (y 880): 352 × 198 live thumbnails, gap 28, logo at top left. The row peeks past the right edge.

**Hero items**
1. The last channel, live. This item is always first.
2. The top Continue Watching item.
3. A new movie.
4. The next recording or reminder.

**Focus and keys**
- Default focus is the Live TV tile.
- Up moves to the hero (OK there plays full screen), and Up again moves to the top bar.
- Down moves to Favorite Channels, then to the Home rows (4.2).
- OK on a favourite tunes it full screen. Long-press offers Remove from Favorites and Move.

**States**
- **No sources yet:** the hero becomes an "Add your TV service" card with an Add a Source button.
- **Channel can't play:** the hero shows programme art with "Can't play Arena Sports 1 right now" and Try Again.

### 4.2 Home rows (`02-home-rows`)

The view after scrolling down Home. The top bar stays pinned.

- **Continue Watching:** 400 × 225 thumbnails with progress bars. Catch-up items show their channel logo. Meta examples: "S2, E4 · 18 min left", "Jellyfin · 52 min left", "Catch-up · Yesterday, 20:00".
  - **Items from other apps** (5.19) carry a source tag at top right, in the catch-up badge style: "JELLYFIN", or the app's name for items from Android's Play Next row.
  - **When an item only has a poster** (2:3), the tile shows it sharp at the left (124 × 186, radius 8) over a blurred, darkened enlargement of the same poster. Kite Season on the board is drawn this way.
- **New in Movies:** 168 × 252 posters, plus a "See All" link to Movies.
- **Apps:** 268 × 151 banners in the user's order, then an "All Apps" tile that opens Apps.

The rows and their order are configurable in Settings › Launcher & Home › Home Rows, which also holds the Continue Watching settings (4.32).

**Scrolling:** the focused row stays at a fixed vertical pivot (about y 300–400). Horizontal rows keep focus at the left until they reach their end.

### 4.3 Search (`03-search`)

**Layout**
- **Query line:** a search glyph and the query at 54 px, weight 600, with "Hold the mic button on your remote to search by voice" on the right.
- **Linear keyboard** (tvOS style, in a 60 px bar): a–z, then 123, Space and Delete.
- **"Live, Upcoming and Catch-up"** row of 400 × 225 cards:
  - live: progress bar plus LIVE badge;
  - catch-up: ↺ CATCH-UP badge;
  - upcoming: "TONIGHT 00:30" plus "Recording set";
  - a logo-fallback card when there is no art.
- **"Movies and Series":** 150 × 225 posters.
- **"Suggestions":** query completions with the matched part in white at weight 600, and recent searches marked with a clock icon.

**Behaviour**
- Results update on every key, debounced by about 150 ms.
- Search covers channel names, EPG titles and subtitles (now, the next 7 days, and the catch-up window), movie titles and series titles.
- Ranking: exact prefix, then word prefix, then contains. Within each band, live-now results come first.
- Voice uses `SpeechRecognizer`. The system may intercept the remote's mic button, so also offer a mic key at the end of the keyboard row.

**Focus:** starts on the keyboard. Down moves into the results. The board shows the first result focused after typing.

**Empty state:** "No results for 'xyz'", followed by suggestions.

### 4.4 Apps (`04-apps`)

**Layout**
- **"On Home"** is the Apps row on Home, in its order.
- **"All Apps"** is an A–Z grid of 264 × 149 banners in 6 columns.
- System tiles at the end: **Inputs**, **Device Settings**, **Get More Apps** (only if the Play Store is installed).

**The board shows move mode**
- Sonar is lifted (scale 1.12, faint 1 px white edge, big shadow) with white round arrows on either side.
- Other tiles are dimmed to 60 %.
- A hint below reads "Moving Sonar · Press OK to drop it here".
- Footer: OK Drop · Back Cancel · ≡ "Hide, app info, uninstall".

**Behaviour**
- Apps come from `PackageManager`: activities with `CATEGORY_LEANBACK_LAUNCHER` first, then `CATEGORY_LAUNCHER`-only apps.
- Each app uses its activity or application **banner** (320 × 180 dp). Without one, show the icon centred on graphite with the label below.
- **Menu** offers:
  - Move
  - Add to Home / Remove from Home
  - Hide
  - App Info (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`)
  - Uninstall (`Intent.ACTION_DELETE`)
- **Inputs** opens a list of HDMI inputs from `TvInputManager`. This is device-dependent, so hide the tile if there are none.

### 4.5 Who's Watching (`05-profiles`)

- **Layout:**
  - The title "Who's watching?" is centred at 56 px, weight 600.
  - Avatars are 200 px circles; the focused one scales to 1.12 with a glint and shadow.
  - The focused name is 28 px, weight 600; other names are 27 px, weight 500, `#C7C7CC`.
  - A meta line under each name: "Last watched Arena Sports 1", "PIN" (with a lock icon), "Ages 7 and under", "No history kept".
  - Add Profile is a dashed circle. Below it are a Manage Profiles button and a one-line explanation.
- **When it shows:** on wake (setting: Ask Who's Watching = On wake / Never) and from the avatar in the top bar.
- **PIN-protected profiles** go to the PIN screen first.

### 4.6 Screensaver (`06-screensaver`)

- Full-bleed scenic art with a slow pan or crossfade.
- At bottom left: the time (150 px, weight 500), the date (32 px, weight 500), and a status line such as "● Recording Harbour City v Redmoor" while recording.
- The wordmark sits at bottom right at 55 % opacity.
- Any key exits back to where you were.
- **Burn-in safety:** move the text block by up to 16 px each minute.
- Implement it as a **DreamService** (section 5.14) and as an in-app idle screen.

### 4.7 Live TV (`07-live-tv`)

**Layout:** three columns, with a footer.

- **Groups** (x 96, w 300), each with a count: All Channels 1,284 · Favorites 24 · Recently Watched 18 · **Sports 186** (selected, `.08` fill) · News 92 · Movies 140 · Entertainment 212 · Documentary 58 · Kids 64 · Music 41 · 4K UHD 36.
- **Channel list** (x 436, w 720):
  - The header reads "Sports · 186 channels".
  - Rows are 90 px tall (96 px and white when focused). Each row has the number (52 px slot, right-aligned), a 150 × 66 logo plate, the title (25 px, weight 600), and the time (21 px, weight 500) with a 110 px progress bar.
  - Trailing markers: ★ favourite, ↺ catch-up, 4K badge, lock.
  - A locked channel (110 Combat Arena) is shown at 45 % opacity with a lock.
  - The bottom of the list fades out to show it scrolls.
- **Preview** (x 1196, w 628):
  - Video at 628 × 353, radius 14.
  - "● NOW 19:45–22:00 · 1 h 19 min left", then the title and a 628 px progress bar.
  - A synopsis of up to 3 lines.
  - The next 3 programmes with times; a red dot marks a scheduled recording and a bell marks a reminder.
- **Footer:**
  - colour keys: Favorite, Catch-up, Sort, Multiview;
  - remote keys: OK Watch · ◀ Groups · ≡ Options · 0–9 Go to channel.

**Behaviour**
- **Numbers in the list** follow the group's numbering (5.2). The board shows Sports keeping the full-list numbers (103–110); Favorites counts from 1 in the mock settings.
- **Preview tuning:** moving focus in the list tunes the preview after **600 ms** of dwell. The preview reuses the main player instance, so it never uses an extra connection.
- **OK** goes full screen using the same player, with no rebuffer.
- **Right** moves focus to the upcoming list; OK on an upcoming programme opens the Remind/Record sheet.
- **Yellow** sorts by number, name, or recently watched.
- **Long-press or Menu** opens channel options: Add to Favorites, Move favourite, Hide channel, Lock channel, Switch source, Channel info.
- Left from the list moves focus to Groups.
- **Overlay mode:** pressing Left in the full-screen player opens the Groups and Channel list columns over the running video. The preview column is left out because the video itself is visible.

### 4.8 Guide (`08-guide`)

**Layout**
- **Selected programme** (x 96, w 1180):
  - Channel logo, number, LIVE and a Recording marker.
  - Title, then "Today, 19:45–22:00 · City League, Matchweek 9".
  - Badges: HD, 5.1, CC.
  - A synopsis of up to 3 lines.
- **Live preview** of the focused channel at 480 × 270 (x 1344).
- **Toolbar** (y 428):
  - Day tabs: Yesterday · Today (selected) · Thu 22 … Mon 26.
  - A "Group: All Channels" selector on the right.
- **Grid** (y 540):
  - **Header row:** the date "Wed 21 Oct" sits in the 316 px channel column, followed by half-hour labels.
  - **Rows** are 62 px tall with 8 px gaps. The channel cell is 316 px (number and logo).
  - **Scale:** 2 hours fill 1412 px (11.77 px per minute, about 5.9 dp per minute).
- **Cells:**
  - past: `#131315`;
  - now: `#232327`;
  - future: `#19191C`;
  - focused: white.

  Each cell shows the title (22 px, weight 600) and time (19 px, weight 500), and truncates with an ellipsis.
- **Now-line:** an accent-coloured vertical line at the current time, with a "20:41" pill in the header.

**Behaviour**
- **Left/Right** moves by programme. Past the edge of the window, the grid scrolls in 30-minute steps.
- **Up/Down** keeps a **time anchor**: it focuses the programme on the new row that overlaps the anchor time. The anchor is the start of the focused cell, clamped to the visible window, or "now" on entry. This is the single most important guide behaviour.
- **CH ±** pages by rows. Number keys jump to a channel.
- **Day tabs** jump to the same time of day on that day; the default for other days is 20:00.
- **OK on a cell:**
  - now: play;
  - past with an archive (↺): play catch-up from the start;
  - future: a sheet with Remind Me, Record, Record Series, and More Episodes;
  - past without an archive: dimmed, info only.
- **Colour keys** follow the global map. Menu offers Record or Remind for the focused programme.
- **Loading** shows skeleton cells.
- **Stale guide:** an amber line in the header, "Guide updated 2 days ago · Refresh".

### 4.9 Player (`09-player`)

**Layout:** the video is full bleed. OK or any key shows the overlay:
- The clock at top right, and a bottom scrim.
- **Info block:** logo (height 50), number, LIVE; title at 52 px, weight 600; "City League, Matchweek 9 · 19:45–22:00"; badges HD, 50 FPS, 5.1, CC. "Next 22:00 Match of the Week Extra" sits on the right.
- **Timeshift bar** (y 880, 1728 px wide, 6 px tall):
  - the played portion in white;
  - the buffer at 50 % white;
  - a red tick at the live edge;
  - a 28 px playhead knob with the bubble "20:39 −2:14 behind live";
  - the programme's start and end times as labels.
- **Controls** (y 944): Start Over, −10 s, Play/Pause (76 px, focused), +10 s, and **Go Live** with a red dot. On the right: Audio ("English 5.1"), "Subtitles off", "Recording" (opens Player Options), Multiview, and Channel list.

**Behaviour**
- **Pause on live is timeshift** (section 5.5).
- Left/Right on the bar seeks 10 s; holding accelerates.
- Go Live jumps to the live edge.
- The overlay hides after 5 s.
- Score bugs are part of the broadcast video, not Plasma's UI.

**VOD variant:** the same overlay, but no live edge and no channel info. It adds a title/episode line, chapters if present, and an "Up Next" card near the end.

### 4.10 Player Options (`10-player-options`)

Opened with Menu or long-press OK in the player, or from the overlay's Recording button.

**Sheet:** centred horizontally from y 112, 800 px wide, radius 28, `rgba(30,30,33,.82)` over the video blurred at about 22 px and darkened.

**Header:** a 120 × 62 logo plate, the title at 30 px, weight 600, and "105 · Arena Sports 1 · 19:45–22:00 · 2:14 behind live".

**Rows** (62–64 px: 24 px icon, label at 24 px, value at 21 px, weight 500, on the right):

| Row | Value shown |
|---|---|
| **Watch from the Start** (default focus) | Started 19:45 |
| Stop Recording (red square) | Recording since 19:45 |
| Record Every City League Match (series icon) | Series rule |
| Remove from Favorites (★) | Favorite 3 of 24 |
| — divider — | |
| Audio and Subtitles | English 5.1 · Off |
| Picture Size | Fit · 16:9 (options: Fit / Fill / Zoom / 16:9 / 4:3) |
| Switch Source | Home Portal → Backup Line |
| **Switch Player** | VLC → mpv |
| Sleep Timer | Off (options: 30, 60, 90, 120 min / end of programme) |

**Switch Player** reopens the same stream in the next engine, in the order VLC → mpv → Android player, and Plasma remembers the choice for this channel. The value shows the current engine and the next one. (On the board, the row links to the Playback settings board so the prototype can reach it.)

**Footer:** stream info with a status dot: green when healthy, amber while buffering or just after a fallback, red on errors.
- **Left:** the engine and format, "VLC · 1080i · H.264 · AC-3 5.1 passthrough". The audio part says how the sound leaves the box: "passthrough", "decoded", or "encoded to Dolby Digital".
- **Right:** "8.2 Mb/s · buffer 2.1 s".

The rows are contextual: a VOD shows Restart, Audio and Subtitles, Picture Size, Switch Player and Sleep Timer.

### 4.11 Zap banner and reminder (`11-zap-banner-and-reminder`)

**Zap banner:** shown on every channel change (CH ±, Up/Down, number entry).
- **Top left:** the channel number "107" (56 px, weight 600) in a `rgba(18,18,20,.62)` box with radius 16.
- **Bottom left:**
  - logo plus "107 · Velocity Motor", with 4K, HDR and ↺ Catch-up badges;
  - the title at 50 px, weight 600 ("Pit Lane");
  - a progress row: start time · accent bar · end time · "1 min left";
  - "Next 22:00 Endurance 24: Night Stint, Part 2".
- **Bottom right:** the neighbouring channels as 88 px cards. CH + (above) shows 108 Courtside+ and CH − (below) shows 106 Arena Sports 2, each with a 104 × 52 logo plate, title, and "108 · Courtside+ · until 23:00".
- **Footer:** the time · CH ± Change channel · 0–9 Go to number · OK Details.
- **Number entry:**
  - Digits collect in the top-left box ("10_") together with the matching channel name.
  - The channel tunes 1.5 s after the last digit, or on OK.
  - An invalid number shows "No channel 999".
  - In a list that counts from 1 (5.2), the box names the list: "Favorites 2 · Velocity Motor". After tuning, the banner shows both numbers, "Favorites 2 · 107".

**"Started 20 min ago" hint** (not drawn; Settings › Playback › Offer Watch from the Start, on by default)
- **When:** after a zap or number entry, if the programme on the new channel started at least 5 minutes ago, has at least 10 minutes left, and can be watched from its start. That means the channel has an archive, or it's a local catch-up channel whose ring holds the start (5.5).
- **What:** one more line in the zap banner, under the progress row, at 21 px: "↺ Started 20 min ago · OK to watch from the start". The banner stays up for 8 s instead of 4 s.
- **Keys:** while the hint shows, OK opens the player overlay with **Start Over** focused instead of Play/Pause, so a second OK starts it. Green and ◀◀ start over at once. Other keys behave as usual and dismiss the hint.
- **Limits:** at most once per programme per channel, and never during Multiview or picture-in-picture.

**Reminder toast** (top right, 600 px, radius 22):
- **Content:** a bell tile, "Reminder · starts in 1 minute", the title "Match of the Week Extra", and the logo with "105 · 22:00–23:00".
- **Buttons:** **Switch Now** (focused) and Dismiss. "Switches at 22:00" appears when auto-switch is on.
- **Focus:** the toast takes focus so that OK switches, but channel and number keys still work underneath it.

### 4.12 Multiview (`12-multiview`)

**Layout**
- A 2 × 2 grid of 840 × 472 tiles, gap 8, at x 116, w 1688.
- Each tile shows its channel logo and title. One tile can show a ticker or subtitles.
- The **focused tile has the sound**: it gets a 4 px white ring and a "Sound" badge. The other tiles show a muted icon.

**Toolbar:** Grid · Focus + 3 (one large, three small) · Picture in Picture · Change Channel · Full Screen.

**Behaviour**
- OK on a tile goes full screen on that channel. Menu offers Change channel and Remove.
- **Each tile is a separate player and a separate connection.** Before adding a tile, check the connection budget: "Your subscription allows 2 streams at a time."
- **Device capability:** detect decoder limits and fall back to 2 tiles if needed. Allow only one 4K tile.
- **Engines:** each tile is its own VLC player with hardware decoding. Multiview sound is always decoded PCM, never passthrough, so moving focus switches the sound instantly. Full Screen hands the focused tile's stream to the main player without reconnecting (the stream hub keeps it open, 5.4).

### 4.13 Movies (`13-movies`)

**Layout**
- **Categories** (w 290):
  - Recently Added 212 (selected) · Continue Watching 3 · My List 14 · 4K UHD 640;
  - a divider;
  - genres, from Action 1,204 to Thriller 1,115. The last visible item is faded to show the list scrolls.
- **Header:** "Recently Added · 212 movies", plus a yellow-key **Sort** hint and a "Newest first ⌄" button.
- **Grid:** 6 columns of 209 × 314 posters with a 96 px row gap. The focused poster scales to 1.08 and shows a caption: the title (23 px, weight 600) and "2025 · Drama · 2 h 14 min".

**Behaviour**
- **Sort** options: Newest first, A–Z, Year, Rating.
- **Long-press** offers Add to My List and Mark as Watched.

### 4.14 Movie Detail (`14-movie-detail`)

**Layout**
- **Backdrop:** full bleed, with the subject on the right and left/bottom scrims. A back pill "‹ Movies" sits at top left.
- **Title art:** the provider's logo image, or else the title as large text.
- **Meta:** "Drama · Mystery · 2025 · 2 h 14 min", with badges 13+, 4K, HDR, 5.1, CC.
- **Text:** a synopsis of up to 3 lines, then the cast and director line.
- **Buttons:** **Resume** (default focus, white), From the Beginning, Trailer, and + (My List). A progress bar under them reads "1 h 02 min left".
- **Tabs:** More Like This · Cast & Crew · Audio & Subtitles · File Info, above a row of 150 × 225 posters.

If the movie hasn't been started, the first button is **Play**.

**File Info** lists the container, video and audio codecs, the HDR format and the engine Plasma will use, for example "Plays with the Android player · Dolby Vision".

### 4.15 Series (`15-series`)

The same layout as Movies, with series categories:
- **Categories:** New Episodes 86 (selected), Continue Watching 4, My List 9, Returning Soon 31, then genres.
- **Focused caption:** "Atlas Station · Resume S2, E4 · 18 min left", with a progress bar on the poster.
- **Poster badges:** "NEW EPISODE" and "NEW SEASON" at bottom left.

### 4.16 Series Detail (`16-series-detail`)

**Layout**
- Backdrop with a back pill "‹ Series", and title art.
- Meta: "Sci-Fi · Drama · 2024–2026 · 3 Seasons", with badges 16+, 4K, HDR, 5.1, then a synopsis.
- Buttons: **Resume S2, E4** (default focus), Trailer, and + (My List). A line below reads "Dead Reckoning · 18 min left".
- Season tabs (Season 1, 2, 3), with "8 episodes". On the right, a green-key hint reads "Mark Season as Watched". This is a contextual colour key.
- An episode row of 336 × 189 cards: "4 · Dead Reckoning", then the duration or "18 of 58 min left", and a check on watched episodes.
- The **current episode** has a 2 px outline at 50 % white plus its progress bar. This marks the current item; it is not focus.

**Focus:** Down from the buttons goes to the seasons, then to the episodes.

### 4.17 Recordings (`17-recordings`)

**Layout**
- **Header:** "‹ Home · Recordings · 20:41".
- **Left column:**
  - Filters: **All Recordings 12** · Series 6 · Sports 3 · News 2 · Movies 1, a divider, then Scheduled 3 · Series Rules 2.
  - A **Storage** card at the bottom: "USB drive", a bar at 58 % used, "212 GB free of 500 GB", "About 96 hours in HD".
- **Coming Up:**
  - The header reads "Coming Up · 3 scheduled", with the status line "● No conflicts · recordings use 1 of 2 connections".
  - Three 100 px cards: title, then logo with time, then a rule icon (single red dot for one-off, double for a series rule).
- **Recorded:**
  - The header reads "Recorded · 12 recordings · 31 hours", with Sort.
  - A 4-column grid of 16:9 thumbnails, 184 px tall, each showing one of these states:
    - recording now: REC badge, red progress bar, "Recording · 19:45–22:00" (this tile has default focus);
    - series folder: stacked layers, "3 EPISODES", "2 unwatched · Series rule";
    - unwatched: white dot;
    - watched: check mark;
    - resume progress bar;
    - amber retention warning: "Deletes in 2 days · keep last 3".
- **Footer:** Red Delete · Green Keep · Yellow Sort · Blue Select · OK Play · ≡ Options.

**Behaviour**
- **OK on a recording in progress** plays it from the start with the live edge available ("chase play").
- **A series folder** opens its episode list, reusing the Series Detail episode row.
- **Scheduled** lists every scheduled recording. Each can be edited (start and end padding) or cancelled.
- **Series Rules** lists the rules: title, channel or any channel, keep last N, padding.
- **Keep** (green) protects a recording from auto-delete. **Select** (blue) starts multi-select for deleting.

### 4.18 Catch-up (`18-catch-up`)

**Layout**
- **Header:** "‹ Home · Catch-up".
- **Left column:** "Channels with catch-up · 214". Each row has a 108 × 52 logo plate, the name, and its archive depth ("7 days"). The selected channel has a `.08` fill.
- **Channel header:** "Arena Sports 1 · 105 · 7-day archive", with a green-key hint: "Catch-up for any channel from the Guide".
- **Day tabs:** only the days inside the archive. Today · **Yesterday** (selected) · Mon 19 … Thu 15.
- **Programme rows** (124 px):
  - a 192 × 108 thumbnail (art or logo fallback; watch progress shown on it);
  - the time, the title (26 px, weight 600) and a subtitle;
  - the duration with ↺ on the right.

  The focused row is white. **Unavailable** programmes are dimmed and show a lock icon, "Unavailable", and "Not available for catch-up in your region".
- **Footer:** OK Watch from the start · CH ± Change day · ≡ Options.

### 4.19 Settings (`19-settings`)

**Layout:** tvOS pattern. A detail panel on the left shows the focused row: a 220 px icon tile (radius 48), the title (38 px, weight 600), a description, and status lines. A list on the right has 66–68 px rows with 5 px gaps: icon, label, value, chevron.

| Row | Value shown |
|---|---|
| **Portals & Playlists** | 3 sources |
| Channels (opens 4.29) | Favorites count from 1 |
| TV Guide | 2 XMLTV feeds · 7 days |
| Playback (opens 4.27) | VLC · match frame rate on |
| Recording (opens 4.34) | USB drive · 212 GB free |
| Parental Controls | On · 18+ locked |
| Profiles | 4 profiles |
| — divider — | |
| Launcher & Home | Plasma is your Home screen |
| Remote & Buttons (its Phone Remote page is 4.33) | Colour keys · long press |
| Appearance | Accent · 24-hour clock |
| Network | Wi-Fi · 94 Mb/s |
| About | Version 1.0.0 |

### 4.20 Portals & Playlists (`20-settings-portals-and-playlists`)

**Detail panel** for the focused source:
- an icon tile, the name, and a type line ("Stalker portal · signs in with this box's MAC address");
- status: "● Connected · 42 ms";
- details: Server (`portal.example.net/c/`) · MAC address (`00:1A:79:4C:2E:91`) · Subscription ("Active until 12 March 2027") · Connections ("1 of 2 in use") · Last updated ("Today, 18:02") · Library ("1,284 channels, 8,412 movies, 2,106 series, 7-day catch-up").

**List**
- **Sources:** Home Portal (focused; Stalker; Connected) · Backup Line (Xtream; Standby) · Sports Extras (M3U, 212 channels; amber "Guide is 2 days old") · **Add Portal or Playlist**.
- **Options:** Refresh Sources (Every 12 Hours) · TV Guide Sources (2 XMLTV Feeds) · Merge Duplicate Channels (On) · Channel Logos (From Portal).

**Footer:** links to the other settings pages.

Merge Duplicate Channels here is a shortcut to the same setting in Settings › Channels (4.29).

**Rows below the fold** (not drawn): Fail Over to Backup (On) · Remember What Works per Channel (On; 5.1).

**Source actions** (OK on a source; not drawn): Edit · Refresh Now · Set as Primary · Test Connection · Account Info · **Playback for This Source** · Disable · Delete.

**Playback for This Source** (not drawn; Settings pattern) holds the per-source overrides from 5.4: Player Engine, Audio Output, Buffer Size, User Agent, Referrer and Extra Headers, Stream Format (Xtream: TS or HLS) and udpxy Proxy (M3U with multicast). Each one defaults to "Same as Playback settings".

### 4.21 Add a Source (`21-add-a-source`)

**Layout**
- **Header:** "‹ Portals & Playlists · Add a Source".
- **Left column** (1060 px):
  - **Segmented type control:** **Stalker Portal** | Xtream Codes | M3U Playlist.
  - A help line: "Signs in with a MAC address, like a MAG box. Your provider gives you the portal URL and registers the MAC."
  - **Floating-label fields** (76 px):
    - Name;
    - **Portal URL** (focused: white with a caret);
    - MAC address, "00:1A:79:4C:2E:91", with "Made for this TV · change";
    - a row with **Advanced** ("MAG250 · serial · time zone") and a **Primary source** switch.
  - **On-screen keyboard:** a digits row; QWERTY rows with ":" "." "/"; a shortcut row with `http://` `.com` `.net` `:8080` `/c/` `-` `_`, Space, and **Next**.
  - **Connect**, with "Plasma checks the portal, then loads channels, the guide and VOD in the background."
- **Right column** (588 px): an **"Easier on Your Phone"** card:
  - a 264 px QR code on white;
  - "Or open **192.168.1.42:8484** and enter **7KQ 4MZ**";
  - "The code changes every 10 minutes. Details stay on your home network."

**Fields by type**
- **Stalker:** Name · Portal URL · MAC (generated or provider-registered). Advanced: STB model (default MAG250), serial number, device ID 1 and 2, signature, time zone, user-agent override, login and password (some portals need them).
- **Xtream:** Name · Server URL (`http(s)://host:port`) · Username · Password · Stream format (TS / HLS) · Guide (the provider's `xmltv.php`, plus an optional extra XMLTV URL).
- **M3U:** Name · Playlist URL (or a file sent from the phone) · XMLTV URL(s), filled automatically from `url-tvg` · User agent · Refresh interval.

**Connect result sheet**
- Success shows the account summary: expiry, maximum connections, channel/VOD counts.
- Failure shows a plain reason with a fix:
  - "Portal rejected this MAC address. Check it's registered with your provider."
  - "Wrong username or password."
  - "Can't reach the server. Check the address and port."

### 4.22 Launcher & Home (`22-settings-launcher-and-home`)

**Detail panel:** "Home Button" with the text "Choose what opens when you press Home on any remote. Plasma Home keeps your last channel playing behind your sections, favorites and apps." Below it, the choices: **Plasma Home ✓** · Live TV, last channel · Channel list · TV Guide.

**Rows**

| Row | Value shown |
|---|---|
| Use Plasma as Home Screen | Home Button Override › (opens Home Screen setup, 4.23) |
| **Home Button** (focused) | Plasma Home |
| Long-Press Home | Recent Apps |
| When the TV Turns On | Last Channel |
| Live Preview on Home | Muted |
| Home Rows (holds Continue Watching, 4.32) | Favorites, Continue Watching, Apps |
| Show Android Apps | On |
| Screensaver | After 10 Minutes |
| Clock | 24-Hour |

**"Use Plasma as Home Screen"** shows the method that is active on this TV and opens Home Screen setup (4.23).

**Rows below the fold** (the list scrolls; not drawn), placed right after When the TV Turns On:
- **When the TV Turns Off:** Stop after 1 minute (default) · Stop at once · Keep playing (5.20).
- **Keep Watching in a Small Window:** When opening another app (default, on TVs with picture-in-picture) · Never. The row is hidden on TVs without picture-in-picture (5.20).

### 4.23 Home Screen setup (`23-home-screen-setup`)

Reached from Settings › Launcher & Home › Use Plasma as Home Screen, and shown as a step during first run.

**Layout** (Settings pattern)
- **Detail panel** for the focused method. The board shows Replace Google TV Home:
  - a terminal icon tile and "Advanced · uses Android's debugging tools (ADB)";
  - "Turns off Google TV Home, so Home, waking the TV and power-on all open Plasma, with no flash of Google TV first.";
  - two green ticks: "Pair once. Plasma can undo it later by itself." and "Google TV Home is turned off, not deleted.";
  - two amber cautions: "A system update can turn Google TV Home back on. Plasma will offer to fix it." and "On some TVs, inputs live in Google TV Home. Use Plasma's Inputs tile instead."
- **This TV** card (outlined, not focusable): the device and OS ("Google TV Streamer · Android TV 14") and live status ("● Home button opens Plasma with Home Button Override").
- **Ways to make Plasma your Home screen:**

  | Row | Subtitle | Value |
  |---|---|---|
  | Home Button Override | Accessibility service · works like Projectivy | ● On |
  | **Replace Google TV Home** (focused) | Advanced · ADB from this TV or a computer | Set Up |
  | Default Home App (dimmed here) | For TVs that let you choose a Home app | Not available on this TV |

- **If something goes wrong:** Restore Google TV Home ("Turns Google TV Home back on") · Help and Commands ("Step-by-step guide and adb commands for a computer").

**Behaviour**
- **Detect what applies on this TV:**
  - whether it offers a Home chooser or the Home role;
  - which package is the stock launcher;
  - whether Plasma's accessibility service is on;
  - whether the stock launcher is disabled;
  - which activity Home resolves to right now.
- **Only one method is active at a time.** After Replace succeeds, offer to turn the override off. If another launcher's override is on (Projectivy, Button Mapper), warn: "Projectivy's override is also on. Turn it off so only one app answers the Home button."
- **Home Button Override** opens the explainer sheet, then `Settings.ACTION_ACCESSIBILITY_SETTINGS`. If the toggle is greyed out as a restricted setting, show the fixes from 5.13.
- **Restore Google TV Home** undoes Replace: over the on-device ADB pairing if there is one, otherwise by showing the commands and the manual path.

### 4.24 Replace Google TV Home (`24-replace-google-tv-home`)

**Layout**
- **Header:** "‹ Home Screen · Replace Google TV Home". Intro: "Three steps. Plasma pairs with this TV once, then does the rest."
- **Step cards.** Done steps get a green check and "Done". The current step has a 2 px outline at 50 % white. Steps not yet reached are at 55 % opacity.
  1. **Turn On Wireless Debugging:** "In Settings › System › About, press OK on Android TV OS build seven times. Then turn on Developer options › Wireless debugging."
  2. **Pair Plasma with This TV:** "Choose Pair device with pairing code. While the code is on the TV, scan the QR code with your phone and type the code and port there." It has an **Open Developer Options** button (focused) and the status "● Waiting for the code from your phone".
  3. **Replace Google TV Home:** "Plasma becomes Home and turns off Google TV Home and its fallback."
- **Prefer a computer?** A monospace block (Space Mono 18 px on `#111113`, radius 12) with the exact commands for this TV, then "To undo, run pm enable for the same two packages, or use Restore Google TV Home."
- **Right card, "Type the Code on Your Phone":** a QR code that opens `http://<tv-ip>:8484/adb?code=…`, "Or open 192.168.1.42:8484 on your phone", and a note: "Plasma only connects to this TV. It stays paired so it can undo the change later. You can turn Wireless debugging off when you're done."

**Behaviour** (implementation details in 5.13)
- **Step 1** is ticked automatically when `Settings.Global` shows developer options and wireless debugging are on. Open Developer Options launches `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`. If the TV is on Ethernet and wireless debugging isn't offered, say so and suggest switching to Wi-Fi for setup.
- **Step 2.** While the TV shows the pairing dialog, the phone page posts the code and pairing port to Plasma's local server. Plasma pairs with `127.0.0.1:<pairing port>`, finds the connection port over mDNS (`_adb-tls-connect._tcp`), and connects.
- **Older TVs** with fixed-port network debugging skip pairing. Plasma connects to `127.0.0.1:5555`, and the TV asks "Allow debugging?"; the user picks Always allow.
- **Step 3** runs the commands. It then checks that Home resolves to Plasma and shows "Done. Press Home to try it." If anything fails, it shows the command output and leaves everything reversible.
- **The command block** uses this TV's real IP address, ports, application id and detected launcher packages.

### 4.25 Parental PIN (`25-parental-pin`)

**Layout**
- **Header:** "‹ Live TV".
- A centred 120 px lock tile (radius 30), then "Enter PIN to Watch" at 48 px, weight 600.
- The logo with "110 · Fight Night: Prelims" and an 18+ badge.
- **Four dots**, filled as digits are entered.
- **Number pad:** a single row 1–0 plus ⌫, with 88 px keys (radius 18). The focused key is white at 1.1 scale.
- **Unlock for:** "This programme ⌄", with the options This programme / 1 hour / Until the TV sleeps.
- A hint: "You can also type the PIN with the number keys. Forgot it? Reset it in Settings › Parental Controls."

**Wrong PIN**
- The dots shake (a fade instead under Reduce Motion) and the line reads "Wrong PIN. 2 tries left".
- After 5 wrong attempts, entry is blocked for 1 minute.

### 4.26 UI kit and remote map (`26-ui-kit-and-remote-map`)

A reference board, not a screen. It shows:
- colour swatches and the type scale;
- focus samples ("lift, shadow and white fill, never a colored ring");
- components: buttons, settings row, badges, logo plate;
- a remote diagram with eight numbered callouts: Home, Back, OK and arrows, Power, Menu, CH ±, colour keys, 0–9.

Use it to check your tokens.

### 4.27 Settings — Playback (`27-settings-playback`)

Reached from Settings › Playback, and from Player Options › Switch Player on the board. Section 5.4 explains how each setting works.

**Layout** (Settings pattern)
- **Header:** "‹ Settings · Playback · 20:41".
- **Detail panel** for the focused row. The board shows Player Engine:
  - a 180 px icon tile (a screen with a play symbol) and the title "Player Engine";
  - "VLC plays almost any IPTV stream, including MPEG-2, interlaced sports, teletext and multicast. If a stream won't start, Plasma tries the next engine by itself.";
  - three engine cards (`surface1`, radius 12, not focusable), each with a status on the right:

    | Engine | Subtitle | Status |
    |---|---|---|
    | VLC | libVLC 3.7 · live TV and most streams | ● Default (green) |
    | mpv | libmpv · backup, and Dolby Digital encoding | ● Backup (grey) |
    | Android player | Media3 · Dolby Vision and HDR10+ movies | ● HDR movies (grey) |

  - a note: "Plasma remembers the engine that worked for each channel. Change it from the player's options."
- **List** in three groups, with small section labels (21 px, weight 600, `text3`):

  | Group | Row | Value shown |
  |---|---|---|
  | Player | **Player Engine** (focused) | VLC |
  | | If a Stream Won't Play | Try mpv, then Android player |
  | | HDR and Dolby Vision Movies | Android player |
  | Picture | Video Decoding | Hardware |
  | | Deinterlacing | Auto |
  | | Match Frame Rate | On · seamless only |
  | Sound, subtitles and streaming | Audio (opens 4.28) | Passthrough · 3 formats |
  | | Subtitles and Teletext | Off |
  | | Buffer Size | Medium · 3 s |
  | | Rewind and Local Catch-up (opens 4.31) | 2-hour buffer · 1 channel |

- Rows are 62 px (68 px when focused), with 5 px gaps.

**Options for each row** (each opens a small sheet anchored to the right, like Sort):
- **Player Engine:** VLC · mpv · Android player.
- **If a Stream Won't Play:** Try mpv, then Android player · Try the Android player only · Show an error.
- **HDR and Dolby Vision Movies:** Android player · Same as Player Engine.
- **Video Decoding:** Hardware · Hardware, copy back · Software.
- **Deinterlacing:** Auto · Off · Always.
- **Match Frame Rate:** Off; On · seamless only; On · always.
- **Subtitles and Teletext:** Off · On · Forced only, then Style (size, colour, background).
- **Buffer Size:** Minimal 0.5 s · Small 1.5 s · Medium 3 s · Large 6 s · Extra Large 12 s. The sheet says "A bigger buffer rides out a shaky connection, but channels take longer to start."
- **Rewind and Local Catch-up** opens its own page (4.31). The value sums it up: the rewind buffer's length and the number of local catch-up channels.

**Further rows below the fold** (scroll; not drawn): Offer Watch from the Start (On; the hint in 4.11) · Connection Timeout (15 s) · Fast Zap (Off) · Up/Down in Player (Change Channel) · Auto-Play Next Episode (On) · Open Movies and Series In (Plasma) · **Advanced**: Tunneled Playback (Off, Android player only) · Play Live TV Through Stream Hub (On) · Show Stream Stats (Off) · Forget Players Chosen per Channel · Save Player Logs.

### 4.28 Settings — Audio (`28-settings-audio`)

Reached from Settings › Playback › Audio.

**Layout** (Settings pattern)
- **Header:** "‹ Playback · Audio · 20:41".
- **Detail panel** for the focused row. The board shows Audio Output set to Passthrough:
  - a speaker icon tile, the title "Passthrough", and "Sends Dolby and DTS untouched to your sound system";
  - an outlined card (not focusable): "Connected", then "● HDMI eARC · AV receiver"; "Your system supports", then format chips. Supported formats get a check on a green-tinted chip: Dolby Digital, Dolby Digital Plus, Dolby Atmos, DTS. Unsupported ones get a dash on a grey chip: DTS-HD MA, Dolby TrueHD;
  - a note: "While passthrough is on, your receiver handles volume and night mode. Other sound is decoded and sent as PCM."
- **List:**

  | Group | Row | Subtitle | Value shown |
  |---|---|---|---|
  | Output | **Audio Output** (focused) | | Passthrough |
  | | Passthrough Formats | | Dolby Digital, DD+, DTS |
  | | Encode Surround to Dolby Digital | For systems that only take Dolby Digital · uses mpv | Off |
  | | Stereo Downmix | | Off |
  | Sound | Night Mode | Evens out loud ads and quiet speech · decoded audio only | Off |
  | | Audio Delay | Fixes lip-sync for this TV · channels can add their own | 0 ms |
  | | Channel Volume Levels | Set from a channel's options · decoded audio only | 3 channels |
  | Languages | Preferred Language | | English, then original |
  | | Skip Audio Description | | On |

- Rows with a subtitle are 84 px tall.

**Behaviour**
- **The card is live.** It updates when the receiver or soundbar is switched on or off, or when the output changes (HDMI, eARC, optical, Bluetooth, the TV's speakers). Section 5.4 describes the detection.
- **Audio Output options:** Auto (the default) · Passthrough · Decode to PCM. The detail panel's title and text follow the focused option.
- **Passthrough Formats** is a checklist of the formats the system reports. Atmos has no checkbox of its own: it travels inside Dolby Digital Plus and TrueHD.
- **Turning on Encode Surround to Dolby Digital** shows a one-line note: "Surround sound that can't pass through will play with mpv."
- **Audio Delay** opens a slider from −500 to +500 ms in 10 ms steps, and it applies while the user listens. Menu on a channel offers a delay for that channel only.
- **Channel Volume Levels** lists the channels with their own level ("Northline News +3 dB"), each with Reset, plus Reset All. Levels are set from Channel options › Volume Level while watching (5.4.6).

### 4.29 Settings — Channels (`29-settings-channels`)

Reached from Settings › Channels. Section 5.2 has the numbering rules.

**Layout** (Settings pattern)
- **Header:** "‹ Settings · Channels · 20:41".
- **Detail panel** for the focused row. The board shows Numbers in Favorites:
  - a 180 px icon tile with a # symbol, and the title "Numbers in Favorites";
  - "Favorites can count from 1 in the order you set, or keep each channel's number from the full list.";
  - **two example cards** side by side, each 310 px wide. Each shows the same three favorites as numbers and 108 × 52 logo plates.
    - "Keep channel numbers": 105 Arena Sports 1, 107 Velocity Motor, 101 Northline News. Outlined, numbers in `text2`.
    - "✓ Count from 1": 1, 2 and 3 for the same channels. It is the selected option, so it has a `.08` fill, a 1.5 px outline at 50 % white, and white numbers.
  - a note: "Type 2 while watching from Favorites to get Velocity Motor. Numbers that aren't in Favorites, like 105, still tune from the full list."
- **List:**

  | Group | Row | Subtitle | Value shown |
  |---|---|---|---|
  | Numbers | Channel Numbers | | From your playlists |
  | | Duplicate Numbers (opens 4.30) | 4 channels had a number that was already taken | 4 fixed |
  | | **Numbers in Favorites** (focused) | | Count from 1 |
  | | Numbers in Groups | | Keep channel numbers |
  | | Number Keys | In Favorites, 2 means the second favorite | Current list first |
  | Duplicate channels | Merge Duplicate Channels | | On · 38 merged |
  | | Preferred Quality | | FHD, then HD |
  | Hidden | Hidden Channels | | 12 |
  | | Hidden Groups | | 3 |

**Options**
- **Channel Numbers:** From your playlists · In playlist order (1, 2, 3…) · Custom (edit numbers from Channel options).
- **Numbers in Favorites** and **Numbers in Groups:** Count from 1 · Keep channel numbers. Groups can also be set one by one from the group's Menu.
- **Number Keys:** Current list first · Full list only.
- **Preferred Quality:** an ordered list (4K, FHD, HD, SD) that decides which source plays first for a merged channel.

### 4.30 Duplicate Numbers (`30-duplicate-numbers`)

Reached from Settings › Channels › Duplicate Numbers, and from a quiet notice after a refresh that found new duplicates ("2 channels got new numbers · Review").

**Layout**
- **Header:** "‹ Channels · Duplicate Numbers · 20:41".
- **Left column** (x 96, w 1060):
  - an intro at 23 px: "Your playlists gave 4 channels a number that another channel already had. Plasma gave them new numbers by adding 1000. The same channel from two sources is merged instead.";
  - tabs: **Renumbered 4** (selected) · Merged 38;
  - **conflict rows**, 140 px tall with 8 px gaps. Each row has the contested number at 40 px with "used twice" under it, a divider, then two lines: logo plate, channel name, source name, and on the right "Keeps 108" for the channel that kept the number or "Now 1108" (white, weight 600) for the one that moved. Plates on unfocused rows use `surface3` so they stay visible on `surface1`.

    | Number | Keeps it | Moves |
    |---|---|---|
    | 104 | Arena Sports Extra (Home Portal) | Arena Sports Extra 2 (Sports Extras) → 1104 |
    | **108** (focused) | Courtside+ (Home Portal) | Courtside Classics (Sports Extras) → 1108 |
    | 109 | Fairway Golf (Home Portal) | Fairway Golf 2 (Sports Extras) → 1109 |
    | 212 | Ringside 1 (Sports Extras) | Ringside 2 (Sports Extras) → 1212 |

    Courtside Classics and the two Ringside channels have no logo, so their plates show the logo fallback: the name at 15 px, weight 600.
- **Right panel** (x 1196, w 628) for the focused conflict:
  - "Sports Extras · M3U playlist", then the title "Courtside Classics";
  - a `surface1` strip showing "108 → 1108" at 56 px, with the old number in `text4`;
  - "Courtside+ on Home Portal, your primary source, keeps 108. Type 1108 to watch Courtside Classics.";
  - buttons: **Keep 1108** · Choose Another Number · Swap Numbers with Courtside+;
  - a note: "Plasma fixes new duplicates the same way after each playlist refresh, and keeps any number you choose here."
- **Footer:** Yellow Sort · OK Change number · ≡ "Fix rule: add 1000" (switches the rule to Next Free Number).

**Behaviour**
- OK on a row moves focus to the right panel's buttons. Choose Another Number opens a number pad that warns if the number is taken.
- The Merged tab lists each merged channel with its sources in play order, for example "Arena Sports 1 · Home Portal, then Backup Line". OK there offers Unmerge.

### 4.31 Settings — Rewind and Local Catch-up (`31-rewind-and-local-catch-up`)

Reached from Settings › Playback › Rewind and Local Catch-up. Section 5.5 has the behaviour.

**Layout** (Settings pattern)
- **Header:** "‹ Playback · Rewind and Local Catch-up · 20:41".
- **Detail panel** for the focused row. The board shows Rewind Buffer:
  - a rewind-clock icon tile and the title "Rewind Buffer";
  - "Plasma keeps the last 2 hours of the channel you're watching, so you can pause, rewind and start again even when the channel has no catch-up.";
  - **storage card** (outlined): "USB drive" with "212 GB free of 500 GB"; a 10 px bar split into Recordings 246 GB (`text`), Local catch-up 38 GB (`text2`) and Rewind 4 GB (`text4`), with a matching legend. Below a divider, an amber line: "Courtside+ uses 1 of your 2 Home Portal connections".
  - a note: "When you rewind past the buffer, Plasma switches to the channel's own catch-up if it has one."
- **List:**

  | Group | Row | Subtitle | Value shown |
  |---|---|---|---|
  | The channel you're watching | **Rewind Buffer** (focused) | | Always · 2 hours |
  | Local catch-up | Local Catch-up Channels | Recorded all the time, for channels without catch-up | Courtside+ |
  | | Keep | | Last 24 hours |
  | | While the TV Is Off | Only if the TV stays online in standby | Keep recording |
  | Storage | Save To | | USB drive |
  | | Storage Limit | Oldest local catch-up goes first · recordings are kept | 50 GB |
  | | Clear Local Catch-up | | 38 GB |

**Options**
- **Rewind Buffer:** Off · When I pause · Always; length 30 min, 1 hour, 2 hours or 4 hours. The sheet notes "Always writes to storage all the time; best with a USB drive."
- **Local Catch-up Channels:** a channel picker (Live TV overlay pattern) with ticks. Before ticking, it shows the connection cost: "Uses 1 of your 2 Home Portal connections all the time." It refuses a channel that would leave no connection for watching.
- **Keep:** 6, 12, 24 or 48 hours, or 7 days.
- **While the TV Is Off:** Keep recording · Pause until the TV is on. The detail text explains Google TV's energy modes (5.5).
- **Save To:** USB drive · This TV. **Storage Limit:** 10–500 GB on a drive, 2–16 GB on the TV.

### 4.32 Settings — Continue Watching (`32-settings-continue-watching`)

Reached from Settings › Launcher & Home › Home Rows › Continue Watching. Section 5.19 has the behaviour.

**Layout** (Settings pattern)
- **Header:** "‹ Launcher & Home · Continue Watching · 20:41".
- **Detail panel** for the focused row. The board shows Include Other Apps:
  - an icon tile of stacked screens with a play symbol, and the title "Other Apps";
  - "Adds what you were watching in other apps to Continue Watching on Home, next to Plasma's own movies, series and catch-up.";
  - **sources card** (outlined): "From Android's Play Next row", "● Allowed", "2 apps · 4 items"; then "From your Jellyfin server", "● media.home.lan · Maya", "3 items";
  - a note: "Only apps that add to Android's Play Next row can appear. Plasma reads Jellyfin straight from your server, so it works without the Jellyfin app."
- **List:**

  | Group | Row | Subtitle | Value shown |
  |---|---|---|---|
  | Other apps | **Include Other Apps** (focused) | | On |
  | | Apps | Reelbox and Driftwood Podcasts | 2 apps |
  | | Jellyfin Server | media.home.lan · signed in as Maya | Connected |
  | | Play Jellyfin Items In | | Plasma |
  | The row | Show Next Episodes | | On |
  | | Hide Items Older Than | | 30 days |
  | | Kids Profiles See | | Plasma items only |

**Behaviour**
- **Turning on Include Other Apps** first shows an explainer sheet: "Plasma will read the Play Next row that apps on this TV share. It stays on this TV." Then it asks for Android's "read TV listings" permission. If the TV never shows the prompt, the sheet shows the ADB command and offers to grant it during the on-device ADB setup (5.19).
- **Apps** lists every app that has published to Play Next, each with a switch.
- **Jellyfin Server** opens a sign-in sheet. It finds servers on the network, or takes an address, then signs in with **Quick Connect** (the TV shows a code to approve in another Jellyfin app) or a username and password. Each Plasma profile can link its own Jellyfin user.
- **Play Jellyfin Items In:** Plasma · the Jellyfin app (if installed).

### 4.33 Phone Remote (`33-phone-remote`)

A presentation board rather than a TV screen. On the left, the TV side; on the right, three phone screens (360 × 760 CSS px each) of the web page Plasma serves. Section 5.21 has the behaviour.

**On the TV** (Settings › Remote & Buttons › Phone Remote, drawn as a `surface1` card)
- "On the TV", then "Settings › Remote & Buttons › Phone Remote".
- A 212 px QR code on white, which opens `http://192.168.1.42:8484/remote?code=7KQ4MZ`.
- "Or open **192.168.1.42:8484** and enter **7KQ 4MZ**".
- **Paired phones:** "Maya's iPhone · ● Connected" and "Daniel's Pixel · Yesterday". OK on a phone offers Rename and Forget.

**The phone page** (same tokens as the TV: `bg`, `surface1`, Instrument Sans, white for the primary action)
- **Every screen:** a top bar with the "plasma" wordmark and "● Living Room TV", and a bottom tab bar: **Remote** · **Channels** · **Type**. Touch targets are at least 44 px.
- **Remote tab:**
  - a now-playing card: logo plate, "105 · Arena Sports 1", the title, a timeline with the live edge, "2:14 behind live" and a **Go Live** pill;
  - a 240 px D-pad ring with a 100 px OK button;
  - round buttons with labels: Back · Home · Menu · Numbers (opens a 0–9 pad);
  - media and channel keys: ◀◀ · Play/Pause · ▶▶ · CH ▲▼;
  - the four colour keys, **labelled with what they do on the current TV screen** ("Favorite · Catch-up · Sort · Multiview" in Live TV).
- **Channels tab:** a search field, list chips (Favorites selected, then Sports, News, Movies…), "Favorites · 24" and "Numbers count from 1", then rows with the list number, a logo plate, the channel and the programme on now with its progress. The playing channel has a `.08` fill and a speaker icon. A tap tunes.
- **Type tab:** "Typing into Search on the TV" with "Results update on the TV as you type"; the text field mirrors the TV's field ("night"); Paste · Clear · **Done**; "On the TV: 4 programmes · 2 movies"; then the phone's own keyboard. The tab opens by itself when a text field gets focus on the TV.

**The Plasma Remote apps** (5.22) use these three phone screens as their spec, in the same tokens. Their extra screens (Find your TV, Pairing, Settings) aren't drawn; 4.35 lists them. On the TV, the Phone Remote page gets one more line under Paired phones once the apps exist (not drawn): "Prefer an app? Plasma Remote for iPhone and Android finds this TV by itself." Each paired phone then shows how it connects, "App" or "Browser", after its status.

### 4.34 Settings — Recording (`34-settings-recording`)

Reached from Settings › Recording. Section 5.6 has the behaviour.

**Layout** (Settings pattern)
- **Header:** "‹ Settings · Recording · 20:41".
- **Detail panel** for the focused row. The board shows Copy to Network Folder:
  - a folder-on-network icon tile and the title "Network Folder";
  - "Plasma copies finished recordings to your network storage, named the way Jellyfin expects, so they show up there with their artwork and details.";
  - **folder card** (outlined): "nas.home.lan › Media › Recordings" with "● 3.1 TB free", then a Space Mono block (15 px on `#111113`) showing the layout Plasma writes:

    ```
    TV Recordings/
      Kingdoms of Ice/Season 01/
        Kingdoms of Ice S01E03.ts  + .nfo, -thumb.jpg
      Late Night Sessions/Season 2026/
        Late Night Sessions S2026E001.ts
    Movie Recordings/
      The Last Lighthouse (2025)/
        The Last Lighthouse (2025).ts  + movie.nfo
    Other Recordings/Arena Sports 1/
      2026-10-21 1945 Harbour City v Redmoor.ts
    ```

  - a note: "After each copy, Plasma asks your Jellyfin server at media.home.lan to look for new files."
- **List:**

  | Group | Row | Subtitle | Value shown |
  |---|---|---|---|
  | Where recordings go | Record To | | USB drive |
  | | **Copy to Network Folder** (focused) | | On · nas.home.lan |
  | | After Copying | | Keep on the drive for 7 days |
  | | Name Files for Jellyfin | Folders, info files and artwork | On |
  | Timing | Start Early | | 1 min |
  | | End Late | | 5 min |
  | | Extra Time for Sports | | +15 min |
  | Clean-up | Delete Watched Recordings | | Never |
  | | When Space Runs Low | | Delete oldest watched |

**Options**
- **Record To:** USB drive · This TV · Network folder (records straight to the share; see 5.6 for what happens when it drops).
- **Copy to Network Folder:** Off · On, then a sheet to pick the share: servers found on the network or an address (`smb://nas.home.lan/Media`), a username and password, and a folder.
- **After Copying:** Keep on the drive for 7 days · Keep until space is needed · Delete from the drive.
- **Name Files for Jellyfin:** On (folders, `.nfo` files and artwork, as in 5.6) · Off (plain "Channel – Title – date" names).

### 4.35 Not drawn yet: build these from the same patterns

| Screen or state | Pattern to reuse |
|---|---|
| Welcome / first run | centred wordmark, one-line pitch, Add a Source / Restore from Backup buttons |
| Sync progress (after Connect) | centred checklist: Signing in ✓ · Channels 1,284 ✓ · Guide 7 days ⋯ · Movies 8,412 ⋯ · Series 2,106 ⋯, plus "Continue to Home" while VOD keeps loading |
| First-run Home step | Home Screen setup (4.23) shown inside onboarding |
| Home Button Override explainer | centred sheet: what the service watches (only which app is in front), what it never reads, then **Open Accessibility Settings** |
| "Google TV Home came back" prompt | centred sheet after a system update: "A system update turned Google TV Home back on. Turn it off again?" with Turn Off / Keep Override |
| Programme sheet (Guide, future) | Player Options sheet style: Remind Me · Record · Record Series · More Episodes |
| Recording conflict | sheet: "You can record 2 things at once. Choose what to skip." with a list of the overlapping items |
| Channel options | sheet: Add to Favorites · Move · Hide · Lock · Switch Source · Change Number · Volume Level · Add to Local Catch-up · Channel Info |
| Volume Level bar | Audio delay slider pattern over the playing video: "Volume level · Northline News · +3 dB", ◀ ▶ in 1 dB steps from −6 to +6 with a tick at 0, OK to keep, Back to cancel. With passthrough on: "Your receiver sets the volume while audio passes through" and a **Decode This Channel** button (5.4.6) |
| Switch Source with a remembered source | Player Options › Switch Source list (4.10); the remembered source is first with a small "Works best" tag and "Remembered 3 days ago" under it, plus Forget at the end of the list (5.1) |
| "Started 20 min ago" hint | one extra line in the zap banner (4.11) |
| Picture-in-picture window | video only, no Plasma chrome, in the corner the TV chooses. The system's window menu shows Previous Channel · Play/Pause · Next Channel. Opening it returns to the full-screen player (5.20) |
| Back from standby | no screen of its own: Plasma tunes the last channel again and shows the normal zap banner. If the provider refuses the connection, the error card offers Try Again |
| Remote & Buttons | Settings pattern: Phone Remote (On · 2 phones) opens the Phone Remote page (4.33), then the rows in 5.16 |
| Phone connected toast | reminder-toast style, 4 s, no buttons: "Maya's iPhone is now a remote" |
| Plasma Remote app: Find your TV (M9) | the phone page's tokens (4.33): "plasma" wordmark, "Find your TV", then rows for each TV found ("Living Room TV · Plasma 1.0", a TV icon tile, ● when it's paired), then Scan Code and Enter Address as outlined buttons. Empty state: "Looking for TVs running Plasma on this Wi-Fi…" with a Help link |
| Plasma Remote app: Pairing (M9) | full-screen camera with a rounded viewfinder and "Scan the code on your TV", or six code boxes with "Enter the code shown on Living Room TV", then "Name this phone" with one text field and Done |
| Plasma Remote app: Settings (M9) | a phone settings list in the same tokens, with the rows in 5.22; Paired TVs each have Forget |
| Plasma Remote app: Swipe pad (M9) | the D-pad ring's area as one rounded `surface1` panel with a faint "Swipe · tap for OK" hint that fades after the first use |
| Network folder picker | Settings pattern: servers found on the network, then Enter Address; a username and password using the on-screen keyboard (or the phone's Type tab); then a folder list with **Use This Folder**. A test write runs before it saves |
| Network folder offline notice | toast, 6 s: "Can't reach nas.home.lan. Recordings will wait on the USB drive and copy when it's back." |
| Number pad for Change Number | Parental PIN pattern (4.25) with up to 4 digits and "108 is taken by Courtside+" under the dots |
| Local catch-up in the Guide and Catch-up screen | past cells and rows marked ↺ as for provider catch-up, with "Local · 24 h" as the archive depth in the Catch-up channel list |
| "New duplicate numbers" notice | toast after a refresh, top right, no buttons for 6 s: "2 channels got new numbers · Settings › Channels" |
| Read TV listings explainer | centred sheet before Android's permission prompt (4.32) |
| Jellyfin sign-in | centred sheet: server list from discovery, Enter Address, then a big Quick Connect code with "Approve it in another Jellyfin app" and a Use Password Instead button |
| Sort and Group sheets | small sheet anchored to the right |
| Errors | inline cards in the content area, with Try Again / Switch Source / Switch Player / Settings. In the player: "Can't play Arena Sports 2. VLC, mpv and the Android player all failed." plus the reason in plain words |
| Engine fallback toast | reminder-toast style at top right, no buttons, 4 s: "VLC couldn't play Arena Sports 2. Playing it with mpv." |
| Playback for This Source | Settings pattern (see 4.20); every row defaults to "Same as Playback settings" |
| Option sheets for Playback and Audio | small sheet anchored to the right, as for Sort; the selected option has a check |
| Audio delay slider | a bottom bar over the playing video: "Audio delay · +120 ms", ◀ ▶ in 10 ms steps, OK to keep |
| Empty states | centred icon, one line and one action ("No recordings yet. Press Record while watching or use the Guide.") |
| Settings sub-pages | Settings pattern (list on the right, detail on the left): see 5.16 for every row and its default |
| VOD player overlay, Next Episode card | Player overlay without the live edge; a small card at bottom right counting down "Next episode in 10 s" |
| Reminders list | under Recordings › Scheduled, as a separate section |
| Multiview channel picker | Live TV overlay (groups and list) |
| Inputs picker | settings-style list of HDMI inputs |
| Restore and update prompts | centred sheet |

---

## 5. Features and behaviour

### 5.1 Sources and accounts

- **Source types:**
  - Stalker (Ministra) portal, signed in by MAC;
  - Xtream Codes API;
  - M3U playlist with XMLTV.

  Section 7 has the protocol notes.
- **Several sources, one list.** Every enabled source contributes channels, and one source is the **primary**.
- **Merging duplicates** ("Merge Duplicate Channels", on by default):
  - Group channels that are the same across sources. Match on the same `tvg-id` or EPG id first, otherwise on the normalised name plus quality.
  - The result is one channel entry with an **ordered list of stream candidates**, primary source first. This drives Switch Source and automatic failover.
- **Failover:**
  - Trigger: the **source** fails: the connection times out, the server returns an HTTP error or an HTML page, or no data arrives for more than 8 s.
  - Retry once, then move to the next candidate.
  - Tell the user quietly with a toast: "Switched to Backup Line."
  - When data does arrive but the engine can't play it, the fault is the engine's, not the source's. Plasma then switches engine on the same source (5.4). The stream hub can tell the two cases apart because it sees the bytes arrive.
- **Remember What Works per Channel** (Settings › Portals & Playlists, on by default). This is the source version of the engine memory in 5.4.3.
  - **When Plasma learns:** after a failover, the new candidate plays for at least 10 minutes; or the first candidate failed on 3 of the channel's last 5 tunes and another one worked.
  - **What changes:** that channel tries the remembered candidate first next time. Failover still walks the others, and the primary source stays first for every other channel. A source the user picked by hand in Switch Source counts as remembered at once.
  - **Checking again:** after 7 days, the next tune tries the original order once. If the first candidate fails, Plasma goes straight to the remembered one without a toast; if it plays for 10 minutes, the memory is cleared.
  - **Seeing it:** Switch Source lists the remembered candidate first with "Works best", and Forget clears it (4.35).
  - **Stored** as `ChannelSourceMemory` (6.3), per channel and not per profile, because it describes the provider rather than a person's taste. It is cleared when either source is removed.
- **Refresh schedule:**

  | Data | When |
  |---|---|
  | Channels | every 12 h by default (6 / 12 / 24 h / manual) |
  | Guide | daily, and at start-up if it's older than 24 h |
  | VOD | weekly, or on demand |

  **Keep the old data until the new import succeeds**, so a failed import never empties a list.
- **Account info:**
  - Show the expiry date and maximum/active connections in the source's detail.
  - Warn 7 days before the subscription expires.
- **Connection budget.** Every concurrent stream counts against the account's limit: the main player (which also drives the previews), extra Multiview tiles, recordings and fast-zap pre-buffering.
  - The **stream hub** (5.4) opens every live connection, so it is the one place that counts them. Watching and recording the same channel share one connection.
  - **Before opening a stream**, check the budget.
  - **If it would exceed the limit**, ask, for example "Stop recording Harbour City v Redmoor to watch this?", or refuse with a plain explanation.
  - **Recordings** show the budget in their header ("recordings use 1 of 2 connections").
- **Per-source overrides:**
  - user agent, referrer and extra headers;
  - EPG time offset;
  - catch-up template;
  - logo source (from the portal, or user overrides);
  - playback: engine, audio output, buffer size, Xtream stream format and a udpxy proxy (5.4).

### 5.2 Channels

- **Channel numbers** (Settings › Channels, 4.29):
  - **From your playlists** (default): Stalker `number`, Xtream `num`, M3U `tvg-chno`.
  - **In playlist order:** 1, 2, 3… across sources in priority order, ignoring the playlists' numbers.
  - **Custom:** starts from either, then single numbers change with Channel options › Change Number.
  - A channel without a number gets the next free number after the highest one in its source, so every channel can be typed.
- **Duplicate numbers** (4.30):
  - The same channel from two sources is merged (Merge Duplicate Channels, 5.1), so it keeps one number.
  - **Different channels with the same number:** the channel from the higher-priority source keeps it (the primary first, then the order in Portals & Playlists). Within one source, the first in playlist order keeps it.
  - The others move to the number plus 1000 (108 → 1108), or plus 2000 if that's taken, and so on. Menu on the review screen switches the rule to Next Free Number.
  - The user can Keep, Choose Another Number or Swap. Choices are stored per source and channel (by `tvg-id`, else stream id, else name), and survive refreshes.
  - **Numbers are sticky.** A refresh never renumbers channels that already have a number. Only new channels and new conflicts are resolved, and a toast reports them (4.35).
- **Numbers in lists:**
  - **Favorites:** Count from 1 (default) or Keep channel numbers. Counting from 1 numbers the favourites 1…N in the user's order.
  - **Groups:** Keep channel numbers (default) or Count from 1, for all groups or per group (the group's Menu).
  - All Channels, Recently Watched and Search always show the full-list numbers.
- **Number keys** (default Current list first): in a list that counts from 1, or while watching a channel tuned from one, a typed number tunes that list's channel if it exists. Otherwise it tunes the full list's channel with that number. "Full list only" always uses the full-list numbers. The number box and the zap banner name the list (4.11).
- **Zapping follows the list:** CH ± and Up/Down move through the list the user tuned from (Favorites, a group or All Channels), wrapping at the ends. Tuning a full-list number switches the context to All Channels.
- **Preferred Quality:** orders a merged channel's candidates (default FHD, then HD, then 4K, then SD), so the first stream played matches the preference. Failover still walks the rest.
- **Hidden channels and groups** keep their numbers but are skipped by zapping. Typing a hidden channel's number shows "Channel 108 is hidden" with an Unhide button.
- **Groups:**
  - Groups come from the provider; users can hide, rename and reorder them.
  - Hidden groups stay out of search unless the user says otherwise.
  - Show counts next to each group.
- **Favourites:**
  - Kept per profile, in the user's order.
  - Red toggles a favourite; Menu › Move reorders it.
  - Favourites also appear as a row on Home.
- **Recently watched:** the last 50 channels, per profile.
- **Last channel:** saved, and used by "When the TV Turns On: Last Channel / Home / Guide" and by the Last Channel key.
- **Adult content:** groups whose names match a configurable keyword list are flagged as adult and locked by default (section 5.12).

### 5.3 Electronic programme guide (EPG)

- **Inputs:**
  - XMLTV URLs (plain, `.gz` or `.xz`; picked up automatically from the playlist header `url-tvg` or `x-tvg-url`);
  - Xtream `xmltv.php` for the whole guide, or `get_short_epg` as a now/next fallback;
  - the Stalker portal's own EPG.
- **Channel mapping:**
  - Match `tvg-id` to the XMLTV `<channel id>`; otherwise fall back to a normalised display-name match.
  - Settings › TV Guide also offers manual mapping.
- **Time:**
  - XMLTV times carry their offset (`20261021194500 +0000`).
  - Allow a correction per source in hours (from M3U `catchup-correction`, or set by the user).
  - Store everything in UTC and display it in the device's time zone.
- **Window and pruning:**
  - Keep the past for as long as the longest catch-up archive (7 days by default).
  - Keep the future for as far as the data goes (7–14 days).
  - Prune daily.
- **Freshness:**
  - Treat the guide as stale if the last import is more than 48 h old, or if the remaining programmes end within 12 h.
  - Stale shows amber "Guide is 2 days old" on the source and in the Guide header.
- **Reminders:** set per programme, stored in the database, and fired one minute before the start (configurable).

### 5.4 Playback engines and player settings

> **Decided:** **libVLC is the default engine** and **mpv is the backup** (the owner's call: VLC handles IPTV streams better than ExoPlayer in their experience). The **Android player (Media3)** plays Dolby Vision and HDR10+ movies and is the last resort (design recommendation). Screens: 4.10 (Switch Player), 4.27 (Playback) and 4.28 (Audio). Architecture: 6.5.
>
> **The engines are invisible.** libVLC, libmpv and Media3 are libraries inside Plasma that draw only video into Plasma's own surface. The user never sees VLC's or mpv's interface, and their apps don't need to be installed. Number entry, the zap banner, the guide, overlays, menus and remote keys are all Plasma's, and behave the same whichever engine is playing. The only time another app opens is when the user chooses an external player for movies (5.4.10, v1.x).

#### 5.4.1 Formats and why three engines

Plasma must play MPEG-TS over HTTP (most IPTV), HLS, MP4 and MKV for VOD, UDP/RTP multicast where an ISP's IPTV uses it, and DASH if a source offers it. No single engine on Android does all of that well.

| | VLC (libVLC 3.7) | mpv (libmpv) | Android player (Media3 1.10) |
|---|---|---|---|
| **Role in Plasma** | default for everything | backup; encoding surround to Dolby Digital | Dolby Vision and HDR10+ movies; last resort |
| Messy broadcast MPEG-TS (discontinuities, PID changes, joining mid-stream) | strongest: its own TS demuxer | good (FFmpeg) | weakest |
| HLS | good | good | strongest (live window, redundant streams) |
| UDP/RTP multicast | yes | yes | no |
| MPEG-2, interlaced H.264, MP2 audio | yes, hardware or FFmpeg software | yes, hardware or software | only what the box's decoders support |
| Software decoding fallback | built in (FFmpeg) | built in (FFmpeg) | needs the FFmpeg extension (audio only) |
| Deinterlacing | the SoC's in hardware mode; `yadif2x` in copy-back or software mode | the SoC's in embed mode; `bwdif` with `vo=gpu` | the SoC's only |
| Dolby Vision | no (profile 5 shows a pink and green tint) | no | yes, where the device supports it |
| HDR10 / HLG | in hardware mode; check on the owner's TV | partial | yes |
| HDR10+ | no | no | yes, where supported |
| Passthrough | AC-3, E-AC-3, DTS, DTS-HD, TrueHD (IEC 61937, with a legacy fallback); restrict per format with `encoded:<mask>` | stock builds pass only AC-3 and core DTS; E-AC-3, DTS-HD and TrueHD need a patched build | what the routed device reports |
| Encode to Dolby Digital | no | yes (`lavcac3enc`) | no |
| DVB subtitles / teletext / CEA-608 | yes / yes / yes | yes / only with libzvbi / yes | yes / no / yes |
| Tunneled playback | no | no | yes (optional) |
| Licence | LGPL-2.1 | wrapper MIT; mpv and FFmpeg GPL or LGPL depending on the build | Apache-2.0 |

Cells marked "check" or "partial" must be confirmed on the owner's TV during M1–M4; the tests are in 6.9.

#### 5.4.2 The stream hub

Plasma opens live HTTP streams itself and hands them to the engine over localhost. This module (`stream/`, 6.2) is the **stream hub**.

- **What goes through it:** live MPEG-TS over HTTP (most IPTV) and udpxy URLs. The hub makes the upstream request with OkHttp: per-source user agent, referrer, headers and cookies; redirects followed; Stalker `create_link` resolved at play time. It serves the bytes on `http://127.0.0.1:<random port>/live/<session token>`, which is the only URL the engine ever sees.
- **What doesn't:** HLS (the engine manages its own segments), raw UDP/RTP multicast, and VOD (which needs byte-range seeking). These go straight to the engine, with headers passed through engine options (5.4.11).
- **Why**
  1. **One connection, several consumers.** One upstream feeds the engine, a recording (5.6) and the pause buffer (5.5). Watching and recording the same channel uses one provider connection, which matters on one- and two-connection subscriptions.
  2. **Switching engines without reconnecting.** On a fallback or Switch Player, the new engine connects to the hub while the upstream stays open. The hub replays its memory buffer from the most recent PAT/PMT, so the new engine starts at once. Providers that ban rapid reconnects never see the switch.
  3. **Clear failure signals.** The hub sees whether bytes arrive. No bytes means a source fault (failover, 5.1). Bytes arriving while the engine shows nothing means an engine fault (fallback, 5.4.3).
  4. **One HTTP behaviour.** User agents, referrers, redirects, tokens, reconnects and the connection budget live in one place instead of in three engines' option dialects.
  5. **Credentials stay out of engine logs.** Xtream URLs carry the username and password, and engines log URLs.
  6. **Fast zap and the pause buffer** build on it (5.4.8, 5.5).
- **Memory:** a ring of the last 8 MB per upstream, about 8 s at 8 Mb/s.
- **Default user agent** for M3U and Xtream through the hub: libVLC's own user agent string, so providers see the same client as before. Stalker uses the MAG headers (7.1). Per-source and per-channel (`#EXTVLCOPT`) values override it.
- **Safety:** bind to 127.0.0.1 only, on a random port, with an unguessable token per session; close the session when it ends (6.10).
- **Kill switch:** Settings › Playback › Advanced › Play Live TV Through Stream Hub (On), with a per-source override. When it's off, engines connect to the provider directly with the per-source headers, and the shared connection, instant engine switch and pause buffer aren't available.
- **Prove it early (M1):** VLC must behave the same on the local stream as on the remote one (start time, timestamps, recovery from discontinuities).

#### 5.4.3 Choosing an engine, and falling back

**Which engine plays a stream** (the first rule that applies wins):
1. The engine the user picked for this channel with Switch Player.
2. The engine that worked for this channel after an automatic fallback.
3. **HDR rule (VOD):** a movie or episode in Dolby Vision or HDR10+ goes to the Android player when "HDR and Dolby Vision Movies" is set to Android player. Use the provider's stream info when it has it (Xtream's `get_vod_info` often includes probe data with codec and HDR fields); otherwise probe the start of the file with a Media3 extractor before choosing.
4. **Sound rule:** with Encode Surround to Dolby Digital on, a stream whose chosen audio track has 3 or more channels that the output can't take as they are goes to mpv (5.4.6).
5. The source's Player Engine override.
6. The global Player Engine (VLC).

**Automatic fallback** ("If a Stream Won't Play", default "Try mpv, then Android player"):
- **Triggers** (an engine fault, while the hub sees data arriving):
  - the engine reports an error (VLC `EncounteredError`, mpv `end-file` with an error, Media3 `onPlayerError` with a decoder or format error);
  - no first video frame (first audio, for radio) within the connection timeout;
  - three stalls within 60 s while the hub's buffer is healthy;
  - the decoder fails to start.
- **Action:** reopen the same stream in the next engine, show the toast (4.35) and an amber dot in the stream info, and remember the engine for this channel once it has played for 30 s.
- **mpv in embed mode can't fall back to software decoding by itself** (6.5). If it fails there, reopen mpv in copy-back mode first, then move on.
- **Not for:** source faults (5.1 handles them), provider refusals such as HTTP 401/403, an expired account or too many connections (show the reason instead), and locked channels.
- **Everything failed:** the error card (4.35).
- Settings › Playback › Advanced › Forget Players Chosen per Channel clears rules 1 and 2.

**Engine lifecycle**
- Start libVLC with the app, because Home shows live video at once. Load libmpv and Media3 only when first needed.
- Keep one main engine alive, plus the Multiview tiles. Release an engine 30 s after it stops being used.
- Both libVLC and libmpv bundle FFmpeg. Load both in one process early in M3 to rule out native symbol clashes.

#### 5.4.4 One video layer

- **One `SurfaceView`** sits at the root of the main activity, behind the Compose UI. Plasma moves and resizes it: full bleed behind Home, 628 × 353 at x 1196 on Live TV, 480 × 270 on the Guide, full screen in the player. Moving it never restarts playback, and screens keep a transparent background where the video shows.
- **Every engine renders into the same surface:**
  - VLC: `IVLCVout.setVideoView()`, `attachViews()`, and `setWindowSize()` on every resize;
  - mpv: `attachSurface()` and the `android-surface-size` option;
  - Media3: `setVideoSurfaceView()`.

  On a switch, the old engine detaches before the new one attaches. The surface itself stays.
- **SurfaceView, not TextureView:** HDR output and Media3 tunneling need it, and it uses less power.
- **Subtitles:**
  - VLC draws them on a second, transparent `SurfaceView` above the video (`setSubtitlesView()`), because in hardware mode it can't blend them into the picture.
  - mpv draws subtitles only with `vo=gpu`, so turning subtitles on with mpv moves it to copy-back mode.
  - Media3 hands out `Cue`s, which Plasma draws in Compose.
- **Picture Size** (Fit, Fill, Zoom, 16:9, 4:3) maps to each engine's scaling: VLC `setVideoScale()` and `setAspectRatio()`, mpv `panscan` and `video-aspect-override`, and Media3 by sizing the surface inside its container. Fit is the default. Engines report the sample aspect ratio, so anamorphic SD shows correctly.

#### 5.4.5 Picture

**Video Decoding**
- **Hardware** (default): MediaCodec renders straight to the surface. It has the lowest CPU use and is required for 4K and HDR. The SoC deinterlaces.
- **Hardware, copy back:** MediaCodec decodes and returns frames to the engine. VLC and mpv can then run their own deinterlacing and draw subtitles into the picture. It costs CPU and memory bandwidth: fine for SD and 1080i, not for 4K.
- **Software:** FFmpeg on the CPU, for codecs or profiles the SoC can't decode (MPEG-2 on some boxes, 10-bit or 4:2:2 H.264). Not for 4K.
- Whatever the setting, VLC drops to software by itself when the hardware decoder refuses a stream.

**Deinterlacing:** Auto (default) · Off · Always.
- **Auto** deinterlaces when the stream is flagged as interlaced, to the field rate: 1080i50 becomes 50 frames per second, which is why the player shows "50 FPS".
- In Hardware mode the SoC does it. In copy-back and software modes, VLC uses `yadif2x` and mpv uses `bwdif`.
- **Always** is for broadcasters that flag interlaced video as progressive.
- If the SoC's deinterlacing looks poor on interlaced sports during testing, set Video Decoding to "Hardware, copy back" for that source.

**Match Frame Rate:** Off; On · seamless only (default); On · always.
- Plasma owns this, not the engines: set Media3's own frame-rate strategy to off.
- When the engine reports the frame rate (doubled for interlaced content shown at field rate):
  - **Android 12+:** `Surface.setFrameRate(rate, FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS or CHANGE_FRAME_RATE_ALWAYS)` on the video surface. Google TV's own "Match content frame rate" setting can still override it.
  - **Android 11:** `Surface.setFrameRate(rate, FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)`.
  - **Older devices, or where the platform won't switch:** choose a `Display.Mode` with the same resolution and a refresh rate equal to the content rate or an integer multiple of it, and set `preferredDisplayModeId` on the window. 23.976 → 23.976 or 47.952, 25 → 50, 29.97 → 59.94, 50 → 50, 59.94 → 59.94.
- **Start delay:** pause for 1–2 s around a mode change so the start isn't lost while the TV resyncs. Some TVs blank for 1–2 s; that's expected.
- Zapping between channels with the same rate changes nothing. When Plasma stops playing or goes to the background, it restores the default mode. Home keeps the mode while live video plays behind it.

**HDR**
- Live 4K channels in HDR10 or HLG stay on VLC with hardware decoding. Check the colours on the owner's TV; if they're wrong, set that source or channel to the Android player.
- Dolby Vision and HDR10+ movies go to the Android player, because libVLC 3.x has no Dolby Vision support (5.4.1).
- Plasma doesn't switch the TV's HDR mode itself. The platform does that when the decoder outputs HDR.

#### 5.4.6 Sound

**Detecting the sound system** (the live card on the Audio screen, 4.28)
- **Formats for the current route:**
  - Android 13+: `AudioManager.getDirectProfilesForAttributes()`.
  - Older: `AudioDeviceInfo.getEncodings()` plus `AudioTrack.isDirectPlaybackSupported()` (API 29+). Before Android 13 that check considers every output, not just the routed one, so confirm by opening the track.
- **Changes during playback:** a receiver waking up, eARC renegotiating or Bluetooth connecting all change the answer. Listen with `AudioDeviceCallback` and `AudioTrack.addOnRoutingChangedListener()` (the `ACTION_HDMI_AUDIO_PLUG` broadcast on old devices), re-evaluate, and reconfigure the engine. Catch write errors from a format that is no longer supported, as Android's TV audio guidance describes.
- **Name the route** for the card: HDMI, HDMI ARC, HDMI eARC, optical, Bluetooth, USB or TV speakers, plus the product name when the system gives one ("AV receiver").

**Audio Output:** Auto (default) · Passthrough · Decode to PCM. Each source and channel can override it.
- **Auto:** pass a format through when it's ticked under Passthrough Formats and the output reports it. Otherwise decode to PCM: multichannel when the output takes it, stereo when it doesn't.
- **Passthrough:** send the ticked formats even when the system doesn't report them, which fixes setups that misreport behind ARC. If the receiver can't decode a format, the result is silence, so the detail text warns about that.
- **Decode to PCM:** always decode. Use it for the TV's own speakers, for Night Mode, or when passthrough misbehaves.
- **Passthrough Formats:** Dolby Digital, Dolby Digital Plus, DTS, DTS-HD MA (including DTS:X) and Dolby TrueHD. Atmos travels inside Dolby Digital Plus and TrueHD. The default is every format the output reports. Users untick a format their system reports but can't actually play; some receivers claim DTS-HD and then stay silent.
- **Engine support for passthrough:**
  - VLC passes all five, through IEC 61937 with a legacy fallback. Plasma restricts the formats with the audio output device `encoded:<mask>` (one bit per `AudioFormat.ENCODING_*` value), and uses `pcm` or `stereo` to decode.
  - mpv's stock output passes only AC-3 and core DTS. E-AC-3, DTS-HD and TrueHD fall back to PCM unless the build carries the fix from the BuzzTV fork (September 2026).
  - Media3 passes what the routed device reports. Plasma filters the audio capabilities it uses so that unticked formats are decoded.

**Encode Surround to Dolby Digital** (Off by default)
- For sound systems that take Dolby Digital but not the stream's format: optical-only soundbars, older ARC, and AAC 5.1 or DD+ broadcasts.
- When it's on, and the chosen audio track has 3 or more channels that can't pass through as they are, Plasma plays the stream with mpv using `af=lavcac3enc` (640 kb/s, S/PDIF output) and `audio-spdif=ac3`. Stereo stays PCM, and other streams keep their engine.
- The libmpv build must include FFmpeg's AC-3 encoder. Check this when choosing the AAR (6.1).

**Stereo Downmix** (Off): force 2-channel PCM for TV speakers, headphones, or an amplifier that mishandles multichannel PCM.

**Night Mode** (Off): compress the gap between loud and quiet passages. It works on decoded audio only; a receiver has its own night mode.
- Prefer Android's `DynamicsProcessing` effect (API 28+) on Plasma's own audio session, so one implementation covers every engine. Plasma creates the session id and gives it to the engine: VLC `--audiotrack-session-id` and Media3 `setAudioSessionId()`; check whether the libmpv build accepts one.
- Check that the TV's audio HAL supports the effect. If not, fall back to engine filters: VLC `compressor`, mpv `acompressor` or `dynaudnorm`, or a Media3 `AudioProcessor`.

**Audio Delay** (0 ms): one value for the TV plus an optional value per channel, added together.
- VLC: `setAudioDelay()` (in microseconds). mpv: `audio-delay`.
- Media3 has no simple control for this. While the Android player is in use, hide the per-channel delay.

**Channel Volume Levels** (0 dB for every channel by default)
- **What it's for:** channels that are much louder or quieter than the rest. The user sets a level while watching, from Channel options › Volume Level (the bar in 4.35): −6 to +6 dB in 1 dB steps. The TV's own volume still sets the overall level. Settings › Audio lists the channels that have one (4.28).
- **Stored** per channel, not per profile, as `ChannelPlayback.volumeOffsetDb` (6.3). It applies on every tune, in Multiview to the tile with sound, and in recordings played back in Plasma.
- **Decoded audio only.** Plasma can't change the level of a bitstream that passes through to a receiver. When the current stream passes through, the bar says "Your receiver sets the volume while audio passes through" and offers **Decode This Channel**, which sets that channel's Audio Output to Decode to PCM (it loses Atmos on that channel, and the detail text says so).
- **How:**
  - **First choice:** the input gain of the `DynamicsProcessing` effect on Plasma's audio session (in dB), the same effect Night Mode uses, with its limiter on so a boost never clips. One implementation then covers every engine that takes Plasma's session id.
  - **Fallback** (the TV has no such effect, or the engine can't take the session): the engine's own volume. VLC `setVolume()` (100 is unchanged; values up to 200 boost) and mpv's `volume` property (100 is unchanged; boosting needs `volume-max` above 100). Media3's `setVolume()` only cuts, so boost it with `LoudnessEnhancer` on the session (target gain in millibels).
  - The engines' volume scales are not in dB and differ from each other. Measure each one once in M3 with a test tone, and keep a small conversion table, rather than assuming a curve.
- **Changing channel** applies the new level before the first audio frame, so there's no jump in loudness.

**Languages**
- **Preferred Language** is an ordered list per profile (5.11), such as "English, then original"; "original" means the stream's default track.
- **Skip Audio Description** avoids tracks flagged as audio description (the DVB audio type for visually impaired commentary, HLS and DASH accessibility roles, or names such as "AD" and "Audio Description"), unless it's the only track.
- **Per-channel memory:** when the user picks a different audio or subtitle track on a channel, Plasma remembers it for that channel and profile, and uses it before the language rules next time.

#### 5.4.7 Subtitles and teletext

- **Settings:** Off (default) · On (the profile's subtitle language) · Forced only, plus Style: size, colour, and a background box. The default style is white text with a soft shadow and no box.
- **Sources:** DVB bitmap subtitles, teletext subtitles (for example page 888), CEA-608/708 captions inside the video, WebVTT in HLS, and SRT, ASS and PGS in VOD files.
- **Engine support:**
  - VLC decodes all of them; teletext uses its `telx` or `zvbi` decoder, so check which one the AAR includes.
  - mpv handles DVB, CEA-608, text formats and PGS, and teletext only if FFmpeg was built with libzvbi.
  - Media3 handles DVB, CEA-608/708, WebVTT, SubRip, SSA and PGS, but not teletext.
- With teletext subtitles on, prefer VLC for that channel.
- **v1.x:** full teletext pages (the MAG "TXT" key).

#### 5.4.8 Streaming

**Buffer Size:** Minimal 0.5 s · Small 1.5 s · **Medium 3 s** (default) · Large 6 s · Extra Large 12 s. Each source can override it.
- For live TV the buffer is also a delay. Each second of buffer adds a second to channel changes and puts the picture a second behind live, unless the server or the hub already has data in hand.
- Medium rides out most provider hiccups. Small suits people who zap a lot on a good line.
- Engine mapping is in 5.4.11.

**Connection Timeout** (15 s; 5–30 s): how long to wait for the first data before treating the source as failed.

**Reconnect:** when a live stream drops or ends unexpectedly, reconnect at once, then after 1, 2 and 4 s. Keep the last frame on screen with "Reconnecting…" for up to 10 s before showing an error. The hub does this for TS; engine options cover HLS.

**Headers:** per-source User Agent, Referrer and Extra Headers. `#EXTVLCOPT:http-user-agent` and `http-referrer` lines in an M3U override them per channel (7.3).

**Xtream Stream Format:** TS (default) or HLS (`.m3u8`), offered when the account's `allowed_output_formats` includes it. Some providers serve HLS more reliably at busy times.

**Fast Zap** (Off): when the connection budget has room, the hub keeps the next channel in the direction of the last zap open, with about 4 s in memory, so zapping to it starts at once.

**Multicast:** M3U entries with `udp://` or `rtp://` URLs, typically an ISP's own IPTV on the home network.
- VLC and mpv play them directly. Hold a `WifiManager.MulticastLock` while playing over Wi-Fi.
- Multicast over Wi-Fi is often unreliable, so each source can set a **udpxy proxy**. It rewrites `udp://@239.1.1.1:1234` to `http://<udpxy host:port>/udp/239.1.1.1:1234`, which then goes through the hub like any other stream.
- Media3 can't play multicast, so the fallback order skips it for these URLs.

**Zapping**
- Show the zap banner straight away. The hub opens the new channel, and the engine plays the new local URL.
- **Target:** first frame within 1 s at the Small buffer. At larger buffers the target is 1 s plus the part of the buffer that the server doesn't send at once. Track the 95th percentile.

**Tracks:** the audio and subtitle choice follows the profile's preferred languages (5.4.6), and the overlay shows the language and channel layout ("English 5.1").

#### 5.4.9 Errors and stream info

- **Classify every failure:**
  - **Source:** no connection, HTTP 4xx/5xx, HTML instead of video, or no data. Retry with backoff at 1, 2 and 4 s, then fail over (5.1).
  - **Engine:** data arrives but nothing plays, the decoder errors, or playback keeps stalling. Fall back to the next engine (5.4.3).
  - **Provider refusal:** 401/403, an expired account, or too many connections. Show the reason and the fix, with no fallback.
- **Final error card:** Try Again · Switch Source · Switch Player · Report. Report exports a diagnostics bundle (5.17).
- **Stream info** appears in the Player Options footer (4.10) and in a debug overlay (Settings › Playback › Advanced › Show Stream Stats). It shows:
  - the engine and its version, and the decoder name and mode (for example `c2.amlogic.avc.decoder`, hardware);
  - resolution, frame rate, interlacing, HDR format and display mode;
  - the audio codec, channels and route (PCM 5.1, passthrough E-AC-3, or encoded to AC-3);
  - bitrate, buffer and dropped frames;
  - the source and candidate, the upstream host (redacted), and the reconnect count.

#### 5.4.10 External players (v1.x)

- **Setting:** Settings › Playback › Open Movies and Series In: Plasma (default) · Ask Each Time · an installed player (VLC, MX Player, Kodi, Just Player or mpv).
- **How:** send `ACTION_VIEW` with `setDataAndType(url, "video/*")` and the title and resume position as extras. Where the app returns a position (VLC and MX Player do), read it back and save it as the resume point. Check each app's intent extras.
- External players get the provider URL directly: they can't use the hub, and some can't send custom headers.
- Live channels always play in Plasma.

#### 5.4.11 Engine option cheat sheet

Option names come from current documentation and source, but they drift between releases. Check each one against the versions you ship.

| Plasma setting | VLC (libVLC 3.7 for Android) | mpv (libmpv) | Android player (Media3 1.10) |
|---|---|---|---|
| Buffer Size | media option `:network-caching=<ms>` | `cache=yes`, `cache-pause-initial=yes`, `cache-pause-wait=<s>`, `demuxer-readahead-secs` | `DefaultLoadControl` buffer durations (`bufferForPlaybackMs` = buffer size); for HLS, the media item's live configuration |
| Video Decoding: Hardware | `Media.setHWDecoderEnabled(true, …)` (direct rendering) | `hwdec=mediacodec`, `vo=mediacodec_embed`, `vd-lavc-software-fallback=no` | default MediaCodec renderers |
| Hardware, copy back | as Hardware, plus `:no-mediacodec-dr` (as VLC for Android does) | `hwdec=mediacodec-copy`, `vo=gpu` | same as Hardware |
| Software | `Media.setHWDecoderEnabled(false, false)` | `hwdec=no`, `vo=gpu` | extension renderers preferred, where present |
| Deinterlacing | `:deinterlace=-1/0/1` (auto/off/on), `:deinterlace-mode=yadif2x` (not in direct rendering) | `deinterlace` (with `vo=gpu`) | not available (the SoC handles it) |
| Match Frame Rate | Plasma, from `VideoTrack.frameRateNum/Den` | Plasma, from the `container-fps` property | Plasma, from `Format.frameRate`; Media3's strategy off |
| Audio Output and Passthrough Formats | `setAudioOutputDevice("encoded:<mask>")`, `"pcm"` or `"stereo"`; `setAudioDigitalOutputEnabled()` enables every detected format | `audio-spdif=ac3,eac3,dts,dts-hd,truehd` (the ticked subset) | filtered `AudioCapabilities` for the audio sink |
| Encode to Dolby Digital | — | `af=lavcac3enc=tospdif=yes:bitrate=640:minch=3` with `audio-spdif=ac3` | — |
| Stereo Downmix | audio device `"stereo"` | `audio-channels=stereo` | `ChannelMixingAudioProcessor` in the audio sink |
| Audio Delay | `setAudioDelay(µs)` | `audio-delay=<s>` | — |
| Languages | Plasma selects with `setAudioTrack()` and `setSpuTrack()` | `alang`, `slang`, or `aid`/`sid` | `TrackSelectionParameters` preferred languages and role flags |
| Headers (direct, not through the hub) | `:http-user-agent=`, `:http-referrer=` | `user-agent`, `referrer`, `http-header-fields` | `OkHttpDataSource.Factory` user agent and request properties |
| Reconnect (direct) | `:http-reconnect` | `stream-lavf-o=reconnect=1,reconnect_streamed=1` | `LoadErrorHandlingPolicy` with more retries |
| Audio session for effects | `--audiotrack-session-id` | check the build | `setAudioSessionId()` |
| Stats | `Media.getStats()` | `video-bitrate`, `demuxer-cache-duration`, `frame-drop-count`, `hwdec-current` | `AnalyticsListener`, `DecoderCounters` |

#### 5.4.12 Licences

Not legal advice, just the facts that matter for the decision in 9.2.
- **libVLC** is published under the LGPL-2.1 (as listed on Maven Central). For a sideloaded app: keep the `.so` files unmodified and replaceable (they are, inside an APK), show the licence under Settings › About › Open-Source Licences, and link to the matching source.
- **libmpv AARs:** the Kotlin/Java wrappers are MIT, but the mpv and FFmpeg inside may be built as GPL. If Plasma is ever shared, either use an LGPL build (mpv with `-Dgpl=false`, and FFmpeg without `--enable-gpl`) or release Plasma under the GPL. For the owner's own sideload, this doesn't bite.
- **Media3** is Apache-2.0.

### 5.5 Timeshift, Start Over and catch-up

- **Provider archive is the preferred method.** Stalker's `tv_archive`, Xtream timeshift URLs and M3U `catchup` templates (section 7) power:
  - **Start Over**;
  - catch-up from the Guide and from the Catch-up screen;
  - seeking back in live TV as far as the programme's start.
- **What plays when the user pauses or rewinds:** the local rewind buffer for anything since the user tuned in (or since the pause), and the provider's archive for anything earlier. Start Over always uses the archive. Some archives lag a few minutes behind live, which is another reason to use the local buffer for the recent part.
- **Pause with an archive and no local buffer** (Rewind Buffer set to Off): pausing records the wall-clock time. Resuming plays the archive URL from that moment, so the overlay shows "−2:14 behind live". **Go Live** returns to the live edge.

**The rewind buffer** (Settings › Playback › Rewind and Local Catch-up, 4.31). It lives in the stream hub, so it works the same with every engine.
- **Modes:** Off · **When I pause** (default) · Always. **Length:** 30 min, **1 hour** (default), 2 hours or 4 hours, capped by the storage limit.
  - **When I pause** writes to storage only after a pause, so watching live TV doesn't wear the TV's flash memory. Rewinding before the pause uses the 8 MB memory buffer, then the archive.
  - **Always** writes from the moment a channel is tuned, so the user can rewind to when they tuned in on any channel. It writes all the time (about 3.6 GB an hour at 8 Mb/s), so the options sheet recommends it with a USB drive.
1. The hub always keeps the last 8 MB of the live stream in memory (5.4.2).
2. **Pause:** the engine pauses and Plasma tells the hub. The hub stops feeding that engine but keeps reading the same upstream connection and writes the ring of TS segments, starting with its memory buffer, so nothing is lost. In Always mode, the ring is already being written.
3. **Play:** the engine resumes and the hub feeds it from the ring, from exactly the byte where it stopped. There's no reopen and no rebuffer.
4. **Seeking** (−10 s, +10 s, scrubbing the timeline): segments are about 2 s long, cut at a keyframe where possible, and the hub records each one's wall-clock time and first timestamp. It serves the ring as a local HLS playlist, `#EXT-X-PLAYLIST-TYPE:EVENT` while the ring grows, then a sliding window once it's full. Plasma reopens the engine on that playlist at the target time.
5. **Go Live** reopens the engine on the live local URL. The ring stays until the channel changes.
6. **Changing channel** ends the buffer and frees its space.
7. **Engine settings for local URLs:** read timeouts must be longer than the longest pause. VLC's own timeshift stays idle because the hub stops feeding it, but point `input-timeshift-path` at the app's cache directory anyway, because Android has no `/tmp`.
8. HLS sources don't go through the hub. Pausing them uses the playlist's own window where the provider offers one, otherwise the archive.

**Engine-native alternatives** (not used, noted for comparison): VLC's timeshift (`input-timeshift-path` and `input-timeshift-granularity`) keeps reading a live stream into temporary files while paused, and mpv's demuxer cache with a back buffer (`demuxer-max-back-bytes`, `cache-on-disk`) allows seeking back. Both belong to one engine and are lost on an engine switch, which is why Plasma's buffer lives in the hub.

**Local catch-up channels** (same page). For channels without an archive, Plasma can record chosen channels all the time into a rolling window that plays like catch-up.
- **What the user gets:** past programmes on those channels inside the window show ↺ in the Guide, appear in the Catch-up screen with "Local · 24 h" as their depth, and come up in Search's catch-up results. OK plays a programme from its start, cut by the guide's start and end times. Menu offers **Keep as Recording**, which copies that range into a normal recording (5.6).
- **How:** the same segment writer and keyframe index as the rewind buffer, with one ring per channel on disk. The hub opens and holds the channel's upstream; if the user watches that channel, the viewing shares the connection.
- **Keep:** 6, 12, **24** (default), 48 hours, or 7 days. **Storage Limit:** default 50 GB on a USB drive, 8 GB on the TV. When the limit is reached, the oldest local catch-up is deleted first; recordings are never deleted to make room.
- **Connections:** each local catch-up channel holds one provider connection all the time. The picker shows the cost before ticking a channel, and refuses one that would leave no connection to watch with. The connection budget (5.1) counts these first.
- **While the TV Is Off:** Keep recording (default) · Pause until the TV is on.
  - Recording in standby needs the TV to stay online. On Android 14+ for TV, the energy mode decides this: **Low energy** cuts the network in standby, while the increased-energy mode keeps it on. The detail text tells the user where to change it on their TV.
  - Run local catch-up in the same `specialUse` foreground service as recordings, with a partial wake lock.
  - If the network drops anyway, the index records a gap. Playback skips it with a toast: "Skipped 12 min that weren't recorded."
- **Clear Local Catch-up** deletes all of it after a confirmation.
- **After a reboot,** local catch-up starts again on its own once the network is up (boot receiver, 6.8).

**For every kind of catch-up**
- **Timeline:** runs from the programme's start to its end, with a live-edge marker, the buffered region (the archive or the ring), and a bubble with the clock time and offset.
- **End of a catch-up programme:** an "Up Next" card offers to continue with the following programme in the archive, or to Go Live.
- **Chase play** of a recording in progress uses the same local-playlist mechanism (5.6).

### 5.6 Recording

- **Types:**
  - **Record once** (one programme);
  - **Record series** (a rule that matches the title or series id, on one channel or any channel);
  - **manual** (channel plus start and end time).
- **Scheduling:**
  - Use exact alarms (`setExactAndAllowWhileIdle`). Because Plasma is sideloaded, declare `USE_EXACT_ALARM` (Android 13+, granted at install; Play would restrict it, but that doesn't apply here) plus `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"` for Android 12.
  - As a safety net, fall back to an inexact window alarm that starts 1 minute early.
  - At start time, a foreground service asks the stream hub (5.4.2) for the channel and writes it to a file. If the channel is already open because someone is watching it, the recording shares that connection.
- **Foreground-service type: decided, `specialUse`.**
  - Declare `FOREGROUND_SERVICE_SPECIAL_USE` and the `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property ("Records scheduled TV programmes and local catch-up to storage"). Local catch-up (5.5) runs in the same service.
  - Don't use `dataSync` or `mediaProcessing`. On apps targeting Android 15+ those types are capped at 6 hours per 24 h, and a long recording would be killed at the cap.
  - `specialUse` needs a justification only for Google Play review, which doesn't apply to a sideloaded build.
- **Files:**
  - Write the MPEG-TS as received. For HLS, download the segments and append them.
  - Store recordings in app-specific storage on the chosen volume. Find USB drives through `StorageManager` volumes, or let the user pick a folder through the Storage Access Framework.
  - Name files "Channel – Title – 2026-10-21 1945.ts", each with a sidecar JSON holding its metadata.
  - While recording, also write a small index: the byte offset and time of a keyframe about every 2 s. The hub turns it into a local HLS playlist with `#EXT-X-BYTERANGE`, which gives chase play and fast seeking in every engine.

**Where recordings go** (Settings › Recording, 4.34)
- **Record To:** USB drive (the default when one is connected) · This TV · Network folder.
- **Copy to Network Folder** (Off until set up): when a recording finishes, copy it to an SMB share so other devices and Jellyfin can use it.
  - **Library:** smbj (Apache-2.0, 6.1), which speaks SMB 2 and 3, including signing and encryption. SMB 1 isn't offered.
  - **Finding the share:** NAS boxes that advertise `_smb._tcp` over mDNS (most Synology, QNAP, TrueNAS and Samba setups) are listed through `NsdManager`. Windows PCs don't advertise that way, so Enter Address is always there (`smb://nas.home.lan/Media` or `\\nas\Media`).
  - **Saving the share:** sign in, pick a folder, then write and delete a test file before saving, so a read-only share fails at setup rather than at 3 a.m. Show its free space ("3.1 TB free").
  - **The copy:** a background job runs after the recording's index is final. It writes `Name.ts.partial`, renames it when complete so Jellyfin never scans half a file, then writes the info file and artwork and compares sizes. Recordings shows "Copying…" and then "On nas.home.lan".
  - **Share offline:** recordings wait on local storage, the job retries with backoff, and a toast says so once (4.35). Recordings shows "Waiting to copy".
- **After Copying:** Keep on the drive for 7 days (default) · Keep until space is needed · Delete from the drive. A recording whose local file is gone still plays in Plasma: the hub reads it from the share with smbj and serves it on localhost with byte ranges, so every engine can seek.
- **Plasma never deletes files on the share by itself.** Retention rules and When Space Runs Low only touch local files. Deleting a recording by hand asks "Delete from this TV" or "Delete everywhere, including nas.home.lan".
- **Record To: Network folder** writes straight to the share and keeps a local spool of the last minutes. If the share drops during a recording, the rest goes to local storage and is appended to the file on the share when it's back.

**Name Files for Jellyfin** (On by default with a network folder; applies to the share, not to Plasma's own local files)
- **Layout**, following Jellyfin's naming guides (appendix D):

  | Kind | When | Path |
  |---|---|---|
  | TV | a Record Series rule, or the guide gives season and episode | `TV Recordings/Kingdoms of Ice/Season 01/Kingdoms of Ice S01E03.ts` |
  | TV, no episode numbers | a series without numbers in the guide | `TV Recordings/Late Night Sessions/Season 2026/Late Night Sessions S2026E001.ts`: the season is the air year and the episode counts up per series within the year |
  | Movie | the guide's category says film or movie | `Movie Recordings/The Last Lighthouse (2025)/The Last Lighthouse (2025).ts`, with the year from the guide's `<date>` when there is one |
  | Other | sport, news and one-offs | `Other Recordings/Arena Sports 1/2026-10-21 1945 Harbour City v Redmoor.ts` |

- **Episode numbers** come from XMLTV `<episode-num>`: `xmltv_ns` is zero-based (`0.2.` is S01E03), and `onscreen` is parsed when it reads like "S01E03" or "S1 E3". Stalker and Xtream guides rarely have them, hence the year rule.
- **Info files**, which Jellyfin reads when they're present: `tvshow.nfo` in the series folder (title, plot); `Kingdoms of Ice S01E03.nfo` next to the episode (title, season, episode, aired, plot, channel as studio); `movie.nfo` in the movie folder (title, year, plot, genre). Characters that are illegal on Windows shares (`\ / : * ? " < > |`) become a space or a dash.
- **Artwork:** the guide's programme image as `Kingdoms of Ice S01E03-thumb.jpg`, the series' or movie's image as `poster.jpg` when there is one, otherwise a frame from the recording 5 minutes in.
- **In Jellyfin,** the user adds TV Recordings as a Shows library and Movie Recordings as a Movies library (the detail text says so). Other Recordings suits a Home Videos and Photos library or can be left out.
- **Telling Jellyfin:** with a Jellyfin server signed in on any profile (5.19), Plasma asks it to scan the library that holds the recordings, at most once every 10 minutes, through the SDK's library refresh call. That needs a Jellyfin administrator account. With any other account, Jellyfin finds the files on its next scheduled scan or through real-time monitoring, and the detail text says so.

**Timing, conflicts and clean-up**
- **Padding:** 1 min before and 5 min after by default, with an extra +15 min for sports. These are settings.
- **Conflicts:**
  - Compare concurrent recordings and viewing against the connection budget.
  - Show the result in the Coming Up header, and resolve conflicts in a sheet.
- **Retention:**
  - Per rule: "keep last N".
  - Global: "delete watched recordings after N days".
  - **Keep** (green) protects a recording.
  - Show an amber warning two days before deletion.
- **Storage card:** free and total space, plus estimated hours. Estimate from bitrate history (HD is about 2.2 GB per hour).
- **Playback:** finished recordings play from the file in the default engine. A recording still in progress plays through the hub's growing playlist (chase play), with the live edge available.
- **Keep as Recording** (from local catch-up, 5.5) copies the programme's range out of the ring into a normal recording file with its own index, so retention rules apply to it like any other recording.
- **v1.x:** "Save from catch-up", which downloads an archived programme after it has aired.

### 5.7 Reminders

- An `AlarmManager` alarm fires one minute before the start (configurable).
- Inside Plasma, the toast from 4.11 appears.
- Outside Plasma, an overlay appears only if Plasma has "Display over other apps" (`SYSTEM_ALERT_WINDOW`); otherwise the reminder is skipped. TVs don't show phone-style notifications reliably.
- Many TVs hide that setting. Grant it with `adb shell appops set tv.plasma.launcher SYSTEM_ALERT_WINDOW allow`, or let Plasma do it during the on-device ADB setup (5.13).
- **Auto-switch at start time** is optional and off by default.

### 5.8 Multiview

See 4.12. In addition:
- **Layouts:** 2×2, 1 + 3, and picture in picture.
- **Audio** follows the focused tile.
- **Capacity:**
  - Probe the hardware decoders for how many instances they support at the content's resolution (`MediaCodecInfo.CodecCapabilities.getMaxSupportedInstances()` is a hint, not a promise; confirm by starting the decoders).
  - Cap the tile count accordingly, and allow only one 4K tile.
- **Engines:** every tile is a VLC player in Hardware mode, with decoded PCM sound (4.12). A tile whose stream VLC can't play shows the error card in the tile rather than falling back.
- **Every tile counts against the connection budget.** The hub keeps a tile's upstream open when it goes full screen, so that switch is instant.

### 5.9 VOD (Movies and Series)

- **Data:**
  - Xtream: `get_vod_categories`, `get_vod_streams`, `get_vod_info`, `get_series_categories`, `get_series`, `get_series_info` (seasons, then episodes).
  - Stalker: the `vod` module (categories, ordered list, `create_link`). Series support varies by portal.
- **Metadata:** comes from the provider (plot, cast, director, rating, year, duration, backdrop). Without a backdrop, use a blurred, enlarged poster.
- **Resume points:**
  - Kept per profile.
  - Saved every 10 s and on stop.
  - An item counts as **watched** at 92 % or more.
- **Continue Watching:** the 20 most recent unfinished items, mixing VOD, episodes, catch-up and recordings.
- **My List:** kept per profile.
- **Trailers:** use the provider's YouTube trailer id with a YouTube intent if the app is installed; otherwise hide the button.
- **Next episode:** plays automatically after a 10 s countdown card (a setting).

### 5.10 Search and voice

- **Index:** Room FTS4 over channel names, programme titles and subtitles, movies and series.
- **Behaviour:** debounced queries, the ranking from 4.3, and the last 10 searches kept per profile.
- **Voice:** `SpeechRecognizer`, or the `RecognizerIntent` UI, which needs `RECORD_AUDIO`.
- **v1.x:** integrate with system-wide search.

### 5.11 Profiles

- **Up to 6 profiles**, of three kinds:
  - **Standard.**
  - **Kids:** a rating cap, kid-safe groups only, and no access to Settings.
  - **Guest:** keeps no history, and can't manage recordings.
- **Each profile has its own:**
  - favourites, recents, resume points and My List;
  - reminders;
  - audio and subtitle preferences;
  - avatar;
  - optional PIN.
- **The picker shows:**
  - on wake, if Ask Who's Watching is on;
  - from the avatar in the top bar.

### 5.12 Parental controls

- **PIN:** 4 digits, stored as a salted hash.
- **What can be locked:**
  - channels and groups;
  - a rating threshold, from XMLTV `<rating>` or provider data;
  - adult keywords;
  - access to Settings.
- **Kids profiles:** locked content never appears, including recordings.
- **Unlock durations:** this programme, 1 hour, or until the TV sleeps.
- **Locked channels never preview.** Show the lock card instead, so no content flashes on screen.
- **Rate limit:** 5 wrong attempts block entry for 1 minute.

### 5.13 Launcher behaviour (sideloaded build)

Plasma is a sideloaded APK, so Google Play's rules don't apply. Home Screen setup (4.23) offers three methods and detects which ones work on the current TV. `tv.plasma.launcher` below is a placeholder for the final application id (section 9.2).

**Manifest**
- The Home activity declares both:
  - `MAIN` + `LEANBACK_LAUNCHER` (and `LAUNCHER`), and
  - `MAIN` + `HOME` + `DEFAULT`.
- It uses `launchMode="singleTask"` and a TV banner.

**Method 1: Home Button Override (accessibility service). The main method on Google TV.**

The owner uses this today with Projectivy, and it works on their TV.

- **How it works:**
  - An `AccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED`.
  - When the stock launcher comes to the front (Home press, wake or boot), it immediately starts Plasma's Home activity, or whatever target the Home Button setting chooses.
- **Keep the service minimal:**
  - It only needs the package name of the window in front: set `canRetrieveWindowContent="false"`.
  - Limit `packageNames` to the launcher packages, which are detected at runtime by resolving the HOME intent and updated with `setServiceInfo`.
- **Known limits:**
  - The stock launcher can flash briefly before Plasma appears. This is inherent to the method; Method 2 avoids it.
  - A Google TV update in March 2026 was reported to break Projectivy's override for some users, though it still works on the owner's TV. Keep Method 2 as the fallback.
- **Restricted setting (Android 13+).** When an APK is installed through a file manager or browser (a non-session install), Android greys out the accessibility toggle as a "Restricted setting". App stores that use session-based installs aren't affected. Whether `adb install` triggers it isn't documented, so test on the target TV. Fixes:
  - App info › ⋮ › **Allow restricted settings**, if the TV's Settings show it;
  - over ADB: `adb shell appops set tv.plasma.launcher ACCESS_RESTRICTED_SETTINGS allow`;
  - or turn the service on directly over ADB:
    ```
    adb shell settings get secure enabled_accessibility_services
    # append Plasma with ":" so other services stay on (this command replaces the whole list)
    adb shell settings put secure enabled_accessibility_services <existing>:tv.plasma.launcher/.home.HomeButtonService
    adb shell settings put secure accessibility_enabled 1
    ```
- **Conflicts:** only one app should answer the Home button. Detect other enabled launcher overrides (Projectivy, Button Mapper and similar) and ask the user to turn them off.
- **Disclosure:** even without Play rules, show a one-screen explanation of what the service watches (only which app is in front) and what it never reads.

**Method 2: Replace Google TV Home (ADB). Advanced, and required as an option.**

- **What it does:** makes Plasma the system Home app and turns off the stock launcher, so Home, wake and power-on open Plasma directly, with no flash.
- **From a computer:**
  ```
  # Android 11+ (Wireless debugging): pair once, then connect. Code and ports are on the TV.
  adb pair <tv-ip>:<pairing-port> <code>
  adb connect <tv-ip>:<port>
  # older TVs with network debugging: adb connect <tv-ip>:5555

  # 1. make Plasma the Home app (same as: cmd role add-role-holder android.app.role.HOME tv.plasma.launcher)
  adb shell cmd package set-home-activity tv.plasma.launcher/.HomeActivity
  # 2. if Google TV Home still opens, turn it and its fallback off
  adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
  adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
  # check which activity now answers Home
  adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

  # undo
  adb shell pm enable com.google.android.apps.tv.launcherx
  adb shell pm enable com.google.android.tungsten.setupwraith
  ```
  - `setupwraith` is Google TV's fallback, which turns the launcher back on if it's disabled on its own.
  - Older Android TV uses `com.google.android.tvlauncher`, and TV makers may ship their own launchers. Don't hard-code package names: list the HOME-capable activities on the device, and show the exact commands for *this* TV in Help.
- **From the TV ("Plasma does it for you", 4.24):**
  - **Android 11+ wireless debugging requires pairing.** The pairing dialog in Settings must stay open while pairing, so the user types the code and pairing port on their phone. Plasma's local server (5.15) receives them.
  - **Wireless debugging needs Wi-Fi.** On Ethernet it may not be offered; this has been reported on the Google TV Streamer. If so, switch the TV to Wi-Fi for setup (and again for a later Restore), or use a computer over Wi-Fi.
  - **Pairing and connecting:** Plasma pairs with `127.0.0.1:<pairing port>`, discovers the connection port with `NsdManager` (`_adb-tls-connect._tcp`), then connects. The connection port changes every time wireless debugging is turned on, but the pairing is remembered, so later reconnects need no new code.
  - **Older TVs with fixed-port network debugging:** connect to `127.0.0.1:5555` and accept the TV's "Allow debugging?" prompt (Always allow).
  - **Library:** `libadb-android` supports pairing, TLS and shell streams. It is dual-licensed GPL-3.0-or-later or Apache-2.0 and has an LGPL dependency, so check the licence fits. `dadb` covers plain TCP connections without pairing.
  - **Then run** the same commands as above, and confirm with `resolve-activity`.
- **Optional extra grants while connected** (each explained, each skippable):
  - `pm grant tv.plasma.launcher android.permission.WRITE_SECURE_SETTINGS`: lets Plasma set its own screensaver, turn its accessibility service on, and switch wireless debugging back on later (`Settings.Global adb_wifi_enabled`) without the user.
  - `appops set tv.plasma.launcher SYSTEM_ALERT_WINDOW allow`: reminders over other apps, and starting at power-on.
  - `appops set tv.plasma.launcher ACCESS_RESTRICTED_SETTINGS allow`.
  - `pm grant tv.plasma.launcher android.permission.READ_TV_LISTINGS`: reads other apps' Play Next items for Continue Watching (5.19), if the user turned that on.
- **Safety rules:**
  - Never disable the stock launcher unless Plasma's Home activity resolves and starts correctly.
  - Show the undo before running anything.
  - After every boot, check whether Home still resolves to Plasma. If a system update turned the stock launcher back on, offer to apply Method 2 again or fall back to Method 1.
  - **Manual recovery:** Settings › Apps › See all apps › show system apps › Google TV Home › Enable usually works. A factory reset is the last resort.
- **Side effects to warn about:** on some Google TV sets the inputs menu, profile switching or picture shortcuts live in the stock launcher. Plasma's Inputs tile (`TvInputManager`) covers input switching, and Google Assistant keeps working.

**Method 3: Default Home App (TVs that allow it).**
- On Android 10+, request `RoleManager.ROLE_HOME` where it's offered.
- Otherwise open `Settings.ACTION_HOME_SETTINGS` if something resolves it.
- Otherwise ask the user to press Home and pick Plasma, then Always. Many Android TV boxes offer this chooser.

**Fire TV:** Plasma runs as an app. Replacing Home there isn't a goal.

**Power-on**
- With Method 2 or 3, the system starts Plasma, and "When the TV Turns On" picks the first screen.
- With Method 1, the override catches the stock launcher appearing after boot.
- Starting at boot from a `RECEIVE_BOOT_COMPLETED` receiver also works once `SYSTEM_ALERT_WINDOW` is allowed.

**Apps**
- Use `<queries>` intents. `QUERY_ALL_PACKAGES` is acceptable in a sideloaded build if it turns out to be simpler.
- Listen for package added, removed and changed events, and save the order and hidden state.
- **Show Android Apps = Off** hides the Apps row and the Apps tab.

**System tiles**
- **Inputs:** `TvInputManager` lists the TV inputs; launch an HDMI passthrough. Device-dependent.
- **Device Settings:** `Settings.ACTION_SETTINGS`.
- **Get More Apps:** open the Play Store if it's installed.

**Long-press Home:** usually handled by the system. Offer the setting only where Plasma actually receives the key; elsewhere show "System default".

### 5.14 Screensaver

- **Implementation:** a `DreamService`, configured in Settings › Launcher & Home › Screensaver: timeout, artwork, clock, and recording status.
- **Google TV:** most Google TV devices on Android TV 12 or later hide the menu for choosing a third-party screensaver. Setting Plasma needs ADB, so document it in Help:
  - set: `settings put secure screensaver_components <package>/<DreamService class>`
  - revert: `settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop`
- **Other devices:** Nvidia Shield still has a menu for this.
- **If Plasma holds `WRITE_SECURE_SETTINGS`** (granted once over ADB, 5.13), it can set itself as the screensaver from its own settings, with no further ADB.
- **In-app idle screen:** shows after the timeout while Plasma is in the foreground. This works everywhere.

### 5.15 Phone pairing (Add a Source)

- **Server:** an embedded HTTP server (Ktor CIO or NanoHTTPD) on port 8484, bound to the TV's LAN address.
  - The pairing pages (`/pair`, `/adb`) answer only while their screen is open. They stop answering on success, on leaving the screen, or after 10 minutes.
  - The phone remote (5.21) uses the same server and port. With Phone Remote off, the server runs only while a pairing page is open; with it on, it runs as 5.21 describes. The Plasma Remote apps (5.22) connect to a TLS listener on port 8485 that runs alongside it.
- **QR code:** encodes `http://<lan-ip>:8484/pair?code=<6 characters>`. The screen also shows the address and code as text.
- **Phone page:**
  - A responsive page in Plasma's styling, with the same fields as the selected source type.
  - It can also upload an M3U file.
  - On submit, the TV checks the code, fills in the fields, shows "Received from your phone" and focuses Connect.
- **ADB pairing page** (`/adb`, used by 4.24):
  - It collects the TV's wireless-debugging pairing code and pairing port, and Plasma pairs with localhost.
  - While the TV shows Settings, Plasma is in the background, so run the server inside a short-lived foreground service until pairing finishes or times out.
- **Security:**
  - a one-time code that rotates every 10 minutes;
  - 5 attempts;
  - only private-range client IPs;
  - a CSRF token in the form;
  - credentials are never echoed back;
  - the pairing page stops answering after success.

### 5.16 Settings inventory (with defaults)

| Page | Settings (default) |
|---|---|
| **Portals & Playlists** | sources list · Refresh Sources (Every 12 h) · TV Guide Sources · Merge Duplicate Channels (On) · Channel Logos (From portal) · Fail Over to Backup (On) · Remember What Works per Channel (On) |
| **Source › Playback for This Source** | Player Engine · Audio Output · Buffer Size · Use Stream Hub (all "Same as Playback settings") · User Agent (MAG for Stalker; libVLC's for others) · Referrer and Extra Headers (none) · Stream Format (Xtream: TS) · udpxy Proxy (none) |
| **Channels** (4.29) | Channel Numbers (From your playlists) · Duplicate Numbers › (fix rule: add 1000) · Numbers in Favorites (Count from 1) · Numbers in Groups (Keep channel numbers) · Number Keys (Current list first) · Merge Duplicate Channels (On) · Preferred Quality (FHD, HD, 4K, SD) · Hidden Channels · Hidden Groups |
| **TV Guide** | XMLTV feeds · time offset per source (0 h) · days to keep (7 past / 7+ future) · update time (04:00 daily, plus when stale) · channel matching |
| **Playback** (4.27) | Player Engine (VLC) · If a Stream Won't Play (Try mpv, then Android player) · HDR and Dolby Vision Movies (Android player) · Video Decoding (Hardware) · Deinterlacing (Auto) · Match Frame Rate (On, seamless only) · Audio › · Subtitles and Teletext (Off) and Style · Buffer Size (Medium, 3 s) · Rewind and Local Catch-up › · Offer Watch from the Start (On) · Connection Timeout (15 s) · Fast Zap (Off) · Up/Down in Player (Change channel) · Auto-Play Next Episode (On) · Open Movies and Series In (Plasma; v1.x) · Advanced: Tunneled Playback (Off; Android player only), Play Live TV Through Stream Hub (On), Show Stream Stats (Off), Forget Players Chosen per Channel, Save Player Logs |
| **Playback › Audio** (4.28) | Audio Output (Auto) · Passthrough Formats (every format the output reports) · Encode Surround to Dolby Digital (Off) · Stereo Downmix (Off) · Night Mode (Off) · Audio Delay (0 ms) · Channel Volume Levels (none) · Preferred Language (the device language, then original) · Skip Audio Description (On) |
| **Playback › Rewind and Local Catch-up** (4.31) | Rewind Buffer (When I pause · 1 hour) · Local Catch-up Channels (none) · Keep (24 hours) · While the TV Is Off (Keep recording) · Save To (USB drive if present, else this TV) · Storage Limit (50 GB on a drive, 8 GB on the TV) · Clear Local Catch-up |
| **Channel options › Playback** (per channel) | Player Engine (remembered automatically, or set here) · Audio Output (Same as source) · Audio Delay (0 ms) · Volume Level (0 dB) |
| **Recording** (4.34) | Record To (USB drive if present, else this TV) · Copy to Network Folder (Off until set up) · After Copying (Keep on the drive for 7 days) · Name Files for Jellyfin (On) · Start Early (1 min) · End Late (5 min) · Extra Time for Sports (+15 min) · Delete Watched Recordings (Never) · When Space Runs Low (Delete oldest watched) |
| **Parental Controls** | PIN · lock adult groups (On) · rating limit (18+ locked) · locked channels and groups · unlock for (This programme) · lock Settings (Off) |
| **Profiles** | profiles list · Ask Who's Watching (Off) · default profile |
| **Launcher & Home** | Use Plasma as Home Screen (opens Home Screen setup: Home Button Override / Replace Google TV Home / Default Home App, Restore, Help) · Home Button (Plasma Home) · Long-Press Home (Recent Apps) · When the TV Turns On (Last Channel) · When the TV Turns Off (Stop after 1 minute) · Keep Watching in a Small Window (When opening another app; only on TVs with picture-in-picture) · Live Preview on Home (Muted) · Home Rows (Favorites, Continue Watching, Apps) · Show Android Apps (On) · Screensaver (After 10 min) · Clock (24-hour) |
| **Launcher & Home › Home Rows › Continue Watching** (4.32) | Include Other Apps (Off until the user turns it on) · Apps (all on once allowed) · Jellyfin Server (not connected) · Play Jellyfin Items In (Plasma) · Show Next Episodes (On) · Hide Items Older Than (30 days) · Kids Profiles See (Plasma items only) |
| **Remote & Buttons** | Phone Remote (On) › QR code and paired phones (4.33) · colour key mapping · long-press OK (Options) · number entry delay (1.5 s) · Up/Down channel direction |
| **Appearance** | accent (White) · clock format (24-hour) · text size (Default) · Reduce Motion (Off) · show channel numbers (On) |
| **Network** | connection type, IP and signal · test the connection to each source |
| **About** | version · Check for Updates (automatic daily check: On) · update channel (Stable) · open-source licences · export diagnostics |

### 5.17 Backup and diagnostics

- **Export:**
  - Settings, sources, favourites, profiles, rules and app order go to a JSON file.
  - Credentials are optional, and encrypted with a passphrase.
  - The file can be saved to USB or downloaded through the phone page.
- **Import:** restores everything on another TV.
- **Diagnostics:** an export of recent logs, with credentials and tokens **redacted**, delivered the same way. It includes the stream stats of the last few failed streams and, when Save Player Logs is on, the engines' own logs (VLC and mpv log levels raised for the session).

### 5.18 Distribution and updates (sideloaded)

- **Builds**
  - Ship a signed release APK.
  - Many TV devices still run a 32-bit userspace, so include `armeabi-v7a` and `arm64-v8a` native code (libVLC, libmpv with FFmpeg, and the ADB library).
  - With two native engines, a universal APK carries every library twice over. **Recommended:** one APK per ABI. The updater picks the file for `Build.SUPPORTED_ABIS[0]`, which correctly reports `armeabi-v7a` on a 64-bit chip running a 32-bit system. A universal APK stays available for first installs from a USB stick (9.2).
  - Measure the APK size per ABI at M1 and M3. Compressing native libraries in the APK (`packaging.jniLibs.useLegacyPackaging = true`) makes downloads smaller, at the cost of extracting them on install.
  - Check that every `.so` is aligned for 16 KB memory pages (`zipalign -c -P 16 -v 4`), for Android 15+ devices that use them.
- **Signing key**
  - Keep the release key safe and backed up. Android only installs an update signed with the same key, and losing it would force every user to uninstall (losing their data) to update.
- **Installing the first time**
  - Use `adb install`, a downloader app, or a USB stick with a file manager.
  - APKs opened from a file manager or browser can trigger the accessibility "Restricted setting" (5.13); the fixes are documented there.
- **In-app updates** (Settings › About › Check for Updates)
  1. Fetch a small JSON manifest from a URL the owner controls (for example GitHub Releases), containing `versionCode`, `versionName`, an `apks` list (`abi`, `url`, `sha256` for each), `minSdk` and release notes.
  2. Download the APK to the cache.
  3. Verify the SHA-256 **and** that the APK's signing certificate matches the installed app.
  4. Install through a `PackageInstaller` session. This needs `REQUEST_INSTALL_PACKAGES`, and the user must allow "Install unknown apps" for Plasma once.
  5. Show a quiet badge on Settings.
  - Check daily in the background.
  - Never install during playback or a recording.
- **Before updating**
  - Automatically export a settings backup (5.17) and keep the last two.
  - Test every Room migration.


### 5.19 Continue Watching from other apps

Opt-in, from Settings › Launcher & Home › Home Rows › Continue Watching (4.32). Plasma's own items (VOD, episodes, catch-up, recordings) always show; other apps' items join them when the user turns this on.

**Source 1: Android's Play Next row** (the Watch Next channel that TV apps share; Projectivy reads the same row)
- **Reading it:** query `TvContractCompat.WatchNextPrograms.CONTENT_URI` (androidx.tvprovider). Without extra permission, an app only sees its own items.
- **Permission:** to see other apps' items, Plasma needs Android's "read TV listings" permission, `android.permission.READ_TV_LISTINGS`. It's the prompt Projectivy shows ("read all TV listings"). Request it at runtime after the explainer sheet (4.32).
  - If the TV never shows the prompt, grant it over ADB: `adb shell pm grant <app id> android.permission.READ_TV_LISTINGS`. The on-device ADB setup (5.13) offers it as one of its optional grants.
  - The constant is hidden in the public SDK (`TvContract`), so test the whole flow on the owner's TV first, in M5.
- **Coverage varies by app.** Only apps that still publish to Android's Watch Next row appear. Many apps now send Continue Watching to Google's servers with the Engage SDK, which Plasma can't read. The settings text says so.
- **Mapping:** title and episode title; `posterArtUri` (16:9 or 2:3, see 4.2); progress from `lastPlaybackPositionMillis` and `durationMillis`; `watchNextType` (show Continue and Next; Next only when Show Next Episodes is on; skip New and Watchlist); `lastEngagementTimeUtcMillis` for ordering; the owning package for the source tag; `intentUri` to open it.
- **OK** opens the item's intent in its app. **Menu:** Open, Hide from Plasma (hidden only in Plasma; it can't delete another app's items), Hide All from This App.
- **Refresh:** when Home opens, and every 15 minutes while it's visible. Also register a `ContentObserver` on the Watch Next URI where the provider allows it.

**Source 2: a Jellyfin server** (read directly, so it works without the Jellyfin app)
- **Library:** the official Jellyfin Kotlin SDK, `org.jellyfin.sdk:jellyfin-core` (1.9.x, LGPL-3.0).
- **Finding the server:** Jellyfin answers discovery broadcasts on UDP port 7359 on the local network; also allow typing an address.
- **Signing in:** **Quick Connect** first. The TV shows a code, the user approves it in another Jellyfin app, and Plasma polls the state every 5 s, then exchanges it for an access token. Username and password are the fallback. Each Plasma profile can link its own Jellyfin user. Store tokens encrypted (6.10).
- **Items:** the user's resume items, plus Next Up when Show Next Episodes is on, with their images.
- **Playing:** with Play Jellyfin Items In set to Plasma (default), play the item's direct stream URL in Plasma's player (the usual engine rules, including the HDR rule in 5.4.3). Report start, progress every 10 s, and stop to the server, so its resume point stays right everywhere. With the Jellyfin app chosen, open the app if it's installed (check what launch intents it accepts), or fall back to Plasma.
- **Offline server:** keep the last items for an hour with their art cached, and mark them "Jellyfin is offline" if OK fails.

**Row rules**
- Merge all sources by last-watched time, and cap the row at 20 items.
- Hide items older than the setting (30 days).
- Remove duplicates: when the same title comes from Jellyfin and from the Play Next row, keep the Jellyfin one, because it carries an exact position.
- **Profiles:** other apps' Play Next items belong to the TV, not to a Plasma profile, so they show for every profile except Kids (Kids Profiles See: Plasma items only). Jellyfin items follow the profile's linked user.
- **Privacy:** everything stays on the TV and the user's own server. Nothing goes to Plasma's developers (6.10).
- **Later (v1.x):** Plex (its PIN sign-in) and Emby (an API close to Jellyfin's).

### 5.20 Leaving Plasma: standby and picture-in-picture

Settings › Launcher & Home › When the TV Turns Off, and Keep Watching in a Small Window (4.22).

**When the TV turns off** (default: Stop after 1 minute)
- **Detecting it:** register for `ACTION_SCREEN_OFF` and `ACTION_SCREEN_ON` at runtime (Android doesn't deliver them to manifest receivers). On a box whose TV is switched off with the TV's own remote, the box may stay awake if HDMI-CEC doesn't put it to sleep. There, also treat `ACTION_HDMI_AUDIO_PLUG` with state 0 as "off", as a hint only, because some TVs keep the HDMI connection up in standby.
- **Screen off:** stop the picture and sound at once. Hold a partial wake lock for 70 s so the timer can't be frozen by the TV going to sleep, and after 1 minute release the engine and let the hub close the upstream, which frees the provider connection straight away instead of when it times out. The minute avoids a reconnect when someone switches the TV off by mistake.
- **Stop at once** skips the minute. **Keep playing** is for people who use Plasma for radio with the screen off.
- **Exceptions:** a recording or local catch-up keeps its own connection (the hub keeps reading for it; only the viewing stops). The rewind buffer is dropped. Multiview tiles stop with the main player.
- **Screen on:** "When the TV Turns On" decides what shows (5.13). With Last Channel, Plasma tunes it again and shows the normal zap banner. Within the minute, nothing was closed, so playback continues at the live edge.

**Keep Watching in a Small Window** (default: When opening another app, on TVs with picture-in-picture)
- **Support:** only where `PackageManager.FEATURE_PICTURE_IN_PICTURE` is present. Only some Android TVs have it (9to5Google reported it on select Android 14 TVs, appendix D). Elsewhere the row is hidden and leaving Plasma stops the stream as before.
- **Which activity goes small:** Android only lets an ordinary (standard-type) activity enter picture-in-picture, and an activity started as the system Home app (Methods 2 and 3 in 5.13) has the Home type. So the small window is its own activity, `PipPlayerActivity` (`supportsPictureInPicture="true"`, `resizeableActivity="true"`, and `configChanges` covering screen size and layout so it isn't recreated). Main and PiP activities share the engines and the hub.
- **Going small:** when the user opens another app from inside Plasma (the Apps row, All Apps, an Inputs tile, a Play Next item) while live TV or a VOD plays, Plasma starts `PipPlayerActivity`, which enters picture-in-picture at once (16:9) and attaches the engine to its own `SurfaceView`. The hub keeps the upstream, so nothing reconnects (5.4.2). Then Plasma opens the chosen app.
- **Leaving by another route** (the Assistant, an input key, a notification): with Home Button Override (Method 1), Plasma's main activity is a standard activity and can use `setAutoEnterEnabled(true)` (Android 12+) to go small by itself. As the system Home app (Methods 2 and 3), the stream stops as usual. Test both on the owner's TV in M5.
- **In the small window:** video only, no Plasma chrome; Multiview shrinks to the tile with sound. `RemoteAction`s in the system's window menu: Previous Channel · Play/Pause · Next Channel (live), or Rewind 10 s · Play/Pause · Forward 10 s (VOD). Opening the window returns to Plasma's full-screen player.
- **Sound:** if the app in front takes audio focus, mute the small window rather than pausing live TV, and unmute when focus comes back. A VOD pauses instead.
- **Going home:** pressing Home or opening Plasma closes the small window, and the channel carries on behind Home as usual. Closing it from the window menu stops the stream.
- **Standby** applies to the small window like any other playback.

### 5.21 Phone remote: the web page

**The web page is the remote anyone can use.** Plasma's local server (the one Add a Source already uses, 5.15) serves it at `http://<tv-ip>:8484/remote`. It works in Safari, Chrome or any phone browser, on iPhone and Android, and on tablets and computers too, with nothing to install. Screens: 4.33.

People who'd rather install an app can use the **Plasma Remote apps** for Android and iPhone instead (5.22). The page and the apps use the same protocol, and the TV treats them alike.

**Pairing**
1. Settings › Remote & Buttons › Phone Remote (On by default) shows a QR code with a one-time code (`/remote?code=7KQ4MZ`), plus the address and code as text. The code rotates every 10 minutes and allows 5 attempts.
2. The phone's camera opens the page. The first time, it asks for a name for the phone, filled in from the browser ("iPhone"), which the user can change to "Maya's iPhone".
3. The TV issues that phone its own random 256-bit token, which the page keeps in the browser's storage. The TV shows the toast "Maya's iPhone is now a remote" and lists the phone under Paired phones, where Forget revokes it.
4. Next time, opening the page (or its home-screen shortcut) connects straight away.

**Protocol** (shared by the web page and the apps)
- **Transport:** a WebSocket carrying JSON messages, each with a type field `t`. The web page uses `ws://<tv-ip>:8484/remote/ws`; the apps use the same messages over TLS at `wss://<tv-ip>:8485/remote/ws` (5.22).
- **Opening:** the phone sends `hello`, the TV answers `welcome`, then the phone sends `auth` with its token, or `pair` the first time. The token travels inside a message, never in the URL, so it stays out of logs.

  | Direction | Message | Contents |
  |---|---|---|
  | phone → TV | `hello` | client kind (`web`, `android` or `ios`), app version, protocol version |
  | TV → phone | `welcome` | the TV's id and name, Plasma version, protocol version, features |
  | phone → TV | `pair` | the code shown on the TV, the phone's name |
  | TV → phone | `paired` | the phone's own random 256-bit token |
  | phone → TV | `auth` | the token |
  | phone → TV | `key` | key code plus `down` or `up`, so long-press OK and a held D-pad repeat as on the remote |
  | phone → TV | `digits`, `text`, `submit` | number entry; the whole text of the focused field (debounced 150 ms); Done |
  | phone → TV | `tune` | a channel id from the lists |
  | TV → phone | `state` | whether Plasma is in front; what's playing, with the times for the timeline; the colour-key labels for the current screen (the same mapping as the TV's footer hints); the focused text field's label and text, or none |
  | TV → phone | `lists` | the profile's lists and channels with their list numbers (5.2), on request, 200 channels at a time |
  | both ways | `ping`, `pong` | every 15 s; the phone reconnects after two missed |

- **The TV's id and name:** the id is a random UUID made when Plasma is installed, so the apps can recognise the TV when its address changes. The name is Android's device name (`Settings.Global.DEVICE_NAME`, such as "Living Room TV").
- **Versions:** the protocol version is a whole number. The TV accepts its own version and the one before. An app newer than the TV shows "Update Plasma on Living Room TV to use this version of the app"; an older app asks to be updated. The web page always matches, because the TV serves it.
- **Logos:** phones load channel logos from the TV (`/remote/logo/<channel id>`, cached on the TV), never from the provider's URLs.
- **Keys reach Plasma** as if from the remote: they go into Plasma's own key handling, so number entry, the zap banner and every shortcut behave the same. This needs no permission, because it's Plasma's own window.

**The three tabs** (4.33)
- **Remote:** now playing with Go Live, the D-pad and OK, Back · Home · Menu · Numbers, ◀◀ · Play/Pause · ▶▶, CH ▲▼, and the four colour keys with their labels. Android phones get a short vibration on each press (`navigator.vibrate`); Safari on iPhone doesn't support it (the iPhone app does, 5.22).
- **Channels:** the profile's lists, numbered by the 5.2 rules (Favorites count from 1 by default), with search. A tap tunes.
- **Type:** when a text field gets focus in Plasma, the page switches to this tab and focuses its own field. Android opens the keyboard straight away; iOS opens it only after a tap, so the field says "Tap to type" there (the apps open it straight away on both). Each change sends the whole text (debounced 150 ms), the TV's field and results update, and Done submits. The dictation key on the phone's keyboard gives voice typing for free.

**Outside Plasma**, Plasma can't send keys into other apps directly (that needs a system permission), so:
- **D-pad, OK, Back and Home** work through the Home Button Override accessibility service, when it's on: `performGlobalAction()` with `GLOBAL_ACTION_BACK` and `GLOBAL_ACTION_HOME`, and on Android 13+ `GLOBAL_ACTION_DPAD_UP`/`DOWN`/`LEFT`/`RIGHT`/`CENTER`.
- **Play/Pause, rewind and fast-forward** go to whichever app is playing, through `AudioManager.dispatchMediaKeyEvent()`.
- **Typing** works only in Plasma, because Plasma isn't a keyboard app. The Type tab says "Typing works in Plasma. In other apps, use the Google TV app's keyboard." Google's Google TV app on the phone remains the way to control the whole TV, unless the Plasma Remote app's optional whole-TV control is on (5.22).
- The channel keys, numbers and colour keys work only in Plasma. The TV tells the page when Plasma isn't in front, and the page greys them out.

**When the page can connect:** the server runs while Plasma is open, and while its foreground service runs for a recording. When the Home Button Override service is on, Android keeps that service running, so the server can live there and the remote stays available in other apps (recommended; 9.2). When the TV can't be reached, the page shows "Can't reach Living Room TV. Open Plasma on the TV."

**Home-screen shortcut:** the page has a web app manifest and icons, so Add to Home Screen gives the phone a Plasma icon. It can't install as a full web app: service workers and installing need a secure (HTTPS) page, and a TV on a home network address can't get a certificate. The page needs no offline mode anyway.

**If the TV's address changes** (a new DHCP lease), the phone's bookmark and shortcut still point to the old address and won't load. Scanning the code on the TV again fixes it, and the Phone Remote page on the TV suggests reserving the TV's address in the router. The apps find the TV again by themselves (5.22).

**Security** (6.10)
- The server answers only private network addresses: IPv4 private ranges, link-local, and IPv6 unique-local.
- Every WebSocket connection must present a token that hasn't been revoked. On the web page's port, its `Origin` must also be the TV's own address, so a web page elsewhere can't drive the TV through the phone's browser. (The apps' TLS port doesn't need that check, because a browser won't connect to the TV's self-signed certificate.)
- At most 30 messages a second per phone.
- The page never receives source addresses, usernames or passwords. Channel names, numbers and logos are fine.
- Plain HTTP on the home network means someone on the same Wi-Fi could see the traffic. That's acceptable for a TV remote; the Phone Remote page says it's for home networks. The apps encrypt the connection (5.22).

### 5.22 Plasma Remote apps for Android and iPhone

Optional apps for people who'd rather install an app than open a web page. The web page (5.21) stays, and keeps working on any phone, tablet or computer. The apps talk to the TV with the same protocol, show the same three tabs (the 4.33 board is their spec too), and add what only an installed app can do. They're built in **M9** (section 8), after the TV app's first release, so they don't hold it up. "Plasma Remote" is a working name until the public name is settled (9.2, item 1).

**What the apps add**

| | Web page (5.21) | Plasma Remote apps |
|---|---|---|
| Installing | nothing to install | from the App Store, Google Play, F-Droid or GitHub |
| Finding the TV | scan the QR code, or type the address | finds every Plasma TV on the Wi-Fi by itself |
| When the TV's address changes | scan the code again | finds the TV again by its id |
| Pairing | scan the QR code with the camera | scan the code inside the app, or pick the TV and type the code |
| Keyboard when the TV wants text | opens by itself on Android; after a tap on iPhone | opens by itself on both |
| Haptics | Android only | both |
| Phone's volume buttons | no | Android: change the TV's volume while the app is open (optional). Not on iPhone (below) |
| Keeping the phone's screen on | no (the browser allows it only on secure pages) | optional, while the Remote tab is open |
| Connection | plain HTTP on the home network | TLS, pinned to the TV's own certificate |
| Several TVs | one bookmark per TV | a TV switcher in the top bar |
| Outside Plasma | as in 5.21 | as in 5.21, plus optional whole-TV control (below) |

**Screens** (not drawn; build them from the 4.33 board and the 4.35 table)
1. **Find your TV** (first run): the TVs found on the Wi-Fi ("Living Room TV · Plasma 1.0"), then Scan Code and Enter Address. If the phone denies local network access, explain why the app needs it and open the phone's Settings.
2. **Pairing:** the camera scanner, or "Enter the code shown on Living Room TV" in six boxes, then "Name this phone", filled in from the device's name.
3. **Remote · Channels · Type:** as on the web page, with a TV switcher in the top bar once more than one TV is paired.
4. **Settings:** Haptics (On) · Volume Buttons Control the TV (Android only; Off) · Keep Screen On (Off) · Swipe Pad Instead of D-pad (Off) · Paired TVs, each with Forget · Whole-TV Control (phase 2; Off) · Try the Demo · About and licences.

**Finding the TV**
- While the remote server runs, the TV advertises `_plasma-remote._tcp` over mDNS (`NsdManager.registerService`) with these TXT entries: `id` (the TV's id), `name`, `v` (protocol version) and `tls` (the TLS port, 8485).
- **Android** browses with `NsdManager`. **iPhone** browses with the Network framework's `NWBrowser`, and its `Info.plist` lists `_plasma-remote._tcp` under `NSBonjourServices` and explains the use in `NSLocalNetworkUsageDescription` ("Plasma Remote looks for TVs running Plasma on your Wi-Fi.").
- **Local network permission:**
  - **iPhone** (iOS 14+) asks the first time the app searches. If the user says no, searching and connecting both fail until they allow it in Settings › Privacy & Security › Local Network. Safari never asks for this, which is one reason the web page needs no setup.
  - **Android** doesn't ask while the app targets API 36, which is what Google Play requires from 31 August 2026. Android 17 makes local network access a runtime permission (`ACCESS_LOCAL_NETWORK`, in the Nearby devices group) for apps that target API 37. Before moving to 37, add that request with an explainer, or use `NsdManager`'s system picker (`DiscoveryRequest.FLAG_SHOW_PICKER`), which lets the user pick one TV without the permission.
- A paired TV is remembered by its id, so when its address changes the app finds it again within a few seconds.

**Pairing and the connection**
- **Scanning the code** inside the app reads the same QR code as the web page. In the shipping version, the QR also carries the fingerprint of the TV's certificate (`&fp=…`), which the app pins; the web page ignores it. The mock QR in `assets/` doesn't have it.
- **Typing the code** after picking the TV from the list trusts the TV's certificate the first time and pins it. The TV's toast ("Maya's iPhone is now a remote") makes an unexpected pairing visible.
- **TLS:** the TV serves the same WebSocket over TLS on port 8485, using a self-signed certificate it creates on first run, with the private key kept encrypted in app-private storage (6.10). Ktor documents TLS with its Netty engine (`sslConnector`). If the remote server stays on CIO, confirm that CIO can serve TLS, or run this one listener on Netty or NanoHTTPD (`makeSecure`).
- **If the pinned certificate changes** (Plasma was reinstalled or its data cleared), the app refuses to connect and asks to pair again.
- **Tokens** live in the iOS Keychain and in Android Keystore-backed storage.
- **Staying connected:** the app keeps the WebSocket open only while it's in front; iOS suspends it in the background anyway. Coming back reconnects in under a second.

**Platform details**
- **Keyboard:** when the TV reports a focused text field, the Type tab opens and focuses its field in code, so the keyboard appears straight away on both platforms.
- **Haptics:** a light tap on each key (`UIImpactFeedbackGenerator` on iPhone, `HapticFeedbackConstants` on Android), with a setting to turn them off.
- **Volume buttons:**
  - **Android:** with Volume Buttons Control the TV on, the app takes `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN` while it's in front and sends them to the TV. The TV changes Android's volume, so the HDMI-CEC caveat in 9.2 item 17 applies.
  - **iPhone:** not offered. App Review rejects apps that change what the volume buttons do (guideline 2.5.9).
- **Swipe pad** (optional): the D-pad area becomes a touch surface. Swipe for the arrows, tap for OK, and press and hold for a long OK.
- **Shortcuts:** pressing and holding the app icon offers Remote, Channels and Type (app shortcuts on Android, quick actions on iPhone). Android also gets a Quick Settings tile that opens the remote.
- **Demo mode:** a built-in pretend TV using the Appendix A data. It's for trying the app without a TV, for store screenshots, and for App Review, which can't test against a real Plasma TV (include a short video of the real thing in the review notes).

**Whole-TV control** (optional, phase 2, off by default)
- Google's own Google TV app controls the TV through a system service that most Android TVs have, the Android TV Remote Service, using its "remote protocol v2". Google doesn't document it, but open-source clients exist: androidtvremote2 (Python, Apache-2.0) and AndroidTVRemoteControl (Swift, MIT).
- With it, the app would pair with the TV itself (the TV shows a code) and could then send keys, text and volume to any app, and open apps by link, even when Plasma isn't running.
- **Risk:** a Google update can change the protocol. Add it only after the core app is stable, behind a setting (9.2).

**How to build them**
- **Recommended: Kotlin Multiplatform with Compose Multiplatform** for both apps. It's one codebase in the language the TV app already uses. Compose Multiplatform for iOS has been stable since 1.8.0 (May 2025), and the current line is 1.12.
- **Shared protocol:** the message types live in a `remote-protocol` module that the TV's `remote/` module uses too, so the two sides can't drift apart.
- **Platform code** goes through `expect`/`actual` or small Swift files: Bonjour browsing, QR scanning, haptics, Keychain and Keystore, keeping the screen on, and the Android volume keys.
  - QR scanning on Android uses CameraX with ZXing, which keeps the build free of Google Play services for F-Droid.
  - QR scanning on iOS uses VisionKit's `DataScannerViewController` (iOS 16+).
- **Alternative:** a SwiftUI iPhone app on top of the same shared Kotlin core. It gives the most native iPhone feel, at the cost of building the interface twice (9.2).
- **Not recommended:** wrapping the web page in a native shell (Capacitor or a WebView). It still couldn't open the iPhone keyboard by itself without workarounds, and App Review rejects apps that are little more than a repackaged website (guideline 4.2).
- **Minimum versions:** Android 8.0 (API 26) and iOS 16 (for the QR scanner).

**Getting them to people** (6.11)
- **iPhone:** the App Store, with TestFlight for betas. Both need a paid Apple Developer Program membership.
- **Android:**
  - Google Play has a one-time registration fee. New personal developer accounts must also run a closed test with at least 12 testers for 14 days in a row before the app can go public.
  - GitHub Releases and Obtainium, like the TV app.
  - F-Droid's main repository builds apps from source. It can take this app, because unlike the TV app it has no prebuilt native libraries.
- **Privacy:** both stores need a privacy policy link and a privacy declaration. The apps collect nothing: the App Store label is "Data Not Collected", and Play's Data safety form declares no data collected or shared. Host the policy on the project's website (GitHub Pages).
- **The web page** gets a small "Get the app" link once the apps are published.
- **Licence:** Apple's App Store terms are widely considered incompatible with the GPL (VLC was pulled from the App Store over this in 2011). So `remote-protocol/` and the apps should be Apache-2.0 (recommended), which the GPL TV app can still include. The alternative is the GPL with a contributor licence agreement, so the owner holds the rights needed to publish on the App Store (9.2).

**Later ideas for the apps** (not in M9) are in appendix E: lock-screen and notification controls, widgets, voice shortcuts and a watch remote.

---

## 6. Technical plan

### 6.1 Stack (versions checked in October 2026)

| Area | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.x, coroutines and Flow | |
| UI | Jetpack Compose with **androidx.tv:tv-material 1.1.0** and **tv-foundation 1.0.0** (both stable since 6 May 2026) | The TV-specific lazy layouts were **removed** from tv-foundation (in 1.0.0-alpha11). Use the standard Compose foundation `LazyRow`, `LazyColumn` and `LazyVerticalGrid`. |
| Player engine (default) | **libVLC 3.7.x:** `org.videolan.android:libvlc-all:3.7.6` on Maven Central (LGPL-2.1). The vlc-android repository tagged libvlc 3.7.7 on 1 October 2026; use the newest 3.7.x on Maven. | libVLC 4.0 is still in early-access builds (EAP 29, July 2026). Don't build on it. |
| Player engine (backup) | **libmpv** as an AAR: `dev.jdtech.mpv:libmpv:1.0.0` (Findroid's) or `io.github.abdallahmehiz:mpv-android-lib:0.1.12` (mpvKt's), both with MIT wrappers; or your own build from mpv-android's build scripts | Before choosing, check: FFmpeg's AC-3 encoder is included (for `lavcac3enc`), whether it's a GPL or LGPL build (5.4.12), whether it has the E-AC-3/TrueHD/DTS-HD passthrough fix (5.4.6), and that both ABIs are there. Your own build controls all four. |
| Player engine (HDR movies, last resort) | **androidx.media3 1.10.x** (released 30 March 2026): exoplayer, exoplayer-hls, exoplayer-dash, datasource-okhttp | 1.10 adds HLS location fallback for redundant streams. Plasma draws its own overlay and video layer, so the UI modules aren't needed. The FFmpeg audio extension is optional and built locally. |
| Stream hub | OkHttp upstream connections, served on 127.0.0.1 by the same embedded HTTP server as the pairing page | 5.4.2 |
| Network | OkHttp with kotlinx.serialization (lenient); Retrofit optional | Allow cleartext: providers use plain `http`. |
| Database | Room (KSP) with FTS4 | batched inserts in transactions |
| Preferences | DataStore (Proto) | |
| Background work | WorkManager for sync · AlarmManager for reminders and recordings · a foreground service for recording | |
| Images | Coil 3 | request exact pixel sizes |
| Dependency injection | Hilt (or Koin) | |
| XML | `XmlPullParser` streaming, with `GZIPInputStream` (and an xz library) | |
| Pairing server | Ktor server (CIO) or NanoHTTPD, with WebSockets (Ktor's `websockets` plugin, or NanoHTTPD's websocket module) | also serves the ADB pairing page (4.24) and the phone remote (5.21); a second instance on 127.0.0.1 serves the stream hub |
| Phone remote page | plain HTML, CSS and JavaScript with no framework, about 30 KB plus the Instrument Sans woff2 files, served from the APK's assets | 5.21 |
| Phone remote apps (M9) | **Kotlin Multiplatform** with **Compose Multiplatform 1.12.x** for Android and iOS (iOS stable since 1.8.0, May 2025) · Ktor client WebSockets and kotlinx.serialization · discovery with `NsdManager` (Android) and the Network framework's `NWBrowser` (iOS) · QR scanning with CameraX and ZXing (Android) and VisionKit (iOS 16+) · tokens in Keystore-backed storage and the Keychain | 5.22. The `remote-protocol` module is shared with the TV app. Alternative: SwiftUI for the iPhone app (9.2) |
| Network folders | **smbj** 0.15.x (`com.hierynomus:smbj`, Apache-2.0): SMB 2 and 3 with signing and encryption | copying recordings to a NAS (5.6) |
| On-device ADB | `libadb-android` (pairing, TLS, shell streams) or `dadb` (plain TCP only); mDNS through `NsdManager` | for Replace Google TV Home (5.13) |
| Secrets | Android Keystore with Tink AEAD | credentials at rest |
| Other apps' Continue Watching | `androidx.tvprovider:tvprovider` (`TvContractCompat.WatchNextPrograms`) | needs `READ_TV_LISTINGS` (5.19) |
| Jellyfin | `org.jellyfin.sdk:jellyfin-core` 1.9.x (LGPL-3.0) | Quick Connect, resume items, Next Up, playback reporting (5.19) |
| Tests | JUnit 5, Turbine, Robolectric for parsers, Compose UI tests, Macrobenchmark with Baseline Profiles | |

**SDK levels:** target API 36 (Android 16) for now. **Open decision:** the minimum. 26 is suggested; older IPTV boxes may need 24 or 25.

**Before targeting API 37 (Android 17):** apps that target Android 17 need the new `ACCESS_LOCAL_NETWORK` permission, a runtime permission in the Nearby devices group, for any traffic on the home network. That includes incoming connections and mDNS. Plasma depends on the home network for:
- the phone remote server, which takes incoming connections;
- NAS copies;
- a Jellyfin server at home;
- udpxy and multicast IPTV;
- discovery over mDNS.

So add the permission request, with an explainer in first run, before raising the target. The stream hub only uses 127.0.0.1, which doesn't count. Because Plasma is sideloaded, Google Play's target deadlines don't force the move. The phone apps meet the same rule (5.22).

### 6.2 Modules

```
app/                 navigation, DI, main activity (HOME + LEANBACK_LAUNCHER), PipPlayerActivity (5.20), DreamService
core/design/         tokens, type, focus modifiers, components, icons, previews
core/model/          domain models
core/database/       Room entities, DAOs, FTS, migrations
core/network/        OkHttp clients, per-source headers and user agent, log redaction
source/stalker/      portal client, session (handshake/token/profile), keep-alive, link resolver
source/xtream/       player_api client, URL builders
source/m3u/          streaming parser, catch-up template engine
epg/                 XMLTV importer, channel mapping, pruning, freshness
channels/            merging, numbering, duplicate fixes and overrides, lists and number entry, preferred quality
stream/              stream hub: upstream connections, fan-out to engine, recorder and rewind buffer,
                     connection budget, localhost server, rewind ring, local catch-up rings, local HLS playlists
player/core/         PlayerEngine interface, PlayerManager, engine selection and fallback, video layer,
                     frame-rate matching, audio route detection and policy, tracks, stream stats,
                     catch-up/timeshift URL resolution
player/vlc/          VlcEngine (libVLC 3.7)
player/mpv/          MpvEngine (libmpv), loaded on first use
player/media3/       Media3Engine (ExoPlayer), loaded on first use
recording/           scheduler, foreground service, file writer and keyframe index, retention,
                     network folder: SMB discovery, copy job, Jellyfin naming, .nfo and artwork writer
launcher/            apps repository, banners, inputs, Home role helpers, Home Button Override service, power-on
adb/                 local ADB client: pairing, connect, shell commands, key storage, launcher replace/restore
update/              update manifest check, download, signature verification, PackageInstaller session
pairing/             local web server and the phone web pages (Add a Source, ADB pairing)
remote/              phone remote: pairing tokens, WebSocket and TLS listeners, mDNS advert, key and text routing,
                     state pushed to phones (message types come from remote-protocol/)
remote-protocol/     Kotlin Multiplatform: the remote's message types and version, shared with the phone apps (Apache-2.0)
phone/               Plasma Remote apps (M9, Apache-2.0): shared/ (Kotlin Multiplatform), androidApp/, iosApp/
lifecycle/           screen-off and wake handling, picture-in-picture hand-off (5.20)
integrations/        watchnext/ (Play Next reader and permission flow), jellyfin/ (discovery, Quick Connect, items, reporting)
feature/*            home, livetv, guide, player, vod, search, recordings, catchup, settings, profiles, apps, onboarding
```

### 6.3 Data model (Room, main entities)

| Entity | Key fields |
|---|---|
| `Source` | id, type (STALKER / XTREAM / M3U), name, url, username?, passwordEnc?, mac?, deviceProfile (model, serial, deviceId1/2, signature, timezone, userAgent), epgUrls, isPrimary, priority, enabled, refreshHours, lastRefreshAt, status, statusMessage, maxConnections?, expiresAt?, playback (engine?, audioOutput?, bufferSize?, useHub?, referrer?, extraHeaders?, streamFormat TS / HLS, udpxyUrl?) |
| `ChannelGroup` | id, sourceId, remoteId, name, sortOrder, hidden, isAdult |
| `Channel` | id, sourceId, remoteId, groupId, name, playlistNumber?, number (assigned), numberReason (PLAYLIST / AUTO / DUPLICATE_FIX / USER), logoUrl, streamRef (URL or Stalker `cmd`), tvgId, epgKey, catchupType, catchupDays, catchupSource, quality (SD / HD / 4K), isAdult, hidden, httpUserAgent? and httpReferrer? (from `#EXTVLCOPT`) |
| `MergedChannel` | id, canonicalName, number, epgKey, plus ordered `candidates` (channelIds), so a merged channel can fall back from source to source |
| `ChannelPlayback` | mergedChannelId, engine? (VLC / MPV / MEDIA3), engineReason (USER / FALLBACK), engineSetAt, audioOutput?, audioDelayMs, volumeOffsetDb (−6…+6, default 0) |
| `ChannelSourceMemory` | mergedChannelId, preferredChannelId (the candidate to try first), reason (FAILOVER / REPEATED_FAILURES / USER), learnedAt, recheckAfter (learnedAt + 7 days), recentResults (the last 5 tunes of the first candidate) |
| `ChannelTrackChoice` | profileId, mergedChannelId, audioLanguage?, audioLabel?, subtitleLanguage?, subtitlesOn |
| `NumberOverride` | sourceId, channelKey (`tvg-id`, else stream id, else normalised name), number, setAt: the user's Keep, Choose Another Number and Swap choices, which survive refreshes |
| `GroupNumbering` | groupId, mode (KEEP / FROM_ONE): per-group override of Numbers in Groups |
| `LocalCatchupChannel` | mergedChannelId, keepHours, enabled, startedAt; the rings and their keyframe indexes live as files under the chosen volume |
| `Programme` | id, epgKey, startUtc, endUtc, title, subtitle, description, category, iconUrl, episodeNum, rating, isNew, archiveRef? · index (epgKey, startUtc) · FTS on title and subtitle |
| `Profile` | id, name, avatar, pinHash?, kind (STANDARD / KIDS / GUEST), ratingLimit, audioLang, subtitleLang |
| `Favorite` | profileId, mergedChannelId, position (also its number when Favorites count from 1) |
| `RecentChannel` | profileId, mergedChannelId, watchedAt |
| `ResumePoint` | profileId, contentType (VOD / EPISODE / RECORDING / CATCHUP), contentId, positionMs, durationMs, updatedAt |
| `VodCategory`, `Movie`, `Series`, `Season`, `Episode` | provider ids, titles, art URLs, plot, cast, year, rating, duration, container extension, addedAt; for playable items also videoCodec?, audioCodecs?, hdrFormat? (SDR / HDR10 / HLG / HDR10_PLUS / DOLBY_VISION) from the provider's probe data or Plasma's own probe |
| `MyListItem` | profileId, contentType, contentId, addedAt |
| `Recording` | id, mergedChannelId, programmeId?, title, startUtc, endUtc, padBefore, padAfter, status (SCHEDULED / RECORDING / DONE / FAILED / CANCELLED), filePath?, indexPath, sizeBytes, ruleId?, keep, watchedPositionMs, kind (TV / MOVIE / OTHER), season?, episode?, copyState (NONE / WAITING / COPYING / COPIED / FAILED), networkPath?, copyAttempts, lastCopyError?, localDeletedAt? |
| `NetworkFolder` | id, host, share, path, domain?, username, passwordEnc, lastFreeBytes, lastCheckedAt, jellyfinLibraryIds (to scan after a copy) |
| `EpisodeCounter` | seriesFolder, year, lastEpisode: the running number for series without episode numbers (S2026E001, 5.6) |
| `PairedPhone` | id, name, tokenHash (SHA-256; the token itself lives only on the phone), client (WEB / ANDROID_APP / IOS_APP), appVersion?, protocolVersion, userAgent, pairedAt, lastSeenAt, revokedAt? |
| *DataStore:* `RemoteIdentity` | tvId (UUID made on install), TLS certificate, its private key (encrypted with Keystore and Tink), certificate fingerprint |
| `RecordingRule` | id, kind (SERIES / KEYWORD / TIMESLOT), match, mergedChannelId? (null = any channel), keepLast, padBefore, padAfter |
| `Reminder` | id, profileId, programmeId, mergedChannelId, startUtc, autoSwitch |
| `AppEntry` | packageName, activity, label, position, onHome, hidden |
| `SearchHistory` | profileId, query, at |
| *DataStore:* `HomeSetup` | method (OVERRIDE / REPLACED / DEFAULT_HOME / NONE), stock launcher packages disabled, last verified at |
| *DataStore:* `AdbPairing` | key alias (Keystore-wrapped private key), paired at, last host and port, grants applied |
| *DataStore:* `PlaybackSettings` | every row of 4.27, 4.28 and 4.31 with the defaults in 5.16; per-profile language preferences stay on `Profile` |
| *DataStore:* `ChannelSettings` | every row of 4.29, plus the duplicate fix rule (ADD_1000 / NEXT_FREE) |
| `ExternalContinueItem` | source (WATCH_NEXT / JELLYFIN), sourceItemId, packageName?, title, subtitle?, imageUrl, imageAspect, positionMs?, durationMs?, type (CONTINUE / NEXT), lastEngagedAt, launchIntent? or jellyfinItemId?, hiddenByUser: a cache for Home |
| `JellyfinAccount` | profileId, serverUrl, serverId, userId, userName, accessTokenEnc, deviceId |

### 6.4 Sync and background work

- **Add-source pipeline:**
  1. Validate the login.
  2. Import groups and channels, then assign numbers (5.2): playlist numbers, sticky existing numbers, user overrides, then duplicate fixes. Live TV becomes usable at this point.
  3. Import the guide in the background, with progress.
  4. Import VOD categories, then load items lazily or in the background.
- **Big imports:**
  - Parse as a stream and insert in batches of about 1,000 rows per transaction.
  - Use a "generation" column and swap to the new generation only when the import succeeds.
  - Never block the UI thread.
- **WorkManager jobs:**
  - periodic source refresh;
  - a daily EPG import around 04:00;
  - EPG pruning;
  - retention clean-up, including local catch-up beyond its Keep time or storage limit (5.5);
  - Jellyfin item refresh every 15 minutes while Home is in use (5.19).

  All of them run only when the network is connected. Local catch-up isn't a WorkManager job: it runs in the recording service (5.6).

### 6.5 Player architecture

```
 Compose screens (Home, Live TV, Guide, Player, Multiview)
        │ intents: play(channel), pause, seek, switchEngine…        ▲ PlayerState flow
        ▼                                                           │
 PlayerManager ── EngineSelector (5.4.3) ── AudioPolicy (5.4.6) ── FrameRateMatcher (5.4.5)
        │ open(PlayRequest)
        ▼
 PlayerEngine:  VlcEngine │ MpvEngine │ Media3Engine ──renders into──▶ VideoLayer (one SurfaceView,
        ▲                                                             plus VLC's subtitle surface)
        │ http://127.0.0.1:<port>/live/<token>
 StreamHub ──┬── upstream (OkHttp) ──▶ provider
             ├── recorder (file + keyframe index)
             └── pause ring (TS segments + local HLS playlist)
```

- **`PlayerEngine`** (in `player/core`) is the only thing the rest of the app knows about an engine:

  ```kotlin
  interface PlayerEngine {
      val id: EngineId                              // VLC, MPV, MEDIA3
      val capabilities: EngineCapabilities          // passthrough formats, ac3Encode, teletext, multicast, dolbyVision, tunneling
      fun attach(layer: VideoLayer)
      fun detach()
      suspend fun open(request: PlayRequest)        // URL, headers, start position, live flag, buffer, decoding, audio route, track prefs
      fun play(); fun pause(); fun stop(); fun release()
      fun seekTo(positionMs: Long)
      fun selectAudio(trackId: String); fun selectSubtitle(trackId: String?)
      fun setAudioRoute(route: AudioRoute)          // Pcm(channels) / Passthrough(formats) / Ac3Encode
      fun setAudioDelay(ms: Int)
      val state: StateFlow<EngineState>             // Idle, Opening, Buffering(percent), Playing, Paused, Ended, Failed(kind, detail)
      val tracks: StateFlow<Tracks>
      val video: StateFlow<VideoInfo?>              // width, height, sample aspect, fps, interlaced, hdr, codec, decoder
      val stats: StateFlow<StreamStats>             // bitrate, buffer, dropped frames, audio route in use
  }
  ```

  Each engine maps settings to its own options using the cheat sheet in 5.4.11. Nothing outside `player/vlc`, `player/mpv` and `player/media3` imports an engine library.
- **`PlayerManager`** (singleton):
  - owns the main engine and the `VideoLayer`;
  - resolves stream URLs at play time through the source module (Stalker `create_link`, or the Xtream and M3U URL builders) and, for live TS, asks the stream hub for a local URL;
  - exposes one `PlayerState` flow (channel, programme, position, live offset, tracks, engine, route, errors), whichever engine is playing;
  - runs the fallback (5.4.3) and the source failover (5.1).
- **Previews:** the Live TV and Guide previews move and resize the same `VideoLayer`; Home places it full bleed behind the UI. No engine is created for a preview.
- **Multiview:** up to 3 extra VLC engines, each with its own `SurfaceView`, released when Multiview closes.
- **Threading:** libVLC and libmpv deliver events on their own threads. Post them to a single player dispatcher and never call back into an engine from inside its own event callback.
- **mpv in embed mode** (`vo=mediacodec_embed`) can't switch to software decoding when the hardware decoder fails (mpv discussion 14199), so `MpvEngine` sets `vd-lavc-software-fallback=no` and reports the failure. The manager then retries mpv in copy-back mode (5.4.3).
- **Seeking in catch-up:** catch-up and timeshift resolve to archive URLs. Seeking within a programme rebuilds the archive URL at the new start time if the provider can't seek inside one URL.

### 6.6 Compose for TV: patterns and pitfalls

- **Focus**
  - Give each screen a default `FocusRequester`.
  - Use `Modifier.focusRestorer()` on rows and grids, and `focusProperties { enter = … }` so entering a list lands on its selected item.
  - Restore focus when returning to a screen.
  - Test with the D-pad only.
- **Scrolling**
  - Use pivot-based bring-into-view, with a custom `BringIntoViewSpec` that keeps the focused item about 30 % from the start edge.
  - Avoid jumpy auto-centering.
- **Guide grid**
  - Build it as a `LazyColumn` of channel rows. Each row lays its programme cells out absolutely against a time window shared across rows.
  - Virtualise horizontally by time: draw only programmes overlapping the visible window ± 1 h.
  - Write the focus logic yourself, using the time anchor from 4.8.
- **Keys**
  - Handle global keys in `onPreviewKeyEvent` at the screen root: colour keys, CH ±, digits, Menu, Guide, Info.
  - Detect long-press from repeat count or `isLongPress`.
  - Never swallow system keys.
- **Text entry**
  - Use Plasma's own keyboards (the Search linear keyboard and the Add a Source QWERTY with URL keys).
  - Also accept hardware keyboards and voice, and offer "Use system keyboard" for accessibility.
- **Performance**
  - Give lazy items stable keys, and keep focus state per item so focusing doesn't recompose a whole row.
  - Animate scale in `graphicsLayer`.
  - Ship Baseline Profiles.
- **Overscan:** apply the 48 / 27 dp padding once at screen level. Full-bleed art and video ignore it.
- **Video under Compose**
  - Keep the video `SurfaceView` outside the Compose hierarchy that changes between screens: a sibling below the `ComposeView`, or an `AndroidView` at the bottom of the root `Box` that is never removed.
  - Screens over live video keep a transparent background where the video shows, and draw their scrims as gradients.
  - Never put a `SurfaceView` inside a lazy list.
  - Moving or resizing the layer must not destroy the surface; a destroyed surface forces the engine to rebuild its video output.

### 6.7 Performance budgets

Measure these on a mid-range box (Amlogic S905X4-class with 2 GB RAM):

| Measure | Budget |
|---|---|
| Cold start to an interactive Home | ≤ 1.5 s |
| Live preview showing video | ≤ 2.5 s |
| Key press to visible focus change | ≤ 100 ms |
| Zap to first frame, Small buffer | ≤ 1 s for a typical provider (track the 95th percentile) |
| Zap to first frame, Medium buffer (default) | ≤ 1 s plus the part of the 3 s buffer the server doesn't send at once; report both numbers |
| Preview ↔ full screen, Home ↔ Live TV | no rebuffer, no black frame |
| Engine fallback, from detecting the fault to the first frame in the next engine | ≤ 3 s, with the hub keeping the upstream open |
| Switch Player (manual) | ≤ 2 s to the first frame |
| Audio route change (receiver switched on, passthrough format change) | sound back within 1 s |
| CPU, 1080i50 H.264 in Hardware mode | under 25 % of one core for Plasma plus the engine |
| Guide scrolling | 60 fps with 1,500 channels and 200k programmes |
| Memory | under 300 MB with VLC loaded; under 380 MB with mpv or Media3 loaded as well |
| APK size | measure per ABI at M1 and M3; aim for under 80 MB per ABI with both native engines |
| Numbering after a refresh (1,500 channels, 3 sources) | under 300 ms, off the UI thread |
| Home with other apps' items | Continue Watching draws from the cache at once; fresh items replace it within 2 s |
| Local catch-up, one HD channel | under 10 % of one core; no dropped segments over 24 hours |
| Phone remote, key press on the phone to the action on the TV (same Wi-Fi) | ≤ 100 ms at the 95th percentile, for the web page and the apps |
| Plasma Remote app, opening it to connected (paired TV, same Wi-Fi) | ≤ 1 s; finding a TV that changed address ≤ 3 s |
| Going into picture-in-picture | video keeps playing; at most one dropped second, no reconnect |
| Copying a 1-hour HD recording to a NAS on gigabit Ethernet | under 2 minutes, with playback unaffected |
| XMLTV import (150 MB uncompressed) | under 90 s with no UI jank |
| M3U with 100k lines | parsed in under 15 s |

### 6.8 Manifest and permissions (sideloaded build)

- **Features:**
  - `android.software.leanback` required (TV only);
  - `android.hardware.touchscreen` and `android.hardware.microphone` not required.
- **Permissions:**
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` (to show the LAN address for pairing)
  - `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`
  - `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`, with the subtype property (recording and local catch-up, 5.5 and 5.6)
  - `USE_EXACT_ALARM`, plus `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"` (reminders and recordings)
  - `RECORD_AUDIO`, for voice search
  - `REQUEST_INSTALL_PACKAGES`, for in-app updates (5.18)
  - `WRITE_SECURE_SETTINGS`: declared, but only grantable over ADB; optional (5.13)
  - optional: `SYSTEM_ALERT_WINDOW`, for reminders over other apps and power-on launch (grant over ADB where the TV hides the setting)
  - `CHANGE_WIFI_MULTICAST_STATE`, for the multicast lock (5.4.8)
  - `android.permission.READ_TV_LISTINGS`, a runtime permission asked only when the user turns on other apps in Continue Watching (5.19)
  - `MODIFY_AUDIO_SETTINGS`, for Night Mode's audio effect and the channel volume levels on Plasma's session (5.4.6)
- **Picture-in-picture** (5.20): `PipPlayerActivity` declares `android:supportsPictureInPicture="true"`, `android:resizeableActivity="true"` and `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"`. Don't declare `android.software.picture_in_picture` as a required feature, because most TVs don't have it.
- **Home Button Override service:**
  - permission `BIND_ACCESSIBILITY_SERVICE`, with an `android:description` explaining what it does, including the phone remote's Back, Home and D-pad in other apps (5.21);
  - a config XML with `accessibilityEventTypes="typeWindowStateChanged"`, `canRetrieveWindowContent="false"` and `accessibilityFeedbackType="feedbackGeneric"`. Global actions don't need window content.
- **Package visibility:** `<queries>` for the `LEANBACK_LAUNCHER`, `LAUNCHER` and `HOME` intents, and for the Jellyfin app's package (to offer opening items in it). `QUERY_ALL_PACKAGES` is acceptable when sideloaded if it's needed.
- **Network:** a network security config that permits cleartext, because providers use `http` and the stream hub serves `http://127.0.0.1`.
- **Phone remote listeners:** port 8484 (HTTP and WebSocket, for the web page) and, from M9, port 8485 (TLS, for the apps), plus the `_plasma-remote._tcp` mDNS advert. None of these needs a permission while Plasma targets API 36; targeting 37 needs `ACCESS_LOCAL_NETWORK` first (6.1).
- **Native libraries:** `abiFilters` for `armeabi-v7a` and `arm64-v8a`; per-ABI APK splits (5.18).
- **Assets:** an application banner of 320 × 180 dp (640 × 360 px).

### 6.9 Testing and devices

- **Device matrix:**
  - Google TV Streamer (Android TV 14)
  - Chromecast with Google TV (HD and 4K)
  - Nvidia Shield TV
  - a Formuler or MyTVOnline-class box
  - a generic Amlogic S905X4 box
  - a Google TV television (TCL, Sony or Philips)
  - a low-RAM device (1–1.5 GB)
  - optionally, Fire TV in app-only mode
- **Remotes:** a Google TV remote (no Menu, colour, number or channel keys) and a MAG-style IR remote with every key.
- **Scenarios:**
  - **Data size and quality:** 1,500 channels and 200k programmes; provider errors and expired accounts; HTML returned instead of JSON.
  - **Network and clock:** bad networks; time-zone and daylight-saving changes.
  - **Storage:** a USB drive removed mid-recording.
  - **Connections:** the connection limit reached.
  - **Parental:** a locked channel inside Favorites.
  - **System:** a reboot while a recording is scheduled.
  - **Home Screen:**
    - Projectivy's override and Plasma's both turned on;
    - the accessibility toggle restricted after a file-manager install;
    - the wireless-debugging port changing after a reboot;
    - a system update re-enabling Google TV Home;
    - Restore Google TV Home, both on-device and from a computer.
- **Stream torture set.** Build a folder of short captures (and a few live test URLs from the owner's providers) and play every item in all three engines, in Hardware, copy-back and software modes. Record which engine wins for each; the result decides the defaults.

  | Area | Cases |
  |---|---|
  | MPEG-TS | PCR and PTS discontinuities; programme or PID change mid-stream; joining without PAT/PMT at the start; continuity errors; null-packet padding; a multi-programme TS; scrambled PIDs (show "This channel is encrypted") |
  | Video | H.264 1080i50 (PAFF and MBAFF); H.264 720p50; MPEG-2 576i; HEVC 2160p50 10-bit in HDR10 and HLG; Dolby Vision profiles 5 and 8.1 (VOD); AV1 (VOD, where the SoC supports it) |
  | Audio | MP2; AC-3 2.0 and 5.1; E-AC-3 5.1 and Atmos; AAC-LC; HE-AAC v1 and v2 in LATM; DTS core and DTS-HD MA (VOD); TrueHD Atmos (VOD); a stereo-to-5.1 switch at an ad break |
  | Subtitles | DVB bitmap; teletext page 888; CEA-608 in the video; WebVTT in HLS; SRT, ASS and PGS in MKV |
  | Transport | HTTP TS behind 302 redirects with tokenised URLs; chunked responses; HTTP/1.0 servers; HLS live with TS and with fMP4 segments; HLS with discontinuity tags; redundant HLS streams; UDP and RTP multicast, with and without udpxy; sources restricted by user agent or referrer; an error page served with status 200; 5 s to the first byte; drops mid-stream; connection-limit errors |

- **Sound systems:** the TV's speakers (PCM); a soundbar on ARC (Dolby Digital and DD+); an AV receiver on eARC (TrueHD and DTS-HD MA); optical only (Dolby Digital and DTS, which exercises Encode Surround to Dolby Digital); Bluetooth headphones (stereo PCM, audio delay); a receiver switched on and off mid-stream; a receiver that claims DTS-HD but stays silent.
- **Match frame rate:** 23.976, 24, 25, 29.97, 50 and 59.94 fps content on a TV that switches seamlessly and on one that doesn't; zapping between channels at the same rate and at different rates.
- **Engines:** fallback on each trigger; Switch Player during playback, during a pause and in catch-up; libVLC and libmpv loaded in the same process; Multiview with four VLC tiles; the hub turned off for one source.
- **Channel numbers:** duplicates across sources and inside one source; channels without numbers; a refresh that adds and removes channels (numbers must not move); a user override surviving a refresh; number entry in Favorites counting from 1 (a Favorites number, a full-list number, a number in neither); CH ± wrapping inside a group.
- **Local catch-up:** 24 hours on one channel to a USB drive; the storage limit reached; the drive removed; standby in each energy mode; the network dropping (gap skipped); a reboot; Keep as Recording; watching the same channel (one connection).
- **Continue Watching from other apps:** permission granted, denied, and granted over ADB; an app with Play Next items and one without; Jellyfin Quick Connect, password sign-in, an offline server, and progress showing up in the Jellyfin app after playing in Plasma.
- **Standby:** off and on within the minute (no reconnect) and after it (the last channel tunes again); off while recording another channel and the same channel; a box whose TV is switched off with the TV's remote (CEC on and off); the provider's connection count dropping straight away.
- **Picture-in-picture:** on a TV with the feature and one without; opening an app from the Apps row, from an Inputs tile and by voice; each Home method (5.13); the window's actions; another app taking audio focus; Home while the window is open.
- **Source memory and volume levels:** a primary that fails three times in five tunes; the 7-day recheck; Forget; a level on a decoded channel, a passthrough channel and in Multiview; zapping between channels with different levels (no jump in loudness).
- **Network folder:** a Synology or Samba share found over mDNS and a Windows share by address; a wrong password; a read-only share (the test write fails); the share going offline during a copy and during Record To: Network folder; illegal characters in titles; a series with and without episode numbers; Jellyfin picking up a show, an episode and a movie with their info files; the rescan with an admin and a non-admin account.
- **Phone remote:** pairing an iPhone (Safari) and an Android phone (Chrome); Forget; a revoked token; the TV's address changing; keys while Plasma is in front and in another app, with and without the accessibility service, on Android 12 and 14; typing into Search on both phones; a WebSocket from another origin (refused); two phones at once.
- **Plasma Remote apps (M9):**
  - **Phones:** an iPhone on iOS 16 and one on the current iOS; Android phones on 8.0, 14 and the newest version.
  - **Finding and pairing:** finding the TV by mDNS, and finding two TVs; pairing by scanning and by typing the code.
  - **Network:** the TV's address changing; the local network permission denied on iPhone; a TV whose certificate changed after reinstalling Plasma (the app must refuse it and offer to pair again).
  - **Versions:** an app newer than the TV, and one older.
  - **Phone features:** the keyboard opening by itself on both platforms; the volume buttons (Android); the swipe pad.
  - **Stores:** demo mode with no TV, for App Review.
  - **Web page and apps together:** the web page and an app paired at the same time.

### 6.10 Security and privacy

- **Credentials at rest:** stored encrypted (Keystore and Tink).
- **Logs:** never write full URLs (Xtream puts the username and password in every URL), Stalker tokens, or MAC addresses in full. Use a redaction helper everywhere.
- **Engine logs:** VLC and mpv log the URLs they open. Through the stream hub they only see local URLs. For streams that go direct (HLS, VOD, multicast), keep engine logging at errors only, and pass anything saved by Save Player Logs through the redaction helper.
- **Stream hub:** listens on 127.0.0.1 only, on a random port. Each session's path carries an unguessable token, and the session closes when playback ends, so other apps on the TV can't pull streams from it.
- **Telemetry:** none by default. Crash reports are opt-in.
- **Pairing server:** see 5.15.
- **Backups:** encrypted when they include credentials.
- **ADB pairing:**
  - A paired ADB key gives full shell access to the TV. Store it Keystore-wrapped in app-private storage, and never export it.
  - Offer **Forget pairing**.
  - Recommend turning Wireless debugging off after setup; with `WRITE_SECURE_SETTINGS`, Plasma can switch it on again briefly when it needs to restore.
  - Never expose the ADB connection beyond localhost.
  - The phone page for the code is LAN-only, one-time and short-lived (5.15).
- **Updates:** verify the checksum and the signing certificate before installing (5.18).
- **Other apps' viewing:** the Play Next items Plasma reads reveal what the household watches in other apps. Keep them on the TV only, never in logs or diagnostics, and drop the cache when the user turns the feature off.
- **Jellyfin:** store access tokens with Keystore and Tink like other credentials. Forget Server also signs out on the server.
- **Local catch-up and the rewind buffer** stay in app-specific storage on the chosen volume, so other apps can't read them.
- **Phone remote:** private network addresses only; a separate token per phone, stored on the TV only as a hash and revocable with Forget; WebSocket `Origin` checked; a message rate limit; no source addresses or credentials ever sent to the page (5.21). A paired phone can drive Plasma like the remote, including unlocking with the parental PIN if the user types it, but it can't read the PIN or change Parental Controls without it.
- **Plasma Remote apps:**
  - TLS to the TV, pinned to the TV's own certificate, which is fingerprinted in the QR code or trusted on first use (5.22).
  - The TV's certificate key is kept encrypted with Keystore and Tink.
  - On the phones, tokens are kept in the Keychain or Keystore-backed storage.
  - No analytics or tracking; the store privacy declarations say no data is collected.
- **Network folder:** the SMB password is stored with Keystore and Tink. Prefer SMB 3 with encryption when the server offers it, and require signing at least. Plasma deletes nothing on the share by itself (5.6). The share's address and path never go into diagnostics.
- **GitHub issues:** diagnostics bundles are redacted before they leave the TV (5.17), because users will attach them to public issues (6.11).

### 6.11 The open-source project on GitHub

Plasma will be developed in the open on GitHub. Set this up in M0, before the first public commit.

- **Licence** (9.2): **GPL-3.0-or-later is recommended.** It fits every dependency: libVLC (LGPL-2.1+), a GPL or LGPL libmpv build, the Jellyfin SDK (LGPL-3.0), and Media3, Tink and smbj (Apache-2.0, which is compatible with GPL 3 but not GPL 2). It also stops closed forks. The alternative is Apache-2.0 for Plasma, which requires an LGPL libmpv build (5.4.12). Add SPDX headers to source files, and a third-party licences screen generated at build time (Settings › About).
- **The phone apps' licence** (9.2): **Apache-2.0 is recommended** for `remote-protocol/` and `phone/`, because the App Store's terms are widely considered incompatible with the GPL (5.22). The GPL TV app can still include the Apache-2.0 protocol module. Say in CONTRIBUTING.md which folders use which licence.
- **Name:** settle the public name and application id before the repository goes public (9.2).
- **Repository layout:**

  ```
  app/, core/, source/, channels/, epg/, stream/, player/, recording/, launcher/,
  adb/, update/, pairing/, remote/, lifecycle/,
  integrations/, feature/                              the modules in 6.2
  remote-protocol/, phone/                             the shared protocol and the phone apps (Apache-2.0, with their own LICENSE files)
  docs/DESIGN.md                                       this brief
  docs/design/                                         boards, screenshots, assets and fonts from the hand-off pack
  .github/workflows/, .github/ISSUE_TEMPLATE/          CI and issue forms
  fastlane/metadata/android/                           descriptions and screenshots for F-Droid-style repositories
  README.md, CONTRIBUTING.md, SECURITY.md, CODE_OF_CONDUCT.md, LICENSE
  ```

- **CI (GitHub Actions):**
  - On every pull request: build the per-ABI debug APKs, run unit tests, lint (ktlint or detekt) and Android Lint.
  - On a version tag: build signed per-ABI release APKs and a universal one, write their SHA-256 sums, and publish them with `update.json` to a GitHub Release, which the in-app updater reads (5.18).
  - **Phone apps (M9):**
    - Pull requests: build the Android app on Linux and the iPhone app on a macOS runner. GitHub's standard hosted runners are free for public repositories.
    - A `remote-v*` tag: sign the Android app and upload it to Google Play's testing track and GitHub Releases, and upload the iPhone build to TestFlight with an App Store Connect API key.
    - Keep all signing material in encrypted Actions secrets, like the TV app's key.
- **The signing key** never goes in the repository. Store it as encrypted Actions secrets (the keystore base64-encoded, plus its passwords) and keep an offline backup. Losing it forces every user to uninstall to update (5.18).
- **Dependencies:** Renovate or Dependabot for Gradle. Bump libVLC and libmpv only after the stream torture set passes (6.9).
- **Issues:**
  - Issue forms ask for the device, Android version, source type (never addresses or credentials), engine, sound system, and the diagnostics bundle.
  - In Plasma, Settings › About › Report a Problem shows a QR code to the new-issue page and offers the redacted diagnostics bundle through the phone page (5.15), because a TV has no browser to attach files from.
- **Distribution:** GitHub Releases (also what the updater uses), Obtainium (installs and updates straight from GitHub releases), and the IzzyOnDroid repository, which accepts developer-built APKs for F-Droid clients. The main F-Droid repository builds everything from source, which would mean building libVLC and libmpv from source too: possible later, not in v1.
- **Phone app distribution** (5.22):
  - **iPhone:** the App Store and TestFlight, which need a paid Apple Developer Program membership.
  - **Android:** Google Play, whose new personal developer accounts must first run a 14-day closed test with at least 12 testers; GitHub Releases and Obtainium; and the main F-Droid repository, which can build this app from source.
  - **Both stores** need a privacy policy page; host it on GitHub Pages with the project site.
- **Content stance in the open** (1.5): the README says Plasma ships with no channels or playlists and is for subscriptions the user already has. Close issues and pull requests that add provider lists or ask where to get streams. The mock art and logos in `docs/design/` are generated placeholders, and the fonts are under the SIL OFL.
- **Translations:** all copy is in string resources; a hosted translation tool (for example Weblate) can come later.
- **Telemetry:** none (6.10). Crash reports stay opt-in, and if they're added, the service must be one the user can see and turn off.

---

## 7. Protocol notes

> These notes come from community documentation and real-world clients, not official specifications. Providers differ, so build defensively: parse leniently, log redacted raw responses in debug builds, and test against the owner's own subscription.

### 7.1 Stalker / Ministra portal (MAC login)

- **Portal URL forms users type:**
  - `http://host/c/`
  - `http://host:port/stalker_portal/c/`
  - `http://host/portal.php`

  Normalise the base by removing the trailing `c/`. The API endpoint is usually `<base>/server/load.php`; some portals use `<host>/portal.php`. Try both.
- **Requests and responses:** HTTP GET with query parameters. JSON responses are wrapped as `{"js": …}`.
- **Headers** (MAG emulation, as used by open-source clients):
  - `User-Agent: Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 234 Safari/533.3` (portals vary; `rev: 250` is also common, so let users override it)
  - `X-User-Agent: Model: MAG250; Link: WiFi`
  - `Referer: <base>/c/`
  - Cookies: `mac=<MAC>; stb_lang=en; timezone=<IANA zone>`
  - After the handshake: `Authorization: Bearer <token>`
- **Session**
  1. `type=stb&action=handshake` returns `token`.
  2. `type=stb&action=get_profile`, with device parameters such as `hd=1`, `sn`, `stb_type=MAG250`, `image_version`, `hw_version`, `device_id`, `device_id2`, `signature`, `ver`, `auth_second_step=0`, `not_valid_token=0`, `JsHttpRequest=1-xml`.
  3. Optionally `type=stb&action=do_auth&login=…&password=…`, for portals that require a login.
  4. If any call returns an authorisation error, perform the handshake again.
- **Channels**
  - `type=itv&action=get_genres`
  - `type=itv&action=get_all_channels`, or paged `type=itv&action=get_ordered_list&genre=<id>&p=<page>`

  Each channel includes `cmd`, `number`, `name`, a logo, a genre id and archive flags.
- **Playback:** call `type=itv&action=create_link&cmd=<cmd>` **at play time**. Links are often tokenised and expire. The returned `cmd` may begin with `ffmpeg ` or `auto `; strip that prefix.
- **EPG:**
  - `type=itv&action=get_short_epg&ch_id=<id>` for now and next;
  - `type=itv&action=get_epg_info&period=<hours>` for bulk;
  - or the provider's XMLTV, if it offers one.
- **Catch-up:** `type=tv_archive&action=create_link&cmd=<programme cmd>`. Programmes mark whether they're in the archive.
- **VOD:**
  - `type=vod&action=get_categories`
  - `type=vod&action=get_ordered_list&category=<id>&p=<page>`
  - `type=vod&action=create_link&cmd=<cmd>`

  Series handling varies between portal versions.
- **Keep-alive:** some portals expect `type=watchdog&action=get_events` to be called periodically during playback. Implement it with a configurable interval of about 60–120 s.
- **Robustness:**
  - Numbers can arrive as strings, empty lists as `false`, and errors as HTML pages instead of JSON.
  - The `timezone` cookie decides EPG times.
- **Identity:**
  - The MAC must be the one registered with the provider; `00:1A:79` is the usual MAG prefix.
  - Plasma can generate a stable MAC per install for the user to register with their provider, or the user enters the one they have.
  - Serial number, device ids and signature are optional advanced fields.

### 7.2 Xtream Codes API

- **Login and account:** `GET {server}/player_api.php?username=U&password=P` returns:
  - `user_info`: status, `exp_date` (Unix time), `max_connections`, `active_cons`, `allowed_output_formats`, `is_trial`;
  - `server_info`: url, port, https_port, protocol, timezone, `timestamp_now`.
- **Actions** (`&action=`):
  - Live: `get_live_categories`, `get_live_streams` (optional `&category_id=`).
  - Movies: `get_vod_categories`, `get_vod_streams`, `get_vod_info&vod_id=`.
  - Series: `get_series_categories`, `get_series`, `get_series_info&series_id=`.
  - Guide: `get_short_epg&stream_id=&limit=`, and `get_simple_data_table&stream_id=` (the full guide for one stream).
- **Full guide:** `{server}/xmltv.php?username=U&password=P`.
- **Stream URLs:**
  - live: `{server}/live/U/P/{stream_id}.ts`, or `.m3u8` when HLS is allowed;
  - movie: `{server}/movie/U/P/{stream_id}.{container_extension}`;
  - episode: `{server}/series/U/P/{episode_id}.{container_extension}`.
- **Catch-up:**
  - Live streams that support it carry `tv_archive: 1` and `tv_archive_duration: <days>`.
  - The usual archive URLs are `{server}/timeshift/U/P/{duration_min}/{YYYY-MM-DD:HH-MM}/{stream_id}.ts`, with the start time in the **server's time zone**, or `{server}/streaming/timeshift.php?username=U&password=P&stream={id}&start={YYYY-MM-DD:HH-MM}&duration={min}`.
- **EPG quirks:**
  - Titles and descriptions from the short EPG are usually Base64-encoded.
  - Prefer the Unix `start_timestamp` / `stop_timestamp` fields to the local-time strings.
- **Credentials are in every URL:** redact them in all logs.

### 7.3 M3U and XMLTV

- **Header:**
  - `#EXTM3U url-tvg="…"` (also written `x-tvg-url`) points at the guide.
  - It may also set catch-up defaults for the whole playlist.
- **Entry:**
  ```
  #EXTINF:-1 tvg-id="arena1.example" tvg-name="Arena Sports 1" tvg-logo="https://…/arena1.png" tvg-chno="105" group-title="Sports" catchup="shift" catchup-days="7",Arena Sports 1
  #EXTVLCOPT:http-user-agent=Mozilla/5.0
  http://provider.example/live/105.ts
  ```
  `#EXTVLCOPT:http-user-agent=` and `http-referrer=` lines, if present, sit between `#EXTINF` and the URL. Store them on the channel and apply them in the stream hub, or pass them to the engine for direct streams (5.4.8). Ignore any other `#EXTVLCOPT` option unless it's on a short allow-list (for example `network-caching`), so a playlist can't change engine behaviour in unexpected ways.
- **Catch-up modes:**
  - `default`: `catchup-source` is the full URL.
  - `append`: append `catchup-source` to the live URL.
  - `shift`: add `?utc={utc}&lutc={lutc}`.
  - `flussonic`, `flussonic-hls`, `flussonic-ts`, `fs`.
  - `xc`: Xtream-style.
  - `vod`.
- **Catch-up placeholders:**
  - start: `{utc}` or `${start}`
  - end: `{utcend}` or `${end}`
  - now: `{lutc}`, `${now}` or `${timestamp}`
  - date parts: `{Y} {m} {d} {H} {M} {S}`
  - duration: `{duration}`, `{duration:60}` (in minutes)
  - offset: `{offset:X}`
  - programme id: `{catchup-id}`
  - formatted variants: `{utc:YmdHM}`, `${start-year}` and similar.
- **XMLTV:**
  - `<channel id>` with `<display-name>` and `<icon src>`.
  - `<programme start="20261021194500 +0000" stop="…" channel="…">` containing `<title>`, `<sub-title>`, `<desc>`, `<category>`, `<icon>`, `<episode-num system="xmltv_ns">`, `<rating>` and `<new/>`.
  - Parse it as a stream, and support gzip.
- **Size:** playlists can exceed 100k lines with VOD mixed in. Classify entries by group name or URL path (`/movie/`, `/series/`), and import only live entries into Live TV unless the source has VOD switched on.

---

## 8. Build plan

Each milestone ends with something demonstrable. Every one of them must look like the boards.

| # | Milestone | Done when |
|---|---|---|
| **M0** | **Foundations:** the GitHub repository (licence, README, CI, issue forms, `docs/`, 6.11) · project and modules, design system, navigation skeleton, mock repository using the Appendix A data and the bundled assets | Every screen in section 4 renders from mock data and matches its screenshot side by side; D-pad focus works everywhere; previews exist for all components; CI builds every pull request |
| **M1** | **Live TV core on VLC:** the `PlayerEngine` interface and `VlcEngine` · the video layer · the stream hub (pipe and connection budget) · M3U and XMLTV import · channel numbers from the playlist, with fixes for duplicates inside it · Live TV · Player and overlay · zap banner · number entry (including Favorites counting from 1) · favourites · last channel · Home hero live preview · Playback settings for VLC (decoding, deinterlacing, match frame rate, buffer size) · Audio settings with passthrough per format · stopping the stream when the TV turns off (5.20) | Add an M3U by URL; zap between 3 channels in ≤ 1 s each at the Small buffer; preview ↔ full screen without a rebuffer; interlaced sports play smoothly at 50 fps; AC-3 reaches the owner's sound system as passthrough; the guide shows now and next; the state survives a reboot; turning the TV off frees the provider connection within a minute, and turning it on plays the last channel again |
| **M2** | **Guide, catch-up, reminders, search** · the "Started 20 min ago" hint (4.11) | Guide scrolls smoothly with real data; time-anchored focus works; catch-up plays from M3U templates; reminders fire; search returns live, upcoming and catch-up results; tuning in late on a catch-up channel offers Watch from the Start |
| **M3** | **Sources, failover and the backup engine:** Xtream and Stalker · merging and source failover · Remember What Works per Channel · Settings › Channels and the Duplicate Numbers review across sources · Portals & Playlists with Playback for This Source · Add a Source with phone pairing · `MpvEngine`, automatic engine fallback, Switch Player and per-channel memory · Encode Surround to Dolby Digital · Channel Volume Levels | Owner's real Stalker portal and Xtream account work end to end; failover switches sources; a channel whose primary keeps failing starts on the source that worked; numbers stay put across a refresh and duplicates show for review; the phone form fills the TV form; a stream VLC can't play starts in mpv without a new provider connection; AAC 5.1 reaches an optical-only soundbar as Dolby Digital; a quiet channel set to +3 dB stays louder in every engine |
| **M4** | **VOD, plus the Android player:** Movies, Series, detail pages, resume, My List, Continue Watching · `Media3Engine` for Dolby Vision and HDR10+ movies · File Info | Resume across reboots; series seasons and episodes load; Continue Watching row on Home; a Dolby Vision movie plays with correct colours |
| **M5** | **Launcher:** intent filters · Home Screen setup with all three methods (accessibility override, Replace Google TV Home over ADB from the TV and from a computer, Default Home App) and Restore · Apps row and All Apps with move and hide · Inputs · Device Settings · power-on behaviour · screensaver (DreamService) · Home-button settings · in-app updater · Continue Watching from other apps (Play Next row and Jellyfin) · picture-in-picture when leaving Plasma (5.20) | On the owner's Google TV, the override works, Replace works from both the TV and a computer, and Restore undoes it; Default Home App works on a chooser device; an update installs over the top without losing data; another app's Play Next item and a Jellyfin movie show in Continue Watching, and the Jellyfin resume point updates after playing in Plasma; on a TV with picture-in-picture, opening an app from the Apps row keeps the channel playing in a small window |
| **M6** | **Recording, rewind and local catch-up:** scheduler, service, Recordings screen, rules, storage, retention, chase play · the rewind buffer · local catch-up channels and Keep as Recording · Settings › Rewind and Local Catch-up · Settings › Recording with the network folder and Jellyfin naming (5.6) | A 2-hour recording to USB plays back; a series rule records the next episode; recording the channel being watched uses one connection; conflicts are detected against the connection budget; pausing a channel without an archive for 10 minutes and resuming loses nothing; a local catch-up channel plays yesterday evening's programmes from the Guide; a finished episode shows up in the owner's Jellyfin library with its title, episode number and artwork |
| **M7** | **Profiles, parental controls, Multiview, phone remote** (5.21) | Kids profile can't see locked content; the PIN flow works; Multiview runs 2–4 tiles within the device and connection limits; an iPhone and an Android phone pair by QR code, zap, tune from the Channels tab and type into Search |
| **M8** | **Hardening:** performance budgets, accessibility, device matrix, the stream torture set (6.9), crash-free sessions, release build | The budgets in 6.7 are met; every torture-set item plays in at least one engine and the defaults reflect the results; TalkBack labels are present; no ANRs; signed per-ABI release APKs |
| **M9** | **Plasma Remote apps for Android and iPhone** (5.22), after the TV app's first release · the `remote-protocol` module (its message types are frozen in M7, so this can start early with a second developer) · the TV's TLS listener and mDNS advert · Find your TV, Pairing, the three tabs, Settings, demo mode · store listings, privacy policy, CI for both apps | Both apps find the owner's TV by themselves, pair by scanning and by typing the code, and reconnect after the TV's address changes; the keyboard opens by itself on both; a reinstalled TV is refused until paired again; the iPhone app is on TestFlight and passes App Review using demo mode; the Android app is on Google Play's testing track and in an F-Droid build |

---

## 9. Decisions

### 9.1 Decided by the owner

| Decision | Answer | What follows from it |
|---|---|---|
| Name | **Plasma** (renamed from Aster) | The wordmark "plasma" on every board. The application id is still open (9.2). |
| Player engines | **libVLC by default, mpv as the backup.** | Section 5.4: three engines behind one interface, automatic fallback and Switch Player. The Android player (Media3) keeps Dolby Vision and HDR10+ movies and the last resort; that part is a design recommendation the owner can overrule. |
| Sound | **TiviMate-style choice between passthrough and decoding**, including per source | Screen 4.28 and 5.4.6: Auto, Passthrough or Decode to PCM; passthrough per format; Encode Surround to Dolby Digital through mpv; per-source and per-channel overrides. |
| Distribution | **Sideloaded APK.** No Google Play for now. | No Play policy limits: `USE_EXACT_ALARM`, a `specialUse` recording service, the accessibility override without a Play declaration, and an in-app updater (5.18). Keep the signing key safe. |
| Home button on Google TV | **The accessibility Home Button Override is the main method.** It already works on the owner's TV with Projectivy. | Build it first in M5. Detect other overrides and warn about them (5.13). |
| ADB option | **Offer Replace Google TV Home over ADB**, from the TV itself and from a computer, with Restore. | Screens 4.23 and 4.24, the `adb/` module, and the safety rules in 5.13. |
| Recording service type | **`specialUse`** | No 6-hour cap on long recordings. |
| Open source | **On GitHub** | Section 6.11: licence, CI, releases, issue forms and the content stance. The in-app updater reads GitHub Releases. |
| Engines stay invisible | **Plasma's own interface everywhere**, whichever engine plays | Number entry, the zap banner, overlays and menus are Plasma's (5.4). |
| Channel numbers | **From the playlists, with duplicates fixed and shown, and Favorites or groups counting from 1 or keeping full-list numbers** | Screens 4.29 and 4.30, section 5.2, the `channels/` module. |
| Local catch-up | **Wanted:** a rewind buffer plus channels recorded all the time, with time and storage limits | Screen 4.31 and 5.5; built in M6 (v1). |
| Continue Watching | **Can include other apps when the user allows it**, as Projectivy does, plus Jellyfin | Screen 4.32 and 5.19; built in M5. |
| Ideas approved for v1 | **Yes to:** stopping the stream in standby, the phone remote and keyboard, the "Started 20 min ago" hint, picture-in-picture when leaving Plasma, per-channel volume levels, remembering the best source per channel, and recordings on a NAS for Jellyfin | 5.20 and 5.21, 4.11, 5.4.6, 5.1 and 5.6, with screens 4.33 and 4.34; spread across M1–M7 (section 8). The rest of the backlog stays in appendix E. |
| Phone remote | **A web page that Plasma serves**, so there's nothing to install | The phone pairs by scanning a QR code (5.21). |
| Phone remote apps | **Also offer Plasma Remote apps for Android and iPhone**, for choice and convenience, while keeping the web page | Same protocol as the web page; they add finding the TV by themselves, a pinned TLS connection, the keyboard opening by itself, haptics and more (5.22). Built in M9, after the TV app's first release. |

### 9.2 Still open (ask the owner before building these)

1. **The name in public, and the application id.** "Plasma" is decided, but the repository will be public now, so check it before the first release. KDE's desktop is called Plasma, and its TV interface, Plasma Bigscreen, came back with Plasma 6.7 in mid-2026. Options: keep "Plasma" as the app name with a more distinctive repository name, or pick a distinctive public name. The ADB commands need the real application id; `tv.plasma.launcher` is a placeholder, and an open-source project often uses `io.github.<owner>.<app>`.
2. **Minimum Android version:** 26 is suggested. Lower it to 24 or 25 only for older boxes the owner cares about.
3. **Default locale:** en-GB or en-US (it affects Favourites/Favorites and programme/program).
4. **Beta channel:** GitHub Releases now hosts the updates (6.11). Offer pre-releases as a beta channel in Settings › About?
5. **Rewind buffer default:** "When I pause" (no constant writes, the default in 5.5) or "Always" when a USB drive is set? The board shows a user who chose Always · 2 hours.
6. **VOD metadata enrichment** (TMDB) when the provider has no art. It needs an API key and attribution.
7. **Multiview maximum:** 4 tiles, or cap it at 2 on low-end boxes?
8. **Fire TV:** support it as app-only?
9. **Accent default:** white, as on the boards, or one of blue, orange or green?
10. **Profiles at start-up:** ask "Who's watching?" on every wake, or never by default?
11. **Repository licence:** GPL-3.0-or-later (recommended, 6.11), or Apache-2.0 with an LGPL build of libmpv (5.4.12). Decide before the first public commit.
12. **APK packaging:** one APK per ABI (recommended, because two native engines make a universal APK large) or a single universal APK (5.18).
13. **Numbering defaults:** Favorites count from 1 and groups keep full-list numbers (5.2), as on the boards. Keep these defaults?
14. **The rest of the idea backlog** (appendix E): which of these go into v1.x, or nowhere?
15. **Phone remote outside Plasma:** run its server inside the Home Button Override service, so the phone works as a remote in other apps too (recommended, 5.21), or only while Plasma is open?
16. **Network folder default:** record to local storage and copy afterwards (recommended, because a Wi-Fi drop can't spoil a recording), or record straight to the share once one is set up (5.6)?
17. **Volume buttons on the phone page:** add Vol −/+ and Mute? They use Android's volume, which reaches a soundbar or receiver only where the TV passes volume on over HDMI-CEC, so test on the owner's setup first. The same applies to the Android app's volume-button setting (5.22).
18. **How to build the phone apps:** Compose Multiplatform for both (recommended: one codebase, in the TV app's language), or a SwiftUI iPhone app on the shared Kotlin core for the most native iPhone feel, at the cost of a second interface (5.22)?
19. **The phone apps' licence:** Apache-2.0 for `remote-protocol/` and `phone/` (recommended, because of the App Store's conflict with the GPL), or the GPL with a contributor licence agreement (5.22, 6.11)?
20. **Store accounts:** the App Store needs a paid Apple Developer Program membership. On Android, publish on Google Play (a one-time fee, plus a 14-day closed test with 12 testers for a new personal account), or only on GitHub and F-Droid?
21. **Whole-TV control in the app:** add Google's undocumented Android TV remote protocol later, so the app can control any app on the TV, type anywhere and change the volume, accepting that a Google update could break it (5.22)?

---

## 10. Appendices

### A. Mock data (use it for previews and test fixtures)

The time on every board is **Wed 21 Oct 2026, 20:41**, except the zap and reminder board, which is at 21:59.

**Channels**

| No. | Channel | Logo asset | On now / notes |
|---|---|---|---|
| 101 | Northline News | `logo_northline.png` | Evening Bulletin 20:00–20:30 · **Business Wrap 20:30–21:00** · The World Tonight 21:00–22:00 |
| 102 | Meridian 24 | `logo_meridian.png` | Markets Close 19:00–21:00 · Late Edition 21:00–21:30 · Meridian Debate 21:30–22:30 |
| 103 | Arena Sports News | `logo_arenanews.png` | Sports Desk 20:00–21:00 |
| 104 | Arena Sports Extra | `logo_arenaextra.png` | Transfer Talk 20:30–21:30 |
| 105 | Arena Sports 1 ★ | `logo_arena1.png` | **Harbour City v Redmoor** 19:45–22:00 (2–1, 64′; recording now) · 22:00 Match of the Week Extra (reminder) · 23:00 Football Tonight · 00:30 Fight Night: Main Card (recording scheduled) |
| 106 | Arena Sports 2 | `logo_arena2.png` | Grand Slam Tennis: Quarter-final 19:00–22:30 |
| 107 | Velocity Motor (4K) | `logo_velocity.png` | Endurance 24: Night Stint 20:00–21:30 · Pit Lane 21:30–22:00 · Endurance 24: Night Stint, Part 2 from 22:00 |
| 108 | Courtside+ | `logo_courtside.png` | City Lights v Northport 20:30–23:00 |
| 109 | Fairway Golf | `logo_fairway.png` | Coastal Classic, Round 2 18:00–22:00 |
| 110 | Combat Arena (18+, locked) | `logo_combat.png` | Fight Night: Prelims 20:00–22:00 |
| 120 | Atlas One | `logo_atlas.png` | The Quiz Hour 20:00–21:00 · Baker's Dozen 21:00–22:00 |
| 140 | Cinema Prime 4K | `logo_cinemaprime.png` | The Last Lighthouse 19:30–21:45 · Glass Orchard 21:45 |
| 180 | Wildframe 4K | `logo_wildframe.png` | Kingdoms of Ice, Episode 3 20:00–21:00 · Rivers of the North 21:00–22:00 |
| 200 | Pulse | `logo_pulse.png` | music; Late Night Sessions (catch-up, Mon 22:00) |
| — | Orbit Science | `logo_orbit.png` | Deep Field (recorded) |

**Counts and groups**
- All Channels 1,284 · Favorites 24 · Recently Watched 18 · Sports 186 · News 92 · Movies 140 · Entertainment 212 · Documentary 58 · Kids 64 · Music 41 · 4K UHD 36.
- 214 channels have catch-up.

**Sources**

| Name | Type and address | Details |
|---|---|---|
| Home Portal | Stalker, `portal.example.net/c/` | MAC `00:1A:79:4C:2E:91` · active until 12 March 2027 · 1 of 2 connections in use · 42 ms · library: 1,284 channels, 8,412 movies, 2,106 series, 7-day catch-up |
| Backup Line | Xtream, `line.example.org` | standby |
| Sports Extras | M3U, 212 channels | guide is 2 days old |

The phone pairing address is `192.168.1.42:8484` with code `7KQ 4MZ`.

**Home Screen (mock state):** Google TV Streamer, Android TV 14 · Home Button Override on · Replace Google TV Home not set up yet (pairing in progress on board 24) · placeholder application id `tv.plasma.launcher` with Home activity `.HomeActivity` and override service `.home.HomeButtonService`.

**Playback (mock state):**
- Arena Sports 1 plays in **VLC**: 1080i H.264, deinterlaced to 50 fps, AC-3 5.1 passed through, 8.2 Mb/s, buffer 2.1 s, from Home Portal. The next engine for Switch Player is mpv.
- Settings: Player Engine VLC · Video Decoding Hardware · Deinterlacing Auto · Match Frame Rate On, seamless only · Buffer Size Medium, 3 s · Rewind and Local Catch-up "2-hour buffer · 1 channel" · Subtitles Off.
- Sound system: **HDMI eARC to an AV receiver** that supports Dolby Digital, Dolby Digital Plus (with Atmos) and DTS, but not DTS-HD MA or TrueHD. Audio Output Passthrough with those 3 formats ticked · Encode Surround to Dolby Digital Off · Stereo Downmix Off · Night Mode Off · Audio Delay 0 ms · Preferred Language "English, then original" · Skip Audio Description On.

**Movies** (`poster_*.jpg`)
Neon Tide · **The Last Lighthouse** (2025 · Drama · Mystery · 2 h 14 min · 13+ · resume at 1 h 02 min left · cast Mara Ellison, Aden Foley, June Okafor · directed by Ines Calloway) · Glass Orchard · Ember Road · Low Orbit · Saltwater Hymn · The Cartographer · Velvet Hour · Polar Night · Paper Kingdoms · The Night Desk · Kite Season.

**Series** (`series_*.jpg`)
**Atlas Station** (Sci-Fi · Drama · 2024–2026 · 3 seasons · 16+ · S2, E4 "Dead Reckoning", 18 of 58 min left) · Kingsway · Harbour Lights · The Panel · North of Nowhere · Last Orders · Night Stint · Small Claims · Backstage · Heat Index · Quiet Hours · Tumble Town.

**Apps** (`app_*.png`)
Reelbox · Lantern Browser · Fileport · Sonar · Snapshot · Shieldline VPN (these six are on Home, in this order) · Brightside Weather · Cloudnest · Driftwood Podcasts · Kinetic · Pingmeter · Quarry · Tumble.

**Profiles**
Maya (main; last watched Arena Sports 1) · Daniel (PIN) · Kids (ages 7 and under) · Guest (no history).

**Recordings** (12 in total)
- Harbour City v Redmoor (recording now)
- Kingdoms of Ice ×3 (series rule, 2 unwatched)
- The Last Lighthouse (Sun 18 Oct)
- Late Night Sessions (Mon 19 Oct)
- Business Wrap (yesterday, 20:30)
- Endurance 24: Hours 1–6 (Sat 17 Oct)
- Deep Field (deletes in 2 days, keep last 3)
- Tuesday Night Football (watched)
- 2 more further down the list

**Scheduled:** Fight Night: Main Card (tonight, 00:30–03:00) · Kingdoms of Ice, Ep. 4 (Thu 22 Oct, 20:00, series rule) · Harbour City v Northport (Sat 24 Oct, 15:00, series rule).

**Storage:** USB drive, 212 GB free of 500 GB: recordings 246 GB, local catch-up 38 GB, rewind buffer 4 GB.

**Channels (mock state):**
- Numbers from the playlists · Favorites count from 1 (1 Arena Sports 1, 2 Velocity Motor, 3 Northline News…) · groups keep full-list numbers · Number Keys "Current list first" · 38 channels merged · Preferred Quality FHD, then HD · 12 hidden channels, 3 hidden groups.
- **Renumbered duplicates** (rule: add 1000), all from Sports Extras: Arena Sports Extra 2 → 1104 · Courtside Classics → 1108 · Fairway Golf 2 → 1109 · Ringside 2 → 1212 (Ringside 1 keeps 212). Courtside Classics and Ringside 1 and 2 have no logos.

**Rewind and local catch-up (mock state):** Rewind Buffer Always · 2 hours · local catch-up on **Courtside+** (108, no provider archive), keeping 24 hours, using 1 of Home Portal's 2 connections · While the TV Is Off: Keep recording · USB drive, limit 50 GB.

**Continue Watching (mock state):** other apps on · Play Next row allowed, with Reelbox and Driftwood Podcasts (4 items) · Jellyfin server `media.home.lan`, signed in as Maya (3 items) · row order: Atlas Station (S2, E4, 18 min left) · **Kite Season** (Jellyfin, 52 min left, poster on a blurred backdrop) · The Last Lighthouse · Kingdoms of Ice, Episode 2 (catch-up) · Late Night Sessions (catch-up). The Night Desk is no longer in this row.

**Phone remote (mock state):** the TV is called **Living Room TV** · page at `192.168.1.42:8484`, code `7KQ 4MZ` (`pair_qr_remote.png`) · paired phones: **Maya's iPhone** (connected now) and Daniel's Pixel (last used yesterday) · the Remote tab shows 105 · Arena Sports 1, Harbour City v Redmoor, 2:14 behind live, with the Live TV colour keys Favorite · Catch-up · Sort · Multiview · the Channels tab shows Favorites · 24, counting from 1: 1 Arena Sports 1, 2 Velocity Motor, 3 Northline News, 4 Meridian 24, 5 Wildframe 4K, 6 Atlas One, 7 Courtside+ · the Type tab is typing "night" into Search, with 4 programmes and 2 movies on the TV.

**Plasma Remote app (mock state, for previews and demo mode):** Find your TV lists **Living Room TV · Plasma 1.0** (paired, ●) and Bedroom TV · Plasma 1.0 (not paired) · TLS port 8485 · protocol version 1 · Maya's iPhone shows "App" on the TV's Paired phones list, and Daniel's Pixel "Browser" · demo mode uses the Remote, Channels and Type data above.

**Recording destinations (mock state):** Record To USB drive · Copy to Network Folder on, to `nas.home.lan › Media › Recordings` (SMB, 3.1 TB free) · After Copying: keep on the drive for 7 days · Name Files for Jellyfin on · Jellyfin at `media.home.lan` rescans after each copy · example files: `Kingdoms of Ice S01E03.ts`, `Late Night Sessions S2026E001.ts`, `The Last Lighthouse (2025).ts`, `2026-10-21 1945 Harbour City v Redmoor.ts`.

**Small helpers (mock state):**
- Channel volume levels on 3 channels: Northline News +3 dB, Meridian 24 −2 dB, Atlas One −3 dB. All three are stereo channels that are decoded, so the levels apply even with passthrough on.
- Source memory: Fairway Golf (109) plays from **Backup Line** first, because Home Portal failed on 3 of its last 5 tunes (remembered 3 days ago, rechecked in 4 days).
- Leaving Plasma: When the TV Turns Off "Stop after 1 minute" · Keep Watching in a Small Window "When opening another app" (shown only on TVs with picture-in-picture).

### B. Assets

| Folder pattern | Contents |
|---|---|
| `assets/logo_*.png` | 15 fictional channel wordmarks, transparent, rendered at 3× |
| `assets/app_*.png` | 13 fictional app banners (16:9) |
| `assets/poster_*.jpg` | 12 movie posters, 600 × 900 |
| `assets/series_*.jpg` | 12 series posters, 600 × 900 |
| `assets/{stadium2,city2,aurora2,orbit2,lighthouse3,motor2,concert2}.jpg` | 1920 × 1080 scenes used as live video, backdrops and thumbnails |
| `assets/pair_qr_local.png` | the source-pairing QR, encoding `http://192.168.1.42:8484/pair?code=7KQ4MZ` |
| `assets/pair_qr_adb.png` | the ADB-pairing QR, encoding `http://192.168.1.42:8484/adb?code=7KQ4MZ` |
| `assets/pair_qr_remote.png` | the phone-remote QR, encoding `http://192.168.1.42:8484/remote?code=7KQ4MZ` |

These images were generated for the mocks. They're fine as preview and test fixtures, but don't ship them as real channel branding.

### C. Board index

| # | Screen | Export name (`screens/<name>.jpg`, `boards/<name>.html`) | Canvas board (Refined page) |
|---|---|---|---|
| 01 | Home | `01-home` | Home-v2 |
| 02 | Home rows and apps | `02-home-rows` | HomeRows-v2 |
| 03 | Search | `03-search` | Search-v2 |
| 04 | Apps (move mode) | `04-apps` | Apps-v2 |
| 05 | Who's Watching | `05-profiles` | Profiles-v2 |
| 06 | Screensaver | `06-screensaver` | Screensaver-v2 |
| 07 | Live TV | `07-live-tv` | LiveTV-v2 |
| 08 | TV Guide | `08-guide` | Guide-v2 |
| 09 | Player, live overlay | `09-player` | Player-v2 |
| 10 | Player Options | `10-player-options` | PlayerMenu-v2 |
| 11 | Zap banner and reminder | `11-zap-banner-and-reminder` | Zap-v2 |
| 12 | Multiview | `12-multiview` | Multiview-v2 |
| 13 | Movies | `13-movies` | Movies-v2 |
| 14 | Movie Detail | `14-movie-detail` | MovieDetail-v2 |
| 15 | Series | `15-series` | Series-v2 |
| 16 | Series Detail | `16-series-detail` | SeriesDetail-v2 |
| 17 | Recordings | `17-recordings` | Recordings-v2 |
| 18 | Catch-up | `18-catch-up` | CatchUp-v2 |
| 19 | Settings | `19-settings` | SettingsRoot-v2 |
| 20 | Portals & Playlists | `20-settings-portals-and-playlists` | SettingsPortals-v2 |
| 21 | Add a Source | `21-add-a-source` | AddSource-v2 |
| 22 | Launcher & Home | `22-settings-launcher-and-home` | SettingsLauncher-v2 |
| 23 | Home Screen setup | `23-home-screen-setup` | HomeSetup-v2 |
| 24 | Replace Google TV Home (ADB) | `24-replace-google-tv-home` | HomeSetupAdb-v2 |
| 25 | Parental PIN | `25-parental-pin` | ParentalPIN-v2 |
| 26 | UI kit and remote map | `26-ui-kit-and-remote-map` | UIKit-v2 |
| 27 | Settings — Playback | `27-settings-playback` | SettingsPlayback-v2 |
| 28 | Settings — Audio | `28-settings-audio` | SettingsAudio-v2 |
| 29 | Settings — Channels | `29-settings-channels` | SettingsChannels-v2 |
| 30 | Duplicate Numbers | `30-duplicate-numbers` | ChannelNumbers-v2 |
| 31 | Settings — Rewind and Local Catch-up | `31-rewind-and-local-catch-up` | SettingsRewind-v2 |
| 32 | Settings — Continue Watching | `32-settings-continue-watching` | SettingsContinue-v2 |
| 33 | Phone Remote | `33-phone-remote` | PhoneRemote-v2 |
| 34 | Settings — Recording | `34-settings-recording` | SettingsRecording-v2 |

**Canvas layout (Refined page):**
- **Row 1, launcher:** Home, Home rows, Search, Apps, Who's Watching, Screensaver.
- **Row 2, Live TV:** Live TV, Guide, Player, Player Options, Zap, Multiview.
- **Row 3, Movies and Series:** Movies, Movie Detail, Series, Series Detail.
- **Row 4:** Recordings, Catch-up.
- **Row 5, setup and system:** Settings, Portals & Playlists, Add a Source, Launcher & Home, Home Screen setup, Replace Google TV Home, Parental PIN, UI kit.
- **Row 6, playback:** Playback, Audio.
- **Row 7, channels, local catch-up and Continue Watching:** Channels, Duplicate Numbers, Rewind and Local Catch-up, Continue Watching.
- **Row 8, phone remote and recordings for Jellyfin:** Phone Remote, Recording.

Boards changed in 1.3: Home rows (a Jellyfin item in Continue Watching), Settings (a Channels row), Playback (Rewind and Local Catch-up replaces Pause Live TV) and Launcher & Home (Home Rows opens Continue Watching).

Boards changed in 1.4: Audio (a Channel Volume Levels row) and Settings (Recording and Remote & Buttons now open their boards).

### D. Sources consulted (October 2026)

- Android Developers, TV layouts and overscan (48 dp sides, 27 dp top and bottom): <https://developer.android.com/design/ui/tv/guides/styles/layouts>
- Android Developers, Compose for TV release notes (tv-material 1.1.0 and tv-foundation 1.0.0, 6 May 2026; TV lazy layouts removed): <https://developer.android.com/jetpack/androidx/releases/tv>
- Android Developers Blog, "Media3 1.10 is out" (30 March 2026): <https://android-developers.googleblog.com/2026/03/media3-110-is-out.html>
- Android Developers, foreground service timeouts (`dataSync` / `mediaProcessing` 6 h per 24 h): <https://developer.android.com/develop/background-work/services/fg-service-timeout>
- Google Play Console Help, use of the AccessibilityService API (only relevant if Plasma ever ships on Play): <https://support.google.com/googleplay/android-developer/answer/10964491>
- Android Authority, a Google TV update blocks Projectivy's override (16 March 2026): <https://www.androidauthority.com/google-tv-update-blocks-projectivy-override-3649445/>
- Freya, replacing the Google TV launcher over ADB (packages and soft-brick warning): <https://freya.cat/blog/post/2025-03-11>
- Aerial Views README, setting a third-party screensaver on Google TV over ADB: <https://github.com/mouthofadesert/aerialviews>
- UniPlayer, M3U attribute and catch-up conventions: <https://uniplayer.net/docs/specs/m3u/>
- StalkerTalker `session.py`, Stalker headers, cookies, handshake and profile: <https://github.com/agsimeonov/StalkerTalker/blob/master/session.py>
- stalkerhek package documentation, portal URL forms and device fields: <https://pkg.go.dev/github.com/kidpoleon/stalkerhek>
- tuliprox issue 874, a portal returning strings for numbers (parse leniently): <https://github.com/euzu/tuliprox/issues/874>
- Fermata discussion 434, Xtream endpoints and URL formats: <https://github.com/AndreyPavlenko/Fermata/discussions/434>
- Esper, Android 13's restricted settings for sideloaded apps (session vs non-session installs, Allow restricted settings): <https://esper.io/blog/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis>
- libadb-android, an ADB client library for Android (pairing, TLS, shell streams; licence notes): <https://github.com/MuntashirAkon/libadb-android>
- Auto ADB Enable, an app that pairs with its own TV over localhost (tested on Chromecast with Google TV; Android 14+ random ports, `adb_wifi_enabled`): <https://github.com/mouldybread/adb-auto-enable>
- Android Developers, Android Debug Bridge (wireless debugging and `adb pair`): <https://developer.android.com/tools/adb>

**Playback engines and sound (added in 1.2)**
- Maven Central, `org.videolan.android:libvlc-all` 3.7.6 (LGPL-2.1): <https://central.sonatype.com/artifact/org.videolan.android/libvlc-all/3.7.6>
- vlc-android release tags (libvlc 3.7.7 tagged 1 October 2026; 4.0.0 still at EAP 29), via a release tracker: <https://releasealert.dev/github/videolan/vlc-android>
- VideoLAN forum, "there's no support for DV yet" (Dolby Vision plays with a pink and green tint): <https://forum.videolan.org/viewtopic.php?p=523798>
- vlc-commits, audiotrack passthrough through IEC 61937 with a fallback to per-codec encodings (AC-3, E-AC-3, DTS, DTS-HD, TrueHD): <https://mailman.videolan.org/pipermail/vlc-commits/2022-May/066804.html>
- vlc-commits, audiotrack checks encodings against the `encoded:<mask>` device: <https://mailman.videolan.org/pipermail/vlc-commits/2017-March/040164.html>
- vlc-android commit with the libVLC `MediaPlayer.setAudioOutputDevice()` Javadoc (`encoded`, `encoded:ENCODING_FLAGS_MASK`, `setAudioDigitalOutputEnabled`): <https://code.videolan.org/videolan/vlc-android/commit/bfa84f3c0fa873dca6b4bef27a1abb9c514b1a22>
- VLC issue 18112, a receiver that reports DTS-HD but plays silence; request for per-codec passthrough: <https://code.videolan.org/videolan/vlc/-/issues/18112>
- VideoLAN wiki, the timeshift module: <https://wiki.videolan.org/Documentation:Modules/timeshift>
- vlc-devel, the `input-timeshift-path` and `input-timeshift-granularity` options: <https://mailman.videolan.org/pipermail/vlc-devel/2008-November/053011.html>
- Maven Central, `dev.jdtech.mpv:libmpv` 1.0.0 (MIT wrapper, Findroid): <https://central.sonatype.com/artifact/dev.jdtech.mpv/libmpv>
- Maven Central, `io.github.abdallahmehiz:mpv-android-lib` 0.1.12 (MIT wrapper, mpvKt): <https://central.sonatype.com/artifact/io.github.abdallahmehiz/mpv-android-lib>
- mpv pull request 5589, `ao_audiotrack` with passthrough through IEC 61937 on API 24+: <https://github.com/mpv-player/mpv/pull/5589>
- BuzzTV libmpv build, pull request 5 (merged 28 September 2026): stock `ao_audiotrack` never passed E-AC-3, DTS-HD or TrueHD through: <https://github.com/BuzzTV-Dev/libmpv-android-video-build/pull/5>
- mpv discussion 14199, `vo=mediacodec_embed` can't fall back to software decoding; `--vd-lavc-software-fallback`: <https://github.com/mpv-player/mpv/discussions/14199>
- mpv, the `lavcac3enc` filter (encode multichannel audio to AC-3 for S/PDIF): <https://git.tjdev.de/mirror/mpv/commit/66abcc42af42d848725974c1a08fd43ce2053f78>
- mpv issue 12703, `audio-spdif` and Atmos or DTS:X: <https://github.com/mpv-player/mpv/issues/12703>
- Android Developers, audio capabilities on TV (`getDirectProfilesForAttributes`, `isDirectPlaybackSupported`, routing changes): <https://developer.android.com/training/tv/playback/audio-capabilities>
- Android Developers, `Surface` reference (`setFrameRate`): <https://developer.android.com/reference/android/view/Surface>
- Phoronix, Plasma Bigscreen in the Plasma 6.7 beta (14 May 2026): <https://phoronix.com/news/plasma-6.7-beta-big-screen>
- Wikipedia, Plasma Bigscreen: <https://en.wikipedia.org/wiki/Plasma_Bigscreen>

**Channels, local catch-up and other apps (added in 1.3)**
- Make Tech Easier, Projectivy Launcher review (7 June 2025): it "asks for permission to read all TV listings on your device": <https://maketecheasier.com/use-projectivy-launcher-android-tv>
- Projectivy Launcher, feature list ("Watch Next and your favorite apps"): <https://projectivylauncher.com/>
- AOSP TvProvider history, the `READ_TV_LISTINGS` permission "that allows EPG apps to access the TV listings from all channel sources": <https://android-git.linaro.org/platform/packages/providers/TvProvider.git/log/AndroidManifest.xml?showmsg=1>
- `TvContract` source, `PERMISSION_READ_TV_LISTINGS` (hidden): <https://code.yawk.at/android/android-9.0.0_r35/android/media/tv/TvContract.java>
- Android Developers, channels on the home screen and the Watch Next row: <https://developer.android.com/training/tv/discovery/home-screen>
- Android Developers codelab, the Play Next row ("The content provider returns all programs that our app has added"): <https://developer.android.com/codelabs/tv-watch-next>
- Android Developers, Engage SDK continue watching for Google TV: <https://developer.android.com/guide/playcore/engage/tv/continue-watching>
- Jellyfin Kotlin SDK, authentication and Quick Connect: <https://kotlin-sdk.jellyfin.org/guide/authentication.html>
- Jellyfin Kotlin SDK, `PlayStateApi` (playback start, progress and stop): <https://kotlin-sdk.jellyfin.org/dokka/jellyfin-api/org.jellyfin.sdk.api.operations/-play-state-api/index.html>
- Maven Central, `org.jellyfin.sdk:jellyfin-core` (LGPL-3.0): <https://central.sonatype.com/artifact/org.jellyfin.sdk/jellyfin-core/>
- Jellyfin docs, networking (client discovery on UDP 7359): <https://jellyfin.org/docs/general/post-install/networking/>
- 9to5Google, Android 14 for TV (15 May 2024): energy modes, and picture-in-picture on select TVs: <https://9to5google.com/2024/05/15/android-14-for-tvs/>

**Phone remote, recordings for Jellyfin and other 1.4 additions**
- Jellyfin docs, naming shows (`Series/Season 01/Series S01E01`): <https://jellyfin.org/docs/general/server/media/shows/>
- Jellyfin docs, naming movies (`Movie (Year)/Movie (Year)`): <https://jellyfin.org/docs/general/server/media/movies/>
- Jellyfin docs, NFO files (`tvshow.nfo`, episode and `movie.nfo` files): <https://jellyfin.org/docs/general/server/metadata/nfo>
- Maven Central, `com.hierynomus:smbj` (SMB 2 and 3 client for Java, Apache-2.0): <https://central.sonatype.com/artifact/com.hierynomus/smbj>
- MDN, secure connections for web apps (service workers and installing need HTTPS; `localhost` is the exception): <https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Tutorials/CycleTracker/Secure_connection>
- Android API differences for level 33, `AccessibilityService` (`GLOBAL_ACTION_DPAD_UP`, `DOWN`, `LEFT`, `RIGHT` and `CENTER` added): <https://developer.android.com/sdk/api_diff/33/changes/android.accessibilityservice.AccessibilityService>

**Plasma Remote apps (added in 1.5)**
- JetBrains, Compose Multiplatform 1.8.0: Compose Multiplatform for iOS is stable (May 2025): <https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/>
- Kotlin docs, Compose Multiplatform compatibility and versions (1.12.2 the latest listed; iOS 14 minimum): <https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html>
- Apple, TN3179 "Understanding local network privacy" (iOS 14+; `NSLocalNetworkUsageDescription`, `NSBonjourServices`; Safari traffic exempt): <https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy>
- Android Developers, local network permission (`ACCESS_LOCAL_NETWORK`, enforced for apps targeting Android 17; `NsdManager` picker): <https://developer.android.com/privacy-and-security/local-network-permission>
- Android Developers, Google Play's target API requirement (API 36 for new apps and updates from 31 August 2026): <https://developer.android.com/google/play/requirements/target-sdk>
- Play Console Help, testing requirements for new personal developer accounts (12 testers for 14 days): <https://support.google.com/googleplay/android-developer/answer/14151465>
- Michael Tsai, App Review guideline 2.5.9 on volume buttons and other standard switches: <https://mjtsai.com/blog/?p=19485>
- MobiLoud, App Review guideline 4.2 on apps that are repackaged websites: <https://www.mobiloud.com/blog/app-store-review-guidelines-webview-wrapper>
- Engadget, "The GPL, the App Store, and you" (VLC's removal in 2011): <https://www.engadget.com/2011-01-09-the-gpl-the-app-store-and-you.html>
- Ktor docs, SSL on the server (configured on the Netty engine with `sslConnector`): <https://ktor.io/docs/server-ssl.html>
- GitHub Docs, Actions billing ("free for standard GitHub-hosted runners in public repositories"): <https://docs.github.com/en/actions/reference/usage-limits-billing-and-administration>
- androidtvremote2, Android TV Remote protocol v2 in Python ("the same protocol the Google TV mobile app is using"; Apache-2.0): <https://github.com/tronikos/androidtvremote2>
- AndroidTVRemoteControl, the same protocol in Swift for iOS (MIT): <https://cocoapods.org/pods/AndroidTVRemoteControl>

### E. Idea backlog (not approved)

None of these is in scope until the owner says so (9.2, item 14). Each one reuses something Plasma already has. The seven ideas the owner approved in 1.4 have moved into the brief (9.1).

| Idea | What it does | Builds on | Size |
|---|---|---|---|
| **More than one Favorites list** | User lists such as Sports, News and Kids, each with its own order and numbering, shown as groups. | 5.2 | medium |
| **Keyword reminders** | "Remind me whenever Harbour City plays": a rule like a series recording, but it only reminds. | 5.6, 5.7 | small |
| **Multiview from the Guide** | Tick up to 4 programmes in the Guide and open them together. | 4.8, 4.12 | small |
| **"Live sports now" Home row** | Favorite channels' live programmes in sports categories, with time left. | EPG categories | small |
| **Quick-launch on number keys** | Long-press 1–9 opens a chosen app. | Remote & Buttons | small |
| **Kids viewing time** | A daily limit for Kids profiles, with a calm end screen. | profiles, PIN | medium |
| **"Still watching?"** (new in 1.4) | After 2, 4 or 6 hours without a button press, ask before carrying on, and stop like standby if nobody answers. For boxes that can't tell when the TV was switched off with the TV's own remote (5.20). | standby handling | small |
| **Phone app extras** (new in 1.5) | What's playing on the iPhone lock screen (a Live Activity) and in an Android notification, with CH ± buttons; home-screen widgets; voice shortcuts ("Plasma, channel 105") through Siri Shortcuts and Android's App Actions; a remote on Apple Watch and Wear OS. | the Plasma Remote apps (5.22) | small to medium each |
