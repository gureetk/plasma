// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.adb

/**
 * Turns an Accessibility service on with the TV's own `settings` command over Plasma's ADB
 * connection (brief 5.13): for a TV that holds Plasma's switch back as a restricted setting, or
 * hides it. The services already on stay on.
 */
class AccessibilitySwitch(private val shell: (String) -> String) {
    /**
     * Turns [service] ("package/class") on, first allowing [restrictedPackage]'s restricted
     * settings where Android holds them back; true once the TV lists it as on.
     */
    fun turnOn(service: String, restrictedPackage: String? = null): Boolean {
        restrictedPackage?.let { shell("appops set $it ACCESS_RESTRICTED_SETTINGS allow") }
        val on = enabled()
        if (on.none { full(it) == full(service) }) shell("settings put secure $SERVICES '${(on + service).joinToString(":")}'")
        shell("settings put secure $ENABLED 1")
        return enabled().any { full(it) == full(service) }
    }

    /** The services on now, as the TV lists them. */
    fun enabled(): List<String> =
        shell("settings get secure $SERVICES").trim().takeUnless { it.isEmpty() || it == "null" }?.split(':')?.filter { it.isNotBlank() }.orEmpty()

    /** "package/.Class" written out in full, as "package/package.Class". */
    private fun full(component: String): String {
        val packageName = component.substringBefore('/')
        val name = component.substringAfter('/', "")
        return if (name.startsWith('.')) "$packageName/$packageName$name" else component
    }

    companion object {
        /** The secure settings that list the services turned on, and turn Accessibility itself on. */
        const val SERVICES = "enabled_accessibility_services"
        const val ENABLED = "accessibility_enabled"
    }
}
