// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

/**
 * Apps' art for the Apps row and All Apps (brief 4.4): each app's banner, or its icon, as an image
 * reference ("app-banner:package/activity") that the app's image loading turns into a bitmap.
 */
object AppArt {
    const val BANNER = "app-banner:"
    const val ICON = "app-icon:"

    /** [app]'s banner, or null when it has none: its icon shows instead, on graphite. */
    fun bannerOf(app: TvApp): String? = if (app.hasBanner) "$BANNER${app.packageName}/${app.activity}" else null

    fun iconOf(app: TvApp): String = "$ICON${app.packageName}/${app.activity}"

    fun isAppArt(model: String): Boolean = model.startsWith(BANNER) || model.startsWith(ICON)

    /** Banners, about a tile's size each, the most recent kept: a TV's worth of apps in a few MB. */
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** The bitmap for [model] if it has been read already, to draw at once. */
    fun cached(model: String): Bitmap? = cache.get(model)

    /** Forgets [packageName]'s art, which an update may have changed. */
    fun forget(packageName: String) {
        for (model in cache.snapshot().keys) {
            if (model.removePrefix(BANNER).removePrefix(ICON).substringBefore('/') == packageName) cache.remove(model)
        }
    }

    /**
     * The bitmap for [model], or null when it isn't app art or the app is gone. Reads the app's
     * resources: call it off the main thread.
     */
    fun load(context: Context, model: String): Bitmap? {
        cache.get(model)?.let { return it }
        val banner = model.startsWith(BANNER)
        val component = ComponentName.unflattenFromString(model.removePrefix(BANNER).removePrefix(ICON)) ?: return null
        val pm = context.packageManager
        val drawable = try {
            if (banner) pm.getActivityBanner(component) ?: pm.getApplicationBanner(component.packageName) else pm.getActivityIcon(component)
        } catch (gone: PackageManager.NameNotFoundException) {
            null
        } ?: return null
        val bitmap = if (banner) drawable.scaled(BANNER_WIDTH, BANNER_HEIGHT) else drawable.toBitmap(ICON_SIZE, ICON_SIZE)
        cache.put(model, bitmap)
        return bitmap
    }

    /** At most [maxWidth] × [maxHeight], keeping its shape: banners are 16:9 (320 × 180 dp). */
    private fun Drawable.scaled(maxWidth: Int, maxHeight: Int): Bitmap {
        val width = intrinsicWidth.takeIf { it > 0 } ?: maxWidth
        val height = intrinsicHeight.takeIf { it > 0 } ?: maxHeight
        val scale = minOf(1f, maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        return toBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
    }

    /** A tile's size on a 1080p TV, where banners show at 264 × 149 px. */
    private const val BANNER_WIDTH = 320
    private const val BANNER_HEIGHT = 180
    private const val ICON_SIZE = 128
    private const val CACHE_BYTES = 12 * 1024 * 1024
}
