// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import ca.gureet.plasma.remote.app.pairing.FindTvScreen
import ca.gureet.plasma.remote.app.pairing.PairScreen
import ca.gureet.plasma.remote.app.pairing.PairTarget
import ca.gureet.plasma.remote.app.pairing.ScanScreen
import ca.gureet.plasma.remote.app.settings.MemoryPrefs
import ca.gureet.plasma.remote.app.settings.PhoneSettings
import ca.gureet.plasma.remote.app.settings.SettingsScreen
import ca.gureet.plasma.remote.app.tv.FoundTv
import ca.gureet.plasma.remote.app.tv.PairedTv
import ca.gureet.plasma.remote.app.tv.PhoneApp
import ca.gureet.plasma.remote.app.tv.TvEndpoint
import ca.gureet.plasma.remote.app.tv.TvFinder
import ca.gureet.plasma.remote.app.tv.Vault
import ca.gureet.plasma.remote.app.ui.RemoteTheme
import ca.gureet.plasma.remote.protocol.RemoteAdvert
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.skia.EncodedImageFormat

/**
 * The app's screens drawn as the board's phones are (board 33: 360 × 760 at three pixels a dp),
 * from the demo TV and made-up TVs, into build/screens, to compare with the board.
 */
class ScreensTest {
    private val app = PhoneApp("android", "0.1.0")

    private fun draw(name: String, content: @Composable () -> Unit) {
        val density = 3f
        val scene = ImageComposeScene(width = (360 * density).toInt(), height = (760 * density).toInt(), density = Density(density)) {
            RemoteTheme { content() }
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

    private fun drawTab(name: String, tab: Tab, typed: String? = null) = draw(name) {
        val tv = rememberDemoTv("Living Room TV")
        if (typed != null) tv.text(typed)
        RemoteScreens(tv, initialTab = tab)
    }

    @Test fun theRemoteTab() = drawTab("33-remote", Tab.Remote)

    @Test fun theChannelsTab() = drawTab("33-channels", Tab.Channels)

    /** The board's "night", which finds 2 programs and 2 movies in the demo. */
    @Test fun theTypeTab() = drawTab("33-type", Tab.Type, typed = "night")

    /** The first time: no TVs paired, none found yet. */
    @Test fun findYourTvTheFirstTime() = draw("33-find-first") {
        val nothing = object : Vault {
            override fun read(): ByteArray? = null
            override fun write(bytes: ByteArray?) = Unit
        }
        PlasmaRemoteApp(Phone(app, "Maya’s Phone", nothing, TvFinder { flowOf(emptyList()) }, MemoryPrefs()))
    }

    /** One TV paired and on the Wi-Fi, another on the Wi-Fi to pair. */
    @Test fun findYourTv() = draw("33-find") {
        val bedroom = PairedTv("tv-2", "Bedroom TV", "192.168.1.50", 8486, "B".repeat(43), "t0k3n")
        val found = listOf(
            FoundTv(RemoteAdvert("tv-1", "Living Room TV", 1, 8486), "192.168.1.42"),
            FoundTv(RemoteAdvert("tv-2", "Bedroom TV", 1, 8486), "192.168.1.50"),
        )
        AppFrame(null, connected = false) {
            FindTvScreen(listOf(bedroom), found, note = null, onPaired = {}, onPair = {}, onScan = {}, onDemo = {})
        }
    }

    /** The camera's picture is a plain box here. */
    @Test fun scanning() = draw("33-scan") {
        ScanScreen(onInvite = {}, onCancel = {})
    }

    /** After scanning: the code came with the QR code, so only the name is asked for. */
    @Test fun namingAfterScanning() = draw("33-pair-scanned") {
        AppFrame(null, connected = false) {
            PairScreen(PairTarget("Living Room TV", TvEndpoint("192.168.1.42", 8486), "A".repeat(43), "7KQ4MZ"), "Maya’s Phone", app, onPaired = {}, onCancel = {})
        }
    }

    @Test fun settings() = draw("33-settings") {
        val livingRoom = PairedTv("tv-1", "Living Room TV", "192.168.1.42", 8486, "A".repeat(43), "t0k3n")
        val settings = remember { PhoneSettings(MemoryPrefs()) }
        AppFrame(null, connected = false) {
            SettingsScreen(settings, listOf(livingRoom), app.version, onForget = {}, onDemo = {}, onDone = {})
        }
    }

    @Test fun pairing() = draw("33-pair") {
        AppFrame(null, connected = false) {
            PairScreen(PairTarget("Living Room TV", TvEndpoint("192.168.1.42", 8486)), "Maya’s Phone", app, onPaired = {}, onCancel = {})
        }
    }
}
