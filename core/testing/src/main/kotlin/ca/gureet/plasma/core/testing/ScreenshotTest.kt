// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.testing

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.fixtures.SampleHost
import ca.gureet.plasma.core.model.PlasmaClock
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A screen test at the boards' size: 1920 × 1080 px (960 × 540 dp at xhdpi) on a TV, with real
 * rendering. Extend it, call [screen] with the composable, then [capture] it as
 * `src/test/screenshots/<name>.png`, named after the board (`01-home`).
 *
 * `./gradlew recordRoborazziDebug` writes the images; CI compares against them and fails on a
 * difference (`-Proborazzi.test.verify=true`).
 *
 * Focus tests use the same setup: [press] remote keys and check what's [focused].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TV_1080P, sdk = [35])
abstract class ScreenshotTest {
    @get:Rule val compose: ComposeContentTestRule = createComposeRule()

    /** Shows [content] in the sample-data host, at 20:41 on the boards' day unless [clock] says otherwise. */
    fun screen(clock: PlasmaClock = Sample.clock, content: @Composable () -> Unit) {
        compose.setContent { SampleHost(clock = clock, content = content) }
    }

    fun capture(name: String) {
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", Options)
    }

    /** Presses a remote key (D-pad, OK, a color key) [times] times on whatever has focus. */
    @OptIn(ExperimentalTestApi::class)
    fun press(key: Key, times: Int = 1) {
        repeat(times) {
            compose.onRoot().performKeyInput { pressKey(key) }
            compose.waitForIdle()
        }
    }

    /**
     * Presses Back the way the remote does, through the activity: what the screen doesn't take
     * goes on to the activity's Back handling (`BackHandler`), as on a TV. [press] with `Key.Back`
     * only reaches the screen.
     */
    fun back() {
        val activity = (compose as AndroidComposeTestRule<*, *>).activity
        compose.runOnUiThread {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.waitForIdle()
    }

    /** The node that has focus now; check it with `assert(hasText(…))` and the like. */
    fun focused(): SemanticsNodeInteraction = compose.onNode(isFocused())

    companion object {
        /** Tolerates the odd anti-aliased pixel between machines. */
        val Options = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f),
        )
    }
}

/** Robolectric qualifiers for a 1080p TV: 960 × 540 dp at xhdpi (2 px per dp). */
const val TV_1080P = "w960dp-h540dp-land-television-xhdpi"
