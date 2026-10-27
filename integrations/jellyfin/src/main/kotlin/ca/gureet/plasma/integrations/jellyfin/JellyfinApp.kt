// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.integrations.jellyfin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Jellyfin's own app on the TV (brief 5.19): its start screen opens an item's page from the item's
 * id, as the app's own items on Android's Play Next row do.
 */
object JellyfinApp {
    const val PACKAGE = "org.jellyfin.androidtv"
    private const val START = "org.jellyfin.androidtv.ui.startup.StartupActivity"
    private const val EXTRA_ITEM_ID = "ItemId"

    /** Whether the app is on this TV. */
    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (missing: PackageManager.NameNotFoundException) {
        false
    }

    /** Opens item [itemId] in the app: false when the TV doesn't have it. */
    fun open(context: Context, itemId: String): Boolean {
        if (!installed(context)) return false
        return try {
            context.startActivity(intent(itemId))
            true
        } catch (gone: ActivityNotFoundException) {
            false
        } catch (refused: SecurityException) {
            false
        }
    }

    private fun intent(itemId: String): Intent = Intent()
        .setClassName(PACKAGE, START)
        .putExtra(EXTRA_ITEM_ID, itemId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
