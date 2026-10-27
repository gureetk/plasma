// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Plasma was just updated: when its own updater asked for it, it opens again (brief 5.18). */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        context.graph.updates.reopenIfUpdated()
    }
}
