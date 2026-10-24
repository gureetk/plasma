// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ca.gureet.plasma.core.design.FocusStyle
import ca.gureet.plasma.core.design.LocalFocused
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaShapes
import ca.gureet.plasma.core.design.PlasmaSurface
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.LocalPlasmaClock
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px

/**
 * Add a Source › Advanced (brief 4.21): what a source rarely needs, by the names
 * [AddSourceForm.advanced] has it under. Choices open a sheet; the rest are typed. An empty one
 * keeps the default.
 */
object AdvancedFields {
    /** A portal's box: the MAG model it presents as. */
    const val STB_MODEL = "stbModel"

    /** The IANA zone a portal writes its guide in. */
    const val TIME_ZONE = "timeZone"
    const val SERIAL = "serial"
    const val DEVICE_ID = "deviceId"
    const val DEVICE_ID2 = "deviceId2"
    const val SIGNATURE = "signature"

    /** A portal's or a playlist's own user agent. */
    const val USER_AGENT = "userAgent"

    /** A login and password, for a portal that wants them as well as the MAC address. */
    const val LOGIN = "login"
    const val LOGIN_PASSWORD = "loginPassword"

    /** Xtream's stream format: one of [FORMATS]. */
    const val FORMAT = "format"

    /** An XMLTV address read after an Xtream server's own guide. */
    const val EXTRA_GUIDE = "extraGuide"

    /** How often a playlist's channels refresh: one of [REFRESH_HOURS], as text. */
    const val REFRESH = "refresh"

    /** The MAG models a portal may know a box as. The first is the usual one. */
    val STB_MODELS: List<String> = listOf(
        "MAG250", "MAG200", "MAG245", "MAG254", "MAG256", "MAG322", "MAG324", "MAG349", "MAG351", "MAG352",
        "MAG420", "MAG424", "MAG520", "MAG524", "AuraHD",
    )

    /** The zones a portal may write its guide in, besides this TV's own. */
    val TIME_ZONES: List<String> = listOf(
        "UTC", "Europe/London", "Europe/Dublin", "Europe/Lisbon", "Europe/Paris", "Europe/Madrid", "Europe/Amsterdam",
        "Europe/Berlin", "Europe/Rome", "Europe/Stockholm", "Europe/Warsaw", "Europe/Athens", "Europe/Bucharest",
        "Europe/Istanbul", "Europe/Kyiv", "Europe/Moscow", "Africa/Cairo", "Africa/Lagos", "Africa/Johannesburg",
        "Asia/Dubai", "Asia/Karachi", "Asia/Kolkata", "Asia/Dhaka", "Asia/Bangkok", "Asia/Singapore", "Asia/Shanghai",
        "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland", "America/St_Johns", "America/Halifax", "America/Toronto",
        "America/New_York", "America/Chicago", "America/Winnipeg", "America/Edmonton", "America/Denver",
        "America/Vancouver", "America/Los_Angeles", "America/Mexico_City", "America/Bogota", "America/Sao_Paulo",
    )

    /** Xtream's stream formats: MPEG-TS, the usual, then HLS. */
    val FORMATS: List<String> = listOf("ts", "m3u8")

    /** How often a playlist's channels refresh, in hours, the usual first; 0 is only with Refresh Now. */
    val REFRESH_HOURS: List<Int> = listOf(12, 6, 24, 0)
}

/** Advanced's fields by row (brief 4.21). */
internal fun SourceType.advancedRows(): List<List<String>> = when (this) {
    SourceType.Stalker -> listOf(
        listOf(AdvancedFields.STB_MODEL, AdvancedFields.TIME_ZONE, AdvancedFields.SERIAL),
        listOf(AdvancedFields.DEVICE_ID, AdvancedFields.DEVICE_ID2),
        listOf(AdvancedFields.SIGNATURE, AdvancedFields.USER_AGENT),
        listOf(AdvancedFields.LOGIN, AdvancedFields.LOGIN_PASSWORD),
    )
    SourceType.Xtream -> listOf(listOf(AdvancedFields.FORMAT), listOf(AdvancedFields.EXTRA_GUIDE))
    SourceType.M3U -> listOf(listOf(AdvancedFields.USER_AGENT), listOf(AdvancedFields.REFRESH))
}

private val CHOICES = setOf(AdvancedFields.STB_MODEL, AdvancedFields.TIME_ZONE, AdvancedFields.FORMAT, AdvancedFields.REFRESH)

internal fun isChoice(field: String): Boolean = field in CHOICES

