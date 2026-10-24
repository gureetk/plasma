// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.OptionsSheet
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcon
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** A Jellyfin server as the sign-in sheet lists it (brief 4.32): where it answers, and its name. */
@Immutable
data class JellyfinServerChoice(val address: String, val name: String) {
    /** Where it is, as its row says: the host, and the port if it has one ("media.home.lan:8096"). */
    val where: String
        get() = runCatching { URI(address) }.getOrNull()?.takeIf { it.host != null }
            ?.let { uri -> if (uri.port > 0) "${uri.host}:${uri.port}" else uri.host }
            ?: address
}

/** Why a step of signing in to Jellyfin didn't work. */
enum class JellyfinSignInProblem {
    /** Nothing answered as Jellyfin at the address typed. */
    NoServer,

    /** The server didn't answer. */
    CantReach,

    /** The server turned the username and password down. */
    WrongLogin,

    /** Quick Connect is turned off on the server: the password is the way in. */
    QuickConnectOff,

    /** The server said no for another reason. */
    Refused,
}

/** How signing in went. */
sealed interface JellyfinSignInOutcome {
    data class SignedIn(val userName: String) : JellyfinSignInOutcome

    data class Failed(val problem: JellyfinSignInProblem) : JellyfinSignInOutcome
}

/**
 * What the Jellyfin sign-in sheet asks of the server (brief 4.32 and 5.19). The app answers over
 * the network and keeps the sign-in for the profile; previews and tests answer from the boards'
 * made-up server.
 */
interface JellyfinSignIn {
    /** Servers answering on this network, as they answer. It ends once the network is quiet. */
    fun discover(): Flow<JellyfinServerChoice>

    /** The server at what was typed, or null when no Jellyfin server answers there. */
    suspend fun find(typed: String): JellyfinServerChoice?

    /**
     * Quick Connect on [server]: [onCode] shows each code to approve (a new one if one runs out),
     * and it returns once one is approved and signed in with, or when it can't go on.
     */
    suspend fun quickConnect(server: JellyfinServerChoice, onCode: (String) -> Unit): JellyfinSignInOutcome

    /** Signs in with a username and password. */
    suspend fun signIn(server: JellyfinServerChoice, username: String, password: String): JellyfinSignInOutcome

    /** The boards' world: one server on the network, which waits for its code to be approved. */
    object Sample : JellyfinSignIn {
        val media = JellyfinServerChoice("http://media.home.lan:8096", "Media")

        override fun discover(): Flow<JellyfinServerChoice> = flowOf(media)

        override suspend fun find(typed: String): JellyfinServerChoice? = media.takeIf { typed.isNotBlank() }

        override suspend fun quickConnect(server: JellyfinServerChoice, onCode: (String) -> Unit): JellyfinSignInOutcome {
            onCode("736204")
            awaitCancellation()
        }

        override suspend fun signIn(server: JellyfinServerChoice, username: String, password: String) =
            JellyfinSignInOutcome.SignedIn(username)
    }

    /** No server anywhere, for screens that don't sign in. */
    object None : JellyfinSignIn {
        override fun discover(): Flow<JellyfinServerChoice> = emptyFlow()

        override suspend fun find(typed: String): JellyfinServerChoice? = null

        override suspend fun quickConnect(server: JellyfinServerChoice, onCode: (String) -> Unit) =
            JellyfinSignInOutcome.Failed(JellyfinSignInProblem.CantReach)

        override suspend fun signIn(server: JellyfinServerChoice, username: String, password: String) =
            JellyfinSignInOutcome.Failed(JellyfinSignInProblem.CantReach)
    }
}

/** Where the Jellyfin sign-in is. */
internal sealed interface SignInStage {
    /** The servers found on the network so far, while [looking] for more. */
    data class Servers(val found: List<JellyfinServerChoice>, val looking: Boolean) : SignInStage

    /** Typing a server's address, starting from [typed]. */
    data class Address(val typed: String) : SignInStage

    /** Looking for a server at the address typed. */
    data class Checking(val typed: String) : SignInStage

    /** Quick Connect on [server], with the [code] to approve once the server gives one. */
    data class QuickConnect(val server: JellyfinServerChoice, val code: String?) : SignInStage

