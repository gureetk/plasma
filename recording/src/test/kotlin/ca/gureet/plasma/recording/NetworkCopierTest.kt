// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.data.NetworkPlace
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import ca.gureet.plasma.core.data.RecordingStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest

/**
 * Copy to Network Folder (brief 5.6): where a recording goes on the share, named for Jellyfin or
 * not, with its info files and artwork, written whole or not at all. The share is a folder here.
 */
class NetworkCopierTest {
    private val root: File = Files.createTempDirectory("share").toFile()
    private val recordings: File = Files.createTempDirectory("recordings").toFile()

    @After fun tidy() {
        root.deleteRecursively()
        recordings.deleteRecursively()
    }

    private val share = FolderShare(root)
    private val folder = NetworkFolder("nas.home.lan", "Media", "Recordings", "maya", "s3cret")
    private val toronto = ZoneId.of("America/Toronto")
    private val evening = Instant.parse("2026-10-21T00:00:00Z")
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    private val fetched = mutableListOf<String>()
    private var picture: ByteArray? = jpeg

    private fun copier() = NetworkCopier(open = { share }, fetch = { url -> fetched += url; picture })

    private fun recording(
        title: String,
        kind: RecordingKind,
        channel: String = "Wildframe 4K",
        start: Instant = evening,
        season: Int? = null,
        episode: Int? = null,
        subtitle: String? = null,
        description: String? = null,
        category: String? = null,
        image: String? = null,
        ruleId: String? = null,
    ): Pair<Recording, File> {
        val file = File(recordings, "${title.hashCode()}-${start.epochSecond}.ts").apply { writeBytes(ByteArray(188 * 40) { (it % 251).toByte() }) }
        return Recording(
            id = "home:180@${start.epochSecond}",
            channelId = "home:180",
            channelName = channel,
            channelLogo = null,
            title = title,
            subtitle = subtitle,
            description = description,
            category = category,
            image = image,
            season = season,
            episode = episode,
            kind = kind,
            start = start,
            end = start.plus(Duration.ofHours(1)),
            padBefore = Duration.ofMinutes(1),
            padAfter = Duration.ofMinutes(5),
            status = RecordingStatus.Done,
            file = file.path,
            sizeBytes = file.length(),
            recordedFrom = start,
            recordedTo = start.plus(Duration.ofHours(1)),
            interruptions = 0,
            problem = null,
            keep = false,
            positionMs = 0,
            watched = false,
            watchedAt = null,
            ruleId = ruleId,
        ) to file
    }

    private fun onShare(path: String) = File(root, "Recordings/$path")

    @Test fun anEpisodeGoesInItsSeasonWithItsDetailsAndArtwork() = runTest {
        val (episode, file) = recording(
            "Kingdoms of Ice",
            RecordingKind.Series,
            season = 1,
            episode = 3,
            subtitle = "The Long Night",
            description = "Ice & fire <live>",
            image = "https://img.example/kingdoms.jpg",
        )

        val copy = copier().copy(episode, file, folder, jellyfin = true, zone = toronto)

        assertEquals("TV Recordings/Kingdoms of Ice/Season 01/Kingdoms of Ice S01E03.ts", copy.path)
        assertEquals(3_100_000_000_000, copy.freeBytes)
        assertTrue(onShare(copy.path).readBytes().contentEquals(file.readBytes()))
        val nfo = onShare("TV Recordings/Kingdoms of Ice/Season 01/Kingdoms of Ice S01E03.nfo").readText()
        assertTrue(nfo, "<episodedetails>" in nfo)
        assertTrue(nfo, "<title>The Long Night</title>" in nfo)
        assertTrue(nfo, "<showtitle>Kingdoms of Ice</showtitle>" in nfo)
        assertTrue(nfo, "<season>1</season>" in nfo)
        assertTrue(nfo, "<episode>3</episode>" in nfo)
        // Aired on the evening of the 20th in Toronto.
        assertTrue(nfo, "<aired>2026-10-20</aired>" in nfo)
        assertTrue(nfo, "<plot>Ice &amp; fire &lt;live&gt;</plot>" in nfo)
        assertTrue(nfo, "<studio>Wildframe 4K</studio>" in nfo)
        assertTrue(onShare("TV Recordings/Kingdoms of Ice/Season 01/Kingdoms of Ice S01E03-thumb.jpg").exists())
        assertTrue("<title>Kingdoms of Ice</title>" in onShare("TV Recordings/Kingdoms of Ice/tvshow.nfo").readText())
        assertTrue(onShare("TV Recordings/Kingdoms of Ice/poster.jpg").readBytes().contentEquals(jpeg))
        // Nothing half-written is left, and the share's let go of.
        assertTrue(root.walk().none { it.name.endsWith(".partial") })
        assertTrue(share.closed)
    }

