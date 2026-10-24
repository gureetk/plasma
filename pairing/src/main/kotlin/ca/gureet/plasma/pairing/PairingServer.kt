// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

import ca.gureet.plasma.core.model.QrMatrix
import java.io.Closeable
import java.io.IOException
import java.net.BindException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A field of the phone form. Its [key] is the field's name on the TV's Add a Source form too, so
 * what the phone sends lands in the same field.
 */
enum class PhoneField(val key: String, internal val required: Boolean = false, internal val address: Boolean = false) {
    Name("name", required = true),
    Portal("portal", required = true, address = true),
    Mac("mac"),
    Server("server", required = true, address = true),
    Username("username", required = true),
    Password("password", required = true),
    Playlist("playlist", required = true, address = true),
    Guide("guide", address = true),
}

/** The kinds of source the phone page offers (brief 4.21), with their fields in order. */
enum class SourceKind(val key: String, val fields: List<PhoneField>) {
    Stalker("stalker", listOf(PhoneField.Name, PhoneField.Portal, PhoneField.Mac)),
    Xtream("xtream", listOf(PhoneField.Name, PhoneField.Server, PhoneField.Username, PhoneField.Password)),
    M3u("m3u", listOf(PhoneField.Name, PhoneField.Playlist, PhoneField.Guide)),
}

/** What a phone sent from the Add a Source page: the kind of source and what was typed, by field. */
data class PhoneSubmission(val kind: SourceKind, val fields: Map<PhoneField, String>)

/** What the phone page is for: Add a Source (brief 4.21), or pairing with the TV's wireless debugging (4.24). */
enum class PhonePurpose(internal val path: String) {
    AddSource("/pair"),
    DebugPairing("/adb"),
}

/**
 * The pairing code and port the TV's wireless debugging shows ("Pair device with pairing code",
 * brief 4.24), as typed on a phone while the TV's dialog stays open.
 */
data class DebugCode(val code: String, val port: Int)

/** What the TV shows to let a phone in (brief 4.21 and 5.15). */
data class PairingInvite(
    /** "192.168.1.42:8484" */
    val address: String,
    /** Six characters with no look-alikes, such as "7KQ4MZ". */
    val code: String,
    /** The page the QR code opens: `http://192.168.1.42:8484/pair?code=7KQ4MZ`. */
    val url: String,
    val qr: QrMatrix,
)

/**
 * Plasma's local web server for Add a Source (brief 5.15): a phone on the same Wi-Fi scans the
 * TV's QR code, or types its address and code, and fills the form in there.
 *
 * Only phones on the home network get an answer. The code is good for one form and 10 minutes,
 * and 5 wrong tries end it; each time, the TV shows a new one. The form carries a token of its
 * own, and nothing typed is ever sent back. The server only runs while Add a Source is open.
 */
