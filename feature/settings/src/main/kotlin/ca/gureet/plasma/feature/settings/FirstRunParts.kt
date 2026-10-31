// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.LARGE_TEXT_SCALE
import ca.gureet.plasma.core.design.LocalReduceMotion
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaTheme
import ca.gureet.plasma.core.design.ShownFocused
import ca.gureet.plasma.core.design.component.CaptionedTile
import ca.gureet.plasma.core.design.component.LogoImage
import ca.gureet.plasma.core.design.component.LogoSize
import ca.gureet.plasma.core.design.component.PlasmaImage
import ca.gureet.plasma.core.design.component.ProgressBar
import ca.gureet.plasma.core.design.focusFree
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.Sample
import ca.gureet.plasma.core.model.Formats

// ---- What a new source has loaded (brief 4.35's sync progress, kept small) ------------------------

private enum class Progress { Done, Waiting, Failed }

@Immutable
private class ProgressLine(val progress: Progress, val text: String)

/**
 * What a source just added has loaded (brief 4.35's sync progress, kept small): its channels, then
 * while it's still loading, its guide, then its movies and series (a playlist has none). Once it's
 * all in, the channels alone. [named] puts the source's name over it, for more than one.
 */
@Composable
internal fun SourceProgress(source: SourceInfo, named: Boolean = false) {
    val lines = buildList {
        add(ProgressLine(Progress.Done, pluralStringResource(R.plurals.channel_count, source.channels, Formats.count(source.channels))))
        val library = source.type != SourceType.M3U
        when (source.loading) {
            SourceLoading.Guide -> {
                add(ProgressLine(Progress.Waiting, stringResource(R.string.setup_guide_loading)))
                if (library) add(ProgressLine(Progress.Waiting, stringResource(R.string.setup_library_waiting)))
            }
            SourceLoading.Library -> {
                // The guide's import has ended; a failure there leaves the source saying so.
                if (source.health == SourceHealth.RefreshFailed) {
                    add(ProgressLine(Progress.Failed, stringResource(R.string.setup_guide_failed)))
                } else {
                    add(ProgressLine(Progress.Done, stringResource(R.string.setup_guide_loaded)))
                }
                if (library) add(ProgressLine(Progress.Waiting, stringResource(R.string.setup_library_loading)))
            }
            null -> Unit
        }
    }
    Column(Modifier.padding(top = 30.px).width(560.px), verticalArrangement = Arrangement.spacedBy(14.px)) {
        if (named) PlasmaText(source.name, plasmaStyle(20f, 600), color = PlasmaColors.Text3)
        for (line in lines) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.px)) {
                when (line.progress) {
                    Progress.Done -> PlasmaIcon(PlasmaIcons.Check, 24.px, tint = PlasmaColors.Ok, stroke = 2.4f)
                    Progress.Waiting -> PlasmaIcon(PlasmaIcons.History, 24.px, tint = PlasmaColors.Text3, stroke = 2f)
                    Progress.Failed -> PlasmaIcon(PlasmaIcons.Caution, 24.px, tint = PlasmaColors.Warn, stroke = 2.2f)
                }
                PlasmaText(
                    line.text,
                    plasmaStyle(22f, 500, tabular = true),
                    color = if (line.progress == Progress.Waiting) PlasmaColors.Text2 else PlasmaColors.TextSoft3,
                )
            }
        }
    }
}

// ---- The look, previewed (brief 5.16) -----------------------------------------------------------

/**
 * A look before it's chosen (#85): two of Home's live tiles, the first with focus, their progress
 * in the [accent] (Appearance's option) and their captions at Default or [large] text. It's drawn
 * with Plasma's own theme for that look, as the whole app will be once it's chosen; focus stays
 * white whatever the accent. Focus never goes into it: it's a picture.
 */
@Composable
internal fun LookPreview(accent: Int, large: Boolean) {
    PlasmaTheme(accent = PlasmaColors.accent(accent), reduceMotion = LocalReduceMotion.current, textScale = if (large) LARGE_TEXT_SCALE else 1f) {
        Row(Modifier.padding(top = 36.px).focusFree(), horizontalArrangement = Arrangement.spacedBy(28.px)) {
            ShownFocused { PreviewTile(Sample.harbourCity) }
            Sample.nowOn(Sample.velocity)?.let { PreviewTile(it) }
        }
    }
}

/** A live tile as Home's rows draw one: the picture, the logo on a scrim, what's on with its progress. */
@Composable
private fun PreviewTile(program: Sample.Program) {
    val channel = program.channel
    CaptionedTile(title = program.title, meta = channel.name, onClick = {}, width = 306.px, height = 172.px) {
        PlasmaImage(channel.still, Modifier.fillMaxSize(), alignment = channel.stillAlignment)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to PlasmaColors.black(0.62f), 0.52f to Color.Transparent)))
        Box(Modifier.padding(start = 14.px).height(52.px), contentAlignment = Alignment.CenterStart) {
            LogoImage(channel.logo, channel.name, LogoSize.Thumbnail)
        }
        ProgressBar(program.progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), height = 5.px, track = PlasmaColors.white(0.18f), rounded = false)
    }
}
