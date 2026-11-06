// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.PlasmaType
import ca.gureet.plasma.core.design.component.FlowingWordmark
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.TurningRing
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import kotlinx.coroutines.flow.StateFlow

/** How far adding a source has got (brief 4.35's sync progress, #158), in its order. */
enum class SyncStage { SigningIn, Channels, Guide, Library, Done, Failed }

/**
 * Where adding a source has got (brief 4.35's sync progress, the owner's #158): signing in, its
 * channels, counted as they're read where the source allows, then its guide, then its movies and
 * series (a playlist has none).
 */
@Immutable
data class SourceSync(
    val name: String,
    val type: SourceType,
    val stage: SyncStage = SyncStage.SigningIn,
    /** The channels read so far, or all of them once they're in. */
    val channels: Int? = null,
    /** The guide's programs read so far. */
    val programs: Int? = null,
    val movies: Int? = null,
    val series: Int? = null,
    val guideFailed: Boolean = false,
    val libraryFailed: Boolean = false,
    /** Once the channels are in: the account, as Connect reports it. */
    val account: ConnectOutcome.Connected? = null,
    /** Why it stopped, with [SyncStage.Failed]. */
    val problem: ConnectProblem? = null,
) {
    /** The channels are still to come: Back stops it, and Load in Background lets it carry on. */
    val beforeChannels: Boolean get() = stage == SyncStage.SigningIn || stage == SyncStage.Channels
}

/** A source being added (#158), which carries on in the app when Add a Source closes. */
interface SourceAdding {
    val progress: StateFlow<SourceSync>

    /** Load in Background: it carries on after Add a Source closes, and a notice says how it went. */
    fun inBackground()

    /** Back before the channels are in: it stops, and the new source isn't kept. */
    fun stop()
}

/**
 * Adding a source (brief 4.35's sync progress, the owner's #158): the wave wordmark flowing, as on
 * Plasma's launch screen, then what's being added and a checklist of how far it has got. Until
 * the channels are in, Load in Background lets Plasma carry on while it's used, with what that
 * means under it, and Back stops. Then Done, while the guide, movies and series carry on loading;
 * Back is Done too.
 */
@Composable
internal fun SourceSyncScreen(sync: SourceSync, onBackground: () -> Unit, onDone: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val button = remember { FocusRequester() }
    val before = sync.beforeChannels
    LaunchedEffect(before) {
        withFrameNanos { }
        button.requestFocus()
    }
    BackHandler { if (before) onStop() else onDone() }
    Box(
        modifier
            .fillMaxSize()
            .background(PlasmaColors.Bg)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
    ) {
        Column(Modifier.fillMaxSize().padding(top = 150.px), horizontalAlignment = Alignment.CenterHorizontally) {
            FlowingWordmark(WORD_PX)
            Spacer(Modifier.height(64.px))
            val account = sync.account
            PlasmaText(
                if (account == null) stringResource(R.string.sync_adding, sync.name) else stringResource(R.string.connected_title, account.name),
                PlasmaType.PageTitle,
            )
            if (account != null) {
                PlasmaText(accountSummary(account), PlasmaType.Caption, Modifier.padding(top = 10.px), color = PlasmaColors.Text2)
            }
            Column(Modifier.padding(top = 40.px).width(LIST_PX.px), verticalArrangement = Arrangement.spacedBy(18.px)) {
                for (line in syncLines(sync)) SyncRow(line)
            }
            Spacer(Modifier.height(52.px))
            PlasmaButton(
                stringResource(if (before) R.string.sync_in_background else R.string.done),
                onClick = if (before) onBackground else onDone,
                modifier = Modifier.focusRequester(button),
                height = 62.px,
                startPadding = 34.px,
                endPadding = 34.px,
                unfocusedColor = PlasmaColors.Selected,
            )
            // The warning sits beside its words, the two centred together.
            Row(Modifier.padding(top = 24.px).width(NOTE_PX.px), horizontalArrangement = Arrangement.Center) {
                if (before) PlasmaIcon(PlasmaIcons.Caution, 24.px, Modifier.padding(end = 14.px, top = 3.px), tint = PlasmaColors.WarnText, stroke = 2.2f)
                PlasmaText(
                    when {
                        before -> stringResource(R.string.sync_background_note, sync.name)
                        sync.stage == SyncStage.Done -> stringResource(R.string.sync_done_note)
                        sync.type == SourceType.M3U -> stringResource(R.string.sync_guide_note)
                        else -> stringResource(R.string.sync_rest_note)
                    },
                    plasmaStyle(21f, 500, lineHeight = 1.35f),
                    Modifier.weight(1f, fill = false),
                    color = PlasmaColors.Text3,
                    maxLines = 2,
                    align = if (before) TextAlign.Start else TextAlign.Center,
                )
            }
        }
    }
}

/** A line's mark: done, being loaded, still to come, or didn't load. */
private enum class SyncMark { Done, Working, Next, Failed }

@Immutable
private class SyncLine(val mark: SyncMark, val text: String)

/**
 * The checklist (brief 4.35): signing in (an account or a portal), the channels, the guide, then
 * movies and series (a playlist has none), each with what has been read so far.
 */
