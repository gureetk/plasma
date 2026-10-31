// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Plasma opens on first-time setup rather than Home (#85). */
class FirstRunTest {
    @Test fun aNewInstallOpensOnSetup() {
        assertTrue(FirstRun().started(hasSources = false).opensSetup)
    }

    @Test fun anInstallWithASourceCountsAsSetUp() {
        // The owner's TV, updated from a build before setup came.
        val updated = FirstRun().started(hasSources = true)
        assertFalse(updated.opensSetup)
        assertTrue("Kept as done", updated.done)
        // Deleting its last source later doesn't bring setup back.
        assertFalse(updated.started(hasSources = false).opensSetup)
    }

    @Test fun skipForNowIsKept() {
        val skipped = FirstRun().started(hasSources = false).finished()
        assertFalse(skipped.opensSetup)
        // The next start, on the sample channels still.
        assertFalse(skipped.started(hasSources = false).opensSetup)
    }

    @Test fun goToHomeAtTheEndIsKept() {
        val finished = FirstRun().started(hasSources = false).finished()
        assertFalse(finished.opensSetup)
        assertFalse(finished.started(hasSources = true).opensSetup)
    }

    @Test fun aSourceAddedDuringSetupDoesntEndIt() {
        // Setup opened on a new install; the screen opening again during it (Home pressed with
        // Plasma as the TV's Home app) opens setup again, though a source has been added since.
        val run = FirstRun().started(hasSources = false)
        assertTrue(run.opensSetup)
        assertFalse("Done only once Plasma starts again with the source", run.started(hasSources = true).opensSetup)
    }

    @Test fun clearingPlasmasStorageBringsItBack() {
        // Nothing kept: a new install again.
        assertTrue(FirstRun(done = false).started(hasSources = false).opensSetup)
    }
}
