// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.PlasmaButton
import ca.gureet.plasma.core.design.component.SecondaryHeader
import ca.gureet.plasma.core.design.component.SheetHost
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.focusRestoringGroup
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.fixtures.fx
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.QrMatrix
import kotlinx.coroutines.launch
import ca.gureet.plasma.core.design.R as DesignR

/** What's typed in so far on Add a Source (brief 4.21), by field, and which field is being typed in. */
@Immutable
data class AddSourceState(
    val type: SourceType,
    val values: Map<String, String>,
    val editing: String,
    val primary: Boolean,
    /** The name of the source Edit opened the form for (brief 4.20); null when adding one. */
    val editOf: String? = null,
    /** The MAC address made for this TV (brief 7.1), which a portal form starts with. */
    val deviceMac: String? = null,
) {
    companion object {
        /** An empty form for [type], starting on the name; a portal's MAC address is this TV's. */
        fun blank(type: SourceType, primary: Boolean, deviceMac: String? = null) =
            AddSourceState(type, deviceMac?.let { mapOf(FIELD_MAC to it) }.orEmpty(), FIELD_NAME, primary, deviceMac = deviceMac)

        /**
         * Edit on a source: its details filled in, starting on its address. The password isn't
         * shown; it stays as it is unless a new one is typed.
         */
        fun edit(type: SourceType, name: String, address: String, username: String = "", guide: String = "", mac: String = "", deviceMac: String? = null) =
            AddSourceState(
                type = type,
                values = mapOf(FIELD_NAME to name, type.addressField() to address, FIELD_USERNAME to username, FIELD_GUIDE to guide, FIELD_MAC to mac)
                    .filter { (field, value) -> value.isNotEmpty() && field in type.fields() },
                editing = type.addressField(),
                primary = false,
                editOf = name,
                deviceMac = deviceMac,
            )
    }
}

/** Board 21: a Stalker portal half filled in, the portal's address being typed. */
object AddSourceSamples {
    val state = AddSourceState(
        type = SourceType.Stalker,
        values = mapOf(
            FIELD_NAME to "Home Portal",
            FIELD_PORTAL to "http://portal.example.net/c/",
            FIELD_MAC to "00:1A:79:4C:2E:91",
        ),
        editing = FIELD_PORTAL,
        primary = true,
        deviceMac = "00:1A:79:4C:2E:91",
    )
}

/** A phone filling in Add a Source (brief 5.15), as the card on the right shows it. */
@Immutable
sealed interface PhonePairing {
    /** Previews and the sample world: the board's QR code, address and code. */
    data object Sample : PhonePairing

    /** The TV's local server is starting. */
    data object Starting : PhonePairing

    /** The TV isn't on a network a phone can reach. */
    data object Unavailable : PhonePairing

    /** A phone can scan [qr], or open [address] ("192.168.1.42:8484") and type [code] ("7KQ4MZ"). */
    data class Ready(val address: String, val code: String, val qr: QrMatrix) : PhonePairing
}

/**
 * A source sent from a phone (brief 5.15): its kind, and its fields by the form's names ("name",
 * "portal", "mac", "server", "username", "password", "playlist", "guide"). [id] tells one from the
 * next.
 */
@Immutable
data class SentFromPhone(val id: Long, val type: SourceType, val values: Map<String, String>)

class AddSourceActions(
    /** Connected: back to Portals & Playlists. */
    val onDone: () -> Unit = {},
    /** The kind of source the form is on, which a phone's form starts on too (brief 5.15). */
    val onType: (SourceType) -> Unit = {},
    /**
     * Checks and saves what's typed in, and loads the channels (brief 6.4). Without it, as in
     * previews and tests, Connect reports the sample portal's account.
     */
    val onConnect: (suspend (AddSourceForm) -> ConnectOutcome)? = null,
)

/** What Connect sends: the kind of source and what's typed in its fields. */
data class AddSourceForm(
    val type: SourceType,
    val name: String,
    /** The portal, the server or the playlist's address. */
    val server: String,
    val username: String,
    val password: String,
    val primary: Boolean,
    /** A playlist's guide addresses, if any were typed in. */
    val guide: String = "",
    /** A portal's MAC address. */
    val mac: String = "",
)

