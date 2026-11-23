// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replacing and restoring the TV's Home with its own commands (brief 4.24 and 5.13). */
class HomeSwitchTest {
    /**
     * A TV's shell. [homes] are the activities that can answer Home, each in its package; one
     * whose package is off doesn't. Home opens [chosen] (the Home role's) while it's on; otherwise
     * the one activity that answers besides the TV's fallback, or the chooser when there are
     * more, as Android picks the highest priority and the fallback's is the lowest. Where
     * [roleWorks] is false, as on Google TV, `set-home-activity` is turned down.
     */
    private class FakeTv(
        private val homes: List<String> = listOf(LAUNCHER_HOME, SETUP_HOME, PLASMA, FALLBACK),
        private val roleWorks: Boolean = true,
        var chosen: String? = LAUNCHER_HOME,
        private val wontTurnOff: String? = null,
        off: Set<String> = emptySet(),
    ) {
        val off = off.toHashSet()
        val ran = ArrayList<String>()

        private fun on(component: String) = component.substringBefore('/') !in off

        fun run(command: String): String {
            ran += command
            val pkg = command.substringAfterLast(' ')
            return when {
                command.startsWith("cmd package set-home-activity ") -> if (roleWorks) {
                    chosen = pkg
                    "Success\n"
                } else {
                    "Error: Failed to set default home.\n"
                }
                command.startsWith("pm disable-user --user 0 ") -> if (pkg == wontTurnOff) {
                    "Error: java.lang.SecurityException: Shell cannot change component state for $pkg to 3\n"
                } else {
                    off += pkg
                    "Package $pkg new state: disabled-user\n"
                }
                command.startsWith("pm enable ") -> {
                    off -= pkg
                    "Package $pkg new state: enabled\n"
                }
                command == HomeSwitch.QUERY -> homes.filter(::on).let { answering ->
                    "${answering.size} activities found:\n" + answering.withIndex().joinToString("") { (i, home) ->
                        "  Activity #$i:\n    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n    $home\n"
                    }
                }
                command == HomeSwitch.RESOLVE -> "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n${home()}\n"
                else -> "unknown\n"
            }
        }

        fun home(): String {
            chosen?.takeIf(::on)?.let { return it }
            val answering = homes.filter { on(it) && it != FALLBACK }
            return answering.singleOrNull() ?: if (answering.isEmpty()) FALLBACK else CHOOSER
        }
    }

    @Test fun replaceSetsPlasmaThenTurnsTheLaunchersOff() {
        val tv = FakeTv()
        val result = HomeSwitch(tv::run).replace(PLASMA, listOf(LAUNCHERX, SETUPWRAITH))
        assertEquals(SwitchResult.Done(PLASMA), result)
        assertEquals(
            listOf(
                "cmd package set-home-activity $PLASMA",
                HomeSwitch.QUERY,
                "pm disable-user --user 0 $LAUNCHERX",
                "pm disable-user --user 0 $SETUPWRAITH",
                HomeSwitch.RESOLVE,
            ),
            tv.ran,
        )
    }

    @Test fun onATvThatTurnsTheHomeRoleDownTheLaunchersOffLeavePlasmasHome() {
        // Google TV: the role stays with its launcher, so Home is Plasma's once the launchers are
        // off, as with the commands for a computer.
        val tv = FakeTv(roleWorks = false)
        val result = HomeSwitch(tv::run).replace(PLASMA, listOf(LAUNCHERX, SETUPWRAITH))
        assertEquals(SwitchResult.Done(PLASMA), result)
        assertEquals(setOf(LAUNCHERX, SETUPWRAITH), tv.off)
        assertEquals(PLASMA, tv.home())
    }

    @Test fun theLaunchersGoBackOnWhenHomeDoesntOpenPlasma() {
        // Another launcher answers Home too: with Google TV's off, Home asks which, so they go
        // back on, last first.
        val tv = FakeTv(homes = listOf(LAUNCHER_HOME, SETUP_HOME, OTHER_HOME, PLASMA, FALLBACK), roleWorks = false)
        val result = HomeSwitch(tv::run).replace(PLASMA, listOf(LAUNCHERX, SETUPWRAITH))
        assertEquals(SwitchResult.Failed(HomeSwitch.RESOLVE, CHOOSER), result)
        assertTrue(tv.off.isEmpty())
        assertEquals(listOf("pm enable $SETUPWRAITH", "pm enable $LAUNCHERX"), tv.ran.takeLast(2))
        assertEquals(LAUNCHER_HOME, tv.home())
    }

