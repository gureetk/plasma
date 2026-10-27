// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.database.SourceEntity
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.source.stalker.StalkerAccount
import ca.gureet.plasma.source.stalker.StalkerClient
import ca.gureet.plasma.source.stalker.StalkerDevice
import ca.gureet.plasma.source.stalker.StalkerLogin
import ca.gureet.plasma.source.stalker.StalkerPortal
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
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What someone typed as the server isn't an address (Add a Source, brief 4.21). */
class InvalidServerAddress : IllegalArgumentException("That isn't a server address")

/** What someone typed as a portal's MAC address isn't one (Add a Source, brief 4.21). */
class InvalidMacAddress : IllegalArgumentException("That isn't a MAC address")

/** A source just saved, and whether it's new or one that was already there. */
data class AddedSource(val source: Source, val isNew: Boolean)

/** What Edit starts with (brief 4.20): the source's details, without its passwords. */
data class EditableSource(
    val type: SourceType,
    val name: String,
    /** The Xtream server, the playlist's address, or the portal's. */
    val address: String,
    val username: String,
    /** A playlist's guide addresses typed in for it, comma-separated. */
    val guideUrls: String,
    /** A portal's MAC address. */
    val mac: String = "",
    /** Its Advanced details, without a portal's password. */
    val details: SourceDetails = SourceDetails(),
)

/**
 * Add a Source › Advanced (brief 4.21): what a source rarely needs. Null leaves the default: a
 * MAG250 with a serial number made from the MAC address and this TV's time zone, the user agent
 * Plasma sends anyway, the account's own stream format, and channels refreshed every 12 hours.
 */
data class SourceDetails(
    /** A portal's or a playlist's own user agent. */
    val userAgent: String? = null,
    // A portal's box (7.1): the registered box's own details, for a portal that checks them.
    val stbModel: String? = null,
    val serialNumber: String? = null,
    val deviceId: String? = null,
    val deviceId2: String? = null,
    val signature: String? = null,
    /** The IANA zone the portal writes guide times in. */
    val timeZone: String? = null,
    /** For a portal that wants a login as well as the MAC address. */
    val login: String? = null,
    /** The login's password. On Edit, none keeps the one stored. */
    val password: String? = null,
    /** Xtream's Stream Format: "ts" or "m3u8". */
    val streamFormat: String? = null,
    /** An XMLTV address read after an Xtream server's own guide. */
    val extraGuide: String? = null,
    /** How often a playlist's channels refresh, in hours; 0 only with Refresh Now. */
    val refreshHours: Int? = null,
) {
    // It can carry a password.
    override fun toString(): String = "SourceDetails(…)"
}

