// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Show Freeze Watch (the owner's ask of 2 November): a tiny copy of what the video surface shows,
 * made with Android's PixelCopy, to tell whether the picture on screen changes, whatever the
 * engine's counts say. It's read only while the overlay shows, about once a second, on the main
 * thread. Some TVs won't copy their video, or copy it black: then it never changes.
 */
internal class ScreenPicture {
    private val bitmap: Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(WIDTH * HEIGHT)
    private val main = Handler(Looper.getMainLooper())

    /** One copy at a time: they share the bitmap. */
    private var copying = false

    /** A hash of what [view] shows now; null when it can't be copied (no surface, or the TV won't). */
    suspend fun read(view: SurfaceView): Int? {
        if (copying || !view.holder.surface.isValid) return null
        copying = true
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { done ->
                    try {
                        PixelCopy.request(
                            view, bitmap,
                            { result ->
                                if (done.isActive) {
                                    val hash = if (result == PixelCopy.SUCCESS) {
                                        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                                        pixels.contentHashCode()
                                    } else {
                                        null
                                    }
                                    done.resume(hash)
                                }
                            },
                            main,
                        )
                    } catch (gone: IllegalArgumentException) {
                        // The surface went between the check and the request.
                        if (done.isActive) done.resume(null)
                    }
                }
            }
        } finally {
            copying = false
        }
    }

    private companion object {
        /** Small enough to copy in a moment, big enough to tell a picture from the next. */
        const val WIDTH = 48
        const val HEIGHT = 27
        const val TIMEOUT_MS = 500L
    }
}
