// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.launcher

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why Plasma is opened as the TV's Home (brief 5.13). */
enum class HomeReason {
    /** Home was pressed: Settings › Launcher & Home › Home Button decides what opens (4.22). */
    Home,

    /** The TV has just started: When the TV Turns On decides. */
    PowerOn,
}

/**
 * The Home button in this process: whether Plasma's Home Button Override is running, and why
 * Plasma's screen is being opened.
 */
object HomeButton {
    private val _running = MutableStateFlow(false)

    /** The Home Button Override is turned on and running. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var service: WeakReference<HomeButtonService>? = null

    /** When Plasma's screen first came to the front in this process, by the boot clock; 0 for not yet. */
    @Volatile private var firstShownAt = 0L

    /** When Plasma's screen last came to the front, by the boot clock. */
    @Volatile private var shownAt = 0L

    /** When the override last opened Plasma, by the boot clock; 0 for never. */
    @Volatile private var openedAt = 0L

    internal fun connected(service: HomeButtonService) {
        this.service = WeakReference(service)
        _running.value = true
    }

    internal fun disconnected(service: HomeButtonService) {
        if (this.service?.get() !== service) return
        this.service = null
        _running.value = false
    }

    /**
     * Turns the override off from Plasma, as when another method has made Plasma the Home app
     * (4.23: one method at a time). False when it isn't running, so Accessibility has to.
     */
    fun turnOff(): Boolean {
        val running = service?.get() ?: return false
        running.disableSelf()
        return true
    }

    /**
     * One of Android's global actions, such as Back, through the override while it runs: the phone
     * remote's keys outside Plasma (brief 5.21). False when it isn't running, or Android refused.
     */
    fun globalAction(action: Int): Boolean = service?.get()?.performGlobalAction(action) ?: false

    /** Plasma's screen came to the front. */
    fun shown(now: Long = SystemClock.elapsedRealtime()) {
        if (firstShownAt == 0L) firstShownAt = now
        shownAt = now
    }

    /**
     * What opening Plasma as Home is for now: power-on while the TV is starting, until Plasma has
     * been up a few seconds (the launcher can come up over it once more as the TV settles),
     * otherwise Home.
     */
    fun reasonNow(now: Long = SystemClock.elapsedRealtime()): HomeReason = reason(now, firstShownAt)

    internal fun reason(now: Long, firstShownAt: Long): HomeReason = when {
        now >= POWER_ON_MS -> HomeReason.Home
        firstShownAt == 0L || now - firstShownAt < STARTING_MS -> HomeReason.PowerOn
        else -> HomeReason.Home
    }

    /**
     * Why [intent] opened Plasma's screen: the override says, and the system's Home intent (Plasma
     * as the Home app, methods 2 and 3) is Home or power-on. Null for an ordinary start.
     */
    fun reasonOf(intent: Intent?, now: Long = SystemClock.elapsedRealtime()): HomeReason? {
        intent ?: return null
        intent.getStringExtra(EXTRA_REASON)?.let { name -> return HomeReason.entries.firstOrNull { it.name == name } }
        return if (isHomeIntent(intent)) reasonNow(now) else null
    }

    /** The intent Android sends its Home app: MAIN in the HOME category. */
    fun isHomeIntent(intent: Intent): Boolean = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)

    /**
     * Whether the override may open Plasma [now]. Once opened, Plasma is waited for: the
     * launcher's other events as it comes up are let go, and if Plasma never comes up, the
     * launcher is left alone for a while rather than Plasma being opened over and over. Once
     * Plasma has come up, the launcher coming back over it opens it again at once.
     */
    internal fun mayOpen(now: Long = SystemClock.elapsedRealtime()): Boolean {
        val waiting = openedAt != 0L && openedAt > shownAt
        if (waiting && now - openedAt < GIVE_UP_MS) return false
        openedAt = now
        return true
    }

    /** Opens Plasma's screen from outside it, for [reason]. */
    internal fun open(context: Context, reason: HomeReason) {
        val pm = context.packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(context.packageName) ?: pm.getLaunchIntentForPackage(context.packageName) ?: return
        launch.putExtra(EXTRA_REASON, reason.name)
        // No animation: Plasma covers the launcher as soon as it can (brief 5.13, the flash).
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            context.startActivity(launch)
        } catch (refused: RuntimeException) {
            // Android turned the start down; the launcher stays.
        }
    }

    /** For tests: as when the process starts. */
    internal fun reset() {
        firstShownAt = 0L
        shownAt = 0L
        openedAt = 0L
        service = null
        _running.value = false
    }

    /** The extra the override sends with Plasma's start: a [HomeReason] by name. */
    const val EXTRA_REASON = "ca.gureet.plasma.launcher.extra.REASON"

    /** A Home opening this soon after the TV starts can be power-on. */
    const val POWER_ON_MS = 3 * 60_000L

    /** How long after Plasma first shows the TV is still starting. */
    internal const val STARTING_MS = 5_000L

    /** How long Plasma, opened and not up yet, is waited for. */
    private const val GIVE_UP_MS = 10_000L
}
