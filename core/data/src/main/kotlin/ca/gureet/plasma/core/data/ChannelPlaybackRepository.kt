// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.ChannelPlaybackEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock

/** Why a channel plays with an engine of its own (brief 5.4.3). */
enum class EngineReason(internal val stored: String) {
    /** Picked in Switch Player. */
    User("USER"),

    /** It played the channel after the first engine couldn't. */
    Fallback("FALLBACK"),
}

/**
 * How each channel plays (brief 6.3, `ChannelPlayback`): for now, the engine it has of its own,
 * kept per channel and not per profile (5.4.3). Engines go by their names: "Vlc", "Mpv", "Media3".
 */
class ChannelPlaybackRepository(private val db: PlasmaDatabase, private val clock: PlasmaClock) {
    private val dao get() = db.channelPlayback()

    /** The engine [channelId] plays with, when it has one of its own. */
    suspend fun engineFor(channelId: String): String? = dao.get(channelId)?.engine

    /** [engine] is [channelId]'s from now on, for [reason]. */
    suspend fun rememberEngine(channelId: String, engine: String, reason: EngineReason) {
        val row = dao.get(channelId) ?: ChannelPlaybackEntity(channelId, engine = null, engineReason = null, engineSetAt = null)
        dao.upsert(row.copy(engine = engine, engineReason = reason.stored, engineSetAt = clock.now().toEpochMilli()))
    }

    /** Forget Players Chosen per Channel (Settings › Playback): returns how many channels had one. */
    suspend fun forgetEngines(): Int = dao.forgetEngines()
}
