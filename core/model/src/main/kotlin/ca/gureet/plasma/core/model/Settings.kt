// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * The settings Plasma acts on, by the keys Settings stores them under. Settings keeps each as the
 * index of the chosen option, in the order its sheet lists them, or 0 for On and 1 for Off for a
 * switch (brief 4.19); the enums below list their options in those orders.
 */
object SettingKeys {
    // Playback (brief 4.27)
    const val PLAYER_ENGINE = "engine"
    const val IF_STREAM_FAILS = "ifFails"
    const val HDR = "hdr"

    /** Check Movies for Dolby Vision (#113): when a movie its provider didn't probe is read first. */
    const val PROBE_MOVIES = "probeMovies"
    const val CONNECTION_TIMEOUT = "timeout"
    const val DECODING = "decoding"
    const val DEINTERLACING = "deinterlacing"
    const val FRAME_RATE = "frameRate"
    const val BUFFER = "buffer"
    const val FROM_START = "fromStart"
    const val STREAM_HUB = "streamHub"
    const val SUBTITLES = "subtitles"
    const val NEXT_EPISODE = "nextEpisode"
    const val TUNNELING = "tunneling"

    /** Restart Frozen Streams (the owner's #91): a live channel whose picture and sound stop is opened again. */
    const val RESTART_FROZEN = "restartFrozen"

    /** Up/Down in Player (4.27): they change channel, or show the channel list. */
    const val UP_DOWN = "upDown"

    /** Show Stream Stats (Playback › Advanced, 4.27 and 5.4.9): the stream's numbers over the picture. */
    const val STREAM_STATS = "streamStats"

    /**
     * Show Freeze Watch (Playback › Advanced, the owner's ask of 2 November): what Restart Frozen
     * Streams sees and does, over every screen.
     */
    const val FREEZE_WATCH = "freezeWatch"

    /**
     * Save Player Logs (Playback › Advanced, 5.17): the players' own logs, kept for Export
     * Diagnostics while it's on.
     */
    const val PLAYER_LOGS = "playerLogs"

    /**
     * Fast Zap (Playback, 5.4.8): which channels the stream hub keeps ready. A choice since the
     * owner's answer of 2 November, kept under a key of its own: "fastZap" was the switch before.
     */
    const val FAST_ZAP = "fastZapMode"

    // Playback › Rewind and Local Catch-up (4.31)
    const val REWIND = "rewind"
    const val LOCAL_KEEP = "keep"
    const val LOCAL_TV_OFF = "tvOffRecording"
    const val SAVE_TO = "saveTo"
    const val STORAGE_LIMIT = "storageLimit"

    /** Storage Limit when it's the TV's own storage (4.31): a choice of its own. */
    const val STORAGE_LIMIT_TV = "storageLimitTv"

    /** Ask Who's Watching (5.11): the profiles when the TV wakes. */
    const val ASK_WHO = "askWho"

    /** Phone Remote (5.21): phones can be remotes. */
    const val PHONE_REMOTE = "phoneRemote"

    // Parental Controls (4.25 and 5.12).
    const val LOCK_ADULT = "lockAdult"
    const val AGE_LIMIT = "ageLimit"
    const val UNLOCK_FOR = "unlockFor"
    const val LOCK_SETTINGS = "lockSettings"

    // Recording (4.34)
    const val RECORD_TO = "recordTo"
    const val AFTER_COPYING = "afterCopying"
    const val JELLYFIN_NAMES = "jellyfinNames"
    const val START_EARLY = "startEarly"
    const val END_LATE = "endLate"
    const val SPORTS_EXTRA = "sportsExtra"
    const val DELETE_WATCHED = "deleteWatched"
    const val SPACE_LOW = "spaceLow"

    // Audio (4.28)
    const val AUDIO_OUTPUT = "audioOutput"
    const val LANGUAGE = "language"
    const val SKIP_DESCRIPTION = "skipDescription"

    /** Stereo Downmix (5.4.6): every sound plays as two channels, decoded on the TV. */
    const val DOWNMIX = "downmix"

    /** Night Mode (5.4.6): loud and quiet passages of decoded sound evened out. */
    const val NIGHT_MODE = "nightMode"

    /** Audio Delay (5.4.6): the sound moved later or earlier than the picture, for this TV. */
    const val AUDIO_DELAY = "audioDelay"

    /** Audio Delay's slider (#116): the delay itself, in ms, −500 to +500, in place of [AUDIO_DELAY]'s five choices. */
    const val AUDIO_DELAY_MS = "audioDelayMs"

    /** A channel's own Audio Delay (#116), in ms, added to this TV's: the key, with the channel's id after it. */
    const val CHANNEL_AUDIO_DELAY = "channel.audioDelay:"

    /** Where [channelId]'s own Audio Delay is kept. */
    fun channelAudioDelay(channelId: String): String = CHANNEL_AUDIO_DELAY + channelId

    // Portals & Playlists (4.20)
    const val REFRESH = "refresh"

