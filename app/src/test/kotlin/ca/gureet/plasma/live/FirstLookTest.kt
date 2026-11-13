// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.NumberingSettings
import ca.gureet.plasma.core.data.Programme
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Home's first look as Plasma starts (#127): first when it's quicker, and never after the whole world. */
class FirstLookTest {
    private var bareCollections = 0

    /** A world without a guide each minute, as the clock moves on. */
    private val bare: Flow<LiveWorld> = flow {
        bareCollections++
        var minute = 0L
        while (true) {
            emit(world(minute))
            delay(MINUTE_MS)
            minute++
        }
    }

    private fun world(minute: Long) = LiveWorld(
        channels = emptyList(),
        groups = emptyList(),
        favoriteIds = emptyList(),
        recentIds = emptyList(),
        programmes = emptyMap(),
        numbering = NumberingSettings(),
        lastWatched = null,
        now = Instant.ofEpochSecond(minute * 60),
        guideWhole = false,
    )

    /** The whole world, once its guide window has taken [loadMs] to load. */
    private fun whole(loadMs: Long): (Flow<LiveWorld>) -> Flow<LiveWorld> = { bares ->
        combine(bares, flow { delay(loadMs); emit(WINDOW) }) { world, programmes -> world.copy(programmes = programmes, guideWhole = true) }
    }

    /** The first look, after [readMs] reading a few channels' guide. */
    private fun look(readMs: Long): suspend (LiveWorld) -> LiveWorld = { world ->
        delay(readMs)
        world.copy(programmes = FEW)
    }

    @Test fun theFirstLookGoesFirstWhileTheWindowLoads() = runTest {
        val worlds = firstLookThenWhole(bare, whole(loadMs = 3_000), look(readMs = 20)).take(2).toList()

        assertEquals(listOf(false to FEW, true to WINDOW), worlds.map { it.guideWhole to it.programmes })
        assertEquals(1, bareCollections)
    }

    @Test fun noFirstLookAfterTheWholeWorld() = runTest {
        // The window's in before the first look is made: the whole worlds alone, minute after minute.
        val worlds = firstLookThenWhole(bare, whole(loadMs = 20), look(readMs = 3_000)).take(3).toList()

        assertEquals(listOf(true, true, true), worlds.map { it.guideWhole })
        assertEquals(listOf(0L, 60L, 120L), worlds.map { it.now.epochSecond })
        assertEquals(1, bareCollections)
    }

    @Test fun theWholeWorldsCarryOnAfterTheFirstLook() = runTest {
        val worlds = firstLookThenWhole(bare, whole(loadMs = 3_000), look(readMs = 20)).take(4).toList()

        assertEquals(listOf(false, true, true, true), worlds.map { it.guideWhole })
        assertEquals(listOf(0L, 0L, 60L, 120L), worlds.map { it.now.epochSecond })
    }

    @Test fun aFirstLookThatFailsLeavesTheWholeWorld() = runTest {
        val failing: suspend (LiveWorld) -> LiveWorld = { error("The guide couldn't be read") }
        val worlds = firstLookThenWhole(bare, whole(loadMs = 3_000), failing).take(2).toList()

        assertEquals(listOf(true, true), worlds.map { it.guideWhole })
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        val FEW = mapOf("few" to emptyList<Programme>())
        val WINDOW = mapOf("all" to emptyList<Programme>())
    }
}
