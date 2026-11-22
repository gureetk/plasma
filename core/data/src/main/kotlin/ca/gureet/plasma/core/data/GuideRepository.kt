// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.ProgrammeEntity
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The guide, for the screens (brief 5.3). */
class GuideRepository(private val db: PlasmaDatabase) {
    /**
     * Everything on between [from] and [to], by guide channel, each in time order. A window can
     * be a day of every channel's programmes, so it's read a few guide channels at a time
     * ([inPieces]), and nothing is done with its rows on the thread that collects it
     * ([byGuideChannel]).
     *
     * It answers as Room's own flow of a query does: at once, then at each change to a table the
     * query reads, one read at a time, a change that comes meanwhile waiting for the read under way.
     */
    fun window(from: Instant, to: Instant): Flow<Map<String, List<Programme>>> =
        db.invalidationTracker.createFlow(PROGRAMMES, SOURCES)
            .conflate()
            .map { inPieces(from.toEpochMilli(), to.toEpochMilli()) }
            .byGuideChannel()

    /**
     * A window's rows, read a few guide channels at a time and put together, in the order one
     * query gives them.
     *
     * Android hands a query's rows over a window of 2 MB at a time, and fills each window by running
     * the query again from its first row: a day of every channel's programmes is many windows, so
     * read as one query its work is done many times over. A piece is as many guide channels as
     * make about [PIECE_BYTES] of rows, going by what the piece before it weighed, which one window
     * holds: so each piece is read once. A guide import that ends between two pieces gives rows
     * from the guide before it and the guide after: the tables' change then brings the next read,
     * which is whole.
     */
    private suspend fun inPieces(from: Long, to: Long): List<ProgrammeEntity> {
        val dao = db.programmes()
        val keys = dao.epgKeys()
        val rows = ArrayList<ProgrammeEntity>()
        var next = 0
        var size = FIRST_PIECE_KEYS
        while (next < keys.size) {
            val piece = keys.subList(next, minOf(next + size, keys.size))
            val read = dao.forKeys(piece, from, to)
            rows += read
            next += piece.size
            size = keysToAPiece(piece.size, read)
        }
        return rows
    }

    /** When the guide runs out: the end of its last programme, or null with no guide at all. */
    val latestEnd: Flow<Instant?> = db.programmes().observeLatestEnd().map { it?.let(Instant::ofEpochMilli) }.distinctUntilChanged()

    suspend fun forChannel(epgKey: String, from: Instant, to: Instant): List<Programme> =
        db.programmes().forKey(epgKey, from.toEpochMilli(), to.toEpochMilli()).map { it.toProgramme() }

    /**
     * [window] for just the guide channels [epgKeys], read once: a few channels' guide in a
     * moment, where the whole window can take seconds on a TV.
     */
    suspend fun windowFor(epgKeys: Collection<String>, from: Instant, to: Instant): Map<String, List<Programme>> =
        epgKeys.distinct().chunked(KEYS_AT_ONCE)
            .flatMap { keys -> db.programmes().forKeys(keys, from.toEpochMilli(), to.toEpochMilli()) }
            .groupBy({ it.epgKey }, { it.toProgramme() })

    /** What starts between [from] and [to] titled [title], whatever its case, on any channel (brief 5.6). */
    suspend fun titled(title: String, from: Instant, to: Instant): List<Programme> =
        db.programmes().titled(title, from.toEpochMilli(), to.toEpochMilli()).map { it.toProgramme() }

    companion object {
        /** Guide channels per query, well under the most arguments SQLite takes in one. */
        private const val KEYS_AT_ONCE = 500

        /** The tables a window's query reads. */
        private const val PROGRAMMES = "programmes"
        private const val SOURCES = "sources"

        /** What's on at [at], and what follows it, from a channel's programmes in time order. */
        fun nowNext(programmes: List<Programme>, at: Instant): NowNext {
            val index = programmes.indexOfFirst { it.isOn(at) }
            if (index >= 0) return NowNext(programmes[index], programmes.getOrNull(index + 1))
            return NowNext(null, programmes.firstOrNull { it.start.isAfter(at) })
        }
    }
}

/**
 * How many guide channels a window's next piece takes, from the [keys] the last one took and the
 * [rows] it gave: as many as make about [PIECE_BYTES] of rows, half of what Android's window
 * holds, so that a piece of channels with twice the last one's programmes still fits. No more
 * than twice the last piece, since a piece of channels with nothing on in the window says nothing
 * of the next.
 */
internal fun keysToAPiece(keys: Int, rows: List<ProgrammeEntity>): Int {
    val bytes = rows.sumOf { it.windowBytes() }
    val fit = if (bytes == 0L) MOST_PIECE_KEYS.toLong() else keys.toLong() * PIECE_BYTES / bytes
    return minOf(fit, keys * 2L).coerceIn(FEWEST_PIECE_KEYS.toLong(), MOST_PIECE_KEYS.toLong()).toInt()
}

/**
 * What a row takes of Android's window, near enough: a slot for each of its fields, and its
 * strings, reckoned at two bytes a character (one for Latin letters, two or three for others).
 */
private fun ProgrammeEntity.windowBytes(): Long =
    FIELDS_BYTES + 2L * (sourceId.length + epgKey.length + title.length + (subtitle?.length ?: 0) + (description?.length ?: 0) + (category?.length ?: 0) + (icon?.length ?: 0) + (rating?.length ?: 0))

/** A window's first piece, before anything is known of the guide's rows. */
internal const val FIRST_PIECE_KEYS = 25
internal const val FEWEST_PIECE_KEYS = 5

/** Well under the most arguments SQLite takes in one query. */
internal const val MOST_PIECE_KEYS = 500
internal const val PIECE_BYTES = 1024L * 1024L

/** A programme's fifteen fields, at sixteen bytes each, and its row's own slot. */
private const val FIELDS_BYTES = 15 * 16L + 4L

/**
 * Each answer of a guide query sorted by guide channel, its rows in the order they came.
 *
 * Writes elsewhere in the table (an import in progress) re-run the query: an answer with the same
 * rows as the last isn't passed on. Comparing an answer with the last and sorting it both go
 * through every row, and a flow's steps run where it's collected unless it says otherwise, which
 * for a screen is the main thread: so both are done on [on], and only the sorted answer reaches
 * the collector. A newer answer takes the place of one not yet taken.
 */
internal fun Flow<List<ProgrammeEntity>>.byGuideChannel(
    on: CoroutineContext = Dispatchers.Default,
    programme: (ProgrammeEntity) -> Programme = ProgrammeEntity::toProgramme,
): Flow<Map<String, List<Programme>>> =
    distinctUntilChanged()
        .map { rows -> rows.groupBy({ it.epgKey }, programme) }
        .conflate()
        .flowOn(on)

internal fun ProgrammeEntity.toProgramme() = Programme(
    epgKey = epgKey,
    start = Instant.ofEpochMilli(startAt),
    end = Instant.ofEpochMilli(endAt),
    title = title,
    subtitle = subtitle,
    description = description,
    category = category,
    icon = icon,
    season = season,
    episode = episode,
    isNew = isNew,
    rating = rating,
)
