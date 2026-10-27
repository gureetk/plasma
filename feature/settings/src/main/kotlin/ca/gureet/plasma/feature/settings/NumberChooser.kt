// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.component.NumberChooser as ChannelNumberChooser

/**
 * Choose Another Number (brief 4.30): the shared number pad, over Duplicate Numbers, for the
 * channel that numbering moved. Use takes a free number; Back cancels.
 */
@Composable
internal fun NumberChooser(
    conflict: NumberConflict,
    ownerOf: (Int) -> String?,
    onUse: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ChannelNumberChooser(
        back = stringResource(R.string.duplicate_numbers),
        title = stringResource(R.string.choose_number),
        subtitle = stringResource(R.string.choose_number_for, conflict.mover.name, conflict.mover.source, conflict.newNumber),
        current = conflict.newNumber,
        ownerOf = ownerOf,
        onUse = onUse,
        onDismiss = onDismiss,
    )
}
