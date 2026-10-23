// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.app.Application
import android.content.Context
import ca.gureet.plasma.core.data.PlasmaData
import ca.gureet.plasma.core.data.ReminderClock
import ca.gureet.plasma.core.data.SourceType
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.network.UserAgents
import ca.gureet.plasma.live.LiveWorld
import ca.gureet.plasma.live.liveWorld
import ca.gureet.plasma.player.AudioRoutes
import ca.gureet.plasma.player.DisplayFrameRates
import ca.gureet.plasma.player.PlaybackPrefs
import ca.gureet.plasma.player.PlayerManager
import ca.gureet.plasma.player.StreamTarget
import ca.gureet.plasma.player.vlc.VlcEngine
import ca.gureet.plasma.source.xtream.StreamFormat
import ca.gureet.plasma.stream.ConnectionBudget
import ca.gureet.plasma.stream.StreamHub
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlasmaApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.standby.start()
        graph.reminderClock.start()
    }
}

val Context.graph: AppGraph get() = (applicationContext as PlasmaApplication).graph

/**
 * Everything the app shares, built once (brief 6.2, "app: navigation, DI"): the data layer, the
 * stream hub, the player, and image loading. Plain constructors stand in for a DI library.
 */
class AppGraph(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val clock: PlasmaClock = PlasmaClock.System
    val data: PlasmaData = PlasmaData.create(context, UserAgents.vlc(), clock)

    /** Until a source is added, Plasma shows the boards' sample world; null while it's being checked. */
    val hasSources: StateFlow<Boolean?> = data.sources.sources.map { it.isNotEmpty() }.stateIn(scope, SharingStarted.Eagerly, null)

    /** What's chosen in Settings; null until it's been read. */
    val storedChoices: StateFlow<Map<String, Int>?> = data.settings.choices.stateIn(scope, SharingStarted.Eagerly, null)

    val choices: SettingsChoices get() = SettingsChoices(storedChoices.value.orEmpty())

    /** The channels, guide and lists the live screens draw from. */
    val world: StateFlow<LiveWorld?> = liveWorld(data, clock).stateIn(scope, SharingStarted.WhileSubscribed(STOP_SHARING_MS), null)

    val images: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { data.http })) }
            .build()
    }

    private val hub: StreamHub by lazy { StreamHub(data.http) }

    /** libVLC loads with the first live picture, so the sample world never pays for it. */
    private val playerSlot = lazy { PlayerManager(VlcEngine(context), hub, ::resolve, ::playbackPrefs, scope, DisplayFrameRates) }
    val player: PlayerManager by playerSlot

    val standby = Standby(context, { choices }, { if (playerSlot.isInitialized()) player else null })

    val reminderClock = ReminderClock(data.reminders, clock, scope) { standby.isOnScreen }

    /**
     * Remind Me, or Cancel Reminder, on a programme by the id the screens give it:
     * "channelId@start in epoch seconds" (brief 5.7).
     */
    fun remind(channelId: String, programmeId: String, on: Boolean) {
        scope.launch {
            attempt {
                if (!on) {
                    data.reminders.cancel(programmeId)
                    return@attempt
                }
                val start = programmeId.substringAfterLast('@').toLongOrNull()?.let(Instant::ofEpochSecond) ?: return@attempt
                val epgKey = data.channels.get(channelId)?.epgKey ?: return@attempt
                val programme = data.guide.forChannel(epgKey, start, start.plusSeconds(1))
                    .firstOrNull { it.start.epochSecond == start.epochSecond } ?: return@attempt
                data.reminders.set(channelId, programme.title, programme.start, programme.end)
            }
        }
    }

    /** Settings › Playback and Audio, read each time a channel opens (brief 4.27 and 4.28). */
    private fun playbackPrefs(): PlaybackPrefs {
        val chosen = choices
        val output = when (chosen.audioOutput) {
            // Auto passes through what the TV or sound system takes, and decodes the rest (5.4.6).
            AudioOutput.Auto -> if (AudioRoutes.current(context).takesPassthrough) AudioOutput.Passthrough else AudioOutput.DecodeToPcm
            else -> chosen.audioOutput
        }
        return PlaybackPrefs(
            buffer = chosen.buffer,
            decoding = chosen.decoding,
            deinterlacing = chosen.deinterlacing,
            audioOutput = output,
            frameRate = chosen.frameRate,
            throughHub = chosen.throughHub,
        )
    }

    /** A channel's stream, built at play time from its source (brief 6.5). */
    private suspend fun resolve(channelId: String): StreamTarget? = withContext(Dispatchers.IO) {
        val channel = data.channels.get(channelId) ?: return@withContext null
        val source = data.sources.get(channel.sourceId) ?: return@withContext null
        when (source.type) {
            SourceType.Xtream -> {
                val format = StreamFormat.entries.firstOrNull { it.extension == source.streamFormat } ?: StreamFormat.Ts
                StreamTarget(
                    channelId = channelId,
                    url = data.sources.xtreamClient(source.id).liveUrl(channel.key, format),
                    headers = listOfNotNull(source.userAgent?.let { USER_AGENT to it }).toMap(),
                    // HLS manages its own segments, so it goes straight to the engine (5.4.2).
                    throughHub = format == StreamFormat.Ts,
                    budget = source.maxConnections?.let { ConnectionBudget(source.id, it) },
                )
            }
            SourceType.M3u -> {
                val stream = data.channels.streamOf(channelId) ?: return@withContext null
                StreamTarget(
                    channelId = channelId,
                    url = stream.url,
                    // The playlist's own headers for the channel win over the source's (5.4.11).
                    headers = listOfNotNull(
                        (stream.userAgent ?: source.userAgent)?.let { USER_AGENT to it },
                        stream.referrer?.let { REFERER to it },
                    ).toMap(),
                    // HLS, and udp://, rtmp:// and the like, go straight to the engine; an address that
                    // only turns out to be HLS is handed over by the hub when it finds out.
                    throughHub = stream.url.isHttp() && !stream.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
                )
            }
        }
    }

    /** The guide follows a new source's channels, in the background (brief 6.4). */
    fun importGuide(sourceId: String) {
        scope.launch { attempt { data.importer.importGuide(sourceId) } }
    }

    /** Refresh on the Guide's out-of-date line (brief 4.8): every source's guide again. */
    fun refreshGuides() {
        scope.launch { data.sources.all().forEach { attempt { data.importer.importGuide(it.id) } } }
    }

    /** Refresh Now on a source (brief 4.20): its channels, then its guide. */
    fun refresh(sourceId: String) {
        scope.launch {
            if (attempt { data.importer.importChannels(sourceId) }) attempt { data.importer.importGuide(sourceId) }
        }
    }

    /** Runs an import whose failure the importer notes on the source, for Settings to show. */
    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        false
    }

    /** Plasma is in front again: what's old is refreshed (5.1) and the channel that was on plays again. */
    fun onActivityStart() {
        scope.launch { data.refreshStale() }
        standby.onActivityStart()
    }

    /** Plasma left the front, or the TV turned off (5.20). */
    fun onActivityStop() = standby.onActivityStop()

    private fun String.isHttp() = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private companion object {
        const val STOP_SHARING_MS = 5_000L
        const val USER_AGENT = "User-Agent"
        const val REFERER = "Referer"
    }
}
