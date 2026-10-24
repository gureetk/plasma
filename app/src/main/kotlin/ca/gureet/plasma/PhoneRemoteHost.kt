// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import ca.gureet.plasma.core.data.Channel
import ca.gureet.plasma.core.data.PhoneRecord
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.launcher.HomeButton
import ca.gureet.plasma.launcher.HomeReason
import ca.gureet.plasma.live.Lists
import ca.gureet.plasma.live.LiveWorld
import ca.gureet.plasma.pairing.LanAddress
import ca.gureet.plasma.pairing.PairingInvite
import ca.gureet.plasma.player.BehindLive
import ca.gureet.plasma.player.LiveStatus
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.remote.PairedPhone
import ca.gureet.plasma.remote.PhoneStore
import ca.gureet.plasma.remote.RemoteHandler
import ca.gureet.plasma.remote.RemotePages
import ca.gureet.plasma.remote.RemoteServer
import ca.gureet.plasma.remote.RemoteTv
import ca.gureet.plasma.remote.protocol.RemoteChannel
import ca.gureet.plasma.remote.protocol.RemoteField
import ca.gureet.plasma.remote.protocol.RemoteKey
import ca.gureet.plasma.remote.protocol.RemoteList
import ca.gureet.plasma.remote.protocol.RemoteLists
import ca.gureet.plasma.remote.protocol.RemotePlaying
import ca.gureet.plasma.remote.protocol.RemoteState
import java.io.IOException
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ca.gureet.plasma.feature.settings.R as SettingsR

/**
 * The phone remote (brief 5.21): while Settings › Remote & Buttons › Phone Remote is on, Plasma's
 * server on the TV's home network address, following it as it changes, and what paired phones
 * send it. Keys reach Plasma as the remote's own would; a channel tapped on a phone opens; text
 * goes into the field taking it. What the TV shows goes back to every phone.
 */
class PhoneRemoteHost(private val graph: AppGraph, private val context: Context, private val http: () -> OkHttpClient) : RemoteHandler {
    private val scope: CoroutineScope = graph.scope

    private val _invite = MutableStateFlow<PairingInvite?>(null)

    /** The address, code and QR code Settings › Remote & Buttons › Phone Remote shows; null while there are none. */
    val invite: StateFlow<PairingInvite?> = _invite.asStateFlow()

    private val _available = MutableStateFlow(true)

    /** False when the TV isn't on a network, so phones can't reach it. */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _connected = MutableStateFlow<Set<String>>(emptySet())

    /** The paired phones connected now, by id. */
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    private var job: Job? = null

    @Volatile private var server: RemoteServer? = null

    /** The channels as the TV has them now, for the Channels tab and the logos. */
    @Volatile private var world: LiveWorld? = null

    /** The channel playing now, for the Channels tab's speaker. */
    @Volatile private var playingChannel: String? = null

    /** Keys held on a phone: each repeats as a held remote key does until it's let go. */
    private val held = ConcurrentHashMap<RemoteKey, Job>()

    /** When each held key went down, for its repeats and its up. */
    private val downAt = ConcurrentHashMap<RemoteKey, Long>()

