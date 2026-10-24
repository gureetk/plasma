// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.integrations.watchnext

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.media.tv.TvContract
import android.os.Handler
import android.os.Looper
import android.provider.BaseColumns
import java.net.URISyntaxException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Why an item is in Android's Play Next row (brief 5.19). */
enum class PlayNextType {
    /** Started and not finished. */
    Continue,

    /** The episode after one that was finished. */
    Next,

    /** New in something the user follows. */
    New,

    /** Saved to watch later. */
    Watchlist,
}

/**
 * An item another app put in Android's Play Next row (brief 5.19): what it is, how far it was
 * watched, when, and how to open it.
 */
data class PlayNextItem(
    val id: Long,
    val packageName: String,
    /** The app's own id for it, when it gives one: the same item keeps it if the row is rebuilt. */
    val contentId: String?,
    val title: String,
    val episodeTitle: String? = null,
    val season: String? = null,
    val episode: String? = null,
    val poster: String? = null,
    /** 16:9 art, rather than a poster. */
    val wide: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val type: PlayNextType = PlayNextType.Continue,
    val lastWatched: Instant = Instant.EPOCH,
    val intentUri: String? = null,
) {
    /** What Hide from Plasma remembers it by. */
    val key: String get() = "$packageName|${contentId ?: id}"
}

/**
 * Android's Play Next row (brief 5.19), which TV apps share: Plasma reads every app's items once
 * it's allowed to read TV listings, and only its own before.
 */
class PlayNext(private val context: Context) {
    /** Whether Android lets Plasma read other apps' items. */
    fun allowed(): Boolean = context.checkSelfPermission(READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED

    /** The row's items as they are now; empty when the TV has no such row. Reads a provider: call it off the main thread. */
    fun items(): List<PlayNextItem> = try {
        context.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.toPlayNextItem()?.let(::add) }
        }.orEmpty()
    } catch (refused: SecurityException) {
        emptyList()
    } catch (missing: IllegalArgumentException) {
        // A TV without the TV provider.
        emptyList()
    }

    /**
     * The row's items, then again whenever an app changes them (where the provider says so) and
     * every [everyMs] while it's collected.
     */
    fun changes(everyMs: Long = REFRESH_MS): Flow<List<PlayNextItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val watching = try {
            context.contentResolver.registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, observer)
            true
        } catch (refused: SecurityException) {
            false
        }
        val ticks = launch {
            while (true) {
                trySend(Unit)
                delay(everyMs)
            }
        }
        awaitClose {
            ticks.cancel()
            if (watching) context.contentResolver.unregisterContentObserver(observer)
        }
    }.conflate().map { items() }.flowOn(Dispatchers.IO)

    /**
     * Opens [item] in its app, by the link it gave (brief 5.19); only a link into that same app,
     * which can't borrow Plasma's access to anything. Without one, the app opens at its start.
     */
    fun open(item: PlayNextItem): Boolean {
        val pm = context.packageManager
        val intent = linkOf(item) ?: pm.getLeanbackLaunchIntentForPackage(item.packageName) ?: pm.getLaunchIntentForPackage(item.packageName) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (refused: RuntimeException) {
            false
        }
    }

    @SuppressLint("QueryPermissionsNeeded") // Apps with a launcher entry are declared in the manifest's <queries>.
    @Suppress("DEPRECATION") // The flags' class is API 33.
    private fun linkOf(item: PlayNextItem): Intent? {
        val uri = item.intentUri ?: return null
        val intent = try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        } catch (bad: URISyntaxException) {
            return null
        }
        intent.selector = null
        intent.flags = intent.flags and GRANTS.inv()
        val opens = context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
        return intent.takeIf { opens == item.packageName }
    }

    companion object {
        /** Android's "read TV listings": other apps' Play Next items. */
        const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

        /** How often the row is read again while Home shows it, besides when apps say it changed. */
        const val REFRESH_MS = 15 * 60_000L

        /** Flags that would hand the app Plasma's own access to files. */
        private const val GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}

private val PROJECTION = arrayOf(
    BaseColumns._ID,
    TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
    TvContract.WatchNextPrograms.COLUMN_CONTENT_ID,
    TvContract.WatchNextPrograms.COLUMN_TITLE,
    TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
    TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
    TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
    TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
    TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
    TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
    TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
    TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
    TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
    TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
)

/** Art shaped for a 16:9 tile: wide, 3:2 or 4:3; a poster otherwise. */
private val WIDE = setOf(
    TvContract.WatchNextPrograms.ASPECT_RATIO_16_9,
    TvContract.WatchNextPrograms.ASPECT_RATIO_3_2,
    TvContract.WatchNextPrograms.ASPECT_RATIO_4_3,
)

/** One row of the provider, or null for one that isn't to be shown. */
internal fun Cursor.toPlayNextItem(): PlayNextItem? {
    fun text(column: String) = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)?.takeIf { it.isNotBlank() }
    fun number(column: String) = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
    if (number(TvContract.WatchNextPrograms.COLUMN_BROWSABLE) == 0L) return null
    val title = text(TvContract.WatchNextPrograms.COLUMN_TITLE) ?: return null
    val packageName = text(TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME) ?: return null
    val aspect = number(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO)
    return PlayNextItem(
        id = number(BaseColumns._ID) ?: return null,
        packageName = packageName,
        contentId = text(TvContract.WatchNextPrograms.COLUMN_CONTENT_ID),
        title = title,
        episodeTitle = text(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE),
        season = text(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER),
        episode = text(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER),
        poster = text(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI),
        wide = aspect == null || aspect.toInt() in WIDE,
        positionMs = number(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS) ?: 0,
        durationMs = number(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS) ?: 0,
        type = when (number(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE)?.toInt()) {
            TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT -> PlayNextType.Next
            TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW -> PlayNextType.New
            TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST -> PlayNextType.Watchlist
            else -> PlayNextType.Continue
        },
        lastWatched = Instant.ofEpochMilli(number(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS) ?: 0),
        intentUri = text(TvContract.WatchNextPrograms.COLUMN_INTENT_URI),
    )
}