@Composable
private fun syncLines(sync: SourceSync): List<SyncLine> {
    val stage = sync.stage
    val library = sync.type != SourceType.M3U
    val lines = ArrayList<SyncLine>(5)
    if (library) {
        lines += if (stage == SyncStage.SigningIn) {
            SyncLine(SyncMark.Working, stringResource(R.string.sync_signing_in))
        } else {
            SyncLine(SyncMark.Done, stringResource(R.string.sync_signed_in))
        }
    }
    val channels = sync.channels
    lines += when {
        stage == SyncStage.SigningIn && library -> SyncLine(SyncMark.Next, stringResource(R.string.sync_channels))
        sync.beforeChannels -> SyncLine(
            SyncMark.Working,
            if (channels != null) stringResource(R.string.sync_channels_so_far, Formats.count(channels)) else stringResource(R.string.sync_channels_loading),
        )
        else -> SyncLine(SyncMark.Done, pluralStringResource(R.plurals.channel_count, channels ?: 0, Formats.count(channels ?: 0)))
    }
    val programs = sync.programs
    lines += when {
        sync.beforeChannels -> SyncLine(SyncMark.Next, stringResource(R.string.sync_guide))
        stage == SyncStage.Guide -> SyncLine(
            SyncMark.Working,
            if (programs != null && programs > 0) stringResource(R.string.sync_guide_so_far, Formats.count(programs)) else stringResource(R.string.setup_guide_loading),
        )
        sync.guideFailed -> SyncLine(SyncMark.Failed, stringResource(R.string.setup_guide_failed))
        else -> SyncLine(SyncMark.Done, if (programs != null && programs > 0) pluralStringResource(R.plurals.sync_guide_programs, programs, Formats.count(programs)) else stringResource(R.string.setup_guide_loaded))
    }
    if (library) {
        val movies = sync.movies ?: 0
        val series = sync.series ?: 0
        when (stage) {
            SyncStage.Library -> {
                lines += SyncLine(SyncMark.Working, if (movies > 0) stringResource(R.string.sync_movies_so_far, Formats.count(movies)) else stringResource(R.string.sync_movies))
                // Series come once they start to arrive (an Xtream account's after its movies).
                lines += SyncLine(if (series > 0) SyncMark.Working else SyncMark.Next, if (series > 0) stringResource(R.string.sync_series_so_far, Formats.count(series)) else stringResource(R.string.sync_series))
            }
            SyncStage.Done -> if (sync.libraryFailed) {
                lines += SyncLine(SyncMark.Failed, stringResource(R.string.sync_library_failed))
            } else {
                lines += SyncLine(SyncMark.Done, pluralStringResource(R.plurals.sync_movie_count, movies, Formats.count(movies)))
                lines += SyncLine(SyncMark.Done, pluralStringResource(R.plurals.sync_series_count, series, Formats.count(series)))
            }
            else -> {
                lines += SyncLine(SyncMark.Next, stringResource(R.string.sync_movies))
                lines += SyncLine(SyncMark.Next, stringResource(R.string.sync_series))
            }
        }
    }
    return lines
}

@Composable
private fun SyncRow(line: SyncLine) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.px)) {
        Box(Modifier.size(28.px), contentAlignment = Alignment.Center) {
            when (line.mark) {
                SyncMark.Done -> PlasmaIcon(PlasmaIcons.Check, 28.px, tint = PlasmaColors.Ok, stroke = 2.4f)
                SyncMark.Working -> TurningRing(24f)
                SyncMark.Next -> Box(Modifier.size(8.px).background(PlasmaColors.Text4, CircleShape))
                SyncMark.Failed -> PlasmaIcon(PlasmaIcons.Caution, 26.px, tint = PlasmaColors.Warn, stroke = 2.2f)
            }
        }
        PlasmaText(
            line.text,
            plasmaStyle(24f, 500, tabular = true),
            color = when (line.mark) {
                SyncMark.Next -> PlasmaColors.Text3
                SyncMark.Working -> PlasmaColors.Text2
                else -> PlasmaColors.TextSoft3
            },
        )
    }
}

/** What Connect found on the account, as its sheet said: "Active until 1 March 2027 · 2 connections · 1,284 channels". */
@Composable
internal fun accountSummary(account: ConnectOutcome.Connected): String {
    val channels = pluralStringResource(R.plurals.summary_channels, account.channels, Formats.count(account.channels))
    if (!account.hasAccount) return channels
    return listOfNotNull(
        account.activeUntil?.let { stringResource(R.string.summary_active_until, Formats.fullDate(it)) } ?: stringResource(R.string.summary_no_expiry),
        if (account.knowsConnections) {
            account.connections?.let { pluralStringResource(R.plurals.summary_connections, it, it) } ?: stringResource(R.string.summary_no_connection_limit)
        } else {
            null
        },
        channels,
    ).joinToString(" · ")
}

/** The wordmark's type, and the widths of the checklist and of the note under the button, in board pixels. */
private const val WORD_PX = 96f
private const val LIST_PX = 560
private const val NOTE_PX = 1000
