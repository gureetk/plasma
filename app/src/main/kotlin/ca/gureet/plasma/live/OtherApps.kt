// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.core.data.ContinueEntry
import ca.gureet.plasma.core.model.Formats
import ca.gureet.plasma.feature.home.ContinueItem
import ca.gureet.plasma.feature.home.ContinueKind
import ca.gureet.plasma.integrations.jellyfin.JellyfinItem
import ca.gureet.plasma.integrations.watchnext.PlayNextItem
import ca.gureet.plasma.integrations.watchnext.PlayNextType
import java.time.Duration
import java.time.Instant
import java.util.Locale

/*
 * Other apps' items in Continue Watching (brief 5.19): Android's Play Next row, next to Plasma's own
 * and a Jellyfin server's.
 */

/** Continue Watching's ids for another app's item: "playnext:" and its key. */
private const val PLAY_NEXT = "playnext:"

/** Home's row has this many items at most, Plasma's own and other apps' together. */
private const val ROW_LIMIT = 20

internal fun playNextId(item: PlayNextItem): String = PLAY_NEXT + item.key

/** The Play Next key in a Continue Watching id, or null for one of Plasma's own. */
internal fun playNextKeyOf(id: String): String? = if (id.startsWith(PLAY_NEXT)) id.removePrefix(PLAY_NEXT) else null

/** The app a Play Next key belongs to. */
internal fun packageOfKey(key: String): String = key.substringBefore('|')

/**
 * The items Continue Watching shows from other apps: those being watched, and the episode up next
 * when Show Next Episodes is on, not older than Hide Items Older Than allows ([since]).
 */
internal fun List<PlayNextItem>.forContinue(nextEpisodes: Boolean, since: Instant?): List<PlayNextItem> = filter { item ->
    (item.type == PlayNextType.Continue || (item.type == PlayNextType.Next && nextEpisodes)) && (since == null || !item.lastWatched.isBefore(since))
}

/** Another app's item as Continue Watching draws it, tagged with [appName]. */
internal fun PlayNextItem.continueItem(words: ContinueWords, appName: String): ContinueItem {
    val left = if (durationMs > 0 && positionMs in 1 until durationMs) words.left(Formats.duration(Duration.ofMillis(durationMs - positionMs))) else null
    val meta = listOfNotNull(
        words.episodeText(season, episode),
        episodeTitle,
        if (type == PlayNextType.Next) words.upNext else left,
    ).joinToString(" · ")
    return ContinueItem(
        id = playNextId(this),
        title = title,
        meta = meta,
        image = poster,
        kind = if (wide) ContinueKind.Art else ContinueKind.Poster,
        progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
        sourceTag = appName.uppercase(Locale.getDefault()),
    )
}

/**
 * Home's Continue Watching (brief 5.19): Plasma's own items, other apps' ([others]) and a Jellyfin
 * server's ([jellyfin]), the latest first, 20 at most. [appName] names each app for its tag. A
 * Play Next item Jellyfin also has shows once, as Jellyfin's.
 */
internal fun continueRow(
    own: List<ContinueEntry>,
    others: List<PlayNextItem>,
    words: ContinueWords,
    jellyfin: List<JellyfinItem> = emptyList(),
    jellyfinOffline: Boolean = false,
    jellyfinInPlasma: Boolean = true,
    appName: (String) -> String,
): List<ContinueItem> {
    val ours = own.zip(continueItems(own, words)).map { (entry, item) -> entry.updated to item }
    val theirs = others.besides(jellyfin).map { it.lastWatched to it.continueItem(words, appName(it.packageName)) }
    // An episode up next that the server can't date goes after the rest.
    val servers = jellyfin.map { (it.lastWatched ?: Instant.EPOCH) to it.continueItem(words, jellyfinOffline, jellyfinInPlasma) }
    return (ours + theirs + servers).sortedByDescending { it.first }.take(ROW_LIMIT).map { it.second }
}
