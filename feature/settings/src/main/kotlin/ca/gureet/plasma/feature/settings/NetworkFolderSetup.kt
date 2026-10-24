// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** A server sharing folders, as the network folder setup lists it (brief 4.34): its name, and where it answers. */
@Immutable
data class ShareServer(val name: String, val host: String)

/** How Plasma signs in to a server: a blank [username] is a guest. */
@Immutable
data class ShareLogin(val username: String, val password: String) {
    // It carries the password.
    override fun toString(): String = "ShareLogin($username)"
}

/** Why a step of setting up the network folder didn't work. */
enum class ShareSetupProblem {
    /** The server didn't answer. */
    CantReach,

    /** The server turned the username and password down. */
    WrongLogin,

    /** The server has no share by the name typed. */
    NoShare,

    /** The server wouldn't list its shares: the share's address is the way in. */
    NoList,

    /** The test write failed: Plasma can't write to the folder. */
    ReadOnly,

    /** The server said no for another reason. */
    Refused,
}

/** How a step went: what it found, or why it didn't work. */
sealed interface ShareStep<out T> {
    data class Done<out T>(val value: T) : ShareStep<T>

    data class Failed(val problem: ShareSetupProblem) : ShareStep<Nothing>
}

/**
 * What the network folder setup asks of the network (brief 4.34 and 5.6). The app answers over
 * SMB and keeps the folder; previews and tests answer from the boards' made-up NAS.
 */
interface NetworkFolderSetup {
    /** Servers sharing folders on this network, as they answer. It ends once the network is quiet. */
    fun discover(): Flow<ShareServer>

    /** The shares on [server], A to Z, signed in with [login]. */
    suspend fun shares(server: ShareServer, login: ShareLogin): ShareStep<List<String>>

    /** The folders in [path] of [share] ("" is the share itself), A to Z. */
    suspend fun folders(server: ShareServer, login: ShareLogin, share: String, path: String): ShareStep<List<String>>

    /**
     * Writes a small file in [path] of [share] and deletes it, so a folder Plasma can't write to
     * fails now rather than at 3 a.m. (5.6); then keeps it as where recordings are copied.
     */
    suspend fun use(server: ShareServer, login: ShareLogin, share: String, path: String): ShareStep<Unit>

    /** The boards' world: a NAS on the network with a Media share. */
    object Sample : NetworkFolderSetup {
        val nas = ShareServer("NAS", "nas.home.lan")
        private val folders = mapOf(
            "Media/" to listOf("Movies", "Music", "Recordings"),
            "Media/Recordings" to listOf("Movie Recordings", "Other Recordings", "TV Recordings"),
        )

        override fun discover(): Flow<ShareServer> = flowOf(nas)

        override suspend fun shares(server: ShareServer, login: ShareLogin) = ShareStep.Done(listOf("Backups", "Media"))

        override suspend fun folders(server: ShareServer, login: ShareLogin, share: String, path: String) =
            ShareStep.Done(folders["$share/$path"].orEmpty())

        override suspend fun use(server: ShareServer, login: ShareLogin, share: String, path: String) = ShareStep.Done(Unit)
    }

    /** No network, for screens that don't set a folder up. */
    object None : NetworkFolderSetup {
        override fun discover(): Flow<ShareServer> = emptyFlow()

        override suspend fun shares(server: ShareServer, login: ShareLogin) = ShareStep.Failed(ShareSetupProblem.CantReach)

        override suspend fun folders(server: ShareServer, login: ShareLogin, share: String, path: String) =
            ShareStep.Failed(ShareSetupProblem.CantReach)

        override suspend fun use(server: ShareServer, login: ShareLogin, share: String, path: String) = ShareStep.Failed(ShareSetupProblem.CantReach)
    }
}

/**
 * A share's address as it's typed (brief 5.6): `smb://nas.home.lan/Media`, `\\nas\Media`, or
 * just `nas.home.lan/Media`, with a folder after the share if wanted, and a username before the
 * server (`smb://maya@nas.home.lan/Media`).
 */