    /** Typing a username and password for [server]. [quickConnectOff] when the password is the only way in. */
    data class Password(val server: JellyfinServerChoice, val username: String, val quickConnectOff: Boolean) : SignInStage

    /** Signing in as [username]. */
    data class SigningIn(val server: JellyfinServerChoice, val username: String, val quickConnectOff: Boolean) : SignInStage

    /** A step didn't work: [retry] is the stage Try Again goes back to. */
    data class Problem(val problem: JellyfinSignInProblem, val server: JellyfinServerChoice?, val typed: String?, val retry: SignInStage) : SignInStage
}

/**
 * Signing in to Jellyfin, step by step (brief 4.32 and 5.19): servers found on the network or at
 * an address, then Quick Connect, with the password as the other way in. [onSignedIn] runs once
 * the app has kept the sign-in. Steps run in [scope], and a new step stops the one before.
 */
internal class JellyfinSignInModel(
    private val signIn: JellyfinSignIn,
    private val scope: CoroutineScope,
    private val onSignedIn: (userName: String) -> Unit,
) {
    var stage: SignInStage by mutableStateOf(SignInStage.Servers(emptyList(), looking = true))
        private set

    private var job: Job? = null

    /** The address last typed, to start from when typing again. */
    private var lastTyped = ""

    private fun step(next: SignInStage, work: (suspend CoroutineScope.() -> Unit)? = null) {
        job?.cancel()
        job = null
        stage = next
        if (work != null) job = scope.launch(block = work)
    }

    /** Looks for servers on the network, listing them as they answer. */
    fun lookForServers() {
        step(SignInStage.Servers(emptyList(), looking = true)) {
            signIn.discover().collect { server ->
                val now = stage as? SignInStage.Servers ?: return@collect
                if (now.found.none { it.address == server.address }) stage = now.copy(found = now.found + server)
            }
            (stage as? SignInStage.Servers)?.let { stage = it.copy(looking = false) }
        }
    }

    /** Enter Address. */
    fun enterAddress() = step(SignInStage.Address(lastTyped))

    /** What was typed on Enter Address: the server there, then Quick Connect on it. */
    fun address(typed: String) {
        lastTyped = typed
        if (typed.isBlank()) return lookForServers()
        step(SignInStage.Checking(typed)) {
            val server = signIn.find(typed)
            if (server == null) {
                stage = SignInStage.Problem(JellyfinSignInProblem.NoServer, null, typed, retry = SignInStage.Address(typed))
            } else {
                choose(server)
            }
        }
    }

    /** A server chosen: Quick Connect on it, or the password where it has Quick Connect turned off. */
    fun choose(server: JellyfinServerChoice) {
        step(SignInStage.QuickConnect(server, code = null)) {
            when (val outcome = signIn.quickConnect(server) { code -> stage = SignInStage.QuickConnect(server, code) }) {
                is JellyfinSignInOutcome.SignedIn -> onSignedIn(outcome.userName)
                is JellyfinSignInOutcome.Failed -> stage = when (outcome.problem) {
                    JellyfinSignInProblem.QuickConnectOff -> SignInStage.Password(server, "", quickConnectOff = true)
                    else -> SignInStage.Problem(outcome.problem, server, null, retry = SignInStage.QuickConnect(server, null))
                }
            }
        }
    }

    /** Use Password Instead. */
    fun usePassword(server: JellyfinServerChoice, quickConnectOff: Boolean = false) =
        step(SignInStage.Password(server, "", quickConnectOff))

    /** What was typed on the password page. */
    fun password(server: JellyfinServerChoice, username: String, password: String, quickConnectOff: Boolean) {
        step(SignInStage.SigningIn(server, username, quickConnectOff)) {
            when (val outcome = signIn.signIn(server, username, password)) {
                is JellyfinSignInOutcome.SignedIn -> onSignedIn(outcome.userName)
                is JellyfinSignInOutcome.Failed -> stage = SignInStage.Problem(
                    outcome.problem,
                    server,
                    null,
                    retry = SignInStage.Password(server, username, quickConnectOff),
                )
            }
        }
    }

    /** Try Again on a problem: back to the step that didn't work. */
    fun retry(problem: SignInStage.Problem) {
        when (val again = problem.retry) {
            is SignInStage.QuickConnect -> choose(again.server)
            else -> step(again)
        }
    }

    /** Back from typing: to Quick Connect on the server, or the servers again. */
    fun back() {
        when (val now = stage) {
            is SignInStage.Password -> if (now.quickConnectOff) lookForServers() else choose(now.server)
            else -> lookForServers()
        }
    }

    /** The sheet closed: whatever's under way stops. */
    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Jellyfin's sign-in (brief 4.32 and 4.35): a centred sheet with the servers found on the network
 * and Enter Address, then a big Quick Connect code to approve in another Jellyfin app, with Use
 * Password Instead. The address and the password are typed on Settings' keyboard page. [onSignedIn]
 * runs once the app has kept the sign-in; Back closes it all.
 */
@Composable
internal fun JellyfinSignInSheet(signIn: JellyfinSignIn, back: String, onClose: () -> Unit, onSignedIn: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val model = remember(signIn) {
        JellyfinSignInModel(signIn, scope) { name ->
            onSignedIn(name)
            onClose()
        }
    }
    LaunchedEffect(model) { model.lookForServers() }
    val close = {
        model.stop()
        onClose()
    }
    val title = stringResource(R.string.jellyfin_server)
    // A fresh sheet for each step, so focus starts on its first choice; and for the first server
    // found, which takes focus. Servers found after it, and Look Again, join the list as it is.
    when (val stage = model.stage) {
        is SignInStage.Servers -> key(stage.found.isEmpty()) {
            SignInSheet(
                title = title,
                text = stringResource(
                    when {
                        stage.found.isNotEmpty() -> R.string.jellyfin_choose
                        stage.looking -> R.string.jellyfin_looking
                        else -> R.string.jellyfin_none_found
                    },
                ),
                onClose = close,
                options = stage.found.map { server -> SheetOption(server.name, onClick = { model.choose(server) }, value = server.where) } +
                    listOfNotNull(
                        SheetOption.Divider.takeIf { stage.found.isNotEmpty() },
                        SheetOption(stringResource(R.string.enter_address), onClick = model::enterAddress),
                        SheetOption(stringResource(R.string.look_again), onClick = model::lookForServers).takeUnless { stage.looking },
                    ),
            )
        }
        is SignInStage.Address -> TypedSetting(
            back = back,
            entry = SettingAction.Type(
                title = title,
                fields = listOf(TypedField(stringResource(R.string.server_address), stage.typed, hint = stringResource(R.string.server_address_hint))),
                text = stringResource(R.string.jellyfin_address_text),
                save = stringResource(R.string.connect),
                note = stringResource(R.string.jellyfin_address_note),
            ) { texts -> model.address(texts.first()) },
            // Back, rather than Connect: to the servers.
            onDone = { if (model.stage is SignInStage.Address) model.back() },
        )
        is SignInStage.Checking -> key(stage) {
            SignInSheet(
                title = title,
                text = stringResource(R.string.jellyfin_checking, stage.typed),
                onClose = close,
                options = listOf(SheetOption(stringResource(R.string.cancel), onClick = model::lookForServers)),
            )
        }
        is SignInStage.QuickConnect -> key(stage.server, stage.code == null) {
            SignInSheet(
                title = stringResource(R.string.jellyfin_sign_in_to, stage.server.name),
                text = stringResource(if (stage.code == null) R.string.jellyfin_asking_code else R.string.quick_connect),
                onClose = close,
                options = listOf(SheetOption(stringResource(R.string.use_password_instead), onClick = { model.usePassword(stage.server) })),
            ) {
                QuickConnectCode(stage.code)
                PlasmaText(
                    stringResource(R.string.jellyfin_approve),
                    plasmaStyle(21f, 500, lineHeight = 1.4f),
                    Modifier.padding(top = 18.px),
                    color = PlasmaColors.Text2,
                    maxLines = 3,
                )
            }
        }
        is SignInStage.Password -> TypedSetting(
            back = back,
            entry = SettingAction.Type(
                title = stringResource(R.string.jellyfin_sign_in_to, stage.server.name),
                fields = listOf(
                    TypedField(stringResource(R.string.field_username), stage.username),
                    TypedField(stringResource(R.string.field_password), "", secret = true),
                ),
                text = if (stage.quickConnectOff) {
                    stringResource(R.string.jellyfin_password_only, stage.server.name)
                } else {
                    stringResource(R.string.jellyfin_password_text)
                },
                save = stringResource(R.string.sign_in),
                note = stringResource(R.string.jellyfin_password_note),
            ) { texts -> model.password(stage.server, texts[0], texts[1], stage.quickConnectOff) },
            onDone = { if (model.stage is SignInStage.Password) model.back() },
        )
        is SignInStage.SigningIn -> key(stage) {
            SignInSheet(
                title = stringResource(R.string.jellyfin_sign_in_to, stage.server.name),
                text = stringResource(R.string.jellyfin_signing_in, stage.username),
                onClose = close,
                options = listOf(SheetOption(stringResource(R.string.cancel), onClick = { model.back() })),
            )
        }
        is SignInStage.Problem -> key(stage) {
            val name = stage.server?.name.orEmpty()
            SignInSheet(
                title = stage.server?.let { stringResource(R.string.jellyfin_sign_in_to, it.name) } ?: title,
                text = when (stage.problem) {
                    JellyfinSignInProblem.NoServer -> stringResource(R.string.jellyfin_no_server, stage.typed.orEmpty())
                    JellyfinSignInProblem.CantReach -> stringResource(R.string.jellyfin_cant_reach, name)
                    JellyfinSignInProblem.WrongLogin -> stringResource(R.string.jellyfin_wrong_login, name)
                    JellyfinSignInProblem.QuickConnectOff, JellyfinSignInProblem.Refused -> stringResource(R.string.jellyfin_refused, name)
                },
                onClose = close,
                options = problemOptions(stage, model),
            )
        }
    }
}

/** What a problem offers: Try Again (or Enter Address for an address), then the other ways on. */
@Composable
private fun problemOptions(stage: SignInStage.Problem, model: JellyfinSignInModel): List<SheetOption> {
    val again = SheetOption(
        stringResource(if (stage.problem == JellyfinSignInProblem.NoServer) R.string.enter_address else R.string.try_again),
        onClick = { model.retry(stage) },
    )
    val retry = stage.retry
    val other = if (retry is SignInStage.Password && !retry.quickConnectOff) {
        // A wrong password: Quick Connect instead, where the server has it.
        SheetOption(stringResource(R.string.use_quick_connect), onClick = { model.choose(retry.server) })
    } else {
        SheetOption(stringResource(R.string.choose_another_server), onClick = model::lookForServers)
    }
    return listOf(again, other)
}

/** The sign-in's centred sheet: the server icon, a title, a line of text, then [body] and the choices. */
@Composable
private fun SignInSheet(
    title: String,
    text: String,
    onClose: () -> Unit,
    options: List<SheetOption>,
    body: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
) {
    OptionsSheet(
        title = title,
        subtitle = text,
        // Choosing runs the step; the sheet stays for the next one.
        onDismiss = {},
        width = 900.px,
        leading = {
            Box(Modifier.size(72.px).background(PlasmaColors.white(0.08f), RoundedCornerShape(18.px)), contentAlignment = Alignment.Center) {
                PlasmaIcon(PlasmaIcons.Server, 36.px, tint = PlasmaColors.TextSoft3, stroke = 1.8f)
            }
        },
        body = body,
        options = options,
        onBack = onClose,
    )
}

/** The Quick Connect code, big, in a plate: "736 204". Dots while the server is asked for one. */
@Composable
private fun QuickConnectCode(code: String?) {
    Box(
        Modifier
            .padding(top = 26.px)
            .fillMaxWidth()
            .height(150.px)
            .background(PlasmaColors.white(0.06f), RoundedCornerShape(20.px)),
        contentAlignment = Alignment.Center,
    ) {
        PlasmaText(
            code?.let(::spaced) ?: "· · ·",
            plasmaStyle(76f, 600, trackingPx = 8f, tabular = true),
            color = if (code == null) PlasmaColors.Text3 else PlasmaColors.Text,
            align = TextAlign.Center,
        )
    }
}

/** A six-digit code in two groups of three, as people read it out: "736 204". */
internal fun spaced(code: String): String = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code
