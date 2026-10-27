// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.feature.settings.DefaultHomeOffer
import ca.gureet.plasma.feature.settings.HomeScreenMethod
import ca.gureet.plasma.feature.settings.SettingsSamples
import ca.gureet.plasma.launcher.DefaultHomeWay
import ca.gureet.plasma.launcher.HomeNow
import ca.gureet.plasma.launcher.HomeSetup
import ca.gureet.plasma.launcher.StockLauncher
import ca.gureet.plasma.live.ChannelSummary
import ca.gureet.plasma.live.settingsFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** Home Screen setup from what was read of the TV (brief 4.23). */
class HomeScreenFactsTest {
    private val googleTv = HomeSetup(
        device = "Chromecast",
        androidVersion = "14",
        home = HomeNow.App("Google TV Home"),
        overrideOn = false,
        launchers = listOf(StockLauncher(LAUNCHERX, "Google TV Home", enabled = true), StockLauncher(SETUPWRAITH, "Setup", enabled = true)),
        defaultHome = DefaultHomeWay.None,
        conflicts = emptyList(),
        restricted = false,
    )

    private fun facts(setup: HomeSetup, running: Boolean = false) = homeScreenFacts(setup, running, "ca.gureet.plasma.dev", "Google TV Home")

    @Test fun aTvWithNothingSetUp() {
        val facts = facts(googleTv)
        assertNull(facts.method)
        assertFalse(facts.overrideOn)
        assertEquals("Chromecast", facts.device)
        assertEquals("Google TV Home", facts.launcherName)
        assertEquals(DefaultHomeOffer.None, facts.defaultHome)
        // Plasma never hears Home held, so Long-Press Home is the TV's.
        assertFalse(facts.longPressHome)
        assertEquals("ca.gureet.plasma.dev", facts.packageName)
    }

    @Test fun theOverrideRunningCountsAsOnBeforeAndroidsListSaysSo() {
        val facts = facts(googleTv, running = true)
        assertTrue(facts.overrideOn)
        assertEquals(HomeScreenMethod.Override, facts.method)
    }

    @Test fun replacedAndDefaultHomeComeThrough() {
        val off = googleTv.launchers.map { it.copy(enabled = false) }
        assertEquals(HomeScreenMethod.Replace, facts(googleTv.copy(home = HomeNow.Plasma, launchers = off)).method)
        assertEquals(HomeScreenMethod.DefaultApp, facts(googleTv.copy(home = HomeNow.Plasma, defaultHome = DefaultHomeWay.Role)).method)
        assertEquals(DefaultHomeOffer.Role, facts(googleTv.copy(defaultHome = DefaultHomeWay.Role)).defaultHome)
    }

    @Test fun withNoLauncherLeftItsNamedAnyway() {
        assertEquals("Google TV Home", facts(googleTv.copy(home = HomeNow.Plasma, launchers = emptyList())).launcherName)
    }

    @Test fun theTvsHomeButtonShowsBeforeAnySourceIsAdded() {
        val home = facts(googleTv)
        val settings = settingsFacts(emptyList(), emptyMap(), emptyMap(), ChannelSummary(), ZoneOffset.UTC, audio = null, home = home)
        assertEquals(home, settings.home)
        // The rest is still the sample world.
        assertEquals(SettingsSamples.facts.sources, settings.sources)
    }

    private companion object {
        const val LAUNCHERX = "com.google.android.apps.tv.launcherx"
        const val SETUPWRAITH = "com.google.android.tungsten.setupwraith"
    }
}
