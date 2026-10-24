// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Signing in to Jellyfin step by step (brief 4.32 and 5.19), against a made-up server. */
class JellyfinSignInModelTest {
    private val media = JellyfinServerChoice("http://media.home.lan:8096", "Media")
    private val den = JellyfinServerChoice("http://192.168.1.31:8096", "Den")

    private class FakeJellyfin : JellyfinSignIn {
        val answers = Channel<JellyfinServerChoice>(Channel.UNLIMITED)
        var at: Map<String, JellyfinServerChoice> = emptyMap()
        var quickConnects = 0
        var cancelled = 0
        var quickConnect: suspend (onCode: (String) -> Unit) -> JellyfinSignInOutcome = { onCode ->
            onCode("736204")
            awaitCancellation()
        }

        override fun discover(): Flow<JellyfinServerChoice> = answers.consumeAsFlow()

        override suspend fun find(typed: String): JellyfinServerChoice? = at[typed]

        override suspend fun quickConnect(server: JellyfinServerChoice, onCode: (String) -> Unit): JellyfinSignInOutcome {
            quickConnects++
            try {
                return quickConnect(onCode)
            } catch (stopped: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw stopped
            }
        }

        override suspend fun signIn(server: JellyfinServerChoice, username: String, password: String) =
            if (password == "s3cret") JellyfinSignInOutcome.SignedIn("Maya") else JellyfinSignInOutcome.Failed(JellyfinSignInProblem.WrongLogin)
    }

    private val jellyfin = FakeJellyfin()
    private val signedIn = mutableListOf<String>()

    private fun TestScope.model() = JellyfinSignInModel(jellyfin, backgroundScope) { signedIn += it }

    @Test fun serversAreListedAsTheyAnswer() = runTest {
        val model = model()
        model.lookForServers()
        runCurrent()
        assertEquals(SignInStage.Servers(emptyList(), looking = true), model.stage)

        jellyfin.answers.send(media)
        jellyfin.answers.send(den)
        // Answering twice lists it once.
        jellyfin.answers.send(media)
        jellyfin.answers.close()
        runCurrent()

        assertEquals(SignInStage.Servers(listOf(media, den), looking = false), model.stage)
    }

    @Test fun aServerChosenShowsItsCodeThenSignsInOnceApproved() = runTest {
        val approved = CompletableDeferred<JellyfinSignInOutcome>()
        jellyfin.quickConnect = { onCode ->
            onCode("736204")
            approved.await()
        }
        val model = model()

        model.choose(media)
        assertEquals(SignInStage.QuickConnect(media, code = null), model.stage)
        runCurrent()
        assertEquals(SignInStage.QuickConnect(media, "736204"), model.stage)

        approved.complete(JellyfinSignInOutcome.SignedIn("Maya"))
        runCurrent()
        assertEquals(listOf("Maya"), signedIn)
    }

    @Test fun usePasswordInsteadStopsQuickConnectAndBackStartsItAgain() = runTest {
        val model = model()
        model.choose(media)
        runCurrent()

        model.usePassword(media)
        runCurrent()
        assertEquals(1, jellyfin.cancelled)
        assertEquals(SignInStage.Password(media, "", quickConnectOff = false), model.stage)

        model.back()
        runCurrent()
        assertEquals(SignInStage.QuickConnect(media, "736204"), model.stage)
        assertEquals(2, jellyfin.quickConnects)
    }

    @Test fun withQuickConnectOffThePasswordIsTheWayIn() = runTest {
        jellyfin.quickConnect = { JellyfinSignInOutcome.Failed(JellyfinSignInProblem.QuickConnectOff) }
        val model = model()

        model.choose(media)
        runCurrent()
        assertEquals(SignInStage.Password(media, "", quickConnectOff = true), model.stage)

        // A wrong password offers Try Again, with the username kept, and no Quick Connect.
        model.password(media, "maya", "wrong", quickConnectOff = true)
        runCurrent()
        val problem = model.stage as SignInStage.Problem
        assertEquals(JellyfinSignInProblem.WrongLogin, problem.problem)
        model.retry(problem)
        assertEquals(SignInStage.Password(media, "maya", quickConnectOff = true), model.stage)

        model.password(media, "maya", "s3cret", quickConnectOff = true)
        runCurrent()
        assertEquals(listOf("Maya"), signedIn)

        // Back from its password page: the servers again.
        model.choose(media)
        runCurrent()
        model.back()
        assertTrue(model.stage is SignInStage.Servers)
    }

    @Test fun anAddressFindsItsServerThenQuickConnect() = runTest {
        jellyfin.at = mapOf("media.home.lan" to media)
        val model = model()

        model.address("media.home.lan")
        assertEquals(SignInStage.Checking("media.home.lan"), model.stage)
        runCurrent()

        assertEquals(SignInStage.QuickConnect(media, "736204"), model.stage)
    }

    @Test fun anAddressWithNoServerCanBeTypedAgain() = runTest {
        val model = model()

        model.address("192.168.1.99")
        runCurrent()
        val problem = model.stage as SignInStage.Problem
        assertEquals(JellyfinSignInProblem.NoServer, problem.problem)
        assertEquals("192.168.1.99", problem.typed)

        model.retry(problem)
        assertEquals(SignInStage.Address("192.168.1.99"), model.stage)
        // Typing again later starts from what was typed last.
        model.lookForServers()
        model.enterAddress()
        assertEquals(SignInStage.Address("192.168.1.99"), model.stage)
    }

    @Test fun aServerThatDoesntAnswerCanBeTriedAgain() = runTest {
        jellyfin.quickConnect = { JellyfinSignInOutcome.Failed(JellyfinSignInProblem.CantReach) }
        val model = model()

        model.choose(media)
        runCurrent()
        val problem = model.stage as SignInStage.Problem
        assertEquals(JellyfinSignInProblem.CantReach, problem.problem)
        assertEquals(media, problem.server)

        model.retry(problem)
        runCurrent()
        assertEquals(2, jellyfin.quickConnects)
    }

    @Test fun closingStopsWhatsUnderWay() = runTest {
        val model = model()
        model.choose(media)
        runCurrent()

        model.stop()
        runCurrent()

        assertEquals(1, jellyfin.cancelled)
    }

    @Test fun aSixDigitCodeIsReadInTwoGroups() {
        assertEquals("736 204", spaced("736204"))
        assertEquals("73620", spaced("73620"))
        assertEquals("media.home.lan:8096", media.where)
        assertEquals("media.home.lan", JellyfinServerChoice("https://media.home.lan", "Media").where)
    }
}
