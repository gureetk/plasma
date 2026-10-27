// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import ca.gureet.plasma.core.model.ChannelTracks
import ca.gureet.plasma.core.model.ORIGINAL_LANGUAGE
import ca.gureet.plasma.core.model.SubtitleMode
import ca.gureet.plasma.core.model.TrackPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Picking a stream's sound and subtitles (brief 5.4.6 and 5.4.7). */
class TracksTest {
    private val spanish = MediaTrack("1", language = "spa", codec = "ac3", channels = 6)
    private val described = MediaTrack("2", language = "eng", title = "Audio Description", codec = "aac", channels = 2)
    private val english = MediaTrack("3", language = "eng", codec = "aac", channels = 2)
    private val englishSubtitles = MediaTrack("5", language = "eng", codec = "dvb_subtitle")
    private val forced = MediaTrack("6", language = "en", codec = "subrip", forced = true)
    private val german = MediaTrack("7", language = "ger", codec = "dvb_teletext")

    private val tracks = MediaTracks(audio = listOf(spanish, described, english), subtitles = listOf(englishSubtitles, forced, german), audioId = "1")

    @Test fun languagesAreKnownByCodeAndName() {
        assertEquals("en", languageCode("eng"))
        assertEquals("en", languageCode("English"))
        assertEquals("en", languageCode("en-US"))
        assertEquals("de", languageCode("ger"))
        assertEquals("de", languageCode("deu"))
        assertEquals("de", languageCode("Deutsch"))
        assertEquals("fr", languageCode("fre"))
        assertNull(languageCode("und"))
        assertNull(languageCode(" "))
        // One no list knows stays as it is.
        assertEquals("qaa", languageCode("qaa"))
        assertTrue(sameLanguage("eng", "en"))
        assertFalse(sameLanguage(null, null))
    }

    @Test fun audioDescriptionIsKnownByItsName() {
        assertTrue(isAudioDescription(described))
        assertTrue(isAudioDescription(MediaTrack("9", language = "eng", title = "English AD")))
        assertTrue(isAudioDescription(MediaTrack("9", language = "eng", title = "Visual impaired commentary")))
        assertTrue(isAudioDescription(MediaTrack("9", language = "qad")))
        assertFalse(isAudioDescription(english))
        assertFalse(isAudioDescription(MediaTrack("9", language = "eng", title = "Stadium")))
    }

    @Test fun englishThenOriginalPicksEnglishPassingOverItsDescription() {
        val choice = chooseTracks(tracks, TrackPrefs(languages = listOf("en", ORIGINAL_LANGUAGE)))

        assertEquals("3", choice.audioId)
        // Subtitles are off by default, and are off already.
        assertNull(choice.subtitleId)
        assertFalse(choice.changeSubtitles)
    }

    @Test fun originalKeepsTheStreamsOwnSound() {
        val choice = chooseTracks(tracks, TrackPrefs(languages = listOf(ORIGINAL_LANGUAGE)))

        assertNull(choice.audioId)
    }

    @Test fun withoutSkippingDescriptionTheFirstEnglishTrackPlays() {
        val choice = chooseTracks(tracks, TrackPrefs(languages = listOf("en"), skipDescription = false))

        assertEquals("2", choice.audioId)
    }

    @Test fun aDescriptionThatsAllThereIsStillPlays() {
        val only = MediaTracks(audio = listOf(described), audioId = null)

        assertEquals("2", chooseTracks(only, TrackPrefs()).audioId)
    }

    @Test fun englishWithNoEnglishTrackKeepsTheStreamsOwn() {
        val noEnglish = tracks.copy(audio = listOf(spanish, MediaTrack("4", language = "fra")))

        assertNull(chooseTracks(noEnglish, TrackPrefs(languages = listOf("en"))).audioId)
    }

    @Test fun subtitlesOnShowThePreferredLanguageAndForcedOnlyTheForcedOnes() {
        val on = chooseTracks(tracks, TrackPrefs(languages = listOf("de", "en"), subtitles = SubtitleMode.On))
        assertEquals("7", on.subtitleId)
        assertTrue(on.changeSubtitles)

        assertEquals("6", chooseTracks(tracks, TrackPrefs(subtitles = SubtitleMode.ForcedOnly)).subtitleId)
        // With no forced track, Forced Only shows none.
        val noForced = tracks.copy(subtitles = listOf(englishSubtitles))
        assertNull(chooseTracks(noForced, TrackPrefs(subtitles = SubtitleMode.ForcedOnly)).subtitleId)
    }

    @Test fun subtitlesOnInEnglishOnlyShowNoneWithoutEnglish() {
        val germanOnly = tracks.copy(subtitles = listOf(german))

        assertNull(chooseTracks(germanOnly, TrackPrefs(languages = listOf("en"), subtitles = SubtitleMode.On)).subtitleId)
        // "Then original" falls back to the stream's own.
        assertEquals("7", chooseTracks(germanOnly, TrackPrefs(languages = listOf("en", ORIGINAL_LANGUAGE), subtitles = SubtitleMode.On)).subtitleId)
    }

    @Test fun whatWasPickedOnTheChannelComesFirst() {
        val picked = ChannelTracks(audio = TrackPick("spa", "ac3", 6), subtitles = TrackPick("ger"))

        val choice = chooseTracks(tracks.copy(audioId = "3"), TrackPrefs(languages = listOf("en")), picked)

        assertEquals("1", choice.audioId)
        assertEquals("7", choice.subtitleId)
    }

    @Test fun subtitlesTurnedOffOnTheChannelStayOff() {
        val choice = chooseTracks(tracks.copy(subtitleId = "5"), TrackPrefs(subtitles = SubtitleMode.On), ChannelTracks(subtitlesOff = true))

        assertNull(choice.subtitleId)
        assertTrue(choice.changeSubtitles)
    }

    @Test fun aPickThatsGoneFallsBackToTheRules() {
        val picked = ChannelTracks(audio = TrackPick("ita"))

        assertEquals("3", chooseTracks(tracks, TrackPrefs(languages = listOf("en")), picked).audioId)
    }

    @Test fun thePickMostLikeTheOneMadeWins() {
        val surround = MediaTrack("8", language = "eng", codec = "ac3", channels = 6)
        val both = tracks.copy(audio = listOf(english, surround), audioId = "3")

        assertEquals("8", chooseTracks(both, TrackPrefs(), ChannelTracks(audio = TrackPick("en", "ac3", 6))).audioId)
        assertNull(chooseTracks(both, TrackPrefs(), ChannelTracks(audio = TrackPick("eng", "aac", 2))).audioId)
    }
}
