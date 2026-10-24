// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.integrations.jellyfin

import android.content.Context
import android.os.Build
import android.provider.Settings
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.SortOrder
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * A Jellyfin server, read directly with Jellyfin's own SDK (brief 5.19), so it works without the
 * Jellyfin app: finding the server, signing in with Quick Connect or a password, and the user's
 * items for Continue Watching. Every call can throw [JellyfinException].
 */
class JellyfinClient(context: Context, version: String, http: OkHttpClient) {
    /** The TV's name, as the server lists the devices signed in: the name it has in its settings. */
    private val deviceName: String = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private val factory = OkHttpFactory(http)
    private val jellyfin = createJellyfin {
        this.context = context.applicationContext
        clientInfo = ClientInfo(name = CLIENT, version = version)
        // Each sign-in brings its own device id; this one is never used.
        deviceInfo = DeviceInfo(id = CLIENT, name = deviceName)
        apiClientFactory = factory
        socketConnectionFactory = factory
    }

    /**
     * Servers answering on this network (UDP port 7359), as they answer. It ends once the network
     * has been quiet for a moment, or straight away without one.
     */
    fun discover(): Flow<JellyfinServer> = jellyfin.discovery.discoverLocalServers(timeout = DISCOVERY_MS)
        .map { JellyfinServer(it.address, it.name.ifBlank { hostOf(it.address) }) }
        .catch { /* No network to ask on. */ }
        .flowOn(Dispatchers.IO)

    /**
     * The Jellyfin server at what was typed: an address with or without its scheme and port, which
     * the SDK tries in turn. Null when nothing there answers as Jellyfin.
     */
    suspend fun find(typed: String): JellyfinServer? = withContext(Dispatchers.IO) {
        val candidates = jellyfin.discovery.getAddressCandidates(typed.trim())
        if (candidates.isEmpty()) return@withContext null
        val best = try {
            jellyfin.discovery.getRecommendedServers(candidates, RecommendedServerInfoScore.OK)
                .minWithOrNull(compareBy({ it.score.ordinal }, { it.responseTime }))
        } catch (failed: ApiClientException) {
            null
        } ?: return@withContext null
        val name = best.systemInfo.getOrNull()?.serverName?.takeIf { it.isNotBlank() }
        JellyfinServer(best.address, name ?: hostOf(best.address))
    }

    /**
     * Starts Quick Connect on [server]: the code to approve in another Jellyfin app. Throws
     * [JellyfinProblem.QuickConnectOff] when the server has it turned off.
     */
    suspend fun quickConnect(server: JellyfinServer): QuickConnectCode = call(on401 = JellyfinProblem.QuickConnectOff) {
        val deviceId = newDeviceId()
        val api = api(server.address, deviceId)
        if (!api.authenticationApi.getQuickConnectEnabled().content) throw JellyfinException(JellyfinProblem.QuickConnectOff)
        val started = api.authenticationApi.initiateQuickConnect().content
        QuickConnectCode(started.code, started.secret, deviceId)
    }

    /** Whether [code] has been approved yet. */
    suspend fun approved(server: JellyfinServer, code: QuickConnectCode): Boolean = call(on401 = JellyfinProblem.QuickConnectOff) {
        api(server.address, code.deviceId).authenticationApi.getQuickConnectState(code.secret).content.authenticated
    }

    /** Signs in with [code], once it's approved. */
    suspend fun signIn(server: JellyfinServer, code: QuickConnectCode): JellyfinLogin = call(on401 = JellyfinProblem.Refused) {
        val api = api(server.address, code.deviceId)
        api.authenticationApi.authenticateWithQuickConnect(QuickConnectDto(secret = code.secret)).content.login(server, code.deviceId)
    }

    /** Signs in with a username and password; [JellyfinProblem.WrongLogin] when the server turns them down. */
    suspend fun signIn(server: JellyfinServer, username: String, password: String): JellyfinLogin = call(on401 = JellyfinProblem.WrongLogin) {
        val deviceId = newDeviceId()
        val api = api(server.address, deviceId)
        api.authenticationApi.authenticateUserByName(AuthenticateUserByName(username = username, pw = password)).content.login(server, deviceId)
    }

    /** Signs [login] out on the server, so its token stops working (brief 6.10). Quietly, if the server can't be reached. */
    suspend fun signOut(login: JellyfinLogin) {
        try {
            call(on401 = JellyfinProblem.SignedOut) { api(login).sessionApi.reportSessionEnded() }
        } catch (gone: JellyfinException) {
            // Forgotten here all the same.
        }
    }

