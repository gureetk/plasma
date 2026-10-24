// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import android.content.ComponentName
import android.view.accessibility.AccessibilityEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What Home Screen setup makes of a TV's launchers and switches (brief 4.23 and 5.13). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HomeSetupTest {
    private val plasma = HomeCandidate(PLASMA, "$PLASMA_CODE.HomeActivity", "$PLASMA_CODE.MainActivity", priority = 0, enabled = true)
    private val launcherx = HomeCandidate(LAUNCHERX, "$LAUNCHERX.home.HomeActivity", null, priority = 2, enabled = true)
    private val setupwraith = HomeCandidate(SETUPWRAITH, "$SETUPWRAITH.FallbackHome", null, priority = -10, enabled = true)
    private val fallbackHome = HomeCandidate("com.android.tv.settings", "com.android.tv.settings.FallbackHome", null, priority = -1000, enabled = true)
    private val ownService = ComponentName(PLASMA, "ca.gureet.plasma.launcher.HomeButtonService")

    private fun readings(
        candidates: List<HomeCandidate> = listOf(plasma, launcherx, setupwraith, fallbackHome),
        resolved: Resolved = Resolved.App(LAUNCHERX, launcherx.name),
        services: List<ComponentName> = emptyList(),
        roleAvailable: Boolean = false,
        homeSettings: Boolean = false,
        restricted: Boolean = false,
    ) = HomeReadings(
        ownPackage = PLASMA,
        candidates = candidates,
        resolved = resolved,
        enabledServices = services,
        ownService = ownService,
        roleAvailable = roleAvailable,
        homeSettings = homeSettings,
        restricted = restricted,
        label = { LABELS[it] ?: it },
    )

    private fun HomeReadings.setup() = toSetup("Chromecast", "14")

    @Test fun aGoogleTvWithNothingSetUpOpensItsOwnHome() {
        val setup = readings().setup()
        assertEquals(HomeNow.App("Google TV Home"), setup.home)
        assertNull(setup.method)
        assertFalse(setup.overrideOn)
        assertEquals(DefaultHomeWay.None, setup.defaultHome)
        assertEquals("Google TV Home", setup.launcherName)
    }

    @Test fun theLaunchersToTurnOffAreGoogleTvHomeAndItsSetupNeverSettings() {
        // Google TV's setup turns its launcher back on, so it goes too (5.13); Settings' own
        // fallback Home must never be turned off.
        val setup = readings().setup()
        assertEquals(listOf(LAUNCHERX, SETUPWRAITH), setup.launchers.map { it.packageName })
        assertTrue(setup.launchers.all { it.enabled })
        // Restore gives Home back to Google TV Home's own activity, not its setup's.
        assertEquals("$LAUNCHERX/$LAUNCHERX.home.HomeActivity", setup.launcherHome)
    }

    @Test fun theOverrideIsOnOnceItsSwitchIs() {
        val setup = readings(services = listOf(ComponentName("com.google.android.marvin.talkback", "x.TalkBackService"), ownService)).setup()
        assertTrue(setup.overrideOn)
        assertEquals(HomeMethod.Override, setup.method)
        // A screen reader isn't a Home override.
        assertEquals(emptyList<String>(), setup.conflicts)
    }

    @Test fun anotherAppsOverrideIsAConflict() {
        val projectivy = ComponentName("com.spocky.projengmenu", "com.spocky.projengmenu.services.ProjectivyAccessibilityService")
        val setup = readings(services = listOf(ownService, projectivy)).setup()
        assertEquals(listOf("Projectivy Launcher"), setup.conflicts)
    }

    @Test fun aLauncherWithAnAccessibilityServiceIsAConflictToo() {
        val other = HomeCandidate("org.example.launcher", "org.example.launcher.Home", null, priority = 0, enabled = true)
        val setup = readings(candidates = listOf(plasma, launcherx, other), services = listOf(ComponentName("org.example.launcher", "org.example.launcher.Watch"))).setup()
        assertEquals(listOf("org.example.launcher"), setup.conflicts)
    }

    @Test fun plasmaAsHomeWithGoogleTvHomeTurnedOffIsReplace() {
        val off = launcherx.copy(enabled = false)
        val setup = readings(candidates = listOf(plasma, off, setupwraith.copy(enabled = false), fallbackHome), resolved = Resolved.App(PLASMA, plasma.name)).setup()
        assertEquals(HomeNow.Plasma, setup.home)
        assertTrue(setup.replaced)
        assertEquals(HomeMethod.Replace, setup.method)
        // Still listed, so Restore can turn them back on.
        assertEquals(listOf(false, false), setup.launchers.map { it.enabled })
    }

    @Test fun plasmaAsHomeWithGoogleTvHomeStillOnIsTheDefaultHomeApp() {
        val setup = readings(resolved = Resolved.App(PLASMA, plasma.name), services = listOf(ownService)).setup()
        assertEquals(HomeMethod.DefaultApp, setup.method)
        assertFalse(setup.replaced)
        // The override is still on, which Home Screen setup offers to turn off.
        assertTrue(setup.overrideOn)
    }

    @Test fun homeResolvingToNoHomeAppIsTheChooser() {
        val setup = readings(resolved = Resolved.App("android", "com.android.internal.app.ResolverActivity")).setup()
        assertEquals(HomeNow.Chooser, setup.home)
        assertEquals(DefaultHomeWay.Chooser, setup.defaultHome)
    }

    @Test fun theHomeRoleComesFirstThenTheTvsOwnSettings() {
        assertEquals(DefaultHomeWay.Role, readings(roleAvailable = true, homeSettings = true).setup().defaultHome)
        assertEquals(DefaultHomeWay.Settings, readings(homeSettings = true).setup().defaultHome)
    }

    @Test fun aRestrictedSwitchMattersOnlyWhileTheOverrideIsOff() {
        assertTrue(readings(restricted = true).setup().restricted)
        assertFalse(readings(restricted = true, services = listOf(ownService)).setup().restricted)
    }

    @Test fun theOverrideWatchesOnlyTheLaunchersHomeActivities() {
        val homes = readings().stockHomes()
        assertEquals(setOf(LAUNCHERX, SETUPWRAITH), homes.packages)
        assertTrue(homes.isHome(LAUNCHERX, launcherx.name, 0))
        // A pane inside it, another of its screens (such as a settings panel), Plasma's own, or
        // a dialog: none of them is Home coming up.
        assertFalse(homes.isHome(LAUNCHERX, launcherx.name, AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED))
        assertFalse(homes.isHome(LAUNCHERX, "$LAUNCHERX.dashboard.DashboardActivity", 0))
        assertFalse(homes.isHome(PLASMA, plasma.target, 0))
        assertFalse(homes.isHome(LAUNCHERX, "android.app.Dialog", 0))
        assertFalse(homes.isHome(null, null, 0))
    }

    @Test fun aLaunchersAliasIsKnownByTheActivityItStandsFor() {
        val aliased = HomeCandidate("org.example.tvhome", "org.example.tvhome.Home", "org.example.tvhome.MainActivity", priority = 1, enabled = true)
        val homes = readings(candidates = listOf(plasma, aliased)).stockHomes()
        assertTrue(homes.isHome("org.example.tvhome", "org.example.tvhome.MainActivity", 0))
    }

    private companion object {
        const val PLASMA = "ca.gureet.plasma.dev"
        const val PLASMA_CODE = "ca.gureet.plasma"
        const val LAUNCHERX = "com.google.android.apps.tv.launcherx"
        const val SETUPWRAITH = "com.google.android.tungsten.setupwraith"
        val LABELS = mapOf(LAUNCHERX to "Google TV Home", SETUPWRAITH to "Google TV Setup", "com.spocky.projengmenu" to "Projectivy Launcher")
    }
}