    /** Logos the TV fetched for phones, by channel, so a phone never asks the provider (5.21). */
    private val logos = object : LinkedHashMap<String, Pair<ByteArray, String>>(LOGOS, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<ByteArray, String>>?) = size > LOGOS
    }

    private val store = object : PhoneStore {
        override suspend fun phones(): List<PairedPhone> = graph.data.settings.phonesNow().map { PairedPhone(it.id, it.name, it.tokenHash, it.pairedAt, it.lastSeen) }

        override suspend fun save(phone: PairedPhone) =
            graph.data.settings.savePhone(PhoneRecord(phone.id, phone.name, phone.tokenHash, phone.pairedAt, phone.lastSeen))

        override suspend fun remove(id: String) = graph.data.settings.removePhone(id)
    }

    /** Phone Remote is on: the server runs on the TV's home network address, moving when it changes. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            homeAddresses().collectLatest { address ->
                _available.value = address != null
                if (address != null) serve(address)
            }
        }
    }

    /** Phone Remote is off: phones can't reach the TV, and connected ones are let go. */
    fun stop() {
        job?.cancel()
        job = null
        _available.value = true
    }

    private suspend fun serve(address: InetAddress) = coroutineScope {
        val tv = RemoteTv(graph.data.settings.tvId(), tvName(context), Updates.versionOf(context))
        val pages = RemotePages(texts(context), context.resources.configuration.locales[0].toLanguageTag())
        val server = RemoteServer(tv, store, this@PhoneRemoteHost, pages, unnamed = context.getString(R.string.phone_unnamed), onPaired = ::paired)
        val started = withContext(Dispatchers.IO) {
            try {
                server.start(address, this@coroutineScope)
                true
            } catch (failed: IOException) {
                false
            }
        }
        if (!started) {
            _available.value = false
            return@coroutineScope
        }
        this@PhoneRemoteHost.server = server
        try {
            launch { server.invite.collect { _invite.value = it } }
            launch { server.connected.collect { _connected.value = it } }
            launch { graph.world.collect { world = it } }
            states().collect { state ->
                playingChannel = state.playing?.channelId
                withContext(Dispatchers.IO) { server.publish(state) }
            }
        } finally {
            this@PhoneRemoteHost.server = null
            _invite.value = null
            _connected.value = emptySet()
            withContext(NonCancellable + Dispatchers.IO) { server.close() }
        }
    }

    /** The TV's private address now, and again each time the network changes; null off the network. */
    @OptIn(FlowPreview::class)
    private fun homeAddresses(): Flow<InetAddress?> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                trySend(Unit)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                trySend(Unit)
            }
        }
        trySend(Unit)
        val watching = connectivity != null && runCatching { connectivity.registerDefaultNetworkCallback(callback) }.isSuccess
        awaitClose { if (watching) runCatching { connectivity?.unregisterNetworkCallback(callback) } }
    }.debounce(NETWORK_SETTLE_MS).map { LanAddress.find() }.flowOn(Dispatchers.IO).distinctUntilChanged()

    /** Forget (5.21): the phone stops being a remote at once. */
    fun forget(phoneId: String) {
        scope.launch(Dispatchers.IO) {
            val running = server
            if (running != null) running.forget(phoneId) else graph.data.settings.removePhone(phoneId)
        }
    }

    /** Rename: the TV's own name for the phone. */
    fun rename(phoneId: String, name: String) {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return
        scope.launch {
            val phone = graph.data.settings.phonesNow().firstOrNull { it.id == phoneId } ?: return@launch
            graph.data.settings.savePhone(phone.copy(name = clean))
        }
    }

    /** "Maya's iPhone is now a remote" (5.21). */
    private fun paired(phone: PairedPhone) {
        scope.launch { graph.notice(Notice(context.getString(R.string.phone_paired, phone.name), PlasmaIcons.Phone)) }
    }

    // ---- What the TV shows -----------------------------------------------------------------------

    /** Whether Plasma's in front, what plays, the colour keys' labels and the field taking text. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun states(): Flow<RemoteState> {
        val screen = snapshotFlow {
            val mirror = graph.remoteMirror
            val field = mirror.field?.let { RemoteField(it.label, it.text(), it.results(), if (it.search) RemoteField.SEARCH else RemoteField.TEXT) }
            field to mirror.colorKeys
        }
        val playing = graph.playerIfMade.flatMapLatest { player ->
            if (player == null) flowOf(null) else combine(player.status, player.behindLive) { status, behind -> Playing(keyOf(status), behind) }
        }
        return combine(graph.standby.inFrontNow, playing, graph.world, screen) { front, now, world, (field, keys) ->
            RemoteState(front = front, playing = now?.key?.let { key -> world?.let { phonePlaying(it, key, now.behind) } }, keys = keys, field = field)
        }.distinctUntilChanged()
    }

    private class Playing(val key: String?, val behind: BehindLive?)

    private fun keyOf(status: LiveStatus): String? = when (status) {
        is LiveStatus.Opening -> status.channelId
        is LiveStatus.Playing -> status.channelId
        is LiveStatus.Reconnecting -> status.channelId
        else -> null
    }

    // ---- What phones send ------------------------------------------------------------------------

    override fun key(key: RemoteKey, down: Boolean) {
        scope.launch { press(key, down) }
    }

    /** A phone went away: any key it was holding is let go. */
    override fun disconnected() {
        scope.launch {
            held.values.forEach { it.cancel() }
            held.clear()
            downAt.clear()
        }
    }

    private fun press(key: RemoteKey, down: Boolean) {
        held.remove(key)?.cancel()
        if (graph.standby.isOnScreen) inPlasma(key, down) else outside(key, down)
    }

    /** In Plasma, a key goes into its own key handling, as from the remote (5.21): no permission needed. */
    private fun inPlasma(key: RemoteKey, down: Boolean) {
        if (key == RemoteKey.Home) {
            // What Plasma's Home button does (5.13).
            if (down) graph.opened(HomeReason.Home)
            return
        }
        val code = codeOf(key)
        val target = keyTarget() ?: return
        val at = SystemClock.uptimeMillis()
        if (!down) {
            target(KeyEvent(downAt.remove(key) ?: at, at, KeyEvent.ACTION_UP, code, 0))
            return
        }
        downAt[key] = at
        target(KeyEvent(at, at, KeyEvent.ACTION_DOWN, code, 0))
        // Held, it repeats as a remote's key does: long-press OK, and a held D-pad that keeps moving.
        held[key] = scope.launch {
            delay(ViewConfiguration.getKeyRepeatTimeout().toLong())
            for (repeat in 1..MAX_REPEATS) {
                val now = keyTarget() ?: break
                now(KeyEvent(at, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, repeat))
                delay(ViewConfiguration.getKeyRepeatDelay().toLong())
            }
        }
    }

    /** Where a key goes: a popup with focus, such as a reminder, or Plasma's screen. */
    private fun keyTarget(): ((KeyEvent) -> Unit)? {
        val popup = graph.keyWindows.newest()
        if (popup != null) return { event -> popup.rootView.dispatchKeyEvent(event) }
        val screen: Activity = graph.screenNow() ?: return null
        return { event -> screen.window.callback?.dispatchKeyEvent(event) ?: screen.dispatchKeyEvent(event) }
    }

    /**
     * Outside Plasma (5.21): play, pause and seeking go to the app playing; the D-pad, OK, Back and
     * Home go through the Home Button Override while it's on. The rest work only in Plasma.
     */
    private fun outside(key: RemoteKey, down: Boolean) {
        when (key) {
            RemoteKey.PlayPause, RemoteKey.Rewind, RemoteKey.FastForward -> {
                val audio = context.getSystemService(AudioManager::class.java) ?: return
                audio.dispatchMediaKeyEvent(KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, codeOf(key)))
            }
            RemoteKey.Back -> if (down) HomeButton.globalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            RemoteKey.Home -> if (down && !HomeButton.globalAction(AccessibilityService.GLOBAL_ACTION_HOME)) openPlasma(HomeReason.Home)
            RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right, RemoteKey.Ok -> if (down) dpadOutside(key)
            else -> Unit
        }
    }

    /** Android 13 and later have global D-pad actions; a held arrow keeps moving. */
    private fun dpadOutside(key: RemoteKey) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val action = when (key) {
            RemoteKey.Up -> AccessibilityService.GLOBAL_ACTION_DPAD_UP
            RemoteKey.Down -> AccessibilityService.GLOBAL_ACTION_DPAD_DOWN
            RemoteKey.Left -> AccessibilityService.GLOBAL_ACTION_DPAD_LEFT
            RemoteKey.Right -> AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT
            else -> AccessibilityService.GLOBAL_ACTION_DPAD_CENTER
        }
        if (!HomeButton.globalAction(action) || key == RemoteKey.Ok) return
        held[key] = scope.launch {
            delay(ViewConfiguration.getKeyRepeatTimeout().toLong())
            for (repeat in 1..MAX_REPEATS) {
                if (graph.standby.isOnScreen || !HomeButton.globalAction(action)) break
                delay(OUTSIDE_REPEAT_MS)
            }
        }
    }

    /** Brings Plasma to the front, as the override does; Android may not let it from the background. */
    private fun openPlasma(reason: HomeReason) {
        val pm = context.packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(context.packageName) ?: pm.getLaunchIntentForPackage(context.packageName) ?: return
        launch.putExtra(HomeButton.EXTRA_REASON, reason.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            context.startActivity(launch)
        } catch (refused: RuntimeException) {
            // Android turned the start down.
        }
    }

    override fun text(text: String) {
        scope.launch { graph.remoteMirror.field?.onText(text) }
    }

    override fun submit() {
        scope.launch { graph.remoteMirror.field?.onSubmit() }
    }

    override fun tune(channelId: String, listId: String?) {
        scope.launch {
            if (world?.byId?.containsKey(channelId) != true) return@launch
            if (!graph.standby.isOnScreen) openPlasma(HomeReason.Home)
            graph.tuneFromPhone(channelId, listId)
        }
    }

    override fun goLive() {
        scope.launch { graph.playerNow()?.goLive() }
    }

    /** The profile's lists for the Channels tab, numbered as the TV numbers them (5.2), 200 channels at a time. */
    override fun lists(listId: String?, from: Int): RemoteLists? =
        phoneLists(world ?: return null, listId, from, playingChannel, context.getString(R.string.favorites), context.getString(R.string.all_channels))

    /** A channel's logo, fetched by the TV and kept a while; a phone never asks the provider (5.21). */
    override fun logo(channelId: String): Pair<ByteArray, String>? {
        synchronized(logos) { logos[channelId] }?.let { return it }
        val url = world?.byId?.get(channelId)?.logo?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        val fetched = try {
            http().newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                val type = body.contentType()?.toString()?.takeIf { it.startsWith("image/") }
                if (!response.isSuccessful || type == null || body.contentLength() > MAX_LOGO) {
                    null
                } else {
                    body.bytes().takeIf { it.size <= MAX_LOGO }?.let { it to type }
                }
            }
        } catch (failed: IOException) {
            null
        } catch (bad: IllegalArgumentException) {
            null
        } ?: return null
        synchronized(logos) { logos[channelId] = fetched }
        return fetched
    }

    private fun codeOf(key: RemoteKey): Int = when (key) {
        RemoteKey.Up -> KeyEvent.KEYCODE_DPAD_UP
        RemoteKey.Down -> KeyEvent.KEYCODE_DPAD_DOWN
        RemoteKey.Left -> KeyEvent.KEYCODE_DPAD_LEFT
        RemoteKey.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
        RemoteKey.Ok -> KeyEvent.KEYCODE_DPAD_CENTER
        RemoteKey.Back -> KeyEvent.KEYCODE_BACK
        RemoteKey.Home -> KeyEvent.KEYCODE_HOME
        RemoteKey.Menu -> KeyEvent.KEYCODE_MENU
        RemoteKey.PlayPause -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        RemoteKey.Rewind -> KeyEvent.KEYCODE_MEDIA_REWIND
        RemoteKey.FastForward -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        RemoteKey.ChannelUp -> KeyEvent.KEYCODE_CHANNEL_UP
        RemoteKey.ChannelDown -> KeyEvent.KEYCODE_CHANNEL_DOWN
        RemoteKey.Red -> KeyEvent.KEYCODE_PROG_RED
        RemoteKey.Green -> KeyEvent.KEYCODE_PROG_GREEN
        RemoteKey.Yellow -> KeyEvent.KEYCODE_PROG_YELLOW
        RemoteKey.Blue -> KeyEvent.KEYCODE_PROG_BLUE
        RemoteKey.Zero -> KeyEvent.KEYCODE_0
        RemoteKey.One -> KeyEvent.KEYCODE_1
        RemoteKey.Two -> KeyEvent.KEYCODE_2
        RemoteKey.Three -> KeyEvent.KEYCODE_3
        RemoteKey.Four -> KeyEvent.KEYCODE_4
        RemoteKey.Five -> KeyEvent.KEYCODE_5
        RemoteKey.Six -> KeyEvent.KEYCODE_6
        RemoteKey.Seven -> KeyEvent.KEYCODE_7
        RemoteKey.Eight -> KeyEvent.KEYCODE_8
        RemoteKey.Nine -> KeyEvent.KEYCODE_9
    }

    companion object {
        /** The TV's name, as Android has it ("Living Room TV"). */
        fun tvName(context: Context): String =
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

        /** The phone page's words, in the TV's language, by the names the page uses ([RemotePages.KEYS]). */
        fun texts(context: Context): Map<String, String> = WORDS.mapValues { (_, id) -> context.getString(id) }

        private val WORDS: Map<String, Int> = mapOf(
            "title" to R.string.remote_page_title,
            "appName" to R.string.remote_page_app_name,
            "shortName" to R.string.remote_page_short_name,
            "remote" to R.string.remote_page_remote,
            "channels" to R.string.remote_page_channels,
            "type" to R.string.remote_page_type,
            "back" to R.string.remote_page_back,
            "home" to R.string.remote_page_home,
            "menu" to R.string.remote_page_menu,
            "numbers" to R.string.remote_page_numbers,
            "ok" to R.string.remote_page_ok,
            "close" to R.string.remote_page_close,
            "up" to R.string.remote_page_up,
            "down" to R.string.remote_page_down,
            "left" to R.string.remote_page_left,
            "right" to R.string.remote_page_right,
            "rewind" to R.string.remote_page_rewind,
            "playPause" to R.string.remote_page_play_pause,
            "fastForward" to R.string.remote_page_fast_forward,
            "channelUp" to R.string.remote_page_channel_up,
            "channelDown" to R.string.remote_page_channel_down,
            "red" to R.string.remote_page_red,
            "green" to R.string.remote_page_green,
            "yellow" to R.string.remote_page_yellow,
            "blue" to R.string.remote_page_blue,
            "goLive" to R.string.remote_page_go_live,
            "behindLive" to R.string.remote_page_behind_live,
            "pairTitle" to R.string.remote_page_pair_title,
            "pairText" to R.string.remote_page_pair_text,
            "codeLabel" to SettingsR.string.phone_code_label,
            "nameLabel" to R.string.remote_page_name,
            "pairButton" to R.string.remote_page_pair,
            "wrongCode" to SettingsR.string.phone_wrong_code,
            "tooManyTries" to SettingsR.string.phone_too_many_tries,
            "androidPhone" to R.string.remote_page_android_phone,
            "computer" to R.string.remote_page_computer,
            "connecting" to R.string.remote_page_connecting,
            "unreachable" to R.string.remote_page_unreachable,
            "unreachableUnnamed" to R.string.remote_page_unreachable_unnamed,
            "searchChannels" to R.string.remote_page_search_channels,
            "countsFromOne" to R.string.remote_page_counts_from_one,
            "typeTitle" to R.string.remote_page_type_title,
            "typingInto" to R.string.remote_page_typing_into,
            "typeText" to R.string.remote_page_type_text,
            "nothingToType" to R.string.remote_page_nothing_to_type,
            "typingElsewhere" to R.string.remote_page_typing_elsewhere,
            "tapToType" to R.string.remote_page_tap_to_type,
            "paste" to R.string.remote_page_paste,
            "clear" to R.string.remote_page_clear,
            "done" to SettingsR.string.done,
            "resultsOnTv" to R.string.remote_page_results_on_tv,
        )

        /** A key "held" for good, its up lost with the phone, stops after about half a minute. */
        private const val MAX_REPEATS = 600

        /** Global D-pad actions repeat a little slower than keys: other apps animate each one. */
        private const val OUTSIDE_REPEAT_MS = 120L

        private const val LOGOS = 300
        private const val LOAD_FACTOR = 0.75f
        private const val MAX_LOGO = 256 * 1024L
        private const val MAX_NAME = 40

        /** Network changes come in bursts; the address is read once they've settled. */
        private const val NETWORK_SETTLE_MS = 500L
    }
}

