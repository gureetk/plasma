// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import ca.gureet.plasma.core.data.JellyfinAccount
import ca.gureet.plasma.core.data.Playable
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.model.PlayJellyfinIn
import ca.gureet.plasma.core.model.SettingsChoices
import ca.gureet.plasma.feature.settings.JellyfinServerChoice
import ca.gureet.plasma.feature.settings.JellyfinSignIn
import ca.gureet.plasma.feature.settings.JellyfinSignInOutcome
import ca.gureet.plasma.feature.settings.JellyfinSignInProblem
import ca.gureet.plasma.integrations.jellyfin.JellyfinApp
import ca.gureet.plasma.integrations.jellyfin.JellyfinClient
import ca.gureet.plasma.integrations.jellyfin.JellyfinException
import ca.gureet.plasma.integrations.jellyfin.JellyfinItem
import ca.gureet.plasma.integrations.jellyfin.JellyfinLogin
import ca.gureet.plasma.integrations.jellyfin.JellyfinProblem
import ca.gureet.plasma.integrations.jellyfin.JellyfinServer
import ca.gureet.plasma.integrations.jellyfin.JellyfinTitle
import ca.gureet.plasma.live.JellyfinFeed
import ca.gureet.plasma.live.VodShelf
import ca.gureet.plasma.live.continueSince
import ca.gureet.plasma.live.forContinue
import ca.gureet.plasma.live.playable
import ca.gureet.plasma.player.StreamKeys
import ca.gureet.plasma.player.StreamTarget
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Plasma's link with the profile's Jellyfin server (brief 5.19), read directly with Jellyfin's
 * SDK: signing in, as the sign-in sheet asks it to; the server's items for Continue Watching;
 * opening one; and Forget Server.
 */
class JellyfinLink(private val context: Context, private val graph: AppGraph) : JellyfinSignIn {
    private val client: JellyfinClient by lazy { JellyfinClient(context, versionOf(context), graph.data.http) }

    /** The profile's sign-in, or null. */
    val account: StateFlow<JellyfinAccount?> = graph.data.jellyfin.observe().stateIn(graph.scope, SharingStarted.Eagerly, null)

    private val feed = JellyfinFeed(
        clock = graph.clock,
        // The server answering again clears "Jellyfin is offline".
        read = { account, nextUp, since -> client.continueItems(account.login(), nextUp, since).also { _offline.value = false } },
        onSignedOut = ::signedOut,
    )

    /** Goes up when something played, so the items are read again with its new place. */
    private val feedReads = MutableStateFlow(0)

    /** What changes the items read: Show Next Episodes and Hide Items Older Than (4.32). */
    private val rowChoices = graph.storedChoices.map { SettingsChoices(it.orEmpty()).let { chosen -> chosen.continueNextEpisodes to chosen.hideOlderDays } }
        .distinctUntilChanged()

    /**
     * The server's items, read while a screen shows them (Home, and Continue Watching's settings)
     * and every 15 minutes; empty while not signed in.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<JellyfinItem>> = combine(account, rowChoices, feedReads) { account, choices, _ -> account to choices.first }
        .flatMapLatest { (account, nextUp) -> feed.items(account, nextUp, continueSince(graph.choices, graph.clock.now())) }
        .stateIn(graph.scope, SharingStarted.WhileSubscribed(STOP_SHARING_MS), emptyList())

    /** What Continue Watching shows of them: not hidden from Plasma, and not older than Hide Items Older Than allows. */
    val shown: Flow<List<JellyfinItem>> = combine(items, graph.data.settings.hiddenJellyfin) { items, hidden ->
        items.forContinue(hidden, continueSince(graph.choices, graph.clock.now()))
    }

    private val _offline = MutableStateFlow(false)

    /**
     * Whether the server didn't answer when an item was opened (5.19): its items say "Jellyfin is
     * offline" until it answers again.
     */
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /**
     * OK on a Jellyfin item: it plays in Plasma, or opens in the Jellyfin app where Play Jellyfin
     * Items In says so and the TV has the app (5.19), what plays going to the small window (5.20).
     * [play] opens Plasma's player on it.
     */
    fun open(id: String, play: (key: String) -> Unit) {
        val key = StreamKeys.jellyfin(id)
        if (playsInPlasma()) return play(key)
        graph.smallWindow.thenOpen { if (!JellyfinApp.open(context, id)) play(key) }
    }

    /** Whether OK plays a Jellyfin item in Plasma: as Play Jellyfin Items In says, or without the Jellyfin app on the TV. */
    fun playsInPlasma(): Boolean = graph.choices.playJellyfinIn == PlayJellyfinIn.Plasma || !JellyfinApp.installed(context)

    // ---- Playing in Plasma (brief 5.19) -------------------------------------------------------------

    /** The titles read for the player, by id, with where each plays from. */
    private val titles = ConcurrentHashMap<String, JellyfinTitle>()