/** How Connect went. */
sealed interface ConnectOutcome {
    data class Connected(
        val name: String,
        /** Null when the account doesn't expire. */
        val activeUntil: java.time.LocalDate?,
        /** Null when the account has no connection limit. */
        val connections: Int?,
        val channels: Int,
        /** A playlist has no account to report, so only its channels show. */
        val hasAccount: Boolean = true,
        /** A portal doesn't say how many connections the account has. */
        val knowsConnections: Boolean = hasAccount,
    ) : ConnectOutcome

    data class Failed(val problem: ConnectProblem) : ConnectOutcome
}

/** Why Connect failed, as Add a Source explains it. */
enum class ConnectProblem(@StringRes internal val text: Int) {
    CantReach(R.string.connect_cant_reach),
    WrongLogin(R.string.connect_wrong_login),
    NotActive(R.string.connect_not_active),
    NotXtream(R.string.connect_not_xtream),
    NotPlaylist(R.string.connect_not_playlist),
    NoChannels(R.string.connect_no_channels),
    Refused(R.string.connect_refused),
    NotStalker(R.string.connect_not_stalker),
    MacRefused(R.string.connect_mac_refused),
    Blocked(R.string.connect_blocked),
    NotMac(R.string.connect_not_mac),
}

/** The DEL control character, which a keyboard's Delete key can carry. */
private const val DELETE_CHAR = 0x7F

internal const val FIELD_NAME = "name"
internal const val FIELD_PORTAL = "portal"
internal const val FIELD_MAC = "mac"
internal const val FIELD_SERVER = "server"
internal const val FIELD_USERNAME = "username"
internal const val FIELD_PASSWORD = "password"
internal const val FIELD_PLAYLIST = "playlist"
internal const val FIELD_GUIDE = "guide"

/** The fields each kind of source asks for, in order (brief 4.21); the rest are under Advanced. */
private fun SourceType.fields(): List<String> = when (this) {
    SourceType.Stalker -> listOf(FIELD_NAME, FIELD_PORTAL, FIELD_MAC)
    SourceType.Xtream -> listOf(FIELD_NAME, FIELD_SERVER, FIELD_USERNAME, FIELD_PASSWORD)
    SourceType.M3U -> listOf(FIELD_NAME, FIELD_PLAYLIST, FIELD_GUIDE)
}

/** The field that holds the address Connect checks first. */
private fun SourceType.addressField(): String = when (this) {
    SourceType.Stalker -> FIELD_PORTAL
    SourceType.Xtream -> FIELD_SERVER
    SourceType.M3U -> FIELD_PLAYLIST
}

private sealed interface ConnectResult {
    data object Connected : ConnectResult

    /** Connected for real, with the account and channel count (M1). */
    data class Live(val outcome: ConnectOutcome.Connected) : ConnectResult

    data class Failed(val reason: Int) : ConnectResult
}

/**
 * Add a Source (board 21): the kind of source, its fields, and an on-screen keyboard that types
 * into the field being edited (the one with a caret). The remote's number keys and a keyboard
 * type too. The card on the right lets a phone fill the form in. Connect checks what's typed in;
 * until sources arrive (M1) it reports the sample portal's account.
 */
