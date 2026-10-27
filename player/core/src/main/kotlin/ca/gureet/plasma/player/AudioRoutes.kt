// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** The surround formats Settings › Audio lists (brief 4.28), as the HDMI output reports them. */
enum class SurroundFormat { DolbyDigital, DolbyDigitalPlus, DolbyAtmos, Dts, DtsHdMa, DolbyTrueHd }

/** How the sound leaves the TV. */
enum class AudioConnection { Hdmi, HdmiArc, HdmiEarc, Speakers }

/** Where the sound goes now and which formats it takes untouched (brief 5.4.6). */
data class AudioRoute(
    val connection: AudioConnection,
    /** The sound system's name, when it gives one. */
    val deviceName: String?,
    val supported: List<SurroundFormat>,
) {
    val unsupported: List<SurroundFormat> get() = SurroundFormat.entries - supported.toSet()

    /** Audio Output › Auto passes through when the sound system takes anything compressed. */
    val takesPassthrough: Boolean get() = supported.isNotEmpty()
}

/** Reads the TV's current audio output (brief 5.4.6). */
object AudioRoutes {
    fun current(context: Context): AudioRoute {
        val manager = context.getSystemService(AudioManager::class.java) ?: return AudioRoute(AudioConnection.Speakers, null, emptyList())
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hdmi = HDMI_TYPES.firstNotNullOfOrNull { type -> outputs.firstOrNull { it.type == type } }
            ?: return AudioRoute(AudioConnection.Speakers, null, emptyList())
        val encodings = hdmi.encodings.toSet()
        val supported = FORMATS.filter { (_, codes) -> codes.any { it in encodings } }.map { it.first }
        val connection = when (hdmi.type) {
            TYPE_HDMI_EARC -> AudioConnection.HdmiEarc
            AudioDeviceInfo.TYPE_HDMI_ARC -> AudioConnection.HdmiArc
            else -> AudioConnection.Hdmi
        }
        val name = hdmi.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() && !it.equals(android.os.Build.MODEL, ignoreCase = true) }
        return AudioRoute(connection, name, supported)
    }

    // AudioDeviceInfo and AudioFormat values, written out because some are newer than Android 8.
    private const val TYPE_HDMI_EARC = 29
    private val HDMI_TYPES = listOf(TYPE_HDMI_EARC, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI)
    private const val ENCODING_AC3 = 5
    private const val ENCODING_E_AC3 = 6
    private const val ENCODING_DTS = 7
    private const val ENCODING_DTS_HD = 8
    private const val ENCODING_DOLBY_TRUEHD = 14
    private const val ENCODING_E_AC3_JOC = 18
    private const val ENCODING_DOLBY_MAT = 19
    private const val ENCODING_DTS_HD_MA = 29

    private val FORMATS = listOf(
        SurroundFormat.DolbyDigital to setOf(ENCODING_AC3),
        SurroundFormat.DolbyDigitalPlus to setOf(ENCODING_E_AC3, ENCODING_E_AC3_JOC),
        SurroundFormat.DolbyAtmos to setOf(ENCODING_E_AC3_JOC, ENCODING_DOLBY_MAT),
        SurroundFormat.Dts to setOf(ENCODING_DTS),
        SurroundFormat.DtsHdMa to setOf(ENCODING_DTS_HD, ENCODING_DTS_HD_MA),
        SurroundFormat.DolbyTrueHd to setOf(ENCODING_DOLBY_TRUEHD, ENCODING_DOLBY_MAT),
    )
}
