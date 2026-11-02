// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant

/**
 * Update Every Day At (Settings › TV Guide, brief 5.3): an alarm at that hour each day has Plasma
 * load what's old, the guide first among it, while the TV sleeps. It's inexact, so Android can
 * fold it in with other work. Plasma still looks for an old guide each time it comes to the front,
 * in case the TV was off the network at that hour.
 */
object GuideUpdateAlarm {
    /** Sets the alarm for [at], in place of the one before. */
    fun arm(context: Context, at: Instant) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent(context))
    }

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GuideUpdateReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** The hour has come: what's old is loaded, and the alarm set for the next day. */
class GuideUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.graph.updateWhileAsleep()
    }
}
