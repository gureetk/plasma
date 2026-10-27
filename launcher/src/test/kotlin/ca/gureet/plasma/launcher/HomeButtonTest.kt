// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Why Plasma opens as Home, and when the override opens it (brief 5.13). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HomeButtonTest {
    @Before fun fresh() = HomeButton.reset()

    @After fun done() = HomeButton.reset()

    @Test fun openingsAsTheTvStartsArePowerOn() {
        assertEquals(HomeReason.PowerOn, HomeButton.reason(now = 40_000, firstShownAt = 0))
        // The launcher coming up over Plasma once more as the TV settles is still power-on.
        assertEquals(HomeReason.PowerOn, HomeButton.reason(now = 43_000, firstShownAt = 41_000))
        // Once Plasma has been up a while, or long after the TV started, it's Home.
        assertEquals(HomeReason.Home, HomeButton.reason(now = 41_000 + HomeButton.STARTING_MS, firstShownAt = 41_000))
        assertEquals(HomeReason.Home, HomeButton.reason(now = HomeButton.POWER_ON_MS, firstShownAt = 0))
    }

    @Test fun theOverrideSaysWhyAndTheSystemsHomeIntentIsHome() {
        HomeButton.shown(now = 1_000)
        val later = HomeButton.POWER_ON_MS + 60_000
        val fromOverride = Intent(Intent.ACTION_MAIN).putExtra(HomeButton.EXTRA_REASON, HomeReason.PowerOn.name)
        assertEquals(HomeReason.PowerOn, HomeButton.reasonOf(fromOverride, later))
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        assertEquals(HomeReason.Home, HomeButton.reasonOf(home, later))
        // The TV's first Home, as it starts with Plasma as its Home app, is power-on.
        HomeButton.reset()
        assertEquals(HomeReason.PowerOn, HomeButton.reasonOf(home, now = 25_000))
        // Opened from the TV's own app row: an ordinary start.
        assertNull(HomeButton.reasonOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)))
        assertNull(HomeButton.reasonOf(null))
    }

    @Test fun oneLauncherComingUpOpensPlasmaOnce() {
        assertTrue(HomeButton.mayOpen(now = 100_000))
        // Its next events come within a moment of the first.
        assertFalse(HomeButton.mayOpen(now = 100_400))
        HomeButton.shown(now = 100_600)
        // Home pressed again later opens it again.
        assertTrue(HomeButton.mayOpen(now = 104_000))
    }

    @Test fun theLauncherComingUpOverPlasmaOpensItAgainAtOnce() {
        // As the TV starts, the override can open Plasma before the launcher covers it.
        assertTrue(HomeButton.mayOpen(now = 30_000))
        HomeButton.shown(now = 30_300)
        assertTrue(HomeButton.mayOpen(now = 30_800))
    }

    @Test fun plasmaThatNeverCameUpIsntOpenedOverAndOver() {
        assertTrue(HomeButton.mayOpen(now = 100_000))
        // The launcher is back and Plasma never showed: left alone for a while.
        assertFalse(HomeButton.mayOpen(now = 103_000))
        assertFalse(HomeButton.mayOpen(now = 109_000))
        assertTrue(HomeButton.mayOpen(now = 111_000))
    }

    @Test fun theSwitchesOnAreReadFromAndroidsList() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val own = ComponentName(context, HomeButtonService::class.java)
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService:${own.flattenToString()}",
        )
        val readings = HomeReadings.of(context)
        assertEquals(own, readings.ownService)
        assertTrue(own in readings.enabledServices)
        val setup = readings.toSetup("Chromecast", "14")
        assertTrue(setup.overrideOn)
        assertEquals(listOf("com.spocky.projengmenu"), setup.conflicts)
    }
}