/**
 * What plays, as the phone's Remote tab shows it (5.21): live TV with its timeline (how far into
 * the program live is, and the picture is) and how far behind live it is; a program from the
 * archive by its title; nothing for a movie or a recording.
 */
internal fun phonePlaying(world: LiveWorld, key: String, behind: BehindLive?): RemotePlaying? {
    if (StreamKeys.isVod(key)) return null
    val channel = world.byId[StreamKeys.channelOf(key)] ?: return null
    val archive = StreamKeys.archiveStart(key)
    if (archive != null) {
        val programme = world.programmesOf(channel).firstOrNull { it.start == archive }
        return RemotePlaying(channel.id, channel.number.toString(), channel.name, programme?.title)
    }
    val now = world.now
    val behindMs = behind?.behindMs(now.toEpochMilli()) ?: 0
    val programme = world.nowNext(channel).now
    return RemotePlaying(
        channelId = channel.id,
        number = channel.number.toString(),
        name = channel.name,
        title = programme?.title,
        live = programme?.let { progressOf(it.start, it.end, now) },
        progress = programme?.let { progressOf(it.start, it.end, now.minusMillis(behindMs)) },
        behindLive = behindMs / SECOND_MS,
        paused = behind?.paused == true,
    )
}

/**
 * The profile's lists for a phone's Channels tab (5.21): Favorites, the groups with channels, then
 * All Channels, and [from] onward of one list's channels, 200 at a time, numbered as the TV numbers
 * them (5.2). [listId] null or gone: Favorites, or the first list with channels.
 */
