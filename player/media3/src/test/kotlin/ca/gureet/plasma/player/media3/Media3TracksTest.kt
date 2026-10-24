// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player.media3

import android.graphics.Bitmap
import android.text.Layout
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.player.CueAlign
import ca.gureet.plasma.player.CueAnchor
import ca.gureet.plasma.player.CuePlace
import ca.gureet.plasma.player.MediaTrack
import ca.gureet.plasma.player.MediaTracks
import ca.gureet.plasma.player.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Android player's tracks, formats and cues, as the rest of Plasma takes them (brief 4.10 and 5.4.4). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class Media3TracksTest {
    private fun group(format: Format, supported: Boolean = true, selected: Boolean = false) = Tracks.Group(
        TrackGroup(format),
        false,
        intArrayOf(if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_SUBTYPE),
        booleanArrayOf(selected),
    )

    private val video = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setWidth(3840).setHeight(2160).build()
    private val english = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).setLanguage("eng").setChannelCount(6).build()
    private val commentary = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("fr").setChannelCount(2).setLabel("Commentary").build()
    private val dts = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_DTS_HD).setLanguage("en").setChannelCount(8).build()
    private val forced = Format.Builder()
        .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
        .setCodecs(MimeTypes.APPLICATION_SUBRIP)
        .setLanguage("en")
        .setSelectionFlags(C.SELECTION_FLAG_FORCED)
        .build()
    private val undetermined = Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_PGS).setLanguage("und").build()

    @Test fun soundAndSubtitleGroupsTheTvCanPlayAreListedWithWhatsOn() {
        val groups = listOf(
            group(video, selected = true),
            group(english, selected = true),
            group(commentary),
            // DTS-HD with nothing on the TV to decode or take it: not offered.
            group(dts, supported = false),
            group(forced),
            group(undetermined),
        )

        val list = trackListOf(Tracks(groups))

        assertEquals(
            MediaTracks(
                audio = listOf(
                    MediaTrack("1", language = "en", codec = "eac3", channels = 6),
                    MediaTrack("2", language = "fr", title = "Commentary", codec = "aac", channels = 2),
                ),
                subtitles = listOf(
                    MediaTrack("4", language = "en", codec = "subrip", forced = true),
                    MediaTrack("5", codec = "pgs"),
                ),
                audioId = "1",
                subtitleId = null,
            ),
            list.tracks,
        )
        // Each is picked by its group, in the qualities the TV can play.
        assertEquals(TrackSelectionOverride(groups[2].mediaTrackGroup, 0), list.picks["2"])
        assertEquals(setOf("1", "2", "4", "5"), list.picks.keys)
    }

    @Test fun formatsHaveTheNamesTheOtherEnginesGiveThem() {
        fun name(mime: String, codecs: String? = null) = codecName(Format.Builder().setSampleMimeType(mime).setCodecs(codecs).build())

        assertEquals("h264", name(MimeTypes.VIDEO_H264))
        assertEquals("hevc", name(MimeTypes.VIDEO_H265))
        // Dolby Vision is named by the picture under it.
        assertEquals("hevc", name(MimeTypes.VIDEO_DOLBY_VISION, "dvhe.08.06"))
        assertEquals("av1", name(MimeTypes.VIDEO_DOLBY_VISION, "dav1.10.06"))
        assertEquals("h264", name(MimeTypes.VIDEO_DOLBY_VISION, "dva1.09.05"))
        assertEquals("eac3", name(MimeTypes.AUDIO_E_AC3_JOC))
        assertEquals("ac3", name(MimeTypes.AUDIO_AC3))
        assertEquals("dts", name(MimeTypes.AUDIO_DTS_HD))
        assertEquals("truehd", name(MimeTypes.AUDIO_TRUEHD))
        assertEquals("aac", name(MimeTypes.AUDIO_AAC))
        assertEquals("mp2", name(MimeTypes.AUDIO_MPEG_L2))
        assertEquals("ass", name(MimeTypes.APPLICATION_MEDIA3_CUES, MimeTypes.TEXT_SSA))
        assertEquals("eia_608", name(MimeTypes.APPLICATION_CEA608))
        assertEquals("dvbsub", name(MimeTypes.APPLICATION_DVBSUBS))
        assertEquals("alac", name("audio/x-alac"))
        assertNull(codecName(Format.Builder().build()))
    }

    @Test fun cuesKeepTheirPlaceInThePicture() {
        // Most subtitles say nothing of where they go: the bottom, in the middle.
        assertEquals(SubtitleCue.Text("Where were you?"), subtitleCueOf(Cue.Builder().setText(" Where were you?\n").build()))
        // At the top, from its middle, lines starting on the left.
        assertEquals(
            SubtitleCue.Text("Sign: EXIT", CuePlace(x = 0.5f, xAnchor = CueAnchor.Middle, y = 0.1f, yAnchor = CueAnchor.Start, width = 0.4f), CueAlign.Start),
            subtitleCueOf(
                Cue.Builder()
                    .setText("Sign: EXIT")
                    .setLine(0.1f, Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_START)
                    .setPosition(0.5f)
                    .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                    .setSize(0.4f)
                    .setTextAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build(),
            ),
        )
        // Captions go by rows: the last one up from the bottom.
        assertEquals(CuePlace(row = -1), subtitleCueOf(Cue.Builder().setText("[MUSIC]").setLine(-1f, Cue.LINE_TYPE_NUMBER).build())?.place)
        assertNull(subtitleCueOf(Cue.Builder().setText("  ").build()))
    }

    @Test fun pictureSubtitlesGoWhereTheyWereDrawn() {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val cue = Cue.Builder()
            .setBitmap(bitmap)
            .setPosition(0.1f)
            .setPositionAnchor(Cue.ANCHOR_TYPE_START)
            .setLine(0.8f, Cue.LINE_TYPE_FRACTION)
            .setLineAnchor(Cue.ANCHOR_TYPE_START)
            .setSize(0.5f)
            .setBitmapHeight(0.1f)
            .build()

        assertEquals(SubtitleCue.Picture(bitmap, CuePlace(x = 0.1f, y = 0.8f, width = 0.5f, height = 0.1f)), subtitleCueOf(cue))
    }

    @Test fun hlsIsKnownByItsAddress() {
        assertTrue(Media3Engine.isHls("http://line.example.org/live/user/pass/101.m3u8"))
        assertTrue(Media3Engine.isHls("http://line.example.org/hls/index.M3U8?token=abc"))
        assertTrue(Media3Engine.isHls("http://portal.example.org/play/live.php?stream=101&extension=m3u8"))
        assertFalse(Media3Engine.isHls("http://line.example.org/live/user/pass/101.ts"))
        assertFalse(Media3Engine.isHls("http://127.0.0.1:41234/live/token.ts"))
    }

    @Test fun pixelShapesAreFractions() {
        assertEquals(1 to 1, Media3Engine.sampleAspect(1f))
        assertEquals(1 to 1, Media3Engine.sampleAspect(0f))
        // Anamorphic PAL widescreen: 64:45.
        assertEquals(1422 to 1000, Media3Engine.sampleAspect(64f / 45f))
    }
}