internal data class ShareAddress(val host: String, val share: String?, val path: String, val username: String? = null) {
    companion object {
        fun parse(typed: String): ShareAddress? {
            var text = typed.trim()
            val url = text.startsWith(SMB, ignoreCase = true)
            text = when {
                url -> decode(text.substring(SMB.length))
                text.startsWith("\\\\") || text.startsWith("//") -> text.substring(2)
                else -> text
            }
            val parts = text.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }
            val first = parts.firstOrNull() ?: return null
            val username = first.substringBeforeLast('@', "").takeIf { it.isNotEmpty() }
            val host = first.substringAfterLast('@')
            if (host.isEmpty() || host.any { it.isWhitespace() || it in "<>\"|*?" }) return null
            return ShareAddress(host, parts.getOrNull(1), parts.drop(2).joinToString("/"), username)
        }

        /** An smb:// address's %20s and the like. */
        private fun decode(text: String): String = PERCENT.replace(text) { it.value.substring(1).toInt(16).toChar().toString() }

        private const val SMB = "smb://"
        private val PERCENT = Regex("%[0-9A-Fa-f]{2}")
    }
}

/** What the setup is doing while it waits for the server. */
internal enum class FolderWork { Connecting, Opening, Checking }

/** Where the network folder setup is. */
internal sealed interface FolderStage {
    /** The servers found on the network so far, while [looking] for more. */
    data class Servers(val found: List<ShareServer>, val looking: Boolean) : FolderStage

    /** Typing a share's address, starting from [typed]. */
    data class Address(val typed: String) : FolderStage

    /** Typing a username and password for [server], to open [share] (or list them, when null) at [path]. */
    data class Login(val server: ShareServer, val share: String?, val path: String, val username: String) : FolderStage

    /** Waiting for [server]: [what] it's doing, and the stage Cancel goes back to. */
    data class Working(val what: FolderWork, val server: ShareServer, val place: String, val before: FolderStage) : FolderStage

    /** [server]'s shares, to choose one. */
    data class Shares(val server: ShareServer, val login: ShareLogin, val shares: List<String>) : FolderStage

    /**
     * The [folders] in [path] of [share], with Use This Folder. [shares] when they were listed, for
     * Back from the share itself.
     */
    data class Folders(
        val server: ShareServer,
        val login: ShareLogin,
        val share: String,
        val path: String,
        val folders: List<String>,
        val shares: List<String>?,
    ) : FolderStage {
        /** "nas.home.lan › Media › Recordings". */
        val shown: String get() = (listOf(server.host, share) + path.split('/').filter { it.isNotEmpty() }).joinToString(" › ")
    }

    /** A step didn't work: [retry] is where Try Again goes, [typed] the address that was typed, if one was. */
    data class Problem(val problem: ShareSetupProblem, val server: ShareServer?, val share: String?, val typed: String?, val retry: FolderStage) : FolderStage

    /** What was typed isn't an address. */
    data class BadAddress(val typed: String) : FolderStage
}

/**
 * Setting up the network folder, step by step (brief 4.34 and 5.6): servers found on the network
 * or an address, a username and password, then the shares and folders, and Use This Folder, which
 * writes a test file before it keeps the folder. [onSaved] runs once the app has kept it. Steps run
 * in [scope], and a new step stops the one before.
 */