    /** Channel Logos: the sources' own logos, or the guide's. */
    const val LOGOS = "logos"
    const val MERGE_DUPLICATES = "merge"
    const val FAIL_OVER = "failOver"
    const val REMEMBER_WORKS = "rememberWorks"

    // Channels (4.29)
    const val PREFERRED_QUALITY = "quality"
    const val CHANNEL_NUMBERS = "channelNumbers"
    const val FAVORITE_NUMBERS = "favoriteNumbers"
    const val GROUP_NUMBERS = "groupNumbers"
    const val NUMBER_KEYS = "numberKeys"

    // Launcher & Home (4.22)
    const val HOME_BUTTON = "homeButton"

    /** Live Preview on Home (4.1): the last channel behind Home, muted, with its sound, or not at all. */
    const val LIVE_PREVIEW = "livePreview"
    const val TV_ON = "tvOn"
    const val TV_OFF = "tvOff"
    const val SMALL_WINDOW = "smallWindow"
    const val ANDROID_APPS = "androidApps"
    const val SCREENSAVER = "screensaver"

    // About (4.19)
    const val CHECK_UPDATES = "checkUpdates"

    // Launcher & Home › Home Rows › Continue Watching (4.32)
    const val INCLUDE_APPS = "includeApps"
    const val PLAY_JELLYFIN_IN = "playJellyfinIn"
    const val NEXT_EPISODES = "nextEpisodes"
    const val HIDE_OLDER = "hideOlder"

    /** Kids Profiles See (4.32): what Continue Watching shows on a kids profile. */
    const val KIDS_SEE = "kidsSee"

    // Remote & Buttons (5.16)

    /** Long-Press OK: holding OK opens an item's options, as Menu does, or does nothing more than OK. */
    const val LONG_PRESS_OK = "longPressOk"

    /** Number Entry Delay (4.11): how long after its last digit a typed number tunes. */
    const val NUMBER_DELAY = "numberDelay"

    /** Channel Up Goes To: the next channel number, or the previous one. */
    const val CHANNEL_UP = "channelUp"

    // Appearance (5.16)
    const val ACCENT = "accent"
    const val CLOCK = "clock"
    const val TEXT_SIZE = "textSize"
    const val REDUCE_MOTION = "reduceMotion"
    const val SHOW_NUMBERS = "showNumbers"

    /** Launcher & Home › Show TV Guide (the owner's choice, 28 October): the top bar's Guide tab. */
    const val SHOW_GUIDE = "showGuide"

    /**
     * Launcher & Home › Home Rows (the owner's #80): each row's switch, under this and the row's id
     * ([HomeRow.key]). The rows' order is kept with the other lists, in the data layer's store.
     */
    const val HOME_ROW = "homeRow."

    // Launcher & Home › Home Rows › Upcoming Events (the owner's #80): what the row brings in.
    const val UPCOMING_MARKED = "upcomingMarked"
    const val UPCOMING_FAVORITES = "upcomingFavorites"
    const val UPCOMING_SPORTS = "upcomingSports"

    // TV Guide (5.3)

    /** Keep Past Days: how far back the guide goes. */
    const val GUIDE_PAST = "pastDays"

    /** Load Ahead: how far ahead the guide goes. */
    const val GUIDE_AHEAD = "aheadDays"

    /** Update Every Day At: when the guide loads each day. */
    const val GUIDE_UPDATE = "updateTime"

    /** Channel Matching: how the guide's channels are paired with the sources' channels. */
    const val GUIDE_MATCHING = "matching"

    /**
     * TV Guide › Live TV Details (the owner's choice, 30 October): whether what's on and coming up
     * under Live TV's picture is the focused channel's or the playing channel's.
     */
    const val LIVE_DETAILS = "liveDetails"

    /**
     * TV Guide › Channel List Shows (the owner's #137): whether Live TV's rows lead with what's on
     * or with the channel's name.
     */
    const val LIVE_LIST_SHOWS = "liveListShows"

    /**
     * Portals & Playlists › Replace Placeholder Posters (the owner's #81): a picture a provider gives
     * many of its titles is its placeholder, and the title's own poster shows instead.
     */
    const val PLACEHOLDER_POSTERS = "placeholderPosters"

    /** Multiview's last layout, which it opens in next time; not a row in Settings. */
    const val MULTIVIEW_LAYOUT = "multiviewLayout"
}

/** Player Engine (Settings › Playback, brief 5.4.3): the engine live TV starts with. */
enum class PlayerEngineChoice { Vlc, Mpv, AndroidPlayer }

/**
 * If a Stream Won't Play (5.4.3): which engines take over from one that can't play a stream. The
 * first tries the other of mpv and VLC, then the Android player (#119).
 */
enum class IfStreamFails { TryOtherThenAndroidPlayer, TryAndroidPlayerOnly, ShowError }

/**
 * HDR and Dolby Vision Movies (5.4.3, rule 3): which engine plays a movie or episode in Dolby
 * Vision or HDR10+.
 */
enum class HdrMovies { AndroidPlayer, SameAsPlayerEngine }

/**
 * Check Movies for Dolby Vision (#113): whether a movie or episode its provider didn't probe has
 * the start of its file read first, for HDR and Dolby Vision Movies. That opens it twice, which a
 * provider can refuse an account with one connection.
 */
