// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * On a device, in an activity of its own with Android's own frames, and not under a test's clock:
 * a followed value moves in the very frames `animateFloatAsState`'s does (#127).
 *
 * The unit test holds the two equal frame by frame under a test's clock, where a coroutine runs
 * the moment it's started. On Android a coroutine started while a frame is made is queued until
 * that frame is done, and `animateFloatAsState` goes through that queue twice where a followed
 * value goes once; both are through it before the next frame, and this is where a frame's
 * difference would show if they weren't. Each frame that's drawn notes the two values, as focus
 * comes, leaves, and turns back part way, and they're the same in every frame. It logs what it
 * found under `Following`. Run it on an Android TV emulator or a TV:
 * `./gradlew :core:design:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class FollowingOnDeviceTest {
    private var target by mutableFloatStateOf(0f)

    /** The animated value and the followed one, as each frame was drawn. */
    private val drawn = CopyOnWriteArrayList<Pair<Float, Float>>()

    private fun ActivityScenario<ComponentActivity>.aim(at: Float, thenWaitMs: Long) {
        onActivity { target = at }
        Thread.sleep(thenWaitMs)
    }

    @Test fun aFollowedValueMovesInTheFramesAnAnimatedOneDoes() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(
                    ComposeView(activity).apply {
                        setContent {
                            val motion = rememberCoroutineScope()
                            val animated = animateFloatAsState(target, PlasmaMotion.focus(), label = "animated")
                            val followed = followFloatAsState(target, PlasmaMotion.focus(), motion)
                            Box(
                                Modifier.fillMaxSize().drawBehind {
                                    // Drawn after every animation has had this frame's time.
                                    val now = animated.value to followed.value
                                    drawn += now
                                    drawRect(Color.White, alpha = now.second)
                                },
                            )
                        }
                    },
                )
            }
            Thread.sleep(REST_MS)
            repeat(ROUNDS) {
                scenario.aim(1f, REST_MS)
                scenario.aim(0f, REST_MS)
                // Turned back part way.
                scenario.aim(1f, PART_WAY_MS)
                scenario.aim(0f, REST_MS)
            }
        }
        val apart = drawn.filter { it.first != it.second }
        val partWay = drawn.count { it.second > 0f && it.second < 1f }
        Log.i(TAG, "${drawn.size} frames drawn, $partWay of them part way, the two apart in ${apart.size}")
        assertTrue("Frames were drawn part way: $partWay of ${drawn.size}", partWay >= ROUNDS)
        assertTrue("The two were apart in ${apart.size} frames of ${drawn.size}: ${apart.take(SHOWN)}", apart.isEmpty())
    }

    private companion object {
        const val TAG = "Following"

        /** Times focus comes, leaves, and turns back part way. */
        const val ROUNDS = 6

        /** Long enough for a move of 150 ms to end, on a slow emulator too. */
        const val REST_MS = 500L

        /** Part way through a move of 150 ms. */
        const val PART_WAY_MS = 60L

        /** How many frames a failure names. */
        const val SHOWN = 8
    }
}
