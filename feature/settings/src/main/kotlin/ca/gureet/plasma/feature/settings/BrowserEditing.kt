// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ca.gureet.plasma.core.design.R as DesignR

/**
 * Edit in a Browser: the TV's page for a computer's or a phone's browser, where favorites,
 * categories, hidden channels, numbering and locks are organized with a keyboard and a mouse, and
 * each change shows on the TV as it's made.
 */
interface BrowserEditor {
    /** The address and code a browser comes in with, while this is collected: the sheet shows them. */
    fun sheet(): Flow<PhonePairing>

    /** The browsers editing now, by the names they gave ("Chrome · Mac"). */
    val browsers: Flow<List<String>>

    /** End Editing: every browser's editing ends. */
    fun endAll()

    /** Previews and the sample world: the board's code, and [editing] editing. */
    class Sample(editing: List<String> = emptyList()) : BrowserEditor {
        override fun sheet(): Flow<PhonePairing> = flowOf(PhonePairing.Sample)

        override val browsers: Flow<List<String>> = flowOf(editing)

        override fun endAll() = Unit
    }
}

/** Portals & Playlists' Edit in a Browser, after Add Portal or Playlist: how many browsers are editing, if any. */
@Composable
internal fun editInBrowserRow(editor: BrowserEditor, editing: List<String>): SettingItem {
    val title = stringResource(R.string.edit_in_browser)
    return SettingItem(
        id = "edit-in-browser",
        label = title,
        value = if (editing.isEmpty()) null else pluralStringResource(R.plurals.browsers_editing, editing.size, editing.size),
        chevron = false,
        leadingIcon = PlasmaIcons.Screen,
        action = SettingAction.Sheet { close -> BrowserEditorSheet(editor, close) },
        detail = SettingDetail(PlasmaIcons.Screen, title, text = stringResource(R.string.edit_in_browser_text)),
    )
}

/**
 * The code a browser comes in with, as Add a Source's phone code (5.15): a QR code for a phone,
 * the address and code for a computer. While it shows, the TV's page answers; it goes on
 * answering the browsers that came in until they end, the TV ends them with End Editing, or 15
 * minutes pass with nothing from them.
 */
@Composable
internal fun BrowserEditorSheet(editor: BrowserEditor, onClose: () -> Unit) {
    val pairing by remember(editor) { editor.sheet() }.collectAsState(PhonePairing.Starting)
    val editing by editor.browsers.collectAsState(emptyList())
    val offline = pairing == PhonePairing.Unavailable
    SignInSheet(
        title = stringResource(R.string.edit_in_browser),
        text = stringResource(if (offline) R.string.edit_in_browser_offline else R.string.edit_in_browser_sheet_text),
        onClose = onClose,
        // Close first: OK out of habit doesn't end anyone's editing.
        options = listOfNotNull(
            SheetOption(stringResource(DesignR.string.key_close), onClick = onClose),
            if (editing.isEmpty()) null else SheetOption(stringResource(R.string.end_editing), onClick = editor::endAll, note = editing.joinToString(", ")),
        ),
        icon = PlasmaIcons.Screen,
        // The code in the middle of the sheet, under its words.
        body = if (offline) null else { { Column(Modifier.fillMaxWidth()) { PhoneCode(pairing) } } },
    )
}
