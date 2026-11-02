// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.player.EngineCount
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.FreezeEvent
import ca.gureet.plasma.player.FreezeLook
import ca.gureet.plasma.player.FreezeWatch
import ca.gureet.plasma.player.StreamKeys
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Whether the picture on screen changes, from Show Freeze Watch's tiny copies of it. */
internal enum class PictureOnScreen {
    /** Not read yet. */
    Unknown,

    /** This TV won't copy the video surface, or there's none. */
    Unreadable,

    /** It changed since the last copy. */
    Changing,

    /** It changed after the stream opened, and has been the same since. */
    Same,

    /** It hasn't changed since the stream opened, or since the copies began. */
    NeverChanged,
}

/** What Show Freeze Watch read at its last look, beside what the player keeps. */
internal data class FreezeReading(
    /** The engine's counts that moved over the last look, per second. */
    val rates: Map<EngineCount, Long> = emptyMap(),
    /** Its counts that are shown as totals: the stream's breaks and damaged packets. */
    val totals: Map<EngineCount, Long> = emptyMap(),
    val picture: PictureOnScreen = PictureOnScreen.Unknown,
    /** While [PictureOnScreen.Same]: for how long, in ms. */
    val pictureSameMs: Long = 0,
)

/**
 * Show Freeze Watch's looks, about once a second: the engine's own counts turned into how much
 * they moved in a second, and whether the copy of the picture on screen changed. A new channel or
 * engine, or counts that start again (a restart opens a new stream), start it afresh.
 */
internal class FreezeSampler {
    private var stream: Pair<String?, EngineId>? = null
    private var counts: Map<EngineCount, Long> = emptyMap()
    private var countedAt = 0L
    private var picture: Int? = null
    private var pictureAt = 0L
    private var pictureChanged = false

    /** Reads [counts] and the [picture]'s hash (null when it couldn't be copied) of [channelId] in [engine], at [now] in ms. */
    fun next(now: Long, channelId: String?, engine: EngineId, counts: Map<EngineCount, Long>, picture: Int?): FreezeReading {
        val same = stream == (channelId to engine)
        stream = channelId to engine
        val elapsed = now - countedAt
        val rates = if (same && elapsed > 0) {
            counts.mapNotNull { (count, value) ->
                if (count in TOTALS) return@mapNotNull null
                val before = this.counts[count] ?: return@mapNotNull null
                // Counts that went back belong to a stream opened since.
                if (value < before) return@mapNotNull null
                count to (value - before) * MILLIS_PER_SECOND / elapsed
            }.toMap()
        } else {
            emptyMap()
        }
        this.counts = counts
        countedAt = now
        if (!same) {
            this.picture = null
            pictureChanged = false
        }
        val seen = when {
            picture == null -> PictureOnScreen.Unreadable
            this.picture == null -> {
                pictureAt = now
                PictureOnScreen.NeverChanged
            }
            picture != this.picture -> {
                pictureChanged = true
                pictureAt = now
                PictureOnScreen.Changing
            }
            pictureChanged -> PictureOnScreen.Same
            else -> PictureOnScreen.NeverChanged
        }
        if (picture != null) this.picture = picture
        return FreezeReading(
            rates = rates,
            totals = counts.filterKeys { it in TOTALS },
            picture = seen,
            pictureSameMs = if (seen == PictureOnScreen.Same) now - pictureAt else 0,
        )
    }

