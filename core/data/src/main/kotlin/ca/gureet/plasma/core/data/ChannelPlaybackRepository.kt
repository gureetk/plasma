// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.ChannelPlaybackEntity
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.ChannelTracks
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.TrackPick
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Why a channel plays with an engine of its own (brief 5.4.3). */
enum class EngineReason(internal val stored: String) {
    /** Picked in Switch Player. */
    User("USER"),

    /** It played the channel after the first engine couldn't. */
    Fallback("FALLBACK"),
}

/** A channel's own sound (brief 5.4.6): its volume level in dB, and whether it's decoded by choice. */
data class ChannelSound(val channelId: String, val levelDb: Int = 0, val decoded: Boolean = false)

/**
 * How each channel plays (brief 6.3, `ChannelPlayback`): the engine it has of its own (5.4.3), its
 * own sound (5.4.6), and the tracks and Picture Size picked on it (4.10), kept per channel and not
 * per profile. Engines go by their names: "Vlc", "Mpv", "Media3".
 */
class ChannelPlaybackRepository(private val db: PlasmaDatabase, private val clock: PlasmaClock) {
    private val dao get() = db.channelPlayback()

    /** The channels with a sound of their own, for Settings › Audio › Channel Volume Levels. */
    val sounds: Flow<List<ChannelSound>> = dao.observeSounds().map { rows -> rows.map { it.sound() } }

    /** [channelId]'s own sound; 0 dB and its source's output when it has none. */
    suspend fun soundOf(channelId: String): ChannelSound = dao.get(channelId)?.sound() ?: ChannelSound(channelId)

    /** Volume Level (Channel options): [channelId] plays at [db] from now on. */
    suspend fun setLevel(channelId: String, db: Int) = change(channelId) { it.copy(volumeOffsetDb = db.coerceIn(MIN_DB, MAX_DB)) }

    /** Decode This Channel: its sound is decoded, so a level can change it. */
    suspend fun decode(channelId: String) = change(channelId) { it.copy(audioOutput = DECODE) }

    /** Reset: [channelId] sounds as the others do again. */
    suspend fun resetSound(channelId: String) = dao.resetSound(channelId)

    /** Reset All: returns how many channels had a sound of their own. */
    suspend fun resetSounds(): Int = dao.resetSounds()

    private suspend fun change(channelId: String, edit: (ChannelPlaybackEntity) -> ChannelPlaybackEntity) {
        dao.upsert(edit(dao.get(channelId) ?: ChannelPlaybackEntity(channelId, engine = null, engineReason = null, engineSetAt = null)))
    }

    private fun ChannelPlaybackEntity.sound() = ChannelSound(channelId, volumeOffsetDb, decoded = audioOutput == DECODE)

    /** The engine [channelId] plays with, when it has one of its own. */
    suspend fun engineFor(channelId: String): String? = dao.get(channelId)?.engine

    /** [engine] is [channelId]'s from now on, for [reason]. */
    suspend fun rememberEngine(channelId: String, engine: String, reason: EngineReason) {
        val row = dao.get(channelId) ?: ChannelPlaybackEntity(channelId, engine = null, engineReason = null, engineSetAt = null)
        dao.upsert(row.copy(engine = engine, engineReason = reason.stored, engineSetAt = clock.now().toEpochMilli()))
    }

    /** Forget Players Chosen per Channel (Settings › Playback): returns how many channels had one. */
    suspend fun forgetEngines(): Int = dao.forgetEngines()

    /** What was picked on [channelId] in Audio and Subtitles, which comes before the language rules (5.4.6). */
    suspend fun tracksOf(channelId: String): ChannelTracks? = dao.get(channelId)?.tracks()

    /** Audio and Subtitles: [pick] plays on [channelId] from now on. */
    suspend fun pickAudio(channelId: String, pick: TrackPick) = change(channelId) { it.copy(audioTrack = encodePick(pick)) }

    /** Audio and Subtitles: [pick] shows on [channelId] from now on; null turns subtitles off on it. */
    suspend fun pickSubtitles(channelId: String, pick: TrackPick?) = change(channelId) { it.copy(subtitleTrack = pick?.let(::encodePick) ?: OFF) }

    /** The channels with a Picture Size of their own (4.10): its name, by the channel's id. */
    val pictureSizes: Flow<Map<String, String>> =
        dao.observePictureSizes().map { rows -> rows.mapNotNull { row -> row.pictureSize?.let { row.channelId to it } }.toMap() }

    /** Picture Size: [channelId] shows its picture as [size] from now on, by its name; null fits it. */
    suspend fun setPictureSize(channelId: String, size: String?) = change(channelId) { it.copy(pictureSize = size) }

    private fun ChannelPlaybackEntity.tracks(): ChannelTracks? {
        if (audioTrack == null && subtitleTrack == null) return null
        return ChannelTracks(
            audio = audioTrack?.let(::decodePick),
            subtitles = subtitleTrack?.takeIf { it != OFF }?.let(::decodePick),
            subtitlesOff = subtitleTrack == OFF,
        )
    }

    private companion object {
        /** AudioOutput.DecodeToPcm, as it's kept. */
        const val DECODE = "DecodeToPcm"

        /** Subtitles turned off on a channel, as it's kept. */
        const val OFF = "off"
        const val MIN_DB = -6
        const val MAX_DB = 6
    }
}

/** A track picked, as it's kept: "language|codec|channels|name", each escaped. */
internal fun encodePick(pick: TrackPick): String =
    listOf(pick.language, pick.codec, pick.channels?.toString(), pick.title).joinToString("|") { escapePart(it.orEmpty()) }

/** A track picked, from how it's kept; null for what isn't one. */
internal fun decodePick(text: String): TrackPick? {
    val parts = text.split('|')
    if (parts.size != PICK_PARTS) return null
    val (language, codec, channels, title) = parts.map { unescapePart(it).ifEmpty { null } }
    return TrackPick(language, codec, channels?.toIntOrNull(), title)
}

private const val PICK_PARTS = 4

private fun escapePart(text: String) = text.replace("%", "%25").replace("|", "%7C")

private fun unescapePart(text: String) = text.replace("%7C", "|").replace("%25", "%")

