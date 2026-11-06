// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.backup

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
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
import ca.gureet.plasma.feature.settings.TvApp
import ca.gureet.plasma.feature.settings.TvFiles
import ca.gureet.plasma.feature.settings.TvFolder
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
import java.util.Locale
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
 * the data layer, saved on a USB drive (in its Download/Plasma and in Plasma's own folder there),
 * in Plasma's folder in the TV's Downloads or Documents or where a file picker says (#141), or
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
     * Backups in Plasma's own storage on the TV: Plasma Update's, and Save on This TV's from before
     * 5 November, told apart by their names. Clearing Plasma's storage or uninstalling it deletes them.
     */
    private val kept = File(context.filesDir, KEPT_FOLDER)

    private val resolver get() = context.contentResolver

    /**
     * What the TV offers besides Plasma's folders: an app that saves a file where it's told, and a
     * file manager app that opens one, other than Android's own file picker and the stand-ins for it
     * ([PLATFORM_PICKERS]); and before Android 10, the permission for Downloads and Documents, where
     * Plasma then finds its backups itself after a reinstall. From Android 10, Android keeps a backup
     * Plasma saved before it was reinstalled, or its storage cleared, from it, save through a file
     * manager app. Asked each time a sheet opens, so one installed meanwhile counts.
     */
    override val tvFiles: TvFiles
        get() {
            val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            return TvFiles(
                folderApp = appFor(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(JSON)),
                fileApp = appFor(Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")),
                permission = if (legacy) Manifest.permission.WRITE_EXTERNAL_STORAGE else null,
                findsOldBackups = legacy,
            )
        }

    private val version: String get() = Updates.versionOf(context)

    /**
     * Save to USB Drive. From Android 10, in Download/Plasma on the drive itself, through Android's
     * media store, where it stays if Plasma is uninstalled or its storage is cleared (#141, 6
     * November): Android empties Plasma's own folder on every drive plugged in then. And in that
     * folder as well, as before, which a reinstalled Plasma, or Plasma on another TV, reads by
     * itself when the drive wasn't plugged in then, where the media store keeps the other from it.
     * Before Android 10, or on a drive the media store doesn't have, only in Plasma's own folder.
     */
    override suspend fun saveToUsb(passphrase: String?): BackupSaved = withContext(Dispatchers.IO) {
        val drive = usb() ?: return@withContext BackupSaved.NoDrive
        val folder = File(drive, FOLDER)
        saving {
            val backup = data.backups.make(version, passphrase)
            val bytes = BackupFile.encode(backup)
            val name = nameOf(backup, prefix = BACKUP)
            val lasting = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaStoreVolume(drive)?.let { volume -> orNull { insert(TvFolder.Downloads, name, bytes, volume) } }
            } else {
                null
            }
            // Under the media store's name, so Restore lists the two as one.
            val own = orNull { (lasting ?: name).also { write(File(folder.apply { mkdirs() }, it), bytes) } }
            when {
                lasting != null -> BackupSaved.Saved(lasting, TvFolder.Downloads.shown, lasts = true)
                own != null -> BackupSaved.Saved(own, shownPath(folder))
                else -> BackupSaved.Failed
            }
        }
    }

    /**
     * Save on This TV (the owner's asks, 3 and 4 November): in Plasma's folder in the TV's
     * Downloads or Documents, where it outlasts Plasma. From Android 10 through Android's media
     * store, which needs no permission; before, as a file, with Android's permission.
     */
    override suspend fun saveOnTv(passphrase: String?, folder: TvFolder): BackupSaved = withContext(Dispatchers.IO) {
        saving {
            val backup = data.backups.make(version, passphrase)
            val name = nameOf(backup, prefix = BACKUP)
            val bytes = BackupFile.encode(backup)
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) insert(folder, name, bytes) else writeShared(folder, name, bytes)
            BackupSaved.OnTv(saved, folder.shown)
        }
    }

    /** Choose a Folder (#141): in the new file the TV's file picker made for it. */
    override suspend fun saveToFile(file: String, passphrase: String?): BackupSaved = withContext(Dispatchers.IO) {
        saving {
            val uri = Uri.parse(file)
            val backup = data.backups.make(version, passphrase)
            resolver.openOutputStream(uri, "w")?.use { it.write(BackupFile.encode(backup)) } ?: throw IOException("Couldn't open $file")
            BackupSaved.Chosen(nameOf(uri) ?: nameOf(backup, prefix = BACKUP))
        }
    }

    override fun newName(): String = BACKUP + STAMP.format(Instant.now().atZone(ZoneId.systemDefault())) + EXTENSION

    /** [save], or why not: Android's permission refused, or the TV's storage or file picker failing. */
    private suspend fun saving(save: suspend () -> BackupSaved): BackupSaved = try {
        save()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (refused: SecurityException) {
        BackupSaved.NotAllowed
    } catch (failed: IOException) {
        BackupSaved.Failed
    } catch (failed: IllegalArgumentException) {
        // The media store or a file picker turning the file down.
        BackupSaved.Failed
    } catch (failed: IllegalStateException) {
        BackupSaved.Failed
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
        val shared = runCatching { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) inMediaStore() else inSharedFolders() }.getOrDefault(emptyList())
        val onTv = listed(kept, BackupPlace.ThisTv).map { found ->
            if (found.name.startsWith(BEFORE_UPDATE)) found.copy(place = BackupPlace.BeforeUpdate) else found
        }
        // Save to USB Drive puts a backup in two folders on the drive: listed once, from Plasma's own.
        (onDrive + shared + onTv)
            .distinctBy { if (it.place == BackupPlace.UsbDrive) "usb:${it.name}" else it.id }
            .sortedByDescending { it.madeAt }
    }

    override suspend fun open(found: FoundBackup): BackupOpened = withContext(Dispatchers.IO) {
        openAt(if (found.id.startsWith(CONTENT)) found.id else Uri.fromFile(File(found.id)).toString())
    }

    /** Choose a File (#141): what the TV's file picker or a file manager app chose. */
    override suspend fun openFile(file: String): BackupOpened = withContext(Dispatchers.IO) { openAt(file) }

    /** The backup at [address], a file's or the media store's, when it's no bigger than a backup is. */
    private fun openAt(address: String): BackupOpened {
        val bytes = try {
            resolver.openInputStream(Uri.parse(address))?.use { input ->
                val read = input.readNBytesOf(PairingServer.MAX_UPLOAD_BYTES + 1)
                read.takeIf { it.size <= PairingServer.MAX_UPLOAD_BYTES }
            }
        } catch (failed: IOException) {
            null
        } catch (refused: SecurityException) {
            null
        } catch (failed: IllegalArgumentException) {
            null
        }
        return bytes?.let { read(it, passphrase = null) } ?: BackupOpened.Unreadable(newer = false)
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
                    is RestoreResult.Restored -> {
                        // Once Plasma has started again, what needs Android's permission asks for it (#141).
                        AfterRestore.mark(context)
                        RestoreOutcome.Restored(result.needLogins)
                    }
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

    /**
     * In Plasma's folder in [folder], through the media store, under [name] or the one it gave
     * instead: on the TV, or on the drive the media store calls [volume].
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insert(folder: TvFolder, name: String, bytes: ByteArray, volume: String = MediaStore.VOLUME_EXTERNAL_PRIMARY): String {
        val collection = when (folder) {
            TvFolder.Downloads -> MediaStore.Downloads.getContentUri(volume)
            TvFolder.Documents -> MediaStore.Files.getContentUri(volume)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, JSON)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder.relativePath)
            // Hidden from other apps till it's whole.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't make $name")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Couldn't write $name")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (failed: Exception) {
            resolver.delete(uri, null, null)
            throw failed
        }
        // The media store names it "… (1)" beside a file of the same name.
        return nameOf(uri) ?: name
    }

    /** In Plasma's folder in [folder], as a file, before Android 10: with Android's permission. */
    @Suppress("DEPRECATION")
    private fun writeShared(folder: TvFolder, name: String, bytes: ByteArray): String {
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) throw SecurityException("No permission")
        val file = File(File(Environment.getExternalStoragePublicDirectory(folder.directory), PLASMA_FOLDER).apply { mkdirs() }, name)
        write(file, bytes)
        return file.name
    }

    /**
     * The backups in Plasma's folders in Downloads and Documents that the media store shows Plasma,
     * on the TV and on a USB drive: those it saved since it was installed, or its storage last
     * cleared.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun inMediaStore(): List<FoundBackup> {
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val columns = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.VOLUME_NAME,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?, ?) AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf(TvFolder.Downloads.relativePath, TvFolder.Documents.relativePath, "$NAME_START%$EXTENSION")
        return resolver.query(files, columns, selection, args, null)?.use { row ->
            buildList {
                while (row.moveToNext()) {
                    val volume = row.getString(4) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                    val place = when {
                        volume != MediaStore.VOLUME_EXTERNAL_PRIMARY -> BackupPlace.UsbDrive
                        row.getString(3) == TvFolder.Documents.relativePath -> BackupPlace.Documents
                        else -> BackupPlace.Downloads
                    }
                    val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri(volume), row.getLong(0))
                    add(FoundBackup(uri.toString(), row.getString(1), place, row.getLong(2) * 1_000))
                }
            }
        }.orEmpty()
    }

    /**
     * What the media store calls the drive [dir] is on ("1234-abcd"), when the media store has it:
     * a USB drive's, never the TV's own storage.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreVolume(dir: File): String? = orNull {
        val volume = context.getSystemService(StorageManager::class.java)?.getStorageVolume(dir) ?: return@orNull null
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) volume.mediaStoreVolumeName else volume.uuid?.lowercase(Locale.ROOT)
        name?.takeIf { it != MediaStore.VOLUME_EXTERNAL_PRIMARY && it in MediaStore.getExternalVolumeNames(context) }
    }

    /** What [work] gives, or null when the drive, Android or the media store turns it down. */
    private inline fun <T> orNull(work: () -> T): T? = try {
        work()
    } catch (failed: IOException) {
        null
    } catch (refused: SecurityException) {
        null
    } catch (failed: IllegalArgumentException) {
        null
    } catch (failed: IllegalStateException) {
        null
    } catch (failed: UnsupportedOperationException) {
        null
    }

    /** The backups in Plasma's folders in Downloads and Documents, before Android 10: with Android's permission. */
    @Suppress("DEPRECATION")
    private fun inSharedFolders(): List<FoundBackup> {
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return TvFolder.entries.flatMap { folder ->
            val place = if (folder == TvFolder.Documents) BackupPlace.Documents else BackupPlace.Downloads
            listed(File(Environment.getExternalStoragePublicDirectory(folder.directory), PLASMA_FOLDER), place)
        }
    }

    /** The name a file picker's or the media store's file goes by. */
    private fun nameOf(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (failed: RuntimeException) {
        null
    }

    /** The app on the TV that does [intent], with its name, unless it's only one of [PLATFORM_PICKERS]. */
    private fun appFor(intent: Intent): TvApp? {
        val packages = context.packageManager
        return packages.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo.packageName !in PLATFORM_PICKERS }
            ?.let { TvApp(it.activityInfo.packageName, it.loadLabel(packages).toString()) }
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
        /** Plasma's own folder on the USB drive, beside Diagnostics. */
        const val FOLDER = "Backups"
        const val KEPT_FOLDER = "backups"
        /** Plasma's folder in the TV's Downloads and Documents. */
        const val PLASMA_FOLDER = "Plasma"
        const val NAME_START = "plasma-"
        const val BACKUP = "plasma-backup-"
        const val BEFORE_UPDATE = "plasma-before-update-"
        const val EXTENSION = ".json"
        const val JSON = "application/json"
        const val CONTENT = "content:"
        const val KEEP_BEFORE_UPDATE = 2
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        /**
         * Android's own file picker, and what many TVs have in its place, which only says that no app
         * can. Neither is made for a remote: on a Chromecast with Google TV, Choose a File froze the
         * TV (the owner on #141, 6 November). Choose a File and Choose a Folder go to another app.
         */
        val PLATFORM_PICKERS = setOf(
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.google.android.tv.frameworkpackagestubs",
            "com.android.tv.frameworkpackagestubs",
        )

        /** Android's folder for [TvFolder]. */
        val TvFolder.directory: String get() = if (this == TvFolder.Documents) Environment.DIRECTORY_DOCUMENTS else Environment.DIRECTORY_DOWNLOADS

        /** Plasma's folder in it, as the media store writes it: "Download/Plasma/". */
        val TvFolder.relativePath: String get() = "$directory/$PLASMA_FOLDER/"

        /** As the TV and a computer show it: "Download/Plasma". */
        val TvFolder.shown: String get() = "$directory/$PLASMA_FOLDER"
    }
}

/** Up to [limit] bytes of what's left: all of it when it's no more. */
private fun java.io.InputStream.readNBytesOf(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