@Composable
fun AddSourceScreen(
    state: AddSourceState,
    actions: AddSourceActions,
    modifier: Modifier = Modifier,
    facts: SettingsFacts = SettingsSamples.facts,
    phone: PhonePairing = PhonePairing.Sample,
    sent: SentFromPhone? = null,
) {
    var type by rememberSaveable { mutableStateOf(state.type) }
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(state.values) } }
    var editing by rememberSaveable { mutableStateOf(state.editing) }
    var primary by rememberSaveable { mutableStateOf(state.primary) }
    var result by remember { mutableStateOf<ConnectResult?>(null) }
    var connecting by remember { mutableStateOf(false) }
    // Edit keeps the source's kind and place; Set as Primary is its own action (brief 4.20).
    val edit = state.editOf != null
    val scope = rememberCoroutineScope()

    val fieldFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun focusOf(field: String) = fieldFocus.getOrPut(field) { FocusRequester() }
    val keyboard = remember { FocusRequester() }
    val connect = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusOf(editing).requestFocus()
    }

    // A phone's form starts on the same kind of source (brief 5.15).
    LaunchedEffect(type) { actions.onType(type) }

    // What a phone sent fills the form in, and Connect is next (brief 3, flow J). Edit keeps its
    // kind; a portal the phone left without a MAC address keeps this TV's.
    var received by remember { mutableStateOf(false) }
    LaunchedEffect(sent?.id) {
        val from = sent ?: return@LaunchedEffect
        if (edit && from.type != type) return@LaunchedEffect
        type = from.type
        val filled = from.type.fields().mapNotNull { field ->
            val value = from.values[field]?.takeIf { it.isNotEmpty() } ?: state.deviceMac?.takeIf { field == FIELD_MAC }
            value?.let { field to it }
        }
        values.clear()
        values.putAll(filled)
        editing = from.type.fields().last()
        received = true
        withFrameNanos { }
        connect.requestFocus()
    }

    fun type(text: String) {
        values[editing] = values[editing].orEmpty() + text
    }

    fun delete() {
        values[editing] = values[editing].orEmpty().dropLast(1)
    }

    fun next() {
        val fields = type.fields()
        val i = fields.indexOf(editing)
        if (i in 0 until fields.lastIndex) editing = fields[i + 1] else connect.requestFocus()
    }

    fun connect() {
        if (connecting) return
        val reason = when {
            values[FIELD_NAME].isNullOrBlank() -> R.string.connect_needs_name
            values[type.addressField()].isNullOrBlank() -> R.string.connect_cant_reach
            type == SourceType.Xtream && values[FIELD_USERNAME].isNullOrBlank() -> R.string.connect_wrong_login
            type == SourceType.Stalker && values[FIELD_MAC].isNullOrBlank() -> R.string.connect_not_mac
            else -> null
        }
        val onConnect = actions.onConnect
        if (reason != null || onConnect == null) {
            result = if (reason == null) ConnectResult.Connected else ConnectResult.Failed(reason)
            return
        }
        val form = AddSourceForm(
            type = type,
            name = values[FIELD_NAME].orEmpty().trim(),
            server = values[type.addressField()].orEmpty().trim(),
            username = values[FIELD_USERNAME].orEmpty(),
            password = values[FIELD_PASSWORD].orEmpty(),
            primary = primary,
            guide = if (type == SourceType.M3U) values[FIELD_GUIDE].orEmpty().trim() else "",
            mac = if (type == SourceType.Stalker) values[FIELD_MAC].orEmpty().trim() else "",
        )
        connecting = true
        scope.launch {
            val outcome = onConnect(form)
            connecting = false
            result = when (outcome) {
                is ConnectOutcome.Connected -> ConnectResult.Live(outcome)
                is ConnectOutcome.Failed -> ConnectResult.Failed(outcome.problem.text)
            }
        }
    }

    // The remote's number keys, and a keyboard plugged into the TV, type into the field too.
    fun onKey(event: KeyEvent): Boolean {
        if (result != null || event.type != KeyEventType.KeyDown) return false
        if (event.key == Key.Backspace || event.key == Key.Delete) {
            delete()
            return true
        }
        val code = event.utf16CodePoint
        if (code < ' '.code || code == DELETE_CHAR) return false
        type(code.toChar().toString())
        return true
    }

    Box(modifier.fillMaxSize().onPreviewKeyEvent(::onKey)) {
        SecondaryHeader(
            stringResource(R.string.sources),
            state.editOf?.let { stringResource(R.string.edit_source_title, it) } ?: stringResource(R.string.add_a_source),
        )

        Column(Modifier.offset(96.px, 148.px).width(1060.px)) {
            if (!edit) {
                TypeTabs(type) { picked ->
                    if (picked != type) {
                        type = picked
                        if (editing !in picked.fields()) editing = picked.addressField()
                    }
                }
            }
            PlasmaText(
                stringResource(
                    when (type) {
                        SourceType.Stalker -> R.string.help_stalker
                        SourceType.Xtream -> R.string.help_xtream
                        SourceType.M3U -> R.string.help_m3u
                    },
                ),
                plasmaStyle(21f, 400, lineHeight = 1.35f),
                Modifier.padding(start = 4.px, end = 4.px, top = 16.px),
                color = PlasmaColors.Text2,
                maxLines = 2,
            )

            Column(Modifier.padding(top = 20.px), verticalArrangement = Arrangement.spacedBy(10.px)) {
                @Composable
                fun field(id: String, label: Int, modifier: Modifier = Modifier, hint: String? = null, secret: Boolean = false, trackingPx: Float = 0f) {
                    SourceField(
                        label = stringResource(label),
                        value = values[id].orEmpty(),
                        editing = editing == id,
                        onFocused = { editing = id },
                        onClick = { keyboard.requestFocus() },
                        modifier = modifier.focusRequester(focusOf(id)),
                        hint = hint,
                        secret = secret,
                        trackingPx = trackingPx,
                    )
                }
                field(FIELD_NAME, R.string.field_name)
                when (type) {
                    SourceType.Stalker -> {
                        field(FIELD_PORTAL, R.string.field_portal)
                        // A MAC address is set a little apart, as on the board.
                        // The hint says when it's the MAC address made for this TV.
                        field(
                            FIELD_MAC, R.string.field_mac, trackingPx = 0.3f,
                            hint = if (state.deviceMac != null && values[FIELD_MAC] == state.deviceMac) stringResource(R.string.mac_hint) else null,
                        )
                    }
                    SourceType.Xtream -> {
                        field(FIELD_SERVER, R.string.field_server)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.px)) {
                            field(FIELD_USERNAME, R.string.field_username, Modifier.weight(1f))
                            field(
                                FIELD_PASSWORD, R.string.field_password, Modifier.weight(1f), secret = true,
                                hint = if (edit && values[FIELD_PASSWORD].isNullOrEmpty()) stringResource(R.string.password_keep_hint) else null,
                            )
                        }
                    }
                    SourceType.M3U -> {
                        field(FIELD_PLAYLIST, R.string.field_playlist)
                        field(FIELD_GUIDE, R.string.field_guide, hint = stringResource(R.string.guide_hint))
                    }
                }
                Row(Modifier.focusRestoringGroup(), horizontalArrangement = Arrangement.spacedBy(10.px)) {
                    AdvancedRow(
                        stringResource(
                            when (type) {
                                SourceType.Stalker -> R.string.advanced_stalker
                                SourceType.Xtream -> R.string.advanced_xtream
                                SourceType.M3U -> R.string.advanced_m3u
                            },
                        ),
                    )
                    if (!edit) PrimaryRow(primary) { primary = !primary }
                }
            }

            SourceKeyboard(
                onCharacters = ::type,
                onDelete = ::delete,
                onNext = ::next,
                first = keyboard,
                modifier = Modifier.padding(top = 26.px),
            )

            Row(Modifier.padding(top = 26.px), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.px)) {
                PlasmaButton(
                    stringResource(
                        when {
                            edit && connecting -> R.string.saving
                            edit -> R.string.save
                            connecting -> R.string.connecting
                            else -> R.string.connect
                        },
                    ),
                    onClick = ::connect,
                    modifier = Modifier.focusRequester(connect),
                    height = 58.px,
                    startPadding = 30.px,
                    endPadding = 30.px,
                    pivot = TransformOrigin(0f, 0.5f),
                    unfocusedColor = PlasmaColors.Selected,
                )
                PlasmaText(
                    stringResource(
                        when (type) {
                            SourceType.Stalker -> R.string.connect_note_stalker
                            SourceType.Xtream -> if (edit) R.string.save_note_xtream else R.string.connect_note_xtream
                            SourceType.M3U -> if (edit) R.string.save_note_m3u else R.string.connect_note_m3u
                        },
                    ),
                    plasmaStyle(20f, 500),
                    color = PlasmaColors.Text3,
                )
            }
        }

        PhoneCard(
            title = stringResource(R.string.easier_on_phone),
            text = stringResource(if (phone == PhonePairing.Unavailable) R.string.phone_unavailable else R.string.easier_on_phone_text),
            qr = when (phone) {
                PhonePairing.Sample -> QrSource.Fixture(fx("pair_qr_local"))
                PhonePairing.Starting -> QrSource.Blank
                PhonePairing.Unavailable -> QrSource.None
                is PhonePairing.Ready -> QrSource.Code(phone.qr)
            },
            modifier = Modifier.offset(1236.px, 148.px),
            bottomPadding = 40.px,
        ) {
            val address = when (phone) {
                PhonePairing.Sample -> "${facts.address}:${facts.phonePort}"
                is PhonePairing.Ready -> phone.address
                else -> null
            }
            val code = when (phone) {
                PhonePairing.Sample -> facts.phoneCode
                // "7KQ 4MZ": two halves are easier to read off.
                is PhonePairing.Ready -> phone.code.chunked(3).joinToString(" ")
                else -> null
            }
            if (address != null && code != null) {
                AddressLine(stringResource(R.string.open_and_enter), address, Modifier.padding(top = 26.px))
                PlasmaText(code, plasmaStyle(44f, 600, trackingPx = 6f, tabular = true), Modifier.padding(top = 10.px))
            }
            if (received) {
                Box(Modifier.padding(top = 18.px)) { StatusDotLine(stringResource(R.string.received_from_phone), Tone.Ok, sizePx = 19f) }
            } else if (phone != PhonePairing.Unavailable) {
                PlasmaText(
                    stringResource(R.string.add_code_note),
                    plasmaStyle(19f, 500),
                    Modifier.padding(top = 18.px),
                    color = PlasmaColors.Text3,
                    align = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }

        SheetHost(visible = result != null, returnFocus = connect) {
            when (val shown = result) {
                ConnectResult.Connected -> {
                    val source = facts.sources.first()
                    OptionsSheet(
                        title = stringResource(R.string.connected_title, values[FIELD_NAME].orEmpty()),
                        subtitle = stringResource(
                            R.string.connected_summary,
                            source.activeUntil?.let(Formats::fullDate).orEmpty(),
                            source.connectionLimit ?: 1,
                            Formats.count(source.channels),
                            Formats.count(source.movies ?: 0),
                            Formats.count(source.series ?: 0),
                        ),
                        onDismiss = { result = null },
                        options = listOf(SheetOption(stringResource(R.string.done), onClick = actions.onDone)),
                    )
                }
                is ConnectResult.Live -> {
                    val account = shown.outcome
                    val channels = pluralStringResource(R.plurals.summary_channels, account.channels, Formats.count(account.channels))
                    val summary = if (!account.hasAccount) {
                        channels
                    } else {
                        listOfNotNull(
                            account.activeUntil?.let { stringResource(R.string.summary_active_until, Formats.fullDate(it)) }
                                ?: stringResource(R.string.summary_no_expiry),
                            if (account.knowsConnections) {
                                account.connections?.let { pluralStringResource(R.plurals.summary_connections, it, it) }
                                    ?: stringResource(R.string.summary_no_connection_limit)
                            } else {
                                null
                            },
                            channels,
                        ).joinToString(" · ")
                    }
                    OptionsSheet(
                        title = stringResource(if (edit) R.string.saved_title else R.string.connected_title, account.name),
                        subtitle = summary,
                        onDismiss = { result = null },
                        options = listOf(SheetOption(stringResource(R.string.done), onClick = actions.onDone)),
                    )
                }
                is ConnectResult.Failed -> OptionsSheet(
                    title = stringResource(R.string.couldnt_connect),
                    subtitle = stringResource(shown.reason),
                    onDismiss = { result = null },
                    options = listOf(SheetOption(stringResource(R.string.try_again), onClick = {})),
                )
                null -> Unit
            }
        }
    }
}

