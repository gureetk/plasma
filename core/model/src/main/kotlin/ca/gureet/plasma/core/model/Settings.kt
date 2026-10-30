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

    // Portals & Playlists (4.20)
    const val REFRESH = "refresh"
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

    // Appearance (5.16)
    const val ACCENT = "accent"
    const val CLOCK = "clock"
    const val TEXT_SIZE = "textSize"
    const val REDUCE_MOTION = "reduceMotion"
    const val SHOW_NUMBERS = "showNumbers"

    /** Launcher & Home › Show TV Guide (the owner's choice, 28 October): the top bar's Guide tab. */
    const val SHOW_GUIDE = "showGuide"

    /** Multiview's last layout, which it opens in next time; not a row in Settings. */
    const val MULTIVIEW_LAYOUT = "multiviewLayout"
}

/** Player Engine (Settings › Playback, brief 5.4.3): the engine live TV starts with. */
enum class PlayerEngineChoice { Vlc, Mpv, AndroidPlayer }

/** If a Stream Won't Play (5.4.3): which engines take over from one that can't play a stream. */
enum class IfStreamFails { TryMpvThenAndroidPlayer, TryAndroidPlayerOnly, ShowError }

/**
 * HDR and Dolby Vision Movies (5.4.3, rule 3): which engine plays a movie or episode in Dolby
 * Vision or HDR10+.
 */
enum class HdrMovies { AndroidPlayer, SameAsPlayerEngine }

/** Connection Timeout (5.4.3): how long a stream may take to show its picture. */
enum class ConnectionTimeout(val millis: Long) {
    Short(5_000),
    Medium(10_000),
    Normal(15_000),
    Long(30_000),
}

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

/** Home Button (Settings › Launcher & Home, brief 4.22): what pressing Home opens. */
enum class HomeButtonChoice { PlasmaHome, LastChannel, ChannelList, Guide }

/** When the TV Turns On (5.13). */
enum class TvTurnsOn { LastChannel, Home, Guide }

/** When the TV Turns Off (5.20). */
enum class TvTurnsOff { StopAfterAMinute, StopAtOnce, KeepPlaying }

/** Play Jellyfin Items In (Continue Watching, brief 5.19): Plasma's own player, or the Jellyfin app if the TV has it. */
enum class PlayJellyfinIn { Plasma, JellyfinApp }

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
    val playerEngine: PlayerEngineChoice get() = pick(SettingKeys.PLAYER_ENGINE, PlayerEngineChoice.Vlc)
    val ifStreamFails: IfStreamFails get() = pick(SettingKeys.IF_STREAM_FAILS, IfStreamFails.TryMpvThenAndroidPlayer)
    val hdrMovies: HdrMovies get() = pick(SettingKeys.HDR, HdrMovies.AndroidPlayer)
    val connectionTimeout: ConnectionTimeout get() = pick(SettingKeys.CONNECTION_TIMEOUT, ConnectionTimeout.Normal)
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
    val homeButton: HomeButtonChoice get() = pick(SettingKeys.HOME_BUTTON, HomeButtonChoice.PlasmaHome)
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

    /** Launcher & Home › Show TV Guide: the Guide tab in the top bar, on by default. */
    val showGuide: Boolean get() = switch(SettingKeys.SHOW_GUIDE, default = true)

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

    override fun equals(other: Any?): Boolean = other is SettingsChoices && other.chosen == chosen

    override fun hashCode(): Int = chosen.hashCode()
}