    @Test fun withoutPlasmasHomeAmongTheTvsNothingIsTurnedOff() {
        val tv = FakeTv(homes = listOf(LAUNCHER_HOME, SETUP_HOME, FALLBACK), roleWorks = false)
        val result = HomeSwitch(tv::run).replace(PLASMA, listOf(LAUNCHERX, SETUPWRAITH))
        assertTrue(result is SwitchResult.Failed)
        assertEquals(HomeSwitch.QUERY, (result as SwitchResult.Failed).step)
        assertTrue(tv.ran.none { it.startsWith("pm disable-user") })
    }

    @Test fun aLauncherThatWontTurnOffPutsTheOnesBeforeItBack() {
        val tv = FakeTv(wontTurnOff = SETUPWRAITH)
        val result = HomeSwitch(tv::run).replace(PLASMA, listOf(LAUNCHERX, SETUPWRAITH))
        val failed = result as SwitchResult.Failed
        assertEquals("pm disable-user --user 0 $SETUPWRAITH", failed.step)
        assertTrue(failed.output.contains("SecurityException"))
        assertTrue(tv.off.isEmpty())
        assertEquals("pm enable $LAUNCHERX", tv.ran.last())
    }

    @Test fun restoreTurnsTheLaunchersOnAndGivesHomeBack() {
        val tv = FakeTv(chosen = PLASMA, off = setOf(LAUNCHERX, SETUPWRAITH))
        val result = HomeSwitch(tv::run).restore(LAUNCHER_HOME, listOf(LAUNCHERX, SETUPWRAITH))
        assertEquals(SwitchResult.Done(LAUNCHER_HOME), result)
        assertEquals(listOf("pm enable $LAUNCHERX", "pm enable $SETUPWRAITH"), tv.ran.take(2))
    }

    @Test fun restoreOnATvThatTurnsTheHomeRoleDownIsDoneOnceTheLauncherIsOn() {
        // The role never left Google TV Home, so it opens again as soon as it's on.
        val tv = FakeTv(roleWorks = false, off = setOf(LAUNCHERX, SETUPWRAITH))
        val result = HomeSwitch(tv::run).restore(LAUNCHER_HOME, listOf(LAUNCHERX, SETUPWRAITH))
        assertEquals(SwitchResult.Done(LAUNCHER_HOME), result)
    }

    @Test fun restoreSaysWhatTheTvSaidWhenHomeIsntTheLaunchers() {
        val tv = FakeTv(roleWorks = false, chosen = null, off = setOf(LAUNCHERX, SETUPWRAITH))
        val result = HomeSwitch(tv::run).restore(LAUNCHER_HOME, listOf(LAUNCHERX, SETUPWRAITH))
        assertEquals(SwitchResult.Failed("cmd package set-home-activity $LAUNCHER_HOME", "Error: Failed to set default home.\n"), result)
    }

    @Test fun homeIsReadFromTheResolversLastLine() {
        assertEquals("$LAUNCHERX/$LAUNCHERX.home.HomeActivity", HomeSwitch.homeIn("priority=0 preferredOrder=0\n$LAUNCHERX/.home.HomeActivity\n"))
        assertEquals(PLASMA, HomeSwitch.homeIn(PLASMA))
        assertNull(HomeSwitch.homeIn("No activity found\n"))
        assertNull(HomeSwitch.homeIn(""))
        // A short name is the same activity as its full one.
        assertTrue(HomeSwitch.same("ca.gureet.plasma/.HomeActivity", "ca.gureet.plasma/ca.gureet.plasma.HomeActivity"))
    }

    @Test fun everyHomeIsReadFromTheListOfThem() {
        val output = "2 activities found:\n  Activity #0:\n    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n" +
            "    $LAUNCHERX/.home.HomeActivity\n  Activity #1:\n    priority=-1000 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n    $PLASMA\n"
        assertEquals(listOf("$LAUNCHERX/$LAUNCHERX.home.HomeActivity", PLASMA), HomeSwitch.homesIn(output))
        assertEquals(emptyList<String>(), HomeSwitch.homesIn("No activities found\n"))
    }

    private companion object {
        const val PLASMA = "ca.gureet.plasma.dev/ca.gureet.plasma.HomeActivity"
        const val LAUNCHERX = "com.google.android.apps.tv.launcherx"
        const val SETUPWRAITH = "com.google.android.tungsten.setupwraith"
        const val LAUNCHER_HOME = "$LAUNCHERX/$LAUNCHERX.home.HomeActivity"
        const val SETUP_HOME = "$SETUPWRAITH/$SETUPWRAITH.MainActivity"
        const val OTHER_HOME = "com.example.launcher/com.example.launcher.Home"
        const val FALLBACK = "com.android.tv.settings/com.android.tv.settings.FallbackHome"
        const val CHOOSER = "android/com.android.internal.app.ResolverActivity"
    }
}
