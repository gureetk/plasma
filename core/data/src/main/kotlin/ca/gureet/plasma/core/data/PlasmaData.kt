// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import ca.gureet.plasma.core.database.PlasmaDatabase
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.network.PlasmaHttp
import okhttp3.OkHttpClient
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

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
    val sources = SourceRepository(database, vault, http, clock)
    val importer = SourceImporter(database, sources, settings, vault, http, clock)
    val channels = ChannelRepository(database, settings, vault, clock)
    val guide = GuideRepository(database)
    val reminders = ReminderRepository(database, clock)

    /**
     * Refreshes what's old (brief 5.1 and 5.3): channels every 12 hours, the guide once a day.
     * A failure is noted on the source and the old data stays.
     */
    suspend fun refreshStale() {
        val now = clock.now()
        for (source in sources.all().filter { it.enabled }) {
            if (source.channelsRefreshedAt.olderThan(now, CHANNELS_EVERY)) attempt { importer.importChannels(source.id) }
            if (source.guideRefreshedAt.olderThan(now, GUIDE_EVERY)) attempt { importer.importGuide(source.id) }
        }
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
        val CHANNELS_EVERY: Duration = Duration.ofHours(12)
        val GUIDE_EVERY: Duration = Duration.ofHours(24)

        fun create(context: Context, userAgent: String, clock: PlasmaClock = PlasmaClock.System): PlasmaData = PlasmaData(
            database = PlasmaDatabase.open(context),
            http = PlasmaHttp.client(userAgent),
            settings = SettingsStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }),
            secrets = KeystoreSecretBox(),
            clock = clock,
        )
    }
}