enum class ProbeMovies {
    /** Only from a source whose account allows two connections or more: the default. */
    WithTwoConnections,
    Always,
    Never,
}

/** Connection Timeout (5.4.3): how long a stream may take to show its picture. */
enum class ConnectionTimeout(val millis: Long) {
    Short(5_000),
    Medium(10_000),
    Normal(15_000),
    Long(30_000),
}

/**
 * Restart Frozen Streams (Settings › Playback, the owner's #91): how long a live channel's picture
 * and sound may stand still, while its stream keeps coming, before Plasma opens it again; Off
 * (null) never does.
 */
enum class RestartFrozen(val millis: Long?) {
    FiveSeconds(5_000),
    TenSeconds(10_000),
    Off(null),
}

/**
 * Fast Zap (Settings › Playback, brief 5.4.8): the channels the stream hub keeps open next to the
 * one playing full screen, so changing to them starts at once. Each takes one more connection.
 */
enum class FastZap {
    Off,

    /** The next channel in the direction the last zap went: one more connection. */
    Ahead,

    /** The channels on both sides: two more. */
    BothWays,
}

/** Up/Down in Player (Settings › Playback, brief 4.27): what Up and Down do while a channel plays full screen. */
enum class UpDownInPlayer { ChangeChannel, ShowChannelList }

/** Number Entry Delay (Settings › Remote & Buttons, brief 4.11): how long a typed number waits for another digit. */
enum class NumberEntryDelay(val millis: Long) {
    OneSecond(1_000),
    OneAndAHalfSeconds(1_500),
    TwoSeconds(2_000),
    ThreeSeconds(3_000),
}

/** Channel Up Goes To (Settings › Remote & Buttons): where CH + takes a channel, and CH − the other way. */
enum class ChannelUpGoesTo { NextNumber, PreviousNumber }

/** Video Decoding (Settings › Playback, brief 5.4.5). */
enum class VideoDecoding { Hardware, HardwareCopyBack, Software }

/** Deinterlacing (5.4.5). */
enum class Deinterlacing { Auto, Off, Always }

/** Match Frame Rate (5.4.4): Plasma asks the TV for the video's own rate. */
enum class FrameRateMatch { Off, SeamlessOnly, Always }

/** Buffer Size (5.4.8), which is also how far live TV runs behind. */
enum class BufferSize(val millis: Int) {
    Minimal(500),
    Small(1_500),
    Medium(3_000),
    Large(6_000),
    ExtraLarge(12_000),
}

/** Audio Output (5.4.6). */
enum class AudioOutput { Auto, Passthrough, DecodeToPcm }

/**
 * Audio Delay (Settings › Audio, brief 5.4.6), in the order its sheet lists them: how much later
 * than the picture the sound plays, in ms; earlier when it's below 0.
 */
enum class AudioDelay(val millis: Int) {
    HundredEarlier(-100),
    FiftyEarlier(-50),
    None(0),
    FiftyLater(50),
    HundredLater(100),
}

/** Audio Delay's slider (brief 4.28; #116): −500 to +500 ms in 10 ms steps, for this TV and for a channel. */
object AudioDelays {
    const val MIN_MS = -500
    const val MAX_MS = 500
    const val STEP_MS = 10

    /** [ms] on the slider: within its ends, on a step. */
    fun clamp(ms: Int): Int = (ms.coerceIn(MIN_MS, MAX_MS) / STEP_MS) * STEP_MS
}

/** Subtitles and Teletext (Settings › Playback, brief 5.4.7). */
enum class SubtitleMode { Off, On, ForcedOnly }

/**
 * Preferred Language (Settings › Audio, brief 5.4.6): the languages tracks are picked in, in order.
 * "Original" is the stream's own default track.
 */
enum class PreferredLanguage(val languages: List<String>) {
    EnglishThenOriginal(listOf("en", ORIGINAL_LANGUAGE)),
    Original(listOf(ORIGINAL_LANGUAGE)),
    English(listOf("en")),
}

/** In [PreferredLanguage.languages]: the stream's own default track. */
const val ORIGINAL_LANGUAGE = "original"

/** Preferred Quality (5.2): which stream of a merged channel plays first. */
enum class PreferredQuality { Fhd, Uhd, Hd }

/**
 * Rewind Buffer (Settings › Playback › Rewind and Local Catch-up, brief 5.5): Off, or written from
 * a pause or always, kept for [hours].
 */
enum class RewindBuffer(val always: Boolean, val hours: Int) {
    Off(always = false, hours = 0),
    WhenPausedOneHour(always = false, hours = 1),
    WhenPausedTwoHours(always = false, hours = 2),
    AlwaysOneHour(always = true, hours = 1),
    AlwaysTwoHours(always = true, hours = 2),
    AlwaysFourHours(always = true, hours = 4),
}

/** Save To (4.31): where the rewind buffer and local catch-up go, the USB drive when there is one. */
enum class SaveTo { UsbDrive, ThisTv }

