// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.Hairline
import ca.gureet.plasma.core.design.LocalContentColor
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.ClockText
import ca.gureet.plasma.core.design.component.LiveMark
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.outline
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Instant

/**
 * The player overlay (board 09): the programme, a timeshift bar from the programme's start to its
 * end with the live edge marked, and the controls. Play/Pause has focus, or the bar when the
 * overlay opened with Right ([focusTimeline], brief 3.6); Up reaches the bar, where Left and Right
 * seek 10 s.
 */
@Composable
internal fun PlayerControls(
    channel: PlayerChannel,
    now: Instant,
    playback: PlaybackInfo,
    onPlayPause: () -> Unit,
    onSeek: (deltaMs: Long) -> Unit,
    onStartOver: () -> Unit,
    onGoLive: () -> Unit,
    onOptions: () -> Unit,
    onMultiview: () -> Unit,
    onChannelList: () -> Unit,
    focusTimeline: Boolean = false,
    /** Opened from the "Started 20 min ago" hint: Start Over has focus, so OK starts it (4.11). */
    focusStartOver: Boolean = false,
) {
    // The program where the picture is: behind live, or from the archive.
    val position = now.minusMillis(playback.behindLiveMs)
    val program = channel.programAt(position)
    val next = channel.programAfter(position)
    val playPause = remember { FocusRequester() }
    val bar = remember { FocusRequester() }
    val startOver = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        when {
            focusStartOver && channel.catchUp -> startOver.requestFocus()
            focusTimeline && program != null -> bar.requestFocus()
            else -> playPause.requestFocus()
        }
    }
    Box(Modifier.fillMaxSize()) {
        // A scrim under the controls, and a soft shade behind the clock.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    520f / 1080f to Color.Transparent,
                    700f / 1080f to PlasmaColors.black(0.55f),
                    900f / 1080f to PlasmaColors.black(0.88f),
                    1f to PlasmaColors.black(0.92f),
                ),
            ),
        )
        Box(
            Modifier
                .offset(1500.px, 0.px)
                .size(420.px, 180.px)
                .background(Brush.radialGradient(listOf(PlasmaColors.black(0.45f), Color.Transparent), center = Offset(714f, 108f), radius = 420f)),
        )
        Box(Modifier.offset(1624.px, 56.px).width(200.px), contentAlignment = Alignment.CenterEnd) { ClockText() }

        if (program != null) {
            NowPlaying(channel, program, next, live = program.end.isAfter(now), Modifier.offset(96.px, 690.px).width(1728.px))
            TimeshiftBar(
                program = program,
                now = now,
                behindLiveMs = playback.behindLiveMs,
                onSeek = onSeek,
                modifier = Modifier
                    .offset(96.px, 880.px)
                    .width(1728.px)
                    .focusRequester(bar)
                    .focusProperties { down = playPause },
            )
        }

        Row(
            Modifier.offset(96.px, 944.px).width(1728.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.px), verticalAlignment = Alignment.CenterVertically) {
                // Start Over needs the provider's archive (brief 5.5).
                RoundButton(
                    PlasmaIcons.StartOver,
                    stringResource(R.string.start_over),
                    onStartOver,
                    iconSize = 26.px,
                    enabled = channel.catchUp,
                    modifier = Modifier.focusRequester(startOver),
                )
                RoundButton(PlasmaIcons.SkipBack, stringResource(R.string.back_10), { onSeek(-10_000) }, iconSize = 30.px, label = "10")
                RoundButton(
                    if (playback.paused) PlasmaIcons.Play else PlasmaIcons.Pause,
                    stringResource(if (playback.paused) R.string.play else R.string.pause),
                    onPlayPause,
                    size = 76.px,
                    iconSize = 30.px,
                    modifier = Modifier.focusRequester(playPause),
                )
                RoundButton(PlasmaIcons.SkipForward, stringResource(R.string.forward_10), { onSeek(10_000) }, iconSize = 30.px, label = "10")
                PlasmaSurface(
                    onClick = onGoLive,
                    modifier = Modifier.height(64.px),
                    style = FocusStyle.Button,
                    shape = CircleShape,
                    color = PlasmaColors.white(0.14f),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.padding(start = 20.px, end = 24.px),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.px),
                    ) {
                        Box(Modifier.size(10.px).background(PlasmaColors.Live, CircleShape))
                        PlasmaText(stringResource(R.string.go_live), plasmaStyle(23f, 600))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.px), verticalAlignment = Alignment.CenterVertically) {
                PillButton(PlasmaIcons.Volume, playback.audio, onOptions)
                PillButton(PlasmaIcons.Subtitles, playback.subtitles ?: stringResource(R.string.subtitles_off), onOptions)
                if (program?.recording == true) {
                    PillButton(null, stringResource(R.string.recording), onOptions, leading = { RecordingIcon(22.px) })
                }
                SquareButton(PlasmaIcons.Multiview, stringResource(R.string.multiview), onMultiview)
                SquareButton(PlasmaIcons.ChannelList, stringResource(R.string.channel_list), onChannelList)
            }
        }
    }
}