/**
 * Stalker Portal | Xtream Codes | M3U Playlist: a segmented control. Coming up from the form,
 * focus lands on the kind that's showing; moving onto another picks it.
 */
@Composable
private fun TypeTabs(type: SourceType, onPick: (SourceType) -> Unit) {
    val focus = remember { SourceType.entries.associateWith { FocusRequester() } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.px)
            .background(PlasmaColors.Surface1, PlasmaShapes.Row)
            .padding(6.px)
            .focusRestoringGroup(focus.getValue(type)),
        horizontalArrangement = Arrangement.spacedBy(6.px),
    ) {
        for (option in SourceType.entries) {
            val selected = option == type
            PlasmaSurface(
                onClick = { onPick(option) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(focus.getValue(option))
                    .onFocusChanged { if (it.isFocused) onPick(option) },
                style = FocusStyle.Pill,
                shape = RoundedCornerShape(10.px),
                selected = selected,
                selectedColor = PlasmaColors.ButtonFill,
                contentAlignment = Alignment.Center,
            ) {
                val focused = LocalFocused.current
                PlasmaText(
                    stringResource(
                        when (option) {
                            SourceType.Stalker -> R.string.tab_stalker
                            SourceType.Xtream -> R.string.tab_xtream
                            SourceType.M3U -> R.string.tab_m3u
                        },
                    ),
                    plasmaStyle(23f, if (selected || focused) 600 else 500),
                    color = when {
                        focused -> PlasmaColors.OnFocus
                        selected -> PlasmaColors.Text
                        else -> PlasmaColors.Text2
                    },
                )
            }
        }
    }
}