/** Record To (Settings › Recording, 4.34): the USB drive when one is connected, this TV, or a network folder. */
enum class RecordTo { UsbDrive, ThisTv, NetworkFolder }

/**
 * After Copying (4.34): a recording's file on the drive, once its copy is on the network folder,
 * stays a week, stays until the room's needed, or goes at once.
 */
enum class AfterCopying { KeepWeek, UntilSpaceNeeded, Delete }

/** When Space Runs Low (4.34): the oldest watched recordings go, or recording stops. */
enum class SpaceLow { DeleteOldestWatched, StopRecording }

/**
 * Live Preview on Home (Settings › Launcher & Home, brief 4.1): the last channel plays behind
 * Home muted, or with its sound, or Home shows its programme's artwork instead.
 */
enum class LivePreview { Muted, WithSound, Off }

/** Home Button (Settings › Launcher & Home, brief 4.22): what pressing Home opens. */
enum class HomeButtonChoice { PlasmaHome, LastChannel, ChannelList, Guide }

/** When the TV Turns On (5.13). */
enum class TvTurnsOn { LastChannel, Home, Guide }

/** When the TV Turns Off (5.20). */
enum class TvTurnsOff { StopAfterAMinute, StopAtOnce, KeepPlaying }

/** Play Jellyfin Items In (Continue Watching, brief 5.19): Plasma's own player, or the Jellyfin app if the TV has it. */
enum class PlayJellyfinIn { Plasma, JellyfinApp }

/**
 * Live TV Details (Settings › TV Guide, the owner's choice of 30 October): under Live TV's
 * picture, what's on and coming up on the channel focus is on, or on the one playing. Moving
 * through the list never changes the picture either way; OK does.
 */
enum class LiveDetails { FocusedChannel, PlayingChannel }

/**
 * Channel List Shows (Settings › TV Guide › Live TV, the owner's #137): each row of Live TV's list
 * leads with what's on, from the guide, with its times under it (board 07), or with the channel's
 * name, with what's on under it. A channel with no guide shows its name either way.
 */
enum class LiveListShows { WhatsOn, ChannelNames }

/**
 * Channel Logos (Settings › Portals & Playlists, brief 4.20): the logos the sources give their
 * channels, or the ones the guide gives its channels; either way, the other where one's missing.
 */
enum class ChannelLogos { FromPortal, FromGuide }

/** Keep Past Days (Settings › TV Guide, brief 5.3): how many days back the guide keeps. */
enum class KeepPastDays(val days: Int) {
    One(1),
    Three(3),
    Seven(7),
    Fourteen(14),
}

/** Load Ahead (Settings › TV Guide, 5.3): how many days ahead the guide loads. */
enum class LoadAhead(val days: Int) {
    Three(3),
    Seven(7),
    Fourteen(14),
}

/** Update Every Day At (Settings › TV Guide, 5.3): the hour the guide loads each day, local time. */
enum class GuideUpdateAt(val hour: Int) {
    Two(2),
    Four(4),
    Six(6),
}

/**
 * Channel Matching (Settings › TV Guide, 5.3): a channel finds its guide channel by the guide id
 * its source gives it, then by name; or only one of the two.
 */
enum class ChannelMatching(val byGuideId: Boolean, val byName: Boolean) {
    Automatic(byGuideId = true, byName = true),
    ByGuideIdOnly(byGuideId = true, byName = false),
    ByNameOnly(byGuideId = false, byName = true),
}

/** Screensaver (Settings › Launcher & Home, brief 5.14): how long Plasma waits for a key first. */
enum class ScreensaverAfter(val minutes: Int?) {
    FiveMinutes(5),
    TenMinutes(10),
    ThirtyMinutes(30),
    Never(null),
}

/**
 * What's chosen in Settings, read with each setting's default: the defaults Settings shows when
 * nothing has been chosen.
 */
class SettingsChoices(private val chosen: Map<String, Int> = emptyMap()) {
    /** mpv unless chosen otherwise: the owner's #119, after it got over streams VLC froze on. */
    val playerEngine: PlayerEngineChoice get() = pick(SettingKeys.PLAYER_ENGINE, PlayerEngineChoice.Mpv)
    val ifStreamFails: IfStreamFails get() = pick(SettingKeys.IF_STREAM_FAILS, IfStreamFails.TryOtherThenAndroidPlayer)
    val hdrMovies: HdrMovies get() = pick(SettingKeys.HDR, HdrMovies.AndroidPlayer)

    /** Check Movies for Dolby Vision (#113): with two connections or more, unless chosen otherwise. */
    val probeMovies: ProbeMovies get() = pick(SettingKeys.PROBE_MOVIES, ProbeMovies.WithTwoConnections)
    val connectionTimeout: ConnectionTimeout get() = pick(SettingKeys.CONNECTION_TIMEOUT, ConnectionTimeout.Normal)

    /** Restart Frozen Streams (#91): after five seconds, unless chosen otherwise. */
    val restartFrozen: RestartFrozen get() = pick(SettingKeys.RESTART_FROZEN, RestartFrozen.FiveSeconds)

