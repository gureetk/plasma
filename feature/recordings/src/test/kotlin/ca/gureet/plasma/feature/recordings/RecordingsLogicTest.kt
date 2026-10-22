// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.recordings

import ca.gureet.plasma.core.fixtures.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingsLogicTest {
    private val recordings = RecordingsSamples.recordings

    @Test fun aSeriesWithSeveralEpisodesIsAFolder() {
        val tiles = recordedTiles(recordings, RecordingFilter.All, RecordingSort.Newest)
        assertEquals(10, tiles.size)
        val folder = tiles.filterIsInstance<RecordedTile.Folder>().single()
        assertEquals("Kingdoms of Ice", folder.title)
        assertEquals(3, folder.episodes.size)
        assertEquals(2, folder.unwatchedCount)
        assertEquals("kingdoms3", folder.newest.id)
    }

    @Test fun newestFirstPutsTheRecordingInProgressFirstAndAFolderByItsNewestEpisode() {
        val ids = recordedTiles(recordings, RecordingFilter.All, RecordingSort.Newest).map { it.id }
        assertEquals(
            listOf("harbour", "business", "football", "sessions", "series:kingdoms", "lighthouse", "endurance", "deepfield", "stormwatch", "bakers"),
            ids,
        )
    }

    @Test fun aToZIgnoresTheArticle() {
        val titles = recordedTiles(recordings, RecordingFilter.All, RecordingSort.AZ).map { it.title }
        assertEquals("Late Night Sessions", titles[titles.indexOf("The Last Lighthouse") + 1])
    }

    @Test fun filtersCountRecordingsNotTiles() {
        assertEquals(6, recordings.count { RecordingFilter.Series.matches(it) })
        assertEquals(3, recordings.count { RecordingFilter.Sports.matches(it) })
        assertEquals(2, recordings.count { RecordingFilter.News.matches(it) })
        assertEquals(1, recordings.count { RecordingFilter.Movies.matches(it) })
    }

    @Test fun anOpenFolderListsItsEpisodes() {
        val tiles = recordedTiles(recordings, RecordingFilter.All, RecordingSort.Newest, folder = "kingdoms")
        assertEquals(listOf("kingdoms3", "kingdoms2", "kingdoms1"), tiles.map { it.id })
    }

    @Test fun totalCountsTheRecordingInProgressSoFar() {
        // 56 minutes of Harbour City v Redmoor so far, and 30 h 03 min of the rest: about 31 hours (board 17).
        assertEquals(1_859, totalRecorded(recordings, Sample.now).toMinutes())
    }

    @Test fun theWarningShowsTwoDaysAheadUnlessKept() {
        val deepField = recordings.first { it.id == "deepfield" }
        assertTrue(deepField.deletesSoon(Sample.today))
        assertFalse(deepField.copy(kept = true).deletesSoon(Sample.today))
        assertFalse(deepField.deletesSoon(Sample.today.minusDays(1)))
    }

    @Test fun storageEstimatesHoursOfHd() {
        val storage = StorageInfo("USB drive", 212_000_000_000, 500_000_000_000)
        assertEquals(96, storage.hoursInHd)
        assertEquals(0.576f, storage.usedFraction, 0.001f)
    }
}
