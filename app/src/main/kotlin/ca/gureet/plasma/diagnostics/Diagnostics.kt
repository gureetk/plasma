// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Process
import android.view.Display
import ca.gureet.plasma.Updates
import ca.gureet.plasma.core.data.ReportSecrets
import ca.gureet.plasma.core.network.ReportScrubber
import ca.gureet.plasma.pairing.PhoneFile
import ca.gureet.plasma.player.AudioRoutes
import ca.gureet.plasma.player.EngineState
import ca.gureet.plasma.player.FreezeEvent
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.PlayerEvent
import ca.gureet.plasma.player.PlayerManager
import ca.gureet.plasma.player.TuneTiming
import ca.gureet.plasma.player.vlc.VlcEngine
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Export Diagnostics and Save Player Logs (brief 5.17): Plasma's own [log], the players' own lines
 * while Save Player Logs is on, and the bundle made from them for a USB drive or a phone, with
 * logins, passwords and servers taken out (6.10). Channels and sources go by their ids, never their
 * names, which can name the provider.
 */
class Diagnostics(private val context: Context, private val scope: CoroutineScope) {
    private val folder = File(context.filesDir, "logs")
    private val engines = RotatingLog(folder, "engines", ENGINE_BYTES)
    private val capture = EngineLogCapture(engines, EngineLogCapture.logcat(Process.myPid()))

    /** Plasma's own log. */
    val log = DiagnosticLog(RotatingLog(folder, "plasma", PLASMA_BYTES))

    /**
     * Catches crashes from now on (#131): an uncaught exception's stack goes to [log] before the
     * app ends, and how Plasma ended since the last start, as Android recorded it, goes there now.
     */
    fun catchCrashes() {
        Thread.setDefaultUncaughtExceptionHandler(Crashes.handler(log, Thread.getDefaultUncaughtExceptionHandler()))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        scope.launch(Dispatchers.IO) {
            val seen = Crashes.seen(folder)
            val (lines, newest) = Crashes.exits(context, seen)
            // The first start with this keeps quiet about what came before.
            if (seen > 0) lines.forEach { log.note(Crashes.TOPIC, it) }
            if (newest != seen || seen == 0L) Crashes.markSeen(folder, maxOf(newest, 1L))
        }
    }

    /** Save Player Logs turned on or off: the players' lines are kept from now on, or no longer. */
    fun savePlayerLogs(on: Boolean) {
        if (on == capture.running) return
        if (on) capture.start() else capture.stop()
        log.note(TOPIC, if (on) "Save Player Logs is on" else "Save Player Logs is off")
    }

    /** Notes what [player] does from now on: what plays, what fails and why, and what Restart Frozen Streams sees. */
    fun watch(player: PlayerManager) {
        scope.launch {
            // Its first state is the player's own start, which says nothing.
            player.status.drop(1).collect { status ->
                when (status) {
                    LiveStatus.Idle -> log.note(PLAYER, "Stopped")
                    is LiveStatus.Opening -> log.note(PLAYER, "Opening ${status.channelId}")
                    is LiveStatus.Playing -> log.note(PLAYER, "Playing ${status.channelId} on ${player.engineId.value}" + fromText(player))
                    is LiveStatus.Reconnecting -> log.note(PLAYER, "Reconnecting ${status.channelId}, try ${status.attempt}")
                    is LiveStatus.Failed -> failed(player, status)
                    is LiveStatus.Ended -> log.note(PLAYER, "Ended ${status.channelId}")
                }
            }
        }
        scope.launch {
            player.engineState.collect { state ->
                if (state is EngineState.Failed) log.note(PLAYER, "${player.engineId.value} failed: ${state.detail}")
            }
        }
        scope.launch {
            // Freeze Watch keeps the latest few, and counts repeated clock jumps on one event.
            val seen = LinkedHashMap<Triple<Long, FreezeEvent.Kind, String>, Int>()
            player.freezeWatch.collect { watch ->
                for (event in watch.events.asReversed()) {
                    val key = Triple(event.at, event.kind, event.channelId)
                    if ((seen[key] ?: 0) >= event.count) continue
                    seen[key] = event.count
                    log.note(FREEZE, freezeText(event))
                }
                while (seen.size > SEEN_EVENTS) seen.remove(seen.keys.first())
            }
        }
    }

