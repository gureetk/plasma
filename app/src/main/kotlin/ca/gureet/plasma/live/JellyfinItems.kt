// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.FileAudio
import ca.gureet.plasma.core.data.FileVideo
import ca.gureet.plasma.core.data.JellyfinAccount
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.feature.home.ContinueItem
import ca.gureet.plasma.feature.home.ContinueKind
import ca.gureet.plasma.integrations.jellyfin.JellyfinException
import ca.gureet.plasma.integrations.jellyfin.JellyfinItem
import ca.gureet.plasma.integrations.jellyfin.JellyfinKind
import ca.gureet.plasma.integrations.jellyfin.JellyfinProblem
import ca.gureet.plasma.integrations.jellyfin.JellyfinTitle
import ca.gureet.plasma.integrations.watchnext.PlayNextItem
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/*
 * A Jellyfin server's items in Continue Watching (brief 5.19), next to Plasma's own and other apps'.
 */

/** Continue Watching's ids for a Jellyfin item: "jellyfin:" and its id. */
private const val JELLYFIN = "jellyfin:"

internal fun jellyfinId(item: JellyfinItem): String = JELLYFIN + item.id

/** The Jellyfin id in a Continue Watching id, or null for another item. */
internal fun jellyfinIdOf(id: String): String? = if (id.startsWith(JELLYFIN)) id.removePrefix(JELLYFIN) else null

/**
 * A Jellyfin item as Continue Watching draws it, tagged JELLYFIN: "Jellyfin · 52 min left" for a
 * movie (brief 4.1), and an episode as Plasma's own are, "S2, E4 · 18 min left" or "Up next".
 * When the server didn't answer as one was opened ([offline]), each says "Jellyfin is offline".
 * [inPlasma] when OK plays it in Plasma's player rather than the Jellyfin app.
 */
internal fun JellyfinItem.continueItem(words: ContinueWords, offline: Boolean = false, inPlasma: Boolean = true): ContinueItem {
    val left = if (durationMs > 0 && positionMs in 1 until durationMs) words.left(Formats.duration(Duration.ofMillis(durationMs - positionMs))) else null
    val meta = when {
        offline -> listOf(words.jellyfinOffline)
        kind == JellyfinKind.Episode -> listOfNotNull(
            words.episodeText(season?.toString(), episode?.toString()) ?: episodeTitle,
            if (upNext) words.upNext else left,
        )
        else -> listOfNotNull(words.jellyfin, left)
    }.joinToString(" · ")
    return ContinueItem(
        id = jellyfinId(this),
        title = title,
        meta = meta,
        image = image,
        kind = if (wide || image == null) ContinueKind.Art else ContinueKind.Poster,
        progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
        sourceTag = words.jellyfin.uppercase(Locale.getDefault()),
        // A server's items are hidden one at a time: there's no app to hide.
        offersHideAll = false,
        resumes = inPlasma,
    )
}

/** The source a Jellyfin title plays from, as Plasma's player knows it. */
private const val JELLYFIN_SOURCE = "jellyfin"

/** A Jellyfin movie or episode as Plasma's player takes it (brief 5.19). */
internal fun JellyfinTitle.playable(): Playable = Playable(
    kind = if (kind == JellyfinKind.Episode) Playable.Kind.Episode else Playable.Kind.Movie,
    id = id,
    sourceId = JELLYFIN_SOURCE,
    remoteId = id,
    extension = null,
    title = title,
    seriesId = seriesId,
    season = season,
    number = episode,
    episodeTitle = episodeTitle,
    year = year,
    genres = genres,
    art = art,
    duration = durationMs.takeIf { it > 0 }?.let(Duration::ofMillis),
    video = video?.let { FileVideo(it.codec, it.profile, it.width, it.height, it.frameRate, it.hdr) },
    audio = audio?.let { FileAudio(it.codec, it.channels, it.language, it.bitRate) },
)

/**
 * The items Continue Watching shows: not hidden with Hide from Plasma ([hidden], with when each
 * had been watched as it was hidden) unless watched again since, and not older than Hide Items
 * Older Than allows ([since]). Next Up episodes the server can't date stay.
 */
internal fun List<JellyfinItem>.forContinue(hidden: Map<String, Long>, since: Instant?): List<JellyfinItem> = filter { item ->
    val watched = item.lastWatched
    val hiddenAt = hidden[item.id]
    val shown = hiddenAt == null || (watched != null && watched.toEpochMilli() > hiddenAt)
    shown && (since == null || watched == null || !watched.isBefore(since))
}

/**
 * Play Next items that are also among [jellyfin]'s: the Jellyfin one stays, as it carries an exact
 * position (5.19). The same item is the same id (the Jellyfin app gives its own items theirs), or
 * the same title, season and episode.
 */
internal fun List<PlayNextItem>.besides(jellyfin: List<JellyfinItem>): List<PlayNextItem> {
    if (jellyfin.isEmpty()) return this
    val ids = jellyfin.map { idKey(it.id) }.toSet()
    val titles = jellyfin.map { titleKey(it.title, it.season?.toString(), it.episode?.toString()) }.toSet()
    return filterNot { item -> item.contentId?.let(::idKey) in ids || titleKey(item.title, item.season, item.episode) in titles }
}

private fun idKey(id: String) = id.replace("-", "").lowercase(Locale.ROOT)

private fun titleKey(title: String, season: String?, episode: String?) =
    Triple(title.trim().lowercase(Locale.ROOT), season?.trimStart('0'), episode?.trimStart('0'))

/**
 * The signed-in user's Jellyfin items (brief 5.19): read at once and every 15 minutes while
 * Continue Watching shows. When the server doesn't answer, the items read last stay for an hour;
 * when it has signed Plasma out, [onSignedOut] runs and the items go.
 */
internal class JellyfinFeed(
    private val clock: PlasmaClock,
    private val read: suspend (JellyfinAccount, nextUp: Boolean, since: Instant?) -> List<JellyfinItem>,
    private val onSignedOut: suspend (JellyfinAccount) -> Unit,
) {
    private class Kept(val who: String, val at: Instant, val items: List<JellyfinItem>)

    @Volatile
    private var kept: Kept? = null

    fun items(account: JellyfinAccount?, nextUp: Boolean, since: Instant?): Flow<List<JellyfinItem>> = flow {
        if (account == null) {
            kept = null
            emit(emptyList())
            return@flow
        }
        val who = "${account.server}|${account.userId}"
        // What was read last shows at once, while the server is asked again.
        keptFor(who)?.let { emit(it) }
        while (true) {
            val items = try {
                read(account, nextUp, since).also { kept = Kept(who, clock.now(), it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                if (failed is JellyfinException && failed.problem == JellyfinProblem.SignedOut) {
                    kept = null
                    try {
                        onSignedOut(account)
                    } catch (stillFailed: Exception) {
                        if (stillFailed is CancellationException) throw stillFailed
                    }
                    emit(emptyList())
                    return@flow
                }
                keptFor(who).orEmpty()
            }
            emit(items)
            delay(EVERY)
        }
    }

    /** The items read last for [who], while they're less than an hour old. */
    private fun keptFor(who: String): List<JellyfinItem>? =
        kept?.takeIf { it.who == who && Duration.between(it.at, clock.now()) < KEEP }?.items

    private companion object {
        val EVERY: Duration = Duration.ofMinutes(15)
        val KEEP: Duration = Duration.ofHours(1)
    }
}

private suspend fun delay(duration: Duration) = delay(duration.toMillis())
