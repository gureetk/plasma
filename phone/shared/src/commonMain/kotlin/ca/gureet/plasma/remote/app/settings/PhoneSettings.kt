// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.settings

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Where the app's own settings are kept: Android's shared preferences, the iPhone's user defaults. Nothing secret goes here. */
interface Prefs {
    fun bool(key: String, default: Boolean): Boolean

    fun setBool(key: String, value: Boolean)
}

/** Settings kept only while the app runs: for the tests and the screens' pictures. */
class MemoryPrefs : Prefs {
    private val kept = mutableMapOf<String, Boolean>()

    override fun bool(key: String, default: Boolean) = kept[key] ?: default

    override fun setBool(key: String, value: Boolean) {
        kept[key] = value
    }
}

/**
 * Brief 5.22's settings that change how the remote feels: Haptics (on), Keep Screen On (off) and
 * Swipe Pad Instead of D-pad (off).
 * Read by the screens as they change, and kept in [prefs].
 */
class PhoneSettings(private val prefs: Prefs) {
    var haptics by mutableStateOf(prefs.bool(HAPTICS, true))
        private set

    var keepScreenOn by mutableStateOf(prefs.bool(KEEP_SCREEN_ON, false))
        private set

    var swipePad by mutableStateOf(prefs.bool(SWIPE_PAD, false))
        private set

    /** Whether the swipe pad has been used: its hint shows until then. */
    var swipeHintSeen by mutableStateOf(prefs.bool(SWIPE_HINT_SEEN, false))
        private set

    fun haptics(on: Boolean) {
        haptics = on
        prefs.setBool(HAPTICS, on)
    }

    fun keepScreenOn(on: Boolean) {
        keepScreenOn = on
        prefs.setBool(KEEP_SCREEN_ON, on)
    }

    fun swipePad(on: Boolean) {
        swipePad = on
        prefs.setBool(SWIPE_PAD, on)
    }

    fun swipeHintSeen() {
        swipeHintSeen = true
        prefs.setBool(SWIPE_HINT_SEEN, true)
    }

    private companion object {
        const val HAPTICS = "haptics"
        const val KEEP_SCREEN_ON = "keep-screen-on"
        const val SWIPE_PAD = "swipe-pad"
        const val SWIPE_HINT_SEEN = "swipe-hint-seen"
    }
}

/** The settings the remote's keys and tabs follow; none (the defaults) for the demo pictures. */
val LocalPhoneSettings = compositionLocalOf<PhoneSettings?> { null }
