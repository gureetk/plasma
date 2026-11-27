// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.settings

import android.content.Context

/** The app's settings in its shared preferences ("settings"), backed up with the app's other settings. */
class SharedPrefs(context: Context) : Prefs {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)

    override fun setBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
}
