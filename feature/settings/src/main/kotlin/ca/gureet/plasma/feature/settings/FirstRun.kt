// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaMotion
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.design.pxSp
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.design.R as DesignR

/**
 * First-time setup's screens, in order (#85; brief 3.4, A): the welcome, the five steps, and All
 * Set. Each step is a page of Settings, so a choice made in it is the same setting there.
 */
enum class SetupScreen {
    Welcome,
    Service,
    HomeScreen,
    Profiles,
    Look,
    Guide,
    Done,
    ;

    /** Its number among the steps, from 1; null for the welcome and All Set. */
    val step: Int? get() = ordinal.takeIf { this != Welcome && this != Done }

    internal val next: SetupScreen get() = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]

    internal val previous: SetupScreen get() = entries[(ordinal - 1).coerceAtLeast(0)]

    companion object {
        /** How many steps there are between the welcome and All Set. */
        val STEPS: Int = entries.size - 2
    }
}

/** What leaves first-time setup (#85). */
class SetupActions(
    /**
     * Skip for Now: on the first run, Home with the sample channels, setup done; run again from
     * Settings, where it reads Back to Settings, back there.
     */
    val onSkip: () -> Unit = {},
    /** Go to Home, on All Set: setup is done, and Home opens. */
    val onFinish: () -> Unit = {},
)

/**
 * First-time setup (#85): the welcome, then the steps (Your TV Service, Home Screen, Profiles and
 * Parental PIN, Look, Guide and Channel Numbers), each a page of Settings with Continue at the end,
 * then All Set. Back goes to the screen before; on the welcome it's the app's, which leaves for
 * Settings when setup was run [again] from there. Each screen keeps where focus was while another
 * shows, as Settings' pages do. [actions] are Settings' own for each step's rows, which it shares
 * with Settings; a screen they open names the step as where Back goes.
 */
@Composable
fun FirstTimeSetup(
    values: SettingsValues,
    actions: (SetupScreen) -> SettingsActions,
    setup: SetupActions,
    modifier: Modifier = Modifier,
    facts: SettingsFacts = SettingsSamples.facts,
    again: Boolean = false,
    /** Where it opens: the welcome, or a step for previews and tests. */
    start: SetupScreen = SetupScreen.Welcome,
) {
    var screen by rememberSaveable { mutableStateOf(start) }
    val saved = rememberSaveableStateHolder()
    BackHandler(enabled = screen != SetupScreen.Welcome) { screen = screen.previous }
    Crossfade(screen, modifier.fillMaxSize(), animationSpec = PlasmaMotion.screen(), label = "setup") { shown ->
        saved.SaveableStateProvider(shown.name) {
            when (shown) {
                SetupScreen.Welcome -> SetupWelcome(again, onStart = { screen = SetupScreen.Service }, onSkip = setup.onSkip)
                SetupScreen.Done -> SetupDone(facts, values, onFinish = setup.onFinish)
                else -> SetupStep(shown, values, actions(shown), facts, onContinue = { screen = shown.next })
            }
        }
    }
}

/**
 * The welcome (brief 3.4, A1, and 4.35): the wordmark as the top bar has it, larger, a line on what
 * Plasma is, Set Up Plasma with focus and Skip for Now. Restore from Backup waits for backups.
 */
@Composable
private fun SetupWelcome(again: Boolean, onStart: () -> Unit, onSkip: () -> Unit) {
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        start.requestFocus()
    }
    Box(Modifier.fillMaxSize().glow(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PlasmaText(stringResource(DesignR.string.wordmark), PlasmaType.Wordmark.copy(fontSize = WORDMARK_PX.pxSp))
            PlasmaText(stringResource(R.string.setup_pitch), plasmaStyle(30f, 500), Modifier.padding(top = 20.px), color = PlasmaColors.Text2)
            Row(Modifier.padding(top = 72.px).focusRestoringGroup(start), horizontalArrangement = Arrangement.spacedBy(20.px)) {
                PrimaryButton(stringResource(R.string.setup_start), onStart, Modifier.focusRequester(start))
                PlasmaButton(stringResource(if (again) R.string.setup_back_to_settings else R.string.setup_skip), onClick = onSkip)
            }
            PlasmaText(
                stringResource(if (again) R.string.setup_again_welcome_text else R.string.setup_skip_text),
                plasmaStyle(21f, 500),
                Modifier.padding(top = 34.px),
                color = PlasmaColors.Text3,
                align = TextAlign.Center,
            )
        }
    }
}

