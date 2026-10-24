// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.stream.RangeSource
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ImpersonationLevel
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.SMB2GuestSigningRequiredException
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.PipeShare
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** Why a network folder couldn't be used (brief 5.6). */
enum class ShareProblem {
    /** Nothing answered at the address, or the network's down. */
    CantReach,

    /** The username or password was turned down. */
    WrongLogin,

    /** The server has no share of that name. */
    NoShare,

    /** The folder can't be written to. */
    ReadOnly,

    /** The server said no for another reason, or a copy came out the wrong size. */
    Refused,
}

class ShareException(val problem: ShareProblem, cause: Throwable? = null) : IOException(problem.name, cause)

/** An open share (brief 5.6). Paths in it have their parts separated by "/"; "" is the share itself. */
interface ShareSession : AutoCloseable {
    /** The folders in [path], A to Z. */
    fun folders(path: String): List<String>

    /** The files in [path]. */
    fun files(path: String): List<String>

    fun exists(path: String): Boolean

    /** Makes [path], and the folders above it. */
    fun makeFolders(path: String)

    /** Writes [path] from [input], in place of what was there. */
    fun write(path: String, input: InputStream)

    /** Renames [from] to [to], in place of what was there. */
    fun rename(from: String, to: String)

    fun delete(path: String)

    /** [path]'s size, or null when it isn't there. */
    fun size(path: String): Long?

    /** The room left on the share; null when it doesn't say. */
    fun freeBytes(): Long?

    override fun close()
}

/**
 * Network folders over SMB 2 and 3 (brief 5.6 and 6.1, smbj): signed at least, and encrypted when
 * the server offers SMB 3's encryption (6.10). SMB 1 isn't offered.
 */
object SmbShares {
    /** Signing required, as for any account. */
    private val signed: SmbConfig by lazy { config(signing = true) }

    /** A guest has no key to sign with: the server's own rules stand. */
    private val unsigned: SmbConfig by lazy { config(signing = false) }

    private fun config(signing: Boolean): SmbConfig = SmbConfig.builder()
        // Android's own crypto has no MD4, which NTLM needs.
        .withSecurityProvider(BCSecurityProvider())
        .withSigningRequired(signing)
        .withEncryptData(true)
        .withTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .withSoTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .withDfsEnabled(false)
        .build()

    fun open(folder: NetworkFolder): ShareSession = open(folder.host, folder.share, folder.username, folder.password, folder.domain)

    /** Signs in to [share] on [host]; a blank [username] signs in as a guest. Throws [ShareException]. */
    fun open(host: String, share: String, username: String, password: String, domain: String?): ShareSession {
        val signedIn = signIn(host, username, password, domain)
        try {
            val disk = try {
                signedIn.session.connectShare(share) as? DiskShare ?: throw ShareException(ShareProblem.NoShare)
            } catch (refused: SMBApiException) {
                throw ShareException(
                    when (refused.status) {
                        NtStatus.STATUS_BAD_NETWORK_NAME -> ShareProblem.NoShare
                        NtStatus.STATUS_ACCESS_DENIED -> ShareProblem.WrongLogin
                        else -> ShareProblem.Refused
                    },
                    refused,
                )
            }
            return SmbSession(signedIn, disk)
        } catch (failed: Exception) {
            signedIn.close()
            throw shareProblemOf(failed)
        }
    }

