// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.remote.app.tv.TvLink
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Channels' logos, fetched from the TV once and kept while the app runs: the Channels tab asks
 * for the same ones each time a list is shown. A logo that can't be had is remembered as none.
 */
class Logos(private val tv: TvLink) {
    private val kept = HashMap<String, ImageBitmap?>()

    suspend fun of(channelId: String): ImageBitmap? {
        if (channelId in kept) return kept[channelId]
        val bitmap = runCatching { tv.logo(channelId)?.decodeToImageBitmap() }.getOrNull()
        kept[channelId] = bitmap
        return bitmap
    }
}

val LocalLogos = staticCompositionLocalOf<Logos?> { null }

/** A channel's logo, fitted within [maxWidth] × [maxHeight] in the middle of its plate; nothing while it comes or if there's none. */
@Composable
fun ChannelLogo(channelId: String, maxWidth: Dp, maxHeight: Dp, modifier: Modifier = Modifier) {
    val logos = LocalLogos.current ?: return
    val bitmap by produceState<ImageBitmap?>(null, channelId, logos) { value = logos.of(channelId) }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.sizeIn(maxWidth = maxWidth, maxHeight = maxHeight), contentScale = ContentScale.Fit) }
    }
}
