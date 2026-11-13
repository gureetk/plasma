// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * A check, not a test of Plasma, and not for `main`: how many times the screen is drawn after one
 * change, by what the change is (#127). The Guide's speed test showed a second frame that only
 * draws after every key that puts focus on a newly made cell, and after about as many keys as
 * move focus to another cell otherwise; a cell gets a layer of its own for its shadow when focus
 * comes. Each case here makes one change, waits, and counts the view's draws; it logs them under
 * `SecondDraw`.
 */
@RunWith(AndroidJUnit4::class)
class DrawsAfterAChangeCheck {
    private var case by mutableIntStateOf(0)
    private var on by mutableStateOf(false)
    private val draws = AtomicInteger()

    private val cases: List<Pair<String, @Composable (Boolean) -> Unit>> = listOf(
        "a fill's color changes" to { on -> Cell(Modifier.background(if (on) Color.White else Color.DarkGray, PlasmaShapes.Cell)) },
        "a shadow is added, as a Guide cell's is" to { on ->
            Cell(
                Modifier
                    .then(if (on) Modifier.shadow(10.px, PlasmaShapes.Cell, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                    .background(if (on) Color.White else Color.DarkGray, PlasmaShapes.Cell),
            )
        },
        "a shadow comes on a layer that's always there" to { on ->
            Cell(
                Modifier
                    .graphicsLayer {
                        shadowElevation = if (on) 10.px.toPx() else 0f
                        shape = PlasmaShapes.Cell
                        clip = false
                        ambientShadowColor = Color.Black
                        spotShadowColor = Color.Black
                    }
                    .background(if (on) Color.White else Color.DarkGray, PlasmaShapes.Cell),
            )
        },
        "a plain layer is added" to { on ->
            Cell(Modifier.then(if (on) Modifier.graphicsLayer() else Modifier).background(Color.DarkGray, PlasmaShapes.Cell))
        },
        "a faded kept layer is added, as a past program's is" to { on ->
            Cell(
                Modifier
                    .then(if (on) Modifier.graphicsLayer(alpha = 0.6f, clip = true, compositingStrategy = CompositingStrategy.Offscreen) else Modifier)
                    .background(Color.DarkGray, PlasmaShapes.Cell),
            )
        },
        "a cell with no layer is made" to { on -> if (on) Cell(Modifier.background(Color.DarkGray, PlasmaShapes.Cell)) },
        "a cell with a shadow is made" to { on ->
            if (on) {
                Cell(
                    Modifier
                        .shadow(10.px, PlasmaShapes.Cell, ambientColor = Color.Black, spotColor = Color.Black)
                        .background(Color.White, PlasmaShapes.Cell),
                )
            }
        },
        "a shadow is added inside a kept row" to { on ->
            Box(Modifier.keptLayer(true)) {
                Cell(
                    Modifier
                        .then(if (on) Modifier.shadow(10.px, PlasmaShapes.Cell, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                        .background(if (on) Color.White else Color.DarkGray, PlasmaShapes.Cell),
                )
            }
        },
    )

    @Composable
    private fun Cell(modifier: Modifier) {
        Box(Modifier.padding(24.px).size(300.px, 62.px).then(modifier))
    }

    @Test fun howManyDrawsFollowOneChange() {
        val found = ArrayList<String>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = ComposeView(activity).apply {
                    setContent {
                        Column(Modifier.fillMaxSize().background(Color.Black)) {
                            val (_, content) = cases[case]
                            content(on)
                        }
                    }
                }
                activity.setContentView(view)
                view.viewTreeObserver.addOnDrawListener { draws.incrementAndGet() }
            }
            for (index in cases.indices) {
                scenario.onActivity {
                    on = false
                    case = index
                }
                Thread.sleep(REST_MS)
                val turnedOn = ArrayList<Int>()
                val turnedOff = ArrayList<Int>()
                repeat(ROUNDS) {
                    turnedOn += scenario.drawsAfter { on = true }
                    turnedOff += scenario.drawsAfter { on = false }
                }
                val line = "${cases[index].first}: on $turnedOn, off $turnedOff"
                Log.i(TAG, line)
                found += line
            }
        }
        assertTrue("Nothing was drawn after a change: $found", found.isNotEmpty() && draws.get() > 0)
    }

    /** The view's draws in the [REST_MS] after [change], made on the main thread. */
    private fun ActivityScenario<ComponentActivity>.drawsAfter(change: () -> Unit): Int {
        onActivity {
            draws.set(0)
            change()
        }
        Thread.sleep(REST_MS)
        return draws.get()
    }

    private companion object {
        const val TAG = "SecondDraw"
        const val ROUNDS = 4

        /** Long enough for any frame a change brings to be drawn, on a slow emulator too. */
        const val REST_MS = 600L
    }
}
