// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.px

/** A step's page and the row focus starts on (#85). */
private class StepPage(val spec: PageSpec, val start: String)

/** Continue's row, by its id: the last on every step. */
internal const val CONTINUE = "continue"

/**
 * A step of first-time setup (#85) in the Settings pattern (brief 4.19): the focused row's detail
 * on the left, the rows on the right, Continue at the end, "‹ the step before" and "Step 2 of 5"
 * in the header. Focus starts on what the step is for. The first step starts again from its rows
 * when a source comes or goes, so coming back from Add a Source with one lands on Continue.
 */
@Composable
internal fun SetupStep(
    screen: SetupScreen,
    values: SettingsValues,
    actions: SettingsActions,
    facts: SettingsFacts,
    onContinue: () -> Unit,
) {
    val page = when (screen) {
        SetupScreen.Service -> serviceStep(facts, actions, onContinue)
        SetupScreen.HomeScreen -> homeStep(facts, actions, onContinue)
        SetupScreen.Profiles -> profilesStep(facts, actions, onContinue)
        SetupScreen.Look -> lookStep(values, onContinue)
        else -> guideStep(values, facts, actions, onContinue)
    }
    key(if (screen == SetupScreen.Service) facts.sources.map { it.id } else Unit) {
        SettingsPageView(page.spec, values, actions.onOpen, actions.onGo, initialItem = page.start)
    }
}

/** A setup screen's title, as its header, the next step's back link and the screens it opens name it. */
@Composable
fun SetupScreen.title(): String = stringResource(
    when (this) {
        SetupScreen.Welcome -> R.string.setup_welcome
        SetupScreen.Service -> R.string.setup_service
        SetupScreen.HomeScreen -> R.string.home_screen
        SetupScreen.Profiles -> R.string.setup_profiles
        SetupScreen.Look -> R.string.setup_look
        SetupScreen.Guide -> R.string.setup_guide
        SetupScreen.Done -> R.string.setup_done
    },
)

/** "Step 2 of 5", in the clock's place. */
@Composable
private fun SetupScreen.stepLabel(): String = stringResource(R.string.setup_step, step ?: 0, SetupScreen.STEPS)

/** "Next: Home Screen.", or after the last step, that it was the last. */
@Composable
private fun SetupScreen.nextText(): String =
    if (next == SetupScreen.Done) stringResource(R.string.setup_last) else stringResource(R.string.setup_next, next.title())

/**
 * Continue, after a divider at the end of a step: on to the next. Nothing in a step is needed, so
 * it's also how a step is skipped. Its detail says what's next, with [text] and [extra] first.
 */
@Composable
private fun continueSection(
    screen: SetupScreen,
    onContinue: () -> Unit,
    subtitle: String? = null,
    text: String? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
): SettingSection {
    val label = stringResource(R.string.phone_continue)
    val next = screen.nextText()
    return SettingSection(
        label = null,
        divider = true,
        items = listOf(
            SettingItem(
                id = CONTINUE,
                label = label,
                value = null,
                subtitle = subtitle,
                action = SettingAction.Run(onContinue),
                detail = SettingDetail(
                    PlasmaIcons.ArrowRight,
                    label,
                    text = text ?: next,
                    extra = {
                        extra?.invoke(this)
                        if (text != null) DetailNote(next)
                    },
                ),
            ),
        ),
    )
}

/** A step drawn like the pages under Settings (boards 27–34). */
@Composable
private fun compactStep(screen: SetupScreen, sections: List<SettingSection>): PageSpec = PageSpec(
    title = screen.title(),
    back = screen.previous.title(),
    style = PageStyle.Compact,
    metrics = RowMetrics.Page,
    detailTop = 160.px,
    sections = sections,
    step = screen.stepLabel(),
)

// ---- Your TV Service (boards 20 and 21; brief 3.4, A2–A4) --------------------------------------------

/**
 * The sources added so far, as Portals & Playlists lists them, each with what it's still loading,
 * then Add Portal or Playlist (board 21, with its QR code for a phone). With no source, Continue
 * goes on with the sample channels.
 */
