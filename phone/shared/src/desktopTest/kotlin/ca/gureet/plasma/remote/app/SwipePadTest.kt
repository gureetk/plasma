// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import ca.gureet.plasma.remote.app.tv.LinkStatus
import ca.gureet.plasma.remote.app.tv.TvLink
import ca.gureet.plasma.remote.app.ui.RemoteTheme
import ca.gureet.plasma.remote.app.ui.SwipePad
import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemoteLists
import ca.gureet.plasma.remote.protocol.RemoteState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The swipe pad's gestures (brief 5.22), as the keys the TV gets. */
@OptIn(ExperimentalTestApi::class)
class SwipePadTest {
    /** A TV that keeps the keys it's sent: "Right↓", "Right↑". */
    private class Keys : TvLink {
        val sent = mutableListOf<String>()
        override val name = "Living Room TV"
        override val status: StateFlow<LinkStatus> = MutableStateFlow(LinkStatus.Connected)
        override val state: StateFlow<RemoteState> = MutableStateFlow(RemoteState())

        override fun key(key: RemoteKey, down: Boolean) {
            sent += key.name + if (down) "↓" else "↑"
        }

        override fun digits(digits: String) = Unit

        override fun text(text: String) = Unit

        override fun submit() = Unit

        override fun tune(channelId: String, listId: String?) = Unit

        override fun goLive() = Unit

        override suspend fun lists(listId: String?, from: Int): RemoteLists? = null

        override suspend fun logo(channelId: String): ByteArray? = null
    }

    private fun gesture(touch: TouchInjectionScope.() -> Unit): List<String> {
        val tv = Keys()
        runComposeUiTest {
            setContent { RemoteTheme { SwipePad(tv) } }
            onNodeWithContentDescription("Swipe pad").performTouchInput(touch)
            waitForIdle()
        }
        return tv.sent
    }

    @Test fun aTapIsOk() {
        assertEquals(listOf("Ok↓", "Ok↑"), gesture { click() })
    }

    @Test fun aSwipeIsAnArrowForEachStep() {
        // 100 dp to the right at one pixel a dp: two steps of 44 dp.
        val sent = gesture { swipe(center - Offset(50f, 0f), center + Offset(50f, 0f), durationMillis = 300) }
        assertEquals(listOf("Right↓", "Right↑", "Right↓", "Right↑"), sent)
    }

    @Test fun aSwipeUpIsUp() {
        val sent = gesture { swipe(center + Offset(0f, 30f), center - Offset(0f, 30f), durationMillis = 200) }
        assertEquals(listOf("Up↓", "Up↑"), sent)
    }

    @Test fun holdingIsALongOk() {
        assertEquals(listOf("Ok↓", "Ok↑"), gesture { longClick() })
    }
}
