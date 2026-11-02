// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.live.bitrateText
import ca.gureet.plasma.player.EngineCount
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.FreezeEvent
import ca.gureet.plasma.player.FreezeLook
import ca.gureet.plasma.player.FreezeWatch
import ca.gureet.plasma.player.PlayerManager
import ca.gureet.plasma.ui.OverlayPopup
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Show Freeze Watch (Settings › Playback › Advanced, the owner's ask of 2 November): what Restart
 * Frozen Streams (#91) sees and does, at the top left over every screen while the setting is on.
 * It takes no keys. Each second it asks the engine for its own counts and copies the picture on
 * screen; the restarts and the rest are counted by the player whether or not it shows.
 */
@Composable
internal fun FreezeWatchPopup(graph: AppGraph) {
    val stored by graph.storedChoices.collectAsState()
    val chosen = stored?.let(::SettingsChoices)
    OverlayPopup(visible = chosen?.showFreezeWatch == true, focusable = false) {
        FreezeWatchPanel(graph, chosen?.restartFrozen?.millis)
    }
}

@Composable
private fun FreezeWatchPanel(graph: AppGraph, afterMs: Long?) {
    // The player is made the first time something plays: till then, nothing plays to watch.
    val player by produceState(graph.playerNow(), graph) {
        while (value == null) {
            delay(LOOK_MS)
            value = graph.playerNow()
        }
    }
    val words = freezeWatchWords()
    val names by remember(graph) { graph.channelNames }.collectAsState(emptyMap())
    val playing = player
    if (playing == null) {
        // The setting, and "Nothing is playing": nothing has been counted yet.
        FreezeWatchCard(freezeWatchLines(afterMs, FreezeWatch(), FreezeReading(), "", names, graph.clock.zone, Instant.now(), words).take(2))
        return
    }
    val watch by playing.freezeWatch.collectAsState()
    val bitrate by remember(playing) { playing.bitrate }.collectAsState(null)
    val reading by rememberFreezeReading(playing)
    FreezeWatchCard(freezeWatchLines(afterMs, watch, reading, bitrateText(bitrate), names, graph.clock.zone, Instant.now(), words))
}

/** The engine's counts and the copy of the picture on screen, read once a second while the overlay shows. */
@Composable
private fun rememberFreezeReading(player: PlayerManager) = remember(player) { mutableStateOf(FreezeReading()) }.also { reading ->
    LaunchedEffect(player) {
        val sampler = FreezeSampler()
        while (true) {
            val (engine, counts) = player.engineCounts()
            val picture = player.pictureHash()
            reading.value = sampler.next(SystemClock.elapsedRealtime(), player.freezeWatch.value.now.channelId, engine, counts, picture)
            delay(LOOK_MS)
        }
    }
}

/** The lines on a dark card at the top left, out of the way of the clock and the notices at the top right. */
@Composable
private fun FreezeWatchCard(lines: List<String>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Column(
            Modifier
                .padding(start = 96.px, top = 128.px)
                .widthIn(max = 1040.px)
                .background(PlasmaColors.black(0.78f), PlasmaShapes.Card)
                .padding(horizontal = 20.px, vertical = 14.px),
            verticalArrangement = Arrangement.spacedBy(4.px),
        ) {
            val strong = plasmaStyle(18f, 600, tabular = true)
            val plain = plasmaStyle(18f, 500, tabular = true)
            lines.forEachIndexed { index, line ->
                PlasmaText(line, if (index == 0) strong else plain, color = if (index == 0) PlasmaColors.Text else PlasmaColors.TextSoft, maxLines = 2)
            }
        }
    }
}

/** Show Freeze Watch's words, from the app's strings. */
@Composable
private fun freezeWatchWords(): FreezeWatchWords {
    val resources = LocalResources.current
    return remember(resources) {
        fun text(id: Int, vararg args: Any): String = resources.getString(id, *args)
        fun engineText(engine: EngineId?): String = engine?.let { text(engineName(it)) }.orEmpty()
        FreezeWatchWords(
            title = text(R.string.freeze_watch_title),
            after = { text(R.string.freeze_watch_after, it) },
            settingOff = text(R.string.freeze_watch_setting_off),
            state = { state ->
                text(
                    when (state) {
                        FreezeLook.State.Idle -> R.string.freeze_watch_idle
                        FreezeLook.State.Off -> R.string.freeze_watch_off
                        FreezeLook.State.NotLive -> R.string.freeze_watch_not_live
                        FreezeLook.State.SkipsHub -> R.string.freeze_watch_skips_hub
                        FreezeLook.State.Opening -> R.string.freeze_watch_opening
                        FreezeLook.State.Paused -> R.string.freeze_watch_paused
                        FreezeLook.State.Buffering -> R.string.freeze_watch_buffering
                        FreezeLook.State.Reconnecting -> R.string.freeze_watch_reconnecting
                        FreezeLook.State.BehindLive -> R.string.freeze_watch_behind_live
                        FreezeLook.State.NoPicture -> R.string.freeze_watch_no_picture
                        FreezeLook.State.NotMoved -> R.string.freeze_watch_not_moved
                        FreezeLook.State.Watching -> R.string.freeze_watch_moving
                    },
                )
            },
            moving = text(R.string.freeze_watch_moving),
            still = { time, bytes -> if (bytes != null) text(R.string.freeze_watch_still, time, bytes) else text(R.string.freeze_watch_still_nothing, time) },
            byPlace = text(R.string.freeze_watch_by_place),
            eachSecond = { text(R.string.freeze_watch_each_second, it) },
            soFar = { text(R.string.freeze_watch_so_far, it) },
            count = { count, value ->
                val n = value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                when (count) {
                    EngineCount.PicturesDecoded -> text(R.string.freeze_watch_decoded, value)
                    EngineCount.PicturesShown -> text(R.string.freeze_watch_shown, value)
                    EngineCount.PicturesDropped -> text(R.string.freeze_watch_dropped, value)
                    EngineCount.PictureMoves -> text(R.string.freeze_watch_picture_moved, value)
                    EngineCount.SoundPlayed -> text(R.string.freeze_watch_sound_played, value)
                    EngineCount.SoundDropped -> text(R.string.freeze_watch_sound_dropped, value)
                    EngineCount.SoundMoves -> text(R.string.freeze_watch_sound_moved, value)
                    EngineCount.Discontinuities -> resources.getQuantityString(R.plurals.freeze_watch_breaks, n, n)
                    EngineCount.Corrupted -> resources.getQuantityString(R.plurals.freeze_watch_damaged, n, n)
                }
            },
            picture = { seen, time ->
                when (seen) {
                    PictureOnScreen.Unknown -> null
                    PictureOnScreen.Unreadable -> text(R.string.freeze_watch_picture_unread)
                    PictureOnScreen.Changing -> text(R.string.freeze_watch_picture_changing)
                    PictureOnScreen.Same -> text(R.string.freeze_watch_picture_same, time)
                    PictureOnScreen.NeverChanged -> text(R.string.freeze_watch_picture_unchanged)
                }
            },
            comingIn = { text(R.string.freeze_watch_coming_in, it) },
            since = { time, what -> text(R.string.freeze_watch_since, time, what) },
            restarts = { resources.getQuantityString(R.plurals.freeze_watch_restarts, it, it) },
            handedOn = { text(R.string.freeze_watch_handed_on, it) },
            noRestarts = text(R.string.freeze_watch_no_restarts),
            event = { event, time, bytes ->
                when (event.kind) {
                    FreezeEvent.Kind.Restarted -> text(R.string.freeze_watch_event_restarted, time, bytes)
                    FreezeEvent.Kind.NextEngine -> text(R.string.freeze_watch_event_next_engine, time, bytes)
                    FreezeEvent.Kind.Buffered -> text(R.string.freeze_watch_event_buffered, time)
                    FreezeEvent.Kind.Reconnecting -> text(R.string.freeze_watch_event_reconnecting, event.attempt)
                    FreezeEvent.Kind.EngineSwitched -> text(R.string.freeze_watch_event_switched, engineText(event.to))
                }
            },
            engine = { engineText(it) },
        )
    }
}

/** An engine's name, as Player Options gives it. */
private fun engineName(engine: EngineId): Int = when (engine) {
    EngineId.Vlc -> R.string.engine_vlc
    EngineId.Mpv -> R.string.engine_mpv
    EngineId.Media3 -> R.string.engine_media3
}

/** How often the overlay reads the engine and the picture. */
private const val LOOK_MS = 1_000L
