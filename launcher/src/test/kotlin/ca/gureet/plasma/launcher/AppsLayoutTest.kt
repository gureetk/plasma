// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home's Apps row and All Apps from the TV's apps (brief 4.4 and 5.13). */
class AppsLayoutTest {
    private fun tv(name: String) = TvApp("tv.example.$name", "tv.example.$name.Main", name.replaceFirstChar(Char::uppercase), forTv = true, hasBanner = true)

    private fun phone(name: String) = TvApp("tv.example.$name", "tv.example.$name.Main", name.replaceFirstChar(Char::uppercase), forTv = false, hasBanner = false)

    // As TvApps lists them: the apps made for TV, A to Z, then the phone apps.
    private val installed = listOf(
        tv("cloudnest"), tv("driftwood"), tv("fileport"), tv("kinetic"), tv("lantern"), tv("reelbox"), tv("sonar"), tv("tumble"),
        phone("brightside"), phone("quarry"),
    )

    private fun names(apps: List<TvApp>) = apps.map { it.packageName.removePrefix("tv.example.") }

    @Test fun untilArrangedTheRowHasTheFirstSixTvApps() {
        val layout = AppsLayout.of(installed, row = null, hidden = emptySet())

        assertEquals(listOf("cloudnest", "driftwood", "fileport", "kinetic", "lantern", "reelbox"), names(layout.onHome))
        assertEquals(listOf("sonar", "tumble", "brightside", "quarry"), names(layout.others))
        assertEquals(emptyList<TvApp>(), layout.hidden)
    }

    @Test fun theRowKeepsItsOrderAndDropsAppsThatAreGone() {
        val layout = AppsLayout.of(installed, row = listOf("tv.example.sonar", "tv.example.quarry", "tv.example.gone", "tv.example.sonar"), hidden = emptySet())

        assertEquals(listOf("sonar", "quarry"), names(layout.onHome))
        assertEquals(listOf("cloudnest", "driftwood", "fileport", "kinetic", "lantern", "reelbox", "tumble", "brightside"), names(layout.others))
    }

    @Test fun appsInstalledSinceGoToAllApps() {
        val layout = AppsLayout.of(installed + tv("atlas"), row = listOf("tv.example.sonar"), hidden = emptySet())

        assertEquals(listOf("sonar"), names(layout.onHome))
        assertEquals("atlas", names(layout.others).last())
    }

    @Test fun aRowWithEveryAppTakenOffStaysEmpty() {
        val layout = AppsLayout.of(installed, row = emptyList(), hidden = emptySet())

        assertEquals(emptyList<TvApp>(), layout.onHome)
        assertEquals(installed, layout.others)
    }

    @Test fun hiddenAppsShowOnlyInTheirOwnList() {
        val hidden = setOf("tv.example.cloudnest", "tv.example.quarry", "tv.example.uninstalled")
        val layout = AppsLayout.of(installed, row = null, hidden = hidden)

        assertEquals(listOf("driftwood", "fileport", "kinetic", "lantern", "reelbox", "sonar"), names(layout.onHome))
        assertEquals(listOf("tumble", "brightside"), names(layout.others))
        assertEquals(listOf("cloudnest", "quarry"), names(layout.hidden))
        assertEquals("Cloudnest", layout.find("tv.example.cloudnest")?.label)
        assertNull(layout.find("tv.example.uninstalled"))
    }
}