/** Logo, number and LIVE; the title; subtitle and time; badges and what's next on the right. */
@Composable
private fun NowPlaying(channel: PlayerChannel, program: PlayerProgram, next: PlayerProgram?, live: Boolean, modifier: Modifier) {
    val zone = LocalPlasmaClock.current.zone
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.px)) {
                LogoImage(channel.logo, channel.name, LogoSize.Player)
                PlasmaText(channel.number, plasmaStyle(23f, 500, tabular = true), color = PlasmaColors.TextSoft)
                Box(Modifier.size(1.px, 26.px).background(PlasmaColors.white(0.25f)))
                if (live) {
                    LiveMark(stringResource(R.string.live), style = plasmaStyle(19f, 600, trackingPx = 1.4f), dot = 9.px, gap = 9.px)
                } else {
                    // A program from the archive.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.px)) {
                        PlasmaIcon(PlasmaIcons.CatchUp, 18.px, stroke = 2.4f, tint = PlasmaColors.TextSoft)
                        PlasmaText(stringResource(R.string.catch_up), plasmaStyle(19f, 600), color = PlasmaColors.TextSoft)
                    }
                }
            }
            PlasmaText(
                program.title,
                plasmaStyle(52f, 600, trackingPx = -1.2f, lineHeight = 1.06f),
                Modifier.padding(top = 14.px),
                color = Color.White,
            )
            Row(
                Modifier.padding(top = 10.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.px),
            ) {
                val meta = plasmaStyle(24f, 400)
                if (program.subtitle != null) {
                    PlasmaText(program.subtitle, meta, color = PlasmaColors.TextSoft)
                    PlasmaText("·", meta, color = PlasmaColors.Text3)
                }
                PlasmaText(
                    Formats.range(program.start.atZone(zone), program.end.atZone(zone)),
                    meta.copy(fontFeatureSettings = "tnum"),
                    color = PlasmaColors.TextSoft,
                )
            }
        }
        Column(Modifier.padding(bottom = 4.px), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.px)) {
            if (program.badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.px)) { for (badge in program.badges) VideoBadge(badge) }
            }
            if (next != null) {
                PlasmaText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = PlasmaColors.Text3)) { append(stringResource(R.string.next)) }
                        append(" ")
                        append(Formats.time(next.start.atZone(zone)))
                        append(" ")
                        append(next.title)
                    },
                    plasmaStyle(22f, 500),
                    color = PlasmaColors.TextSoft,
                )
            }
        }
    }
}

/**
 * HD, 50 FPS, 5.1 over video: an outlined badge with more contrast than on Plasma's own screens.
 * The 28 px height and the padding are inside the border, as on boards 09 and 11 (30 px tall).
 */
@Composable
internal fun VideoBadge(text: String) {
    Box(
        Modifier.outline(Hairline, PlasmaColors.white(0.45f), PlasmaShapes.Badge).height(28.px).padding(horizontal = 8.px),
        contentAlignment = Alignment.Center,
    ) {
        PlasmaText(text, plasmaStyle(15f, 700, trackingPx = 0.4f), color = PlasmaColors.TextSoft3)
    }
}

/** ◉ with a red centre. */
@Composable
internal fun RecordingIcon(size: Dp, ring: Color = LocalContentColor.current) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        PlasmaIcon(PlasmaIcons.Ring, size, stroke = 2f, tint = ring)
        Box(Modifier.size(size * (8f / 24f)).background(PlasmaColors.Live, CircleShape))
    }
}

// ---- Timeshift bar -------------------------------------------------------------------------------

/**
 * From the programme's start to its end (board 09): what's been played in white, the buffer up to
 * the live edge at 50 %, a red tick at the live edge, and the playhead with "20:39 −2:14 behind live".
 */
