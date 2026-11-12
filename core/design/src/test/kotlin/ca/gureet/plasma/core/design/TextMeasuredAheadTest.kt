// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.LocalBackgroundTextMeasurementExecutor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import ca.gureet.plasma.core.testing.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Text measured ahead of the main thread (#127): given somewhere to do it, Compose hands each of
 * Plasma's texts of a few words or more on as it's composed, what's handed on can be done on
 * another thread, and the text comes out the size it would have.
 */
class TextMeasuredAheadTest : ScreenshotTest() {
    private val handed = mutableListOf<Runnable>()

    /** When true, a text is measured as it's handed on: before the main thread lays it out, as it is when another thread keeps ahead. */
    private var atOnce = false
    private val somewhere = Executor {
        handed += it
        if (atOnce) it.run()
    }
    private var ahead by mutableStateOf(true)
    private var words by mutableStateOf(listOf(LONG))

    private fun texts() = screen {
        CompositionLocalProvider(LocalBackgroundTextMeasurementExecutor provides if (ahead) somewhere else null) {
            Column { for (text in words) PlasmaText(text, PlasmaType.ListTitle) }
        }
    }

    /** Compose only hands text on with four cores or more, which GitHub's machines have. */
    private fun assumeEnoughCores() = assumeTrue("Four cores or more", Runtime.getRuntime().availableProcessors() >= CORES)

    @Test fun aTextOfAFewWordsIsHandedOnAsItsComposed() {
        assumeEnoughCores()
        words = listOf(SHORT)
        texts()
        compose.waitForIdle()
        assertEquals("A short one isn't worth handing on", 0, handed.size)

        words = listOf(SHORT, LONG)
        compose.waitForIdle()
        assertTrue("One of a few words is", handed.isNotEmpty())

        val before = handed.size
        words = listOf(SHORT, OTHER)
        compose.waitForIdle()
        assertTrue("And again with other words", handed.size > before)
    }

    @Test fun whatsHandedOnCanBeDoneOnAnotherThread() {
        assumeEnoughCores()
        texts()
        compose.waitForIdle()
        assertTrue(handed.isNotEmpty())
        val failure = AtomicReference<Throwable>()
        val other = thread(name = "text-ahead-test") { runCatching { handed.toList().forEach { it.run() } }.onFailure { failure.set(it) } }
        other.join(WAIT_MS)
        assertFalse("It ends", other.isAlive)
        assertEquals("Nothing goes wrong there", null, failure.get())
    }

    @Test fun aTextMeasuredAheadIsTheSizeItWouldHaveBeen() {
        assumeEnoughCores()
        ahead = false
        texts()
        val plain = compose.onNodeWithText(LONG).getBoundsInRoot()
        assertEquals("Nowhere to hand it on, so nothing is", 0, handed.size)

        // Other words, then the first ones again, each measured ahead before it's laid out.
        atOnce = true
        ahead = true
        words = listOf(OTHER)
        compose.waitForIdle()
        words = listOf(LONG)
        compose.waitForIdle()
        assertTrue(handed.isNotEmpty())
        compose.onNodeWithText(LONG).assertWidthIsEqualTo(plain.width).assertHeightIsEqualTo(plain.height)
    }

    private companion object {
        const val CORES = 4
        const val WAIT_MS = 10_000L

        /** Eight characters is the least Compose hands on. */
        const val LONG = "Match of the Week Extra"
        const val OTHER = "Football Tonight"
        const val SHORT = "8:00 PM"
    }
}
