// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.player.EngineCount
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.FreezeEvent
import ca.gureet.plasma.player.FreezeLook
import ca.gureet.plasma.player.FreezeWatch
import ca.gureet.plasma.player.StreamKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** Show Freeze Watch (Settings › Playback › Advanced, the owner's ask of 2 November). */
class FreezeWatchTextTest {
    private val words = FreezeWatchWords(
        title = "Freeze Watch",
        after = { "restarts after $it" },
        settingOff = "Restart Frozen Streams is off",
        state = { it.name },
        moving = "moving",
        still = { time, bytes -> if (bytes != null) "still for $time, $bytes came in" else "still for $time, nothing came in" },
        byPlace = "by its place in the stream",
        eachSecond = { "Each second: $it" },
        soFar = { "So far: $it" },
        count = { count, value -> "$value ${count.name}" },
        picture = { seen, time ->
            when (seen) {
                PictureOnScreen.Unknown -> null
                PictureOnScreen.Same -> "Picture on screen: the same for $time"
                else -> "Picture on screen: ${seen.name}"
            }
        },
        comingIn = { "Coming in: $it" },
        since = { time, what -> "Since $time: $what" },
        restarts = { "$it restarts" },
        handedOn = { "$it handed to the next engine" },
        noRestarts = "no restarts",
        event = { event, time, bytes -> "${event.kind} after $time, $bytes" },
        engine = { it.name },
    )

    private val today = Instant.parse("2026-11-02T21:00:00Z")

    @Test fun theSamplerTurnsCountsIntoEachSecondAndWatchesThePicture() {
        val sampler = FreezeSampler()
        val first = sampler.next(
            now = 0, channelId = "ch105", engine = EngineId.Vlc,
            counts = mapOf(EngineCount.PicturesDecoded to 100, EngineCount.PicturesDropped to 0, EngineCount.SoundPlayed to 200, EngineCount.Discontinuities to 1),
            picture = 7,
        )
        // The first look only takes the counts in; the stream's breaks are a total.
        assertTrue(first.rates.isEmpty())
        assertEquals(mapOf(EngineCount.Discontinuities to 1L), first.totals)
        assertEquals(PictureOnScreen.NeverChanged, first.picture)

        // VLC decodes and drops every picture, and plays no sound; the copy changed.
        val second = sampler.next(
            now = 1_000, channelId = "ch105", engine = EngineId.Vlc,
            counts = mapOf(EngineCount.PicturesDecoded to 125, EngineCount.PicturesDropped to 25, EngineCount.SoundPlayed to 200, EngineCount.Discontinuities to 3),
            picture = 8,
        )
        assertEquals(mapOf(EngineCount.PicturesDecoded to 25L, EngineCount.PicturesDropped to 25L, EngineCount.SoundPlayed to 0L), second.rates)
        assertEquals(PictureOnScreen.Changing, second.picture)

        // Two seconds later, the picture is the same: for two seconds.
        val third = sampler.next(
            now = 3_000, channelId = "ch105", engine = EngineId.Vlc,
            counts = mapOf(EngineCount.PicturesDecoded to 175, EngineCount.PicturesDropped to 75, EngineCount.SoundPlayed to 200),
            picture = 8,
        )
        assertEquals(25L, third.rates[EngineCount.PicturesDecoded])
        assertEquals(PictureOnScreen.Same, third.picture)
        assertEquals(2_000, third.pictureSameMs)

        // Restarted, its counts start again: they're left out until the next look.
        val restarted = sampler.next(now = 4_000, channelId = "ch105", engine = EngineId.Vlc, counts = mapOf(EngineCount.PicturesDecoded to 20), picture = 9)
        assertTrue(restarted.rates.isEmpty())

        // Another channel starts afresh; a TV that won't copy says so.
        val other = sampler.next(now = 5_000, channelId = "ch106", engine = EngineId.Vlc, counts = mapOf(EngineCount.PicturesDecoded to 40), picture = 9)
        assertTrue(other.rates.isEmpty())
        assertEquals(PictureOnScreen.NeverChanged, other.picture)
        assertEquals(PictureOnScreen.Unreadable, sampler.next(now = 6_000, channelId = "ch106", engine = EngineId.Vlc, counts = emptyMap(), picture = null).picture)
    }

    @Test fun itSaysWhatItSeesWhatItCountedAndWhatItDid() {
        val watch = FreezeWatch(
            now = FreezeLook("ch105", EngineId.Vlc, FreezeLook.State.Watching, afterMs = 5_000, stillMs = 3_200, arrivedBytes = 1_234_567),
            restarts = mapOf(EngineId.Vlc to 2, EngineId.Mpv to 1),
            handedOn = mapOf(EngineId.Vlc to 1),
            events = listOf(
                FreezeEvent(Instant.parse("2026-11-02T20:41:07Z").toEpochMilli(), FreezeEvent.Kind.Restarted, "ch105", EngineId.Vlc, ms = 5_100, bytes = 3_100_000),
                FreezeEvent(Instant.parse("2026-11-02T20:30:00Z").toEpochMilli(), FreezeEvent.Kind.Buffered, StreamKeys.movie("home:7101"), EngineId.Mpv, ms = 75_000),
            ),
            since = Instant.parse("2026-11-02T14:02:00Z").toEpochMilli(),
        )
        val reading = FreezeReading(
            rates = mapOf(EngineCount.SoundPlayed to 0, EngineCount.PicturesDropped to 25, EngineCount.PicturesDecoded to 25),
            totals = mapOf(EngineCount.Discontinuities to 3),
            picture = PictureOnScreen.Same,
            pictureSameMs = 12_000,
        )

        val lines = freezeWatchLines(5_000, watch, reading, "6.4 Mb/s", mapOf("ch105" to "Arena Sports 2"), ZoneOffset.UTC, today, words)

        assertEquals(
            listOf(
                "Freeze Watch · restarts after 5 s",
                "Arena Sports 2 · Vlc · still for 3.2 s, 1.2 MB came in",
                // The picture's counts, then the sound's; then the stream's, which are totals.
                "Each second: 25 PicturesDecoded · 25 PicturesDropped · 0 SoundPlayed",
                "So far: 3 Discontinuities",
                "Picture on screen: the same for 12 s",
                "Coming in: 6.4 Mb/s",
                "Since 14:02: 3 restarts (Vlc 2 · Mpv 1) · 1 handed to the next engine",
                "20:41:07 · Arena Sports 2 · Vlc · Restarted after 5.1 s, 3.1 MB",
                // A movie has no channel name.
                "20:30:00 · Mpv · Buffered after 1 min 15 s, 0 bytes",
            ),
            lines,
        )
    }

    @Test fun movingAndNotWatchedAndNothingPlayingReadPlainly() {
        val moving = FreezeWatch(now = FreezeLook("ch105", EngineId.Mpv, FreezeLook.State.Watching, afterMs = 10_000, stillMs = 0, byPlace = true), since = today.toEpochMilli())
        assertEquals(
            listOf("Freeze Watch · restarts after 10 s", "Arena Sports 2 · Mpv · moving · by its place in the stream", "Since 21:00: no restarts"),
            freezeWatchLines(10_000, moving, FreezeReading(), "", mapOf("ch105" to "Arena Sports 2"), ZoneOffset.UTC, today, words),
        )

        val off = FreezeWatch(now = FreezeLook("ch105", EngineId.Vlc, FreezeLook.State.Off), since = Instant.parse("2026-10-31T09:15:00Z").toEpochMilli())
        assertEquals(
            listOf("Freeze Watch · Restart Frozen Streams is off", "Arena Sports 2 · Vlc · Off", "Since Sat 31 Oct, 09:15: no restarts"),
            freezeWatchLines(null, off, FreezeReading(), "", mapOf("ch105" to "Arena Sports 2"), ZoneOffset.UTC, today, words),
        )

        assertEquals("Idle", freezeWatchLines(5_000, FreezeWatch(), FreezeReading(), "", emptyMap(), ZoneOffset.UTC, today, words)[1])
    }

    @Test fun durationsAndSizesReadAsTheBriefWritesThem() {
        assertEquals("0.4 s", durationText(400))
        assertEquals("9.9 s", durationText(9_940))
        assertEquals("45 s", durationText(45_200))
        assertEquals("2 min 05 s", durationText(125_000))
        assertEquals("1 h 02 min", durationText(3_725_000))
        assertEquals("312 bytes", bytesText(312))
        assertEquals("640 KB", bytesText(640_400))
        assertEquals("1.2 MB", bytesText(1_234_567))
    }
}
