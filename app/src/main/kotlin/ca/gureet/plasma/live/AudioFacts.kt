// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.R
import ca.gureet.plasma.player.AudioConnection
import ca.gureet.plasma.player.AudioRoutes
import ca.gureet.plasma.player.SurroundFormat

/** This TV's sound output for Settings › Audio (brief 4.28): how it's connected and what it takes. */
@Composable
fun audioFacts(): AudioFacts {
    val context = LocalContext.current
    val route = remember(context) { AudioRoutes.current(context) }
    val kind = stringResource(
        when (route.connection) {
            AudioConnection.Hdmi -> R.string.audio_hdmi
            AudioConnection.HdmiArc -> R.string.audio_hdmi_arc
            AudioConnection.HdmiEarc -> R.string.audio_hdmi_earc
            AudioConnection.Speakers -> R.string.audio_speakers
        },
    )
    val names = SurroundFormat.entries.associateWith { stringResource(it.label) }
    return remember(route, kind, names) {
        AudioFacts(
            connection = listOfNotNull(kind, route.deviceName).joinToString(" · "),
            supported = route.supported.map(names::getValue),
            unsupported = route.unsupported.map(names::getValue),
        )
    }
}

private val SurroundFormat.label: Int
    get() = when (this) {
        SurroundFormat.DolbyDigital -> R.string.format_dolby_digital
        SurroundFormat.DolbyDigitalPlus -> R.string.format_dolby_digital_plus
        SurroundFormat.DolbyAtmos -> R.string.format_dolby_atmos
        SurroundFormat.Dts -> R.string.format_dts
        SurroundFormat.DtsHdMa -> R.string.format_dts_hd_ma
        SurroundFormat.DolbyTrueHd -> R.string.format_dolby_truehd
    }
