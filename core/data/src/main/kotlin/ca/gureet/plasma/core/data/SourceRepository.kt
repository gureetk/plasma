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
import okhttp3.Request
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What someone typed as the server isn't an address (Add a Source, brief 4.21). */
class InvalidServerAddress : IllegalArgumentException("That isn't a server address")

/** A source just saved, and whether it's new or one that was already there. */
data class AddedSource(val source: Source, val isNew: Boolean)

/** What Edit starts with (brief 4.20): the source's details, without its password. */
data class EditableSource(
    val type: SourceType,
    val name: String,
    /** The Xtream server, or the playlist's address. */
    val address: String,
    val username: String,
    /** A playlist's guide addresses typed in for it, comma-separated. */
    val guideUrls: String,
)

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
        db.sources().markPlaylistGuides(sourceId, urls.takeIf { it.isNotEmpty() }?.joinToString("\n")?.let(secrets::seal))
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

    /**
     * Edit on an Xtream source (brief 4.20): signs in with the new details and keeps them, with the
     * source's own id, so its channels, favorites and history stay. A blank [password] keeps the
     * stored one.
     *
     * @throws InvalidServerAddress when [server] isn't an address, and what [addXtream] throws.
     */
    suspend fun updateXtream(id: String, server: String, username: String, password: String, name: String?): Source {
        val source = db.sources().get(id) ?: throw NoSuchElementException("No source $id")
        val url = XtreamLogin.server(server) ?: throw InvalidServerAddress()
        val secret = password.ifEmpty { source.passwordEnc?.let(secrets::open).orEmpty() }
        val account = XtreamClient(http, XtreamLogin(url, username.trim(), secret), source.userAgent).signIn()
        val updated = source.copy(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: source.name,
            server = url.toString(),
            username = username.trim(),
            passwordEnc = secrets.seal(secret),
        ).withAccount(account)
        db.sources().upsert(updated)
        return updated.toSource()
    }

    /**
     * Edit on a playlist (brief 4.20): a new address, name or guide addresses, kept with the
     * source's own id. Nothing is downloaded yet: the channel import reads the playlist.
     *
     * @throws InvalidServerAddress when [url], or one of [guideUrls], isn't a web address.
     */
    suspend fun updateM3u(id: String, url: String, name: String?, guideUrls: String?): Source {
        val source = db.sources().get(id) ?: throw NoSuchElementException("No source $id")
        val address = url.trim().toHttpUrlOrNull() ?: throw InvalidServerAddress()
        val guides = guideUrls.orEmpty().split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (guides.any { it.toHttpUrlOrNull() == null }) throw InvalidServerAddress()
        val updated = source.copy(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: source.name,
            server = serverOf(address),
            playlistUrlEnc = secrets.seal(address.toString()),
            guideUrlsEnc = guides.takeIf { it.isNotEmpty() }?.joinToString("\n")?.let(secrets::seal),
        )
        db.sources().upsert(updated)
        return updated.toSource()
    }

    /** What an Edit form starts with: the server or playlist address, without the password. */
    suspend fun editable(id: String): EditableSource? {
        val source = db.sources().get(id) ?: return null
        return EditableSource(
            type = SourceType.of(source.type),
            name = source.name,
            address = when (SourceType.of(source.type)) {
                SourceType.Xtream -> source.server
                SourceType.M3u -> source.playlistUrlEnc?.let(::openOrNull).orEmpty()
            },
            username = source.username.orEmpty(),
            guideUrls = source.guideUrlsEnc?.let(::openOrNull)?.lines()?.filter { it.isNotBlank() }?.joinToString(", ").orEmpty(),
        )
    }

    /** Set as Primary (brief 4.20): this source first; the others keep their order behind it. */
    suspend fun setPrimary(id: String) {
        val all = db.sources().all()
        val chosen = all.firstOrNull { it.id == id } ?: return
        (listOf(chosen) + all.filter { it.id != id }).forEachIndexed { index, source ->
            if (source.priority != index) db.sources().upsert(source.copy(priority = index))
        }
    }

    /** Disable and Enable (brief 4.20): a disabled source's channels leave every list until it's on again. */
    suspend fun setEnabled(id: String, enabled: Boolean) = db.sources().setEnabled(id, enabled)

    /**
     * Playback for This Source (brief 4.20), and the guide's time offset (5.16): a new offset
     * moves the programmes already loaded.
     */
    suspend fun setPlayback(id: String, playback: SourcePlayback) {
        db.sources().setPlayback(
            id = id,
            streamFormat = playback.streamFormat,
            bufferChoice = playback.bufferChoice,
            audioOutputChoice = playback.audioOutputChoice,
            catchupOffsetMinutes = playback.catchupOffsetMinutes,
        )
        db.programmes().setGuideOffset(id, playback.epgOffsetMinutes)
    }

    /**
     * Runs [edit], which changes the source [id] and loads it again, and puts the source back as
     * it was if that fails or is cancelled: an edit that doesn't connect leaves it working as before.
     */
    suspend fun <T> keepingOnFailure(id: String, edit: suspend () -> T): T {
        val before = db.sources().get(id) ?: throw NoSuchElementException("No source $id")
        try {
            return edit()
        } catch (failed: Throwable) {
            withContext(NonCancellable) { db.sources().upsert(before) }
            throw failed
        }
    }

    /**
     * Test Connection (brief 4.20): signs in to an Xtream server, keeping what it says about the
     * account, or asks a playlist's server for the playlist, and says how long it took.
     *
     * @throws java.io.IOException when the server can't be reached, and what signing in throws.
     */
    suspend fun check(id: String): Duration {
        val source = db.sources().get(id) ?: throw NoSuchElementException("No source $id")
        val started = System.nanoTime()
        when (SourceType.of(source.type)) {
            SourceType.Xtream -> recordAccount(id, xtreamClient(source).signIn())
            SourceType.M3u -> withContext(Dispatchers.IO) {
                val request = Request.Builder().url(playlistUrl(source)).apply { source.userAgent?.let { header("User-Agent", it) } }.build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw PlaylistRefused(response.code)
                }
            }
        }
        return Duration.ofNanos(System.nanoTime() - started)
    }

    /** Removes a source with its channels, guide, favorites and number choices; the next one becomes primary. */
    suspend fun remove(sourceId: String) {
        db.programmes().deleteForSource(sourceId)
        db.favorites().removeForSource(sourceId)
        db.recentChannels().removeForSource(sourceId)
        db.reminders().removeForSource(sourceId)
        db.numberOverrides().deleteForSource(sourceId)
        db.sources().delete(sourceId)
        db.sources().all().forEachIndexed { index, source ->
            if (source.priority != index) db.sources().upsert(source.copy(priority = index))
        }
    }

    internal suspend fun recordAccount(sourceId: String, account: XtreamAccount) {
        db.sources().markAccount(
            id = sourceId,
            status = account.status,
            expiresAt = account.expiresAt?.toEpochMilli(),
            maxConnections = account.maxConnections,
            activeConnections = account.activeConnections,
            outputFormats = account.outputFormats.joinToString(","),
            serverTimeZone = account.serverTimeZone,
        )
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
    serverTimeZone = serverTimeZone,
    outputFormats = outputFormats.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    playback = SourcePlayback(
        streamFormat = streamFormat,
        bufferChoice = bufferChoice,
        audioOutputChoice = audioOutputChoice,
        epgOffsetMinutes = epgOffsetMinutes,
        catchupOffsetMinutes = catchupOffsetMinutes,
    ),
)