/**
 * A floating-label field (board 21): the label over the value. The field being typed in has a
 * caret; with focus it's white. OK moves to the keyboard.
 */
@Composable
private fun SourceField(
    label: String,
    value: String,
    editing: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    secret: Boolean = false,
    trackingPx: Float = 0f,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(76.px).semantics { contentDescription = listOfNotNull(label, value.takeUnless { secret }, hint).joinToString(", ") },
        style = FocusStyle.Field,
        shape = PlasmaShapes.Field,
        color = if (editing) PlasmaColors.Surface3 else PlasmaColors.Surface1,
        onFocusChanged = { if (it) onFocused() },
        contentAlignment = Alignment.CenterStart,
    ) {
        val focused = LocalFocused.current
        Row(Modifier.padding(horizontal = 22.px), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.px)) {
                PlasmaText(label, plasmaStyle(17f, 600), color = if (focused) PlasmaColors.OnFocusSoft else PlasmaColors.Text3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlasmaText(
                        if (secret && !focused) "•".repeat(value.length) else value,
                        plasmaStyle(24f, 500, trackingPx = trackingPx, tabular = true),
                        color = if (focused) PlasmaColors.OnFocus else PlasmaColors.TextSoft3,
                    )
                    if (editing) {
                        Box(
                            Modifier
                                .padding(start = 2.px)
                                .size(2.px, 28.px)
                                .background(if (focused) PlasmaColors.OnFocus else PlasmaColors.Text),
                        )
                    }
                }
            }
            if (hint != null) PlasmaText(hint, plasmaStyle(20f, 500), color = if (focused) PlasmaColors.OnFocus3 else PlasmaColors.Text3)
        }
    }
}

