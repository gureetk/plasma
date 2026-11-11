// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    @Test
    fun theStylesHaveEveryWeightAndTabularFigures() {
        assertEquals(setOf(400, 500, 600, 700), WARM_UP_STYLES.mapNotNull { it.fontWeight?.weight }.toSet())
        assertTrue(WARM_UP_STYLES.all { it.fontFamily == InstrumentSans })
        assertTrue(WARM_UP_STYLES.any { it.fontFeatureSettings == "tnum" })
        assertTrue(WARM_UP_STYLES.any { it.fontFeatureSettings == null })
    }
}
