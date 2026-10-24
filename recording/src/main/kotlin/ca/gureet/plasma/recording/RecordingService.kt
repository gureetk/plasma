// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingProblems
import ca.gureet.plasma.core.data.RecordingStatus
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The recording service (brief 5.6): a foreground service of the `specialUse` type, so a long
 * recording isn't stopped at a time cap. The alarm starts it a little before a recording; it
 * writes every recording that's due, each through the stream hub, and stops once none is left.
 * Local catch-up (5.5) runs in it too, all the time, for as long as there are channels to write.
 * A partial wake lock, and a Wi-Fi lock, keep the stream coming while the TV is off.
 */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<String, Job>()
    private val titles = HashMap<String, String>()

    /** Local catch-up's channels being written, by id. */
    private val local = HashMap<String, Job>()

    /** Where those are written. */
    private var localRoot: File? = null
    private var plan = LocalCatchUpPlan.None
    private var planning: Job? = null
    private var tidying: Job? = null
    private var checking: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val host: RecordingHost? get() = (application as? RecordingHost.Owner)?.recordingHost

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification()
        checking?.cancel()
        checking = scope.launch { check() }
        if (planning == null) {
            planning = scope.launch {
                host?.localCatchUp?.collect { now -> keepLocal(now) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        release()
        super.onDestroy()
    }

    /** Starts every recording that's due, and finishes ones whose time went by while Plasma was off. */
    private suspend fun check() {
        val host = host ?: return finish()
        plan = host.localCatchUp.first()
        val now = Instant.now()
        for (recording in host.recordings.pending()) {
            if (recording.id in jobs) continue
            when {
                // Its time went by while Plasma wasn't running.
                !recording.until.isAfter(now) -> host.recordings.finished(
                    recording.id,
                    failed = recording.sizeBytes == 0L,
                    problem = RecordingProblems.MISSED.takeIf { recording.sizeBytes == 0L },
                )
                recording.from.minusMillis(RecordingAlarms.LEAD_MS) <= now -> start(host, recording)
            }
        }
        when {
            jobs.isNotEmpty() || plan.writing.isNotEmpty() -> hold()
            // Local catch-up paused while the TV's off: the service waits for it, without keeping it awake.
            plan.channelIds.isEmpty() -> finish()
        }
        follow(host)
    }

    private fun start(host: RecordingHost, recording: Recording) {
        titles[recording.id] = recording.title
        jobs[recording.id] = scope.launch {
            // Opened a little early; written from the start of its padding.
            val wait = recording.from.toEpochMilli() - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            Recorder(host).record(recording)
        }.also { job ->
            job.invokeOnCompletion {
                jobs.remove(recording.id)
                titles.remove(recording.id)
                if (scope.isActive) {
                    showNotification()
                    if (jobs.isEmpty() && local.isEmpty()) {
                        if (plan.channelIds.isEmpty()) finish() else release()
                    }
                }
            }
        }
        showNotification()
    }

    /**
     * Stops a recording that's cancelled or deleted while it's written: what the store says of it
     * stands. Checked every few seconds while anything's recording.
     */
    private suspend fun follow(host: RecordingHost) {
        while (jobs.isNotEmpty()) {
            delay(FOLLOW_MS)
            for ((id, job) in jobs.toMap()) {
                val now = host.recordings.get(id)
                if (now == null || (now.status != RecordingStatus.Recording && now.status != RecordingStatus.Scheduled)) job.cancel()
            }
        }
    }

    /**
     * Local catch-up as Settings has it now (brief 5.5): its channels are written, each in its
     * folder, those taken off or paused stop, and what it keeps is tidied every minute.
     */
    private fun keepLocal(now: LocalCatchUpPlan) {
        val host = host ?: return
        plan = now
        // Somewhere else to write now, as when a USB drive comes or goes: each channel starts again there.
        if (now.root != localRoot) {
            local.values.forEach { it.cancel() }
            local.clear()
            localRoot = now.root
        }
        val writing = now.writing.toSet()
        for ((id, job) in local.toMap()) {
            if (id !in writing) {
                job.cancel()
                local.remove(id)
            }
        }
        val root = now.root
        if (root != null) {
            for (id in writing) {
                if (id !in local) local[id] = scope.launch { LocalCatchUp(host).write(id, LocalCatchUp.folderOf(root, id)) }
            }
        }
        tidying?.cancel()
        tidying = if (root == null) {
            null
        } else {
            scope.launch(Dispatchers.IO) {
                while (true) {
                    runCatching { LocalCatchUp.tidy(root, now.keepMs, now.maxBytes) }
                    // A wake lock held for days is renewed as it goes.
                    if (local.isNotEmpty()) wakeLock?.acquire(MAX_HOLD_MS)
                    delay(TIDY_MS)
                }
            }
        }
        showNotification()
        when {
            local.isNotEmpty() -> hold()
            jobs.isNotEmpty() || checking?.isActive == true -> Unit
            // Paused while the TV's off: the service waits for it to come on, letting it sleep.
            now.channelIds.isNotEmpty() && now.root != null -> release()
            else -> finish()
        }
    }

    private fun showNotification() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.recording_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val text = when {
            titles.size == 1 -> getString(R.string.recording_notification, titles.values.first())
            titles.size > 1 -> resources.getQuantityString(R.plurals.recording_notification_several, titles.size, titles.size)
            local.isNotEmpty() -> resources.getQuantityString(R.plurals.local_catch_up_notification, local.size, local.size)
            plan.channelIds.isNotEmpty() && plan.paused -> getString(R.string.local_catch_up_paused)
            else -> getString(R.string.recording_notification_starting)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_recording)
            .setContentTitle(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION, notification, type)
    }

    /** Keeps the TV and its Wi-Fi awake while anything's recording. */
    private fun hold() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)?.apply {
                setReferenceCounted(false)
                acquire(MAX_HOLD_MS)
            }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)?.createWifiLock(mode, WAKE_TAG)?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }

    private fun finish() {
        release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private companion object {
        const val CHANNEL = "recordings"
        const val NOTIFICATION = 7_101
        const val WAKE_TAG = "plasma:recording"
        const val FOLLOW_MS = 3_000L

        /** How often local catch-up lets go of what it no longer keeps. */
        const val TIDY_MS = 60_000L

        /** A recording longer than a day is still let go of then. */
        const val MAX_HOLD_MS = 24 * 3_600_000L
    }
}
