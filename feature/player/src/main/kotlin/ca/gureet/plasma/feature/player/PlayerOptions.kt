// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.boxShadow
import ca.gureet.plasma.core.design.component.CenteredKeyFooter
import ca.gureet.plasma.core.design.component.FooterKey
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.component.LogoPlate
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Instant

/** The sheet's glassy fill over the blurred video (board 10). */
private val SheetFill = Color(0xD11E1E21)

/**
 * Player Options (board 10, brief 4.10): a sheet over the blurred video with what can be done to
 * this programme and this channel, then audio, picture, source and player choices, and the stream's
 * health at the bottom. Watch from the Start has focus.
 */
@Composable
internal fun PlayerOptions(
    channel: PlayerChannel,
    now: Instant,
    playback: PlaybackInfo,
    favoriteIndex: Int?,
    favoritesCount: Int,
    onStartOver: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSwitchPlayer: () -> Unit,
) {
    val zone = LocalPlasmaClock.current.zone
    val program = channel.programAt(now)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }
    Box(Modifier.fillMaxSize().background(PlasmaColors.black(0.25f))) {
        Column(
            Modifier
                .offset(560.px, 112.px)
                .width(800.px)
                .boxShadow(PlasmaColors.black(0.6f), offsetY = 40.px, blur = 90.px, cornerRadius = 28.px)
                .background(SheetFill, PlasmaShapes.Sheet)
                .drawWithContent {
                    drawContent()
                    drawLine(PlasmaColors.white(0.08f), Offset(28.px.toPx(), 0.5f), Offset(size.width - 28.px.toPx(), 0.5f))
                }
                .padding(start = 30.px, end = 30.px, top = 30.px, bottom = 26.px),
        ) {
            Row(
                Modifier.padding(horizontal = 10.px),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.px),
            ) {
                LogoPlate(
                    channel.logo,
                    channel.name,
                    120.px,
                    62.px,
                    logoSize = LogoSize.Plate,
                    color = PlasmaColors.black(0.35f),
                    shape = PlasmaShapes.Button,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
                    PlasmaText(program?.title ?: channel.name, plasmaStyle(30f, 600, trackingPx = -0.4f))
                    val meta = buildList {
                        add(channel.number)
                        add(channel.name)
                        if (program != null) add(Formats.range(program.start.atZone(zone), program.end.atZone(zone)))
                        if (playback.behindLiveMs > 0) add(stringResource(R.string.behind_live_meta, clockDuration(playback.behindLiveMs)))
                    }.joinToString(" · ")
                    PlasmaText(meta, plasmaStyle(21f, 500, tabular = true), color = PlasmaColors.Text2)
                }
            }
            Box(Modifier.padding(top = 22.px, bottom = 14.px).fillMaxWidth().height(1.px).background(PlasmaColors.white(0.1f)))

            Column(Modifier.focusRestoringGroup(first), verticalArrangement = Arrangement.spacedBy(4.px)) {
                if (program != null && channel.catchUp) {
                    OptionRow(
                        icon = { PlasmaIcon(PlasmaIcons.StartOver, 24.px) },
                        label = stringResource(R.string.watch_from_start),
                        value = stringResource(R.string.started_at, Formats.time(program.start.atZone(zone))),
                        onClick = onStartOver,
                        modifier = Modifier.focusRequester(first),
                    )
                }
                if (program != null) {
                    if (program.recording) {
                        OptionRow(
                            icon = { PlasmaIcon(PlasmaIcons.StopSquare, 24.px, tint = PlasmaColors.Live) },
                            label = stringResource(R.string.stop_recording),
                            value = stringResource(R.string.recording_since, Formats.time(program.start.atZone(zone))),
                            onClick = {},
                            modifier = if (!channel.catchUp) Modifier.focusRequester(first) else Modifier,
                        )
                    } else {
                        OptionRow(
                            icon = { RecordingIcon(24.px) },
                            label = stringResource(R.string.record_program),
                            value = null,
                            onClick = {},
                            modifier = if (!channel.catchUp) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                    if (program.series != null) {
                        OptionRow(
                            icon = { SeriesRecordIcon() },
                            label = stringResource(R.string.record_series, program.series),
                            value = stringResource(R.string.series_rule),
                            onClick = {},
                        )
                    }
                }
                OptionRow(
                    icon = { PlasmaIcon(PlasmaIcons.Star, 24.px) },
                    label = stringResource(if (favoriteIndex != null) R.string.remove_from_favorites else R.string.add_to_favorites),
                    value = favoriteIndex?.let { stringResource(R.string.favorite_n_of_m, it, favoritesCount) },
                    onClick = onToggleFavorite,
                    modifier = if (program == null) Modifier.focusRequester(first) else Modifier,
                )
                Box(Modifier.padding(horizontal = 22.px, vertical = 8.px).fillMaxWidth().height(1.px).background(PlasmaColors.Divider))
                OptionRow(
                    icon = { PlasmaIcon(PlasmaIcons.Volume, 24.px, tint = iconTint()) },
                    label = stringResource(R.string.audio_and_subtitles),
                    value = "${playback.audio} · ${playback.subtitles ?: stringResource(R.string.off)}",
                    onClick = {},
                )
                OptionRow(
                    icon = { PlasmaIcon(PlasmaIcons.PictureSize, 24.px, tint = iconTint()) },
                    label = stringResource(R.string.picture_size),
                    value = playback.pictureSize,
                    onClick = {},
                )
                OptionRow(
                    icon = { PlasmaIcon(PlasmaIcons.SwitchSource, 24.px, tint = iconTint()) },
                    label = stringResource(R.string.switch_source),
                    value = playback.nextSource?.let { "${playback.source} → $it" } ?: playback.source,
                    onClick = {},
                )
                OptionRow(
                    icon = { PlasmaIcon(PlasmaIcons.SwitchPlayer, 24.px, tint = iconTint()) },
                    label = stringResource(R.string.switch_player),
                    value = "${playback.engine} → ${playback.nextEngine}",
                    onClick = onSwitchPlayer,
                )
                OptionRow(
                    icon = { PlasmaIcon(PlasmaIcons.Moon, 24.px, tint = iconTint()) },
                    label = stringResource(R.string.sleep_timer),
                    value = playback.sleepTimer ?: stringResource(R.string.off),
                    onClick = {},
                )
            }

            Row(
                Modifier
                    .padding(top = 18.px)
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        drawLine(PlasmaColors.white(0.1f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.px.toPx())
                    }
                    .padding(start = 22.px, end = 22.px, top = 16.px),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val info = plasmaStyle(19f, 500, tabular = true)
                Box(
                    Modifier.size(9.px).background(
                        when (playback.health) {
                            StreamHealth.Good -> PlasmaColors.Ok
                            StreamHealth.Warning -> PlasmaColors.Warn
                            StreamHealth.Error -> PlasmaColors.Live
                        },
                        CircleShape,
                    ),
                )
                PlasmaText("${playback.engine} · ${playback.format}", info, Modifier.padding(start = 10.px).weight(1f), color = PlasmaColors.Text3)
                PlasmaText("${playback.bitrate} · buffer ${playback.buffer}", info, color = PlasmaColors.Text3)
            }
        }
        CenteredKeyFooter(
            keys = listOf(
                FooterKey("OK", stringResource(R.string.key_select)),
                FooterKey(PlasmaIcons.Back, stringResource(R.string.key_back_to_program)),
            ),
        )
    }
}

