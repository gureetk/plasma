// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
        advanced = "Advanced",
        fieldLabels = mapOf("stbModel" to "STB model", "timeZone" to "Time zone", "userAgent" to "User agent", "loginPassword" to "Password", "format" to "Stream format", "refresh" to "Refresh channels"),
        choices = mapOf(
            "stbModel" to listOf(PhoneChoice("", "MAG250"), PhoneChoice("MAG254", "MAG254")),
            "format" to listOf(PhoneChoice("", "The account’s own"), PhoneChoice("ts", "MPEG-TS"), PhoneChoice("m3u8", "HLS")),
            "refresh" to listOf(PhoneChoice("", "Same as Portals & Playlists"), PhoneChoice("6", "Every 6 hours")),
        ),
        fieldPlaylistFile = "Or a playlist file",
        missingPlaylist = "Fill in the playlist’s address, or choose its file.",
        notPlaylist = "That file isn’t an M3U playlist.",
        playlistTooBig = "That file is bigger than 16 MB.",
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

    @Test fun advancedIsOnThePhoneTooFolded() {
        // The TV's Add a Source › Advanced, by the same names (#122).
        val server = server()
        val code = server.code
        val (_, form) = get(server.url("/pair?code=$code&type=stalker"))
        assertTrue(form.contains("<details><summary>Advanced</summary>"))
        assertTrue(form.contains("<select id=\"stbModel\" name=\"stbModel\"><option value=\"\">MAG250</option><option value=\"MAG254\">MAG254</option></select>"))
        assertTrue(form.contains("name=\"loginPassword\" type=\"password\""))

        post(
            server.url("/pair"),
            mapOf(
                "code" to code, "csrf" to csrfOf(form), "type" to "stalker",
                "name" to "Home", "portal" to "http://portal.example.org/c/",
                "stbModel" to "MAG254", "userAgent" to " Box/2.0 ", "loginPassword" to " pass word ",
                // A choice the page didn't offer, and Xtream's.
                "timeZone" to "Mars/Olympus", "format" to "m3u8",
            ),
        )

        assertEquals(
            mapOf(
                PhoneField.Name to "Home",
                PhoneField.Portal to "http://portal.example.org/c/",
                PhoneField.StbModel to "MAG254",
                PhoneField.UserAgent to "Box/2.0",
                // A portal's password as typed, as on the TV.
                PhoneField.LoginPassword to " pass word ",
            ),
            received.single().fields,
        )
    }

    @Test fun aPlaylistCanComeAsAFile() {
        // #122: a file on the phone, kept by the TV, in place of the playlist's address.
        val server = server()
        val code = server.code
        val (_, form) = get(server.url("/pair?code=$code&type=m3u"))
        assertTrue(form.contains("enctype=\"multipart/form-data\""))
        assertTrue(form.contains("name=\"playlistFile\" type=\"file\""))
        // Its address isn't needed on its own any more.
        assertFalse(Regex("name=\"playlist\"[^>]*required").containsMatchIn(form))
        val csrf = csrfOf(form)

        fun send(file: String?, playlist: String = ""): Pair<Int, String> {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("code", code).addFormDataPart("csrf", csrf).addFormDataPart("type", "m3u")
                .addFormDataPart("name", "Home").addFormDataPart("playlist", playlist)
                .apply { if (file != null) addFormDataPart("playlistFile", "My List.m3u", file.toRequestBody("audio/x-mpegurl".toMediaType())) }
                .build()
            return http.newCall(Request.Builder().url(server.url("/pair")).post(body).build()).execute().use { it.code to it.body.string() }
        }

        assertTrue(send(null).second.contains("Fill in the playlist’s address, or choose its file."))
        assertTrue(send("<!DOCTYPE html><html></html>").second.contains("That file isn’t an M3U playlist."))
        assertTrue(received.isEmpty())
        val (status, sent) = send("#EXTM3U\n#EXTINF:-1,Arena Sports 1\nhttp://stream.example/live/9105.ts\n", playlist = "http://typed.example.org/list.m3u")

        assertEquals(200, status)
        assertTrue(sent.contains("Sent to Living Room TV"))
        val submission = received.single()
        assertEquals("My List.m3u", submission.playlist!!.name)
        assertTrue(String(submission.playlist!!.bytes).startsWith("#EXTM3U"))
        // The file is taken over the address typed beside it.
        assertEquals(mapOf(PhoneField.Name to "Home"), submission.fields)
    }

    @Test fun aFormMissingAFieldItNeedsIsAskedForAgain() {
        val server = server()
        val code = server.code
        val (_, form) = get(server.url("/pair?code=$code&type=m3u"))

        val (status, page) = post(server.url("/pair"), mapOf("code" to code, "csrf" to csrfOf(form), "type" to "m3u", "name" to "Backup"))

        assertEquals(400, status)
        // A playlist's address or its file (#122).
        assertTrue(page.contains("Fill in the playlist’s address, or choose its file."))
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

    private val debugTexts = texts.copy(
        debugTitle = "Pair Plasma with Living Room TV",
        debugText = "Type what the TV shows under Pair device with pairing code.",
        fieldDebugCode = "Wi-Fi pairing code",
        fieldDebugPort = "Port",
        debugPortHint = "The number after the colon in IP address & Port.",
        debugSend = "Pair",
        debugSentTitle = "Sent to Living Room TV",
        debugSentText = "Keep the code on the TV until it says it’s paired.",
        debugMissing = "Type the six-digit code and the port.",
    )

    private fun debugServer(codes: MutableList<DebugCode>): PairingServer =
        PairingServer(debugTexts, onSubmission = { received += it }, now = { clock }, purpose = PhonePurpose.DebugPairing, onDebugCode = { codes += it }).also { server ->
            server.start(InetAddress.getLoopbackAddress(), scope, port = 0)
            servers += server
        }

    @Test fun theTvsPairingCodeComesFromThePhone() {
        val codes = CopyOnWriteArrayList<DebugCode>()
        val server = debugServer(codes)
        // The QR code opens /adb (brief 4.24).
        assertTrue(server.invite.value!!.url.endsWith("/adb?code=${server.code}"))
        val code = server.code
        val (status, form) = get(server.url("/adb?code=$code"))
        assertEquals(200, status)
        assertTrue(form.contains("Wi-Fi pairing code"))
        // The source form isn't here.
        assertEquals(404, get(server.url("/pair?code=$code")).first)

        val (sent, page) = post(server.url("/adb"), mapOf("code" to code, "csrf" to csrfOf(form), "pairing" to "482 913", "port" to "37115"))
        assertEquals(200, sent)
        assertTrue(page.contains("Sent to Living Room TV"))
        assertEquals(listOf(DebugCode("482913", 37115)), codes.toList())
        assertTrue(received.isEmpty())
        // One code per page: a new one for the next.
        assertNotEquals(code, server.code)
    }

    @Test fun aPairingCodeNeedsSixDigitsAndAPort() {
        val codes = CopyOnWriteArrayList<DebugCode>()
        val server = debugServer(codes)
        val code = server.code
        val (_, form) = get(server.url("/adb?code=$code"))
        val (status, page) = post(server.url("/adb"), mapOf("code" to code, "csrf" to csrfOf(form), "pairing" to "4829", "port" to ""))
        assertEquals(400, status)
        assertTrue(page.contains("Type the six-digit code and the port."))
        assertTrue(codes.isEmpty())
        // A forged form is turned away.
        val (forged, _) = post(server.url("/adb"), mapOf("code" to code, "csrf" to "forged", "pairing" to "482913", "port" to "37115"))
        assertEquals(403, forged)
        assertTrue(codes.isEmpty())
    }

    @Test fun aClosedServerStopsAnswering() {
        val server = server()
        val address = server.url("/")

        server.close()

        assertEquals(null, server.invite.value)
        // Nothing of Plasma's answers there any more. The port is free again, so another test's
        // server on this machine may have been given it since: what that one sends doesn't count.
        val after = runCatching { get(address) }.getOrNull()
        assertTrue(after == null || !after.second.contains("Enter the Code on Your TV"))
    }

    // ---- Export Diagnostics on a phone (brief 5.17 and 6.11) -------------------------------------

    private val diagnosticsTexts = texts.copy(
        diagnosticsTitle = "Diagnostics from Living Room TV",
        diagnosticsText = "Recent logs from Plasma, with logins, passwords and server addresses taken out.",
        diagnosticsDownload = "Download",
        diagnosticsReportText = "To report a problem, open an issue on GitHub and attach the file.",
        diagnosticsReport = "Report a Problem on GitHub",
    )

    private var bundles = 0

    private fun diagnosticsServer(): PairingServer = PairingServer(
        diagnosticsTexts,
        onSubmission = { received += it },
        now = { clock },
        allows = ::isHomeNetwork,
        purpose = PhonePurpose.Diagnostics,
        onFile = {
            bundles++
            PhoneFile("plasma-diagnostics-20261102-2041.zip", "application/zip", byteArrayOf(80, 75, 5, 6))
        },
        reportUrl = "https://github.com/gureetk/plasma/issues/new/choose",
    ).also { server ->
        server.start(InetAddress.getLoopbackAddress(), scope, port = 0)
        servers += server
    }

    @Test fun theDiagnosticsPageOffersTheBundleAndTheIssueForm() {
        val server = diagnosticsServer()
        assertTrue(server.invite.value!!.url.contains("/diagnostics?code=${server.code}"))

        val (status, page) = get(server.url("/diagnostics?code=${server.code}"))

        assertEquals(200, status)
        assertTrue(page.contains("Diagnostics from Living Room TV"))
        assertTrue(page.contains("action=\"/diagnostics/file\""))
        assertTrue(page.contains("name=\"code\" value=\"${server.code}\""))
        assertTrue(page.contains("href=\"https://github.com/gureetk/plasma/issues/new/choose\""))
        assertEquals("Nothing is made until it's asked for", 0, bundles)
    }

    @Test fun theBundleComesAsAFileOnlyWithTheCodeAndThePagesToken() {
        val server = diagnosticsServer()
        val code = server.code
        val (_, page) = get(server.url("/diagnostics?code=$code"))
        val csrf = csrfOf(page)

        val (noToken, _) = get(server.url("/diagnostics/file?code=$code"))
        val (wrongCode, _) = get(server.url("/diagnostics/file?code=AAAAAA&csrf=$csrf"))
        assertEquals(403, noToken)
        assertEquals(403, wrongCode)
        assertEquals(0, bundles)

        http.newCall(Request.Builder().url(server.url("/diagnostics/file?code=$code&csrf=$csrf")).build()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("application/zip", response.header("Content-Type"))
            assertEquals("attachment; filename=\"plasma-diagnostics-20261102-2041.zip\"", response.header("Content-Disposition"))
            assertEquals(listOf<Byte>(80, 75, 5, 6), response.body.bytes().toList())
        }
        // The code stays good: the phone can fetch it again.
        assertEquals(200, get(server.url("/diagnostics/file?code=$code&csrf=$csrf")).first)
        assertEquals(2, bundles)
    }

    // ---- Back Up Settings and Restore from Backup on a phone (#108) ---------------------------------

    private val backupTexts = diagnosticsTexts.copy(
        backupTitle = "Backup of Living Room TV",
        backupText = "Plasma's settings, sources, favorites and profiles, in one file.",
        backupDownload = "Download",
        restoreTitle = "Restore Living Room TV",
        restoreText = "Choose a backup of Plasma to send to the TV.",
        restoreFile = "Backup file",
        restorePassphrase = "Passphrase",
        restorePassphraseHint = "Only if the backup has logins.",
        restoreSend = "Send to Living Room TV",
        restoreNoFile = "Choose a backup file.",
        restoreSentTitle = "Sent to Living Room TV",
        restoreSentText = "Check what the backup replaces on the TV, then choose Restore.",
    )

    private val uploads = CopyOnWriteArrayList<PhoneUpload>()

    private fun backupServer(purpose: PhonePurpose, answer: (PhoneUpload) -> String? = { null }): PairingServer = PairingServer(
        backupTexts,
        onSubmission = { received += it },
        now = { clock },
        purpose = purpose,
        onFile = { PhoneFile("plasma-backup-20261103-2000.json", "application/json", "{\"format\":\"plasma-backup\"}".toByteArray()) },
        onUpload = { upload -> uploads += upload; answer(upload) },
    ).also { server ->
        server.start(InetAddress.getLoopbackAddress(), scope, port = 0)
        servers += server
    }

    private fun upload(server: PairingServer, code: String, csrf: String, file: ByteArray?, passphrase: String = ""): Pair<Int, String> {
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("code", code)
            .addFormDataPart("csrf", csrf)
            .apply { if (file != null) addFormDataPart("backup", "plasma-backup.json", file.toRequestBody("application/json".toMediaType())) }
            .addFormDataPart("passphrase", passphrase)
            .build()
        return http.newCall(Request.Builder().url(server.url("/restore")).post(body).build()).execute().use { it.code to it.body.string() }
    }

    @Test fun theBackupPageHandsOverTheBackup() {
        val server = backupServer(PhonePurpose.Backup)
        val code = server.code
        val (status, page) = get(server.url("/backup?code=$code"))
        assertEquals(200, status)
        assertTrue(page.contains("Backup of Living Room TV"))
        assertTrue(page.contains("action=\"/backup/file\""))
        val csrf = csrfOf(page)

        assertEquals(403, get(server.url("/backup/file?code=$code")).first)
        http.newCall(Request.Builder().url(server.url("/backup/file?code=$code&csrf=$csrf")).build()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("attachment; filename=\"plasma-backup-20261103-2000.json\"", response.header("Content-Disposition"))
            assertEquals("{\"format\":\"plasma-backup\"}", response.body.string())
        }
        // Diagnostics' file isn't here.
        assertEquals(404, get(server.url("/diagnostics/file?code=$code&csrf=$csrf")).first)
    }

    @Test fun aBackupSentFromAPhoneReachesTheTvOncePerCode() {
        val server = backupServer(PhonePurpose.Restore)
        val code = server.code
        val (status, page) = get(server.url("/restore?code=$code"))
        assertEquals(200, status)
        assertTrue(page.contains("enctype=\"multipart/form-data\""))
        assertTrue(page.contains("type=\"file\""))
        val csrf = csrfOf(page)

        // Without its token, or without a file, it isn't taken.
        assertEquals(403, upload(server, code, "forged", "{}".toByteArray()).first)
        val (noFile, noFilePage) = upload(server, code, csrf, file = null)
        assertEquals(400, noFile)
        assertTrue(noFilePage.contains("Choose a backup file."))
        assertTrue(uploads.isEmpty())

        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        val (sent, sentPage) = upload(server, code, csrf, bytes, passphrase = "correct horse")
        assertEquals(200, sent)
        assertTrue(sentPage.contains("Sent to Living Room TV"))
        val received = uploads.single()
        assertEquals("plasma-backup.json", received.name)
        assertEquals(bytes.toList(), received.bytes.toList())
        assertEquals("correct horse", received.passphrase)
        // One backup per code: the TV shows a new one.
        assertNotEquals(code, server.code)
        assertEquals(403, upload(server, code, csrf, bytes).first)
    }

    @Test fun whatTheTvRefusesIsSaidOnThePhone() {
        val server = backupServer(PhonePurpose.Restore) { "That isn't a Plasma backup." }
        val code = server.code
        val csrf = csrfOf(get(server.url("/restore?code=$code")).second)

        val (status, page) = upload(server, code, csrf, "hello".toByteArray())
        assertEquals(400, status)
        assertTrue(page.contains("That isn&#39;t a Plasma backup."))
        assertEquals(null, uploads.single().passphrase)
        // The code stays good for another try.
        assertEquals(code, server.code)
    }

    @Test fun aBackupBiggerThanAnyIsRefused() {
        val server = backupServer(PhonePurpose.Restore)
        val code = server.code
        val csrf = csrfOf(get(server.url("/restore?code=$code")).second)
        val status = runCatching { upload(server, code, csrf, ByteArray(PairingServer.MAX_UPLOAD_BYTES + 1)).first }.getOrDefault(413)
        assertEquals(413, status)
        assertTrue(uploads.isEmpty())
    }
}
