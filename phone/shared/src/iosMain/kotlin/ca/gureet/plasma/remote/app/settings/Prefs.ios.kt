// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.settings

import platform.Foundation.NSUserDefaults

/** The app's settings in its user defaults. */
class UserDefaultsPrefs : Prefs {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun bool(key: String, default: Boolean) = if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    override fun setBool(key: String, value: Boolean) = defaults.setBool(value, key)
}
