// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * The Home Button Override (brief 5.13, method 1), the Projectivy technique: when one of the TV's
 * own launchers shows its Home, as on Home, waking the TV or turning it on, Plasma opens over it.
 *
 * It hears only which window came to the front, and only from the launchers: Android is told so
 * when it starts, and its configuration lets it read nothing on the screen.
 */
class HomeButtonService : AccessibilityService() {
    private var homes = StockHomes.NONE
    private var listening = false

    /** An app installed, updated or removed may be a launcher, or stop being one. */
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = watchLaunchers()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        watchLaunchers()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packages, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        listening = true
        HomeButton.connected(this)
        // Android starts the service with the TV, and the launcher may come up before it does.
        if (HomeButton.reasonNow() == HomeReason.PowerOn) open(HomeReason.PowerOn)
    }

    /** Hears from the launchers alone, and knows their Home activities. */
    private fun watchLaunchers() {
        homes = HomeReadings.stockHomesOf(this)
        val info = serviceInfo ?: return
        // With no launcher to watch, Plasma's own package keeps the rest of the TV quiet.
        info.packageNames = homes.packages.ifEmpty { setOf(packageName) }.toTypedArray()
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!homes.isHome(event.packageName, event.className, event.contentChangeTypes)) return
        open(HomeButton.reasonNow())
    }

    private fun open(reason: HomeReason) {
        if (HomeButton.mayOpen()) HomeButton.open(this, reason)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stopListening()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    private fun stopListening() {
        HomeButton.disconnected(this)
        if (!listening) return
        listening = false
        unregisterReceiver(packages)
    }
}