class PairingServer(
    private val texts: PhonePageTexts,
    private val onSubmission: (PhoneSubmission) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val allows: (InetAddress) -> Boolean = ::isHomeNetwork,
    private val purpose: PhonePurpose = PhonePurpose.AddSource,
    private val onDebugCode: (DebugCode) -> Unit = {},
) : Closeable {
    private val _invite = MutableStateFlow<PairingInvite?>(null)

    /** What the TV shows: the address, the code and its QR code. Null until it starts. */
    val invite: StateFlow<PairingInvite?> = _invite.asStateFlow()

    /** The kind the TV's form is on, which the phone's form starts on too. */
    @Volatile var kind: SourceKind = SourceKind.Stalker

    private var server: ServerSocket? = null
    private var host = ""
    private var session: Session? = null
    private var timer: Job? = null

    @Volatile private var closed = false

    /** Requests being answered: each has a thread, so there's a cap, and a deadline for each. */
    private val serving = AtomicInteger()
    private val deadlines = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "plasma-pairing-deadline").apply { isDaemon = true } }

    private class Session(val code: String, val csrf: String, val expiresAt: Long) {
        var attempts = 0
    }

    /**
     * Starts answering at [address], on port 8484 or, when something else has it, another free
     * port. A new code comes every 10 minutes, kept in [scope].
     *
     * @throws IOException when no port can be opened.
     */
    fun start(address: InetAddress, scope: CoroutineScope, port: Int = PORT) {
        check(server == null) { "Already started" }
        val socket = try {
            ServerSocket(port, BACKLOG, address)
        } catch (taken: BindException) {
            ServerSocket(0, BACKLOG, address)
        }
        server = socket
        val name = address.hostAddress.substringBefore('%')
        host = (if (address is Inet6Address) "[$name]" else name) + ":${socket.localPort}"
        synchronized(this) { newSession() }
        Thread(::accept, "plasma-pairing").apply { isDaemon = true }.start()
        timer = scope.launch {
            while (true) {
                val wait = synchronized(this@PairingServer) { session?.expiresAt?.minus(now()) } ?: return@launch
                delay(wait.coerceAtLeast(0) + 1)
                synchronized(this@PairingServer) { expireIfDue() }
            }
        }
    }

    override fun close() {
        closed = true
        timer?.cancel()
        runCatching { server?.close() }
        deadlines.shutdownNow()
        synchronized(this) { session = null }
        _invite.value = null
    }

    private fun accept() {
        val socket = server ?: return
        while (!closed) {
            val client = try {
                socket.accept()
            } catch (error: SocketException) {
                if (closed) return else continue
            } catch (error: IOException) {
                continue
            }
            // A phone needs a request or two at a time; more than a few at once is something else.
            if (serving.incrementAndGet() > MAX_REQUESTS) {
                serving.decrementAndGet()
                runCatching { client.close() }
                continue
            }
            Thread({
                try {
                    serve(client)
                } finally {
                    serving.decrementAndGet()
                }
            }, "plasma-pairing-request").apply { isDaemon = true }.start()
        }
    }

    private fun serve(socket: Socket) {
        // A request that trickles in a byte at a time is cut off, however slow each read.
        val deadline = runCatching { deadlines.schedule({ runCatching { socket.close() } }, REQUEST_DEADLINE_MS, TimeUnit.MILLISECONDS) }.getOrNull()
        socket.use {
            try {
                socket.soTimeout = READ_TIMEOUT_MS
                val out = socket.getOutputStream()
                if (!allows(socket.inetAddress)) return Http.respond(out, 403, null)
                val request = try {
                    Http.read(socket.getInputStream()) ?: return
                } catch (big: TooLarge) {
                    return Http.respond(out, 413, null)
                }
                val (status, page) = answer(request)
                Http.respond(out, status, page)
            } catch (error: IOException) {
                // The phone went away mid-request, or took too long.
            } finally {
                deadline?.cancel(false)
            }
        }
    }

    /** The status and page for [request]. */
    internal fun answer(request: HttpRequest): Pair<Int, String?> = synchronized(this) {
        if (session == null) return 404 to PhonePage.notFound(texts)
        expireIfDue()
        when {
            request.method != "GET" && request.method != "POST" -> 405 to null
            request.path == "/" && request.method == "GET" -> 200 to PhonePage.code(texts, action = purpose.path)
            request.path != purpose.path -> 404 to PhonePage.notFound(texts)
            request.method == "GET" -> openForm(request.query)
            purpose == PhonePurpose.DebugPairing -> submitDebugCode(request.form())
            else -> submit(request.form())
        }
    }

    private fun openForm(query: Map<String, String>): Pair<Int, String> {
        val code = query["code"] ?: return 200 to PhonePage.code(texts, action = purpose.path)
        val wrong = checkCode(code)
        if (wrong != null) return 403 to PhonePage.code(texts, wrong, purpose.path)
        val current = session ?: return 404 to PhonePage.notFound(texts)
        if (purpose == PhonePurpose.DebugPairing) return 200 to PhonePage.debugForm(texts, current.code, current.csrf)
        val chosen = SourceKind.entries.firstOrNull { it.key == query["type"] } ?: kind
        return 200 to PhonePage.form(texts, chosen, current.code, current.csrf)
    }

    private fun submit(form: Map<String, String>): Pair<Int, String> {
        val wrong = checkCode(form["code"].orEmpty())
        if (wrong != null) return 403 to PhonePage.code(texts, wrong)
        val current = session ?: return 404 to PhonePage.notFound(texts)
        // A form from somewhere else: it doesn't carry this one's token.
        if (!same(form["csrf"].orEmpty(), current.csrf)) {
            val ended = failedAttempt()
            return 403 to PhonePage.code(texts, if (ended) texts.tooManyTries else texts.wrongCode)
        }
        val chosen = SourceKind.entries.firstOrNull { it.key == form["type"] } ?: return 400 to PhonePage.form(texts, kind, current.code, current.csrf)
        val fields = chosen.fields.associateWith { form[it.key].orEmpty().trim() }.filterValues { it.isNotEmpty() }
        if (chosen.fields.any { it.required && it !in fields }) {
            return 400 to PhonePage.form(texts, chosen, current.code, current.csrf, texts.missingAddress)
        }
        onSubmission(PhoneSubmission(chosen, fields))
        // One form per code: this one stops working.
        newSession()
        return 200 to PhonePage.sent(texts)
    }

    /** The TV's pairing code and port, typed on the phone (brief 4.24). */
    private fun submitDebugCode(form: Map<String, String>): Pair<Int, String> {
        val wrong = checkCode(form["code"].orEmpty())
        if (wrong != null) return 403 to PhonePage.code(texts, wrong, purpose.path)
        val current = session ?: return 404 to PhonePage.notFound(texts)
        if (!same(form["csrf"].orEmpty(), current.csrf)) {
            val ended = failedAttempt()
            return 403 to PhonePage.code(texts, if (ended) texts.tooManyTries else texts.wrongCode, purpose.path)
        }
        val pairing = form["pairing"].orEmpty().filter { it.isDigit() }
        val port = form["port"].orEmpty().filter { it.isDigit() }.toIntOrNull()
        if (pairing.length != DEBUG_CODE_LENGTH || port == null || port !in 1..MAX_PORT) {
            return 400 to PhonePage.debugForm(texts, current.code, current.csrf, texts.debugMissing)
        }
        onDebugCode(DebugCode(pairing, port))
        newSession()
        return 200 to PhonePage.debugSent(texts)
    }

    /** Null when [typed] is the code; otherwise why it isn't, with the try counted. */
    private fun checkCode(typed: String): String? {
        val current = session ?: return texts.wrongCode
        val normalized = typed.uppercase().filter { it.isLetterOrDigit() }
        if (same(normalized, current.code)) return null
        return if (failedAttempt()) texts.tooManyTries else texts.wrongCode
    }

    /** Counts a wrong try; after the last one, the code is replaced. Returns whether it was. */
    private fun failedAttempt(): Boolean {
        val current = session ?: return true
        current.attempts++
        if (current.attempts < MAX_ATTEMPTS) return false
        newSession()
        return true
    }

    private fun expireIfDue() {
        val current = session ?: return
        if (now() >= current.expiresAt) newSession()
    }

    private fun newSession() {
        val code = String(CharArray(CODE_LENGTH) { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] })
        val token = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        session = Session(code, Base64.getUrlEncoder().withoutPadding().encodeToString(token), now() + LIFETIME_MS)
        val url = "http://$host${purpose.path}?code=$code"
        _invite.value = PairingInvite(host, code, url, QrCodes.encode(url))
    }

    /** Compares in constant time, so how long a wrong guess takes says nothing. */
    private fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    companion object {
        const val PORT = 8484

        /** How long a code lasts. */
        const val LIFETIME_MS = 10 * 60_000L

        /** Wrong tries a code takes before it's replaced. */
        const val MAX_ATTEMPTS = 5

        const val CODE_LENGTH = 6

        /** The TV's own pairing code: six digits. */
        private const val DEBUG_CODE_LENGTH = 6
        private const val MAX_PORT = 65535

        /** Characters that can't be taken for one another: no 0 or O, 1, I or L, 2, 5 or S, 8 or B, U or V. */
        internal const val CODE_ALPHABET = "34679ACDEFGHJKMNPQRTWXYZ"

        private const val TOKEN_BYTES = 18
        private const val BACKLOG = 8
        private const val READ_TIMEOUT_MS = 10_000
        private const val REQUEST_DEADLINE_MS = 20_000L
        private const val MAX_REQUESTS = 8
    }
}