@Composable
private fun iconTint(): Color = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.TextSoft

/** A row in a sheet: icon, label, and the current value on the right. 62 px, 64 when focused. */
@Composable
private fun OptionRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val height by animateDpAsState(if (focused) 64.px else 62.px, PlasmaMotion.focus(), label = "optionHeight")
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height),
        style = FocusStyle.Row,
        shape = PlasmaShapes.Row,
        onFocusChanged = { focused = it },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.padding(horizontal = 22.px),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.px),
        ) {
            icon()
            PlasmaText(label, plasmaStyle(24f, if (focused) 600 else 500), Modifier.weight(1f))
            if (value != null) {
                PlasmaText(value, plasmaStyle(21f, 500, tabular = true), color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text2)
            }
        }
    }
}

/** Two overlapping red dots: record every episode (board 10). */
@Composable
private fun SeriesRecordIcon() {
    Box(Modifier.size(24.px)) {
        Box(Modifier.offset(0.px, 5.px).size(14.px).alpha(0.55f).background(PlasmaColors.Live, CircleShape))
        Box(
            Modifier
                // A 14 px dot with a 2.5 px ring in the sheet's colour, so it reads as in front.
                .offset(6.5f.px, 2.5f.px)
                .size(19.px)
                .background(if (LocalFocused.current) PlasmaColors.Text else SeriesRing, CircleShape)
                .padding(2.5f.px)
                .background(PlasmaColors.Live, CircleShape),
        )
    }
}

private val SeriesRing = Color(0xFF2A2A2D)
