// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.R
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * One of the sources a channel plays from (Switch Source, brief 4.10 and 5.1). [playing] marks
 * the one it plays from; [remembered] is when Remember What Works per Channel learned it's the one
 * that works, null for the others.
 */
@Immutable
data class SourceChoice(val key: String, val name: String, val playing: Boolean, val remembered: Instant? = null)

/**
 * Switch Source (brief 4.10 and 4.35), in Player Options and a channel's options: the channel's
 * sources, the one it plays from ticked. The one Remember What Works per Channel keeps comes first,
 * with "Works best" and when it was learned, and Forget What Works ends the list after a divider.
 */
@Composable
fun SwitchSourceSheet(
    channel: String,
    sources: List<SourceChoice>,
    onPick: (SourceChoice) -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now by rememberNow()
    val worksBest = stringResource(R.string.works_best)
    val options = sources.sortedByDescending { it.remembered != null }.map { source ->
        SheetOption(
            source.name,
            onClick = { onPick(source) },
            icon = PlasmaIcons.SwitchSource,
            checked = source.playing,
            tag = if (source.remembered != null) worksBest else null,
            note = source.remembered?.let { rememberedAgo(it, now) },
        )
    }
    val forget = if (sources.any { it.remembered != null }) {
        listOf(SheetOption.Divider, SheetOption(stringResource(R.string.forget_what_works), onClick = onForget, icon = PlasmaIcons.History))
    } else {
        emptyList()
    }
    OptionsSheet(
        title = stringResource(R.string.switch_source),
        subtitle = channel,
        onDismiss = onDismiss,
        options = options + forget,
    )
}

/** "Remembered 3 days ago": when Remember What Works per Channel learned a source (brief 4.35). */
@Composable
private fun rememberedAgo(at: Instant, now: ZonedDateTime): String {
    val days = ChronoUnit.DAYS.between(at.atZone(now.zone).toLocalDate(), now.toLocalDate()).toInt()
    return when {
        days <= 0 -> stringResource(R.string.remembered_today)
        days == 1 -> stringResource(R.string.remembered_yesterday)
        else -> pluralStringResource(R.plurals.remembered_days_ago, days, days)
    }
}
