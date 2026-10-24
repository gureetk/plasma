// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.network.PlasmaHttp
import okhttp3.OkHttpClient
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The data layer in one place, built once by the app: the database, the HTTP client, settings,
 * and the repositories the screens use.
 */
class PlasmaData(
    val database: PlasmaDatabase,
    val http: OkHttpClient,
    val settings: SettingsStore,
    secrets: SecretBox,
    private val clock: PlasmaClock,
) {
    private val vault = Vault(secrets, settings)
    private val adbKeyLock = Mutex()

    /**
     * Plasma's ADB key (brief 5.13), as PKCS #8 bytes: [make] gives a new one the first time, and
     * it's kept sealed by the Keystore, so the TV, once paired, knows Plasma afterwards.
     */
    suspend fun adbKey(make: () -> ByteArray): ByteArray = adbKeyLock.withLock {
        settings.sealedAdbKey()?.let { sealed -> return@withLock Base64.getDecoder().decode(vault.open(sealed)) }
        make().also { fresh -> settings.setSealedAdbKey(vault.seal(Base64.getEncoder().encodeToString(fresh))) }
    }

    // Imports and Duplicate Numbers both number channels, so they take turns.
    private val numberingLock = Mutex()
    val sources = SourceRepository(database, vault, http, clock)
    val importer = SourceImporter(database, sources, settings, vault, http, clock, numberingLock)
    val numbering = ChannelNumbering(database, settings, clock, numberingLock)
    val channels = ChannelRepository(database, settings, vault, clock)
    val guide = GuideRepository(database)
    val reminders = ReminderRepository(database, clock)
    val search = SearchRepository(database, clock)
    val archive = ArchiveRepository(sources, channels, guide, clock)
    val memory = SourceMemory(database, clock)
    val playback = ChannelPlaybackRepository(database, clock)
    val vod = VodRepository(database, sources, clock)
    val jellyfin = JellyfinAccounts(database, secrets, clock)

    /**
     * Refreshes what's old (brief 5.1, 5.3 and 6.4): channels as often as Portals & Playlists ›
     * Refresh says (every 12 hours by default), or a playlist's own Advanced, the guide once a day,
     * and movies and series once a week. A failure is noted on the source and the old data stays.
     */
    suspend fun refreshStale() {
        val now = clock.now()
        val usual = SettingsChoices(settings.choices.first()).refreshHours
        for (source in sources.all().filter { it.enabled }) {
            val every = channelsEvery(source.refreshHours ?: usual, source.channelsRefreshedAt)
            if (every != null && source.channelsRefreshedAt.olderThan(now, every)) attempt { importer.importChannels(source.id) }
            if (source.guideRefreshedAt.olderThan(now, GUIDE_EVERY)) attempt { importer.importGuide(source.id) }
            if (source.type.hasCatalog && source.vodRefreshedAt.olderThan(now, VOD_EVERY)) attempt { importer.importVod(source.id) }
        }
    }

    /**
     * How often channels refresh by themselves, every [hours]; null for only when asked (0), unless
     * they've never loaded.
     */
    private fun channelsEvery(hours: Int, refreshedAt: Instant?): Duration? = when (hours) {
        0 -> if (refreshedAt == null) Duration.ZERO else null
        else -> Duration.ofHours(hours.toLong())
    }

    private fun Instant?.olderThan(now: Instant, age: Duration) = this == null || Duration.between(this, now) >= age

    /** Runs an import whose failure the importer has already noted on the source. */
    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // Noted on the source; the old data stays.
        }
    }

    companion object {
        val GUIDE_EVERY: Duration = Duration.ofHours(24)
        val VOD_EVERY: Duration = Duration.ofDays(7)

        fun create(context: Context, userAgent: String, clock: PlasmaClock = PlasmaClock.System): PlasmaData = PlasmaData(
            database = PlasmaDatabase.open(context),
            http = PlasmaHttp.client(userAgent),
            settings = SettingsStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }),
            secrets = KeystoreSecretBox(),
            clock = clock,
        )
    }
}
