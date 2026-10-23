// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import android.view.Window
import ca.gureet.plasma.core.model.FrameRateMatch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Asks the display for a video's frame rate (Match Frame Rate, brief 5.4.4). */
fun interface FrameRateControl {
    /** Asks for [rate] frames a second on [view]'s display, or clears the request when [rate] is null. */
    fun apply(view: SurfaceView?, rate: Float?, mode: FrameRateMatch)

    companion object {
        val None = FrameRateControl { _, _, _ -> }
    }
}

/**
 * Match Frame Rate on the TV (brief 5.4.4): Plasma owns it, not the engines. Android 11 and up
 * take the rate on the video's surface, and from Android 12 "seamless only" skips a switch that
 * would blank the screen. Older TVs switch the window's display mode, which always blanks it, so
 * they only switch when the setting says always.
 */
object DisplayFrameRates : FrameRateControl {
    override fun apply(view: SurfaceView?, rate: Float?, mode: FrameRateMatch) {
        val surface = view?.holder?.surface?.takeIf { it.isValid }
        val window = view?.context?.activity()?.window
        if (rate == null || rate <= 0f || mode == FrameRateMatch.Off) {
            clear(surface, window)
            return
        }
        val display = displayRate(rate)
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> surface?.setFrameRate(
                    display,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    if (mode == FrameRateMatch.Always) Surface.CHANGE_FRAME_RATE_ALWAYS else Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> surface?.setFrameRate(display, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                mode == FrameRateMatch.Always && window != null -> preferMode(window, display)
            }
        }
    }

    /**
     * The rate to ask for: 25 and 29.97 fps are usually interlaced broadcasts, which play as 50 and
     * 59.94 fields a second once deinterlaced (5.4.5); the rest as they are.
     */
    fun displayRate(content: Float): Float = when (content) {
        in 24.5f..25.5f, in 29.5f..30.5f -> content * 2
        else -> content
    }

    private fun clear(surface: Surface?, window: Window?) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) surface?.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            if (window != null && window.attributes.preferredDisplayModeId != 0) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
            }
        }
    }

    /** A display mode the same size as now whose refresh rate is [rate] or a whole multiple of it. */
    private fun preferMode(window: Window, rate: Float) {
        val display = window.decorView.display ?: return
        val now = display.mode
        val mode = display.supportedModes
            .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight && isMultiple(it.refreshRate, rate) }
            .minByOrNull { it.refreshRate }
            ?: return
        if (window.attributes.preferredDisplayModeId != mode.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        }
    }

    private fun isMultiple(refresh: Float, rate: Float): Boolean {
        val ratio = refresh / rate
        val whole = ratio.roundToInt()
        return whole >= 1 && abs(ratio - whole) < MULTIPLE_TOLERANCE
    }

    private fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }

    private const val MULTIPLE_TOLERANCE = 0.01f
}