    /** Why a title couldn't be read, by id, for the player's message. */
    private val problems = ConcurrentHashMap<String, JellyfinProblem>()

    /** Each title's play session, from its start to its stop, for the server's reports. */
    private val sessions = ConcurrentHashMap<String, String>()

    /** Reads [id] for the player; null, with the reason kept, when the server can't say. */
    private suspend fun title(id: String): JellyfinTitle? {
        val login = account.value?.login() ?: return null.also { problems[id] = JellyfinProblem.SignedOut }
        return try {
            client.title(login, id).also {
                titles[id] = it
                problems.remove(id)
                _offline.value = false
            }
        } catch (failed: JellyfinException) {
            problems[id] = failed.problem
            when (failed.problem) {
                JellyfinProblem.CantReach -> _offline.value = true
                JellyfinProblem.SignedOut -> account.value?.let { signedOut(it) }
                else -> Unit
            }
            null
        }
    }

    /** What the player says when [id] couldn't be read: the server's offline, it signed Plasma out, or the title's gone. */
    fun problemOf(id: String): String = when (problems[id]) {
        JellyfinProblem.CantReach -> context.getString(R.string.jellyfin_offline_play, account.value?.serverName.orEmpty())
        JellyfinProblem.SignedOut -> context.getString(R.string.jellyfin_sign_in_again)
        else -> context.getString(R.string.jellyfin_title_gone)
    }

    /** What the player plays [id] from: its file as it is, straight to the engine. */
    suspend fun target(key: String, id: String): StreamTarget? {
        val login = account.value?.login() ?: return null
        val title = titles[id] ?: title(id) ?: return null
        return StreamTarget(channelId = key, url = client.streamUrl(login, title), throughHub = false)
    }

    /** [id]'s HDR format, for the HDR rule (5.4.3). */
    fun hdrOf(id: String): String? = titles[id]?.video?.hdr

    /** [id]'s name, for the notices about it. */
    fun nameOf(id: String): String? = titles[id]?.title

    /** Jellyfin's movies and episodes for Plasma's player: their places go to the server (5.19). */
    val shelf: VodShelf = object : VodShelf {
        override fun keyOf(id: String): String = StreamKeys.jellyfin(id)

        override suspend fun playable(id: String): Playable? = title(id)?.playable()

        override suspend fun next(episode: Playable): Playable? {
            val login = account.value?.login() ?: return null
            val title = titles[episode.id] ?: return null
            val next = try {
                client.nextEpisode(login, title)
            } catch (failed: JellyfinException) {
                null
            } ?: return null
            titles[next.id] = next
            return next.playable()
        }

        override suspend fun resumeFrom(playable: Playable): Duration = Duration.ofMillis(titles[playable.id]?.positionMs ?: 0)

        override suspend fun started(playable: Playable, at: Duration) {
            val login = account.value?.login() ?: return
            val title = titles[playable.id] ?: return
            val session = UUID.randomUUID().toString().also { sessions[playable.id] = it }
            report { client.started(login, title, session, at.toMillis()) }
        }

        override suspend fun keep(playable: Playable, at: Duration, total: Duration, paused: Boolean, stopped: Boolean) {
            val login = account.value?.login() ?: return
            val title = titles[playable.id] ?: return
            val session = sessions[playable.id] ?: return
            if (stopped) {
                sessions.remove(playable.id)
                report { client.stopped(login, title, session, at.toMillis()) }
                // Home's row reads the new place.
                feedReads.update { it + 1 }
            } else {
                report { client.progress(login, title, session, at.toMillis(), paused) }
            }
        }
    }

    /** Reports to the server; one that doesn't get there is let go, as the next one carries the place. */
    private suspend fun report(send: suspend () -> Unit) {
        try {
            send()
        } catch (failed: JellyfinException) {
            // Offline for a moment, or signed out: the place is kept when it's back.
        }
    }

    /** Hide from Plasma on a Jellyfin item, until it's watched again. */
    fun hide(id: String) {
        val watched = items.value.firstOrNull { it.id == id }?.lastWatched
        graph.scope.launch { runCatching { graph.data.settings.hideJellyfin(id, watched) } }
    }

    // ---- Recordings for Jellyfin (brief 5.6) ------------------------------------------------------

    /**
     * Whether the profile's sign-in is the server's administrator, so Plasma can ask it to look for
     * new recordings (Settings › Recording says which); null while it's not known, or not signed in.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val administrator: StateFlow<Boolean?> = account
        .mapLatest { now ->
            now ?: return@mapLatest null
            try {
                client.isAdministrator(now.login())
            } catch (failed: JellyfinException) {
                null
            }
        }
        .stateIn(graph.scope, SharingStarted.WhileSubscribed(STOP_SHARING_MS), null)

    /** When the server was last asked to look for new recordings, in [SystemClock.elapsedRealtime] ms. */
    @Volatile private var scannedAt: Long? = null