@Composable
private fun serviceStep(facts: SettingsFacts, actions: SettingsActions, onContinue: () -> Unit): StepPage {
    val screen = SetupScreen.Service
    val sources = facts.sources
    val loading = sources.filter { it.loading != null }
    val continueRow = if (sources.isEmpty()) {
        continueSection(screen, onContinue, subtitle = stringResource(R.string.setup_sample_channels), text = stringResource(R.string.setup_sample_text))
    } else {
        continueSection(
            screen,
            onContinue,
            text = if (loading.isEmpty()) null else stringResource(R.string.setup_loading_text),
            extra = if (loading.isEmpty()) null else ({ for (source in loading) SourceProgress(source, named = loading.size > 1) }),
        )
    }
    val spec = PageSpec(
        title = screen.title(),
        back = screen.previous.title(),
        style = PageStyle.Large,
        metrics = RowMetrics.Sources,
        detailTop = 178.px,
        sections = listOf(
            SettingSection(stringResource(R.string.section_sources), sources.map { sourceRow(it, actions.onSource, setupSourceDetail(it)) } + addSourceRow()),
            continueRow,
        ),
        step = screen.stepLabel(),
    )
    return StepPage(spec, start = if (sources.isEmpty()) "add" else CONTINUE)
}

/** A source's detail in setup: its kind, how it's doing, and what it has loaded so far. */
@Composable
private fun setupSourceDetail(source: SourceInfo): SettingDetail = SettingDetail(
    icon = PlasmaIcons.ServerLines,
    title = source.name,
    subline = stringResource(
        when (source.type) {
            SourceType.Stalker -> R.string.type_line_stalker
            SourceType.Xtream -> R.string.type_line_xtream
            SourceType.M3U -> R.string.type_line_m3u
        },
    ),
    status = source.health.status(withPing = true),
    extra = { SourceProgress(source) },
)

// ---- Home Screen (board 23; brief 3.4, A5) ----------------------------------------------------------

/** Home Screen setup as a step, focus on the way that suits this TV while Plasma isn't Home yet. */
@Composable
private fun homeStep(facts: SettingsFacts, actions: SettingsActions, onContinue: () -> Unit): StepPage {
    val screen = SetupScreen.HomeScreen
    val spec = homeScreenPage(facts.home, actions.onHome).asStep(screen.previous.title(), screen.stepLabel(), continueSection(screen, onContinue))
    return StepPage(spec, start = recommendedHomeMethod(facts.home)?.let(::homeMethodRow) ?: CONTINUE)
}

// ---- Profiles and Parental PIN (boards 05 and 25; brief 5.11 and 5.12) --------------------------------

/**
 * The profiles there are, each opening its page, and Add Profile, as on Settings › Profiles; then
 * the parental PIN: Set PIN, or Change PIN once there's one, which asks for it first.
 */
@Composable
private fun profilesStep(facts: SettingsFacts, actions: SettingsActions, onContinue: () -> Unit): StepPage {
    val screen = SetupScreen.Profiles
    val profiles = profileRows(facts, actions)
    val note = stringResource(R.string.setup_parental_note)
    val pin = if (facts.pinSet) {
        val label = stringResource(R.string.change_pin)
        SettingItem(
            id = "pin",
            label = label,
            value = null,
            action = SettingAction.Go(SettingsDestination.ChangePin),
            detail = SettingDetail(PlasmaIcons.Lock, label, text = stringResource(R.string.setup_change_pin_text), extra = { DetailNote(note) }),
        )
    } else {
        val label = stringResource(R.string.set_pin)
        SettingItem(
            id = "pin",
            label = label,
            value = null,
            action = SettingAction.Go(SettingsDestination.SetPin),
            detail = SettingDetail(PlasmaIcons.Lock, label, text = stringResource(R.string.pin_text_not_set), extra = { DetailNote(note) }),
        )
    }
    val spec = compactStep(
        screen,
        listOf(
            SettingSection(stringResource(R.string.section_profiles), profiles),
            SettingSection(stringResource(R.string.section_parental_pin), listOf(pin)),
            continueSection(screen, onContinue),
        ),
    )
    return StepPage(spec, start = if (profiles.any { it.id == ADD_PROFILE }) ADD_PROFILE else CONTINUE)
}

// ---- Look (brief 5.16) ------------------------------------------------------------------------------

/**
 * Accent and Text Size, each option a row with the chosen one ticked: moving through them shows
 * each in the preview, OK chooses it (Settings › Appearance's own settings). The preview is drawn
 * with Plasma's own theme for that look.
 */
