// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Night Mode (Settings › Audio, brief 5.4.6) on one engine's own sound session: Android's
 * DynamicsProcessing effect (Android 9 and later) brings loud passages down and quiet ones up, and
 * its limiter keeps the result from clipping. Like any effect it works on the sound the TV
 * decodes: Dolby and DTS passed through go round it, to a receiver with a night mode of its own.
 * On Android 8, or a TV without the effect, the sound plays as it would without Night Mode.
 *
 * The effect is made the first time Night Mode is turned on, and kept until [release].
 */
class NightMode(private val session: Int) {
    private var effect: AudioEffect? = null

    /** The TV turned the effect down once: it isn't asked again. */
    private var unavailable = false

    fun set(on: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (session == AudioManager.ERROR || session == 0) return
        val made = effect ?: (if (on && !unavailable) make() else null)?.also { effect = it } ?: return
        runCatching { made.enabled = on }
    }

    fun release() {
        effect?.let { runCatching { it.release() } }
        effect = null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun make(): AudioEffect? = try {
        DynamicsProcessing(PRIORITY, session, config())
    } catch (refused: RuntimeException) {
        // UnsupportedOperationException and the like: the TV's audio has no such effect.
        unavailable = true
        null
    }

    companion object {
        private const val PRIORITY = 0
        private const val CHANNELS = 2

        // One compressor over every frequency, 4:1 above −30 dBFS with a soft knee, then 6 dB of
        // make-up gain: speech around −22 dBFS stays within a couple of dB of where it was, a
        // passage at −6 dBFS comes down 12 dB, and one below −35 dBFS comes up 6 dB.
        private const val TOP_HZ = 20_000f
        private const val ATTACK_MS = 10f
        private const val RELEASE_MS = 300f
        private const val RATIO = 4f
        private const val THRESHOLD_DB = -30f
        private const val KNEE_DB = 10f

        /** The noise gate and expander stay out of it. */
        private const val GATE_DB = -90f
        private const val EXPANDER_RATIO = 1f
        private const val MAKEUP_DB = 6f

        // Then a limiter, quick to catch peaks the make-up gain would push past full scale.
        private const val LIMITER_ATTACK_MS = 1f
        private const val LIMITER_RELEASE_MS = 60f
        private const val LIMITER_RATIO = 10f
        private const val LIMITER_THRESHOLD_DB = -2f

        @RequiresApi(Build.VERSION_CODES.P)
        private fun config(): DynamicsProcessing.Config {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                CHANNELS,
                false, 0,
                true, 1,
                false, 0,
                true,
            ).build()
            val band = DynamicsProcessing.MbcBand(
                true, TOP_HZ, ATTACK_MS, RELEASE_MS, RATIO, THRESHOLD_DB, KNEE_DB, GATE_DB, EXPANDER_RATIO, 0f, MAKEUP_DB,
            )
            config.setMbcAllChannelsTo(DynamicsProcessing.Mbc(true, true, 1).apply { setBand(0, band) })
            config.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(true, true, 0, LIMITER_ATTACK_MS, LIMITER_RELEASE_MS, LIMITER_RATIO, LIMITER_THRESHOLD_DB, 0f),
            )
            return config
        }
    }
}
