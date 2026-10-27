// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Setting up the network folder step by step (brief 4.34 and 5.6), against a made-up NAS. */
class NetworkFolderModelTest {
    private val nas = ShareServer("NAS", "nas.home.lan")
    private val den = ShareServer("Den", "192.168.1.40")
    private val maya = ShareLogin("maya", "s3cret")

    private class FakeShares : NetworkFolderSetup {
        val answers = Channel<ShareServer>(Channel.UNLIMITED)
        val asked = mutableListOf<String>()
        var shares: ShareStep<List<String>> = ShareStep.Done(listOf("Backups", "Media"))
        var use: ShareStep<Unit> = ShareStep.Done(Unit)
        val used = mutableListOf<String>()
        val folders = mapOf(
            "Media/" to listOf("Movies", "Recordings"),
            "Media/Recordings" to listOf("TV Recordings"),
            "Media/Recordings/TV Recordings" to emptyList(),
        )

        override fun discover(): Flow<ShareServer> = answers.consumeAsFlow()

        override suspend fun shares(server: ShareServer, login: ShareLogin): ShareStep<List<String>> {
            asked += "shares ${server.host} ${login.username}"
            return if (login.password == "s3cret") shares else ShareStep.Failed(ShareSetupProblem.WrongLogin)
        }

        override suspend fun folders(server: ShareServer, login: ShareLogin, share: String, path: String): ShareStep<List<String>> {
            asked += "folders $share/$path"
            if (login.password != "s3cret") return ShareStep.Failed(ShareSetupProblem.WrongLogin)
            return folders["$share/$path"]?.let { ShareStep.Done(it) } ?: ShareStep.Failed(ShareSetupProblem.NoShare)
        }

        override suspend fun use(server: ShareServer, login: ShareLogin, share: String, path: String): ShareStep<Unit> {
            used += "${server.host} $share/$path ${login.username}"
            return use
        }
    }

    private val setup = FakeShares()
    private var saved = 0

    private fun TestScope.model() = NetworkFolderModel(setup, backgroundScope) { saved++ }

    @Test fun serversAreListedAsTheyAnswer() = runTest {
        val model = model()
        model.lookForServers()
        runCurrent()
        assertEquals(FolderStage.Servers(emptyList(), looking = true), model.stage)

        setup.answers.send(nas)
        setup.answers.send(den)
        // Answering twice lists it once.
        setup.answers.send(nas)
        setup.answers.close()
        runCurrent()

        assertEquals(FolderStage.Servers(listOf(nas, den), looking = false), model.stage)
    }

    @Test fun aServerChosenListsItsSharesThenItsFolders() = runTest {
        val model = model()
        model.choose(nas)
        val login = model.stage as FolderStage.Login
        assertEquals(FolderStage.Login(nas, share = null, path = "", username = ""), login)

        model.signIn(login, "maya", "s3cret")
        assertTrue(model.stage is FolderStage.Working)
        runCurrent()
        val shares = model.stage as FolderStage.Shares
        assertEquals(listOf("Backups", "Media"), shares.shares)

        model.openShare(shares, "Media")
        runCurrent()
        val media = model.stage as FolderStage.Folders
        assertEquals(FolderStage.Folders(nas, maya, "Media", "", listOf("Movies", "Recordings"), listOf("Backups", "Media")), media)
        assertEquals("nas.home.lan › Media", media.shown)

        model.openFolder(media, "Recordings")
        runCurrent()
        val recordings = model.stage as FolderStage.Folders
        assertEquals("Recordings", recordings.path)
        assertEquals("nas.home.lan › Media › Recordings", recordings.shown)

        // Back goes up without asking the server again, then to the shares.
        val asked = setup.asked.size
        model.up(recordings)
        assertEquals(media, model.stage)
        assertEquals(asked, setup.asked.size)
        model.up(media)
        assertEquals(shares, model.stage)

        // Use This Folder: the test write, then it's kept.
        model.openShare(shares, "Media")
        model.openFolder(model.stage as FolderStage.Folders, "Recordings")
        runCurrent()
        model.use(model.stage as FolderStage.Folders)
        runCurrent()
        assertEquals(listOf("nas.home.lan Media/Recordings maya"), setup.used)
        assertEquals(1, saved)
    }

    @Test fun anAddressOpensItsShareAndFolderStraightAway() = runTest {
        val model = model()
        model.address("smb://nas.home.lan/Media/Recordings")
        val login = model.stage as FolderStage.Login
        assertEquals(FolderStage.Login(ShareServer("nas.home.lan", "nas.home.lan"), "Media", "Recordings", ""), login)

        model.signIn(login, "maya", "s3cret")
        runCurrent()
        val folders = model.stage as FolderStage.Folders
        assertEquals(listOf("TV Recordings"), folders.folders)
        assertNull(folders.shares)

        // Up to the share, then, with no shares listed, the servers.
        model.up(folders)
        runCurrent()
        assertEquals("", (model.stage as FolderStage.Folders).path)
        model.up(model.stage as FolderStage.Folders)
        assertTrue(model.stage is FolderStage.Servers)
    }

