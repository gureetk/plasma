// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.SourceEntity
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.source.xtream.XtreamAccount
import ca.gureet.plasma.source.xtream.XtreamClient
import ca.gureet.plasma.source.xtream.XtreamLogin
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What someone typed as the server isn't an address (Add a Source, brief 4.21). */
class InvalidServerAddress : IllegalArgumentException("That isn't a server address")

/** A source just saved, and whether it's new or one that was already there. */
data class AddedSource(val source: Source, val isNew: Boolean)

/** Provider accounts: adding, signing in and removing them (brief 5.1). */
class SourceRepository(
    private val db: PlasmaDatabase,
    private val secrets: SecretBox,
    private val http: OkHttpClient,
    private val clock: PlasmaClock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    val sources: Flow<List<Source>> = db.sources().observeAll().map { list -> list.map { it.toSource() } }

    suspend fun all(): List<Source> = db.sources().all().map { it.toSource() }

    suspend fun get(id: String): Source? = db.sources().get(id)?.toSource()

    /**
     * Signs in to an Xtream server and saves the account, encrypted. Adding the same server and
     * username again updates the password instead of adding a second source.
     *
     * @throws InvalidServerAddress when [server] isn't an address.
     * @throws ca.gureet.plasma.source.xtream.XtreamException when the server turns the login down
     *   or isn't an Xtream server, and [java.io.IOException] when it can't be reached.
     */
    suspend fun addXtream(server: String, username: String, password: String, name: String? = null): AddedSource {
        val url = XtreamLogin.server(server) ?: throw InvalidServerAddress()
        val login = XtreamLogin(url, username.trim(), password)
        val account = XtreamClient(http, login).signIn()

        val existing = db.sources().all()
        val same = existing.firstOrNull { it.type == SourceType.Xtream.id && it.server == url.toString() && it.username == login.username }
        val entity = (same ?: SourceEntity(
            id = newId(),
            type = SourceType.Xtream.id,
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: url.host,
            server = url.toString(),
            username = login.username,
            passwordEnc = null,
            priority = (existing.maxOfOrNull { it.priority } ?: -1) + 1,
            addedAt = clock.now().toEpochMilli(),
            streamFormat = account.defaultFormat.extension,
        )).copy(passwordEnc = secrets.seal(password)).withAccount(account)
        db.sources().upsert(entity)
        return AddedSource(entity.toSource(), isNew = same == null)
    }

    /**
     * Saves an M3U playlist by its address, encrypted, with any guide addresses typed in for it
     * (brief 4.21). Nothing is downloaded yet: the channel import reads the playlist. Adding the same
     * address again updates its name and guide instead of adding a second source.
     *
     * @throws InvalidServerAddress when [url], or one of [guideUrls], isn't a web address.
     */
    suspend fun addM3u(url: String, name: String? = null, guideUrls: String? = null): AddedSource {
        val address = url.trim().toHttpUrlOrNull() ?: throw InvalidServerAddress()
        val guides = guideUrls.orEmpty().split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (guides.any { it.toHttpUrlOrNull() == null }) throw InvalidServerAddress()

        val existing = db.sources().all()
        val same = existing.firstOrNull { it.type == SourceType.M3u.id && it.playlistUrlEnc?.let(::openOrNull) == address.toString() }
        val entity = (same ?: SourceEntity(
            id = newId(),
            type = SourceType.M3u.id,
            name = address.host,
            server = serverOf(address),
            username = null,
            passwordEnc = null,
            priority = (existing.maxOfOrNull { it.priority } ?: -1) + 1,
            addedAt = clock.now().toEpochMilli(),
        )).copy(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: same?.name ?: address.host,
            playlistUrlEnc = secrets.seal(address.toString()),
            guideUrlsEnc = guides.takeIf { it.isNotEmpty() }?.joinToString("\n")?.let(secrets::seal),
        )
        db.sources().upsert(entity)
        return AddedSource(entity.toSource(), isNew = same == null)
    }

    /** A playlist's address, opened. */
    internal fun playlistUrl(source: SourceEntity): String =
        source.playlistUrlEnc?.let(secrets::open) ?: throw IllegalStateException("No playlist address for ${source.id}")

    /** The guides to read for a playlist: the ones typed in, or else the ones the playlist names. */
    internal fun guideUrls(source: SourceEntity): List<String> =
        (source.guideUrlsEnc ?: source.playlistGuideUrlsEnc)?.let(secrets::open)?.lines()?.filter { it.isNotBlank() }.orEmpty()

    /** Keeps the guide addresses a playlist names (`url-tvg`), for when none were typed in. */
    internal suspend fun recordPlaylistGuides(sourceId: String, urls: List<String>) {
        val source = db.sources().get(sourceId) ?: return
        val now = source.playlistGuideUrlsEnc?.let(::openOrNull)?.lines()?.filter { it.isNotBlank() }.orEmpty()
        if (now == urls) return
        db.sources().upsert(source.copy(playlistGuideUrlsEnc = urls.takeIf { it.isNotEmpty() }?.joinToString("\n")?.let(secrets::seal)))
    }

    /** A sealed value that can't be opened any more, as after the Keystore was reset, reads as nothing. */
    private fun openOrNull(sealed: String): String? = runCatching { secrets.open(sealed) }.getOrNull()

    /** The scheme, host and port: what Settings shows of an address. */
    private fun serverOf(url: HttpUrl): String =
        if (url.port == HttpUrl.defaultPort(url.scheme)) "${url.scheme}://${url.host}" else "${url.scheme}://${url.host}:${url.port}"

    /** A client for the source, signed in with its stored credentials. */
    suspend fun xtreamClient(sourceId: String): XtreamClient {
        val source = db.sources().get(sourceId) ?: throw NoSuchElementException("No source $sourceId")
        return xtreamClient(source)
    }

    internal fun xtreamClient(source: SourceEntity): XtreamClient {
        val password = source.passwordEnc?.let(secrets::open).orEmpty()
        return XtreamClient(http, XtreamLogin(source.server.toHttpUrl(), source.username.orEmpty(), password), source.userAgent)
    }

    /** Removes a source with its channels, guide, favorites and number choices; the next one becomes primary. */
    suspend fun remove(sourceId: String) {
        db.programmes().deleteForSource(sourceId)
        db.favorites().removeForSource(sourceId)
        db.recentChannels().removeForSource(sourceId)
        db.numberOverrides().deleteForSource(sourceId)
        db.sources().delete(sourceId)
        db.sources().all().forEachIndexed { index, source ->
            if (source.priority != index) db.sources().upsert(source.copy(priority = index))
        }
    }

    internal suspend fun recordAccount(sourceId: String, account: XtreamAccount) {
        db.sources().get(sourceId)?.let { db.sources().upsert(it.withAccount(account)) }
    }

    private fun SourceEntity.withAccount(account: XtreamAccount) = copy(
        status = account.status,
        expiresAt = account.expiresAt?.toEpochMilli(),
        maxConnections = account.maxConnections,
        activeConnections = account.activeConnections,
        outputFormats = account.outputFormats.joinToString(","),
        serverTimeZone = account.serverTimeZone,
    )
}

internal fun SourceEntity.toSource() = Source(
    id = id,
    type = SourceType.of(type),
    name = name,
    server = server,
    username = username,
    priority = priority,
    enabled = enabled,
    status = status,
    expiresAt = expiresAt?.let(Instant::ofEpochMilli),
    maxConnections = maxConnections,
    activeConnections = activeConnections,
    streamFormat = streamFormat,
    userAgent = userAgent,
    channelsRefreshedAt = channelsRefreshedAt?.let(Instant::ofEpochMilli),
    guideRefreshedAt = guideRefreshedAt?.let(Instant::ofEpochMilli),
    lastError = lastError,
)