/** Advanced: the settings a source rarely needs (brief 4.21), summed up on the right. */
@Composable
private fun RowScope.AdvancedRow(summary: String) {
    PlasmaSurface(
        onClick = {},
        modifier = Modifier.weight(1f).height(64.px),
        shape = PlasmaShapes.Field,
        color = PlasmaColors.Surface1,
        contentAlignment = Alignment.CenterStart,
    ) {
        val focused = LocalFocused.current
        val secondary = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text2
        Row(Modifier.padding(horizontal = 22.px), verticalAlignment = Alignment.CenterVertically) {
            PlasmaText(stringResource(R.string.advanced), plasmaStyle(22f, 500), Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.px)) {
                PlasmaText(summary, plasmaStyle(22f, 500), color = secondary)
                PlasmaIcon(PlasmaIcons.ChevronRight, 18.px, tint = secondary, stroke = 2.4f)
            }
        }
    }
}

/** Primary source, with a switch: the source Plasma tries first (5.1). */
@Composable
private fun PrimaryRow(on: Boolean, onFlip: () -> Unit) {
    val label = stringResource(R.string.primary_source)
    val state = stringResource(if (on) R.string.on else R.string.off)
    PlasmaSurface(
        onClick = onFlip,
        modifier = Modifier.width(360.px).height(64.px).semantics { contentDescription = "$label, $state" },
        shape = PlasmaShapes.Field,
        color = PlasmaColors.Surface1,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(start = 22.px, end = 18.px), verticalAlignment = Alignment.CenterVertically) {
            PlasmaText(label, plasmaStyle(22f, 500), Modifier.weight(1f))
            ToggleSwitch(on)
        }
    }
}