@Composable
private fun TimeshiftBar(program: PlayerProgram, now: Instant, behindLiveMs: Long, onSeek: (Long) -> Unit, modifier: Modifier) {
    val zone = LocalPlasmaClock.current.zone
    val start = program.start.toEpochMilli()
    val length = (program.end.toEpochMilli() - start).toFloat()
    val live = ((now.toEpochMilli() - start) / length).coerceIn(0f, 1f)
    val playheadTime = now.minusMillis(behindLiveMs)
    val playhead = ((playheadTime.toEpochMilli() - start) / length).coerceIn(0f, 1f)
    var focused by remember { mutableStateOf(false) }
    val knob by animateFloatAsState(if (focused) 1.2f else 1f, label = "knob")
    val description = stringResource(R.string.timeline)
    Column(
        modifier
            .semantics { contentDescription = description }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeek(-10_000); true }
                    Key.DirectionRight -> { onSeek(10_000); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        Box(Modifier.fillMaxWidth().height(6.px)) {
            Box(Modifier.fillMaxSize().background(PlasmaColors.white(0.22f), RoundedCornerShape(3.px)))
            Box(Modifier.fillMaxWidth(playhead).fillMaxHeight().background(Color.White, RoundedCornerShape(3.px)))
            // Buffered, between the playhead and the live edge.
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val width = ((live - playhead) * constraints.maxWidth).toInt().coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(width, placeable.height) { placeable.place((playhead * constraints.maxWidth).toInt(), 0) }
                    }
                    .fillMaxHeight()
                    .background(PlasmaColors.white(0.5f)),
            )
            // The live edge.
            Box(Modifier.centeredAt(live).offset(y = (-6).px).size(3.px, 18.px).background(PlasmaColors.Live, RoundedCornerShape(2.px)))
            // The playhead.
            Box(
                Modifier
                    .centeredAt(playhead)
                    .offset(y = (-11).px)
                    .size(28.px)
                    .scale(knob)
                    .shadow(7.px, CircleShape)
                    .background(Color.White, CircleShape),
            )
            Row(
                Modifier
                    .centeredAt(playhead)
                    .offset(y = (-62).px)
                    .height(38.px)
                    .background(Color.White, RoundedCornerShape(9.px))
                    .padding(horizontal = 14.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.px),
            ) {
                // To the nearest minute: 2:14 behind 20:41 reads 20:39.
                PlasmaText(Formats.time(playheadTime.plusSeconds(30).atZone(zone)), plasmaStyle(19f, 600, tabular = true), color = PlasmaColors.OnFocus)
                if (behindLiveMs > 0) {
                    PlasmaText(
                        stringResource(R.string.behind_live, clockDuration(behindLiveMs)),
                        plasmaStyle(19f, 500, tabular = true),
                        color = PlasmaColors.OnFocusSoft,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 14.px).fillMaxWidth()) {
            val label = plasmaStyle(20f, 500, tabular = true)
            PlasmaText(Formats.time(program.start.atZone(zone)), label, color = PlasmaColors.Text2)
            Spacer(Modifier.weight(1f))
            PlasmaText(Formats.time(program.end.atZone(zone)), label, color = PlasmaColors.Text2, align = TextAlign.End)
        }
    }
}

/** "2:14", minutes and seconds behind live (or hours, minutes and seconds). */
internal fun clockDuration(ms: Long): String {
    val seconds = ms / 1_000
    val h = seconds / 3_600
    val m = seconds / 60 % 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Places the element so its centre sits [fraction] of the way across its parent. */
private fun Modifier.centeredAt(fraction: Float): Modifier = layout { measurable, constraints ->
    // Free of the 6 px bar's height, so the knob and bubble keep their own size.
    val placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth))
    layout(placeable.width, placeable.height) {
        placeable.place((fraction * constraints.maxWidth - placeable.width / 2f).toInt(), 0)
    }
}

// ---- Buttons -------------------------------------------------------------------------------------

/** A round control: Start Over, ±10 s, Play/Pause (white when focused). */
@Composable
private fun RoundButton(
    icon: PlasmaIcons,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.px,
    iconSize: Dp,
    label: String? = null,
    enabled: Boolean = true,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.size(size).semantics { contentDescription = description },
        style = FocusStyle.Button,
        shape = CircleShape,
        color = PlasmaColors.white(0.14f),
        enabled = enabled,
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
            PlasmaIcon(icon, iconSize)
            if (label != null) {
                // The "10" inside the ±10 s arrows (board 09).
                PlasmaText(label, plasmaStyle(iconSize.value * 2f * 0.3f, 700, tabular = true), Modifier.offset(y = iconSize * 0.04f))
            }
        }
    }
}

/** Audio, Subtitles, Recording: an icon and a value in a rounded pill. */
@Composable
private fun PillButton(icon: PlasmaIcons?, text: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    PlasmaSurface(
        onClick = onClick,
        modifier = Modifier.height(56.px),
        style = FocusStyle.Button,
        shape = PlasmaShapes.Button,
        color = PlasmaColors.Selected,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(start = 18.px, end = 20.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.px),
        ) {
            if (icon != null) PlasmaIcon(icon, 22.px)
            leading?.invoke()
            PlasmaText(text, plasmaStyle(22f, if (LocalFocused.current) 600 else 500))
        }
    }
}

@Composable
private fun SquareButton(icon: PlasmaIcons, description: String, onClick: () -> Unit) {
    PlasmaSurface(
        onClick = onClick,
        modifier = Modifier.size(56.px).semantics { contentDescription = description },
        style = FocusStyle.Button,
        shape = PlasmaShapes.Button,
        color = PlasmaColors.Selected,
        contentAlignment = Alignment.Center,
    ) {
        PlasmaIcon(icon, 24.px)
    }
}