    /** A scan waiting for the 10 minutes to pass. */
    private var scanning: Job? = null

    /**
     * A recording's copy is on the network folder: the server is asked to look for it in the
     * libraries with [folders], at most once every 10 minutes (brief 5.6). Without an
     * administrator's sign-in it's refused, and Jellyfin finds it on its own next scan.
     */
    fun recordingCopied(folders: Set<String>) {
        graph.scope.launch {
            if (scanning?.isActive == true) return@launch
            scanning = launch {
                val last = scannedAt
                if (last != null) delay(last + SCAN_EVERY_MS - SystemClock.elapsedRealtime())
                scannedAt = SystemClock.elapsedRealtime()
                val login = account.value?.login() ?: return@launch
                report { client.scanRecordings(login, folders) }
            }
        }
    }

    /** Forget Server: Plasma signs out on the server too, so the token stops working (6.10), then forgets it. */
    fun forget() {
        val now = account.value ?: return
        graph.scope.launch {
            client.signOut(now.login())
            graph.data.jellyfin.forget()
            graph.notice(Notice(context.getString(R.string.jellyfin_forgotten, JellyfinServerChoice(now.server, now.serverName).where), PlasmaIcons.Server))
        }
    }

    /** The server signed Plasma out: its sign-in goes, unless it's been signed in again meanwhile. */
    private suspend fun signedOut(gone: JellyfinAccount) {
        if (account.value?.token == gone.token) graph.data.jellyfin.forget()
        graph.notice(Notice(context.getString(R.string.jellyfin_signed_out, gone.serverName), PlasmaIcons.Server, Notice.LONG_MS))
    }

    // ---- The sign-in sheet's steps (brief 4.32) ----------------------------------------------------

    override fun discover(): Flow<JellyfinServerChoice> = client.discover().map { JellyfinServerChoice(it.address, it.name) }

    override suspend fun find(typed: String): JellyfinServerChoice? = client.find(typed)?.let { JellyfinServerChoice(it.address, it.name) }

    override suspend fun quickConnect(server: JellyfinServerChoice, onCode: (String) -> Unit): JellyfinSignInOutcome = outcome {
        val at = server.server()
        var login: JellyfinLogin? = null
        // A code that runs out before it's approved makes way for a new one.
        while (login == null) {
            val code = client.quickConnect(at)
            onCode(code.code)
            login = try {
                client.signInWhenApproved(at, code)
            } catch (failed: JellyfinException) {
                if (failed.problem != JellyfinProblem.Expired) throw failed
                null
            }
        }
        keep(login)
    }

    override suspend fun signIn(server: JellyfinServerChoice, username: String, password: String): JellyfinSignInOutcome =
        outcome { keep(client.signIn(server.server(), username, password)) }

    /** Keeps [login] as the profile's sign-in, and says so. */
    private suspend fun keep(login: JellyfinLogin): JellyfinSignInOutcome {
        graph.data.jellyfin.save(
            JellyfinAccount(login.address, login.serverId, login.serverName, login.userId, login.userName, login.token, login.deviceId),
        )
        graph.notice(Notice(context.getString(R.string.jellyfin_signed_in, login.serverName, login.userName), PlasmaIcons.Server))
        return JellyfinSignInOutcome.SignedIn(login.userName)
    }

    private suspend fun outcome(step: suspend () -> JellyfinSignInOutcome): JellyfinSignInOutcome = try {
        step()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: JellyfinException) {
        JellyfinSignInOutcome.Failed(
            when (failed.problem) {
                JellyfinProblem.CantReach -> JellyfinSignInProblem.CantReach
                JellyfinProblem.WrongLogin -> JellyfinSignInProblem.WrongLogin
                JellyfinProblem.QuickConnectOff -> JellyfinSignInProblem.QuickConnectOff
                JellyfinProblem.Expired, JellyfinProblem.SignedOut, JellyfinProblem.Refused -> JellyfinSignInProblem.Refused
            },
        )
    } catch (failed: Exception) {
        // Keeping the sign-in failed.
        JellyfinSignInOutcome.Failed(JellyfinSignInProblem.Refused)
    }

    private companion object {
        const val STOP_SHARING_MS = 5_000L

        /** Jellyfin's asked to look for new recordings this often at most (brief 5.6). */
        const val SCAN_EVERY_MS = 10 * 60_000L

        fun JellyfinServerChoice.server() = JellyfinServer(address, name)

        fun JellyfinAccount.login() = JellyfinLogin(server, serverId, serverName, userId, userName, token, deviceId)

        /** Plasma's version, as the server lists the apps signed in. */
        fun versionOf(context: Context): String = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1"
        } catch (missing: PackageManager.NameNotFoundException) {
            "1"
        }
    }
}
