// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gureet.plasma.remote.app.tv.TvLink
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Posters from the TV (#230), kept as the TV sent them (small JPEGs) for the last [KEPT] titles
 * while the app runs, and drawn as each row shows. One the TV has none of is remembered as none.
 */
class Posters(private val tv: TvLink) {
    /** In the order they were last used, the oldest first: Kotlin's own map, as the iPhone has no access order. */
    private val kept = LinkedHashMap<String, ByteArray?>()

    suspend fun of(kind: String, id: String): ImageBitmap? {
        val key = "$kind/$id"
        val bytes = if (key in kept) kept.remove(key) else runCatching { tv.poster(kind, id) }.getOrNull()
        kept[key] = bytes
        if (kept.size > KEPT) kept.remove(kept.keys.first())
        return bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }

    private companion object {
        const val KEPT = 400
    }
}

val LocalPosters = staticCompositionLocalOf<Posters?> { null }

/**
 * A title's poster, 2:3, rounded: its name on a plate while the poster comes, and in its place
 * when there's none or it won't load, as on the TV (#228).
 */
@Composable
fun TitlePoster(kind: String, id: String, name: String, hasPoster: Boolean, modifier: Modifier = Modifier) {
    val posters = LocalPosters.current
    val bitmap by produceState<ImageBitmap?>(null, kind, id, posters, hasPoster) { value = if (hasPoster) posters?.of(kind, id) else null }
    Box(modifier.size(48.dp, 72.dp).clip(RoundedCornerShape(6.dp)).background(RemoteColors.Plate), contentAlignment = Alignment.Center) {
        val shown = bitmap
        if (shown != null) {
            Image(shown, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BasicText(
                name,
                Modifier.padding(4.dp),
                style = textStyle(9.sp, FontWeight.SemiBold, RemoteColors.Text3).copy(textAlign = TextAlign.Center),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