    /** Show Stream Stats (Playback › Advanced, 5.4.9): off by default. */
    val showStreamStats: Boolean get() = switch(SettingKeys.STREAM_STATS, default = false)

    /** Show Freeze Watch (Playback › Advanced, the owner's ask of 2 November): off by default. */
    val showFreezeWatch: Boolean get() = switch(SettingKeys.FREEZE_WATCH, default = false)

    /** Fast Zap (Playback, 5.4.8): off unless chosen, since it uses the account's connections. */
    val fastZap: FastZap get() = pick(SettingKeys.FAST_ZAP, FastZap.Off)

    /** Save Player Logs (Playback › Advanced, 5.17): off by default. */
    val savePlayerLogs: Boolean get() = switch(SettingKeys.PLAYER_LOGS, default = false)

    /** Up/Down in Player (4.27): they change channel, unless chosen otherwise. */
    val upDownInPlayer: UpDownInPlayer get() = pick(SettingKeys.UP_DOWN, UpDownInPlayer.ChangeChannel)

    /** Long-Press OK (Remote & Buttons): holding OK opens an item's options, unless set to Nothing. */
    val longPressOk: Boolean get() = (chosen[SettingKeys.LONG_PRESS_OK] ?: 0) == 0

    /** Number Entry Delay (4.11): a second and a half, unless chosen otherwise. */
    val numberEntryDelay: NumberEntryDelay get() = pick(SettingKeys.NUMBER_DELAY, NumberEntryDelay.OneAndAHalfSeconds)

    /** Channel Up Goes To (Remote & Buttons): the next number, unless chosen otherwise. */
    val channelUpGoesTo: ChannelUpGoesTo get() = pick(SettingKeys.CHANNEL_UP, ChannelUpGoesTo.NextNumber)
    val decoding: VideoDecoding get() = pick(SettingKeys.DECODING, VideoDecoding.Hardware)
    val deinterlacing: Deinterlacing get() = pick(SettingKeys.DEINTERLACING, Deinterlacing.Auto)
    val frameRate: FrameRateMatch get() = pick(SettingKeys.FRAME_RATE, FrameRateMatch.SeamlessOnly)
    val buffer: BufferSize get() = pick(SettingKeys.BUFFER, BufferSize.Medium)

    /** Play Live TV Through Stream Hub (5.4.2), the kill switch. */
    val throughHub: Boolean get() = switch(SettingKeys.STREAM_HUB, default = true)

    /** Tunneled Playback (Advanced, 4.27): the Android player's video and sound kept in step by the TV, off by default. */
    val tunneling: Boolean get() = switch(SettingKeys.TUNNELING, default = false)

    /** Offer Watch from the Start: the hint for tuning in late (4.11). */
    val offerStartOver: Boolean get() = switch(SettingKeys.FROM_START, default = true)

    /** Auto-Play Next Episode (5.9): an episode's end counts down to the next, on by default. */
    val autoPlayNext: Boolean get() = switch(SettingKeys.NEXT_EPISODE, default = true)

    /** Continue Watching › Show Next Episodes (4.32): a watched episode's next one, on by default. */
    val continueNextEpisodes: Boolean get() = switch(SettingKeys.NEXT_EPISODES, default = true)

    /** Continue Watching › Include Other Apps (5.19): other apps' Play Next items join the row; off until turned on. */
    val includeOtherApps: Boolean get() = switch(SettingKeys.INCLUDE_APPS, default = false)

    /**
     * Continue Watching › Kids Profiles See (4.32 and 5.19): other apps' items too, or Plasma's own
     * only, as unless chosen.
     */
    val kidsSeeOtherApps: Boolean get() = (chosen[SettingKeys.KIDS_SEE] ?: 0) == 1

    /** Continue Watching › Play Jellyfin Items In (5.19): Plasma by default. */
    val playJellyfinIn: PlayJellyfinIn get() = pick(SettingKeys.PLAY_JELLYFIN_IN, PlayJellyfinIn.Plasma)

    /** Continue Watching › Hide Items Older Than (4.32), in days: 30 by default; null is never. */
    val hideOlderDays: Int?
        get() = HIDE_OLDER_DAYS.getOrElse(chosen[SettingKeys.HIDE_OLDER] ?: HIDE_OLDER_DEFAULT) { HIDE_OLDER_DAYS[HIDE_OLDER_DEFAULT] }
    val audioOutput: AudioOutput get() = pick(SettingKeys.AUDIO_OUTPUT, AudioOutput.Passthrough)

    /** Subtitles and Teletext (5.4.7), off by default. */
    val subtitles: SubtitleMode get() = pick(SettingKeys.SUBTITLES, SubtitleMode.Off)

    /** Preferred Language (5.4.6), "English, then original" by default. */
    val preferredLanguage: PreferredLanguage get() = pick(SettingKeys.LANGUAGE, PreferredLanguage.EnglishThenOriginal)

    /** Skip Audio Description (5.4.6), on by default. */
    val skipDescription: Boolean get() = switch(SettingKeys.SKIP_DESCRIPTION, default = true)