/** A switch, green when on (brief 2.9). */
@Composable
private fun ToggleSwitch(on: Boolean) {
    Box(
        Modifier
            .size(58.px, 34.px)
            .background(if (on) PlasmaColors.Ok else PlasmaColors.white(0.2f), CircleShape)
            .padding(3.px),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(28.px).background(PlasmaColors.white(1f), CircleShape))
    }
}

private val KeyRows = listOf("1234567890", "qwertyuiop", "asdfghjkl:", "zxcvbnm./")

/**
 * The keyboard for addresses (board 21): digits and letters, the characters URLs need, and a row
 * of shortcuts (`http://`, `.com`, `:8080`…), Space and Next. Next moves to the next field.
 */
@Composable
private fun SourceKeyboard(
    onCharacters: (String) -> Unit,
    onDelete: () -> Unit,
    onNext: () -> Unit,
    first: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().focusRestoringGroup(first), verticalArrangement = Arrangement.spacedBy(8.px)) {
        KeyRows.forEachIndexed { row, keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.px)) {
                keys.forEachIndexed { i, key ->
                    TextKey(
                        key.toString(),
                        onClick = { onCharacters(key.toString()) },
                        modifier = Modifier.weight(1f).then(if (row == 0 && i == 0) Modifier.focusRequester(first) else Modifier),
                    )
                }
                if (row == KeyRows.lastIndex) {
                    val delete = stringResource(DesignR.string.key_delete)
                    KeyboardKey(onDelete, Modifier.weight(1f).semantics { contentDescription = delete }) {
                        PlasmaIcon(PlasmaIcons.Backspace, 26.px, tint = keyTint(), stroke = 2f)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.px)) {
            for ((text, width) in listOf("http://" to 120, ".com" to 96, ".net" to 96, ":8080" to 104, "/c/" to 84)) {
                TextKey(text, onClick = { onCharacters(text) }, modifier = Modifier.width(width.px))
            }
            TextKey("-", onClick = { onCharacters("-") }, modifier = Modifier.weight(1f))
            TextKey("_", onClick = { onCharacters("_") }, modifier = Modifier.weight(1f))
            TextKey(stringResource(R.string.key_space), onClick = { onCharacters(" ") }, modifier = Modifier.width(170.px))
            TextKey(
                stringResource(R.string.key_next),
                onClick = onNext,
                modifier = Modifier.width(150.px),
                color = PlasmaColors.white(0.16f),
                strong = true,
            )
        }
    }
}

@Composable
private fun keyTint() = if (LocalFocused.current) PlasmaColors.OnFocus else PlasmaColors.TextSoft2

@Composable
private fun TextKey(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = PlasmaColors.Surface2, strong: Boolean = false) {
    KeyboardKey(onClick, modifier, color) {
        PlasmaText(
            text,
            plasmaStyle(22f, if (strong || LocalFocused.current) 600 else 500),
            color = if (LocalFocused.current) PlasmaColors.OnFocus else if (strong) PlasmaColors.Text else PlasmaColors.TextSoft2,
        )
    }
}

@Composable
private fun KeyboardKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PlasmaColors.Surface2,
    height: Dp = 48.px,
    content: @Composable () -> Unit,
) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.height(height),
        style = FocusStyle.Key,
        shape = PlasmaShapes.Key,
        color = color,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