/** Advanced's typed fields, in the order Next goes through them. */
internal fun SourceType.advancedTextFields(): List<String> = advancedRows().flatten().filterNot(::isChoice)

/** Advanced's fields, typed or chosen. */
internal fun SourceType.advancedFields(): List<String> = advancedRows().flatten()

/** "Advanced · Stalker Portal", where the kinds of source are on the main form. */
@Composable
internal fun AdvancedHeading(type: SourceType) {
    Row(Modifier.fillMaxWidth().height(62.px).padding(start = 4.px), verticalAlignment = Alignment.CenterVertically) {
        PlasmaText(stringResource(R.string.advanced), plasmaStyle(30f, 600, trackingPx = -0.4f))
        PlasmaText(" · ${stringResource(type.tabName())}", plasmaStyle(30f, 500, trackingPx = -0.4f), color = PlasmaColors.Text3)
    }
}

internal fun SourceType.tabName(): Int = when (this) {
    SourceType.Stalker -> R.string.tab_stalker
    SourceType.Xtream -> R.string.tab_xtream
    SourceType.M3U -> R.string.tab_m3u
}

internal fun SourceType.advancedHelp(): Int = when (this) {
    SourceType.Stalker -> R.string.advanced_help_stalker
    SourceType.Xtream -> R.string.advanced_help_xtream
    SourceType.M3U -> R.string.advanced_help_m3u
}

/**
 * Advanced's fields, in rows like the main form's. A typed field works as the main form's do; OK
 * on a choice opens its sheet ([onChoose]).
 */
@Composable
internal fun AdvancedForm(
    type: SourceType,
    values: Map<String, String>,
    editing: String,
    /** Edit on a source: an empty portal password keeps the one stored. */
    edit: Boolean,
    focusOf: (String) -> FocusRequester,
    onEditing: (String) -> Unit,
    onType: () -> Unit,
    onChoose: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.px)) {
        for (row in type.advancedRows()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.px)) {
                for (field in row) {
                    val width = choiceWidth(field)
                    val modifier = (if (width != null) Modifier.width(width) else Modifier.weight(1f)).focusRequester(focusOf(field))
                    if (isChoice(field)) {
                        ChoiceField(advancedLabel(field), choiceLabel(field, values[field].orEmpty()), onClick = { onChoose(field) }, modifier = modifier)
                    } else {
                        val value = values[field].orEmpty()
                        SourceField(
                            label = advancedLabel(field),
                            value = value,
                            editing = editing == field,
                            onFocused = { onEditing(field) },
                            onClick = onType,
                            modifier = modifier,
                            hint = if (value.isEmpty()) advancedHint(type, field, edit && values[AdvancedFields.LOGIN].orEmpty().isNotEmpty()) else null,
                            secret = field == AdvancedFields.LOGIN_PASSWORD,
                        )
                    }
                }
            }
        }
    }
}

/** A choice's width; a typed field shares what's left of its row. */
private fun choiceWidth(field: String): Dp? = when (field) {
    AdvancedFields.STB_MODEL -> 260.px
    AdvancedFields.TIME_ZONE -> 380.px
    AdvancedFields.FORMAT, AdvancedFields.REFRESH -> 420.px
    else -> null
}

@Composable
private fun advancedLabel(field: String): String = stringResource(
    when (field) {
        AdvancedFields.STB_MODEL -> R.string.field_stb_model
        AdvancedFields.TIME_ZONE -> R.string.field_time_zone
        AdvancedFields.SERIAL -> R.string.field_serial
        AdvancedFields.DEVICE_ID -> R.string.field_device_id
        AdvancedFields.DEVICE_ID2 -> R.string.field_device_id2
        AdvancedFields.SIGNATURE -> R.string.field_signature
        AdvancedFields.USER_AGENT -> R.string.field_user_agent
        AdvancedFields.LOGIN -> R.string.field_login
        AdvancedFields.LOGIN_PASSWORD -> R.string.field_password
        AdvancedFields.FORMAT -> R.string.field_stream_format
        AdvancedFields.EXTRA_GUIDE -> R.string.field_extra_guide
        else -> R.string.field_refresh
    },
)