    /**
     * The folders [host] shares, A to Z, for the setup's list (brief 5.6), signing in as [open]
     * does. Throws [ShareException]: [ShareProblem.Refused] when the server won't say.
     */
    fun shares(host: String, username: String, password: String, domain: String?): List<String> {
        val signedIn = signIn(host, username, password, domain)
        try {
            val ipc = signedIn.session.connectShare(IPC) as? PipeShare ?: throw ShareException(ShareProblem.Refused)
            return ipc.use { pipes ->
                pipes.open(SRVSVC, SMB2ImpersonationLevel.Impersonation, PIPE_ACCESS, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
                    .use { pipe -> ShareListRpc.folders(pipe::transact, pipe::read) }
            }
        } catch (failed: Exception) {
            throw when (failed) {
                is ShareException -> failed
                // The server's there, and the sign-in worked: it won't list its shares.
                is SMBApiException, is IOException -> if (failed is TransportException) shareProblemOf(failed) else ShareException(ShareProblem.Refused, failed)
                else -> shareProblemOf(failed)
            }
        } finally {
            signedIn.close()
        }
    }

    /**
     * [path] in [share] on [folder]'s server, signed in to as [folder] is, to read from any point,
     * as a recording plays from the network folder (brief 5.6). Each read opens a connection of its
     * own, so a seek never waits on the one before. Throws [ShareException].
     */
    fun openFile(folder: NetworkFolder, share: String, path: String): RangeSource {
        val size = open(folder.host, share, folder.username, folder.password, folder.domain).use { it.size(path) }
            ?: throw ShareException(ShareProblem.Refused)
        return object : RangeSource {
            override val length: Long = size

            override fun open(offset: Long): InputStream = try {
                SmbFileInput(signIn(folder.host, folder.username, folder.password, folder.domain), share, path, offset, size)
            } catch (failed: ShareException) {
                throw IOException(failed)
            }
        }
    }

    /** A file on a share, read from [offset] to [length]; the connection goes with it. */
    private class SmbFileInput(private val signedIn: SignedIn, share: String, path: String, offset: Long, private val length: Long) : InputStream() {
        private val disk: DiskShare
        private val file: com.hierynomus.smbj.share.File
        private var at = offset

        init {
            try {
                disk = signedIn.session.connectShare(share) as? DiskShare ?: throw IOException("$share isn't a folder")
                file = disk.openFile(
                    path.trim('/').replace('/', '\\'),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
            } catch (failed: Exception) {
                signedIn.close()
                throw if (failed is IOException) failed else IOException(failed)
            }
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(into: ByteArray, offset: Int, count: Int): Int {
            if (count == 0) return 0
            if (at >= length) return -1
            val read = try {
                file.read(into, at, offset, minOf(count.toLong(), length - at).toInt())
            } catch (failed: SMBApiException) {
                throw IOException(failed)
            } catch (failed: SMBRuntimeException) {
                throw IOException(failed)
            }
            if (read <= 0) return -1
            at += read
            return read
        }

        override fun close() {
            runCatching { file.close() }
            runCatching { disk.close() }
            signedIn.close()
        }
    }

    /** A connection to a server, signed in. */
    private class SignedIn(val client: SMBClient, val connection: Connection, val session: Session) : AutoCloseable {
        override fun close() {
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { client.close() }
        }
    }

    /** Connects to [host] and signs in; "DOMAIN\user" names the domain too. Throws [ShareException]. */
    private fun signIn(host: String, username: String, password: String, domain: String?): SignedIn {
        val guest = username.isBlank()
        val client = SMBClient(if (guest) unsigned else signed)
        var connection: Connection? = null
        try {
            val (name, port) = hostAndPort(host)
            connection = client.connect(name, port)
            val login = if (guest) {
                AuthenticationContext.guest()
            } else {
                val (user, realm) = if ('\\' in username) username.substringAfter('\\') to username.substringBefore('\\') else username to domain
                AuthenticationContext(user, password.toCharArray(), realm)
            }
            val session = try {
                connection.authenticate(login)
            } catch (refused: SMBApiException) {
                throw ShareException(if (refused.status in LOGIN_STATUSES) ShareProblem.WrongLogin else ShareProblem.Refused, refused)
            }
            return SignedIn(client, connection, session)
        } catch (failed: Exception) {
            runCatching { connection?.close() }
            runCatching { client.close() }
            throw shareProblemOf(failed)
        }
    }

    /** What smbj threw, as a [ShareException]. */
    private fun shareProblemOf(failed: Throwable): ShareException = when (failed) {
        is ShareException -> failed
        // The server took the account as a guest, as Samba does for a name it doesn't know.
        is SMB2GuestSigningRequiredException -> ShareException(ShareProblem.WrongLogin, failed)
        is SMBApiException -> ShareException(if (failed.status in LOGIN_STATUSES) ShareProblem.WrongLogin else ShareProblem.Refused, failed)
        else -> ShareException(ShareProblem.CantReach, failed)
    }

    /** "nas.home.lan", or "nas.home.lan:4450" for a server on another port. */
    private fun hostAndPort(host: String): Pair<String, Int> {
        val trimmed = host.trim()
        val port = trimmed.substringAfterLast(':', "").toIntOrNull()
        return if (port != null && trimmed.count { it == ':' } == 1) trimmed.substringBeforeLast(':') to port else trimmed.removePrefix("[").removeSuffix("]") to SMB_PORT
    }

    private class SmbSession(private val signedIn: SignedIn, private val share: DiskShare) : ShareSession {
        override fun folders(path: String): List<String> = guarded {
            share.list(smb(path))
                .filter { it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L }
                .map { it.fileName }
                .filterNot { it == "." || it == ".." || it.first() in HIDDEN }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        override fun files(path: String): List<String> = guarded {
            if (!share.folderExists(smb(path))) return@guarded emptyList()
            share.list(smb(path)).filter { it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value == 0L }.map { it.fileName }
        }

        override fun exists(path: String): Boolean = guarded { share.fileExists(smb(path)) || share.folderExists(smb(path)) }

        override fun makeFolders(path: String) = guarded {
            var at = ""
            for (part in path.split('/').filter { it.isNotEmpty() }) {
                at = if (at.isEmpty()) part else "$at/$part"
                if (!share.folderExists(smb(at))) share.mkdir(smb(at))
            }
        }

        override fun write(path: String, input: InputStream): Unit = guarded {
            share.openFile(
                smb(path),
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null,
            ).use { file ->
                file.outputStream.use { out -> input.copyTo(out, BUFFER) }
            }
            Unit
        }

        override fun rename(from: String, to: String) = guarded {
            share.openFile(
                smb(from),
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { file -> file.rename(smb(to), true) }
        }

        override fun delete(path: String) = guarded {
            if (share.fileExists(smb(path))) share.rm(smb(path))
        }

        override fun size(path: String): Long? = guarded {
            if (!share.fileExists(smb(path))) null else share.getFileInformation(smb(path)).standardInformation.endOfFile
        }

        override fun freeBytes(): Long? = runCatching { share.shareInformation.freeSpace }.getOrNull()

        override fun close() {
            runCatching { share.close() }
            signedIn.close()
        }

        /** What smbj throws, as a [ShareException]. */
        private inline fun <T> guarded(work: () -> T): T = try {
            work()
        } catch (refused: SMBApiException) {
            throw ShareException(
                when (refused.status) {
                    NtStatus.STATUS_ACCESS_DENIED -> ShareProblem.ReadOnly
                    else -> ShareProblem.Refused
                },
                refused,
            )
        } catch (failed: IOException) {
            throw ShareException(ShareProblem.CantReach, failed)
        } catch (failed: SMBRuntimeException) {
            // The connection dropped, mostly.
            throw ShareException(ShareProblem.CantReach, failed)
        }

        /** smbj's paths have their parts separated by "\". */
        private fun smb(path: String): String = path.trim('/').replace('/', '\\')
    }

    private val LOGIN_STATUSES = setOf(
        NtStatus.STATUS_LOGON_FAILURE,
        NtStatus.STATUS_ACCOUNT_DISABLED,
        NtStatus.STATUS_PASSWORD_EXPIRED,
        NtStatus.STATUS_ACCESS_DENIED,
    )

    /** System folders a NAS shows: Synology's "#recycle" and "@eaDir", and hidden ones. */
    private val HIDDEN = setOf('.', '$', '#', '@')
    private const val IPC = "IPC$"
    private const val SRVSVC = "srvsvc"
    private val PIPE_ACCESS = EnumSet.of(
        AccessMask.FILE_READ_DATA,
        AccessMask.FILE_WRITE_DATA,
        AccessMask.FILE_APPEND_DATA,
        AccessMask.FILE_READ_EA,
        AccessMask.FILE_WRITE_EA,
        AccessMask.FILE_READ_ATTRIBUTES,
        AccessMask.FILE_WRITE_ATTRIBUTES,
        AccessMask.READ_CONTROL,
        AccessMask.SYNCHRONIZE,
    )
    private const val SMB_PORT = 445
    private const val TIMEOUT_S = 30L
    private const val BUFFER = 1024 * 1024
}

/**
 * Checks [path] in [share] can be written to, by writing a small file and deleting it, so a
 * read-only share fails when it's set up rather than at 3 a.m. (brief 5.6). Throws [ShareException].
 */
fun ShareSession.checkWritable(path: String) {
    val probe = (if (path.isBlank()) "" else "${path.trim('/')}/") + ".plasma-check"
    write(probe, "Plasma".byteInputStream())
    delete(probe)
}
