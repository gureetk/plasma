// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.guide

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Which cell draws which program as the window moves under a Guide row (#127): one that stays keeps
 * its cell, one that comes in takes the cell of one that left, and the order the cells are composed
 * in is left as it was. And where the row puts a cell, from its program's times.
 */
class GuideCellKeysTest {
    private val keys = CellKeys()

    /** The cells of [ids] in the order they're composed in, as "key:id". */
    private fun composed(vararg ids: String): List<String> {
        val keysOf = keys.keysFor(ids.toList())
        return inKeyOrder(keysOf).map { "${keysOf[it]}:${ids[it]}" }
    }

    @Test fun aRowsFirstCellsAreComposedInTheOrderOfTheirPrograms() {
        assertEquals(listOf("0:a", "1:b", "2:c", "3:d"), composed("a", "b", "c", "d"))
    }

    @Test fun aProgramThatStaysKeepsItsCellAndTheOneThatComesInTakesTheCellOfTheOneThatLeft() {
        composed("a", "b", "c", "d")
        // The window moves on: the first program is out, and one comes in at the end.
        assertEquals(listOf("0:e", "1:b", "2:c", "3:d"), composed("b", "c", "d", "e"))
        // And back: the last is out, and the first comes in again, in the cell it had.
        assertEquals(listOf("0:a", "1:b", "2:c", "3:d"), composed("a", "b", "c", "d"))
    }

    @Test fun askedAgainWithTheSameProgramsItAnswersTheSame() {
        composed("a", "b", "c", "d")
        val moved = composed("b", "c", "d", "e")
        assertEquals(moved, composed("b", "c", "d", "e"))
        assertEquals(moved, composed("b", "c", "d", "e"))
    }

    @Test fun withNoCellToTakeAProgramGetsANewOneAfterTheOthers() {
        composed("a", "b", "c")
        // Two short programs come in where one left.
        assertEquals(listOf("0:d", "1:b", "2:c", "3:e"), composed("b", "c", "d", "e"))
    }

    @Test fun aCellNoProgramTakesIsLetGo() {
        composed("a", "b", "c", "d")
        assertEquals(listOf("2:c", "3:d"), composed("c", "d"))
        // What comes in later gets new cells, after the others.
        assertEquals(listOf("2:c", "3:d", "4:e", "5:f"), composed("c", "d", "e", "f"))
    }

    @Test fun anotherDaysProgramsTakeTheCellsOfTheDayBefore() {
        composed("a", "b", "c")
        assertEquals(listOf("0:x", "1:y", "2:z"), composed("x", "y", "z"))
    }

    @Test fun aProgramThereTwiceHasTwoCells() {
        assertEquals(listOf("0:a", "1:a", "2:b"), composed("a", "a", "b"))
        assertEquals(listOf("0:a", "1:a", "2:b"), composed("a", "a", "b"))
    }

    @Test fun noTwoProgramsOfARowShareACellHoweverTheWindowMoves() {
        val random = Random(8)
        val day = List(40) { "program-$it" }
        var first = 0
        repeat(500) {
            // A window of one to seven programs, moved up to three programs either way.
            first = (first + random.nextInt(-3, 4)).coerceIn(0, day.size - 1)
            val window = day.subList(first, minOf(day.size, first + random.nextInt(1, 8)))
            val before = keys.keysFor(window)
            assertEquals("Keys of $window: ${before.toList()}", window.size, before.toSet().size)
            assertArrayEquals(before, keys.keysFor(window))
        }
    }

    @Test fun theOrderOfTheCellsIsTheOrderOfTheirKeys() {
        assertArrayEquals(intArrayOf(3, 0, 1, 2), inKeyOrder(intArrayOf(1, 2, 3, 0)))
        assertArrayEquals(intArrayOf(0, 1, 2), inKeyOrder(intArrayOf(4, 7, 9)))
        assertArrayEquals(intArrayOf(), inKeyOrder(intArrayOf()))
    }

    // ---- Where a cell goes ----------------------------------------------------------------------

    private val window = 1_000_000_000_000L
    private val minute = 60_000L

    @Test fun halfAnHourIsAQuarterOfTheWindow() {
        assertEquals(0f, cellLeft(window, window), 0.01f)
        assertEquals(WINDOW_PX / 4, cellWidth(window, window + 30 * minute, window), 0.01f)
        assertEquals(WINDOW_PX / 2, cellLeft(window + 60 * minute, window), 0.01f)
        assertEquals(WINDOW_PX / 4, cellWidth(window + 60 * minute, window + 90 * minute, window), 0.01f)
    }

    @Test fun aProgramThatStartedBeforeTheWindowIsCutAtItsStart() {
        assertEquals(0f, cellLeft(window - 45 * minute, window), 0.01f)
        assertEquals(WINDOW_PX / 8, cellWidth(window - 45 * minute, window + 15 * minute, window), 0.01f)
    }

    @Test fun aProgramThatEndsAfterTheWindowIsCutAtItsEnd() {
        assertEquals(WINDOW_PX * 7 / 8, cellLeft(window + 105 * minute, window), 0.01f)
        assertEquals(WINDOW_PX / 8, cellWidth(window + 105 * minute, window + 240 * minute, window), 0.01f)
    }

    @Test fun aProgramLongerThanTheWindowFillsIt() {
        assertEquals(0f, cellLeft(window - 60 * minute, window), 0.01f)
        assertEquals(WINDOW_PX, cellWidth(window - 60 * minute, window + 300 * minute, window), 0.01f)
    }
}
