// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

/** How replacing or restoring the TV's Home went (brief 4.24). */
sealed interface SwitchResult {
    /** Done: Home now opens [home]. */
    data class Done(val home: String) : SwitchResult

    /** It stopped at [step], with what the TV said; whatever was done before it is undone. */
    data class Failed(val step: String, val output: String) : SwitchResult
}

/**
 * Replaces the TV's Home with Plasma's, and puts it back (brief 5.13, method 2), with the TV's own
 * commands, run through [shell] (an ADB connection's): the commands board 24 gives for a computer.
 *
 * The safety rules: the launchers are only turned off when Plasma's Home activity is one the TV
 * would open, and turned back on if Home doesn't open Plasma once they're off; nothing is
 * deleted; and each step is checked.
 */
class HomeSwitch(private val shell: (String) -> String) {
    /**
     * Makes [plasmaHome] ("package/class") the Home app, then turns off [launchers], the TV's own
     * Home and its fallback, so they can't come back by themselves.
     *
     * The first step is Android's Home role, which some TVs turn down (Google TV among them):
     * there, Plasma's Home is the one left once the launchers are off, as with the commands for a
     * computer, so a refused role doesn't stop it.
     */
    fun replace(plasmaHome: String, launchers: List<String>): SwitchResult {
        shell("cmd package set-home-activity $plasmaHome")
        // Never turn a launcher off unless Plasma's Home activity is one the TV would open.
        val homes = shell(QUERY)
        if (homesIn(homes).none { same(it, plasmaHome) }) return SwitchResult.Failed(QUERY, homes)
        val off = ArrayList<String>()
        for (launcher in launchers) {
            val disable = "pm disable-user --user 0 $launcher"
            val output = shell(disable)
            if (!output.contains("new state: disabled", ignoreCase = true)) {
                turnOn(off)
                return SwitchResult.Failed(disable, output)
            }
            off += launcher
        }
        val after = homeNow()
        if (same(after, plasmaHome)) return SwitchResult.Done(plasmaHome)
        // Home doesn't open Plasma, even with them off: they go back on, as they were.
        turnOn(off)
        return SwitchResult.Failed(RESOLVE, after ?: "")
    }

    /**
     * Turns [launchers] back on and makes [launcherHome] ("package/class", the TV's own) the Home
     * app again: Restore Google TV Home (4.23). A TV that turns the Home role down opens its own
     * launcher again once it's on.
     */
    fun restore(launcherHome: String, launchers: List<String>): SwitchResult {
        for (launcher in launchers) {
            val enable = "pm enable $launcher"
            val output = shell(enable)
            if (!output.contains("new state: enabled", ignoreCase = true)) return SwitchResult.Failed(enable, output)
        }
        val setHome = "cmd package set-home-activity $launcherHome"
        val set = shell(setHome)
        val after = homeNow()
        return when {
            same(after, launcherHome) -> SwitchResult.Done(launcherHome)
            !succeeded(set) -> SwitchResult.Failed(setHome, set)
            else -> SwitchResult.Failed(RESOLVE, after ?: "")
        }
    }

    /** The activity Home opens now, as "package/class", from the TV's own resolver. */
    fun homeNow(): String? = homeIn(shell(RESOLVE))

    /** Turns [launchers] back on, last first: what [replace] turned off before it stopped. */
    private fun turnOn(launchers: List<String>) {
        for (launcher in launchers.asReversed()) shell("pm enable $launcher")
    }

    private fun succeeded(output: String): Boolean = output.contains("Success", ignoreCase = true)

    companion object {
        /** Which activity answers Home now (brief 5.13). */
        const val RESOLVE = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

        /** Every activity that can answer Home, whether or not it's the one Home opens. */
        const val QUERY = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

        /**
         * The component `resolve-activity --brief` names: its last line, "package/class", where
         * the class can be short (".Home"). Null when nothing answers or the output isn't that.
         */
        internal fun homeIn(output: String): String? {
            val last = output.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: return null
            if (!COMPONENT.matches(last)) return null
            return full(last)
        }

        /** The components `query-activities --brief` lists, each on a line of its own, in full. */
        internal fun homesIn(output: String): List<String> =
            output.lineSequence().map { it.trim() }.filter { COMPONENT.matches(it) }.map(::full).toList()

        /** "package/.Class" as "package/package.Class". */
        internal fun full(component: String): String {
            val (pkg, cls) = component.split('/', limit = 2)
            return "$pkg/" + if (cls.startsWith('.')) pkg + cls else cls
        }

        internal fun same(a: String?, b: String?): Boolean = a != null && b != null && full(a) == full(b)

        private val COMPONENT = Regex("[\\w.]+/[\\w.$]+")
    }
}