    @Test fun aWrongPasswordGoesBackToTheSignInWithTheUsername() = runTest {
        val model = model()
        model.choose(nas)
        model.signIn(model.stage as FolderStage.Login, "maya", "wrong")
        runCurrent()
        val problem = model.stage as FolderStage.Problem
        assertEquals(ShareSetupProblem.WrongLogin, problem.problem)

        model.retry(problem)
        assertEquals(FolderStage.Login(nas, null, "", "maya"), model.stage)
        // The username's kept for next time too.
        model.choose(den)
        assertEquals("maya", (model.stage as FolderStage.Login).username)
    }

    @Test fun aServerThatWontListItsSharesAsksForTheAddress() = runTest {
        setup.shares = ShareStep.Failed(ShareSetupProblem.NoList)
        val model = model()
        model.choose(nas)
        model.signIn(model.stage as FolderStage.Login, "maya", "s3cret")
        runCurrent()
        val problem = model.stage as FolderStage.Problem
        assertEquals("smb://nas.home.lan/", problem.typed)

        model.retry(problem)
        assertEquals(FolderStage.Address("smb://nas.home.lan/"), model.stage)
    }

    @Test fun aShareThatIsntThereAsksForTheAddressAgain() = runTest {
        val model = model()
        model.address("\\\\nas.home.lan\\Films")
        model.signIn(model.stage as FolderStage.Login, "maya", "s3cret")
        runCurrent()
        val problem = model.stage as FolderStage.Problem
        assertEquals(ShareSetupProblem.NoShare, problem.problem)
        assertEquals("Films", problem.share)

        model.retry(problem)
        assertEquals(FolderStage.Address("smb://nas.home.lan/Films"), model.stage)
    }

    @Test fun aFolderPlasmaCantWriteToIsntKept() = runTest {
        setup.use = ShareStep.Failed(ShareSetupProblem.ReadOnly)
        val model = model()
        model.address("nas.home.lan/Media")
        model.signIn(model.stage as FolderStage.Login, "maya", "s3cret")
        runCurrent()
        val media = model.stage as FolderStage.Folders
        model.use(media)
        runCurrent()
        val problem = model.stage as FolderStage.Problem
        assertEquals(ShareSetupProblem.ReadOnly, problem.problem)
        assertEquals(0, saved)

        // Choose Another Folder: the same folders again; or sign in as someone else.
        model.retry(problem)
        assertEquals(media, model.stage)
        model.use(media)
        runCurrent()
        model.signInAgain(model.stage as FolderStage.Problem)
        assertEquals(FolderStage.Login(media.server, "Media", "", ""), model.stage)
    }

    @Test fun cancelGoesBackAStep() = runTest {
        val model = model()
        model.choose(nas)
        val login = model.stage as FolderStage.Login
        model.signIn(login, "maya", "s3cret")
        val working = model.stage as FolderStage.Working
        assertEquals(FolderWork.Connecting, working.what)
        assertEquals("nas.home.lan", working.place)

        model.cancel(working)
        assertEquals(login, model.stage)
        runCurrent()
        assertEquals(login, model.stage)
    }

    @Test fun whatsTypedIsReadAsAnAddress() {
        assertEquals(ShareAddress("nas.home.lan", "Media", "Recordings"), ShareAddress.parse("smb://nas.home.lan/Media/Recordings"))
        assertEquals(ShareAddress("nas", "Media", ""), ShareAddress.parse("\\\\nas\\Media"))
        assertEquals(ShareAddress("nas.home.lan", null, ""), ShareAddress.parse(" nas.home.lan "))
        assertEquals(ShareAddress("192.168.1.40", "TV", "A/B"), ShareAddress.parse("//192.168.1.40/TV/A/B/"))
        assertEquals(ShareAddress("nas.home.lan", "My Media", "", "maya"), ShareAddress.parse("SMB://maya@nas.home.lan/My%20Media"))
        assertNull(ShareAddress.parse("smb://"))
        assertNull(ShareAddress.parse("nas home/Media"))
    }

    @Test fun somethingThatIsntAnAddressSaysSo() = runTest {
        val model = model()
        model.address("nas home")
        assertEquals(FolderStage.BadAddress("nas home"), model.stage)
        // Nothing typed: the servers again.
        model.address(" ")
        assertTrue(model.stage is FolderStage.Servers)
    }
}
