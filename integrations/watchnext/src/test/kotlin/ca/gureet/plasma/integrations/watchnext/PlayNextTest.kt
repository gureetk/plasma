// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.integrations.watchnext

import android.database.MatrixCursor
import android.media.tv.TvContract
import android.provider.BaseColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** Android's Play Next row as Plasma reads it (brief 5.19). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlayNextTest {
    private val columns = arrayOf(
        BaseColumns._ID,
        TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
        TvContract.WatchNextPrograms.COLUMN_CONTENT_ID,
        TvContract.WatchNextPrograms.COLUMN_TITLE,
        TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
        TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
        TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
        TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
        TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
        TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
        TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
        TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
        TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
        TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
    )

    private fun rows(vararg rows: Array<Any?>): List<PlayNextItem?> {
        val cursor = MatrixCursor(columns)
        rows.forEach(cursor::addRow)
        return buildList { while (cursor.moveToNext()) add(cursor.toPlayNextItem()) }
    }

    @Test fun eachRowIsAnItem() {
        val (episode, movie) = rows(
            arrayOf(12L, "tv.example.reelbox", "ep-204", "Night Stint", "The Gauntlet", "2", "4", "https://img.example/s2e4.jpg",
                TvContract.WatchNextPrograms.ASPECT_RATIO_16_9, 2_100_000L, 3_480_000L, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE,
                1_792_000_000_000L, "intent://open/ep-204#Intent;package=tv.example.reelbox;end", 1),
            arrayOf(20L, "tv.example.tumble", null, "Polar Night", null, null, null, "https://img.example/polar.jpg",
                TvContract.WatchNextPrograms.ASPECT_RATIO_2_3, 0L, 6_000_000L, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT,
                1_791_000_000_000L, null, null),
        )

        assertEquals(
            PlayNextItem(
                id = 12, packageName = "tv.example.reelbox", contentId = "ep-204", title = "Night Stint", episodeTitle = "The Gauntlet", season = "2", episode = "4",
                poster = "https://img.example/s2e4.jpg", wide = true, positionMs = 2_100_000, durationMs = 3_480_000, type = PlayNextType.Continue,
                lastWatched = Instant.ofEpochMilli(1_792_000_000_000L), intentUri = "intent://open/ep-204#Intent;package=tv.example.reelbox;end",
            ),
            episode,
        )
        assertEquals("tv.example.reelbox|ep-204", episode?.key)
        assertEquals(false, movie?.wide)
        assertEquals(PlayNextType.Next, movie?.type)
        assertEquals("tv.example.tumble|20", movie?.key)
    }

    @Test fun rowsThatArentShownAreLeftOut() {
        val (hidden, untitled) = rows(
            arrayOf(30L, "tv.example.reelbox", null, "Hidden", null, null, null, null, null, 0L, 0L, 0, 0L, null, 0),
            arrayOf(31L, "tv.example.reelbox", null, " ", null, null, null, null, null, 0L, 0L, 0, 0L, null, 1),
        )
        assertNull(hidden)
        assertNull(untitled)
    }
}