    /** Stereo Downmix (5.4.6), off by default. */
    val stereoDownmix: Boolean get() = switch(SettingKeys.DOWNMIX, default = false)

    /** Night Mode (5.4.6), off by default. */
    val nightMode: Boolean get() = switch(SettingKeys.NIGHT_MODE, default = false)

    /** Audio Delay (5.4.6): none by default. */
    val audioDelay: AudioDelay get() = pick(SettingKeys.AUDIO_DELAY, AudioDelay.None)

    /**
     * Audio Delay on the slider (brief 4.28; #116), in ms: −500 to +500, none by default. Until
     * it's moved, the row's choice from before (−100 to +100 ms) still counts.
     */
    val audioDelayMs: Int get() = AudioDelays.clamp(chosen[SettingKeys.AUDIO_DELAY_MS] ?: audioDelay.millis)

    /** [channelId]'s own Audio Delay (#116), in ms, added to [audioDelayMs]; none unless it has one. */
    fun channelAudioDelayMs(channelId: String): Int = AudioDelays.clamp(chosen[SettingKeys.channelAudioDelay(channelId)] ?: 0)
    val homeButton: HomeButtonChoice get() = pick(SettingKeys.HOME_BUTTON, HomeButtonChoice.PlasmaHome)

    /** Channel Logos (4.20): the sources' own by default. */
    val channelLogos: ChannelLogos get() = pick(SettingKeys.LOGOS, ChannelLogos.FromPortal)

    /** Keep Past Days (5.3): a week by default. */
    val keepPastDays: KeepPastDays get() = pick(SettingKeys.GUIDE_PAST, KeepPastDays.Seven)

    /** Load Ahead (5.3): a week by default. */
    val loadAhead: LoadAhead get() = pick(SettingKeys.GUIDE_AHEAD, LoadAhead.Seven)

    /** Update Every Day At (5.3): 04:00 by default. */
    val guideUpdateAt: GuideUpdateAt get() = pick(SettingKeys.GUIDE_UPDATE, GuideUpdateAt.Four)

    /** Channel Matching (5.3): by guide id, then by name, by default. */
    val channelMatching: ChannelMatching get() = pick(SettingKeys.GUIDE_MATCHING, ChannelMatching.Automatic)

    /** Live Preview on Home (4.1): muted by default. */
    val livePreview: LivePreview get() = pick(SettingKeys.LIVE_PREVIEW, LivePreview.Muted)
    val tvTurnsOn: TvTurnsOn get() = pick(SettingKeys.TV_ON, TvTurnsOn.LastChannel)
    val tvTurnsOff: TvTurnsOff get() = pick(SettingKeys.TV_OFF, TvTurnsOff.StopAfterAMinute)

    /** About › Check for Updates (5.18): Plasma looks for a new version once a day. */
    val checkUpdatesDaily: Boolean get() = switch(SettingKeys.CHECK_UPDATES, default = true)

    /** Rewind Buffer (5.5): When I pause, for an hour, unless chosen otherwise. */
    val rewindBuffer: RewindBuffer get() = pick(SettingKeys.REWIND, RewindBuffer.WhenPausedOneHour)

    val saveTo: SaveTo get() = pick(SettingKeys.SAVE_TO, SaveTo.UsbDrive)

    /**
     * Storage Limit (4.31): what local catch-up, and the rewind buffer, may take: 50 GB on a USB
     * drive, or [onTv], 8 GB of the TV's own, unless chosen otherwise.
     */
    fun storageLimitBytes(onTv: Boolean): Long = if (onTv) {
        STORAGE_LIMIT_TV_GB.getOrElse(chosen[SettingKeys.STORAGE_LIMIT_TV] ?: 2) { STORAGE_LIMIT_TV_GB[2] } * GB
    } else {
        STORAGE_LIMIT_GB.getOrElse(chosen[SettingKeys.STORAGE_LIMIT] ?: 2) { STORAGE_LIMIT_GB[2] } * GB
    }

    /** Local catch-up's Keep (5.5): the last 24 hours, unless chosen otherwise. */
    val localKeepHours: Int get() = LOCAL_KEEP_HOURS.getOrElse(chosen[SettingKeys.LOCAL_KEEP] ?: 2) { LOCAL_KEEP_HOURS[2] }

    /** While the TV Is Off (5.5): local catch-up pauses until it's on, rather than keep recording. */
    val pauseLocalWhileOff: Boolean get() = (chosen[SettingKeys.LOCAL_TV_OFF] ?: 0) == 1

    val recordTo: RecordTo get() = pick(SettingKeys.RECORD_TO, RecordTo.UsbDrive)

    /** Appearance › Accent, as its option's index: White (the default), Blue, Orange, Green. */
    val accent: Int get() = chosen[SettingKeys.ACCENT] ?: 0

    /** Appearance › Clock: 12-Hour instead of 24-Hour. */
    val twelveHourClock: Boolean get() = (chosen[SettingKeys.CLOCK] ?: 0) == 1

    /** Appearance › Text Size: Large instead of Default. */
    val largeText: Boolean get() = (chosen[SettingKeys.TEXT_SIZE] ?: 0) == 1

