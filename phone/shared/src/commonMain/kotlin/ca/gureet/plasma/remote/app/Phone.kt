// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app

import ca.gureet.plasma.remote.app.settings.Prefs
import ca.gureet.plasma.remote.app.tv.PhoneApp
import ca.gureet.plasma.remote.app.tv.TvFinder
import ca.gureet.plasma.remote.app.tv.Vault

/**
 * What each phone gives the shared app: the app's name and version as it says hello, the phone's
 * own name (filled in under "Name this phone"), where paired TVs are kept, how TVs are found, and
 * where the app's settings are kept.
 */
class Phone(val app: PhoneApp, val deviceName: String, val vault: Vault, val finder: TvFinder, val prefs: Prefs)