    @Test fun aSeriesWithoutNumbersCountsItsEpisodesUpWithinTheYear() = runTest {
        val (first, firstFile) = recording("Late Night Sessions", RecordingKind.Other, channel = "Pulse", ruleId = "rule-1")
        val (second, secondFile) = recording("Late Night Sessions", RecordingKind.Other, channel = "Pulse", start = evening.plus(Duration.ofDays(1)), ruleId = "rule-1")

        assertEquals("TV Recordings/Late Night Sessions/Season 2026/Late Night Sessions S2026E001.ts", copier().copy(first, firstFile, folder, true, toronto).path)
        assertEquals("TV Recordings/Late Night Sessions/Season 2026/Late Night Sessions S2026E002.ts", copier().copy(second, secondFile, folder, true, toronto).path)
        val nfo = onShare("TV Recordings/Late Night Sessions/Season 2026/Late Night Sessions S2026E002.nfo").readText()
        assertTrue(nfo, "<season>2026</season>" in nfo)
        assertTrue(nfo, "<episode>2</episode>" in nfo)
    }

    @Test fun writtenStraightThereItTakesItsNameWithItsDetailsOnceItsAllThere() = runTest {
        val (first, firstFile) = recording("Late Night Sessions", RecordingKind.Other, channel = "Pulse", ruleId = "rule-1")
        copier().copy(first, firstFile, folder, true, toronto)
        val (second, _) = recording("Late Night Sessions", RecordingKind.Other, channel = "Pulse", start = evening.plus(Duration.ofDays(1)), ruleId = "rule-1")

        // Record To › Network folder: where it goes, its folders made, written there as it comes.
        val plan = copier().plan(second, folder, true, toronto)
        assertEquals("TV Recordings/Late Night Sessions/Season 2026/Late Night Sessions S2026E002.ts", plan.media)
        onShare("${plan.media}.partial").writeBytes(ByteArray(188 * 3))
        // One more planned meanwhile counts on from the one being written.
        val (third, _) = recording("Late Night Sessions", RecordingKind.Other, channel = "Pulse", start = evening.plus(Duration.ofDays(2)), ruleId = "rule-1")
        assertEquals("TV Recordings/Late Night Sessions/Season 2026/Late Night Sessions S2026E003.ts", copier().plan(third, folder, true, toronto).media)

        val copy = copier().finish(second, folder, true, toronto, plan.media)

        assertEquals(plan.media, copy.path)
        assertEquals(188 * 3L, onShare(plan.media).length())
        assertFalse(onShare("${plan.media}.partial").exists())
        // Its info file says the episode it was given.
        assertTrue("<episode>2</episode>" in onShare("TV Recordings/Late Night Sessions/Season 2026/Late Night Sessions S2026E002.nfo").readText())
    }

    @Test fun oneThatNeverGotGoingIsDiscarded() = runTest {
        val (match, _) = recording("Harbour City v Redmoor", RecordingKind.Sports, channel = "Arena Sports 1")
        val plan = copier().plan(match, folder, true, toronto)
        onShare("${plan.media}.partial").writeBytes(ByteArray(0))

        copier().discard(folder, plan.media)

        assertFalse(onShare("${plan.media}.partial").exists())
    }

