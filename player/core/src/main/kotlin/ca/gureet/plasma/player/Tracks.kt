// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import ca.gureet.plasma.core.model.ChannelTracks
import ca.gureet.plasma.core.model.ORIGINAL_LANGUAGE
import ca.gureet.plasma.core.model.SubtitleMode
import ca.gureet.plasma.core.model.TrackPick
import java.util.Locale

/**
 * Settings › Audio › Preferred Language and Skip Audio Description, and Settings › Playback ›
 * Subtitles and Teletext, as the player picks a stream's tracks by them (brief 5.4.6 and 5.4.7).
 */
data class TrackPrefs(
    /** In order, as language codes; [ORIGINAL_LANGUAGE] is the stream's own default track. */
    val languages: List<String> = listOf("en", ORIGINAL_LANGUAGE),
    val skipDescription: Boolean = true,
    val subtitles: SubtitleMode = SubtitleMode.Off,
)

/** [this] as it's kept for the channel it was picked on (5.4.6). */
fun MediaTrack.pick(): TrackPick = TrackPick(language, codec, channels, title)

/**
 * A sound track's format, as far as passing it through goes (5.4.6), from the name either engine
 * gives it: libVLC's four letters ("a52 ") or FFmpeg's ("ac3").
 */
fun audioCodecOf(codec: String?): AudioCodec = when (codec?.trim()?.lowercase()?.removePrefix("spdif_")) {
    "a52", "a52b", "ac3", "ac-3" -> AudioCodec.Ac3
    "eac3", "ec-3", "e-ac-3" -> AudioCodec.EAc3
    "dts", "dtsh", "dtsc", "dtse", "dca" -> AudioCodec.Dts
    "mlp", "trhd", "truehd" -> AudioCodec.TrueHd
    else -> AudioCodec.Other
}

/** The tracks to switch to: [audioId] null leaves the sound as it is, and so does [changeSubtitles] false. */
data class TrackChoice(val audioId: String?, val subtitleId: String?, val changeSubtitles: Boolean)

/**
 * The tracks a stream should play with (5.4.6 and 5.4.7): what was picked on the channel, then
 * the preferred languages, passing over audio description unless it's all there is. "Original"
 * is the track the stream itself starts with.
 */
fun chooseTracks(tracks: MediaTracks, prefs: TrackPrefs, picked: ChannelTracks? = null): TrackChoice {
    val audio = tracks.audio
    val usable = if (prefs.skipDescription) audio.filterNot(::isAudioDescription).ifEmpty { audio } else audio
    val original = tracks.playingAudio?.takeIf { it in usable } ?: usable.firstOrNull()
    val sound = picked?.audio?.let { bestMatch(audio, it) }
        ?: prefs.languages.firstNotNullOfOrNull { language ->
            if (language == ORIGINAL_LANGUAGE) original else usable.firstOrNull { sameLanguage(it.language, language) }
        }
        ?: original

    val subtitles = tracks.subtitles
    val wanted: MediaTrack? = when {
        picked?.subtitlesOff == true -> null
        else -> picked?.subtitles?.let { bestMatch(subtitles, it) } ?: subtitlesByPrefs(tracks, prefs)
    }
    return TrackChoice(
        audioId = sound?.id?.takeIf { it != tracks.audioId },
        subtitleId = wanted?.id,
        changeSubtitles = wanted?.id != tracks.subtitleId,
    )
}

/** Subtitles and Teletext's own pick: Off, the first in a preferred language, or a forced track. */
private fun subtitlesByPrefs(tracks: MediaTracks, prefs: TrackPrefs): MediaTrack? {
    val all = tracks.subtitles
    val candidates = when (prefs.subtitles) {
        SubtitleMode.Off -> return null
        SubtitleMode.On -> all.filterNot { it.forced }.ifEmpty { all }
        SubtitleMode.ForcedOnly -> all.filter { it.forced }
    }
    return prefs.languages.firstNotNullOfOrNull { language ->
        if (language == ORIGINAL_LANGUAGE) {
            tracks.showingSubtitles?.takeIf { it in candidates } ?: candidates.firstOrNull()
        } else {
            candidates.firstOrNull { sameLanguage(it.language, language) }
        }
    }
}

/**
 * The track among [tracks] most like [pick]: the same language first, then the same format,
 * channels and name. Null when none has its language.
 */
internal fun bestMatch(tracks: List<MediaTrack>, pick: TrackPick): MediaTrack? =
    tracks.map { it to matchScore(it, pick) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first

private fun matchScore(track: MediaTrack, pick: TrackPick): Int {
    val language = languageCode(track.language)
    val wanted = languageCode(pick.language)
    var score = when {
        language == null && wanted == null -> 4
        language != null && language == wanted -> 8
        else -> return 0
    }
    if (track.codec != null && track.codec == pick.codec) score += 2
    if (track.channels != null && track.channels == pick.channels) score += 1
    if (track.title != null && track.title == pick.title) score += 1
    return score
}

/**
 * A track for blind and partially sighted viewers (Skip Audio Description, 5.4.6): DVB's visually
 * impaired commentary, or a name such as "AD" or "Audio Description".
 */
fun isAudioDescription(track: MediaTrack): Boolean {
    val code = track.language?.trim()?.lowercase()
    if (code in DESCRIPTION_CODES) return true
    val title = track.title?.trim()?.lowercase() ?: return false
    if (title == "ad" || title.endsWith(" ad") || "(ad)" in title || "[ad]" in title) return true
    return DESCRIPTION_WORDS.any { it in title }
}

private val DESCRIPTION_CODES = setOf("qad", "nar")
private val DESCRIPTION_WORDS = listOf("audio description", "audio-description", "audio described", "described video", "visual impaired", "visually impaired")

/** Whether two languages, as streams give them ("eng", "en", "English", "en-US"), are the same. */
fun sameLanguage(a: String?, b: String?): Boolean {
    val first = languageCode(a) ?: return false
    return first == languageCode(b)
}

/**
 * A language as a two-letter code where there is one: "eng", "English", "en-US" and "en" are all
 * "en". Unknown ones stay as they are, lowercased; "und" and empty ones are null.
 */
fun languageCode(raw: String?): String? {
    val text = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" && it != "unknown" } ?: return null
    val base = text.substringBefore('-').substringBefore('_')
    return when (base.length) {
        2 -> base
        3 -> Languages.byThreeLetters[base] ?: base
        else -> Languages.byName[base] ?: base
    }
}

/** Language codes and names, from the platform's list, looked up once. */
private object Languages {
    val byThreeLetters: Map<String, String> by lazy {
        val terminology = Locale.getISOLanguages().mapNotNull { code -> runCatching { Locale.forLanguageTag(code).isO3Language }.getOrNull()?.let { it to code } }.toMap()
        // Older codes some broadcasters still use (ISO 639-2/B).
        terminology + mapOf(
            "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh", "cze" to "cs",
            "dut" to "nl", "fre" to "fr", "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is",
            "mac" to "mk", "mao" to "mi", "may" to "ms", "per" to "fa", "rum" to "ro", "slo" to "sk",
            "tib" to "bo", "wel" to "cy",
        )
    }

    /** "english", and "deutsch", "español"… in their own languages. */
    val byName: Map<String, String> by lazy {
        Locale.getISOLanguages().flatMap { code ->
            val locale = Locale.forLanguageTag(code)
            listOf(locale.getDisplayLanguage(Locale.ENGLISH), locale.getDisplayLanguage(locale)).map { it.lowercase() to code }
        }.filter { (name, code) -> name.isNotEmpty() && name != code }.toMap()
    }
}