@Composable
private fun lookStep(values: SettingsValues, onContinue: () -> Unit): StepPage {
    val screen = SetupScreen.Look
    val accents = stringArrayResource(R.array.accent_options).toList()
    val sizes = stringArrayResource(R.array.text_size_options).toList()
    val accent = values.index(KEY_ACCENT, 0).coerceIn(accents.indices)
    val size = values.index(KEY_TEXT_SIZE, 0).coerceIn(sizes.indices)
    val accentLabel = stringResource(R.string.accent)
    val accentText = stringResource(R.string.accent_text)
    val sizeLabel = stringResource(R.string.text_size)
    val sizeText = stringResource(R.string.text_size_text)

    // An option and the look it previews: its own, with the other setting as chosen.
    fun option(id: String, key: String, index: Int, label: String, setting: String, text: String, chosen: Boolean, preview: Pair<Int, Boolean>) = SettingItem(
        id = id,
        label = label,
        value = null,
        chevron = false,
        checked = chosen,
        action = SettingAction.Run { values.choose(key, index) },
        detail = SettingDetail(PlasmaIcons.Contrast, label, subline = setting, text = text, extra = { LookPreview(preview.first, preview.second) }),
    )

    val accentRows = accents.mapIndexed { i, label -> option("accent-$i", KEY_ACCENT, i, label, accentLabel, accentText, i == accent, i to (size == 1)) }
    val sizeRows = sizes.mapIndexed { i, label -> option("text-$i", KEY_TEXT_SIZE, i, label, sizeLabel, sizeText, i == size, accent to (i == 1)) }
    val spec = compactStep(
        screen,
        listOf(
            SettingSection(stringResource(R.string.section_accent), accentRows),
            SettingSection(stringResource(R.string.section_text_size), sizeRows),
            continueSection(screen, onContinue, extra = { LookPreview(accent, size == 1) }),
        ),
    )
    return StepPage(spec, start = "accent-$accent")
}

// ---- Guide and Channel Numbers (boards 29 and 30; brief 5.2 and 5.3) --------------------------------

/**
 * Where each source's guide comes from (a portal's and a login's are their own; a playlist's come
 * from its addresses, which Edit changes), then Channel Numbers, and Duplicate Numbers when some
 * channels had to move.
 */
@Composable
private fun guideStep(values: SettingsValues, facts: SettingsFacts, actions: SettingsActions, onContinue: () -> Unit): StepPage {
    val screen = SetupScreen.Guide
    val guides = if (facts.sources.isEmpty()) {
        val label = stringResource(R.string.guide_sources)
        listOf(
            SettingItem(
                id = "guide-sample",
                label = label,
                value = null,
                chevron = false,
                detail = SettingDetail(PlasmaIcons.GuideTable, label, text = stringResource(R.string.setup_guide_none_text)),
            ),
        )
    } else {
        facts.sources.map { source ->
            val from = stringResource(
                when (source.type) {
                    SourceType.Stalker -> R.string.guide_from_portal
                    SourceType.Xtream -> R.string.guide_from_server
                    SourceType.M3U -> R.string.guide_from_playlist
                },
            )
            // A portal's guide is the portal's alone; a login and a playlist take guide addresses in Edit.
            val editable = source.type != SourceType.Stalker
            SettingItem(
                id = "guide-${source.id}",
                label = source.name,
                value = from,
                chevron = editable,
                action = if (editable) SettingAction.Run { actions.onSource(source.id, SourceAction.Edit) } else SettingAction.None,
                detail = SettingDetail(
                    PlasmaIcons.GuideTable,
                    source.name,
                    subline = from,
                    text = stringResource(
                        when (source.type) {
                            SourceType.Stalker -> R.string.setup_guide_portal_text
                            SourceType.Xtream -> R.string.setup_guide_server_text
                            SourceType.M3U -> R.string.setup_guide_playlist_text
                        },
                        source.name,
                    ),
                ),
            )
        }
    }
    val numbers = values.choice(KEY_CHANNEL_NUMBERS, R.array.channel_numbers_options, 0)
    val numberRows = listOfNotNull(
        choiceRow("numbers", stringResource(R.string.channel_numbers), numbers, PlasmaIcons.Hash, stringResource(R.string.channel_numbers_text)),
        duplicatesRow(facts).takeIf { facts.duplicatesFixed > 0 },
    )
    val spec = compactStep(
        screen,
        listOf(
            SettingSection(stringResource(R.string.section_feeds), guides),
            SettingSection(stringResource(R.string.section_numbers), numberRows),
            continueSection(screen, onContinue),
        ),
    )
    return StepPage(spec, start = guides.first().id)
}
