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
    const val ANDROID_APPS = "androidApps"
    const val SCREENSAVER = "screensaver"

    // Launcher & Home › Home Rows › Continue Watching (4.32)
    const val NEXT_EPISODES = "nextEpisodes"
    const val HIDE_OLDER = "hideOlder"
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

/** Home Button (Settings › Launcher & Home, brief 4.22): what pressing Home opens. */
enum class HomeButtonChoice { PlasmaHome, LastChannel, ChannelList, Guide }

/** When the TV Turns On (5.13). */
enum class TvTurnsOn { LastChannel, Home, Guide }

/** When the TV Turns Off (5.20). */
enum class TvTurnsOff { StopAfterAMinute, StopAtOnce, KeepPlaying }

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
