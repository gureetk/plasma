// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.player.MediaTrack
import ca.gureet.plasma.player.MediaTracks
import ca.gureet.plasma.player.PictureSize
import ca.gureet.plasma.player.languageCode
import java.util.Locale
import kotlin.math.abs

/**
 * Audio and Subtitles (brief 4.10): the stream's sound tracks, the one playing ticked, then its
 * subtitles after Subtitles Off. Picking one closes Player Options; Back returns to it.
 */
@Composable
internal fun TracksSheet(
    channel: String,
    tracks: MediaTracks,
    onAudio: (id: String) -> Unit,
    onSubtitles: (id: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val forced = stringResource(R.string.forced)
    val audio = tracks.audio.mapIndexed { index, track ->
        SheetOption(
            TrackNames.audio(resources, track, index),
            onClick = { onAudio(track.id) },
            value = TrackNames.audioFormat(resources, track.codec),
            icon = PlasmaIcons.Volume,
            checked = track.id == tracks.audioId,
            note = TrackNames.note(track),
        )
    }
    val off = SheetOption(stringResource(R.string.subtitles_off), onClick = { onSubtitles(null) }, icon = PlasmaIcons.Subtitles, checked = tracks.subtitleId == null)
    val subtitles = tracks.subtitles.mapIndexed { index, track ->
        SheetOption(
            TrackNames.subtitles(resources, track, index),
            onClick = { onSubtitles(track.id) },
            value = TrackNames.subtitlesKind(resources, track.codec),
            icon = PlasmaIcons.Subtitles,
            checked = track.id == tracks.subtitleId,
            tag = if (track.forced) forced else null,
            note = TrackNames.note(track),
        )
    }
    OptionsSheet(
        title = stringResource(R.string.audio_and_subtitles),
        subtitle = channel,
        onDismiss = onDismiss,
        options = audio + listOfNotNull(SheetOption.Divider.takeIf { audio.isNotEmpty() }) + off + subtitles,
    )
}

/** Picture Size (4.10): Fit, Fill, Zoom, 16:9 and 4:3, the channel's own ticked. */
@Composable
internal fun PictureSizeSheet(channel: String, size: PictureSize, onPick: (PictureSize) -> Unit, onDismiss: () -> Unit) {
    OptionsSheet(
        title = stringResource(R.string.picture_size),
        subtitle = channel,
        onDismiss = onDismiss,
        options = PictureSize.entries.map { choice ->
            SheetOption(pictureSizeName(choice), onClick = { onPick(choice) }, icon = PlasmaIcons.PictureSize, checked = choice == size)
        },
    )
}

/** "Fit", "Zoom", "16:9". */
@Composable
internal fun pictureSizeName(size: PictureSize): String = stringResource(
    when (size) {
        PictureSize.Fit -> R.string.size_fit
        PictureSize.Fill -> R.string.size_fill
        PictureSize.Zoom -> R.string.size_zoom
        PictureSize.Wide -> R.string.size_wide
        PictureSize.Standard -> R.string.size_standard
    },
)

/** The picture's own shape, from its width over height: "16:9", "4:3", "2.39:1". */
@Composable
internal fun shapeName(aspect: Float): String = when {
    abs(aspect - WIDE) < SHAPE_SLACK -> stringResource(R.string.size_wide)
    abs(aspect - STANDARD) < SHAPE_SLACK -> stringResource(R.string.size_standard)
    else -> String.format(Locale.US, "%.2f:1", aspect)
}

private const val WIDE = 16f / 9f
private const val STANDARD = 4f / 3f
private const val SHAPE_SLACK = 0.03f

/**
 * How Audio and Subtitles names a stream's tracks (4.10): "English 5.1", with its format, "Dolby
 * Digital", beside it. Plain functions, so the app's notices name them the same way.
 */
object TrackNames {
    /** "English 5.1"; without a language, the stream's own name, or "Track 2". [index] counts from 0. */
    fun audio(resources: Resources, track: MediaTrack, index: Int): String {
        val language = languageName(track.language)
        val layout = track.channels?.let { layout(resources, it) }
        return when {
            language != null && layout != null -> resources.getString(R.string.audio_track, language, layout)
            language != null -> language
            else -> track.title ?: resources.getString(R.string.track_n, index + 1)
        }
    }

    /** "English"; without a language, the stream's own name, or "Subtitles 2". */
    fun subtitles(resources: Resources, track: MediaTrack, index: Int): String =
        languageName(track.language) ?: track.title ?: resources.getString(R.string.subtitles_n, index + 1)

    /** The stream's own name for [track], when it says more than its language: "Audio Description". */
    fun note(track: MediaTrack): String? {
        val title = track.title ?: return null
        val language = languageName(track.language) ?: return null
        return title.takeIf { !it.equals(language, ignoreCase = true) && !it.equals(track.language, ignoreCase = true) }
    }

    /** "English" for "eng", "en" or "English"; null when the language isn't known. */
    fun languageName(language: String?): String? {
        val code = languageCode(language) ?: return null
        val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.US)
        return name.takeIf { it.isNotEmpty() && !it.equals(code, ignoreCase = true) }
    }

    /** A sound format by its name: "Dolby Digital", "AAC"; null for one Plasma doesn't name. */
    fun audioFormat(resources: Resources, codec: String?): String? {
        val name = codec?.trim()?.lowercase() ?: return null
        val id = when {
            name in setOf("a52", "a52b", "ac3", "ac-3") -> R.string.codec_dolby_digital
            name in setOf("eac3", "ec-3", "e-ac-3") -> R.string.codec_dolby_digital_plus
            name in setOf("dts", "dtsh", "dtsc", "dtse", "dca") -> R.string.codec_dts
            name in setOf("mlp", "trhd", "truehd") -> R.string.codec_truehd
            name.startsWith("aac") || name in setOf("mp4a", "mp4as") -> R.string.codec_aac
            name in setOf("mpga", "mp2", "mp2a", "mpa") -> R.string.codec_mpeg
            name == "mp3" -> R.string.codec_mp3
            name == "opus" -> R.string.codec_opus
            name == "flac" -> R.string.codec_flac
            name.startsWith("pcm") || name in setOf("araw", "lpcm", "s16l", "s16b", "s24l", "fl32") -> R.string.codec_pcm
            else -> return null
        }
        return resources.getString(id)
    }

    /** What kind of subtitles a format carries: "Teletext", "DVB", "Closed captions", "Text". */
    fun subtitlesKind(resources: Resources, codec: String?): String? {
        val name = codec?.trim()?.lowercase() ?: return null
        val id = when (name) {
            "telx", "dvb_teletext" -> R.string.subtitles_kind_teletext
            "dvbs", "dvb_subtitle", "dvbsub" -> R.string.subtitles_kind_dvb
            "c608", "c708", "cc1", "cc2", "cc3", "cc4", "eia_608", "eia_708" -> R.string.subtitles_kind_captions
            "subt", "subrip", "srt", "ass", "ssa", "webvtt", "wvtt", "vtt", "mov_text", "tx3g", "text" -> R.string.subtitles_kind_text
            else -> return null
        }
        return resources.getString(id)
    }

    /** "Stereo", "5.1", "4 channels". */
    private fun layout(resources: Resources, channels: Int): String = when (channels) {
        1 -> resources.getString(R.string.layout_mono)
        2 -> resources.getString(R.string.layout_stereo)
        3 -> resources.getString(R.string.layout_2_1)
        6 -> resources.getString(R.string.layout_5_1)
        7 -> resources.getString(R.string.layout_6_1)
        8 -> resources.getString(R.string.layout_7_1)
        else -> resources.getQuantityString(R.plurals.layout_channels, channels, channels)
    }
}