    private companion object {
        val TOTALS = setOf(EngineCount.Discontinuities, EngineCount.Corrupted)
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/** The words Show Freeze Watch needs, from the app's strings; tests make their own. */
internal class FreezeWatchWords(
    /** "Freeze Watch". */
    val title: String,
    /** "restarts after 5 s". */
    val after: (time: String) -> String,
    /** "Restart Frozen Streams is off". */
    val settingOff: String,
    /** What the player is doing about the stream, short of watching it: "opening", "paused"… */
    val state: (FreezeLook.State) -> String,
    /** "moving". */
    val moving: String,
    /** "still for 3.2 s, 1.2 MB came in", or "…, nothing came in" without [bytes]. */
    val still: (time: String, bytes: String?) -> String,
    /** "by its place in the stream". */
    val byPlace: String,
    /** "Each second: 25 decoded · 25 shown". */
    val eachSecond: (counts: String) -> String,
    /** "So far: 2 breaks in the stream · 0 damaged packets". */
    val soFar: (counts: String) -> String,
    /** One count: "25 decoded", "sound 47 played", "2 breaks in the stream". */
    val count: (EngineCount, Long) -> String,
    /** The picture on screen: "Picture on screen: the same for 12 s"; null when it isn't known. */
    val picture: (PictureOnScreen, time: String) -> String?,
    /** "Coming in: 6.4 Mb/s". */
    val comingIn: (rate: String) -> String,
    /** "Since 14:02: 3 restarts (VLC 2 · mpv 1)". */
    val since: (time: String, what: String) -> String,
    /** "3 restarts", "1 handed to the next engine", "no restarts". */
    val restarts: (Int) -> String,
    val handedOn: (Int) -> String,
    val noRestarts: String,
    /** What an event was: "restarted after 5.0 s still, 3.1 MB came in". */
    val event: (FreezeEvent, time: String, bytes: String) -> String,
    /** "VLC", "mpv", "Android player". */
    val engine: (EngineId) -> String,
)

/**
 * Show Freeze Watch's lines (the owner's ask of 2 November), top to bottom: the setting, what's
 * playing and what Restart Frozen Streams makes of it, the engine's counts each second, the picture
 * on screen, what's coming in, the restarts since Plasma started, and the latest events, newest first.
 */
internal fun freezeWatchLines(
    afterMs: Long?,
    watch: FreezeWatch,
    reading: FreezeReading,
    bitrate: String,
    names: Map<String, String>,
    zone: ZoneId,
    today: Instant,
    words: FreezeWatchWords,
): List<String> = buildList {
    add(listOf(words.title, afterMs?.let { words.after("${it / MILLIS} s") } ?: words.settingOff).joinToString(" · "))
    val look = watch.now
    val channelId = look.channelId
    val engine = look.engine
    if (channelId == null || engine == null || look.state == FreezeLook.State.Idle) {
        add(words.state(FreezeLook.State.Idle))
    } else {
        val doing = when (look.state) {
            FreezeLook.State.Watching -> if (look.stillMs < STILL_SHOWN_MS) {
                words.moving
            } else {
                words.still(durationText(look.stillMs), look.arrivedBytes.takeIf { it > 0 }?.let(::bytesText))
            }
            else -> words.state(look.state)
        }
        val measure = words.byPlace.takeIf { look.byPlace && look.state in LOOKED_AT }
        add(listOfNotNull(nameOf(channelId, names), words.engine(engine), doing, measure).joinToString(" · "))
        val counts = COUNT_ORDER.mapNotNull { count -> reading.rates[count]?.let { words.count(count, it) } }
        if (counts.isNotEmpty()) add(words.eachSecond(counts.joinToString(" · ")))
        val totals = COUNT_ORDER.mapNotNull { count -> reading.totals[count]?.let { words.count(count, it) } }
        if (totals.isNotEmpty()) add(words.soFar(totals.joinToString(" · ")))
        words.picture(reading.picture, durationText(reading.pictureSameMs))?.let(::add)
        if (bitrate.isNotEmpty()) add(words.comingIn(bitrate))
    }
    val restarts = watch.restarts.values.sum()
    val handedOn = watch.handedOn.values.sum()
    val done = if (restarts == 0 && handedOn == 0) {
        words.noRestarts
    } else {
        listOfNotNull(
            words.restarts(restarts).takeIf { restarts > 0 }?.let { total ->
                val byEngine = ENGINE_ORDER.mapNotNull { id -> watch.restarts[id]?.takeIf { it > 0 }?.let { "${words.engine(id)} $it" } }
                "$total (${byEngine.joinToString(" · ")})"
            },
            words.handedOn(handedOn).takeIf { handedOn > 0 },
        ).joinToString(" · ")
    }
    add(words.since(whenText(watch.since, zone, today), done))
    for (event in watch.events) {
        val at = Formats.timeWithSeconds(Instant.ofEpochMilli(event.at).atZone(zone))
        val what = words.event(event, durationText(event.ms), bytesText(event.bytes))
        add(listOfNotNull(at, nameOf(event.channelId, names), words.engine(event.engine), what).joinToString(" · "))
    }
}

/** A channel's name for the key the player shows; null for a movie or an episode, or one it doesn't know. */
private fun nameOf(key: String, names: Map<String, String>): String? {
    if (StreamKeys.isVod(key)) return null
    return names[StreamKeys.channelOf(key)] ?: names[key]
}

/** "14:02" today, "Sat 31 Oct, 14:02" before. */
private fun whenText(at: Long, zone: ZoneId, today: Instant): String {
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val time24 = Formats.time(time)
    return if (time.toLocalDate() == today.atZone(zone).toLocalDate()) time24 else "${Formats.date(time)}, $time24"
}

/** "0.4 s", "3.2 s", "45 s", "2 min 05 s", "1 h 02 min". */
internal fun durationText(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / MILLIS
    return when {
        ms < TEN_SECONDS_MS -> String.format(Locale.US, "%.1f s", ms.coerceAtLeast(0) / MILLIS.toDouble())
        seconds < SECONDS_PER_MINUTE -> "$seconds s"
        seconds < SECONDS_PER_HOUR -> String.format(Locale.US, "%d min %02d s", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
        else -> Formats.duration(java.time.Duration.ofSeconds(seconds))
    }
}

/** "312 bytes", "640 KB", "1.2 MB". */
internal fun bytesText(bytes: Long): String = when {
    bytes >= MEGABYTE -> String.format(Locale.US, "%.1f MB", bytes / MEGABYTE.toDouble())
    bytes >= KILOBYTE -> "${bytes / KILOBYTE} KB"
    else -> "$bytes bytes"
}

/** The states in which the player looks at what the engine puts out. */
private val LOOKED_AT = setOf(FreezeLook.State.Watching, FreezeLook.State.NotMoved)

/** The counts in the order the line gives them: the picture's, then the sound's, then the stream's. */
private val COUNT_ORDER = listOf(
    EngineCount.PicturesDecoded,
    EngineCount.PicturesShown,
    EngineCount.PicturesDropped,
    EngineCount.PictureMoves,
    EngineCount.SoundPlayed,
    EngineCount.SoundDropped,
    EngineCount.SoundMoves,
    EngineCount.Discontinuities,
    EngineCount.Corrupted,
)

private val ENGINE_ORDER = listOf(EngineId.Vlc, EngineId.Mpv, EngineId.Media3)

/** Below this, a look that found nothing moving since the last says it's moving. */
private const val STILL_SHOWN_MS = 1_000L
private const val MILLIS = 1_000L
private const val TEN_SECONDS_MS = 10_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val KILOBYTE = 1_000L
private const val MEGABYTE = 1_000_000L