/** Provider accounts: adding, signing in and removing them (brief 5.1). */
class SourceRepository(
    private val db: PlasmaDatabase,
    private val secrets: SecretBox,
    private val http: OkHttpClient,
    private val clock: PlasmaClock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    val sources: Flow<List<Source>> = db.sources().observeAll().map { list -> list.map { it.model() } }

    suspend fun all(): List<Source> = db.sources().all().map { it.model() }

    suspend fun get(id: String): Source? = db.sources().get(id)?.model()

    /** Signed-in portal clients by source, so zapping doesn't start with a handshake (brief 7.1). */
    private val portals = ConcurrentHashMap<String, StalkerClient>()

    /** The source as the screens see it, with a portal's MAC address and its extra headers opened. */
    private fun SourceEntity.model(): Source = toSource().let { source ->
        source.copy(mac = macEnc?.let(::openOrNull), playback = source.playback.copy(extraHeaders = extraHeadersEnc?.let(::openOrNull)))
    }

    /**
     * Signs in to an Xtream server and saves the account, encrypted. Adding the same server and
     * username again updates the password instead of adding a second source.
     *
     * @throws InvalidServerAddress when [server] isn't an address.
     * @throws ca.gureet.plasma.source.xtream.XtreamException when the server turns the login down
     *   or isn't an Xtream server, and [java.io.IOException] when it can't be reached.
     */
    suspend fun addXtream(server: String, username: String, password: String, name: String? = null, details: SourceDetails = SourceDetails()): AddedSource {
        val url = XtreamLogin.server(server) ?: throw InvalidServerAddress()
        val extraGuide = guidesOf(details.extraGuide)
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
        )).copy(
            passwordEnc = secrets.seal(password),
            streamFormat = details.streamFormat ?: same?.streamFormat ?: account.defaultFormat.extension,
            guideUrlsEnc = extraGuide.sealed(),
        ).withAccount(account)
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
    suspend fun addM3u(url: String, name: String? = null, guideUrls: String? = null, details: SourceDetails = SourceDetails()): AddedSource {
        val address = url.trim().toHttpUrlOrNull() ?: throw InvalidServerAddress()
        val guides = guidesOf(guideUrls)

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
            guideUrlsEnc = guides.sealed(),
            userAgent = details.userAgent,
            refreshHours = details.refreshHours,
        )
        db.sources().upsert(entity)
        return AddedSource(entity.toSource(), isNew = same == null)
    }

    /**
     * Signs in to a Stalker portal as a MAG box with the MAC address [mac] (brief 7.1) and saves
     * the portal, with the MAC address encrypted. The same portal and MAC address again updates
     * the one there instead of adding a second.
     *
     * @throws InvalidServerAddress when [portal] isn't an address.
     * @throws InvalidMacAddress when [mac] isn't a MAC address.
     * @throws ca.gureet.plasma.source.stalker.StalkerException when the portal turns the box down
     *   or isn't a portal, and [java.io.IOException] when it can't be reached.
     */
    suspend fun addStalker(portal: String, mac: String, name: String? = null, details: SourceDetails = SourceDetails()): AddedSource {
        val address = StalkerPortal.of(portal) ?: throw InvalidServerAddress()
        val box = StalkerDevice.normalizeMac(mac) ?: throw InvalidMacAddress()
        val device = deviceOf(details, box)
        val client = StalkerClient(http, address, device, details.userAgent ?: StalkerClient.USER_AGENT, loginOf(details.login, details.password.orEmpty()))
        val account = client.connect()

        val existing = db.sources().all()
        val same = existing.firstOrNull { it.type == SourceType.Stalker.id && it.server == address.base.toString() && it.macEnc?.let(::openOrNull) == device.mac }
        val entity = (same ?: SourceEntity(
            id = newId(),
            type = SourceType.Stalker.id,
            name = address.base.host,
            server = address.base.toString(),
            username = null,
            passwordEnc = null,
            priority = (existing.maxOfOrNull { it.priority } ?: -1) + 1,
            addedAt = clock.now().toEpochMilli(),
        )).copy(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: same?.name ?: address.base.host,
            macEnc = secrets.seal(device.mac),
        ).withBox(details, client.login).withAccount(account)
        db.sources().upsert(entity)
        portals[entity.id] = client
        return AddedSource(entity.toSource(), isNew = same == null)
    }

    /**
     * Edit on a portal (brief 4.20): signs in with the new address or MAC address and keeps them,
     * with the source's own id.
     *
     * @throws InvalidServerAddress, InvalidMacAddress, and what [addStalker] throws.
     */
    suspend fun updateStalker(id: String, portal: String, mac: String, name: String?, details: SourceDetails? = null): Source {
        val source = db.sources().get(id) ?: throw NoSuchElementException("No source $id")
        val address = StalkerPortal.of(portal) ?: throw InvalidServerAddress()
        val box = StalkerDevice.normalizeMac(mac) ?: throw InvalidMacAddress()
        // Without details, as from an older form, the box stays as it was.
        val wanted = details ?: detailsOf(source)
        // An empty password keeps the one stored, as for an Xtream login.
        val password = wanted.password?.takeIf { it.isNotEmpty() } ?: source.passwordEnc?.let(::openOrNull).orEmpty()
        val client = StalkerClient(http, address, deviceOf(wanted, box), wanted.userAgent ?: StalkerClient.USER_AGENT, loginOf(wanted.login, password))
        val account = client.connect()
        val updated = source.copy(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: source.name,
            server = address.base.toString(),
            macEnc = secrets.seal(box),
        ).withBox(wanted, client.login).withAccount(account)
        db.sources().upsert(updated)
        portals[id] = client
        return updated.toSource()
    }

    /** The client for a Stalker source, signed in once and kept while its details stay the same. */
    suspend fun stalkerClient(sourceId: String): StalkerClient {
        val source = db.sources().get(sourceId) ?: throw NoSuchElementException("No source $sourceId")
        return stalkerClient(source)
    }

    internal fun stalkerClient(source: SourceEntity): StalkerClient {
        val portal = StalkerPortal.of(source.server) ?: throw IllegalStateException("No portal address for ${source.id}")
        val device = deviceOf(detailsOf(source), source.macEnc?.let(secrets::open) ?: throw IllegalStateException("No MAC address for ${source.id}"))
        val userAgent = source.userAgent ?: StalkerClient.USER_AGENT
        val login = loginOf(source.username, source.passwordEnc?.let(::openOrNull).orEmpty())
        portals[source.id]?.takeIf { it.portal.base == portal.base && it.device == device && it.userAgent == userAgent && it.login == login }?.let { return it }
        return StalkerClient(http, portal, device, userAgent, login).also { portals[source.id] = it }
    }

    /** The zone a portal writes guide times in: this TV's, by its IANA name. */
    private fun portalZone(): String = clock.zone.id.takeUnless { it == "Z" } ?: "UTC"

    /**
     * The box a portal knows: its MAC address, and the registered box's details where they're set.
     * A time zone that isn't one reads as this TV's.
     */
    private fun deviceOf(details: SourceDetails, mac: String) = StalkerDevice(
        mac = mac,
        timeZone = details.timeZone?.takeIf { zone -> runCatching { ZoneId.of(zone) }.isSuccess } ?: portalZone(),
        model = details.stbModel ?: StalkerDevice.DEFAULT_MODEL,
        serialNumber = details.serialNumber,
        deviceId = details.deviceId,
        deviceId2 = details.deviceId2,
        signature = details.signature,
    )

    private fun loginOf(login: String?, password: String): StalkerLogin? = login?.let { StalkerLogin(it, password) }

    /** A source's Advanced details as stored, without a portal's password. */
    private fun detailsOf(source: SourceEntity) = SourceDetails(
        userAgent = source.userAgent,
        stbModel = source.stbModel,
        serialNumber = source.serialNumber,
        deviceId = source.deviceId,
        deviceId2 = source.deviceId2,
        signature = source.signature,
        timeZone = source.portalTimeZone,
        login = source.username.takeIf { SourceType.of(source.type) == SourceType.Stalker },
        streamFormat = source.streamFormat.takeIf { SourceType.of(source.type) == SourceType.Xtream },
        extraGuide = guideUrls(source).joinToString(", ").takeIf { it.isNotEmpty() && SourceType.of(source.type) == SourceType.Xtream },
        refreshHours = source.refreshHours,
    )

    /** A portal's box and login as Advanced has them; the login's password is sealed. */
    private fun SourceEntity.withBox(details: SourceDetails, login: StalkerLogin?) = copy(
        stbModel = details.stbModel,
        serialNumber = details.serialNumber,
        deviceId = details.deviceId,
        deviceId2 = details.deviceId2,
        signature = details.signature,
        portalTimeZone = details.timeZone,
        userAgent = details.userAgent,
        username = login?.login,
        passwordEnc = login?.let { secrets.seal(it.password) },
    )

    /** Guide addresses typed in, separated by commas, spaces or lines. */
    private fun guidesOf(typed: String?): List<String> {
        val guides = typed.orEmpty().split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (guides.any { it.toHttpUrlOrNull() == null }) throw InvalidServerAddress()
        return guides
    }

    private fun List<String>.sealed(): String? = takeIf { it.isNotEmpty() }?.joinToString("\n")?.let(secrets::seal)

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
    suspend fun updateXtream(id: String, server: String, username: String, password: String, name: String?, details: SourceDetails? = null): Source {
        val source = db.sources().get(id) ?: throw NoSuchElementException("No source $id")
        val url = XtreamLogin.server(server) ?: throw InvalidServerAddress()
        val wanted = details ?: detailsOf(source)
        val extraGuide = guidesOf(wanted.extraGuide)
        val secret = password.ifEmpty { source.passwordEnc?.let(secrets::open).orEmpty() }
        val account = XtreamClient(http, XtreamLogin(url, username.trim(), secret), source.userAgent).signIn()
        val updated = source.copy(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: source.name,
            server = url.toString(),
            username = username.trim(),
            passwordEnc = secrets.seal(secret),
            streamFormat = wanted.streamFormat ?: source.streamFormat,
            guideUrlsEnc = extraGuide.sealed(),
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
    suspend fun updateM3u(id: String, url: String, name: String?, guideUrls: String?, details: SourceDetails? = null): Source {
        val source = db.sources().get(id) ?: throw NoSuchElementException("No source $id")
        val address = url.trim().toHttpUrlOrNull() ?: throw InvalidServerAddress()
        val guides = guidesOf(guideUrls)
        val wanted = details ?: detailsOf(source)
        val updated = source.copy(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: source.name,
            server = serverOf(address),
            playlistUrlEnc = secrets.seal(address.toString()),
            guideUrlsEnc = guides.sealed(),
            userAgent = wanted.userAgent,
            refreshHours = wanted.refreshHours,
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
                // As people type it: the portal's c/ page.
                SourceType.Stalker -> StalkerPortal.of(source.server)?.referer?.toString() ?: source.server
            },
            username = source.username.takeIf { SourceType.of(source.type) == SourceType.Xtream }.orEmpty(),
            guideUrls = source.guideUrlsEnc?.let(::openOrNull)?.lines()?.filter { it.isNotBlank() }?.joinToString(", ")
                ?.takeIf { SourceType.of(source.type) == SourceType.M3u }.orEmpty(),
            mac = source.macEnc?.let(::openOrNull).orEmpty(),
            details = detailsOf(source),
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
            engineChoice = playback.engineChoice,
            useHub = playback.useHub,
            userAgent = playback.userAgent?.takeIf { it.isNotBlank() },
            referrer = playback.referrer?.takeIf { it.isNotBlank() },
            extraHeadersEnc = playback.extraHeaders?.takeIf { it.isNotBlank() }?.let(secrets::seal),
            udpxy = playback.udpxy?.takeIf { it.isNotBlank() },
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
            SourceType.Stalker -> recordAccount(id, stalkerClient(source).connect())
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
        // What worked for a channel it was part of no longer holds (brief 5.1).
        db.channelSourceMemory().deleteForSource("$sourceId:")
        db.channelPlayback().deleteForSource("$sourceId:")
        db.sources().delete(sourceId)
        portals.remove(sourceId)
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

    internal suspend fun recordAccount(sourceId: String, account: StalkerAccount) {
        db.sources().markAccount(
            id = sourceId,
            status = statusOf(account),
            expiresAt = account.expiresAt?.toEpochMilli(),
            maxConnections = null,
            activeConnections = null,
            outputFormats = "",
            serverTimeZone = null,
        )
    }

    private fun SourceEntity.withAccount(account: StalkerAccount) = copy(
        status = statusOf(account),
        expiresAt = account.expiresAt?.toEpochMilli(),
    )

    /** Portals give no status as Xtream servers do: active, or blocked. */
    private fun statusOf(account: StalkerAccount): String = if (account.active) "Active" else "Blocked"

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
    vodRefreshedAt = vodRefreshedAt?.let(Instant::ofEpochMilli),
    serverTimeZone = serverTimeZone,
    outputFormats = outputFormats.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    refreshHours = refreshHours,
    playback = SourcePlayback(
        streamFormat = streamFormat,
        bufferChoice = bufferChoice,
        audioOutputChoice = audioOutputChoice,
        epgOffsetMinutes = epgOffsetMinutes,
        catchupOffsetMinutes = catchupOffsetMinutes,
        engineChoice = engineChoice,
        useHub = useHub,
        userAgent = userAgent,
        referrer = referrer,
        udpxy = udpxy,
    ),
)
