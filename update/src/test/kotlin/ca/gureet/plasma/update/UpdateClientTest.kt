// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.update

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Reading the update file and downloading an update (brief 5.18), from a made-up server. */
class UpdateClientTest {
    private val server = MockWebServer()
    private val answers = ConcurrentHashMap<String, () -> MockResponse>()
    private val client = UpdateClient(OkHttpClient())
    private val folder: File = Files.createTempDirectory("updates").toFile()

    /** A made-up APK: what matters is its bytes. */
    private val apk = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val apkSha = UpdateClient.hex(MessageDigest.getInstance("SHA-256").digest(apk))

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = answers[request.url.encodedPath]?.invoke() ?: MockResponse(code = 404)
        }
        server.start()
    }

    @After fun stop() {
        server.close()
        folder.deleteRecursively()
    }

    private fun manifest(sha: String = apkSha) = """
        {"versionCode": 512, "versionName": "0.2.0", "minSdk": 26, "notes": "New",
         "apks": [{"abi": "arm64-v8a", "url": "apk/app-arm64-v8a-release.apk", "sha256": "$sha"}]}
    """.trimIndent()

    private fun address() = server.url("/plasma/update.json").toString()

    private fun update(sha: String = apkSha) = Update(512, "0.2.0", "New", server.url("/plasma/apk/app-arm64-v8a-release.apk").toString(), sha)

    private inline fun expect(problem: UpdateProblem, block: () -> Unit) {
        try {
            block()
            fail("Expected $problem")
        } catch (e: UpdateException) {
            assertEquals(problem, e.problem)
        }
    }

    @Test fun readsWhatTheFileOffers() = runBlocking {
        answers["/plasma/update.json"] = { MockResponse(code = 200, body = manifest()) }

        val check = client.check(address(), installedCode = 480, sdk = 34, abis = listOf("arm64-v8a"))

        assertEquals(UpdateCheck.Available(update()), check)
        assertEquals(UpdateCheck.UpToDate, client.check(address(), installedCode = 512, sdk = 34, abis = listOf("arm64-v8a")))
    }

    @Test fun saysWhyItCouldntCheck() = runBlocking {
        expect(UpdateProblem.NotThere) { client.check(address(), 480, 34, listOf("arm64-v8a")) }

        answers["/plasma/update.json"] = { MockResponse(code = 503) }
        expect(UpdateProblem.CantReach) { client.check(address(), 480, 34, listOf("arm64-v8a")) }

        answers["/plasma/update.json"] = { MockResponse(code = 200, body = "<html>Sign in</html>") }
        expect(UpdateProblem.BadFile) { client.check(address(), 480, 34, listOf("arm64-v8a")) }

        // Too big to be an update file.
        answers["/plasma/update.json"] = { MockResponse(code = 200, body = "{" + " ".repeat(300_000) + "}") }
        expect(UpdateProblem.BadFile) { client.check(address(), 480, 34, listOf("arm64-v8a")) }

        val offline = server.url("/").toString()
        server.close()
        expect(UpdateProblem.CantReach) { client.check(offline + "update.json", 480, 34, listOf("arm64-v8a")) }
    }

    @Test fun downloadsAndChecksTheApk() = runBlocking {
        answers["/plasma/apk/app-arm64-v8a-release.apk"] = { MockResponse.Builder().body(Buffer().write(apk)).build() }
        val into = File(folder, "plasma-512.apk")
        val heard = mutableListOf<Float>()

        client.download(update(), into) { heard += it }

        assertArrayEquals(apk, into.readBytes())
        assertEquals(1f, heard.last())
        assertTrue("Only as it moves on: $heard", heard.size in 2..101)
        assertEquals("Nothing left over", listOf(into.name), folder.list()!!.toList())
    }

    @Test fun aDamagedDownloadLeavesNothing() = runBlocking {
        answers["/plasma/apk/app-arm64-v8a-release.apk"] = { MockResponse.Builder().body(Buffer().write(apk.copyOf(apk.size - 1))).build() }
        val into = File(folder, "plasma-512.apk")

        expect(UpdateProblem.Damaged) { client.download(update(), into) }

        assertFalse(into.exists())
        assertEquals(emptyList<String>(), folder.list()!!.toList())
    }

    @Test fun aMissingApkSaysSo() = runBlocking {
        val into = File(folder, "plasma-512.apk")
        expect(UpdateProblem.NotThere) { client.download(update(), into) }
        assertFalse(into.exists())
    }
}
