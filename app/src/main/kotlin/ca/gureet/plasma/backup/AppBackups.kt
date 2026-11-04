// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.backup

import android.content.Context
import ca.gureet.plasma.PhonePairingHost
import ca.gureet.plasma.R
import ca.gureet.plasma.RestartActivity
import ca.gureet.plasma.Updates
import ca.gureet.plasma.core.data.BackupFile
import ca.gureet.plasma.core.data.BackupUnreadable
import ca.gureet.plasma.core.data.PlasmaBackup
import ca.gureet.plasma.core.data.PlasmaData
import ca.gureet.plasma.core.data.RestoreResult
import ca.gureet.plasma.feature.settings.BackupActions
import ca.gureet.plasma.feature.settings.BackupOpened
import ca.gureet.plasma.feature.settings.BackupPlace
import ca.gureet.plasma.feature.settings.BackupSaved
import ca.gureet.plasma.feature.settings.BackupSummary
import ca.gureet.plasma.feature.settings.FoundBackup
import ca.gureet.plasma.feature.settings.PhonePairing
import ca.gureet.plasma.feature.settings.PhoneRestore
import ca.gureet.plasma.feature.settings.RestoreOutcome
import ca.gureet.plasma.pairing.LanAddress
import ca.gureet.plasma.pairing.PairingServer
import ca.gureet.plasma.pairing.PhoneFile
import ca.gureet.plasma.pairing.PhonePageTexts
import ca.gureet.plasma.pairing.PhonePurpose
import ca.gureet.plasma.pairing.PhoneUpload
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ca.gureet.plasma.feature.settings.R as SettingsR

/**
 * Back Up Settings and Restore from Backup in the app (brief 5.17 and 5.18, #108): backups made by
 * the data layer, saved in Plasma's folder on a USB drive, in Plasma's own storage on the TV or
 * handed to a phone, kept on the TV before an update, and put back, after which Plasma starts
 * again. [usb] is Plasma's storage on a USB drive, when one is connected; [busy] says when a
 * restore has to wait (a recording under way); [beforeRestore] stops what plays, just before
 * anything changes.
 */