    /**
     * The user's items for Continue Watching (brief 5.19): what they've started, and with [nextUp]
     * the episodes up next, [limit] of each at most. Next Up leaves out series not watched since
     * [since]. [JellyfinProblem.SignedOut] when the sign-in no longer works.
     */
    suspend fun continueItems(login: JellyfinLogin, nextUp: Boolean, since: Instant?, limit: Int = LIMIT): List<JellyfinItem> =
        call(on401 = JellyfinProblem.SignedOut) {
            val api = api(login)
            val user = UUID.fromString(login.userId)
            val started = api.libraryApi.getResumeItems(
                userId = user,
                limit = limit,
                fields = FIELDS,
                mediaTypes = listOf(MediaType.VIDEO),
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = IMAGES,
                enableTotalRecordCount = false,
                enableImages = true,
            ).content.items
            if (!nextUp) return@call started.map { it.item(api, upNext = false, lastWatched = it.userData?.lastPlayedDate?.instant()) }
            val next = api.showApi.getNextUp(
                userId = user,
                limit = limit,
                fields = FIELDS,
                enableImages = true,
                imageTypeLimit = 1,
                enableImageTypes = IMAGES,
                enableUserData = true,
                nextUpDateCutoff = since?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()) },
                enableTotalRecordCount = false,
                enableResumable = false,
                enableRewatching = false,
            ).content.items.filter { up -> started.none { it.id == up.id } }
            val watched = if (next.isEmpty()) emptyMap() else seriesWatched(api, user)
            started.map { it.item(api, upNext = false, lastWatched = it.userData?.lastPlayedDate?.instant()) } +
                next.map { it.item(api, upNext = true, lastWatched = it.seriesId?.let(watched::get)) }
        }

    /** When each series was last watched, from the episodes watched last: Next Up doesn't say. */
    private suspend fun seriesWatched(api: ApiClient, user: UUID): Map<UUID, Instant> = api.libraryApi.getItems(
        userId = user,
        limit = WATCHED_LIMIT,
        recursive = true,
        includeItemTypes = listOf(BaseItemKind.EPISODE),
        sortBy = listOf(ItemSortBy.DATE_PLAYED),
        sortOrder = listOf(SortOrder.DESCENDING),
        isPlayed = true,
        enableUserData = true,
        enableImages = false,
        enableTotalRecordCount = false,
    ).content.items
        .mapNotNull { episode ->
            val series = episode.seriesId ?: return@mapNotNull null
            val at = episode.userData?.lastPlayedDate?.instant() ?: return@mapNotNull null
            series to at
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, times) -> times.max() }

    private fun BaseItemDto.item(api: ApiClient, upNext: Boolean, lastWatched: Instant?): JellyfinItem {
        val episode = type == BaseItemKind.EPISODE
        val art = art(api)
        return JellyfinItem(
            id = id.toString(),
            kind = when (type) {
                BaseItemKind.MOVIE -> JellyfinKind.Movie
                BaseItemKind.EPISODE -> JellyfinKind.Episode
                else -> JellyfinKind.Other
            },
            title = (if (episode) seriesName?.takeIf { it.isNotBlank() } else null) ?: name.orEmpty(),
            episodeTitle = if (episode) name else null,
            season = if (episode) parentIndexNumber else null,
            episode = if (episode) indexNumber else null,
            image = art?.url,
            wide = art?.wide ?: false,
            positionMs = (userData?.playbackPositionTicks ?: 0) / TICKS_PER_MS,
            durationMs = (runTimeTicks ?: 0) / TICKS_PER_MS,
            lastWatched = lastWatched,
            upNext = upNext,
        )
    }

    private class Art(val url: String, val wide: Boolean)

    /**
     * The picture for an item, as Jellyfin's own apps choose it for Continue Watching: 16:9 art
     * first (its thumb, its series' thumb, a backdrop), then its own picture, then its series'
     * poster.
     */
    private fun BaseItemDto.art(api: ApiClient): Art? {
        fun wide(item: UUID, type: ImageType, tag: String) = Art(api.imageApi.getItemImageUrl(item, type, maxWidth = WIDE_PX, tag = tag), wide = true)
        imageTags?.get(ImageType.THUMB)?.let { return wide(id, ImageType.THUMB, it) }
        parentThumbItemId?.let { parent -> parentThumbImageTag?.let { return wide(parent, ImageType.THUMB, it) } }
        backdropImageTags?.firstOrNull()?.let { return wide(id, ImageType.BACKDROP, it) }
        parentBackdropItemId?.let { parent -> parentBackdropImageTags?.firstOrNull()?.let { return wide(parent, ImageType.BACKDROP, it) } }
        imageTags?.get(ImageType.PRIMARY)?.let { tag ->
            // An episode's own picture is a 16:9 still; a movie's is a poster.
            val isWide = (primaryImageAspectRatio ?: 0.0) >= WIDE_RATIO
            return Art(api.imageApi.getItemImageUrl(id, ImageType.PRIMARY, maxWidth = if (isWide) WIDE_PX else POSTER_PX, tag = tag), isWide)
        }
        seriesId?.let { series -> seriesPrimaryImageTag?.let { return Art(api.imageApi.getItemImageUrl(series, ImageType.PRIMARY, maxWidth = POSTER_PX, tag = it), wide = false) } }
        return null
    }

    private fun AuthenticationResult.login(server: JellyfinServer, deviceId: String): JellyfinLogin {
        val user = user ?: throw JellyfinException(JellyfinProblem.Refused)
        val token = accessToken ?: throw JellyfinException(JellyfinProblem.Refused)
        return JellyfinLogin(
            address = server.address,
            serverId = serverId ?: user.serverId.orEmpty(),
            serverName = server.name,
            userId = user.id.toString(),
            userName = user.name.orEmpty(),
            token = token,
            deviceId = deviceId,
        )
    }

    private fun api(address: String, deviceId: String, token: String? = null): ApiClient =
        jellyfin.createApi(baseUrl = address, accessToken = token, deviceInfo = DeviceInfo(id = deviceId, name = deviceName))

    private fun api(login: JellyfinLogin): ApiClient = api(login.address, login.deviceId, login.token)

    /** Runs [block] off the main thread, in the SDK's errors' place a [JellyfinException]; [on401] is what "not allowed" means for it. */
    private suspend fun <T> call(on401: JellyfinProblem, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (already: JellyfinException) {
            throw already
        } catch (status: InvalidStatusException) {
            throw JellyfinException(
                when (status.status) {
                    HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> on401
                    in HTTP_SERVER_ERRORS -> JellyfinProblem.CantReach
                    else -> JellyfinProblem.Refused
                },
                status,
            )
        } catch (garbled: InvalidContentException) {
            throw JellyfinException(JellyfinProblem.Refused, garbled)
        } catch (network: ApiClientException) {
            // Timeouts, refused connections, unknown hosts, certificates.
            throw JellyfinException(JellyfinProblem.CantReach, network)
        }
    }

    companion object {
        init {
            // The SDK logs with kotlin-logging, which looks for SLF4J unless told to use Android's
            // own log. Set before any of the SDK's classes load.
            System.setProperty("kotlin-logging-to-android-native", "true")
            System.setProperty("kotlin-logging.logStartupMessage", "false")
        }

        /** The name Plasma goes by on the server's list of devices and in its logs. */
        const val CLIENT = "Plasma"

        /** How long discovery waits for each answer. */
        private const val DISCOVERY_MS = 1_500

        /** Continue Watching shows 20 items at most, with Plasma's own (5.19). */
        private const val LIMIT = 20

        /** Episodes looked at to tell when each series was last watched. */
        private const val WATCHED_LIMIT = 50

        private const val TICKS_PER_MS = 10_000L

        /** Pictures at the size Home draws them: 400 × 225 thumbnails, 124 × 186 posters, at twice the density. */
        private const val WIDE_PX = 800
        private const val POSTER_PX = 400

        /** An item's own picture this much wider than tall is 16:9 art rather than a poster. */
        private const val WIDE_RATIO = 1.2

        private val FIELDS = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)
        private val IMAGES = listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP)

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val HTTP_SERVER_ERRORS = 500..599

        private fun newDeviceId(): String = UUID.randomUUID().toString()

        /** The server's host, for a server that gives no name. */
        internal fun hostOf(address: String): String = runCatching { URI(address).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: address

        /** The SDK reads the server's dates into this TV's time zone; null for the server's "never". */
        private fun DateTime.instant(): Instant? = if (this == DateTime.MIN) null else atZone(ZoneId.systemDefault()).toInstant()
    }
}