internal fun phoneLists(world: LiveWorld, listId: String?, from: Int, playing: String?, favorites: String, allChannels: String): RemoteLists {
    val lists = buildList {
        add(RemoteList(Lists.FAVORITES, favorites, world.list(Lists.FAVORITES).count { !it.hidden }, world.countsFromOne(Lists.FAVORITES)))
        for (group in world.groups.filter { !it.hidden }) {
            val count = world.list(group.id).count { !it.hidden }
            if (count > 0) add(RemoteList(group.id, group.name, count, world.countsFromOne(group.id)))
        }
        add(RemoteList(Lists.ALL, allChannels, world.channels.count { !it.hidden }, world.countsFromOne(Lists.ALL)))
    }
    val id = listId?.takeIf { wanted -> lists.any { it.id == wanted } } ?: lists.firstOrNull { it.count > 0 }?.id ?: Lists.ALL
    val countsFromOne = world.countsFromOne(id)
    // Hidden channels keep their places, as on the TV, and aren't sent.
    val numbered = world.list(id).mapIndexedNotNull { index, channel -> (channel to if (countsFromOne) index + 1 else channel.number).takeUnless { channel.hidden } }
    val page = numbered.drop(from.coerceAtLeast(0)).take(PHONE_PAGE).map { (channel, number) -> phoneChannel(world, channel, number, playing) }
    return RemoteLists(lists, id, from, page)
}

