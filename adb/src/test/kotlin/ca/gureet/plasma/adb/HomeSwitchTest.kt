// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replacing and restoring the TV's Home with its own commands (brief 4.24 and 5.13). */
class HomeSwitchTest {
    /** A TV's shell: Home answers [home] until set, and the launchers turn off and on. */
    private class FakeShell(var home: String, private val setWorks: Boolean = true, private val setChangesHome: Boolean = true) {
        val ran = ArrayList<String>()

        fun run(command: String): String {
            ran += command
            return when {
                command.startsWith("cmd package set-home-activity ") -> if (setWorks) {
                    if (setChangesHome) home = command.substringAfterLast(' ')
                    "Success\n"
                } else {
                    "Error: no such activity\n"
                }
                command.startsWith("pm disable-user --user 0 ") -> "Package ${command.substringAfterLast(' ')} new state: disabled-user\n"
                command.startsWith("pm enable ") -> "Package ${command.substringAfterLast(' ')} new state: enabled\n"
                command == HomeSwitch.RESOLVE -> "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n$home\n"
                else -> "unknown\n"
            }
        }
    }

    @Test fun replaceSetsPlasmaThenTurnsTheLaunchersOff() {
        val shell = FakeShell(home = "$LAUNCHERX/.home.HomeActivity")
        val result = HomeSwitch(shell::run).replace(PLASMA, listOf(LAUNCHERX, SETUPWRAITH))
        assertEquals(SwitchResult.Done(PLASMA), result)
        assertEquals(
            listOf(
                "cmd package set-home-activity $PLASMA",
                HomeSwitch.RESOLVE,
                "pm disable-user --user 0 $LAUNCHERX",
                "pm disable-user --user 0 $SETUPWRAITH",
                HomeSwitch.RESOLVE,
            ),
            shell.ran,
        )
    }

    @Test fun theLaunchersStayOnWhenHomeDoesntOpenPlasma() {
        val shell = FakeShell(home = "$LAUNCHERX/.home.HomeActivity", setChangesHome = false)
        val result = HomeSwitch(shell::run).replace(PLASMA, listOf(LAUNCHERX))
        assertTrue(result is SwitchResult.Failed)
        assertTrue(shell.ran.none { it.startsWith("pm disable-user") })
    }

    @Test fun aFailedStepSaysWhatTheTvSaid() {
        val shell = FakeShell(home = "$LAUNCHERX/.home.HomeActivity", setWorks = false)
        val result = HomeSwitch(shell::run).replace(PLASMA, listOf(LAUNCHERX))
        assertEquals(SwitchResult.Failed("cmd package set-home-activity $PLASMA", "Error: no such activity\n"), result)
    }

    @Test fun restoreTurnsTheLaunchersOnAndGivesHomeBack() {
        val shell = FakeShell(home = PLASMA)
        val result = HomeSwitch(shell::run).restore("$LAUNCHERX/.home.HomeActivity", listOf(LAUNCHERX, SETUPWRAITH))
        assertEquals(SwitchResult.Done("$LAUNCHERX/.home.HomeActivity"), result)
        assertEquals(listOf("pm enable $LAUNCHERX", "pm enable $SETUPWRAITH"), shell.ran.take(2))
    }

    @Test fun homeIsReadFromTheResolversLastLine() {
        assertEquals("$LAUNCHERX/$LAUNCHERX.home.HomeActivity", HomeSwitch.homeIn("priority=0 preferredOrder=0\n$LAUNCHERX/.home.HomeActivity\n"))
        assertEquals(PLASMA, HomeSwitch.homeIn(PLASMA))
        assertNull(HomeSwitch.homeIn("No activity found\n"))
        assertNull(HomeSwitch.homeIn(""))
        // A short name is the same activity as its full one.
        assertTrue(HomeSwitch.same("ca.gureet.plasma/.HomeActivity", "ca.gureet.plasma/ca.gureet.plasma.HomeActivity"))
    }

    private companion object {
        const val PLASMA = "ca.gureet.plasma.dev/ca.gureet.plasma.HomeActivity"
        const val LAUNCHERX = "com.google.android.apps.tv.launcherx"
        const val SETUPWRAITH = "com.google.android.tungsten.setupwraith"
    }
}
