// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

/** How replacing or restoring the TV's Home went (brief 4.24). */
sealed interface SwitchResult {
    /** Done: Home now opens [home]. */
    data class Done(val home: String) : SwitchResult

    /** It stopped at [step], with what the TV said; whatever was done before it can be undone. */
    data class Failed(val step: String, val output: String) : SwitchResult
}

/**
 * Replaces the TV's Home with Plasma's, and puts it back (brief 5.13, method 2), with the TV's own
 * commands, run through [shell] (an ADB connection's).
 *
 * The safety rules: the launchers are only turned off once Home opens Plasma; nothing is
 * deleted; and each step is checked, so a failure leaves things as they can be undone.
 */
class HomeSwitch(private val shell: (String) -> String) {
    /**
     * Makes [plasmaHome] ("package/class") the Home app, then turns off [launchers], the TV's own
     * Home and its fallback, so they can't come back by themselves.
     */
    fun replace(plasmaHome: String, launchers: List<String>): SwitchResult {
        val setHome = "cmd package set-home-activity $plasmaHome"
        val set = shell(setHome)
        if (!succeeded(set)) return SwitchResult.Failed(setHome, set)
        // Never turn the launcher off unless Home opens Plasma first.
        val first = homeNow()
        if (!same(first, plasmaHome)) return SwitchResult.Failed(RESOLVE, first ?: "")
        for (launcher in launchers) {
            val disable = "pm disable-user --user 0 $launcher"
            val output = shell(disable)
            if (!output.contains("new state: disabled", ignoreCase = true)) return SwitchResult.Failed(disable, output)
        }
        val after = homeNow()
        return if (same(after, plasmaHome)) SwitchResult.Done(plasmaHome) else SwitchResult.Failed(RESOLVE, after ?: "")
    }

    /**
     * Turns [launchers] back on and makes [launcherHome] ("package/class", the TV's own) the Home
     * app again: Restore Google TV Home (4.23).
     */
    fun restore(launcherHome: String, launchers: List<String>): SwitchResult {
        for (launcher in launchers) {
            val enable = "pm enable $launcher"
            val output = shell(enable)
            if (!output.contains("new state: enabled", ignoreCase = true)) return SwitchResult.Failed(enable, output)
        }
        val setHome = "cmd package set-home-activity $launcherHome"
        val set = shell(setHome)
        if (!succeeded(set)) return SwitchResult.Failed(setHome, set)
        val after = homeNow()
        return if (same(after, launcherHome)) SwitchResult.Done(launcherHome) else SwitchResult.Failed(RESOLVE, after ?: "")
    }

    /** The activity Home opens now, as "package/class", from the TV's own resolver. */
    fun homeNow(): String? = homeIn(shell(RESOLVE))

    private fun succeeded(output: String): Boolean = output.contains("Success", ignoreCase = true)

    companion object {
        /** Which activity answers Home now (brief 5.13). */
        const val RESOLVE = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

        /**
         * The component `resolve-activity --brief` names: its last line, "package/class", where
         * the class can be short (".Home"). Null when nothing answers or the output isn't that.
         */
        internal fun homeIn(output: String): String? {
            val last = output.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: return null
            if (!Regex("[\\w.]+/[\\w.$]+").matches(last)) return null
            return full(last)
        }

        /** "package/.Class" as "package/package.Class". */
        internal fun full(component: String): String {
            val (pkg, cls) = component.split('/', limit = 2)
            return "$pkg/" + if (cls.startsWith('.')) pkg + cls else cls
        }

        internal fun same(a: String?, b: String?): Boolean = a != null && b != null && full(a) == full(b)
    }
}