private fun phoneChannel(world: LiveWorld, channel: Channel, number: Int, playing: String?): RemoteChannel {
    val now = world.nowNext(channel).now
    return RemoteChannel(
        id = channel.id,
        number = number.toString(),
        name = channel.name,
        now = now?.title,
        progress = now?.let { progressOf(it.start, it.end, world.now) },
        playing = playing != null && (channel.id == playing || playing in channel.candidates),
    )
}

private fun progressOf(start: Instant, end: Instant, at: Instant): Float {
    val length = Duration.between(start, end).toMillis().coerceAtLeast(1)
    return (Duration.between(start, at).toMillis().toFloat() / length).coerceIn(0f, 1f)
}

/** Channels a phone gets at a time (5.21). */
private const val PHONE_PAGE = 200
private const val SECOND_MS = 1000L

/** Plasma's popups that take keys, such as a reminder: a phone's key goes to the newest one, over the screen. */
class KeyWindows {
    private val popups = CopyOnWriteArrayList<View>()

    fun opened(view: View) {
        popups += view
    }

    fun closed(view: View) {
        popups -= view
    }

    fun newest(): View? = popups.lastOrNull()
}

/** A channel tapped on a phone (5.21), in its list; when, by the boot clock, so an old one isn't opened later. */
data class PhoneTune(val channelId: String, val listId: String, val at: Long)

/** In a popup that takes keys, such as a reminder: a phone's keys go to it while it shows (5.21). */
@Composable
fun PhoneKeysHere(graph: AppGraph) {
    val view = LocalView.current
    DisposableEffect(graph, view) {
        graph.keyWindows.opened(view)
        onDispose { graph.keyWindows.closed(view) }
    }
}