/**
 * All Set (brief 3.4, A6): a line for each thing set up, only what's true, and Go to Home with
 * focus. With no source, it says the sample channels are on.
 */
@Composable
private fun SetupDone(facts: SettingsFacts, values: SettingsValues, onFinish: () -> Unit) {
    val home = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        home.requestFocus()
    }
    val lines = doneLines(facts, values)
    Box(Modifier.fillMaxSize().glow(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PlasmaText(stringResource(R.string.setup_done), plasmaStyle(56f, 600, trackingPx = -1.3f))
            if (facts.sources.isEmpty()) {
                PlasmaText(
                    stringResource(R.string.setup_done_sample),
                    plasmaStyle(24f, 500, lineHeight = 1.35f),
                    Modifier.padding(top = 30.px).widthIn(max = 900.px),
                    color = PlasmaColors.Text2,
                    align = TextAlign.Center,
                    maxLines = 2,
                )
            }
            if (lines.isNotEmpty()) {
                Column(Modifier.padding(top = 36.px), verticalArrangement = Arrangement.spacedBy(16.px)) {
                    for (line in lines) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.px)) {
                            PlasmaIcon(PlasmaIcons.Check, 26.px, tint = PlasmaColors.Ok, stroke = 2.4f)
                            PlasmaText(line, plasmaStyle(25f, 500), color = PlasmaColors.TextSoft3)
                        }
                    }
                }
            }
            PrimaryButton(stringResource(R.string.setup_go_home), onFinish, Modifier.padding(top = 56.px).focusRequester(home))
            PlasmaText(stringResource(R.string.setup_done_text), plasmaStyle(21f, 500), Modifier.padding(top = 34.px), color = PlasmaColors.Text3)
        }
    }
}

/** What All Set lists, only what's true: the sources, Plasma as Home, profiles, the PIN, a look of one's own. */
@Composable
private fun doneLines(facts: SettingsFacts, values: SettingsValues): List<String> {
    val accents = stringArrayResource(R.array.accent_options)
    return buildList {
        val sources = facts.sources
        if (sources.isNotEmpty()) {
            val channels = sources.sumOf { it.channels }
            val named = sources.singleOrNull()?.name ?: pluralStringResource(R.plurals.source_count, sources.size, sources.size)
            add(stringResource(R.string.setup_done_sources, named, pluralStringResource(R.plurals.channel_count, channels, Formats.count(channels))))
        }
        if (facts.home.method != null) add(stringResource(R.string.launcher_value))
        if (facts.profiles.size > 1) add(pluralStringResource(R.plurals.profile_count, facts.profiles.size, facts.profiles.size))
        if (facts.pinSet) add(stringResource(R.string.setup_done_pin))
        val accent = values.index(KEY_ACCENT, 0)
        if (accent != 0) accents.getOrNull(accent)?.let { add(stringResource(R.string.setup_done_accent, it)) }
        if (values.index(KEY_TEXT_SIZE, 0) == 1) add(stringResource(R.string.setup_done_large_text))
    }
}

/** The button with focus at the start, a little larger, as board 14's Resume is. */
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PlasmaButton(text, onClick = onClick, modifier = modifier, textSizePx = 25f, startPadding = 34.px, endPadding = 34.px)
}

/** The soft light behind the welcome and All Set, as behind Who's Watching (board 05). */
private fun Modifier.glow(): Modifier = background(PlasmaColors.Bg).drawBehind {
    val center = Offset(size.width / 2, size.height * 0.46f)
    val radiusY = 700.px.toPx()
    val radiusX = 1200.px.toPx()
    scale(scaleX = radiusX / radiusY, scaleY = 1f, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(0f to PlasmaColors.Glow, 0.7f to PlasmaColors.Bg, center = center, radius = radiusY),
            radius = radiusY,
            center = center,
        )
    }
}

/** The welcome's wordmark: the top bar's, at nearly four times the size. */
private const val WORDMARK_PX = 120
