// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.content.pm.PackageManager
import ca.gureet.plasma.core.data.JellyfinAccount
import ca.gureet.plasma.core.design.icon.PlasmaIcons
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
import ca.gureet.plasma.live.JellyfinFeed
import ca.gureet.plasma.live.continueSince
import ca.gureet.plasma.live.forContinue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        read = { account, nextUp, since -> client.continueItems(account.login(), nextUp, since) },
        onSignedOut = ::signedOut,
    )

    /** What changes the items read: Show Next Episodes and Hide Items Older Than (4.32). */
    private val rowChoices = graph.storedChoices.map { SettingsChoices(it.orEmpty()).let { chosen -> chosen.continueNextEpisodes to chosen.hideOlderDays } }
        .distinctUntilChanged()

    /**
     * The server's items, read while a screen shows them (Home, and Continue Watching's settings)
     * and every 15 minutes; empty while not signed in.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<JellyfinItem>> = combine(account, rowChoices) { account, choices -> account to choices.first }
        .flatMapLatest { (account, nextUp) -> feed.items(account, nextUp, continueSince(graph.choices, graph.clock.now())) }
        .stateIn(graph.scope, SharingStarted.WhileSubscribed(STOP_SHARING_MS), emptyList())

    /** What Continue Watching shows of them: not hidden from Plasma, and not older than Hide Items Older Than allows. */
    val shown: Flow<List<JellyfinItem>> = combine(items, graph.data.settings.hiddenJellyfin) { items, hidden ->
        items.forContinue(hidden, continueSince(graph.choices, graph.clock.now()))
    }

    /** OK on a Jellyfin item: its page in the Jellyfin app. */
    fun open(id: String) {
        if (!JellyfinApp.open(context, id)) graph.notice(Notice(context.getString(R.string.jellyfin_app_missing), PlasmaIcons.Server, Notice.LONG_MS))
    }

    /** Hide from Plasma on a Jellyfin item, until it's watched again. */
    fun hide(id: String) {
        val watched = items.value.firstOrNull { it.id == id }?.lastWatched
        graph.scope.launch { runCatching { graph.data.settings.hideJellyfin(id, watched) } }
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