    /** Appearance › Reduce Motion (2.6): off by default. */
    val reduceMotion: Boolean get() = switch(SettingKeys.REDUCE_MOTION, default = false)

    /** Appearance › Show Channel Numbers: on by default. */
    val showChannelNumbers: Boolean get() = switch(SettingKeys.SHOW_NUMBERS, default = true)

    /** Launcher & Home › Show TV Guide: the Guide tab in the top bar and the Guide on Home's dock, on by default. */
    val showGuide: Boolean get() = switch(SettingKeys.SHOW_GUIDE, default = true)

    /** TV Guide › Live TV Details: the focused channel's, unless chosen otherwise. */
    val liveDetails: LiveDetails get() = pick(SettingKeys.LIVE_DETAILS, LiveDetails.FocusedChannel)

    /** TV Guide › Channel List Shows: what's on, unless chosen otherwise. */
    val liveListShows: LiveListShows get() = pick(SettingKeys.LIVE_LIST_SHOWS, LiveListShows.WhatsOn)

    /** Portals & Playlists › Replace Placeholder Posters: on by default. */
    val replacePlaceholderPosters: Boolean get() = switch(SettingKeys.PLACEHOLDER_POSTERS, default = true)

    /** Multiview's last layout, by its place in Multiview's toolbar; null until one's picked. */
    val multiviewLayout: Int? get() = chosen[SettingKeys.MULTIVIEW_LAYOUT]

    /** Phone Remote (5.21): the phone remote's page answers; on by default. */
    val phoneRemote: Boolean get() = switch(SettingKeys.PHONE_REMOTE, default = true)

    /** Ask Who's Watching (5.11): Who's Watching shows when the TV wakes; off by default. */
    val askWhosWatching: Boolean get() = switch(SettingKeys.ASK_WHO, default = false)

    /** Lock Adult Groups (5.12): groups whose names say they're adult need the PIN, on by default. */
    val lockAdultGroups: Boolean get() = switch(SettingKeys.LOCK_ADULT, default = true)

    /** Age Limit (5.12): programs rated above this many years need the PIN; 18+ by default, null when Off. */
    val ageLimit: Int? get() = AGE_LIMITS.getOrElse(chosen[SettingKeys.AGE_LIMIT] ?: AGE_LIMIT_DEFAULT) { AGE_LIMITS[AGE_LIMIT_DEFAULT] }

    /** Unlock For (4.25): how long the PIN opens a program, as its option's index: this program by default. */
    val unlockFor: Int get() = chosen[SettingKeys.UNLOCK_FOR] ?: 0

    /** Lock Settings (5.12): Settings asks for the PIN before it opens, off by default. */
    val lockSettings: Boolean get() = switch(SettingKeys.LOCK_SETTINGS, default = false)

    /** Name Files for Jellyfin (5.6): folders, info files and artwork on the network folder, unless turned off. */
    val jellyfinNames: Boolean get() = switch(SettingKeys.JELLYFIN_NAMES, true)

    /** After Copying (5.6): the drive's copy stays a week, unless chosen otherwise. */
    val afterCopying: AfterCopying get() = pick(SettingKeys.AFTER_COPYING, AfterCopying.KeepWeek)

    /** Start Early (5.6): a minute, unless chosen otherwise. */
    val startEarlyMinutes: Int get() = START_EARLY_MINUTES.getOrElse(chosen[SettingKeys.START_EARLY] ?: 1) { START_EARLY_MINUTES[1] }

    /** End Late: five minutes, unless chosen otherwise. */
    val endLateMinutes: Int get() = END_LATE_MINUTES.getOrElse(chosen[SettingKeys.END_LATE] ?: 1) { END_LATE_MINUTES[1] }

    /** Extra Time for Sports: 15 more minutes at the end of sport, unless chosen otherwise. */
    val sportsExtraMinutes: Int get() = SPORTS_EXTRA_MINUTES.getOrElse(chosen[SettingKeys.SPORTS_EXTRA] ?: 1) { SPORTS_EXTRA_MINUTES[1] }

    /** Delete Watched Recordings (5.6): after this many days, or never (null, the default). */
    val deleteWatchedDays: Int? get() = DELETE_WATCHED_DAYS.getOrElse(chosen[SettingKeys.DELETE_WATCHED] ?: 2) { null }

    val spaceLow: SpaceLow get() = pick(SettingKeys.SPACE_LOW, SpaceLow.DeleteOldestWatched)

    /** Keep Watching in a Small Window (5.20): when opening another app, unless set to Never. */
    val keepWatchingSmall: Boolean get() = (chosen[SettingKeys.SMALL_WINDOW] ?: 0) == 0

    /** Show Android Apps (5.13): Off takes away Home's Apps row and the Apps tab. */
    val showAndroidApps: Boolean get() = switch(SettingKeys.ANDROID_APPS, default = true)

    /** Home Rows (the owner's #80): whether [row]'s switch is on. */
    fun rowOn(row: HomeRow): Boolean = switch(row.key, row.onByDefault)