class AppBackups(
    private val context: Context,
    private val data: PlasmaData,
    private val usb: () -> File?,
    private val busy: suspend () -> Boolean,
    private val beforeRestore: suspend () -> Unit,
) : BackupActions {
    /** The backup opened last, which [restore] puts back. */
    @Volatile private var opened: PlasmaBackup? = null

    /**
     * Backups on the TV itself, in Plasma's own storage: Save on This TV's and Plasma Update's, told
     * apart by their names. Clearing Plasma's storage or uninstalling it deletes them.
     */
    private val kept = File(context.filesDir, KEPT_FOLDER)

    private val version: String get() = Updates.versionOf(context)

    override suspend fun saveToUsb(passphrase: String?): BackupSaved = withContext(Dispatchers.IO) {
        val drive = usb() ?: return@withContext BackupSaved.NoDrive
        val folder = File(drive, FOLDER)
        try {
            val backup = data.backups.make(version, passphrase)
            val file = File(folder.apply { mkdirs() }, nameOf(backup, prefix = BACKUP))
            write(file, BackupFile.encode(backup))
            BackupSaved.Saved(file.name, shownPath(folder))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: IOException) {
            BackupSaved.Failed
        }
    }

    /** Save on This TV (the owner's ask, 3 November): in Plasma's own storage, the newest five kept. */
    override suspend fun saveOnTv(passphrase: String?): BackupSaved = withContext(Dispatchers.IO) {
        try {
            val backup = data.backups.make(version, passphrase)
            val file = File(kept.apply { mkdirs() }, nameOf(backup, prefix = BACKUP))
            write(file, BackupFile.encode(backup))
            keepNewest(BACKUP, KEEP_ON_TV)
            BackupSaved.OnTv(file.name)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: IOException) {
            BackupSaved.Failed
        }
    }

    override fun toPhone(passphrase: String?): Flow<PhonePairing> = channelFlow {
        val server = PairingServer(
            texts(),
            onSubmission = {},
            purpose = PhonePurpose.Backup,
            // Made when the phone asks for it, on the server's own thread.
            onFile = {
                runBlocking {
                    runCatching {
                        val backup = data.backups.make(version, passphrase)
                        PhoneFile(nameOf(backup, prefix = BACKUP), JSON, BackupFile.encode(backup))
                    }.getOrNull()
                }
            },
        )
        serve(server) { pairing -> pairing }
    }

    override suspend fun found(): List<FoundBackup> = withContext(Dispatchers.IO) {
        val onDrive = usb()?.let { listed(File(it, FOLDER), BackupPlace.UsbDrive) }.orEmpty()
        val onTv = listed(kept, BackupPlace.ThisTv).map { found ->
            if (found.name.startsWith(BEFORE_UPDATE)) found.copy(place = BackupPlace.BeforeUpdate) else found
        }
        (onDrive + onTv).sortedByDescending { it.madeAt }
    }

    override suspend fun open(found: FoundBackup): BackupOpened = withContext(Dispatchers.IO) {
        val file = File(found.id)
        val bytes = try {
            if (file.length() > PairingServer.MAX_UPLOAD_BYTES) null else file.readBytes()
        } catch (failed: IOException) {
            null
        }
        bytes?.let { read(it, passphrase = null) } ?: BackupOpened.Unreadable(newer = false)
    }

    override fun fromPhone(): Flow<PhoneRestore> = channelFlow {
        val server = PairingServer(
            texts(),
            onSubmission = {},
            purpose = PhonePurpose.Restore,
            onUpload = { upload -> received(upload) },
        )
        serve(server) { pairing -> PhoneRestore.Waiting(pairing) }
    }

    /** A backup a phone sent: opened, and handed to the sheet; or why not, for the phone. */
    private fun ProducerScope<PhoneRestore>.received(upload: PhoneUpload): String? = when (val read = read(upload.bytes, upload.passphrase)) {
        is BackupOpened.Ready -> {
            trySend(PhoneRestore.Received(read.summary))
            null
        }
        is BackupOpened.Unreadable -> context.getString(if (read.newer) R.string.phone_restore_newer else R.string.phone_restore_not_a_backup)
    }

    override suspend fun restore(passphrase: String?): RestoreOutcome {
        val backup = opened ?: return RestoreOutcome.Failed
        return try {
            // Once it starts, a restore finishes, whatever happens to the sheet.
            withContext(NonCancellable) {
                if (busy()) return@withContext RestoreOutcome.Busy
                when (val result = data.backups.restore(backup, passphrase, beforeChange = beforeRestore)) {
                    RestoreResult.WrongPassphrase -> RestoreOutcome.WrongPassphrase
                    is RestoreResult.Restored -> RestoreOutcome.Restored(result.needLogins)
                }
            }
        } catch (failed: Exception) {
            // A file that read as a backup but couldn't be put back, or the database refusing it.
            RestoreOutcome.Failed
        }
    }

    override fun restart() = RestartActivity.now(context)

    /**
     * Plasma Update's backup (5.18), just before it installs an update: without logins, which this
     * TV keeps anyway, on the TV itself, the newest two kept. Save on This TV's are left alone.
     */
    suspend fun beforeUpdate() = withContext(Dispatchers.IO) {
        val backup = data.backups.make(version, passphrase = null, beforeUpdate = true)
        write(File(kept.apply { mkdirs() }, nameOf(backup, prefix = BEFORE_UPDATE)), BackupFile.encode(backup))
        keepNewest(BEFORE_UPDATE, KEEP_BEFORE_UPDATE)
    }

    /** Deletes all but the newest [count] backups on the TV whose names start with [prefix]. */
    private fun keepNewest(prefix: String, count: Int) {
        kept.listFiles { file -> file.isFile && file.name.startsWith(prefix) && file.name.endsWith(EXTENSION) }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(count)
            .forEach { it.delete() }
    }

    private fun read(bytes: ByteArray, passphrase: String?): BackupOpened = try {
        val backup = BackupFile.decode(bytes)
        opened = backup
        val preview = data.backups.preview(backup)
        BackupOpened.Ready(
            BackupSummary(
                madeAt = preview.made,
                plasma = preview.plasma,
                beforeUpdate = preview.beforeUpdate,
                sources = preview.sources,
                profiles = preview.profiles,
                favorites = preview.favorites,
                plans = preview.plans,
                withLogins = preview.withLogins,
                passphrase = passphrase?.takeIf { preview.withLogins },
            ),
        )
    } catch (unreadable: BackupUnreadable) {
        BackupOpened.Unreadable(newer = unreadable.reason == BackupUnreadable.Reason.Newer)
    }

    /** Runs [server] while the flow is collected, sending what the TV shows. */
    private suspend fun <T> ProducerScope<T>.serve(server: PairingServer, shown: (PhonePairing) -> T) {
        try {
            val started = withContext(Dispatchers.IO) {
                val address = LanAddress.find() ?: return@withContext false
                try {
                    server.start(address, this@serve)
                    true
                } catch (failed: IOException) {
                    false
                }
            }
            if (!started) {
                send(shown(PhonePairing.Unavailable))
                awaitCancellation()
            }
            server.invite.collect { invite -> send(shown(invite?.let { PhonePairing.Ready(it.address, it.code, it.qr) } ?: PhonePairing.Unavailable)) }
        } finally {
            server.close()
        }
    }

    private fun listed(folder: File, place: BackupPlace): List<FoundBackup> =
        folder.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }.orEmpty()
            .map { FoundBackup(it.absolutePath, it.name, place, it.lastModified()) }

    /** Written whole or not at all: a half-written backup never sits under the real name. */
    private fun write(file: File, bytes: ByteArray) {
        val partial = File(file.parentFile, file.name + ".part")
        partial.writeBytes(bytes)
        if (!partial.renameTo(file)) {
            partial.delete()
            throw IOException("Couldn't save ${file.name}")
        }
    }

    /** "plasma-backup-20261103-2041.json" */
    private fun nameOf(backup: PlasmaBackup, prefix: String): String =
        prefix + STAMP.format(Instant.ofEpochMilli(backup.made).atZone(ZoneId.systemDefault())) + EXTENSION

    /** Where [folder] is on the drive, as a computer shows it: "Android/data/ca.gureet.plasma/files/Backups". */
    private fun shownPath(folder: File): String =
        folder.absolutePath.substringAfter("/Android/", "").let { if (it.isEmpty()) folder.name else "Android/$it" }

    /** The phone pages' words for backups, from Add a Source's with their own. */
    private fun texts(): PhonePageTexts {
        val base = PhonePairingHost.texts(context)
        return base.copy(
            title = context.getString(SettingsR.string.backup),
            codeText = context.getString(R.string.phone_diagnostics_code_text),
            backupTitle = context.getString(R.string.phone_backup_title, base.tvName),
            backupText = context.getString(R.string.phone_backup_text),
            backupDownload = context.getString(R.string.phone_diagnostics_download),
            restoreTitle = context.getString(R.string.phone_restore_title, base.tvName),
            restoreText = context.getString(R.string.phone_restore_text),
            restoreFile = context.getString(R.string.phone_restore_file),
            restorePassphrase = context.getString(SettingsR.string.field_passphrase),
            restorePassphraseHint = context.getString(R.string.phone_restore_passphrase_hint),
            restoreSend = context.getString(R.string.phone_restore_send, base.tvName),
            restoreNoFile = context.getString(R.string.phone_restore_no_file),
            restoreSentTitle = context.getString(R.string.phone_restore_sent_title, base.tvName),
            restoreSentText = context.getString(R.string.phone_restore_sent_text),
        )
    }

    private companion object {
        /** Plasma's folder on the USB drive, beside Diagnostics. */
        const val FOLDER = "Backups"
        const val KEPT_FOLDER = "backups"
        const val BACKUP = "plasma-backup-"
        const val BEFORE_UPDATE = "plasma-before-update-"
        const val EXTENSION = ".json"
        const val JSON = "application/json"
        const val KEEP_BEFORE_UPDATE = 2
        const val KEEP_ON_TV = 5
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
