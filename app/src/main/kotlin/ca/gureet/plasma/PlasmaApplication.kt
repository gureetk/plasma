// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import ca.gureet.plasma.adb.AdbKey
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.ChannelLineup
import ca.gureet.plasma.core.data.ChannelNumbering
import ca.gureet.plasma.core.data.CopyState
import ca.gureet.plasma.core.data.EngineReason
import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.data.PlasmaData
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.data.ProfileKind
import ca.gureet.plasma.core.data.Programme
import ca.gureet.plasma.core.data.ProgrammeToRecord
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingKind
import ca.gureet.plasma.core.data.RecordingProblems
import ca.gureet.plasma.core.data.RecordingRepository
import ca.gureet.plasma.core.data.RecordingStatus
import ca.gureet.plasma.core.data.RecordingStore
import ca.gureet.plasma.core.data.RememberedSource
import ca.gureet.plasma.core.data.ReminderClock
import ca.gureet.plasma.core.data.Source
import ca.gureet.plasma.core.data.SourceType
import ca.gureet.plasma.core.data.TuneMemory
import ca.gureet.plasma.core.design.RemoteMirror
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.model.AdultWords
import ca.gureet.plasma.core.model.AfterCopying
import ca.gureet.plasma.core.model.AudioOutput
import ca.gureet.plasma.core.model.BufferSize
import ca.gureet.plasma.core.model.PlasmaClock
import ca.gureet.plasma.core.model.RecordTo
import ca.gureet.plasma.core.model.RewindBuffer
import ca.gureet.plasma.core.model.SaveTo
import ca.gureet.plasma.core.model.SettingKeys
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.core.model.SpaceLow
import ca.gureet.plasma.core.network.UserAgents
import ca.gureet.plasma.feature.player.TrackNames
import ca.gureet.plasma.feature.profiles.PinTries
import ca.gureet.plasma.feature.settings.ChannelSoundInfo
import ca.gureet.plasma.feature.settings.DuplicateNumbersState
import ca.gureet.plasma.feature.settings.ProfileSettings
import ca.gureet.plasma.feature.settings.SourceSettings
import ca.gureet.plasma.integrations.watchnext.PlayNext
import ca.gureet.plasma.integrations.watchnext.PlayNextItem
import ca.gureet.plasma.launcher.AppsLayout
import ca.gureet.plasma.launcher.HomeReason
import ca.gureet.plasma.launcher.TvApps
import ca.gureet.plasma.launcher.TvInputs
import ca.gureet.plasma.live.ChannelSummary
import ca.gureet.plasma.live.Lists
import ca.gureet.plasma.live.LiveWorld
import ca.gureet.plasma.live.LocalCatchUpNow
import ca.gureet.plasma.live.RecordingChase
import ca.gureet.plasma.live.channelSummary
import ca.gureet.plasma.live.duplicateNumbersState
import ca.gureet.plasma.live.engineOf
import ca.gureet.plasma.live.hdrEngine
import ca.gureet.plasma.live.liveWorld
import ca.gureet.plasma.live.packageOfKey
import ca.gureet.plasma.live.sourceHeaders
import ca.gureet.plasma.live.throughUdpxy
import ca.gureet.plasma.live.toInfo
import ca.gureet.plasma.live.toPlayback
import ca.gureet.plasma.live.vodEngine
import ca.gureet.plasma.player.AudioCodec
import ca.gureet.plasma.player.AudioRoutes
import ca.gureet.plasma.player.ChannelAudio
import ca.gureet.plasma.player.DisplayFrameRates
import ca.gureet.plasma.player.EngineId
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.PictureSize
import ca.gureet.plasma.player.PlaybackPrefs
import ca.gureet.plasma.player.PlayerEvent
import ca.gureet.plasma.player.PlayerManager
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.player.StreamResolver
import ca.gureet.plasma.player.StreamTarget
import ca.gureet.plasma.player.TrackPrefs
import ca.gureet.plasma.player.audioCodecOf
import ca.gureet.plasma.player.media3.Media3Engine
import ca.gureet.plasma.player.mpv.MpvEngine
import ca.gureet.plasma.player.pick
import ca.gureet.plasma.player.vlc.VlcEngine
import ca.gureet.plasma.recording.JellyfinNames
import ca.gureet.plasma.recording.KeyframeIndex
import ca.gureet.plasma.recording.LocalCatchUp
import ca.gureet.plasma.recording.LocalCatchUpPlan
import ca.gureet.plasma.recording.NetworkCopier
import ca.gureet.plasma.recording.Recorder
import ca.gureet.plasma.recording.RecordingAlarms
import ca.gureet.plasma.recording.RecordingFiles
import ca.gureet.plasma.recording.RecordingHost
import ca.gureet.plasma.recording.ShareException
import ca.gureet.plasma.recording.SmbShares
import ca.gureet.plasma.recording.SpoolSource
import ca.gureet.plasma.recording.SpoolView
import ca.gureet.plasma.source.xtream.StreamFormat
import ca.gureet.plasma.stream.CatchUpRing
import ca.gureet.plasma.stream.ConnectionBudget
import ca.gureet.plasma.stream.ConnectionLimitReached
import ca.gureet.plasma.stream.FileSession
import ca.gureet.plasma.stream.GrowingFileReader
import ca.gureet.plasma.stream.HlsReader
import ca.gureet.plasma.stream.HubConfig
import ca.gureet.plasma.stream.HubSession
import ca.gureet.plasma.stream.LocalSession
import ca.gureet.plasma.stream.RewindMode
import ca.gureet.plasma.stream.RewindSettings
import ca.gureet.plasma.stream.StreamFailure
import ca.gureet.plasma.stream.StreamHub
import ca.gureet.plasma.stream.UpstreamRequest
import ca.gureet.plasma.stream.UpstreamState
import ca.gureet.plasma.stream.tablesOf
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import kotlinx.coroutines.channels.Channel as WorkQueue

/** A notice for the corner of the screen (brief 4.35), with its icon, for [millis]. */
data class Notice(val text: String, val icon: PlasmaIcons = PlasmaIcons.SwitchSource, val millis: Long = SHORT_MS) {
    companion object {
        const val SHORT_MS = 4_000L

        /** For a notice with something to do in it. */
        const val LONG_MS = 8_000L
    }
}

/**
 * The drive the rewind buffer goes to (Settings › Rewind and Local Catch-up, 4.31): a USB drive or
 * the TV, its room [free] of [total], and what the rewind buffer takes now.
 */
data class StorageNow(val onUsb: Boolean, val free: Long, val total: Long, val rewindBytes: Long)

/** A drive, whether it's a USB drive, and its room: [free] of [total]. */
data class DriveRoom(val onUsb: Boolean, val free: Long, val total: Long)

/** How Test Connection went on a source (brief 4.20). */
sealed interface SourceTest {
    data object Testing : SourceTest

    data class Passed(val pingMs: Int) : SourceTest

    data class Failed(val error: Exception) : SourceTest
}

/** Plasma opened by Home or at power-on (brief 5.13): each opening is its own, even for the same reason. */
class HomeOpening(val reason: HomeReason)

class PlasmaApplication : Application(), RecordingHost.Owner {
    lateinit var graph: AppGraph
        private set

    override val recordingHost: RecordingHost get() = graph.recordingHost

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.standby.start()
        graph.reminderClock.start()
        graph.reminderOverlay.start()
        graph.updates.start()
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

    /**
     * Every channel as its source lists it, and with the same channel from several sources made
     * one (brief 5.1), worked out once for the screens, the player and Settings.
     */
    private val lineup: SharedFlow<ChannelLineup> = data.channels.lineup
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /** Lock Adult Groups (5.12): the words that make a group adult while it's on; null while it's off. */
    private val adultWords: Flow<AdultWords?> = combine(
        storedChoices.filterNotNull().map { SettingsChoices(it).lockAdultGroups }.distinctUntilChanged(),
        data.settings.adultWords,
    ) { on, words -> words.takeIf { on } }

    /** Which groups' channels need the PIN (5.12), for the channels and Settings › Parental Controls. */
    val groupLocks: Flow<GroupLocks> = combine(data.channels.groups, adultWords, ::GroupLocks).flowOn(Dispatchers.Default)

    /**
     * The channels the screens show, those in a locked group locked too (5.12). Until there's a
     * parental PIN nothing is locked; the channels and groups locked stay so for when there's one again.
     */
    private val channels: Flow<List<Channel>> = combine(lineup, data.settings.pinSet, groupLocks, data.profiles.current) { lineup, pinSet, groups, profile ->
        val shown = when {
            !pinSet -> lineup.shown.map { if (it.locked) it.copy(locked = false) else it }
            groups.lockedIds.isEmpty() -> lineup.shown
            else -> lineup.shown.map { if (!it.locked && it.groupId in groups.lockedIds) it.copy(locked = true, lockedWithGroup = true) else it }
        }
        // A kids profile never sees what's locked (5.12).
        if (profile.kind == ProfileKind.Kids) shown.filter { !it.locked } else shown
    }.flowOn(Dispatchers.Default)

    /**
     * Age Limit (5.12), while there's a PIN, and a kids profile's ages (5.11): programs rated for
     * the lower of the two or older need the parental PIN.
     */
    private val ratingLimit: Flow<Int?> = combine(storedChoices.filterNotNull(), data.settings.pinSet, data.profiles.current) { chosen, pinSet, profile ->
        val parental = if (pinSet) SettingsChoices(chosen).ageLimit else null
        val kids = profile.maxAge?.takeIf { profile.kind == ProfileKind.Kids }?.plus(1)
        listOfNotNull(parental, kids).minOrNull()
    }.distinctUntilChanged()

    /** The channels that always need the PIN, by number, for Settings › Parental Controls (5.12). */
    val lockedChannels: Flow<List<Channel>> = lineup
        .map { lineup -> lineup.shown.filter { it.locked }.sortedBy { it.number } }
        .distinctUntilChanged()

    /**
     * Every channel's number and the name it shows under, those merged into another and hidden
     * ones too: Change Number takes only a number none has (brief 5.2).
     */
    val numberOwners: Flow<Map<Int, String>> = lineup
        .map { lineup -> lineup.listed.associate { it.number to it.name } + lineup.shown.associate { it.number to it.name } }
        .flowOn(Dispatchers.Default)

    private val _localWindows = MutableStateFlow<Map<String, LongRange>>(emptyMap())

    /** What local catch-up holds now (brief 5.5), by channel: from when to when, in Unix ms. */
    val localWindows: StateFlow<Map<String, LongRange>> = _localWindows.asStateFlow()