    /**
     * The rows Home shows, in [order]: those whose switches are on, and Apps only while Show
     * Android Apps is on too, whatever its own switch says (the owner's #80).
     */
    fun homeRows(order: List<HomeRow>): List<HomeRow> = order.filter { rowOn(it) && (it != HomeRow.Apps || showAndroidApps) }

    /** Home Rows › Upcoming Events (the owner's #80): the kinds of program it brings in, all unless turned off. */
    val upcomingKinds: Set<UpcomingKind> get() = UpcomingKind.entries.filterTo(LinkedHashSet()) { switch(it.key, default = true) }

    /** Screensaver (5.14): when Plasma's own screensaver shows, if ever. */
    val screensaverAfter: ScreensaverAfter get() = pick(SettingKeys.SCREENSAVER, ScreensaverAfter.TenMinutes)

    /**
     * Portals & Playlists › Refresh (5.1): how often sources' channels refresh by themselves, in
     * hours, every 12 by default; 0 is only when asked.
     */
    val refreshHours: Int get() = REFRESH_HOURS.getOrElse(chosen[SettingKeys.REFRESH] ?: 1) { REFRESH_HOURS[1] }

    /** Merge Duplicate Channels (5.1): the same channel from several sources is one, on by default. */
    val mergeDuplicates: Boolean get() = switch(SettingKeys.MERGE_DUPLICATES, default = true)

    /** Fail Over to Backup (5.1): a source that fails hands over to the next, on by default. */
    val failOver: Boolean get() = switch(SettingKeys.FAIL_OVER, default = true)

    /** Remember What Works per Channel (5.1), on by default. */
    val rememberWorks: Boolean get() = switch(SettingKeys.REMEMBER_WORKS, default = true)

    /** Preferred Quality (5.2): "FHD, then HD" first, then "4K, then FHD" and "HD, then FHD". */
    val preferredQuality: PreferredQuality get() = pick(SettingKeys.PREFERRED_QUALITY, PreferredQuality.Fhd)

    /** Channel Numbers: "In playlist order" is the second option; the others start from the playlists' numbers. */
    val numbersInPlaylistOrder: Boolean get() = chosen[SettingKeys.CHANNEL_NUMBERS] == 1

    /** Favorites and groups: "Count from 1" is the first option, "Keep channel numbers" the second. */
    val favoritesCountFromOne: Boolean get() = (chosen[SettingKeys.FAVORITE_NUMBERS] ?: 0) == 0
    val groupsCountFromOne: Boolean get() = (chosen[SettingKeys.GROUP_NUMBERS] ?: 1) == 0

    /** Number Keys: "Full list only" is the second option. */
    val numberKeysFullListOnly: Boolean get() = chosen[SettingKeys.NUMBER_KEYS] == 1

    companion object {
        /** Refresh's options in hours, in Settings' order: 6, 12, 24, then only when asked. */
        val REFRESH_HOURS: List<Int> = listOf(6, 12, 24, 0)

        /** Storage Limit's options on a USB drive, in gigabytes. */
        val STORAGE_LIMIT_GB: List<Long> = listOf(10, 25, 50, 100, 250, 500)

        /** Age Limit's options, in years, in Settings' order: Off, 12+, 16+, 18+. */
        val AGE_LIMITS: List<Int?> = listOf(null, 12, 16, 18)
        const val AGE_LIMIT_DEFAULT = 3

        /** Storage Limit's options on the TV, in gigabytes. */
        val STORAGE_LIMIT_TV_GB: List<Long> = listOf(2, 4, 8, 16)

        /** Keep's choices for local catch-up, in hours, in Settings' order: 6, 12, 24, 48 hours, 7 days. */
        val LOCAL_KEEP_HOURS: List<Int> = listOf(6, 12, 24, 48, 168)

        /** Recording's padding options, in minutes, in Settings' order. */
        val START_EARLY_MINUTES: List<Int> = listOf(0, 1, 2, 5, 10)
        val END_LATE_MINUTES: List<Int> = listOf(0, 5, 10, 15, 30)
        val SPORTS_EXTRA_MINUTES: List<Int> = listOf(0, 15, 30, 60)

        /** Delete Watched Recordings' options, in days, in Settings' order: 7, 30, never. */
        val DELETE_WATCHED_DAYS: List<Int?> = listOf(7, 30, null)
        private const val GB = 1_000_000_000L

        /** Hide Items Older Than's choices, in days, in the sheet's order. */
        val HIDE_OLDER_DAYS: List<Int?> = listOf(7, 14, 30, 90, null)
        private const val HIDE_OLDER_DEFAULT = 2
    }

    private inline fun <reified E : Enum<E>> pick(key: String, default: E): E =
        chosen[key]?.let { enumValues<E>().getOrNull(it) } ?: default

    private fun switch(key: String, default: Boolean): Boolean = chosen[key]?.let { it == 0 } ?: default

    /** Every choice made, by key, as Export Diagnostics lists them (5.17). */
    val chosenByKey: Map<String, Int> get() = chosen

    override fun equals(other: Any?): Boolean = other is SettingsChoices && other.chosen == chosen

    override fun hashCode(): Int = chosen.hashCode()
}
