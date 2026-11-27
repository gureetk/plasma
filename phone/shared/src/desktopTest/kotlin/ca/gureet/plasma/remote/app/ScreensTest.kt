// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ca.gureet.plasma.remote.app.ui.RemoteTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * The app's screens drawn as the board's phones are (board 33: 360 × 760 at three pixels a dp),
 * from the demo TV, into build/screens, to compare with the board.
 */
class ScreensTest {
    private fun draw(name: String, tab: Tab, typed: String? = null) {
        val density = 3f
        val scene = ImageComposeScene(width = (360 * density).toInt(), height = (760 * density).toInt(), density = Density(density)) {
            RemoteTheme {
                val tv = rememberDemoTv("Living Room TV")
                if (typed != null) tv.text(typed)
                RemoteScreens(tv, initialTab = tab)
            }
        }
        try {
            // A few frames: the first loads the fonts, then the logos come.
            repeat(4) { scene.render(it * 100_000_000L) }
            Thread.sleep(300)
            val image = scene.render(1_000_000_000)
            val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
            val file = File("build/screens/$name.png").apply { parentFile.mkdirs() }
            file.writeBytes(png)
            assertTrue(file.length() > 0)
        } finally {
            scene.close()
        }
    }

    @Test fun theRemoteTab() = draw("33-remote", Tab.Remote)

    @Test fun theChannelsTab() = draw("33-channels", Tab.Channels)

    /** The board's "night", which finds 2 programs and 2 movies in the demo. */
    @Test fun theTypeTab() = draw("33-type", Tab.Type, typed = "night")
}