    @Test fun theShowsOwnDetailsAreWrittenOnce() = runTest {
        val show = onShare("TV Recordings/Kingdoms of Ice").apply { mkdirs() }
        File(show, "tvshow.nfo").writeText("Mine")
        File(show, "poster.png").writeBytes(png)
        val (episode, file) = recording("Kingdoms of Ice", RecordingKind.Series, season = 1, episode = 4, image = "https://img.example/kingdoms.jpg")

        copier().copy(episode, file, folder, true, toronto)

        assertEquals("Mine", File(show, "tvshow.nfo").readText())
        assertFalse(File(show, "poster.jpg").exists())
        // The episode's own picture still comes.
        assertTrue(File(show, "Season 01/Kingdoms of Ice S01E04-thumb.jpg").exists())
    }

    @Test fun aPictureIsNamedForWhatItIs() = runTest {
        picture = png
        val (episode, file) = recording("Kingdoms of Ice", RecordingKind.Series, season = 1, episode = 3, image = "https://img.example/kingdoms")

        copier().copy(episode, file, folder, true, toronto)

        assertTrue(onShare("TV Recordings/Kingdoms of Ice/Season 01/Kingdoms of Ice S01E03-thumb.png").exists())
        assertTrue(onShare("TV Recordings/Kingdoms of Ice/poster.png").exists())
        assertEquals("webp", NetworkCopier.imageTypeOf("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test fun aMovieGoesInAFolderOfItsOwn() = runTest {
        val (movie, file) = recording("The Last Lighthouse", RecordingKind.Movie, channel = "Cinema Prime 4K", category = "Drama", description = "A keeper stays.")

        val copy = copier().copy(movie, file, folder, true, toronto)

        assertEquals("Movie Recordings/The Last Lighthouse/The Last Lighthouse.ts", copy.path)
        val nfo = onShare("Movie Recordings/The Last Lighthouse/movie.nfo").readText()
        assertTrue(nfo, "<movie>" in nfo)
        assertTrue(nfo, "<title>The Last Lighthouse</title>" in nfo)
        assertTrue(nfo, "<genre>Drama</genre>" in nfo)
        assertTrue(nfo, "<plot>A keeper stays.</plot>" in nfo)
        // No picture in the guide, none asked for.
        assertTrue(fetched.isEmpty())
    }

    @Test fun everythingElseGoesUnderItsChannel() = runTest {
        val (match, file) = recording("Harbour City v Redmoor: Final?", RecordingKind.Sports, channel = "Arena Sports 1", start = Instant.parse("2026-10-21T18:45:00Z"))

        val copy = copier().copy(match, file, folder, true, toronto)

        assertEquals("Other Recordings/Arena Sports 1/2026-10-21 1445 Harbour City v Redmoor - Final.ts", copy.path)
        assertEquals(listOf("2026-10-21 1445 Harbour City v Redmoor - Final.ts"), onShare("Other Recordings/Arena Sports 1").list()?.toList())
    }

    @Test fun withoutJellyfinsNamesItsOwnNameInTheFolder() = runTest {
        val (episode, file) = recording("Kingdoms of Ice", RecordingKind.Series, season = 1, episode = 3, image = "https://img.example/kingdoms.jpg")

        val copy = copier().copy(episode, file, folder.copy(path = ""), jellyfin = false, zone = toronto)

        assertEquals("Wildframe 4K – Kingdoms of Ice – 2026-10-20 2000.ts", copy.path)
        assertTrue(File(root, copy.path).isFile)
        assertEquals(1, root.list()?.size)
        assertTrue(fetched.isEmpty())
    }

    @Test fun artworkThatCantBeHadLeavesTheRecordingAsItIs() = runTest {
        picture = null
        val (episode, file) = recording("Kingdoms of Ice", RecordingKind.Series, season = 1, episode = 3, image = "https://img.example/gone.jpg")

        val copy = copier().copy(episode, file, folder, true, toronto)

        assertTrue(onShare(copy.path).exists())
        assertFalse(onShare("TV Recordings/Kingdoms of Ice/Season 01/Kingdoms of Ice S01E03-thumb.jpg").exists())
    }

    @Test fun aShareThatRefusesSaysWhy() = runTest {
        share.refuse = ShareProblem.ReadOnly
        val (episode, file) = recording("Kingdoms of Ice", RecordingKind.Series, season = 1, episode = 3)

        try {
            copier().copy(episode, file, folder, true, toronto)
            fail("It was copied")
        } catch (refused: ShareException) {
            assertEquals(ShareProblem.ReadOnly, refused.problem)
        }
        assertTrue(share.closed)
    }

    @Test fun aCopyThatComesOutShortIsntKept() = runTest {
        share.cutShort = 1_000
        val (episode, file) = recording("Kingdoms of Ice", RecordingKind.Series, season = 1, episode = 3)

        try {
            copier().copy(episode, file, folder, true, toronto)
            fail("A short copy was kept")
        } catch (refused: ShareException) {
            assertEquals(ShareProblem.Refused, refused.problem)
        }
        assertTrue(root.walk().none { it.name.endsWith(".ts") || it.name.endsWith(".partial") })
    }

    @Test fun aCopysPlaceSaysWhichServerItsOn() {
        assertEquals("smb://nas.home.lan/Media/Recordings/TV Recordings/A S01E01.ts", folder.locate("TV Recordings/A S01E01.ts"))
        assertEquals("smb://nas.home.lan/Media/A.ts", folder.copy(path = "").locate("A.ts"))
        assertEquals("nas.home.lan", NetworkFolder.hostOf(folder.locate("A.ts")))
        assertNull(NetworkFolder.hostOf("/storage/A.ts"))
        assertEquals("nas.home.lan › Media › Recordings", folder.shown)
        // The password stays out of logs.
        assertFalse("s3cret" in folder.toString())
    }

    @Test fun aCopysPlaceIsItsServerItsShareAndItsPath() {
        assertEquals(NetworkPlace("nas.home.lan", "Media", "Recordings/TV Recordings/A S01E01.ts"), NetworkFolder.placeOf(folder.locate("TV Recordings/A S01E01.ts")))
        assertNull(NetworkFolder.placeOf("smb://nas.home.lan/Media"))
        assertNull(NetworkFolder.placeOf("/storage/A.ts"))
    }

    @Test fun deletingEverywhereTakesWhatsBesideTheCopy() = runTest {
        val (episode, episodeFile) = recording("Kingdoms of Ice", RecordingKind.Series, season = 1, episode = 3, image = "https://img.example/kingdoms.jpg")
        val (later, laterFile) = recording("Kingdoms of Ice", RecordingKind.Series, season = 1, episode = 4, start = evening.plus(Duration.ofDays(7)), image = "https://img.example/kingdoms.jpg")
        val (movie, movieFile) = recording("The Last Lighthouse", RecordingKind.Movie, image = "https://img.example/lighthouse.jpg")
        val third = copier().copy(episode, episodeFile, folder, true, toronto).path
        copier().copy(later, laterFile, folder, true, toronto)
        val film = copier().copy(movie, movieFile, folder, true, toronto).path

        NetworkCopier.deleteCopy(share, "Recordings/$third")
        NetworkCopier.deleteCopy(share, "Recordings/$film")

        val season = onShare("TV Recordings/Kingdoms of Ice/Season 01")
        assertEquals(listOf("Kingdoms of Ice S01E04-thumb.jpg", "Kingdoms of Ice S01E04.nfo", "Kingdoms of Ice S01E04.ts"), season.list()?.sorted())
        // The show's own stay for its other episodes; the film's go with it.
        assertTrue(onShare("TV Recordings/Kingdoms of Ice/tvshow.nfo").exists())
        assertTrue(onShare("TV Recordings/Kingdoms of Ice/poster.jpg").exists())
        assertEquals(emptyList<String>(), onShare("Movie Recordings/The Last Lighthouse").list()?.toList())
    }

    @Test fun episodesOnTheShareAreCountedByTheirNames() {
        assertEquals(4, NetworkCopier.episodeOf("Late Night Sessions S2026E004.ts"))
        assertEquals(12, NetworkCopier.episodeOf("Kingdoms of Ice S01E12.ts"))
        assertNull(NetworkCopier.episodeOf("Late Night Sessions S2026E004.ts.partial"))
        assertNull(NetworkCopier.episodeOf("Late Night Sessions S2026E004.nfo"))
    }
}
