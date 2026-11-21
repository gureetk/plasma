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
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Home's first look as Plasma starts (#127): first, with the guide window not read until it's out;
 * and never after the whole world, when it's slow.
 */
class FirstLookTest {
    private var bareCollections = 0

    /** When the guide window began to be read, by the test's clock: -1 until it has. */
    private var windowAskedAtMs = -1L

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

    /** The whole world, once its guide window has taken [loadMs] to load; [now] is the test's clock. */
    private fun whole(loadMs: Long, now: () -> Long = { 0 }): (Flow<LiveWorld>) -> Flow<LiveWorld> = { bares ->
        val window = flow {
            windowAskedAtMs = now()
            delay(loadMs)
            emit(WINDOW)
        }
        combine(bares, window) { world, programmes -> world.copy(programmes = programmes, guideWhole = true) }
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

    @Test fun theWindowIsntReadUntilTheFirstLookIsOut() = runTest {
        val worlds = firstLookThenWhole(bare, whole(loadMs = 3_000) { currentTime }, look(readMs = 200)).take(2).toList()

        assertEquals(listOf(false, true), worlds.map { it.guideWhole })
        assertEquals("The window was read beside the first look", 200L, windowAskedAtMs)
    }

    @Test fun aSlowFirstLookHoldsTheWindowBackOnlySoLong() = runTest {
        val worlds = firstLookThenWhole(bare, whole(loadMs = 20) { currentTime }, look(readMs = 3_000)).take(1).toList()

        assertEquals(listOf(true), worlds.map { it.guideWhole })
        assertEquals(FIRST_LOOK_ALONE_MS, windowAskedAtMs)
    }

    @Test fun aFirstLookThatFailsDoesntHoldTheWindowBack() = runTest {
        val failing: suspend (LiveWorld) -> LiveWorld = {
            delay(50)
            error("The guide couldn't be read")
        }
        firstLookThenWhole(bare, whole(loadMs = 20) { currentTime }, failing).take(1).toList()

        assertEquals(50L, windowAskedAtMs)
    }

    @Test fun noFirstLookAfterTheWholeWorld() = runTest {
        // The first look is too slow to wait for, and the window's in before it's made: the whole
        // worlds alone, minute after minute.
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
