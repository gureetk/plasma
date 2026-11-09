// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcons

/** Reset Settings' steps (#159). */
internal enum class ResetStage {
    /** What goes back to its default and what stays, Cancel first. */
    Asking,

    /** Back Up Settings First: where to. */
    BackingUpWhere,
}

/**
 * Settings › About › Reset Settings (#159): what goes back to its default and what stays, with
 * Cancel focused, Back Up Settings First, and Reset Settings, which hands over to the app
 * ([onReset]: the parental PIN when there's one, then the reset and Plasma starting again).
 * Backing up first runs Back Up Settings in this sheet, then comes back to the question.
 */
@Composable
internal fun ResetSettingsSheet(backups: BackupActions, back: String, onReset: () -> Unit, onClose: () -> Unit) {
    var stage by remember { mutableStateOf(ResetStage.Asking) }
    var backUpTo by remember { mutableStateOf<BackupTo?>(null) }
    val to = backUpTo
    if (to != null) {
        // Saved, or not after all: the question again.
        key(to) {
            BackupSheet(backups, to, back, onClose = {
                backUpTo = null
                stage = ResetStage.Asking
            })
        }
        return
    }
    val title = stringResource(R.string.reset_settings)
    when (stage) {
        ResetStage.Asking -> key(stage) {
            SignInSheet(
                title = title,
                text = stringResource(R.string.reset_settings_sheet),
                onClose = onClose,
                icon = PlasmaIcons.Caution,
                options = listOf(
                    SheetOption(stringResource(R.string.cancel), onClick = onClose),
                    SheetOption(stringResource(R.string.reset_backup_first), onClick = { stage = ResetStage.BackingUpWhere }, icon = PlasmaIcons.Drive),
                    SheetOption(title, onClick = {
                        onClose()
                        onReset()
                    }),
                ),
            )
        }
        ResetStage.BackingUpWhere -> key(stage) {
            SignInSheet(
                title = stringResource(R.string.backup),
                text = stringResource(R.string.backup_menu_text),
                onClose = onClose,
                icon = PlasmaIcons.Drive,
                onBack = { stage = ResetStage.Asking },
                options = listOf(
                    SheetOption(stringResource(R.string.backup_to_usb), onClick = { backUpTo = BackupTo.UsbDrive }),
                    SheetOption(stringResource(R.string.backup_on_tv), onClick = { backUpTo = BackupTo.ThisTv }),
                    SheetOption(stringResource(R.string.backup_to_phone), onClick = { backUpTo = BackupTo.Phone }),
                ),
            )
        }
    }
}