    /** Something that went wrong in the background, which nothing else says: the error, and where in Plasma. */
    fun failed(error: Throwable) {
        val at = error.stackTrace.firstOrNull { it.className.startsWith(PLASMA_CODE) }
        log.note(ERROR, error.toString() + at?.let { " at ${it.className.substringAfterLast('.')}.${it.methodName}(${it.fileName}:${it.lineNumber})" }.orEmpty())
    }

    /** What the player told the app (failover, fallbacks, restarts, choices). */
    fun event(event: PlayerEvent) {
        val text = when (event) {
            is PlayerEvent.SourceFailed -> "Source failed for ${event.channelId}: ${event.candidate}, ${event.next} next"
            is PlayerEvent.Playing -> return
            is PlayerEvent.PlayedLong -> "${event.channelId} has played 10 minutes from ${event.candidate}"
            is PlayerEvent.SwitchedEngine -> "${event.from} couldn't play ${event.channelId}; ${event.to} plays it"
            is PlayerEvent.Restarted -> if (event.onJump) "${event.channelId}'s clock jumped in VLC, so it was restarted" else "${event.channelId} froze and was restarted"
            is PlayerEvent.EngineWorked -> "${event.engine} worked for ${event.channelId} after a fallback"
            is PlayerEvent.EngineChosen -> "Switch Player: ${event.engine} for ${event.channelId}"
            is PlayerEvent.AudioChosen, is PlayerEvent.SubtitlesChosen -> return
            // How long the picture took (brief 6.7's budgets, #127), so a TV reports its own numbers.
            is PlayerEvent.Timed -> event.timing.let { "${it.kind.logName} for ${it.channelId} in ${it.engine}: ${it.millis} ms" }
        }
        log.note(PLAYER, text)
    }