/** What an empty field stands for: the default, or nothing. */
@Composable
private fun advancedHint(type: SourceType, field: String, keepsPassword: Boolean): String = when (field) {
    AdvancedFields.SERIAL -> stringResource(R.string.hint_serial)
    AdvancedFields.USER_AGENT -> stringResource(if (type == SourceType.Stalker) R.string.hint_mag_agent else R.string.hint_standard_agent)
    AdvancedFields.LOGIN_PASSWORD -> stringResource(if (keepsPassword) R.string.password_keep_hint else R.string.hint_none)
    else -> stringResource(R.string.hint_none)
}

/**
 * A choice's options as (value, label), the usual one first. Nothing chosen reads as the first:
 * for Xtream's stream format, that's the account's own when the source is added.
 */
@Composable
internal fun advancedOptions(field: String): List<Pair<String, String>> = when (field) {
    AdvancedFields.STB_MODEL -> AdvancedFields.STB_MODELS.map { it to it }
    AdvancedFields.TIME_ZONE -> listOf("" to stringResource(R.string.time_zone_tv, tvZone())) + AdvancedFields.TIME_ZONES.map { it to it }
    AdvancedFields.FORMAT -> listOf(AdvancedFields.FORMATS[0] to stringResource(R.string.format_ts), AdvancedFields.FORMATS[1] to stringResource(R.string.format_hls))
    else -> AdvancedFields.REFRESH_HOURS.map { hours -> hours.toString() to stringResource(refreshName(hours)) }
}

/** This TV's time zone by its name, as the portal is told it: "UTC" for a plain offset of zero. */
@Composable
private fun tvZone(): String = LocalPlasmaClock.current.zone.normalized().id.let { if (it == "Z") "UTC" else it }

/** Which of [options] [value] is; nothing chosen is the first. */
internal fun chosenIndex(options: List<Pair<String, String>>, value: String): Int = options.indexOfFirst { it.first == value }.coerceAtLeast(0)

/** The label of [value], one of [field]'s options. */
@Composable
internal fun choiceLabel(field: String, value: String): String {
    val options = advancedOptions(field)
    return options[chosenIndex(options, value)].second
}

private fun refreshName(hours: Int): Int = when (hours) {
    6 -> R.string.refresh_6
    24 -> R.string.refresh_24
    0 -> R.string.refresh_manual
    else -> R.string.refresh_12
}

/** The Advanced row's summary (board 21): what's chosen, in short. */
@Composable
internal fun advancedSummary(type: SourceType, values: Map<String, String>): String = when (type) {
    SourceType.Stalker -> stringResource(R.string.advanced_stalker, values[AdvancedFields.STB_MODEL]?.takeIf { it.isNotEmpty() } ?: AdvancedFields.STB_MODELS.first())
    SourceType.Xtream -> {
        val format = stringResource(if (values[AdvancedFields.FORMAT] == AdvancedFields.FORMATS[1]) R.string.format_short_hls else R.string.format_short_ts)
        stringResource(if (values[AdvancedFields.EXTRA_GUIDE].isNullOrBlank()) R.string.advanced_xtream else R.string.advanced_xtream_extra, format)
    }
    SourceType.M3U -> {
        val refresh = stringResource(
            when (values[AdvancedFields.REFRESH]) {
                "6" -> R.string.refresh_summary_6
                "24" -> R.string.refresh_summary_24
                "0" -> R.string.refresh_summary_manual
                else -> R.string.refresh_summary_12
            },
        )
        stringResource(if (values[AdvancedFields.USER_AGENT].isNullOrBlank()) R.string.advanced_m3u else R.string.advanced_m3u_own, refresh)
    }
}

/** A field whose value is chosen from a sheet, with ▾ on the right. OK opens the sheet. */
@Composable
private fun ChoiceField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PlasmaSurface(
        onClick = onClick,
        modifier = modifier.height(76.px).semantics { contentDescription = "$label, $value" },
        style = FocusStyle.Field,
        shape = PlasmaShapes.Field,
        color = PlasmaColors.Surface1,
        contentAlignment = Alignment.CenterStart,
    ) {
        val focused = LocalFocused.current
        Row(Modifier.padding(start = 22.px, end = 18.px), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.px)) {
                PlasmaText(label, plasmaStyle(17f, 600), color = if (focused) PlasmaColors.OnFocusSoft else PlasmaColors.Text3)
                PlasmaText(value, plasmaStyle(24f, 500, tabular = true), color = if (focused) PlasmaColors.OnFocus else PlasmaColors.TextSoft3)
            }
            PlasmaIcon(PlasmaIcons.ChevronDown, 20.px, tint = if (focused) PlasmaColors.OnFocus2 else PlasmaColors.Text3, stroke = 2.4f)
        }
    }
}
