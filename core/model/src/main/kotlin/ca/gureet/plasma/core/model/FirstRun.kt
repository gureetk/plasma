// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * First-time setup (#85), as Plasma keeps it: whether it has been finished or skipped on this
 * install ([done]). Until it has, Plasma's screen opens on setup rather than on Home.
 */
data class FirstRun(val done: Boolean = false) {
    /** Whether Plasma's screen opens on setup. */
    val opensSetup: Boolean get() = !done

    /**
     * Plasma has started, with a source added already or without one. An install that has one,
     * such as the owner's TV updated from a build before setup, counts as set up, and stays so if
     * its sources go later. One without opens on setup, and a source added during setup doesn't
     * end it: only Plasma's next start counts it.
     */
    fun started(hasSources: Boolean): FirstRun = if (hasSources) FirstRun(done = true) else this

    /** Skip for Now on the welcome, or Go to Home at the end: Home from now on. */
    fun finished(): FirstRun = FirstRun(done = true)
}