    /**
     * Export Diagnostics' bundle (5.17): what Plasma and the players logged, the TV, and the
     * settings, with [secrets] and every server taken out. [settings] are the choices made, by key;
     * [extra] adds lines about Plasma's state, such as how many sources of each kind there are.
     */
    fun bundle(secrets: ReportSecrets, settings: Map<String, Int>, extra: List<String>): PhoneFile {
        val made = LocalDateTime.now()
        val files = listOf(
            ReportFile("README.txt", readme(made)),
            ReportFile("device.txt", (deviceLines() + extra).joinToString("\n", postfix = "\n")),
            ReportFile("settings.txt", settings.toSortedMap().entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key = $value" }),
            ReportFile("plasma.log", String(log.read(), Charsets.UTF_8)),
            ReportFile("players.log", String(engines.read(), Charsets.UTF_8)),
        )
        val scrubber = ReportScrubber(secrets.secrets, secrets.hosts)
        return PhoneFile("plasma-diagnostics-${FILE_STAMP.format(made)}.zip", "application/zip", diagnosticsZip(files, scrubber::scrub))
    }

    private suspend fun failed(player: PlayerManager, status: LiveStatus.Failed) {
        val stats = withTimeoutOrNull(STATS_MS) { player.stats.first() }
        val text = buildString {
            append("FAILED ").append(status.channelId).append(": ").append(status.failure)
            append("\n    engine ").append(player.engineId.value)
            stats?.engine?.let { engine ->
                engine.version?.let { append(' ').append(it) }
                engine.decoder?.let { append(", decoder ").append(it) }
                engine.hardware?.let { append(if (it) " (hardware)" else " (software)") }
                engine.bufferMs?.let { append(", ").append(it).append(" ms ahead") }
                engine.droppedFrames?.let { append(", ").append(it).append(" pictures dropped") }
            }
            player.video.value?.let { append("\n    picture ").append(it) }
            player.audio.value?.let { append("\n    sound ").append(it) }
            stats?.reconnects?.let { append("\n    the stream hub connected again ").append(it).append(" times") }
            append(fromText(player))
        }
        log.note(PLAYER, text)
    }

    private fun fromText(player: PlayerManager): String =
        player.playingFrom.value?.let { from -> " from ${from.candidate} (${from.candidates.indexOf(from.candidate) + 1} of ${from.candidates.size})" }.orEmpty()

    private fun freezeText(event: FreezeEvent): String = buildString {
        append(event.kind).append(' ').append(event.channelId).append(" on ").append(event.engine)
        when (event.kind) {
            FreezeEvent.Kind.Restarted, FreezeEvent.Kind.NextEngine -> if (event.onJump) {
                append(" as the stream's clock jumped")
            } else {
                append(" after ").append(event.ms).append(" ms still, ").append(event.bytes).append(" bytes in meanwhile")
                if (event.pictureOnly) append(", picture only")
            }
            FreezeEvent.Kind.Buffered -> append(" for ").append(event.ms).append(" ms")
            FreezeEvent.Kind.Reconnecting -> append(", try ").append(event.attempt)
            FreezeEvent.Kind.EngineSwitched -> append(" to ").append(event.to)
            FreezeEvent.Kind.ClockJumped -> {
                append(" by ").append(event.ms).append(" ms")
                if (event.count > 1) append(", ").append(event.count).append(" times")
                if (event.marked) append(", marked by the stream")
            }
        }
    }

    /** The TV and Plasma: what a report needs to know of the device. */
    private fun deviceLines(): List<String> {
        val mode = runCatching { context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.mode }.getOrNull()
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
        val route = runCatching { AudioRoutes.current(context) }.getOrNull()
        return listOfNotNull(
            "Plasma ${Updates.versionOf(context)}, ${context.packageName}",
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            "ABIs: ${Build.SUPPORTED_ABIS.joinToString()}",
            mode?.let { "Display: ${it.physicalWidth}×${it.physicalHeight} at ${String.format(Locale.US, "%.3f", it.refreshRate)} Hz" },
            "Memory: ${memory.totalMem / MB} MB, ${memory.availMem / MB} MB free" + if (memory.lowMemory) ", low" else "",
            route?.let { "Sound: ${it.connection}${it.deviceName?.let { name -> " ($name)" }.orEmpty()}, takes ${it.supported.joinToString().ifEmpty { "PCM only" }}" },
            "VLC ${VlcEngine.version()}",
            "Time zone: ${ZoneId.systemDefault()}, ${Locale.getDefault().toLanguageTag()}",
        )
    }

    private fun readme(made: LocalDateTime): String = """
        Plasma diagnostics, made ${made.format(README_STAMP)} (the TV's time).

        device.txt    the TV, Plasma and the players' versions, the sound system
        settings.txt  the choices made in Settings, by key
        plasma.log    what Plasma did: streams starting, failing (search for FAILED), engines
                      switching, Restart Frozen Streams and Freeze Watch
        players.log   VLC's, mpv's and the Android player's own lines, while Save Player Logs
                      was on

        Logins, passwords, tokens and MAC addresses are taken out. Every server is named
        server-1, server-2 and so on, the same name for the same server in every file.
        Channels and sources are named by their ids in Plasma, not by their names.
    """.trimIndent() + "\n"

    private companion object {
        const val TOPIC = "diagnostics"
        const val PLAYER = "player"
        const val FREEZE = "freeze"
        const val ERROR = "error"
        const val PLASMA_CODE = "ca.gureet.plasma"

        /** Plasma's log keeps two files of this size at most. */
        const val PLASMA_BYTES = 1L * 1024 * 1024

        /** The players' log, much busier while Save Player Logs is on: two files of this size at most. */
        const val ENGINE_BYTES = 4L * 1024 * 1024

        const val SEEN_EVENTS = 64
        const val STATS_MS = 1_500L
        const val MB = 1024 * 1024

        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        val README_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

/** A timing as the log names it: "Zap to picture", "Fallback to picture", "Switch Player to picture". */
private val TuneTiming.Kind.logName: String
    get() = when (this) {
        TuneTiming.Kind.Zap -> "Zap to picture"
        TuneTiming.Kind.Fallback -> "Fallback to picture"
        TuneTiming.Kind.SwitchPlayer -> "Switch Player to picture"
    }