    /** The channels, guide and lists the live screens draw from. */
    val world: StateFlow<LiveWorld?> = liveWorld(data, clock, channels, combine(data.settings.localCatchUp, localWindows, ::LocalCatchUpNow), ratingLimit)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_SHARING_MS), null)

    /**
     * Duplicate Numbers (brief 4.30) as the channels are. The fix rule is read as the channels
     * come: it changes first, so the channels it moves come with it.
     */
    val duplicateNumbers: Flow<DuplicateNumbersState> = combine(lineup, data.sources.sources) { lineup, sources ->
        duplicateNumbersState(lineup, sources, data.settings.numberingNow().duplicateRule)
    }.flowOn(Dispatchers.Default)

    /** What Settings › Channels counts (4.29): merged channels, duplicates fixed, hidden channels and groups. */
    val channelSummary: Flow<ChannelSummary> = combine(lineup, duplicateNumbers, data.channels.groups) { lineup, duplicates, groups ->
        channelSummary(lineup, duplicates, groups.count { it.hidden })
    }.flowOn(Dispatchers.Default).distinctUntilChanged()

    val images: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { data.http })) }
            .build()
    }

    private val hubMade = lazy {
        // A rewind buffer left by a Plasma that stopped suddenly is no use now.
        for (storage in listOf(context.cacheDir) + context.getExternalFilesDirs(null).drop(1).filterNotNull()) {
            runCatching { File(storage, REWIND_FOLDER).deleteRecursively() }
        }
        StreamHub(data.http, HubConfig(rewind = ::rewindSettings))
    }
    private val hub: StreamHub by hubMade

    /**
     * The rewind buffer as Settings has it (brief 5.5): on the USB drive when Save To says so and
     * one is there, otherwise on the TV, never taking more than half the room left. A USB drive
     * isn't a volume Android allocates on, so the room is what's free on it.
     */
    @SuppressLint("UsableSpace")
    private fun rewindSettings(): RewindSettings {
        val chosen = choices.rewindBuffer
        if (chosen == RewindBuffer.Off) return RewindSettings.Off
        val usb = usbStorage()
        val folder = rewindFolder(usb)
        return RewindSettings(
            mode = if (chosen.always) RewindMode.Always else RewindMode.WhenPaused,
            keepMs = chosen.hours * HOUR_MS,
            maxBytes = minOf(choices.storageLimitBytes(onTv = usb == null), folder.usableSpace / 2),
            folder = folder,
        )
    }

    /** Where the rewind buffer goes: app storage on a USB drive, or the TV's cache. */
    private fun rewindFolder(usb: File? = usbStorage()): File = File(usb ?: context.cacheDir, REWIND_FOLDER)

    /**
     * Plasma's storage on a USB drive, when Save To says so and one is there (Android lists it
     * after the TV's own).
     */
    private fun usbStorage(): File? = if (choices.saveTo == SaveTo.UsbDrive) usbVolume() else null

    /** Plasma's own storage on a USB drive, when one is connected: Android lists it after the TV's own. */
    private fun usbVolume(): File? {
        // Android answers this from another process: asked once every few seconds at most.
        val now = SystemClock.elapsedRealtime()
        usbLooked?.let { (at, found) -> if (now - at < USB_LOOK_MS) return found }
        val found = context.getExternalFilesDirs(null).drop(1).firstOrNull { it != null && Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
        usbLooked = now to found
        return found
    }

    /** When the USB drive was last looked for, and what was found. */
    @Volatile private var usbLooked: Pair<Long, File?>? = null

    /**
     * Settings › Rewind and Local Catch-up's Storage (4.31): the drive the rewind buffer goes to,
     * its room, and what the buffer takes now.
     */
    @SuppressLint("UsableSpace")
    fun storage(): StorageNow {
        val usb = usbStorage()
        val drive = usb ?: context.cacheDir
        return StorageNow(
            onUsb = usb != null,
            free = drive.usableSpace,
            total = drive.totalSpace,
            rewindBytes = if (hubMade.isInitialized()) hub.rewindBytes() else 0,
        )
    }

    /**
     * Recording (brief 5.6): what the recording service needs from the rest of Plasma. The alarm
     * for the next recording follows what's scheduled.
     */
    val recordingHost: RecordingHost = object : RecordingHost {
        override val recordings: RecordingStore get() = data.recordings
        override val zone: ZoneId get() = clock.zone

        override suspend fun openForRecording(channelId: String): RecordingHost.Opened = this@AppGraph.openForRecording(channelId)

        override fun recordingFolder(): File = this@AppGraph.recordingFolder()

        override suspend fun makeRoom(folder: File): Boolean = this@AppGraph.makeRoom(folder)

        override val localCatchUp: Flow<LocalCatchUpPlan> get() = localPlan

        override suspend fun copyTo(): RecordingHost.CopyTo? = this@AppGraph.copyTo()

        // Record To › Network folder, once a folder's set up (5.6).
        override suspend fun recordStraightTo(): RecordingHost.CopyTo? = if (choices.recordTo == RecordTo.NetworkFolder) this@AppGraph.copyTo() else null

        override suspend fun fetch(url: String): ByteArray? = fetchArtwork(url)

        override suspend fun copied(recording: Recording, freeBytes: Long?) = this@AppGraph.copied(recording.id, freeBytes)

        override suspend fun copyWaiting(folder: NetworkFolder) = shareAway(folder)
    }

    // ---- Copy to Network Folder (brief 5.6) ---------------------------------------------------

    /** Settings › Recording › Copy to Network Folder's setup (4.34). */
    val networkFolder: NetworkFolderLink by lazy { NetworkFolderLink(context, this) }

    /** Where finished recordings are copied, named for Jellyfin as Settings says; null while it's off. */
    private suspend fun copyTo(): RecordingHost.CopyTo? {
        val folder = data.networkFolders.get() ?: return null
        return RecordingHost.CopyTo(folder, SettingsChoices(storedChoices.filterNotNull().first()).jellyfinNames)
    }

    /** The guide's picture at [url], for a copy's artwork; null when it can't be had, or is too big to be one. */
    private suspend fun fetchArtwork(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            data.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val source = response.body.source()
                when {
                    !response.isSuccessful -> null
                    source.request(MAX_ARTWORK_BYTES + 1) -> null
                    else -> source.readByteArray()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            null
        }
    }

    /** "Can't reach nas.home.lan" was said, and isn't said again until a copy gets there. */
    @Volatile private var shareAwaySaid = false

    /**
     * [id]'s copy is there: the share's room, Jellyfin asked to look for it, and with After
     * Copying's Delete from the Drive, the drive's file gone (5.6).
     */
    private suspend fun copied(id: String, freeBytes: Long?) {
        shareAwaySaid = false
        if (freeBytes != null) attempt { data.networkFolders.setFree(freeBytes) }
        if (choices.afterCopying == AfterCopying.Delete) attempt { dropLocal(id) }
        val folder = data.networkFolders.get() ?: return
        // The libraries to scan: those with Plasma's folders, or with the network folder itself.
        val own = folder.path.trim('/').substringAfterLast('/').ifEmpty { folder.share }
        jellyfin.recordingCopied(setOf(JellyfinNames.TV, JellyfinNames.MOVIES, JellyfinNames.OTHER, own))
    }

    /** The recording last played from the network folder: the hub serves one at a time. */
    @Volatile private var sharePlaying: FileSession? = null

    /** The recordings being written now, by id; null until they're read. */
    private val writing: StateFlow<Set<String>?> = data.recordings.recordings
        .map { list -> list.filter { it.status == RecordingStatus.Recording }.mapTo(HashSet()) { it.id }.toSet() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The recording last played as it's written: the hub serves one at a time. */
    @Volatile private var chasePlaying: LocalSession? = null

    /**
     * A recording still being written, played as it grows (chase play, brief 5.6): the hub reads
     * its file from a keyframe, the stream's PAT and PMT first, and waits at the end for more until
     * the recording's done; a seek opens it again at the keyframe its index has for then.
     */
    private suspend fun chaseTarget(key: String, recording: Recording, file: File): StreamTarget {
        val id = recording.id
        val session = hub.serve { at ->
            val from = at ?: 0
            // Looked for each time: a recording that's only just started may not have had them yet.
            val tables = if (from > 0) tablesOf(file) else null
            // Gone, as when nothing could be recorded: Not Found.
            if (file.isFile) GrowingFileReader(file, from, tables, done = { writing.value?.let { id !in it } ?: false }) else null
        }
        chasePlaying?.close()
        chasePlaying = session
        // Its time counts from its first keyframe, as it does once it plays from the file.
        val startedAt = withContext(Dispatchers.IO) { RecordingFiles.keyframeAt(file, 0)?.second }
            ?: recording.recordedFrom?.toEpochMilli()
            ?: recording.from.toEpochMilli()
        return StreamTarget(channelId = key, url = session.url, throughHub = false, chase = RecordingChase(session, file, startedAt))
    }

    /**
     * A recording written straight to the network folder (Record To › Network folder, brief 5.6),
     * played as it's written, or before all of it's there: what this TV keeps of it from its
     * [spool], and what's older from the file on the share, which the hub reads from a keyframe on
     * as for chase play. Null when it's on a server that isn't the network folder's.
     */
    private suspend fun spoolTarget(key: String, recording: Recording, spool: File): StreamTarget? {
        val place = recording.networkPath?.let(NetworkFolder::placeOf) ?: return null
        val folder = data.networkFolders.get()?.takeIf { it.host.equals(place.host, ignoreCase = true) } ?: return null
        val id = recording.id
        val view = SpoolView(spool)
        // Unfinished, or finished meanwhile.
        val paths = listOf(place.path + NetworkCopier.PARTIAL, place.path)
        val session = hub.serve { at ->
            val from = at ?: 0
            val source = SpoolSource(view, { SmbShares.openGrowing(folder, place.share, paths) })
            GrowingFileReader(source, from, if (from > 0) tablesOf(source) else null, done = { writing.value?.let { id !in it } ?: false })
        }
        chasePlaying?.close()
        chasePlaying = session
        val startedAt = withContext(Dispatchers.IO) { RecordingFiles.keyframeIn(view.index, 0)?.second }
            ?: recording.recordedFrom?.toEpochMilli()
            ?: recording.from.toEpochMilli()
        return StreamTarget(channelId = key, url = session.url, throughHub = false, chase = RecordingChase(session, view.index, startedAt) { view.writtenAt })
    }

    /**
     * A recording whose file on the drive has gone, from its copy on the network folder (brief
     * 5.6): the hub reads it from the share and serves it with byte ranges, so every engine can
     * seek. Signed in to as the network folder is; null when it's on another server, or can't be
     * reached.
     */
    private suspend fun shareTarget(key: String, recording: Recording): StreamTarget? {
        val place = recording.networkPath?.let(NetworkFolder::placeOf) ?: return null
        val folder = data.networkFolders.get()?.takeIf { it.host.equals(place.host, ignoreCase = true) } ?: return null
        val source = withContext(Dispatchers.IO) {
            try {
                SmbShares.openFile(folder, place.share, place.path)
            } catch (failed: ShareException) {
                null
            }
        } ?: return null
        val session = hub.serveFile(source)
        sharePlaying?.close()
        sharePlaying = session
        return StreamTarget(channelId = key, url = session.url, throughHub = false)
    }

    /** The network folder can't be reached: said once (4.35), with where recordings wait meanwhile. */
    private fun shareAway(folder: NetworkFolder) {
        if (shareAwaySaid) return
        shareAwaySaid = true
        val text = context.getString(if (recordingDrive().onUsb) R.string.share_away_usb else R.string.share_away_tv, folder.host)
        _notices.tryEmit(Notice(text, PlasmaIcons.NetworkFolder, SHARE_AWAY_MS))
    }

    /** Copy to Network Folder › Turn Off (4.34): the folder and its password are forgotten. Nothing on it goes. */
    fun forgetNetworkFolder() {
        scope.launch {
            attempt {
                val folder = data.networkFolders.get() ?: return@attempt
                data.networkFolders.forget()
                // What was waiting to go there isn't any more.
                data.recordings.dropCopies()
                _notices.tryEmit(Notice(context.getString(R.string.network_folder_forgotten, folder.host), PlasmaIcons.NetworkFolder))
            }
        }
    }

    /** A recording finished outside the service, as Keep as Recording's: it's copied too, when there's a folder (5.6). */
    private suspend fun copySoon(id: String) {
        if (data.networkFolders.get() == null) return
        data.recordings.setCopy(id, CopyState.Waiting)
        RecordingAlarms.startService(context)
    }

    /**
     * When Space Runs Low (Settings › Recording, 4.34): recordings with a copy on the network folder
     * go from [folder]'s drive first, the first copied first, as nothing's lost (5.6); then the
     * oldest watched recordings, those kept aside, until there's room again; or, set to Stop
     * Recording, none of those.
     */
    @SuppressLint("UsableSpace")
    private suspend fun makeRoom(folder: File): Boolean {
        val drive = (folder.parentFile ?: folder).absolutePath
        for (copied in data.recordings.copiedOnDrive()) {
            if (folder.usableSpace >= ROOM_BYTES) break
            if (copied.file?.startsWith(drive) == true) dropLocal(copied.id)
        }
        if (choices.spaceLow == SpaceLow.StopRecording) return folder.usableSpace >= Recorder.LOW_SPACE_BYTES
        for (old in data.recordings.watchedOldestFirst()) {
            if (folder.usableSpace >= ROOM_BYTES) break
            val file = old.file ?: continue
            if (!file.startsWith(drive)) continue
            forgetRecordings(listOf(old.id))
        }
        return folder.usableSpace >= Recorder.LOW_SPACE_BYTES
    }

    /**
     * Where recordings go (Settings › Recording › Record To, 4.34): Plasma's own storage on the USB
     * drive when one is connected (the default), or on the TV. With Network folder, it's where the
     * spool of what the share doesn't have yet is kept, and where recordings go when the share can't
     * be reached as one starts.
     */
    private fun recordingFolder(): File {
        val usb = if (choices.recordTo != RecordTo.ThisTv) usbVolume() else null
        return File(usb ?: context.getExternalFilesDir(null) ?: context.filesDir, RECORDINGS_FOLDER)
    }

    /**
     * Opens [channelId] for a recording (brief 5.6): its sources in order, the way a merged channel
     * fails over. Through the stream hub, or for HLS, which the hub doesn't hold, a segment at a
     * time. A multicast or RTMP address can't be recorded.
     */
    private suspend fun openForRecording(channelId: String): RecordingHost.Opened {
        var problem = RecordingProblems.NO_STREAM
        for (candidate in candidatesOf(channelId)) {
            val target = try {
                resolve(candidate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                null
            } ?: continue
            val hls = target.url.isHttp() && target.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
            if (!target.throughHub && !hls) continue
            try {
                if (hls) return openSegments(target)
                // Shared with the viewing by channel, so it's one connection (5.6).
                val session = hub.open(UpstreamRequest(target.url, target.headers, target.budget, shareKey = target.channelId, viewing = false))
                // An address that turns out to be HLS: its segments instead.
                if (turnsOutPlaylist(session)) {
                    session.close()
                    return openSegments(target)
                }
                return RecordingHost.Opened.Stream(session)
            } catch (full: ConnectionLimitReached) {
                problem = RecordingProblems.NO_CONNECTION
            }
        }
        return RecordingHost.Opened.Problem(problem)
    }

    /**
     * An HLS channel for a recording, read a segment at a time (brief 5.6), counted as one of its
     * source's connections while it's read. Throws [ConnectionLimitReached] when there's none left.
     */
    private fun openSegments(target: StreamTarget): RecordingHost.Opened {
        val held = target.budget?.let(hub::reserve)
        return RecordingHost.Opened.Segments(HlsReader(data.http, target.url, target.headers, onClose = { held?.close() }))
    }

    /** Whether the hub found a playlist behind [session]'s address, once it's heard back. */
    private suspend fun turnsOutPlaylist(session: HubSession): Boolean {
        val state = withTimeoutOrNull(PLAYLIST_LOOK_MS) { session.state.first { it != UpstreamState.Connecting } }
        return (state as? UpstreamState.Failed)?.failure == StreamFailure.Playlist
    }

    /** Settings › Recording's padding for a recording of [kind]: Start Early, and End Late with Extra Time for Sports. */
    private fun paddingFor(kind: RecordingKind): Pair<Duration, Duration> {
        val chosen = choices
        val sports = if (kind == RecordingKind.Sports) chosen.sportsExtraMinutes else 0
        return Duration.ofMinutes(chosen.startEarlyMinutes.toLong()) to Duration.ofMinutes((chosen.endLateMinutes + sports).toLong())
    }

    /**
     * Record Series, or Cancel Series, on a programme by the id the screens give it (brief 4.8 and
     * 5.6): a rule for its title on its channel, which sets every airing the guide has, now and as
     * the guide grows. Cancelling takes the rule away with what it set that's still to come.
     */
    fun recordSeries(channelId: String, programmeId: String, on: Boolean) {
        scope.launch {
            attempt {
                val programme = programmeOf(channelId, programmeId) ?: return@attempt
                if (!on) {
                    data.recordings.ruleFor(programme.title, channelId)?.let { data.recordings.deleteRule(it.id) }
                    return@attempt
                }
                val channel = data.channels.get(channelId) ?: return@attempt
                if (data.recordings.ruleFor(programme.title, channelId) == null) {
                    data.recordings.addRule(programme.title, Triple(channel.id, channel.name, channel.logo))
                }
                scheduleSeries()
            }
        }
    }

    /** A rule's Keep Last (Recordings › Series Rules): its newest [keepLast] recordings, or all of them. */
    fun setKeepLast(ruleId: String, keepLast: Int?) {
        scope.launch { attempt { data.recordings.setKeepLast(ruleId, keepLast) } }
    }

    /** Takes a Record Series rule away, with what it set that's still to come (Recordings › Series Rules). */
    fun deleteRule(id: String) {
        scope.launch { attempt { data.recordings.deleteRule(id) } }
    }

    /** Sets what the Record Series rules find in the guide ahead (brief 5.6). */
    private suspend fun scheduleSeries() {
        data.seriesRules.schedule(channels.first(), ::paddingFor)
    }

    /** The programme the screens' [programmeId] ("channelId@start") stands for, from the guide. */
    private suspend fun programmeOf(channelId: String, programmeId: String): Programme? {
        val start = programmeId.substringAfterLast('@').toLongOrNull()?.let(Instant::ofEpochSecond) ?: return null
        val epgKey = data.channels.get(channelId)?.epgKey ?: return null
        return data.guide.forChannel(epgKey, start, start.plusSeconds(1)).firstOrNull { it.start.epochSecond == start.epochSecond }
    }

    /**
     * Record, or Cancel Recording, on a programme by the id the screens give it: "channelId@start
     * in epoch seconds" (brief 4.7 and 5.6). It starts and ends as Settings › Recording's padding
     * says, with Extra Time for Sports on sport.
     */
    fun record(channelId: String, programmeId: String, on: Boolean) {
        scope.launch {
            attempt {
                if (!on) {
                    data.recordings.cancel(programmeId)
                    return@attempt
                }
                val channel = data.channels.get(channelId) ?: return@attempt
                val programme = programmeOf(channelId, programmeId) ?: return@attempt
                val (before, after) = paddingFor(RecordingKind.guess(programme.category, programme.season, programme.episode))
                data.recordings.schedule(
                    ProgrammeToRecord(
                        channelId = channelId,
                        channelName = channel.name,
                        channelLogo = channel.logo,
                        title = programme.title,
                        subtitle = programme.subtitle,
                        description = programme.description,
                        category = programme.category,
                        image = programme.icon,
                        season = programme.season,
                        episode = programme.episode,
                        start = programme.start,
                        end = programme.end,
                    ),
                    padBefore = before,
                    padAfter = after,
                )
            }
        }
    }

    /** The title of what's recording now, for the screensaver's line (brief 4.6); null when nothing is. */
    val recordingNow: Flow<String?> = data.recordings.recordings
        .map { list -> list.firstOrNull { it.status == RecordingStatus.Recording }?.title }
        .distinctUntilChanged()

    /** Whether a recording is being written now: Plasma doesn't update in the middle of one (brief 5.18). */
    suspend fun recordingInProgress(): Boolean = recordingNow.first() != null

    /** Keep (Recordings' green): kept from automatic deletion, or not (brief 5.6). */
    fun keepRecording(id: String, keep: Boolean) {
        scope.launch { attempt { data.recordings.keep(id, keep) } }
    }

    /** Mark as Watched or Unwatched, from a recording's options. */
    fun markRecordingWatched(id: String, watched: Boolean) {
        scope.launch { attempt { data.recordings.markWatched(id, watched) } }
    }

    /** The drive recordings go to now, and its room, for Recordings' Storage card (board 17). */
    @SuppressLint("UsableSpace")
    fun recordingDrive(): DriveRoom {
        val folder = recordingFolder()
        val drive = folder.parentFile ?: folder
        return DriveRoom(onUsb = drive != context.getExternalFilesDir(null) && drive != context.filesDir, free = drive.usableSpace, total = drive.totalSpace)
    }

    /** Deletes recordings and their files (Recordings' red), stopping any being written. */
    fun deleteRecordings(ids: Set<String>) {
        scope.launch { attempt { forgetRecordings(ids) } }
    }

    /**
     * Delete Everywhere (Recordings' red, brief 5.6): recordings, their files, and their copies on
     * the network folder with what's beside them. A copy the network folder couldn't be reached for
     * stays there, and a notice says so.
     */
    fun deleteRecordingsEverywhere(ids: Set<String>) {
        scope.launch {
            attempt {
                val copies = ids.mapNotNull { data.recordings.get(it) }.filter { it.copy == CopyState.Copied && it.networkPath != null }
                val left = deleteCopies(copies)
                forgetRecordings(ids)
                if (left.isNotEmpty()) {
                    val host = left.firstNotNullOfOrNull { it.networkPath?.let(NetworkFolder::hostOf) }.orEmpty()
                    _notices.tryEmit(Notice(context.resources.getQuantityString(R.plurals.copies_left, left.size, left.size, host), PlasmaIcons.NetworkFolder, Notice.LONG_MS))
                }
            }
        }
    }

    /** Deletes [copies] from the network folder, signed in as it is; the ones it couldn't. */
    private suspend fun deleteCopies(copies: List<Recording>): List<Recording> = withContext(Dispatchers.IO) {
        if (copies.isEmpty()) return@withContext emptyList()
        val folder = data.networkFolders.get()
        val left = ArrayList<Recording>()
        val places = copies.groupBy { copy -> copy.networkPath?.let(NetworkFolder::placeOf)?.let { it.host to it.share } }
        for ((place, group) in places) {
            if (place == null || folder == null || !folder.host.equals(place.first, ignoreCase = true)) {
                left += group
                continue
            }
            try {
                SmbShares.open(folder.host, place.second, folder.username, folder.password, folder.domain).use { share ->
                    for (copy in group) {
                        val path = copy.networkPath?.let(NetworkFolder::placeOf)?.path ?: continue
                        NetworkCopier.deleteCopy(share, path)
                    }
                }
            } catch (failed: ShareException) {
                left += group
            }
        }
        left
    }

    private suspend fun forgetRecordings(ids: Collection<String>) {
        // On their way to the network folder: their spools, and their unfinished files there (5.6).
        val unfinished = ids.mapNotNull { data.recordings.get(it) }.filter { it.spool != null }
        val files = data.recordings.delete(ids)
        withContext(Dispatchers.IO) {
            files.forEach { RecordingFiles.delete(File(it)) }
            unfinished.forEach { recording -> recording.spool?.let { File(it).deleteRecursively() } }
        }
        if (unfinished.isNotEmpty()) discardUnfinished(unfinished)
    }

    /** The unfinished files [recordings] left on the network folder go with them: they're Plasma's own. */
    private suspend fun discardUnfinished(recordings: List<Recording>) {
        val folder = data.networkFolders.get() ?: return
        val copier = NetworkCopier()
        for (recording in recordings) {
            val media = recording.networkPath?.let { Recorder.mediaOf(folder, it) } ?: continue
            try {
                copier.discard(folder, media)
            } catch (failed: ShareException) {
                // Away: it stays there, unfinished, where Jellyfin doesn't look.
            }
        }
    }

    /**
     * Retention (brief 5.6): what a rule's Keep Last, and Delete Watched Recordings, let go of now.
     * Kept recordings stay.
     */
    private suspend fun tidyRecordings() {
        data.recordings.forgetPastCancelled()
        val days = choices.deleteWatchedDays
        val since = data.settings.chosenAt(SettingKeys.DELETE_WATCHED).first()
        val expired = data.recordings.expired(days?.let { Duration.ofDays(it.toLong()) }, since)
        if (expired.isNotEmpty()) forgetRecordings(expired.map { it.id })
        // After Copying (5.6): the drive's file goes a week after its copy got there, or at once.
        val keepFor = when (choices.afterCopying) {
            AfterCopying.KeepWeek -> Duration.ofDays(AFTER_COPYING_DAYS)
            AfterCopying.Delete -> Duration.ZERO
            AfterCopying.UntilSpaceNeeded -> return
        }
        val before = clock.now().minus(keepFor)
        for (copied in data.recordings.copiedOnDrive()) {
            if (copied.copiedAt?.isAfter(before) == false) dropLocal(copied.id)
        }
    }

    /**
     * After Copying (brief 5.6): [id]'s file on the drive goes, with its index and what the guide
     * said, its copy being on the network folder; it plays from there from now on.
     */
    private suspend fun dropLocal(id: String) {
        val recording = data.recordings.get(id)?.takeIf { it.copy == CopyState.Copied && it.networkPath != null } ?: return
        val file = recording.file ?: return
        data.recordings.forgetFile(id)
        withContext(Dispatchers.IO) { RecordingFiles.delete(File(file)) }
    }

    /**
     * Each merged channel's candidates, under every candidate's id (brief 5.1), kept current so a
     * zap finds them at once.
     */
    private val candidatesById: StateFlow<Map<String, List<String>>> = channels
        .map { channels -> buildMap { for (channel in channels) if (channel.isMerged) for (id in channel.candidates) put(id, channel.candidates) } }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Streams by key, and what to play a merged channel from. */
    private val streams = object : StreamResolver {
        override suspend fun resolve(key: String): StreamTarget? = this@AppGraph.resolve(key)

        override suspend fun candidates(key: String): List<String> = plan(key)

        // Picked in Switch Player, or the one that played after a fallback (5.4.3); a movie or
        // episode in Dolby Vision or HDR10+ has the Android player's.
        override suspend fun engineFor(key: String): EngineId? {
            if (StreamKeys.isVod(key)) return hdrEngine(jellyfinOf(key)?.let(jellyfin::hdrOf) ?: playableOf(key)?.video?.hdr, choices.hdrMovies)
            return data.playback.engineFor(StreamKeys.channelOf(key))?.let { name -> EngineId.entries.firstOrNull { it.name == name } }
        }

        // Channel options › Volume Level and Decode This Channel, and the tracks picked on it (5.4.6).
        override suspend fun audioFor(key: String): ChannelAudio? {
            if (StreamKeys.isVod(key)) return null
            val channelId = StreamKeys.channelOf(key)
            val sound = data.playback.soundOf(channelId)
            return ChannelAudio(sound.levelDb, AudioOutput.DecodeToPcm.takeIf { sound.decoded }, data.playback.tracksOf(channelId))
        }
    }

    /**
     * The tune Remember What Works per Channel follows (brief 5.1): a merged channel on live TV,
     * with the setting on. Used on the main thread only.
     */
    private var tune: TuneMemory? = null

    /** What the player reports for [tune], kept one write at a time, in order. */
    private val memoryWork = WorkQueue<suspend () -> Unit>(WorkQueue.UNLIMITED).also { work ->
        scope.launch { for (job in work) attempt { job() } }
    }

    private val playerMade = MutableStateFlow(false)

    /**
     * libVLC loads with the first live picture, so the sample world never pays for it; libmpv and
     * Media3 the first time a stream needs them (brief 5.4.3).
     */
    private val playerSlot = lazy {
        PlayerManager(
            VlcEngine(context), hub, streams, ::playbackPrefs, scope,
            onEvent = ::onPlayerEvent,
            backups = mapOf(
                EngineId.Mpv to { MpvEngine.create(context) },
                EngineId.Media3 to { Media3Engine.create(context, data.http) },
            ),
            frameRates = DisplayFrameRates,
        ).also { playerMade.value = true }
    }
    val player: PlayerManager by playerSlot

    /** The player once something has played, without making it: for the phone remote's card (5.21). */
    val playerIfMade: Flow<PlayerManager?> = playerMade.map { made -> if (made) player else null }

    /** The player if something has played; null without making it. */
    fun playerNow(): PlayerManager? = if (playerSlot.isInitialized()) player else null

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = NOTICES)

    /** Short notices for the corner of the screen (brief 4.35): "Switched to Backup Line." */
    val notices: SharedFlow<Notice> = _notices.asSharedFlow()

    init {
        // A refresh that moved channels the lists show says so (4.35); merged ones don't count.
        scope.launch {
            data.importer.newDuplicates.collect { ids ->
                attempt {
                    val shown = data.channels.lineup.flowOn(Dispatchers.Default).first().shown.mapTo(HashSet()) { it.id }
                    val count = ids.count { it in shown }
                    if (count > 0) {
                        val text = context.resources.getQuantityString(R.plurals.new_duplicates, count, count)
                        _notices.tryEmit(Notice(text, PlasmaIcons.Hash, NEW_NUMBERS_MS))
                    }
                }
            }
        }
        // The alarm for the next recording follows what's scheduled (brief 5.6).
        scope.launch {
            data.recordings.recordings.collect { recordings ->
                attempt { RecordingAlarms.arm(context, RecordingAlarms.nextStart(recordings)) }
            }
        }
        // Record Series rules set what the guide has for them, as rules and the guide change and
        // each hour; retention lets go of what it should, as recordings finish and each hour.
        scope.launch {
            combine(data.recordings.rules, data.guide.latestEnd, ticker(HOUR_MS)) { _, _, _ -> }.collectLatest {
                attempt { scheduleSeries() }
            }
        }
        scope.launch {
            val finished = data.recordings.recordings
                .map { list -> list.count { it.status == RecordingStatus.Done || it.watched } }
                .distinctUntilChanged()
            combine(finished, ticker(HOUR_MS)) { _, _ -> }.collectLatest { attempt { tidyRecordings() } }
        }
    }

    val standby = Standby(context, { choices }, { if (playerSlot.isInitialized()) player else null }, inSmallWindow = { smallWindow.open })

    /** Parental controls (brief 5.12): the PIN, and what it's opened for how long (4.25). */
    val parental = ParentalControls(data.settings.pins(), clock, scope)

    /** Wrong tries at profiles' own PINs (5.11), counted as the parental PIN's are. */
    val profileTries = PinTries()

    /**
     * A choice on a profile's page (5.11): its picture, its kind or a kids profile's ages, by the
     * option's place, as Settings lists them.
     */
    fun chooseForProfile(profileId: String, field: String, index: Int) {
        scope.launch {
            attempt {
                val profile = data.profiles.get(profileId) ?: return@attempt
                var avatar = profile.avatar
                var kind = profile.kind
                var maxAge = profile.maxAge
                when (field) {
                    ProfileSettings.AVATAR -> avatar = ProfileSettings.AVATARS.getOrNull(index - 1)
                    ProfileSettings.TYPE -> kind = ProfileKind.entries.getOrElse(index) { ProfileKind.Standard }
                    ProfileSettings.AGES -> maxAge = ProfileSettings.AGES_OPTIONS.getOrNull(index)
                    else -> return@attempt
                }
                // A new kids profile starts at 7 and under.
                if (kind == ProfileKind.Kids && maxAge == null) maxAge = KIDS_AGE
                data.profiles.update(profileId, profile.name, avatar, kind, maxAge)
            }
        }
    }

    /** Add Profile (5.11): a standard profile named [name], with a picture none of the others has. */
    suspend fun addProfile(name: String) {
        attempt {
            val taken = data.profiles.all.first().mapNotNull { it.avatar }.toSet()
            val avatar = ProfileSettings.AVATARS.firstOrNull { it !in taken } ?: ProfileSettings.AVATARS.first()
            data.profiles.add(name, avatar, ProfileKind.Standard, maxAge = null)
        }
    }

    /** Default Profile (5.11): Whoever Watched Last, first, then each profile in its order. */
    fun chooseDefaultProfile(index: Int) {
        scope.launch {
            attempt { data.settings.setDefaultProfile(data.profiles.all.first().getOrNull(index - 1)?.id) }
        }
    }

    init {
        // What was opened until the TV sleeps, and Settings opened with the PIN, lock again then.
        scope.launch { standby.tvOn.collect { on -> if (!on) parental.slept() } }
    }

    // ---- Local catch-up (brief 5.5) -----------------------------------------------------------

    /**
     * Local catch-up as Settings has it, for the recording service: its channels, where they go,
     * how long they're kept, and whether the TV being off pauses them. Looked at again every
     * minute too, for a USB drive coming and going.
     */
    private val localPlan: Flow<LocalCatchUpPlan> = combine(
        data.settings.localCatchUp,
        storedChoices.filterNotNull(),
        standby.tvOn,
        ticker(LOCAL_LOOK_MS),
    ) { ids, chosen, tvOn, _ ->
        val settings = SettingsChoices(chosen)
        val usb = if (settings.saveTo == SaveTo.UsbDrive) usbVolume() else null
        LocalCatchUpPlan(
            channelIds = ids.sorted(),
            root = localCatchUpRoot(usb),
            keepMs = settings.localKeepHours * HOUR_MS,
            maxBytes = settings.storageLimitBytes(onTv = usb == null),
            paused = !tvOn && settings.pauseLocalWhileOff,
        )
    }.distinctUntilChanged()

    /**
     * Where local catch-up goes: Plasma's own storage on the USB drive when Save To says so and one
     * is there, otherwise on the TV.
     */
    private fun localCatchUpRoot(usb: File? = if (choices.saveTo == SaveTo.UsbDrive) usbVolume() else null): File =
        File(usb ?: context.getExternalFilesDir(null) ?: context.filesDir, LOCAL_FOLDER)

    init {
        // It runs in the recording service while there are channels to write, and starts again
        // by itself after a restart.
        scope.launch {
            data.settings.localCatchUp.map { it.isNotEmpty() }.distinctUntilChanged().collect { any ->
                if (any) attempt { RecordingAlarms.startService(context) }
            }
        }
        // What it holds, for the Guide, Catch-up and Search.
        scope.launch(Dispatchers.IO) {
            while (true) {
                attempt { refreshLocalWindows() }
                delay(LOCAL_LOOK_MS)
            }
        }
    }

    private fun refreshLocalWindows() {
        _localWindows.value = LocalCatchUp.folders(localCatchUpRoot()).mapNotNull { folder ->
            CatchUpRing.window(folder)?.let { LocalCatchUp.channelOf(folder) to it }
        }.toMap()
    }

    /**
     * What local catch-up on [channelId] costs (4.31): its source, how many of its connections local
     * catch-up holds already, and its limit. Null for a channel Plasma doesn't have.
     */
    suspend fun localCost(channelId: String): LocalCost? {
        val channel = data.channels.get(channelId) ?: return null
        val source = data.sources.get(channel.sourceId) ?: return null
        val held = data.settings.localCatchUp.first().count { id -> id != channelId && data.channels.get(id)?.sourceId == source.id }
        return LocalCost(source.name, held, source.maxConnections)
    }

    /**
     * Local catch-up on [channelId], or off (brief 5.5). Each channel holds one of its source's
     * connections all the time, so one that would leave none to watch with is refused: false.
     */
    suspend fun setLocalCatchUp(channelId: String, on: Boolean): Boolean {
        if (on && localCost(channelId)?.leavesNone == true) return false
        data.settings.setLocalCatchUp(channelId, on)
        return true
    }

    /**
     * Add to, or Remove from, Local Catch-up in a channel's options (brief 5.5), with a notice that
     * says what it costs, or why it can't (4.35).
     */
    fun toggleLocalCatchUp(channelId: String, on: Boolean) {
        scope.launch {
            attempt {
                val name = data.channels.get(channelId)?.name ?: return@attempt
                val cost = localCost(channelId)
                val text = when {
                    !on -> {
                        setLocalCatchUp(channelId, false)
                        context.getString(R.string.local_removed, name)
                    }
                    !setLocalCatchUp(channelId, true) -> context.getString(R.string.local_refused, cost?.sourceName.orEmpty())
                    cost?.limit != null -> context.getString(R.string.local_added_cost, name, cost.held + 1, cost.limit, cost.sourceName)
                    else -> context.getString(R.string.local_added, name)
                }
                _notices.tryEmit(Notice(text, PlasmaIcons.CatchUp, Notice.LONG_MS))
            }
        }
    }

    /** Clear Local Catch-up (4.31): what it holds goes; its channels carry on from now. */
    fun clearLocalCatchUp() {
        scope.launch(Dispatchers.IO) {
            attempt {
                LocalCatchUp.folders(localCatchUpRoot()).forEach(CatchUpRing::clear)
                refreshLocalWindows()
            }
        }
    }

    /** The space local catch-up takes now (Settings' Storage card, 4.31). */
    fun localCatchUpBytes(): Long = LocalCatchUp.size(localCatchUpRoot())

    /** The last programme played from local catch-up: the hub serves one at a time (5.5). */
    @Volatile private var localPlaying: LocalSession? = null

    /**
     * A programme from local catch-up (brief 5.5), from [start] to the end of what's on then: the
     * hub serves it from the drive, from the keyframe before [start]. Null when it doesn't hold it.
     */
    private suspend fun localTarget(key: String, channelId: String, start: Instant): StreamTarget? {
        val window = localWindows.value[channelId] ?: return null
        if (start.toEpochMilli() !in window.first - LOCAL_SLACK_MS..window.last) return null
        val folder = LocalCatchUp.folderOf(localCatchUpRoot(), channelId)
        val programme = data.channels.get(channelId)?.epgKey
            ?.let { epgKey -> data.guide.forChannel(epgKey, start, start.plusSeconds(1)).firstOrNull() }
        val end = programme?.end ?: start.plus(LOCAL_UNKNOWN_LENGTH)
        val from = start.toEpochMilli()
        val to = end.toEpochMilli()
        val session = hub.serve { CatchUpRing.open(folder, from, to) }
        localPlaying?.close()
        localPlaying = session
        // What wasn't written, while the stream was down or the TV off, is skipped: said once a programme.
        val said = "$channelId@${programme?.start ?: start}"
        val missing = CatchUpRing.missingMs(folder, from, to)
        if (missing >= MINUTE_MS && said != skippedSaid) {
            skippedSaid = said
            val minutes = (missing / MINUTE_MS).toInt()
            _notices.tryEmit(Notice(context.resources.getQuantityString(R.plurals.local_skipped, minutes, minutes), PlasmaIcons.CatchUp, Notice.LONG_MS))
        }
        return StreamTarget(channelId = key, url = session.url, headers = emptyMap(), throughHub = false)
    }

    /** The programme the "Skipped … min" notice was last given for. */
    @Volatile private var skippedSaid: String? = null

    /**
     * Keep as Recording (brief 5.5): a programme local catch-up holds, copied into a recording of
     * its own, with its index and what the guide said, so retention applies to it as to any other.
     * A notice says how it went.
     */
    fun keepAsRecording(channelId: String, programmeId: String) {
        scope.launch {
            attempt {
                val programme = programmeOf(channelId, programmeId) ?: return@attempt
                val channel = data.channels.get(channelId) ?: return@attempt
                val toRecord = ProgrammeToRecord(
                    channelId = channelId,
                    channelName = channel.name,
                    channelLogo = channel.logo,
                    title = programme.title,
                    subtitle = programme.subtitle,
                    description = programme.description,
                    category = programme.category,
                    image = programme.icon,
                    season = programme.season,
                    episode = programme.episode,
                    start = programme.start,
                    end = programme.end,
                )
                _notices.tryEmit(Notice(keep(toRecord), PlasmaIcons.Record, Notice.LONG_MS))
            }
        }
    }

    /** Copies [programme] out of local catch-up into Recordings; what the notice says of it. */
    private suspend fun keep(programme: ProgrammeToRecord): String = withContext(Dispatchers.IO) {
        val kept = data.recordings.get(RecordingRepository.idOf(programme.channelId, programme.start))
        if (kept?.status == RecordingStatus.Done) return@withContext context.getString(R.string.kept_already, programme.title)
        val folder = LocalCatchUp.folderOf(localCatchUpRoot(), programme.channelId)
        val named = data.recordings.recordingOf(programme)
        val recordings = recordingFolder().takeIf { it.isDirectory || it.mkdirs() }
            ?: return@withContext context.getString(R.string.keep_no_room, programme.title)
        val file = RecordingFiles.newFile(recordings, named, clock.zone)
        val bytes = try {
            FileOutputStream(file).use { out ->
                KeyframeIndex(RecordingFiles.indexOf(file)).use { index ->
                    CatchUpRing.copy(folder, programme.start.toEpochMilli(), programme.end.toEpochMilli(), out) { offset, time -> index.add(offset, time) }
                }
            }
        } catch (full: IOException) {
            RecordingFiles.delete(file)
            return@withContext context.getString(R.string.keep_no_room, programme.title)
        }
        if (bytes == 0L) {
            RecordingFiles.delete(file)
            return@withContext context.getString(R.string.keep_gone, programme.title)
        }
        RecordingFiles.writeSidecar(file, named)
        data.recordings.kept(programme, file.path, bytes)
        attempt { copySoon(RecordingRepository.idOf(programme.channelId, programme.start)) }
        context.getString(R.string.kept_as_recording, programme.title)
    }

    /** Plasma's own updates (brief 5.18). */
    val updates: Updates by lazy { Updates(context, this) }

    /** Keep Watching in a Small Window (brief 5.20): what plays goes to a corner when another app opens. */
    val smallWindow: SmallWindow by lazy { SmallWindow(context, this) }

    /** What plays now, or is tuning, by its key; null when nothing does. */
    fun playingNow(): String? = if (playerSlot.isInitialized()) playingKey(player.status.value) else null

    /**
     * The small window's Previous or Next Channel went to [channelId]: it's the last channel now,
     * so Home's picture shows it when Plasma comes back (5.20).
     */
    fun watchedInWindow(channelId: String) {
        val now = world.value
        // From the list last watched, if the window's buttons went through it.
        val listId = now?.lastWatched?.listId?.takeIf { id -> now.list(id).any { it.id == channelId } }
        scope.launch { attempt { data.channels.watched(channelId, listId) } }
    }

    /**
     * Player Options › Sleep Timer (brief 4.10): the picture and sound stop, and the connection
     * goes at once; a button plays the channel again.
     */
    val sleep = SleepTimer(
        clock,
        scope,
        onSleep = { if (playerSlot.isInitialized()) player.standby(graceMs = 0) },
        onWake = { if (playerSlot.isInitialized()) player.resume() },
    )

    /**
     * Reminders come due on Plasma's screen, or over another app where Android lets Plasma show
     * them there (brief 5.7); otherwise they're skipped.
     */
    val reminderClock = ReminderClock(data.reminders, clock, scope) { standby.isOnScreen || (standby.isTvOn && reminderOverlay.allowed()) }

    val reminderOverlay by lazy { ReminderOverlay(context, this) }

    private val _watchFromReminder = MutableStateFlow<String?>(null)

    /** A reminder's Switch Now from over another app: the channel to open full screen, until it's open. */
    val watchFromReminder: StateFlow<String?> = _watchFromReminder.asStateFlow()

    fun watchFromReminder(channelId: String) {
        _watchFromReminder.value = channelId
    }

    fun watchedFromReminder(channelId: String) {
        _watchFromReminder.compareAndSet(channelId, null)
    }

    // ---- The Home button (brief 5.13) ------------------------------------------------------------

    private val _homeOpening = MutableStateFlow<HomeOpening?>(null)

    /** Plasma was opened by Home or at power-on, and the screen it leads to hasn't opened yet. */
    val homeOpening: StateFlow<HomeOpening?> = _homeOpening.asStateFlow()

    /**
     * Plasma's screen was opened for [reason], or for none (an ordinary start). Home pressed as
     * the TV comes back on is the TV turning on, which When the TV Turns On already answers (5.20).
     */
    fun opened(reason: HomeReason?) {
        reason ?: return
        if (reason == HomeReason.Home && standby.waking) return
        _homeOpening.value = HomeOpening(reason)
    }

    /** The screen [opening] leads to is open. */
    fun handled(opening: HomeOpening) {
        _homeOpening.compareAndSet(opening, null)
    }

    /** Shows [notice] at the top right, over any screen. */
    fun notice(notice: Notice) {
        _notices.tryEmit(notice)
    }

    // ---- The phone remote (brief 5.21) -----------------------------------------------------------

    /** What the phone remote shows of the screen: the field taking text, and the colour keys' labels. */
    val remoteMirror = RemoteMirror()

    /** Plasma's server for phones while Settings › Remote & Buttons › Phone Remote is on. */
    val phoneRemote = PhoneRemoteHost(this, context) { data.http }

    private val _tuneFromPhone = MutableStateFlow<PhoneTune?>(null)

    /** A channel tapped on a phone's Channels tab: the player opens on it, until it has. */
    val tuneFromPhone: StateFlow<PhoneTune?> = _tuneFromPhone.asStateFlow()

    fun tuneFromPhone(channelId: String, listId: String?) {
        _tuneFromPhone.value = PhoneTune(channelId, listId ?: Lists.ALL, SystemClock.elapsedRealtime())
    }

    fun tunedFromPhone(tune: PhoneTune) {
        _tuneFromPhone.compareAndSet(tune, null)
    }

    init {
        // On by default; off, phones can't reach the TV at all (5.21).
        scope.launch {
            storedChoices.filterNotNull().map { SettingsChoices(it).phoneRemote }.distinctUntilChanged().collect { on ->
                if (on) phoneRemote.start() else phoneRemote.stop()
            }
        }
    }

    /** Plasma's ADB key (brief 5.13), made the first time and kept sealed, so a paired TV knows it. */
    suspend fun adbKey(): AdbKey = withContext(Dispatchers.Default) { AdbKey.of(data.adbKey { AdbKey.generate().encoded() }) }

    // ---- The TV's apps and inputs (brief 4.4 and 5.13) -----------------------------------------------

    val tvApps = TvApps(context)
    val tvInputs = TvInputs(context)

    /**
     * Home's Apps row and All Apps as the TV's apps are now, with the row's order and the hidden
     * apps kept in Settings; null until the apps have been read. The package manager is only
     * watched while a screen shows them.
     */
    val apps: StateFlow<AppsLayout?> = combine(tvApps.changes(), data.settings.homeApps, data.settings.hiddenApps) { installed, row, hidden ->
        AppsLayout.of(installed, row, hidden)
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_SHARING_MS), null)

    /** Opens the app [packageName] from Home's Apps row or Apps; what plays goes to the small window (5.20). */
    fun openApp(packageName: String) {
        val app = apps.value?.find(packageName) ?: return
        smallWindow.thenOpen { tvApps.open(app) }
    }

    /** Opens one of the TV's inputs from Apps › Inputs (4.4), what plays going to the small window. */
    fun openInput(id: String) {
        smallWindow.thenOpen { tvInputs.open(id) }
    }

    /** Apps' Device Settings tile (4.4), what plays going to the small window. */
    fun openDeviceSettings() {
        smallWindow.thenOpen { tvApps.openDeviceSettings() }
    }

    /** Apps' Get More Apps tile (4.4), what plays going to the small window. */
    fun openPlayStore() {
        smallWindow.thenOpen { tvApps.openPlayStore() }
    }

    /** The Apps row's new order, after a move, or an app added or taken off (4.4). */
    fun setHomeApps(packages: List<String>) {
        scope.launch { attempt { data.settings.setHomeApps(packages) } }
    }

    /** Hides an app from the Apps row and All Apps, or shows it again (4.4). */
    fun setAppHidden(packageName: String, hidden: Boolean) {
        scope.launch { attempt { data.settings.setAppHidden(packageName, hidden) } }
    }

    // ---- Other apps' items in Continue Watching (brief 5.19) ---------------------------------------

    val playNext = PlayNext(context)

    /** Goes up when Android's permission may have changed, so the row is read again at once. */
    private val playNextReads = MutableStateFlow(0)

    /**
     * Android's Play Next row, other apps' items, while Include Other Apps is on; empty while it's
     * off. Read while a screen shows it, as apps change it, and every 15 minutes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val playNextRow: StateFlow<List<PlayNextItem>> = combine(storedChoices.map { SettingsChoices(it.orEmpty()).includeOtherApps }, playNextReads) { on, _ -> on }
        .flatMapLatest { on -> if (on) playNext.changes() else flowOf(emptyList()) }
        .map { items -> items.filter { it.packageName != context.packageName } }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_SHARING_MS), emptyList())

    /** What Continue Watching shows of it: not the items and apps hidden from Plasma. */
    val playNextShown: Flow<List<PlayNextItem>> = combine(playNextRow, data.settings.hiddenPlayNext, data.settings.hiddenPlayNextApps) { items, hidden, apps ->
        items.filter { it.key !in hidden && it.packageName !in apps }
    }

    /** Reads the row again: Android may let Plasma read other apps' items now. */
    fun readPlayNextAgain() {
        playNextReads.update { it + 1 }
    }

    /** Opens another app's item, by its key, in that app; what plays goes to the small window (5.20). */
    fun openPlayNext(key: String) {
        val item = playNextRow.value.firstOrNull { it.key == key } ?: return
        smallWindow.thenOpen { playNext.open(item) }
    }

    /** Hide from Plasma on another app's item, or the whole app's items with [wholeApp]. */
    fun hidePlayNext(key: String, wholeApp: Boolean) {
        scope.launch {
            attempt {
                if (wholeApp) data.settings.setPlayNextAppHidden(packageOfKey(key), hidden = true) else data.settings.hidePlayNext(setOf(key))
            }
        }
    }

    /** Continue Watching › Apps: one app's items shown or hidden. */
    fun showPlayNextApp(packageName: String, shown: Boolean) {
        scope.launch { attempt { data.settings.setPlayNextAppHidden(packageName, hidden = !shown) } }
    }

    /** The profile's Jellyfin server, its items for Continue Watching, and its sign-in (5.19). */
    val jellyfin: JellyfinLink by lazy { JellyfinLink(context, this) }

    private val appNames = ConcurrentHashMap<String, String>()

    /** An app's name, for its items' tag: the package itself if the TV won't say. */
    fun appName(packageName: String): String = appNames.getOrPut(packageName) {
        val pm = context.packageManager
        try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString().trim().ifEmpty { packageName }
        } catch (gone: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private var screen: WeakReference<Activity>? = null

    /** Plasma's screen while it's made, for keys from the phone remote (5.21). */
    fun screenNow(): Activity? = screen?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    /** Plasma's popups that take keys, over the screen: a phone's keys go to the newest (5.21). */
    val keyWindows = KeyWindows()

    /**
     * Plasma's screen was made. Started as the TV's Home and as an app (methods 2 and 3, and the
     * override, can each start it), there would be two: the older goes, with its task.
     */
    fun screenCreated(activity: Activity) {
        screen?.get()?.takeIf { it !== activity && !it.isFinishing }?.finishAndRemoveTask()
        screen = WeakReference(activity)
    }

    fun screenDestroyed(activity: Activity) {
        if (screen?.get() === activity) screen = null
    }

    val keys = RemoteKeyMap(data.settings, scope)

    private val _tests = MutableStateFlow<Map<String, SourceTest>>(emptyMap())

    /** How Test Connection went on each source (brief 4.20), until it refreshes or Plasma restarts. */
    val tests: StateFlow<Map<String, SourceTest>> = _tests.asStateFlow()

    /** One source's own settings change one at a time. */
    private val sourceChoices = Mutex()

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
        return PlaybackPrefs(
            buffer = chosen.buffer,
            decoding = chosen.decoding,
            deinterlacing = chosen.deinterlacing,
            audioOutput = outputFor(chosen.audioOutput),
            frameRate = chosen.frameRate,
            throughHub = chosen.throughHub,
            failOver = chosen.failOver,
            engine = engineOf(chosen.playerEngine.ordinal) ?: EngineId.Vlc,
            ifStreamFails = chosen.ifStreamFails,
            connectionTimeoutMs = chosen.connectionTimeout.millis,
            tracks = TrackPrefs(chosen.preferredLanguage.languages, chosen.skipDescription, chosen.subtitles),
            tunneling = chosen.tunneling,
        )
    }

    /**
     * What to play [key] from: a merged channel's candidates, best first (brief 5.1). A programme
     * from the archive plays from the candidates that keep one.
     */
    private suspend fun candidatesOf(key: String): List<String> {
        val members = candidatesById.value[StreamKeys.channelOf(key)] ?: return listOf(key)
        val start = StreamKeys.archiveStart(key) ?: return members
        return members.filter { id -> (data.channels.get(id)?.catchupDays ?: 0) > 0 || id in localWindows.value }
            .map { StreamKeys.archive(it, start) }
            .ifEmpty { listOf(key) }
    }

    /**
     * What to play [key] from, in order (brief 5.1): a merged channel's candidates, with the one
     * Remember What Works per Channel keeps first. A programme from the archive keeps the
     * channel's own order.
     */
    private suspend fun plan(key: String): List<String> {
        val candidates = candidatesOf(key)
        tune = null
        // Picked in the channel's options before it played: it counts as a pick now (5.1).
        val picked = pickedSource?.takeIf { it.first == key }?.second
        pickedSource = null
        if (candidates.size < 2 || StreamKeys.archiveStart(key) != null || !choices.rememberWorks) return candidates
        val plan = try {
            data.memory.plan(key, candidates)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            return candidates
        }
        tune = TuneMemory(data.memory, plan)
        if (picked != null) memoryWork.trySend { data.memory.choose(plan, picked) }
        return plan.order
    }

    private fun onPlayerEvent(event: PlayerEvent) {
        // Movies and episodes have nothing to remember per channel; a fallback is still noticed.
        if (StreamKeys.isVod(event.channelId)) {
            if (event is PlayerEvent.SwitchedEngine) {
                scope.launch {
                    val name = vodNameOf(event.channelId) ?: return@launch
                    _notices.tryEmit(Notice(context.getString(R.string.engine_fallback, engineName(event.from), name, engineName(event.to))))
                }
            }
            return
        }
        val current = tune?.takeIf { it.plan.channelId == event.channelId }
        when (event) {
            is PlayerEvent.SourceFailed -> {
                // Failover (5.1): a quiet notice of where the channel comes from now, except when
                // the week's check goes straight back to the source that works.
                if (current?.plan?.isQuietSwitch(event.candidate, event.next) != true) noticeSwitchedTo(event.next)
                if (current != null) memoryWork.trySend { current.failed(event.candidate, event.next) }
            }
            is PlayerEvent.Playing -> if (current != null) memoryWork.trySend { current.playing(event.candidate) }
            is PlayerEvent.PlayedLong -> if (current != null) memoryWork.trySend { current.playedLong(event.candidate) }
            // Automatic fallback (5.4.3): "VLC couldn't play Arena Sports 2. Playing it with mpv."
            is PlayerEvent.SwitchedEngine -> scope.launch {
                val name = data.channels.get(StreamKeys.channelOf(event.channelId))?.name ?: return@launch
                _notices.tryEmit(Notice(context.getString(R.string.engine_fallback, engineName(event.from), name, engineName(event.to))))
            }
            is PlayerEvent.EngineWorked -> rememberEngine(event.channelId, event.engine, EngineReason.Fallback)
            is PlayerEvent.EngineChosen -> rememberEngine(event.channelId, event.engine, EngineReason.User)
            // Audio and Subtitles: the channel's next tunes start with what was picked (5.4.6).
            is PlayerEvent.AudioChosen -> scope.launch { attempt { data.playback.pickAudio(StreamKeys.channelOf(event.channelId), event.track.pick()) } }
            is PlayerEvent.SubtitlesChosen -> scope.launch { attempt { data.playback.pickSubtitles(StreamKeys.channelOf(event.channelId), event.track?.pick()) } }
        }
    }

    /** The engine for a channel, from now on (brief 5.4.3). */
    private fun rememberEngine(key: String, engine: EngineId, reason: EngineReason) {
        scope.launch { attempt { data.playback.rememberEngine(StreamKeys.channelOf(key), engine.name, reason) } }
    }

    private fun engineName(engine: EngineId): String = context.getString(
        when (engine) {
            EngineId.Vlc -> R.string.engine_vlc
            EngineId.Mpv -> R.string.engine_mpv
            EngineId.Media3 -> R.string.engine_media3
        },
    )

    /** The channels with a sound of their own, by name, for Settings › Audio (4.28 and 5.4.6). */
    val channelSounds: Flow<List<ChannelSoundInfo>> = combine(data.playback.sounds, lineup) { sounds, lineup ->
        val names = lineup.listed.associate { it.id to it.name }
        sounds.mapNotNull { sound -> names[sound.channelId]?.let { ChannelSoundInfo(sound.channelId, it, sound.levelDb, sound.decoded) } }
    }

    /** Volume Level: OK keeps [db] as [channelId]'s level (5.4.6). */
    fun keepLevel(channelId: String, db: Int) {
        scope.launch { attempt { data.playback.setLevel(channelId, db) } }
    }

    /** Decode This Channel (5.4.6): its sound is decoded from now on, starting with the stream playing. */
    fun decodeChannel(channelId: String) {
        scope.launch {
            attempt {
                data.playback.decode(channelId)
                if (player.status.value.playingChannel() == channelId) player.retry()
            }
        }
    }

    /** Settings › Audio › Channel Volume Levels: Reset on [channelId], or Reset All. */
    fun resetSound(channelId: String?) {
        scope.launch {
            attempt {
                val playing = if (playerSlot.isInitialized()) player.status.value.playingChannel() else null
                val affected = playing?.takeIf { channelId == null || channelId == it }
                val wasDecoded = affected != null && data.playback.soundOf(affected).decoded
                if (channelId != null) data.playback.resetSound(channelId) else data.playback.resetSounds()
                // What's playing sounds as it should now: passing through again opens it again.
                if (affected != null) {
                    player.setLevel(affected, 0)
                    if (wasDecoded) player.retry()
                }
            }
        }
    }

    /**
     * The channel playing whose sound passes through to the receiver untouched, so a level can't
     * change it (5.4.6): a format the TV's output takes, with Audio Output passing it through.
     */
    fun passingThrough(): Flow<String?> = combine(player.status, player.playingFrom, player.audio, data.playback.sounds) { status, from, audio, sounds ->
        val channelId = status.playingChannel() ?: return@combine null
        val format = audio?.codec?.surround ?: return@combine null
        if (sounds.any { it.channelId == channelId && it.decoded }) return@combine null
        val sourceId = (from?.candidate ?: channelId).substringBefore(':')
        val sourceChoice = data.sources.get(sourceId)?.playback?.audioOutputChoice?.let { AudioOutput.entries.getOrNull(it) }
        val output = outputFor(sourceChoice ?: choices.audioOutput)
        channelId.takeIf { output == AudioOutput.Passthrough && format in AudioRoutes.current(context).supported }
    }

    private fun LiveStatus.playingChannel(): String? = playingKey(this)?.let(StreamKeys::channelOf)

    /**
     * The remote's Audio key in the player (brief 3.7): the next sound track plays, and a notice
     * says which, and how it leaves the box: "English 5.1 · passthrough" (4.35).
     */
    fun nextAudio() {
        val tracks = player.tracks.value
        if (tracks.audio.size < 2) return
        val index = (tracks.audio.indexOfFirst { it.id == tracks.audioId } + 1).mod(tracks.audio.size)
        val track = tracks.audio[index]
        player.selectAudio(track.id)
        val channelId = player.status.value.playingChannel() ?: return
        scope.launch {
            val route = if (passesThrough(channelId, audioCodecOf(track.codec))) R.string.sound_passthrough else R.string.sound_decoded
            val name = TrackNames.audio(context.resources, track, index)
            _notices.tryEmit(Notice(context.getString(R.string.track_notice, name, context.getString(route)), icon = PlasmaIcons.Volume))
        }
    }

    /** The remote's Subtitles key in the player: the first subtitles on, or them off, with a notice. */
    fun nextSubtitles() {
        val tracks = player.tracks.value
        val text = when {
            tracks.subtitleId != null -> {
                player.selectSubtitles(null)
                context.getString(R.string.subtitles_off_notice)
            }
            tracks.subtitles.isEmpty() -> context.getString(R.string.no_subtitles_notice)
            else -> {
                val track = tracks.subtitles.first()
                player.selectSubtitles(track.id)
                context.getString(R.string.subtitles_notice, TrackNames.subtitles(context.resources, track, 0))
            }
        }
        _notices.tryEmit(Notice(text, icon = PlasmaIcons.Subtitles))
    }

    /** Whether [channelId]'s sound in [codec] passes through to the TV or sound system untouched (5.4.6). */
    private suspend fun passesThrough(channelId: String, codec: AudioCodec): Boolean {
        val format = codec.surround ?: return false
        if (data.playback.soundOf(channelId).decoded) return false
        val sourceId = (player.playingFrom.value?.candidate ?: channelId).substringBefore(':')
        val sourceChoice = data.sources.get(sourceId)?.playback?.audioOutputChoice?.let { AudioOutput.entries.getOrNull(it) }
        return outputFor(sourceChoice ?: choices.audioOutput) == AudioOutput.Passthrough && format in AudioRoutes.current(context).supported
    }

    /** Each channel's own Picture Size, by its id (Player Options, brief 4.10); the rest fit. */
    val pictureSizes: Flow<Map<String, PictureSize>> = data.playback.pictureSizes.map { sizes ->
        sizes.mapNotNull { (id, name) -> PictureSize.entries.firstOrNull { it.name == name }?.let { id to it } }.toMap()
    }

    /** Player Options › Picture Size: [key]'s channel shows its picture as [size] from now on. */
    fun setPictureSize(key: String, size: PictureSize) {
        scope.launch { attempt { data.playback.setPictureSize(StreamKeys.channelOf(key), size.takeIf { it != PictureSize.Fit }?.name) } }
    }

    /** Settings › Playback › Forget Players Chosen per Channel (5.4.3), with a notice of how many. */
    fun forgetPlayers() {
        scope.launch {
            attempt {
                val count = data.playback.forgetEngines()
                val text = if (count == 0) {
                    context.getString(R.string.no_players_chosen)
                } else {
                    context.resources.getQuantityString(R.plurals.forgot_players, count, count)
                }
                _notices.tryEmit(Notice(text))
            }
        }
    }

    private fun noticeSwitchedTo(candidate: String) {
        scope.launch {
            val name = data.sources.get(StreamKeys.channelOf(candidate).substringBefore(':'))?.name ?: return@launch
            _notices.tryEmit(Notice(context.getString(R.string.switched_to_source, name)))
        }
    }

    /**
     * Switch Source (brief 4.10 and 5.1): the channel on screen, [key], plays from [candidate],
     * which it remembers when Remember What Works per Channel is on.
     */
    fun switchSource(key: String, candidate: String) {
        val current = tune?.takeIf { it.plan.channelId == key }
        if (current != null) memoryWork.trySend { data.memory.choose(current.plan, candidate) }
        player.switchSource(candidate)
    }

    /** A source picked in a channel's options before the channel played: Remember What Works hears of it then. */
    private var pickedSource: Pair<String, String>? = null

    /**
     * Live TV › channel options › Switch Source (brief 4.10 and 5.1): [channelId] plays from
     * [candidate] now if it's the channel playing, or else the next time it plays. Either way,
     * Remember What Works hears it was picked.
     */
    fun switchSourceFor(channelId: String, candidate: String) {
        val current = tune?.takeIf { it.plan.channelId == channelId }
        if (current != null) {
            memoryWork.trySend { data.memory.choose(current.plan, candidate) }
        } else {
            pickedSource = channelId to candidate
        }
        player.switchSource(channelId, candidate)
    }

    /**
     * Live TV › channel options › Change Number (brief 4.35 and 5.2): [channelId] has [number] in
     * the full list from now on, if no channel has it; otherwise a notice says which one does.
     */
    fun changeNumber(channelId: String, number: Int) {
        scope.launch {
            attempt {
                if (data.numbering.choose(channelId, number)) return@attempt
                val owner = numberOwners.first()[number] ?: return@attempt
                _notices.tryEmit(Notice(context.getString(R.string.number_taken_notice, number, owner), icon = PlasmaIcons.Hash))
            }
        }
    }

    /** The ids a channel's favorite may be kept under: its own, and a merged channel's on each of its sources (5.1). */
    private fun sameChannel(channelId: String): Set<String> = candidatesById.value[channelId].orEmpty().toSet() + channelId

    /**
     * Red, or Add to or Remove from Favorites (brief 5.2), for the channel the screens show as
     * [channelId]. A merged channel's favorite may be kept under another source's id (5.1).
     */
    fun setFavorite(channelId: String, favorite: Boolean) {
        scope.launch { attempt { data.channels.setFavorite(channelId, favorite, sameChannel(channelId)) } }
    }

    /** Move, on Home or in Live TV's Favorites (4.1 and 4.7): [channelId] comes right after [after], or first. */
    fun moveFavorite(channelId: String, after: String?) {
        scope.launch { attempt { data.channels.moveFavorite(sameChannel(channelId), after?.let(::sameChannel)) } }
    }

    /** Forget What Works, in Switch Source: the channel on screen, [key], goes back to its own order. */
    fun forgetSource(key: String) {
        val current = tune?.takeIf { it.plan.channelId == key } ?: return
        memoryWork.trySend { data.memory.forget(current.plan.candidates) }
    }

    /** What's remembered for the merged channel with [candidates] (Switch Source, brief 4.35). */
    fun remembered(candidates: List<String>): Flow<RememberedSource?> =
        combine(storedChoices, data.memory.observe(candidates)) { chosen, remembered ->
            remembered.takeIf { SettingsChoices(chosen.orEmpty()).rememberWorks }
        }

    /** Everything remembered, by the candidate id it's kept under (Live TV's Switch Source, 4.35). */
    val rememberedAll: Flow<Map<String, RememberedSource>> =
        combine(storedChoices, data.memory.observeAll()) { chosen, remembered ->
            if (SettingsChoices(chosen.orEmpty()).rememberWorks) remembered else emptyMap()
        }

    /** Forget What Works, in a channel's options: [channelId] goes back to its own order. */
    fun forgetSourceFor(channelId: String) {
        val candidates = tune?.plan?.takeIf { it.channelId == channelId }?.candidates
            ?: candidatesById.value[channelId]
            ?: return
        memoryWork.trySend { data.memory.forget(candidates) }
    }

    /** Audio Output as the engine takes it: Auto passes through what the TV or sound system takes, and decodes the rest (5.4.6). */
    private fun outputFor(chosen: AudioOutput): AudioOutput = when (chosen) {
        AudioOutput.Auto -> if (AudioRoutes.current(context).takesPassthrough) AudioOutput.Passthrough else AudioOutput.DecodeToPcm
        else -> chosen
    }

    /**
     * Playback for This Source (brief 4.20) over Settings': Player Engine, Buffer Size, Audio
     * Output and Use Stream Hub, and the referrer and extra headers its streams are asked for with.
     */
    private fun StreamTarget.withChoicesOf(source: Source?): StreamTarget {
        val playback = source?.playback ?: return this
        return copy(
            buffer = playback.bufferChoice?.let { BufferSize.entries.getOrNull(it) },
            audioOutput = playback.audioOutputChoice?.let { AudioOutput.entries.getOrNull(it) }?.let(::outputFor),
            engine = playback.engineChoice?.let(::engineOf),
            useHub = playback.useHub,
            headers = sourceHeaders(headers, playback, REFERER),
        )
    }

    /** A channel's stream, built at play time from its source (brief 6.5). */
    private suspend fun resolve(key: String): StreamTarget? = withContext(Dispatchers.IO) {
        if (StreamKeys.isVod(key)) return@withContext vodTarget(key)
        // A program from the archive (catch-up and Start Over, brief 5.5) goes straight to the
        // engine: it's a recording, and the hub's reconnects are for live streams.
        StreamKeys.archiveStart(key)?.let { start ->
            val channelId = StreamKeys.channelOf(key)
            // The provider's archive first; otherwise local catch-up, if it holds the programme (5.5).
            val stream = data.archive.streamOf(channelId, start) ?: return@withContext localTarget(key, channelId, start)
            val source = data.channels.get(channelId)?.let { data.sources.get(it.sourceId) }
            return@withContext StreamTarget(
                channelId = key,
                url = stream.url,
                headers = listOfNotNull(stream.userAgent?.let { USER_AGENT to it }, stream.referrer?.let { REFERER to it }).toMap(),
                throughHub = false,
            ).withChoicesOf(source)
        }
        val channelId = key
        val channel = data.channels.get(channelId) ?: return@withContext null
        val source = data.sources.get(channel.sourceId) ?: return@withContext null
        val target = when (source.type) {
            SourceType.Xtream -> {
                val format = StreamFormat.entries.firstOrNull { it.extension == source.streamFormat } ?: StreamFormat.Ts
                StreamTarget(
                    channelId = channelId,
                    url = data.sources.xtreamClient(source.id).liveUrl(channel.key, format),
                    headers = listOfNotNull(source.userAgent?.let { USER_AGENT to it }).toMap(),
                    // HLS manages its own segments, so it goes straight to the engine (5.4.2).
                    throughHub = format == StreamFormat.Ts,
                    budget = source.maxConnections?.let { ConnectionBudget(source.id, it) },
                ).withChoicesOf(source)
            }
            SourceType.Stalker -> {
                // The portal hands out an address that expires, so it's asked for at each tune (brief 7.1).
                val cmd = data.channels.streamOf(channelId)?.url ?: return@withContext null
                val portal = data.sources.stalkerClient(source.id)
                val url = portal.createLink(cmd)
                watchPortal()
                StreamTarget(
                    channelId = channelId,
                    url = url,
                    headers = portal.playbackHeaders,
                    throughHub = url.isHttp() && !url.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
                ).withChoicesOf(source)
            }
            SourceType.M3u -> {
                val stream = data.channels.streamOf(channelId) ?: return@withContext null
                // Multicast through the source's udpxy proxy, if it has one (5.4.10).
                val url = throughUdpxy(stream.url, source.playback.udpxy)
                StreamTarget(
                    channelId = channelId,
                    url = url,
                    // The playlist's own headers for the channel win over the source's (5.4.11).
                    headers = listOfNotNull(
                        (stream.userAgent ?: source.userAgent)?.let { USER_AGENT to it },
                        stream.referrer?.let { REFERER to it },
                    ).toMap(),
                    // HLS, and udp://, rtmp:// and the like, go straight to the engine; an address that
                    // only turns out to be HLS is handed over by the hub when it finds out.
                    throughHub = url.isHttp() && !url.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
                ).withChoicesOf(source)
            }
        }
        // A radio channel has no picture to wait for (5.4.3).
        target.copy(radio = channel.isRadio)
    }

    /**
     * The engine movie [movieId], in [hdr], plays with (5.4.3): the HDR rule, its source's Player
     * Engine, then Settings'. File Info says which (4.14).
     */
    suspend fun vodEngineFor(movieId: String, hdr: String?): EngineId {
        val sourceEngine = try {
            data.sources.get(movieId.substringBefore(':'))?.playback?.engineChoice?.let(::engineOf)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            null
        }
        return vodEngine(hdr, choices, sourceEngine)
    }

    /** The movie or episode the player's [key] is, if it's in a source's catalog. */
    private suspend fun playableOf(key: String): Playable? {
        val (kind, id) = StreamKeys.vodOf(key) ?: return null
        return when (kind) {
            StreamKeys.KIND_MOVIE -> data.vod.playable(Playable.Kind.Movie, id)
            StreamKeys.KIND_EPISODE -> data.vod.playable(Playable.Kind.Episode, id)
            // A Jellyfin server's (5.19).
            else -> null
        }
    }

    /** The recording's id in the player's [key], if it's a recording (5.6). */
    private fun recordingOf(key: String): String? = StreamKeys.vodOf(key)?.takeIf { it.first == StreamKeys.KIND_RECORDING }?.second

    /** The Jellyfin id in the player's [key], if it's a Jellyfin server's movie or episode (5.19). */
    private fun jellyfinOf(key: String): String? = StreamKeys.vodOf(key)?.takeIf { it.first == StreamKeys.KIND_JELLYFIN }?.second

    /** A movie's or episode's name, for the notices about it. */
    private suspend fun vodNameOf(key: String): String? =
        jellyfinOf(key)?.let(jellyfin::nameOf) ?: recordingOf(key)?.let { data.recordings.get(it)?.title } ?: playableOf(key)?.title

    /**
     * A movie's or episode's file at its provider (brief 5.9), or on a Jellyfin server (5.19). It
     * goes straight to the engine, which reads it as it needs and seeks in it; the hub is for live
     * streams. A portal hands out its address at play time, as it does a channel's (brief 7.1).
     */
    private suspend fun vodTarget(key: String): StreamTarget? {
        jellyfinOf(key)?.let { id -> return jellyfin.target(key, id) }
        // A recording plays from its file (brief 5.6), as it grows while it's written, or once
        // the file's gone, from its copy.
        recordingOf(key)?.let { id ->
            val recording = data.recordings.get(id) ?: return null
            // On its way to the network folder: from this TV, and the share (Record To › Network folder).
            recording.spool?.let { spool -> return spoolTarget(key, recording, File(spool)) }
            val file = recording.file?.let(::File)?.takeIf { it.exists() } ?: return shareTarget(key, recording)
            if (recording.status == RecordingStatus.Recording) return chaseTarget(key, recording, file)
            return StreamTarget(channelId = key, url = Uri.fromFile(file).toString(), throughHub = false)
        }
        val playable = playableOf(key) ?: return null
        val source = data.sources.get(playable.sourceId) ?: return null
        return when (source.type) {
            SourceType.Xtream -> {
                val client = data.sources.xtreamClient(source.id)
                StreamTarget(
                    channelId = key,
                    url = if (playable.kind == Playable.Kind.Movie) client.movieUrl(playable.remoteId, playable.extension) else client.episodeUrl(playable.remoteId, playable.extension),
                    headers = listOfNotNull(source.userAgent?.let { USER_AGENT to it }).toMap(),
                    throughHub = false,
                ).withChoicesOf(source)
            }
            SourceType.Stalker -> {
                val command = playable.command ?: return null
                val portal = data.sources.stalkerClient(source.id)
                val cmd = if (command.fileApart) portal.videoFile(playable.remoteId) ?: command.cmd else command.cmd
                val url = portal.videoLink(cmd, command.episode)
                watchPortal()
                StreamTarget(channelId = key, url = url, headers = portal.playbackHeaders, throughHub = false).withChoicesOf(source)
            }
            SourceType.M3u -> null
        }
    }

    /** The portal watchdog runs once a portal's channel, movie or episode has played. */
    private var portalWatch: Job? = null

    /**
     * While a portal's channel, movie or episode plays, tells the portal every minute or so that
     * the box is still there, as some portals want (brief 7.1); it stops when something else plays,
     * or nothing. A merged channel can play from a portal under another source's name: it's the
     * source playing that counts.
     */
    private fun watchPortal() {
        if (portalWatch != null) return
        portalWatch = scope.launch {
            combine(player.status, player.playingFrom) { status, from ->
                playingKey(status)?.let { key -> from?.takeIf { it.key == key }?.candidate ?: key }?.let(StreamKeys::channelOf)
            }
                .distinctUntilChanged()
                .collectLatest { channelId ->
                    // A channel's source, or a movie's or episode's.
                    val sourceId = channelId?.let { key -> if (StreamKeys.isVod(key)) playableOf(key)?.sourceId else data.channels.get(key)?.sourceId } ?: return@collectLatest
                    if (data.sources.get(sourceId)?.type != SourceType.Stalker) return@collectLatest
                    val portal = data.sources.stalkerClient(sourceId)
                    while (true) {
                        delay(portal.watchdogInterval.toMillis())
                        attempt { portal.watchdog() }
                    }
                }
        }
    }

    private fun playingKey(status: LiveStatus): String? = when (status) {
        is LiveStatus.Opening -> status.channelId
        is LiveStatus.Playing -> status.channelId
        is LiveStatus.Reconnecting -> status.channelId
        else -> null
    }

    /** The guide follows a source's channels, in the background (brief 6.4). */
    fun importGuide(sourceId: String) {
        scope.launch { attempt { data.importer.importGuide(sourceId) } }
    }

    /** A new source's guide, then its movies and series, follow its channels, in the background (brief 6.4). */
    fun importTheRest(sourceId: String) {
        scope.launch {
            attempt { data.importer.importGuide(sourceId) }
            attempt { data.importer.importVod(sourceId) }
        }
    }

    /** Refresh on the Guide's out-of-date line (brief 4.8): every source's guide again, but those turned off. */
    fun refreshGuides() {
        scope.launch { data.sources.all().filter { it.enabled }.forEach { attempt { data.importer.importGuide(it.id) } } }
    }

    /**
     * Refresh Now on a source (brief 4.20): its channels, then its guide, then its movies and
     * series. A test before it no longer says how it is.
     */
    fun refresh(sourceId: String) {
        _tests.update { it - sourceId }
        scope.launch {
            if (attempt { data.importer.importChannels(sourceId) }) {
                attempt { data.importer.importGuide(sourceId) }
                attempt { data.importer.importVod(sourceId) }
            }
        }
    }

    /** Test Connection on a source (brief 4.20): signs in, or asks for the playlist, and times it. */
    fun testSource(sourceId: String) {
        if (_tests.value[sourceId] == SourceTest.Testing) return
        _tests.update { it + (sourceId to SourceTest.Testing) }
        scope.launch {
            val result = try {
                SourceTest.Passed(data.sources.check(sourceId).toMillis().toInt())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                SourceTest.Failed(failed)
            }
            _tests.update { it + (sourceId to result) }
        }
    }

    /** Set as Primary (brief 4.20): the source Plasma tries first. */
    fun setPrimary(sourceId: String) {
        scope.launch { attempt { data.sources.setPrimary(sourceId) } }
    }

    /** Disable or Enable (brief 4.20). A source turned on again catches up if it's out of date. */
    fun setEnabled(sourceId: String, enabled: Boolean) {
        scope.launch {
            if (attempt { data.sources.setEnabled(sourceId, enabled) } && enabled) data.refreshStale()
        }
    }

    /**
     * A choice on one source's own settings, by the key and option Settings gives it: Playback for
     * This Source (brief 4.20) or its guide's time offset (5.16).
     */
    fun chooseForSource(sourceId: String, key: String, index: Int) {
        scope.launch {
            attempt {
                sourceChoices.withLock {
                    val source = data.sources.get(sourceId) ?: return@withLock
                    data.sources.setPlayback(sourceId, SourceSettings.chosen(source.playback.toInfo(), key, index).toPlayback())
                }
            }
        }
    }

    /** Something typed on Playback for This Source (brief 4.20): its user agent, referrer and headers, or udpxy proxy. */
    fun typeForSource(key: String, texts: List<String>) {
        val sourceId = SourceSettings.sourceOf(key) ?: return
        scope.launch {
            attempt {
                sourceChoices.withLock {
                    val source = data.sources.get(sourceId) ?: return@withLock
                    data.sources.setPlayback(sourceId, SourceSettings.typed(source.playback.toInfo(), key, texts).toPlayback())
                }
            }
        }
    }

    /**
     * A choice in Settings, stored. A new way of numbering, From your playlists or In playlist
     * order, numbers every channel afresh (brief 5.2); Custom starts from the numbers there are.
     * Merge Duplicate Channels turned on merges channels unmerged on Duplicate Numbers again.
     */
    fun choose(key: String, index: Int) {
        scope.launch {
            attempt {
                data.settings.choose(key, index)
                when {
                    key == SettingKeys.CHANNEL_NUMBERS && index != CUSTOM_NUMBERS -> data.numbering.numberAfresh()
                    key == SettingKeys.MERGE_DUPLICATES && index == ON -> data.settings.mergeAgain()
                }
            }
        }
    }

    /** A choice on Duplicate Numbers (brief 4.30), kept. */
    fun changeNumbers(change: suspend ChannelNumbering.() -> Unit) {
        scope.launch { attempt { data.numbering.change() } }
    }

    /** A Search result was opened: the query joins the recent searches (brief 5.10). */
    fun searched(query: String) {
        scope.launch { attempt { data.search.remember(query) } }
    }

    /**
     * Runs work whose failure leaves things as they were, or is noted already: an import notes it
     * on the source, for Settings to show.
     */
    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        false
    }

    /** Checks what's old while Plasma is in front, which as the TV's home can be for days. */
    private var refreshing: Job? = null

    /** Plasma's screens started and not stopped: two for a moment while a newer one takes over. */
    private var screensStarted = 0

    /**
     * Plasma is in front again: what's old is refreshed (5.1), and checked again every half hour
     * while it stays there, and the channel that was on plays again.
     */
    fun onActivityStart() {
        if (screensStarted++ > 0) return
        refreshing?.cancel()
        refreshing = scope.launch {
            while (true) {
                attempt { data.refreshStale() }
                delay(REFRESH_CHECK_MS)
            }
        }
        sleep.awake()
        standby.onActivityStart()
    }

    /** Plasma left the front, or the TV turned off (5.20). */
    fun onActivityStop() {
        if (screensStarted == 0 || --screensStarted > 0) return
        refreshing?.cancel()
        refreshing = null
        standby.onActivityStop()
    }

    private fun String.isHttp() = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private companion object {
        const val STOP_SHARING_MS = 5_000L
        const val HOUR_MS = 3_600_000L
        const val USB_LOOK_MS = 10_000L

        /** The rewind buffer's folder, inside the storage chosen. */
        const val REWIND_FOLDER = "rewind"

        /** Recordings' folder, inside the storage chosen. */
        const val RECORDINGS_FOLDER = "Recordings"

        /** Local catch-up's folder, inside the storage chosen (5.5). */
        const val LOCAL_FOLDER = "LocalCatchUp"

        /** How often what local catch-up holds, and the drive it goes to, is looked at again. */
        const val LOCAL_LOOK_MS = 60_000L

        /** A programme that began just before local catch-up's oldest moment plays from there. */
        const val LOCAL_SLACK_MS = 60_000L

        /** A programme the guide doesn't know runs for an hour. */
        val LOCAL_UNKNOWN_LENGTH: Duration = Duration.ofHours(1)

        const val MINUTE_MS = 60_000L

        /** When Space Runs Low makes this much room: two hours or so of HD. */
        const val ROOM_BYTES = 4_000_000_000L
        const val NOTICES = 4

        /** The "New duplicate numbers" notice stays a little longer (brief 4.35). */
        const val NEW_NUMBERS_MS = 6_000L

        /** So does "Can't reach nas.home.lan" (4.35). */
        const val SHARE_AWAY_MS = 6_000L

        /** After Copying's Keep on the Drive for 7 Days (5.6). */
        const val AFTER_COPYING_DAYS = 7L

        /** A guide picture bigger than this isn't one. */
        const val MAX_ARTWORK_BYTES = 8_000_000L

        /** How long the hub is given to say whether a recording's address is HLS. */
        const val PLAYLIST_LOOK_MS = 5_000L

        /** Channel Numbers' third option, Custom (4.29). */
        const val REFRESH_CHECK_MS = 30 * 60 * 1000L
        const val CUSTOM_NUMBERS = 2

        /** A switch's On, as Settings keeps it. */
        const val ON = 0
        const val USER_AGENT = "User-Agent"
        const val REFERER = "Referer"
    }
}

/** What local catch-up on a channel costs (4.31): [held] of [sourceName]'s connections already, of [limit]. */
data class LocalCost(val sourceName: String, val held: Int, val limit: Int?) {
    /** One more would leave no connection to watch with. */
    val leavesNone: Boolean get() = limit != null && held + 1 >= limit
}

/** Something to look again on: now, then every [everyMs]. */
private fun ticker(everyMs: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(everyMs)
    }
}

/** A new kids profile's ages: 7 and under (5.11). */
private const val KIDS_AGE = 7
