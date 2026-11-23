// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import java.util.Locale

/**
 * Show Stream Stats (Settings › Playback › Advanced, brief 5.4.9): the stream's numbers at the top
 * right, under the overlay's clock, over the picture and everything on it, for as long as the
 * setting is on:
 *
 * ```
 * VLC 3.6.2 · mediacodec, hardware
 * 1920×1080 · 25 fps · H.264 · AC-3 5.1 passthrough
 * 8.2 Mb/s · buffer 2.1 s
 * 12 dropped frames
 * Home Portal · reconnected 2 times
 * Zap 0.8 s · 95th percentile 1.2 s of 24 · fallback 2.1 s · Switch Player 1.4 s
 * ```
 *
 * Lines it knows nothing for are left out.
 */
@Composable
internal fun StreamStatsPanel(stats: StreamStats) {
    val decoder = when (stats.hardware) {
        true -> stats.decoder?.let { stringResource(R.string.stats_decoder_hardware, it) } ?: stringResource(R.string.stats_hardware)
        false -> stats.decoder?.let { stringResource(R.string.stats_decoder_software, it) } ?: stringResource(R.string.stats_software)
        null -> stats.decoder
    }
    val buffer = stats.buffer.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.buffer_value, it) }
    val dropped = stats.droppedFrames?.let { pluralStringResource(R.plurals.stats_dropped_frames, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), it) }
    val reconnects = stats.reconnects?.let { pluralStringResource(R.plurals.stats_reconnects, it, it) }
    // How long pictures took to come up (#127); the percentile once there are two zaps to rank.
    val timings = listOfNotNull(
        stats.zapMs?.let { stringResource(R.string.stats_zap, seconds(it)) },
        stats.zapP95Ms?.takeIf { stats.zaps > 1 }?.let { stringResource(R.string.stats_zap_p95, seconds(it), stats.zaps) },
        stats.fallbackMs?.let { stringResource(R.string.stats_fallback, seconds(it)) },
        stats.switchMs?.let { stringResource(R.string.stats_switch_player, seconds(it)) },
    )
    val lines = listOf(
        listOfNotNull(stats.engine.takeIf { it.isNotEmpty() }, decoder),
        listOf(stats.format),
        listOfNotNull(stats.bitrate.takeIf { it.isNotEmpty() }, buffer),
        listOfNotNull(dropped),
        listOfNotNull(stats.source.takeIf { it.isNotEmpty() }, reconnects),
        timings,
    ).map { parts -> parts.filter { it.isNotEmpty() }.joinToString(" · ") }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Column(
            Modifier
                .padding(top = 128.px, end = 96.px)
                .widthIn(max = 760.px)
                .background(PlasmaColors.black(0.72f), PlasmaShapes.Card)
                .padding(horizontal = 20.px, vertical = 14.px),
            verticalArrangement = Arrangement.spacedBy(4.px),
        ) {
            val style = plasmaStyle(19f, 500, tabular = true)
            lines.forEach { PlasmaText(it, style, color = PlasmaColors.Text, maxLines = 1) }
        }
    }
}

/** 812 ms → "0.8 s". */
private fun seconds(ms: Long): String = String.format(Locale.US, "%.1f s", ms / MILLIS_PER_SECOND)

private const val MILLIS_PER_SECOND = 1_000.0