internal class NetworkFolderModel(
    private val setup: NetworkFolderSetup,
    private val scope: CoroutineScope,
    private val onSaved: () -> Unit,
) {
    var stage: FolderStage by mutableStateOf(FolderStage.Servers(emptyList(), looking = true))
        private set

    private var job: Job? = null

    /** The address last typed, and the username, to start from when typing again. */
    private var lastTyped = ""
    private var lastUsername = ""

    /** Folders already listed, by share and path, so Back doesn't ask the server again. */
    private val listed = HashMap<String, List<String>>()

    private fun step(next: FolderStage, work: (suspend CoroutineScope.() -> Unit)? = null) {
        job?.cancel()
        job = null
        stage = next
        if (work != null) job = scope.launch(block = work)
    }

    /** Looks for servers on the network, listing them as they answer. */
    fun lookForServers() {
        step(FolderStage.Servers(emptyList(), looking = true)) {
            setup.discover().collect { server ->
                val now = stage as? FolderStage.Servers ?: return@collect
                if (now.found.none { it.host == server.host }) stage = now.copy(found = now.found + server)
            }
            (stage as? FolderStage.Servers)?.let { stage = it.copy(looking = false) }
        }
    }

    /** Enter Address, starting from [typed], or what was typed last. */
    fun enterAddress(typed: String = lastTyped) = step(FolderStage.Address(typed))

    /** What was typed on Enter Address: the server there, then its username and password. */
    fun address(typed: String) {
        lastTyped = typed
        if (typed.isBlank()) return lookForServers()
        val address = ShareAddress.parse(typed) ?: return step(FolderStage.BadAddress(typed))
        val server = ShareServer(address.host, address.host)
        step(FolderStage.Login(server, address.share, address.path, address.username ?: lastUsername))
    }

    /** A server chosen from the list: its username and password, then its shares. */
    fun choose(server: ShareServer) = step(FolderStage.Login(server, share = null, path = "", username = lastUsername))

    /** What was typed on the sign-in page: the shares, or the share and folder typed in the address. */
    fun signIn(at: FolderStage.Login, username: String, password: String) {
        lastUsername = username
        val login = ShareLogin(username, password)
        listed.clear()
        val share = at.share
        if (share == null) {
            listShares(at.server, login, before = at)
        } else {
            open(FolderWork.Connecting, at.server, login, share, at.path, shares = null, before = at)
        }
    }

    /** A share chosen: its folders. */
    fun openShare(at: FolderStage.Shares, share: String) = open(FolderWork.Opening, at.server, at.login, share, "", at.shares, before = at)

    /** A folder chosen: the folders in it. */
    fun openFolder(at: FolderStage.Folders, folder: String) =
        open(FolderWork.Opening, at.server, at.login, at.share, join(at.path, folder), at.shares, before = at)

    /** Back in the folders: up a folder, then to the shares, then to the servers. */
    fun up(at: FolderStage.Folders) {
        when {
            at.path.isNotEmpty() -> open(FolderWork.Opening, at.server, at.login, at.share, at.path.substringBeforeLast('/', ""), at.shares, before = at)
            at.shares != null -> step(FolderStage.Shares(at.server, at.login, at.shares))
            else -> lookForServers()
        }
    }

    /** Use This Folder: a test write, then it's kept. */
    fun use(at: FolderStage.Folders) {
        step(FolderStage.Working(FolderWork.Checking, at.server, at.shown, at)) {
            when (val used = setup.use(at.server, at.login, at.share, at.path)) {
                is ShareStep.Done -> onSaved()
                is ShareStep.Failed -> stage = problem(used.problem, at.server, at.login, at.share, at.path, retry = at)
            }
        }
    }

    /**
     * Try Again on a problem: the step again, or the page to fix what was typed. Choose Another
     * Folder, on one Plasma can't write to, goes back to its folders.
     */
    fun retry(problem: FolderStage.Problem) {
        when (val again = problem.retry) {
            is FolderStage.Address -> enterAddress(again.typed)
            is FolderStage.Shares -> listShares(again.server, again.login, before = problem)
            is FolderStage.Folders -> if (problem.problem == ShareSetupProblem.ReadOnly) {
                step(again)
            } else {
                open(FolderWork.Connecting, again.server, again.login, again.share, again.path, again.shares, before = problem)
            }
            else -> step(again)
        }
    }

    /** Sign In as Someone Else, on a folder Plasma can't write to. */
    fun signInAgain(problem: FolderStage.Problem) {
        val at = problem.retry as? FolderStage.Folders ?: return lookForServers()
        step(FolderStage.Login(at.server, at.share, at.path, ""))
    }

    /** Cancel while waiting: back to the step before. */
    fun cancel(working: FolderStage.Working) = step(working.before)

    /** The sheet closed: whatever's under way stops. */
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun listShares(server: ShareServer, login: ShareLogin, before: FolderStage) {
        step(FolderStage.Working(FolderWork.Connecting, server, server.host, before)) {
            stage = when (val found = setup.shares(server, login)) {
                is ShareStep.Done -> FolderStage.Shares(server, login, found.value)
                // Try Again lists them again.
                is ShareStep.Failed -> problem(found.problem, server, login, null, "", retry = FolderStage.Shares(server, login, emptyList()))
            }
        }
    }

    private fun open(what: FolderWork, server: ShareServer, login: ShareLogin, share: String, path: String, shares: List<String>?, before: FolderStage) {
        val key = "$share/$path"
        listed[key]?.let { folders -> return step(FolderStage.Folders(server, login, share, path, folders, shares)) }
        val place = (listOf(server.host, share) + path.split('/').filter { it.isNotEmpty() }).joinToString(" › ")
        step(FolderStage.Working(what, server, place, before)) {
            stage = when (val found = setup.folders(server, login, share, path)) {
                is ShareStep.Done -> {
                    listed[key] = found.value
                    FolderStage.Folders(server, login, share, path, found.value, shares)
                }
                // Try Again opens it again.
                is ShareStep.Failed -> problem(found.problem, server, login, share, path, retry = FolderStage.Folders(server, login, share, path, emptyList(), shares))
            }
        }
    }

    /**
     * A problem, with where Try Again goes: the address to fix the share in, or the sign-in page,
     * where that's the trouble; otherwise [retry].
     */
    private fun problem(problem: ShareSetupProblem, server: ShareServer, login: ShareLogin, share: String?, path: String, retry: FolderStage): FolderStage.Problem {
        val typed = when (problem) {
            ShareSetupProblem.NoShare -> "smb://${server.host}/${share.orEmpty()}"
            ShareSetupProblem.NoList -> "smb://${server.host}/"
            else -> null
        }
        val again = when {
            typed != null -> FolderStage.Address(typed)
            problem == ShareSetupProblem.WrongLogin -> FolderStage.Login(server, share, path, login.username)
            else -> retry
        }
        return FolderStage.Problem(problem, server, share, typed, again)
    }

    private fun join(path: String, folder: String) = if (path.isEmpty()) folder else "$path/$folder"
}

