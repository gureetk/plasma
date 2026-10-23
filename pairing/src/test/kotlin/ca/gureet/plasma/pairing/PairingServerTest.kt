// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Add a Source from a phone (brief 5.15), against the server on this machine. */
class PairingServerTest {
    private val texts = PhonePageTexts(
        tvName = "Living Room TV",
        title = "Add a Source",
        stalker = "Stalker Portal",
        xtream = "Xtream Codes",
        m3u = "M3U Playlist",
        fieldName = "Name",
        fieldPortal = "Portal URL",
        fieldMac = "MAC address",
        macHint = "Leave it empty to use the TV’s own.",
        fieldServer = "Server URL",
        fieldUsername = "Username",
        fieldPassword = "Password",
        fieldPlaylist = "Playlist URL",
        fieldGuide = "Guide URL (XMLTV)",
        send = "Send to Living Room TV",
        codeTitle = "Enter the Code on Your TV",
        codeText = "It’s under the QR code on Add a Source.",
        codeLabel = "Code",
        next = "Continue",
        wrongCode = "That code didn’t work. Check the one on your TV.",
        tooManyTries = "Too many tries. Your TV shows a new code now.",
        missingAddress = "Fill in every field that needs it.",
        sentTitle = "Sent to Living Room TV",
        sentText = "Check the details on the TV, then choose Connect.",
        notFound = "Not here. Scan the code on your TV again.",
        note = "Details stay on your home network.",
    )
    private val received = CopyOnWriteArrayList<PhoneSubmission>()
    private var clock = 1_792_611_900_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = OkHttpClient()
    private val servers = mutableListOf<PairingServer>()

    @After fun tearDown() {
        servers.forEach { it.close() }
        scope.cancel()
    }

    private fun server(allows: (InetAddress) -> Boolean = ::isHomeNetwork): PairingServer =
        PairingServer(texts, onSubmission = { received += it }, now = { clock }, allows = allows).also { server ->
            server.start(InetAddress.getLoopbackAddress(), scope, port = 0)
            servers += server
        }

    private val PairingServer.code: String get() = invite.value!!.code

    private fun PairingServer.url(path: String) = "http://${invite.value!!.address}$path"

    private fun get(url: String): Pair<Int, String> = http.newCall(Request.Builder().url(url).build()).execute().use { it.code to it.body.string() }

    private fun post(url: String, form: Map<String, String>): Pair<Int, String> {
        val body = FormBody.Builder().apply { form.forEach { (name, value) -> add(name, value) } }.build()
        return http.newCall(Request.Builder().url(url).post(body).build()).execute().use { it.code to it.body.string() }
    }

    private fun csrfOf(page: String): String = Regex("name=\"csrf\" value=\"([^\"]+)\"").find(page)!!.groupValues[1]

    @Test fun theFormOpensOnlyWithTheCodeOnTheTv() {
        val server = server()
        server.kind = SourceKind.Xtream

        val (_, start) = get(server.url("/"))
        assertTrue(start.contains("Enter the Code on Your TV"))
        val (wrongStatus, wrong) = get(server.url("/pair?code=AAAAAA"))
        assertEquals(403, wrongStatus)
        assertTrue(wrong.contains("That code didn’t work"))
        // Typed on a phone: lower case, with a space.
        val typed = server.code.lowercase().chunked(3).joinToString(" ")
        val (status, form) = get(server.url("/pair?code=${typed.replace(" ", "%20")}"))

        assertEquals(200, status)
        assertTrue(form.contains("aria-current=\"page\">Xtream Codes"))
        assertTrue(form.contains("name=\"username\""))
        assertTrue(form.contains("type=\"password\""))
        assertFalse(form.contains("name=\"portal\""))
        assertTrue(csrfOf(form).length > 20)
    }

    @Test fun aSentFormFillsInTheTvAndEndsItsCode() {
        val server = server()
        val code = server.code
        val (_, form) = get(server.url("/pair?code=$code&type=xtream"))

        val (status, sent) = post(
            server.url("/pair"),
            mapOf(
                "code" to code, "csrf" to csrfOf(form), "type" to "xtream",
                "name" to " Home ", "server" to "http://line.example.org:8080", "username" to "viewer", "password" to "s3cret",
                // Not one of Xtream's fields.
                "portal" to "http://elsewhere.example.org/c/",
            ),
        )

        assertEquals(200, status)
        assertTrue(sent.contains("Sent to Living Room TV"))
        assertFalse(sent.contains("s3cret") || sent.contains("viewer") || sent.contains("line.example.org"))
        assertEquals(
            listOf(
                PhoneSubmission(
                    SourceKind.Xtream,
                    mapOf(
                        PhoneField.Name to "Home",
                        PhoneField.Server to "http://line.example.org:8080",
                        PhoneField.Username to "viewer",
                        PhoneField.Password to "s3cret",
                    ),
                ),
            ),
            received.toList(),
        )
        // The code worked once; the TV shows a new one.
        assertNotEquals(code, server.code)
        assertEquals(403, get(server.url("/pair?code=$code")).first)
    }

