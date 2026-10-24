// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingStatus
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Starts recordings on time (brief 5.6): one alarm, for the next recording to start, a little
 * before its padding begins, so the stream is open by then. Exact alarms wake the TV and may start
 * the recording service from the background; where Android doesn't allow them, a window alarm
 * that opens a minute early stands in.
 */
object RecordingAlarms {
    /** The service starts this long before a recording's padding begins, to open the stream. */
    const val LEAD_MS = 30_000L

    /** How early the window alarm opens, where exact alarms aren't allowed. */
    private const val WINDOW_MS = 60_000L

    /** When the next of [recordings] needs the service: the soonest one still to start, less [LEAD_MS]. */
    fun nextStart(recordings: List<Recording>): Instant? =
        recordings.filter { it.status == RecordingStatus.Scheduled }.minOfOrNull { it.from }?.minusMillis(LEAD_MS)

    /** Sets the alarm for [at], or clears it when there's nothing to record. */
    fun arm(context: Context, at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = alarmIntent(context)
        if (at == null) {
            alarms.cancel(intent)
            return
        }
        val time = at.toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, time - WINDOW_MS, WINDOW_MS, intent)
        }
    }

    /** Starts the recording service now, in the foreground: it records whatever's due. */
    fun startService(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RecordingAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** The alarm went off: the service records what's due. */
class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RecordingAlarms.startService(context)
    }
}

/**
 * After a restart, the alarm is set again for the next recording, and a recording that should be
 * running now carries on.
 */
class RecordingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val host = (context.applicationContext as? RecordingHost.Owner)?.recordingHost ?: return
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val pending = host.recordings.pending()
                val now = Instant.now()
                // One being written when the TV went off, or due already: the service sees to it.
                if (pending.any { it.status == RecordingStatus.Recording || it.from.minusMillis(RecordingAlarms.LEAD_MS) <= now }) {
                    RecordingAlarms.startService(context)
                } else {
                    RecordingAlarms.arm(context, RecordingAlarms.nextStart(pending))
                }
            } finally {
                done.finish()
            }
        }
    }
}