/**
 * Copy to Network Folder's setup (brief 4.34 and 4.35): a centred sheet with the servers found on
 * the network and Enter Address, a username and password on Settings' keyboard page, then the
 * shares and folders with Use This Folder. [back] names the page under it; Back closes it, or in
 * the folders goes up one.
 */
@Composable
internal fun NetworkFolderSheet(setup: NetworkFolderSetup, back: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(setup) { NetworkFolderModel(setup, scope) { onClose() } }
    LaunchedEffect(model) { model.lookForServers() }
    val close = {
        model.stop()
        onClose()
    }
    val title = stringResource(R.string.network_folder)
    // A fresh sheet for each step, so focus starts on its first choice.
    when (val stage = model.stage) {
        is FolderStage.Servers -> key(stage.found.isEmpty()) {
            SignInSheet(
                title = title,
                text = stringResource(
                    when {
                        stage.found.isNotEmpty() -> R.string.share_choose
                        stage.looking -> R.string.share_looking
                        else -> R.string.share_none_found
                    },
                ),
                onClose = close,
                icon = PlasmaIcons.NetworkFolder,
                options = stage.found.map { server -> SheetOption(server.name, onClick = { model.choose(server) }, value = server.host.takeIf { it != server.name }) } +
                    listOfNotNull(
                        SheetOption.Divider.takeIf { stage.found.isNotEmpty() },
                        SheetOption(stringResource(R.string.enter_address), onClick = { model.enterAddress() }),
                        SheetOption(stringResource(R.string.look_again), onClick = model::lookForServers).takeUnless { stage.looking },
                    ),
            )
        }
        is FolderStage.Address -> TypedSetting(
            back = back,
            entry = SettingAction.Type(
                title = title,
                fields = listOf(TypedField(stringResource(R.string.share_address), stage.typed, hint = stringResource(R.string.share_address_hint))),
                text = stringResource(R.string.share_address_text),
                save = stringResource(R.string.connect),
                note = stringResource(R.string.share_address_note),
                shortcuts = SHARE_SHORTCUTS,
            ) { texts -> model.address(texts.first()) },
            // Back, rather than Connect: to the servers.
            onDone = { if (model.stage is FolderStage.Address) model.lookForServers() },
        )
        is FolderStage.BadAddress -> key(stage) {
            SignInSheet(
                title = title,
                text = stringResource(R.string.share_bad_address),
                onClose = close,
                icon = PlasmaIcons.NetworkFolder,
                options = listOf(
                    SheetOption(stringResource(R.string.enter_address), onClick = { model.enterAddress(stage.typed) }),
                    SheetOption(stringResource(R.string.choose_another_server), onClick = model::lookForServers),
                ),
            )
        }
        is FolderStage.Login -> TypedSetting(
            back = back,
            entry = SettingAction.Type(
                title = stringResource(R.string.jellyfin_sign_in_to, stage.server.host),
                fields = listOf(
                    TypedField(stringResource(R.string.field_username), stage.username),
                    TypedField(stringResource(R.string.field_password), "", secret = true),
                ),
                text = stringResource(R.string.share_login_text),
                save = stringResource(R.string.sign_in),
                note = stringResource(R.string.share_login_note),
            ) { texts -> model.signIn(stage, texts[0], texts[1]) },
            onDone = { if (model.stage is FolderStage.Login) model.lookForServers() },
        )
        is FolderStage.Working -> key(stage) {
            SignInSheet(
                title = title,
                text = stringResource(
                    when (stage.what) {
                        FolderWork.Connecting -> R.string.share_connecting
                        FolderWork.Opening -> R.string.share_opening
                        FolderWork.Checking -> R.string.share_checking
                    },
                    stage.place,
                ),
                onClose = close,
                icon = PlasmaIcons.NetworkFolder,
                options = listOf(SheetOption(stringResource(R.string.cancel), onClick = { model.cancel(stage) })),
            )
        }
        is FolderStage.Shares -> key(stage) {
            SignInSheet(
                title = stage.server.host,
                text = stringResource(if (stage.shares.isEmpty()) R.string.share_no_shares else R.string.share_choose_share),
                onClose = close,
                onBack = model::lookForServers,
                icon = PlasmaIcons.NetworkFolder,
                options = stage.shares.map { share -> SheetOption(share, onClick = { model.openShare(stage, share) }, icon = PlasmaIcons.Folder) } +
                    listOfNotNull(
                        SheetOption.Divider.takeIf { stage.shares.isNotEmpty() },
                        SheetOption(stringResource(R.string.enter_address), onClick = { model.enterAddress("smb://${stage.server.host}/") }),
                    ),
            )
        }
        is FolderStage.Folders -> key(stage) {
            SignInSheet(
                title = stage.shown,
                text = stringResource(R.string.share_folder_text),
                onClose = close,
                onBack = { model.up(stage) },
                icon = PlasmaIcons.NetworkFolder,
                options = listOf(SheetOption(stringResource(R.string.use_this_folder), onClick = { model.use(stage) })) +
                    listOfNotNull(SheetOption.Divider.takeIf { stage.folders.isNotEmpty() }) +
                    stage.folders.map { folder -> SheetOption(folder, onClick = { model.openFolder(stage, folder) }, icon = PlasmaIcons.Folder) },
            )
        }
        is FolderStage.Problem -> key(stage) {
            val host = stage.server?.host.orEmpty()
            SignInSheet(
                title = title,
                text = when (stage.problem) {
                    ShareSetupProblem.CantReach -> stringResource(R.string.share_cant_reach, host)
                    ShareSetupProblem.WrongLogin -> stringResource(R.string.share_wrong_login, host)
                    ShareSetupProblem.NoShare -> stringResource(R.string.share_no_share, host, stage.share.orEmpty())
                    ShareSetupProblem.NoList -> stringResource(R.string.share_no_list, host)
                    ShareSetupProblem.ReadOnly -> stringResource(R.string.share_read_only, (listOf(host) + listOfNotNull(stage.share)).joinToString(" › "))
                    ShareSetupProblem.Refused -> stringResource(R.string.share_refused, host)
                },
                onClose = close,
                icon = PlasmaIcons.NetworkFolder,
                options = problemOptions(stage, model),
            )
        }
    }
}

/** What a problem offers: Try Again (or Enter Address, or Choose Another Folder), then another way on. */
@Composable
private fun problemOptions(stage: FolderStage.Problem, model: NetworkFolderModel): List<SheetOption> {
    val again = SheetOption(
        stringResource(
            when {
                stage.typed != null -> R.string.enter_address
                stage.problem == ShareSetupProblem.ReadOnly -> R.string.choose_another_folder
                else -> R.string.try_again
            },
        ),
        onClick = { model.retry(stage) },
    )
    val other = if (stage.problem == ShareSetupProblem.ReadOnly) {
        SheetOption(stringResource(R.string.sign_in_as_someone_else), onClick = { model.signInAgain(stage) })
    } else {
        SheetOption(stringResource(R.string.choose_another_server), onClick = model::lookForServers)
    }
    return listOf(again, other)
}

/** The keyboard's shortcuts for a share's address. */
private val SHARE_SHORTCUTS = listOf("smb://" to 120, ".local" to 104, ".lan" to 96, "192.168." to 130)
