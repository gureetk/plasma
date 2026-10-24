// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

/** How making a screensaver the TV's own went (brief 5.14). */
sealed interface ScreensaverResult {
    /** It's set. [previous] is the screensaver the TV had, to put back, or null for the TV's default. */
    data class Done(val previous: String?) : ScreensaverResult

    /** The TV didn't take it: what it says its screensaver is now. */
    data class Failed(val output: String) : ScreensaverResult
}

/**
 * The TV's screensaver, set with its own `settings` command over Plasma's ADB connection (brief
 * 5.14), since Google TV doesn't list other apps' screensavers in its settings.
 */
class ScreensaverSwitch(private val shell: (String) -> String) {
    /** Makes [component] ("package/class") the TV's screensaver, and turns screensavers on. */
    fun use(component: String): ScreensaverResult {
        val before = current()
        shell("settings put secure $COMPONENTS $component")
        shell("settings put secure $ENABLED 1")
        val now = current()
        return if (now == component) ScreensaverResult.Done(before?.takeUnless { it == component }) else ScreensaverResult.Failed(now.orEmpty())
    }

    /** Puts [previous] back, or the TV's default when it had none set; true once the TV says so. */
    fun restore(previous: String?): Boolean {
        shell(if (previous == null) "settings delete secure $COMPONENTS" else "settings put secure $COMPONENTS $previous")
        return current() == previous
    }

    /** The TV's screensaver now, or null when none is set and the TV's default shows. */
    fun current(): String? = shell("settings get secure $COMPONENTS").trim().takeUnless { it.isEmpty() || it == "null" }

    companion object {
        /** The secure setting that names the TV's screensaver. */
        const val COMPONENTS = "screensaver_components"
        const val ENABLED = "screensaver_enabled"
    }
}
