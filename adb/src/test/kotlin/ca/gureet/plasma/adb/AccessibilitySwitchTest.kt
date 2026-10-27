// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Turning Plasma's Home Button Override on with the TV's own commands (brief 5.13). */
class AccessibilitySwitchTest {
    /** A TV's secure settings and app ops, as its commands read and write them. */
    private class FakeTv(var services: String?, private val writable: Boolean = true) {
        var accessibility = "0"
        val ran = ArrayList<String>()

        fun run(command: String): String {
            ran += command
            return when {
                command == "settings get secure ${AccessibilitySwitch.SERVICES}" -> (services ?: "null") + "\n"
                command.startsWith("settings put secure ${AccessibilitySwitch.SERVICES} ") -> {
                    if (writable) services = command.substringAfter("${AccessibilitySwitch.SERVICES} ").trim('\'')
                    ""
                }
                command == "settings put secure ${AccessibilitySwitch.ENABLED} 1" -> {
                    accessibility = "1"
                    ""
                }
                command.startsWith("appops set ") -> ""
                else -> "unknown\n"
            }
        }
    }

    @Test fun plasmasServiceJoinsTheOnesAlreadyOn() {
        val tv = FakeTv(services = "tv.example.mapper/.KeyService")
        assertTrue(AccessibilitySwitch(tv::run).turnOn(PLASMA))

        assertEquals("tv.example.mapper/.KeyService:$PLASMA", tv.services)
        assertEquals("1", tv.accessibility)
        // In quotes, for a class with a $ in its name.
        assertTrue(tv.ran.any { it == "settings put secure ${AccessibilitySwitch.SERVICES} 'tv.example.mapper/.KeyService:$PLASMA'" })
    }

    @Test fun alreadyOnItsLeftAlone() {
        val tv = FakeTv(services = "ca.gureet.plasma/.launcher.HomeButtonService")
        assertTrue(AccessibilitySwitch(tv::run).turnOn("ca.gureet.plasma/ca.gureet.plasma.launcher.HomeButtonService"))
        assertTrue(tv.ran.none { it.startsWith("settings put secure ${AccessibilitySwitch.SERVICES}") })
    }

    @Test fun aRestrictedSwitchIsAllowedFirst() {
        val tv = FakeTv(services = null)
        assertTrue(AccessibilitySwitch(tv::run).turnOn(PLASMA, restrictedPackage = "ca.gureet.plasma"))
        assertEquals("appops set ca.gureet.plasma ACCESS_RESTRICTED_SETTINGS allow", tv.ran.first())
        assertEquals(PLASMA, tv.services)
    }

    @Test fun aTvThatDoesntTakeItSaysSo() {
        assertFalse(AccessibilitySwitch(FakeTv(services = null, writable = false)::run).turnOn(PLASMA))
    }

    private companion object {
        const val PLASMA = "ca.gureet.plasma/ca.gureet.plasma.launcher.HomeButtonService"
    }
}
