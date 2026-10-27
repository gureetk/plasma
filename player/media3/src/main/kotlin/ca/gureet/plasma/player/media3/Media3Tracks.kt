// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.media3

import android.text.Layout
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import ca.gureet.plasma.player.CueAlign
import ca.gureet.plasma.player.CueAnchor
import ca.gureet.plasma.player.CuePlace
import ca.gureet.plasma.player.MediaTrack
import ca.gureet.plasma.player.MediaTracks
import ca.gureet.plasma.player.SubtitleCue

/**
 * The sound and subtitle tracks of what plays, as Plasma lists them (brief 4.10), and how to pick
 * each one: its group and the tracks in it this TV can play.
 */
internal class TrackList(val tracks: MediaTracks, val picks: Map<String, TrackSelectionOverride>)

/**
 * The sound and subtitle tracks in [tracks], one for each group the TV can play: a group is one
 * language or name, in one or more qualities. Each has its place among the groups as its id.
 */
@OptIn(UnstableApi::class)
internal fun trackListOf(tracks: Tracks): TrackList {
    val audio = mutableListOf<MediaTrack>()
    val subtitles = mutableListOf<MediaTrack>()
    val picks = HashMap<String, TrackSelectionOverride>()
    var playing: String? = null
    var showing: String? = null
    tracks.groups.forEachIndexed { index, group ->
        val type = group.type
        if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) return@forEachIndexed
        val playable = (0 until group.length).filter { group.isTrackSupported(it) }
        if (playable.isEmpty()) return@forEachIndexed
        val format = group.getTrackFormat((0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: playable.first())
        val id = index.toString()
        picks[id] = TrackSelectionOverride(group.mediaTrackGroup, playable)
        val track = MediaTrack(
            id = id,
            language = format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED },
            title = format.label?.takeIf { it.isNotBlank() },
            codec = codecName(format),
            channels = format.channelCount.takeIf { type == C.TRACK_TYPE_AUDIO && it != Format.NO_VALUE && it > 0 },
            forced = type == C.TRACK_TYPE_TEXT && format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
        )
        if (type == C.TRACK_TYPE_AUDIO) {
            audio += track
            if (group.isSelected) playing = id
        } else {
            subtitles += track
            if (group.isSelected) showing = id
        }
    }
    return TrackList(MediaTracks(audio, subtitles, playing, showing), picks)
}

/**
 * A format by the name the other engines give it (FFmpeg's), which the stream info, Audio and
 * Subtitles and passthrough go by: "hevc", "eac3", "subrip". Subtitles Media3 has already read come
 * as its own cues, with their file's format kept as their codec.
 */
@OptIn(UnstableApi::class)
internal fun codecName(format: Format): String? {
    val mime = if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) format.codecs else format.sampleMimeType
    return when (mime) {
        null -> null
        MimeTypes.VIDEO_H264 -> "h264"
        MimeTypes.VIDEO_H265 -> "hevc"
        // Dolby Vision is a layer over HEVC, H.264 or AV1, which its codec string says.
        MimeTypes.VIDEO_DOLBY_VISION -> when (format.codecs?.substringBefore('.')?.lowercase()) {
            "dvav", "dva1" -> "h264"
            "dav1" -> "av1"
            else -> "hevc"
        }
        MimeTypes.VIDEO_AV1 -> "av1"
        MimeTypes.VIDEO_VP9 -> "vp9"
        MimeTypes.VIDEO_MPEG2 -> "mpeg2video"
        MimeTypes.VIDEO_MP4V -> "mpeg4"
        MimeTypes.VIDEO_VC1 -> "vc1"
        MimeTypes.AUDIO_AC3 -> "ac3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "eac3"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS, MimeTypes.AUDIO_DTS_UHD_P2 -> "dts"
        MimeTypes.AUDIO_TRUEHD -> "truehd"
        MimeTypes.AUDIO_AAC -> "aac"
        MimeTypes.AUDIO_MPEG -> "mp3"
        MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG_L2 -> "mp2"
        MimeTypes.AUDIO_OPUS -> "opus"
        MimeTypes.AUDIO_VORBIS -> "vorbis"
        MimeTypes.AUDIO_FLAC -> "flac"
        MimeTypes.AUDIO_RAW -> "pcm"
        MimeTypes.APPLICATION_SUBRIP -> "subrip"
        MimeTypes.TEXT_SSA -> "ass"
        MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_MP4VTT -> "webvtt"
        MimeTypes.APPLICATION_TX3G -> "tx3g"
        MimeTypes.APPLICATION_TTML -> "ttml"
        MimeTypes.APPLICATION_PGS -> "pgs"
        MimeTypes.APPLICATION_DVBSUBS -> "dvbsub"
        MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_MP4CEA608 -> "eia_608"
        MimeTypes.APPLICATION_CEA708 -> "eia_708"
        else -> mime.substringAfter('/').removePrefix("x-")
    }
}

/**
 * A cue as Plasma draws it (brief 5.4.4): its text, or its picture, and where it goes. Media3's
 * placing carries over: a position set with no anchor is the cue's left edge, as Media3 has it.
 */
@OptIn(UnstableApi::class)
internal fun subtitleCueOf(cue: Cue): SubtitleCue? {
    val bitmap = cue.bitmap
    val vertical = when {
        cue.line == Cue.DIMEN_UNSET -> Vertical()
        cue.lineType == Cue.LINE_TYPE_NUMBER -> Vertical(row = cue.line.toInt())
        else -> Vertical(y = cue.line, anchor = anchorOf(cue.lineAnchor))
    }
    val place = CuePlace(
        x = cue.position.takeIf { it != Cue.DIMEN_UNSET },
        xAnchor = anchorOf(cue.positionAnchor),
        y = vertical.y,
        yAnchor = vertical.anchor,
        row = vertical.row,
        width = cue.size.takeIf { it != Cue.DIMEN_UNSET && it > 0f },
        height = cue.bitmapHeight.takeIf { bitmap != null && it != Cue.DIMEN_UNSET && it > 0f },
    )
    if (bitmap != null) return SubtitleCue.Picture(bitmap, place)
    val text = cue.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val align = when (cue.textAlignment) {
        Layout.Alignment.ALIGN_NORMAL -> CueAlign.Start
        Layout.Alignment.ALIGN_OPPOSITE -> CueAlign.End
        else -> CueAlign.Center
    }
    return SubtitleCue.Text(text, place, align)
}

private class Vertical(val y: Float? = null, val anchor: CueAnchor = CueAnchor.Start, val row: Int? = null)

private fun anchorOf(type: Int): CueAnchor = when (type) {
    Cue.ANCHOR_TYPE_MIDDLE -> CueAnchor.Middle
    Cue.ANCHOR_TYPE_END -> CueAnchor.End
    else -> CueAnchor.Start
}
