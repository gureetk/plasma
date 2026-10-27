// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.feature.settings.UpdateFacts
import ca.gureet.plasma.feature.settings.UpdateStatus
import ca.gureet.plasma.feature.settings.UpdateTrouble
import ca.gureet.plasma.update.Update
import ca.gureet.plasma.update.UpdateProblem
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** The updater's state as Settings › About › Plasma Update shows it (brief 5.18). */
class UpdateFactsTest {
    private val zone = ZoneOffset.UTC
    private val checked = Instant.parse("2026-10-21T18:02:00Z")
    private val update = Update(512, "0.2.0", "Picture-in-picture.", "https://updates.example/plasma/app-arm64-v8a-release.apk", "a".repeat(64))

    private fun facts(state: UpdateState, allowed: Boolean = true, tvSettings: Boolean = true) = updateFacts(state, allowed, tvSettings, zone)

    @Test fun beforeAndAfterACheck() {
        assertEquals(UpdateFacts(UpdateStatus.Off), facts(UpdateState.Off))
        assertEquals(UpdateFacts(UpdateStatus.NotChecked), facts(UpdateState.Current(null)))
        assertEquals(UpdateFacts(UpdateStatus.Checking, checkedAt = checked.atZone(zone)), facts(UpdateState.Checking(checked)))
        assertEquals(UpdateFacts(UpdateStatus.UpToDate, checkedAt = checked.atZone(zone)), facts(UpdateState.Current(checked)))
        // A first check that didn't work says why, rather than "Not checked yet".
        assertEquals(UpdateFacts(UpdateStatus.UpToDate, problem = UpdateTrouble.NotThere), facts(UpdateState.Current(null, UpdateProblem.NotThere)))
    }

    @Test fun anUpdateAndItsInstall() {
        assertEquals(
            UpdateFacts(UpdateStatus.Ready, version = "0.2.0 (512)", notes = "Picture-in-picture.", checkedAt = checked.atZone(zone), allowed = false, tvSettings = false),
            facts(UpdateState.Ready(update, checked), allowed = false, tvSettings = false),
        )
        assertEquals(UpdateTrouble.Damaged, facts(UpdateState.Ready(update, checked, UpdateProblem.Damaged)).problem)
        // Not while a recording is being written (5.18).
        assertEquals(UpdateTrouble.Recording, facts(UpdateState.Ready(update, checked, UpdateProblem.Recording)).problem)
        assertEquals(UpdateFacts(UpdateStatus.Downloading, version = "0.2.0 (512)", percent = 50), facts(UpdateState.Downloading(update, 0.5f)))
        // The server didn't say how big it is.
        assertEquals(-1, facts(UpdateState.Downloading(update, -1f)).percent)
        assertEquals(UpdateFacts(UpdateStatus.Installing, version = "0.2.0 (512)"), facts(UpdateState.Installing(update)))
    }
}