    @Test fun aFormMissingAFieldItNeedsIsAskedForAgain() {
        val server = server()
        val code = server.code
        val (_, form) = get(server.url("/pair?code=$code&type=m3u"))

        val (status, page) = post(server.url("/pair"), mapOf("code" to code, "csrf" to csrfOf(form), "type" to "m3u", "name" to "Backup"))

        assertEquals(400, status)
        assertTrue(page.contains("Fill in every field that needs it."))
        assertTrue(received.isEmpty())
        assertEquals(code, server.code)
    }

    @Test fun aFormWithoutItsTokenIsTurnedAway() {
        val server = server()
        val code = server.code

        val (status, _) = post(server.url("/pair"), mapOf("code" to code, "csrf" to "forged", "type" to "m3u", "name" to "Backup", "playlist" to "http://x.example.org/a.m3u"))

        assertEquals(403, status)
        assertTrue(received.isEmpty())
    }

    @Test fun fiveWrongTriesEndTheCode() {
        val server = server()
        val code = server.code

        repeat(4) { assertTrue(get(server.url("/pair?code=AAAAAA")).second.contains("That code didn’t work")) }
        assertEquals(code, server.code)
        val (_, last) = get(server.url("/pair?code=AAAAAA"))

        assertTrue(last.contains("Too many tries"))
        assertNotEquals(code, server.code)
        assertEquals(403, get(server.url("/pair?code=$code")).first)
    }

    @Test fun aCodeLastsTenMinutes() {
        val server = server()
        val code = server.code

        clock += PairingServer.LIFETIME_MS

        assertEquals(403, get(server.url("/pair?code=$code")).first)
        assertNotEquals(code, server.code)
        assertEquals(200, get(server.url("/pair?code=${server.code}")).first)
    }

    @Test fun onlyPhonesOnTheHomeNetworkGetAnAnswer() {
        val server = server(allows = { false })

        assertEquals(403, get(server.url("/")).first)
        assertTrue(isHomeNetwork(InetAddress.getByName("192.168.1.20")))
        assertTrue(isHomeNetwork(InetAddress.getByName("10.0.0.7")))
        assertTrue(isHomeNetwork(InetAddress.getByName("172.20.1.2")))
        assertTrue(isHomeNetwork(InetAddress.getByName("fd12:3456::1")))
        assertTrue(isHomeNetwork(InetAddress.getByName("fe80::1")))
        assertFalse(isHomeNetwork(InetAddress.getByName("8.8.8.8")))
        assertFalse(isHomeNetwork(InetAddress.getByName("2001:db8::1")))
    }

    @Test fun pagesAreSafeToShow() {
        val server = PairingServer(texts.copy(tvName = "<b>TV</b>"), onSubmission = {}, now = { clock })
        server.start(InetAddress.getLoopbackAddress(), scope, port = 0)
        servers += server

        http.newCall(Request.Builder().url(server.url("/")).build()).execute().use { response: Response ->
            assertTrue(response.body.string().contains("&lt;b&gt;TV&lt;/b&gt;"))
            assertEquals("no-store", response.header("Cache-Control"))
            assertTrue(response.header("Content-Security-Policy")!!.contains("default-src 'none'"))
        }
        val huge = "a".repeat(20_000).toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val status = http.newCall(Request.Builder().url(server.url("/pair")).post(huge).build()).execute().use { it.code }
        assertEquals(413, status)
    }

    @Test fun theQrCodeOpensTheForm() {
        val server = server()
        val invite = server.invite.value!!

        assertEquals("http://${invite.address}/pair?code=${invite.code}", invite.url)
        assertEquals(QrCodes.encode(invite.url), invite.qr)
        assertTrue(invite.code.all { it in PairingServer.CODE_ALPHABET })
        assertEquals(PairingServer.CODE_LENGTH, invite.code.length)
    }

    @Test fun slowConnectionsCantTakeItOver() {
        val server = server()
        val loopback = InetAddress.getLoopbackAddress()
        val port = server.invite.value!!.address.substringAfterLast(':').toInt()
        // Eight connections that never send a request hold every place.
        val stalled = (1..8).map { Socket(loopback, port) }
        Thread.sleep(300)

        Socket(loopback, port).use { extra ->
            extra.soTimeout = 2_000
            // Turned away at once, with no answer.
            assertEquals(-1, extra.getInputStream().read())
        }

        stalled.forEach { it.close() }
        // Room again for a phone.
        var status = 0
        repeat(20) {
            if (status != 200) {
                status = runCatching { get(server.url("/")).first }.getOrDefault(0)
                if (status != 200) Thread.sleep(100)
            }
        }
        assertEquals(200, status)
    }

    @Test fun aClosedServerStopsAnswering() {
        val server = server()
        val address = server.url("/")

        server.close()

        assertEquals(null, server.invite.value)
        assertTrue(runCatching { get(address) }.isFailure)
    }
}
