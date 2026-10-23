// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * The settings Plasma acts on, by the keys Settings stores them under. Settings keeps each as the
 * index of the chosen option, in the order its sheet lists them, or 0 for On and 1 for Off for a
 * switch (brief 4.19); the enums below list their options in those orders.
 */
object SettingKeys {
    // Playback (brief 4.27)
    const val DECODING = "decoding"
    const val DEINTERLACING = "deinterlacing"
    const val FRAME_RATE = "frameRate"
    const val BUFFER = "buffer"
    const val STREAM_HUB = "streamHub"

    // Audio (4.28)
    const val AUDIO_OUTPUT = "audioOutput"

    // Channels (4.29)
    const val CHANNEL_NUMBERS = "channelNumbers"
    const val FAVORITE_NUMBERS = "favoriteNumbers"
    const val GROUP_NUMBERS = "groupNumbers"
    const val NUMBER_KEYS = "numberKeys"

    // Launcher & Home (4.22)
    const val TV_ON = "tvOn"
    const val TV_OFF = "tvOff"
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

/** When the TV Turns On (5.13). */
enum class TvTurnsOn { LastChannel, Home, Guide }

/** When the TV Turns Off (5.20). */
enum class TvTurnsOff { StopAfterAMinute, StopAtOnce, KeepPlaying }

/**
 * What's chosen in Settings, read with each setting's default: the defaults Settings shows when
 * nothing has been chosen.
 */
class SettingsChoices(private val chosen: Map<String, Int> = emptyMap()) {
    val decoding: VideoDecoding get() = pick(SettingKeys.DECODING, VideoDecoding.Hardware)
    val deinterlacing: Deinterlacing get() = pick(SettingKeys.DEINTERLACING, Deinterlacing.Auto)
    val frameRate: FrameRateMatch get() = pick(SettingKeys.FRAME_RATE, FrameRateMatch.SeamlessOnly)
    val buffer: BufferSize get() = pick(SettingKeys.BUFFER, BufferSize.Medium)

    /** Play Live TV Through Stream Hub (5.4.2), the kill switch. */
    val throughHub: Boolean get() = switch(SettingKeys.STREAM_HUB, default = true)
    val audioOutput: AudioOutput get() = pick(SettingKeys.AUDIO_OUTPUT, AudioOutput.Passthrough)
    val tvTurnsOn: TvTurnsOn get() = pick(SettingKeys.TV_ON, TvTurnsOn.LastChannel)
    val tvTurnsOff: TvTurnsOff get() = pick(SettingKeys.TV_OFF, TvTurnsOff.StopAfterAMinute)

    /** Channel Numbers: "In playlist order" is the second option; the others start from the playlists' numbers. */
    val numbersInPlaylistOrder: Boolean get() = chosen[SettingKeys.CHANNEL_NUMBERS] == 1

    /** Favorites and groups: "Count from 1" is the first option, "Keep channel numbers" the second. */
    val favoritesCountFromOne: Boolean get() = (chosen[SettingKeys.FAVORITE_NUMBERS] ?: 0) == 0
    val groupsCountFromOne: Boolean get() = (chosen[SettingKeys.GROUP_NUMBERS] ?: 1) == 0

    /** Number Keys: "Full list only" is the second option. */
    val numberKeysFullListOnly: Boolean get() = chosen[SettingKeys.NUMBER_KEYS] == 1

    private inline fun <reified E : Enum<E>> pick(key: String, default: E): E =
        chosen[key]?.let { enumValues<E>().getOrNull(it) } ?: default

    private fun switch(key: String, default: Boolean): Boolean = chosen[key]?.let { it == 0 } ?: default

    override fun equals(other: Any?): Boolean = other is SettingsChoices && other.chosen == chosen

    override fun hashCode(): Int = chosen.hashCode()
}
