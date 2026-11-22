// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections
import kotlin.concurrent.thread

/** Text set as Plasma starts (#127), on a thread of its own, in every weight of Instrument Sans. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TextWarmUpTest {
    @Test
    fun setsTextInEachStyleOffTheMainThread() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var failure: Throwable? = null
        thread { failure = runCatching { warmUpText(context) }.exceptionOrNull() }.join()
        assertNull(failure)
    }

    /**
     * A composition reads state as it was when the composition began. Compose's text code makes
     * state as it sets text for the first time (whether Android's emoji font is loaded), and at a
     * start it's the warm-up that sets text first, while the launch screen, which measures its
     * wordmark as it's composed, may already be in its first composition. So state made as the
     * warm-up sets a line has to be readable by a composition that began before the warm-up did.
     * Made on another thread outside a snapshot it isn't: reading it throws, which closed Plasma.
     */
    @Test
    fun stateMadeAsItSetsTextIsReadByACompositionThatBeganBefore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val composition = Snapshot.takeMutableSnapshot()
        try {
            // What this guards against: state another thread makes outside a snapshot, once the
            // composition has begun, can't be read in it.
            lateinit var outside: State<Boolean>
            thread { outside = mutableStateOf(false) }.join()
            assertTrue(runCatching { composition.enter { outside.value } }.exceptionOrNull() is IllegalStateException)
            // State made as the warm-up sets each of its lines can.
            val made = Collections.synchronizedList(ArrayList<State<Boolean>>())
            var failure: Throwable? = null
            thread {
                failure = runCatching {
                    warmUpText(context) { measurer, style ->
                        made += mutableStateOf(false)
                        measurer.measure(WARM_UP_LINE, style)
                    }
                }.exceptionOrNull()
            }.join()
            assertNull(failure)
            assertEquals(WARM_UP_STYLES.size, made.size)
            composition.enter { made.forEach { assertFalse(it.value) } }
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun theStylesHaveEveryWeightAndTabularFigures() {
        assertEquals(setOf(400, 500, 600, 700), WARM_UP_STYLES.mapNotNull { it.fontWeight?.weight }.toSet())
        assertTrue(WARM_UP_STYLES.all { it.fontFamily == InstrumentSans })
        assertTrue(WARM_UP_STYLES.any { it.fontFeatureSettings == "tnum" })
        assertTrue(WARM_UP_STYLES.any { it.fontFeatureSettings == null })
    }
}
