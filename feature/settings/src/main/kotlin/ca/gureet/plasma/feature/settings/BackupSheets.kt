// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ca.gureet.plasma.core.design.PlasmaColors
import ca.gureet.plasma.core.design.PlasmaText
import ca.gureet.plasma.core.design.component.SheetOption
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.core.design.plasmaStyle
import ca.gureet.plasma.core.design.px
import ca.gureet.plasma.core.model.Formats
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Where a backup Restore from Backup found is (#108). */
enum class BackupPlace {
    /** Plasma's folders on a USB drive, Download/Plasma and its own, where Save to USB Drive puts it. */
    UsbDrive,

    /** Plasma's folder in the TV's Downloads, where Save on This TV can put it (the owner on #141). */
    Downloads,

    /** Plasma's folder in the TV's Documents, as Downloads. */
    Documents,

    /** Plasma's own storage on the TV, where Save on This TV put backups before 5 November. */
    ThisTv,

    /** Kept on the TV by Plasma Update, before it installed an update (brief 5.18). */
    BeforeUpdate,
}

/**
 * Where on the TV Save on This TV puts a backup: Plasma's folder in Downloads or Documents, where
 * it stays when Plasma is uninstalled or its storage is cleared (the owner on #141, 4 November).
 */
enum class TvFolder { Downloads, Documents }

/** An app on the TV that saves or opens a file for Plasma (#141): its [packageName], and the [name] it shows. */
@Immutable
data class TvApp(val packageName: String, val name: String)

/**
 * What this TV offers Back Up Settings and Restore from Backup besides Plasma's folders (#141):
 * [folderApp], an app that saves a new file wherever it's told (Choose a Folder); [fileApp], a
 * file manager app that opens one (Choose a File). Each is an app installed on the TV, never one
 * that came with it: on a Chromecast with Google TV, Choose a File froze the TV (the owner, 6
 * November). Then [permission], Android's permission Plasma asks for first to use
 * Downloads and Documents, on TVs before Android 10; and whether Plasma [findsOldBackups] there
 * itself after it's reinstalled or its storage is cleared, which Android 10 and later keep from it.
 */
@Immutable
data class TvFiles(
    val folderApp: TvApp? = null,
    val fileApp: TvApp? = null,
    val permission: String? = null,
    val findsOldBackups: Boolean = false,
) {
    val chooseFolder: Boolean get() = folderApp != null

    val chooseFile: Boolean get() = fileApp != null
}

/** Choose a File's request, sent to [app] alone, so no app that came with the TV can take it (#141). */
private class GetContentIn(private val app: String?) : ActivityResultContracts.GetContent() {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).setPackage(app)
}

/** Choose a Folder's, the same way. */
private class CreateDocumentIn(private val app: String?) : ActivityResultContracts.CreateDocument(BACKUP_TYPE) {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).setPackage(app)
}

/** A backup Restore from Backup lists: its [id] for the app, and when it was made, Unix ms. */
@Immutable
data class FoundBackup(val id: String, val name: String, val place: BackupPlace, val madeAt: Long)

/** What a backup holds, shown before it replaces anything (#108). */
@Immutable
data class BackupSummary(
    /** When it was made, Unix ms. */
    val madeAt: Long,
    /** The Plasma that made it. */
    val plasma: String,
    val beforeUpdate: Boolean,
    /** Its sources, the primary first. */
    val sources: List<String>,
    val profiles: Int,
    val favorites: Int,
    /** Reminders, recording rules and recordings to come. */
    val plans: Int,
    /** Its logins are locked with a passphrase. */
    val withLogins: Boolean,
    /** The passphrase a phone sent with it: tried first, so the TV only asks when it's wrong. */
    val passphrase: String? = null,
)

/** A backup opened to restore: what's in it, or why it can't be read. */
sealed interface BackupOpened {
    data class Ready(val summary: BackupSummary) : BackupOpened

    /** Not a backup of Plasma's, or one from a [newer] Plasma than this. */
    data class Unreadable(val newer: Boolean) : BackupOpened
}

/** Where Back Up Settings puts a backup. */
enum class BackupTo { UsbDrive, ThisTv, Phone }

/** How Back Up Settings › Save to USB Drive or Save on This TV went. */
sealed interface BackupSaved {
    /**
     * Saved as [name] in [folder] on the USB drive, as a computer shows it: in Download/Plasma there
     * when it [lasts] if Plasma is uninstalled or its storage is cleared (#141); otherwise only in
     * Plasma's own folder, which Android empties then while the drive is plugged in.
     */
    data class Saved(val name: String, val folder: String, val lasts: Boolean = false) : BackupSaved

    /** Saved as [name] in [folder] on the TV: "Download/Plasma". */
    data class OnTv(val name: String, val folder: String) : BackupSaved

    /** Saved as [name] where the file picker was told (Choose a Folder). */
    data class Chosen(val name: String) : BackupSaved

    data object NoDrive : BackupSaved

    /** Android didn't give Plasma its permission to save there. */
    data object NotAllowed : BackupSaved

    data object Failed : BackupSaved
}

/** Restore from Your Phone: the code for the phone, till a backup comes from it. */
sealed interface PhoneRestore {
    data class Waiting(val pairing: PhonePairing) : PhoneRestore

    data class Received(val summary: BackupSummary) : PhoneRestore
}

/** How a restore went. */
sealed interface RestoreOutcome {
    /** The passphrase isn't the backup's; nothing changed. */
    data object WrongPassphrase : RestoreOutcome

    /** Everything's in place; [needLogins] names the sources that came back without their logins. */
    data class Restored(val needLogins: List<String>) : RestoreOutcome

    /** A recording is under way: the restore waits till it's done; nothing changed. */
    data object Busy : RestoreOutcome

    data object Failed : RestoreOutcome
}

/**
 * What Back Up Settings and Restore from Backup ask of the app (brief 5.17, #108). A backup to
 * restore is opened first, from a file found or from a phone; [restore] puts the one opened last
 * in place of what's here, and [restart] starts Plasma again to load it.
 */
interface BackupActions {
    /** A backup on the USB drive; with the logins locked with [passphrase], or without them when it's null. */
    suspend fun saveToUsb(passphrase: String?): BackupSaved

    /** A backup in Plasma's folder in [folder] on the TV, as [saveToUsb] makes one. */
    suspend fun saveOnTv(passphrase: String?, folder: TvFolder): BackupSaved

    /** A backup in [file], the new file a file picker made for it, by its address (Choose a Folder). */
    suspend fun saveToFile(file: String, passphrase: String?): BackupSaved

    /** The name Choose a Folder offers the file picker: "plasma-backup-20261105-2041.json". */
    fun newName(): String

    /** What this TV offers besides Plasma's folders, asked as each sheet opens. */
    val tvFiles: TvFiles get() = TvFiles()

    /** The page a phone downloads a backup from, while this is collected. */
    fun toPhone(passphrase: String?): Flow<PhonePairing>

    /** The backups on a USB drive, on the TV, and those Plasma Update kept, the newest first. */
    suspend fun found(): List<FoundBackup>

    suspend fun open(found: FoundBackup): BackupOpened

    /** The backup in [file], which a file picker or file manager chose, by its address (Choose a File). */
    suspend fun openFile(file: String): BackupOpened

    /** The page a phone sends a backup from, while this is collected: what it sent is opened. */
    fun fromPhone(): Flow<PhoneRestore>

    suspend fun restore(passphrase: String?): RestoreOutcome

    fun restart()

    /** Previews, screenshots and tests: a backup on a drive, one in Downloads, and one Plasma Update kept. */
    object Sample : BackupActions {
        val summary = BackupSummary(
            madeAt = Instant.parse("2026-11-03T20:41:00Z").toEpochMilli(),
            plasma = "1.0 (178)",
            beforeUpdate = false,
            sources = listOf("Home Server", "Arena Playlist"),
            profiles = 3,
            favorites = 24,
            plans = 5,
            withLogins = true,
        )

        override suspend fun saveToUsb(passphrase: String?): BackupSaved =
            BackupSaved.Saved("plasma-backup-20261103-2041.json", "Download/Plasma", lasts = true)

        override suspend fun saveOnTv(passphrase: String?, folder: TvFolder): BackupSaved =
            BackupSaved.OnTv("plasma-backup-20261103-2041.json", if (folder == TvFolder.Downloads) "Download/Plasma" else "Documents/Plasma")

        override suspend fun saveToFile(file: String, passphrase: String?): BackupSaved = BackupSaved.Chosen("plasma-backup-20261103-2041.json")

        override fun newName(): String = "plasma-backup-20261103-2041.json"

        override fun toPhone(passphrase: String?): Flow<PhonePairing> = flowOf(PhonePairing.Sample)

        override suspend fun found(): List<FoundBackup> = listOf(
            FoundBackup("usb", "plasma-backup-20261103-2041.json", BackupPlace.UsbDrive, summary.madeAt),
            FoundBackup("tv", "plasma-backup-20261102-1915.json", BackupPlace.Downloads, summary.madeAt - 3_600_000L),
            FoundBackup("update", "plasma-before-update-177.json", BackupPlace.BeforeUpdate, summary.madeAt - 86_400_000L),
        )

        override suspend fun open(found: FoundBackup): BackupOpened = BackupOpened.Ready(summary)

        override suspend fun openFile(file: String): BackupOpened = BackupOpened.Ready(summary)

        override fun fromPhone(): Flow<PhoneRestore> = flowOf(PhoneRestore.Waiting(PhonePairing.Sample))

        override suspend fun restore(passphrase: String?): RestoreOutcome = RestoreOutcome.Restored(emptyList())

        override fun restart() = Unit
    }
}

/** About › Back Up Settings (brief 5.17): to a USB drive, on the TV itself (the owner's ask, 3 November), or to a phone. */
@Composable
internal fun backupMenu(actions: BackupActions, back: String): SettingAction.Menu = SettingAction.Menu(
    title = stringResource(R.string.backup),
    subtitle = stringResource(R.string.backup_menu_text),
    options = listOf(
        MenuOption(stringResource(R.string.backup_to_usb), sheet = SettingAction.Sheet { close -> BackupSheet(actions, BackupTo.UsbDrive, back, close) }),
        MenuOption(stringResource(R.string.backup_on_tv), sheet = SettingAction.Sheet { close -> BackupSheet(actions, BackupTo.ThisTv, back, close) }),
        MenuOption(stringResource(R.string.backup_to_phone), sheet = SettingAction.Sheet { close -> BackupSheet(actions, BackupTo.Phone, back, close) }),
    ),
)

/** Back Up Settings' steps. */
internal sealed interface BackupStage {
    /** With the logins, or without. */
    data object Logins : BackupStage

    /** Choosing the passphrase, typed twice. */
    data object Passphrase : BackupStage

    /** The passphrase wasn't the same twice, or was [tooShort]. */
    data class Mismatch(val tooShort: Boolean) : BackupStage

    /** Where on the TV: Plasma's folder in Downloads or Documents, or one the file picker shows. */
    data class Where(val passphrase: String?) : BackupStage

    data object Saving : BackupStage

    /**
     * Saved as [name] in [folder]: on the USB drive, or on the TV when [onTv]; where the file picker
     * was told when [folder] is null. On the drive, it [lasts] past Plasma as on the TV, or not.
     */
    data class Saved(val name: String, val folder: String?, val onTv: Boolean = false, val lasts: Boolean = false) : BackupStage

    /**
     * No USB drive, or the backup couldn't be written ([failed]), or Android didn't allow it
     * ([notAllowed]): Try Again with the same [passphrase].
     */
    data class NotSaved(val passphrase: String?, val failed: Boolean, val notAllowed: Boolean = false) : BackupStage

    /** The code for a phone, which downloads the backup. */
    data class Phone(val passphrase: String?) : BackupStage
}

/** Back Up Settings, step by step (#108): the logins or not, the passphrase, then the drive, the TV or the phone. */
internal class BackupModel(private val actions: BackupActions, private val scope: CoroutineScope, private val to: BackupTo) {
    var stage: BackupStage by mutableStateOf(BackupStage.Logins)
        private set

    private var job: Job? = null

    private fun step(next: BackupStage, work: (suspend CoroutineScope.() -> Unit)? = null) {
        job?.cancel()
        job = null
        stage = next
        if (work != null) job = scope.launch(block = work)
    }

    fun withoutLogins() = make(null)

    fun withLogins() = step(BackupStage.Passphrase)

    /** The passphrase as typed, twice. */
    fun passphrase(first: String, again: String) = when {
        first.length < MIN_PASSPHRASE -> step(BackupStage.Mismatch(tooShort = true))
        first != again -> step(BackupStage.Mismatch(tooShort = false))
        else -> make(first)
    }

    private fun make(passphrase: String?) = when (to) {
        BackupTo.Phone -> step(BackupStage.Phone(passphrase))
        // Where on the TV, asked each time (the owner on #141, 4 November).
        BackupTo.ThisTv -> step(BackupStage.Where(passphrase))
        BackupTo.UsbDrive -> save(passphrase) { actions.saveToUsb(passphrase) }
    }

    /** Save on This TV, in Plasma's folder in [folder]. */
    fun saveIn(where: BackupStage.Where, folder: TvFolder) = save(where.passphrase) { actions.saveOnTv(where.passphrase, folder) }

    /** Choose a Folder: in [file], the new file the file picker made. */
    fun saveTo(where: BackupStage.Where, file: String) = save(where.passphrase) { actions.saveToFile(file, where.passphrase) }

    /** Android didn't give Plasma its permission to save in Downloads or Documents. */
    fun notAllowed(where: BackupStage.Where) = step(BackupStage.NotSaved(where.passphrase, failed = true, notAllowed = true))

    private fun save(passphrase: String?, saving: suspend () -> BackupSaved) = step(BackupStage.Saving) {
        stage = when (val saved = saving()) {
            is BackupSaved.Saved -> BackupStage.Saved(saved.name, saved.folder, lasts = saved.lasts)
            is BackupSaved.OnTv -> BackupStage.Saved(saved.name, saved.folder, onTv = true)
            is BackupSaved.Chosen -> BackupStage.Saved(saved.name, folder = null, onTv = true)
            BackupSaved.NoDrive -> BackupStage.NotSaved(passphrase, failed = false)
            BackupSaved.NotAllowed -> BackupStage.NotSaved(passphrase, failed = true, notAllowed = true)
            BackupSaved.Failed -> BackupStage.NotSaved(passphrase, failed = true)
        }
    }

    /** The same again; on the TV, from where to save it. */
    fun tryAgain(stage: BackupStage.NotSaved) = make(stage.passphrase)

    fun back() = step(BackupStage.Logins)

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val MIN_PASSPHRASE = 6
    }
}

/**
 * Back Up Settings' sheet (#108): with the logins or not, the passphrase typed on Settings'
 * keyboard page, then the backup saved [to] the USB drive or the TV, where it's asked where (#141),
 * or the code for a phone to download it.
 */
@Composable
internal fun BackupSheet(actions: BackupActions, to: BackupTo, back: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(actions) { BackupModel(actions, scope, to) }
    val close = {
        model.stop()
        onClose()
    }
    val title = stringResource(if (to == BackupTo.Phone) R.string.backup_phone_title else R.string.backup)
    val icon = if (to == BackupTo.ThisTv) PlasmaIcons.Screen else PlasmaIcons.Drive
    val done = stringResource(R.string.done)
    when (val stage = model.stage) {
        BackupStage.Logins -> key(stage) {
            SignInSheet(
                title = title,
                text = stringResource(R.string.backup_logins_text),
                onClose = close,
                icon = icon,
                options = listOf(
                    SheetOption(stringResource(R.string.backup_without_logins), onClick = model::withoutLogins),
                    SheetOption(stringResource(R.string.backup_with_logins), onClick = model::withLogins),
                ),
            )
        }
        BackupStage.Passphrase -> TypedSetting(
            back = back,
            entry = SettingAction.Type(
                title = stringResource(R.string.backup_passphrase_title),
                fields = listOf(
                    TypedField(stringResource(R.string.field_passphrase), "", secret = true),
                    TypedField(stringResource(R.string.field_passphrase_again), "", secret = true),
                ),
                text = stringResource(R.string.backup_passphrase_text),
                save = stringResource(R.string.backup_save),
                note = stringResource(R.string.backup_passphrase_note),
            ) { texts -> model.passphrase(texts[0], texts[1]) },
            onDone = { if (model.stage is BackupStage.Passphrase) model.back() },
        )
        is BackupStage.Mismatch -> key(stage) {
            SignInSheet(
                title = stringResource(R.string.backup_passphrase_title),
                text = stringResource(if (stage.tooShort) R.string.backup_too_short else R.string.backup_mismatch),
                onClose = close,
                icon = PlasmaIcons.Lock,
                onBack = model::back,
                options = listOf(SheetOption(stringResource(R.string.try_again), onClick = model::withLogins)),
            )
        }
        is BackupStage.Where -> key(stage) { WhereOnTv(actions, model, stage, title, close) }
        BackupStage.Saving -> key(stage) {
            SignInSheet(
                title = title,
                text = stringResource(R.string.backup_saving),
                onClose = close,
                icon = icon,
                options = listOf(SheetOption(stringResource(R.string.cancel), onClick = close)),
            )
        }
        is BackupStage.Saved -> key(stage) {
            val files = remember { actions.tvFiles }
            SignInSheet(
                title = title,
                text = when {
                    !stage.onTv -> stringResource(if (stage.lasts) R.string.backup_saved_usb else R.string.backup_saved, stage.name, stage.folder.orEmpty())
                    stage.folder == null -> stringResource(R.string.backup_saved_chosen, stage.name)
                    files.findsOldBackups -> stringResource(R.string.backup_saved_tv, stage.name, stage.folder)
                    files.chooseFile -> stringResource(R.string.backup_saved_tv_choose, stage.name, stage.folder)
                    else -> stringResource(R.string.backup_saved_tv_manager, stage.name, stage.folder)
                },
                onClose = close,
                icon = PlasmaIcons.Check,
                options = listOf(SheetOption(done, onClick = close)),
            )
        }
        is BackupStage.NotSaved -> key(stage) {
            SignInSheet(
                title = title,
                text = stringResource(
                    when {
                        stage.notAllowed -> R.string.backup_not_allowed
                        to == BackupTo.ThisTv -> R.string.backup_failed_tv
                        stage.failed -> R.string.backup_failed
                        else -> R.string.backup_no_drive
                    },
                ),
                onClose = close,
                icon = PlasmaIcons.Caution,
                options = listOf(
                    SheetOption(stringResource(R.string.try_again), onClick = { model.tryAgain(stage) }),
                    SheetOption(stringResource(R.string.cancel), onClick = close),
                ),
            )
        }
        is BackupStage.Phone -> key(stage) {
            val pairing by remember(stage) { actions.toPhone(stage.passphrase) }.collectAsState(PhonePairing.Starting)
            val offline = pairing == PhonePairing.Unavailable
            SignInSheet(
                title = title,
                text = stringResource(if (offline) R.string.diagnostics_phone_offline else R.string.backup_phone_text),
                onClose = close,
                icon = PlasmaIcons.Phone,
                options = listOf(SheetOption(done, onClick = close)),
                body = if (offline) null else { { PhoneCode(pairing) } },
            )
        }
    }
}

/**
 * Save on This TV's question (the owner on #141, 4 November): Plasma's folder in Downloads or
 * Documents, which outlast Plasma, or Choose a Folder where the TV has a file picker that saves.
 * On TVs before Android 10, Android's permission comes first.
 */
@Composable
private fun WhereOnTv(actions: BackupActions, model: BackupModel, where: BackupStage.Where, title: String, close: () -> Unit) {
    val context = LocalContext.current
    val files = remember { actions.tvFiles }
    var asked by remember { mutableStateOf<TvFolder?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val folder = asked ?: return@rememberLauncherForActivityResult
        asked = null
        if (granted) model.saveIn(where, folder) else model.notAllowed(where)
    }
    val pick = rememberLauncherForActivityResult(remember(files) { CreateDocumentIn(files.folderApp?.packageName) }) { file ->
        if (file != null) model.saveTo(where, file.toString())
    }
    val saveIn = { folder: TvFolder ->
        val permission = files.permission
        if (permission == null || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            model.saveIn(where, folder)
        } else {
            asked = folder
            ask.launch(permission)
        }
    }
    SignInSheet(
        title = title,
        text = stringResource(R.string.backup_where_text),
        onClose = close,
        icon = PlasmaIcons.Screen,
        onBack = model::back,
        options = listOfNotNull(
            SheetOption(stringResource(R.string.backup_downloads), onClick = { saveIn(TvFolder.Downloads) }, value = stringResource(R.string.backup_downloads_folder)),
            SheetOption(stringResource(R.string.backup_documents), onClick = { saveIn(TvFolder.Documents) }, value = stringResource(R.string.backup_documents_folder)),
            files.folderApp?.let { app ->
                SheetOption(stringResource(R.string.backup_choose_folder), onClick = { pick.launch(actions.newName()) }, value = app.name, icon = PlasmaIcons.Folder)
            },
        ),
    )
}

/** Restore from Backup's steps. */
internal sealed interface RestoreStage {
    data object Looking : RestoreStage

    data class Found(val backups: List<FoundBackup>) : RestoreStage

    /** The code for a phone, which sends a backup. */
    data object Phone : RestoreStage

    data object Opening : RestoreStage

    data class Unreadable(val newer: Boolean) : RestoreStage

    /** What the backup holds, and what it replaces: Restore, or Cancel. */
    data class Summary(val summary: BackupSummary) : RestoreStage

    data class Passphrase(val summary: BackupSummary) : RestoreStage

    data object Restoring : RestoreStage

    data class WrongPassphrase(val summary: BackupSummary) : RestoreStage

    /** A recording is under way. */
    data object Busy : RestoreStage

    data object Failed : RestoreStage

    data class Done(val needLogins: List<String>) : RestoreStage
}

/**
 * Restore from Backup, step by step (#108): a backup found, or sent from a phone; what it holds;
 * the passphrase when it has logins; then Plasma starts again.
 */
internal class RestoreModel(private val actions: BackupActions, private val scope: CoroutineScope) {
    var stage: RestoreStage by mutableStateOf(RestoreStage.Looking)
        private set

    private var job: Job? = null

    private fun step(next: RestoreStage, work: (suspend CoroutineScope.() -> Unit)? = null) {
        job?.cancel()
        job = null
        stage = next
        if (work != null) job = scope.launch(block = work)
    }

    fun look() = step(RestoreStage.Looking) { stage = RestoreStage.Found(actions.found()) }

    fun choose(found: FoundBackup) = step(RestoreStage.Opening) { stage = shown(actions.open(found)) }

    /** Choose a File: [file], which a file picker or file manager chose (#141). */
    fun chooseFile(file: String) = step(RestoreStage.Opening) { stage = shown(actions.openFile(file)) }

    private fun shown(opened: BackupOpened): RestoreStage = when (opened) {
        is BackupOpened.Ready -> RestoreStage.Summary(opened.summary)
        is BackupOpened.Unreadable -> RestoreStage.Unreadable(opened.newer)
    }

    fun fromPhone() = step(RestoreStage.Phone)

    /** A phone sent a backup, which the app has opened. */
    fun received(summary: BackupSummary) = step(RestoreStage.Summary(summary))

    /** Restore with Logins: with the passphrase the phone sent, if it did; otherwise it's asked for. */
    fun withLogins(summary: BackupSummary) {
        val sent = summary.passphrase
        if (sent != null) restore(summary, sent) else step(RestoreStage.Passphrase(summary))
    }

    fun withoutLogins() = restore(null, null)

    fun passphrase(summary: BackupSummary, typed: String) = restore(summary, typed)

    private fun restore(summary: BackupSummary?, passphrase: String?) = step(RestoreStage.Restoring) {
        stage = when (val outcome = actions.restore(passphrase)) {
            // Asked again on the TV, even where the phone's was the one that didn't work.
            RestoreOutcome.WrongPassphrase -> RestoreStage.WrongPassphrase(summary!!.copy(passphrase = null))
            is RestoreOutcome.Restored -> RestoreStage.Done(outcome.needLogins)
            RestoreOutcome.Busy -> RestoreStage.Busy
            RestoreOutcome.Failed -> RestoreStage.Failed
        }
    }

    fun tryAgain(stage: RestoreStage.WrongPassphrase) = step(RestoreStage.Passphrase(stage.summary))

    fun backTo(summary: BackupSummary) = step(RestoreStage.Summary(summary))

    fun restart() = actions.restart()

    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Restore from Backup's sheet (#108): the backups on a USB drive, on the TV and those Plasma
 * Update kept, one a file picker or file manager chooses (#141), or one sent from a phone; what it
 * holds and replaces; the passphrase typed on Settings' keyboard page when it has logins; then
 * Start Again. Back goes back a step, and from the list closes it.
 */
@Composable
internal fun RestoreSheet(actions: BackupActions, back: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(actions) { RestoreModel(actions, scope) }
    val context = LocalContext.current
    // On TVs before Android 10, Downloads and Documents need Android's permission first; without
    // it, the rest are found.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.look() }
    LaunchedEffect(model) {
        val permission = actions.tvFiles.permission
        if (permission != null && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) ask.launch(permission) else model.look()
    }
    val close = {
        model.stop()
        onClose()
    }
    val title = stringResource(R.string.restore)
    val cancel = stringResource(R.string.cancel)
    when (val stage = model.stage) {
        RestoreStage.Looking -> key(stage) {
            SignInSheet(title, stringResource(R.string.restore_looking), close, listOf(SheetOption(cancel, onClick = close)), PlasmaIcons.Drive)
        }
        is RestoreStage.Found -> key(stage) {
            val zone = ZoneId.systemDefault()
            // Asked again each time the list shows, so a file manager app installed meanwhile counts.
            val files = remember { actions.tvFiles }
            val chooseFile = files.chooseFile
            val pick = rememberLauncherForActivityResult(remember(files) { GetContentIn(files.fileApp?.packageName) }) { file ->
                if (file != null) model.chooseFile(file.toString())
            }
            SignInSheet(
                title = title,
                text = stringResource(
                    when {
                        stage.backups.isNotEmpty() -> R.string.restore_choose
                        chooseFile -> R.string.restore_none_choose
                        else -> R.string.restore_none
                    },
                ),
                onClose = close,
                icon = PlasmaIcons.Drive,
                options = stage.backups.map { found ->
                    SheetOption(
                        madeText(found.madeAt, zone),
                        onClick = { model.choose(found) },
                        value = stringResource(
                            when (found.place) {
                                BackupPlace.UsbDrive -> R.string.restore_place_usb
                                BackupPlace.Downloads -> R.string.backup_downloads
                                BackupPlace.Documents -> R.string.backup_documents
                                BackupPlace.ThisTv -> R.string.this_tv
                                BackupPlace.BeforeUpdate -> R.string.restore_place_update
                            },
                        ),
                    )
                } + listOfNotNull(
                    SheetOption.Divider.takeIf { stage.backups.isNotEmpty() },
                    // One saved before Plasma was reinstalled, which Android keeps from it (#141), in the
                    // file manager app it names.
                    files.fileApp?.let { app ->
                        SheetOption(stringResource(R.string.restore_choose_file), onClick = { pick.launch("*/*") }, value = app.name, icon = PlasmaIcons.Folder)
                    },
                    SheetOption(stringResource(R.string.restore_from_phone), onClick = model::fromPhone, icon = PlasmaIcons.Phone),
                ),
            )
        }
        RestoreStage.Phone -> key(stage) {
            val restore by remember { actions.fromPhone() }.collectAsState(PhoneRestore.Waiting(PhonePairing.Starting))
            LaunchedEffect(restore) { (restore as? PhoneRestore.Received)?.let { model.received(it.summary) } }
            val pairing = (restore as? PhoneRestore.Waiting)?.pairing ?: PhonePairing.Starting
            val offline = pairing == PhonePairing.Unavailable
            SignInSheet(
                title = title,
                text = stringResource(if (offline) R.string.diagnostics_phone_offline else R.string.restore_phone_text),
                onClose = close,
                icon = PlasmaIcons.Phone,
                onBack = model::look,
                options = listOf(SheetOption(stringResource(R.string.key_back), onClick = model::look)),
                body = if (offline) null else { { PhoneCode(pairing) } },
            )
        }
        RestoreStage.Opening -> key(stage) {
            SignInSheet(title, stringResource(R.string.restore_opening), close, listOf(SheetOption(cancel, onClick = model::look)), PlasmaIcons.Drive, onBack = model::look)
        }
        is RestoreStage.Unreadable -> key(stage) {
            SignInSheet(
                title = title,
                text = stringResource(if (stage.newer) R.string.restore_newer else R.string.restore_not_a_backup),
                onClose = close,
                icon = PlasmaIcons.Caution,
                onBack = model::look,
                options = listOf(SheetOption(stringResource(R.string.key_back), onClick = model::look)),
            )
        }
        is RestoreStage.Summary -> key(stage) {
            val summary = stage.summary
            SignInSheet(
                title = stringResource(R.string.restore_summary_title),
                text = stringResource(R.string.restore_summary_text, madeText(summary.madeAt, ZoneId.systemDefault()), summary.plasma),
                onClose = close,
                icon = PlasmaIcons.Drive,
                onBack = model::look,
                options = if (summary.withLogins) {
                    listOf(
                        SheetOption(stringResource(R.string.restore_with_logins), onClick = { model.withLogins(summary) }),
                        SheetOption(stringResource(R.string.restore_without_logins), onClick = model::withoutLogins),
                        SheetOption(cancel, onClick = model::look),
                    )
                } else {
                    listOf(
                        SheetOption(stringResource(R.string.restore_now), onClick = model::withoutLogins),
                        SheetOption(cancel, onClick = model::look),
                    )
                },
                body = { SummaryLines(summary) },
            )
        }
        is RestoreStage.Passphrase -> TypedSetting(
            back = back,
            entry = SettingAction.Type(
                title = stringResource(R.string.restore_passphrase_title),
                fields = listOf(TypedField(stringResource(R.string.field_passphrase), "", secret = true)),
                text = stringResource(R.string.restore_passphrase_text),
                save = stringResource(R.string.restore_now),
            ) { texts -> model.passphrase(stage.summary, texts[0]) },
            onDone = { if (model.stage is RestoreStage.Passphrase) model.backTo(stage.summary) },
        )
        RestoreStage.Restoring -> key(stage) {
            // Nothing stops a restore half done: Back waits with it.
            SignInSheet(title, stringResource(R.string.restore_restoring_text), onClose = {}, listOf(SheetOption(stringResource(R.string.restore_restoring), onClick = {})), PlasmaIcons.Drive, onBack = {})
        }
        is RestoreStage.WrongPassphrase -> key(stage) {
            SignInSheet(
                title = stringResource(R.string.restore_passphrase_title),
                text = stringResource(R.string.restore_wrong),
                onClose = close,
                icon = PlasmaIcons.Lock,
                onBack = { model.backTo(stage.summary) },
                options = listOf(
                    SheetOption(stringResource(R.string.try_again), onClick = { model.tryAgain(stage) }),
                    SheetOption(cancel, onClick = close),
                ),
            )
        }
        RestoreStage.Failed, RestoreStage.Busy -> key(stage) {
            val text = stringResource(if (stage == RestoreStage.Busy) R.string.restore_busy else R.string.restore_backup_failed)
            SignInSheet(title, text, close, listOf(SheetOption(stringResource(R.string.done), onClick = close)), PlasmaIcons.Caution)
        }
        is RestoreStage.Done -> key(stage) {
            val text = if (stage.needLogins.isEmpty()) {
                stringResource(R.string.restore_backup_done)
            } else {
                stringResource(R.string.restore_need_logins, stage.needLogins.joinToString(", "))
            }
            // Plasma starts again on its own way out too.
            SignInSheet(
                title = title,
                text = text,
                onClose = model::restart,
                icon = PlasmaIcons.Check,
                onBack = model::restart,
                options = listOf(SheetOption(stringResource(R.string.restore_start_again), onClick = model::restart)),
            )
        }
    }
}

/** What a backup holds, a line each, under the summary's text. */
@Composable
private fun SummaryLines(summary: BackupSummary) {
    val lines = listOfNotNull(
        summary.sources.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.restore_sources, it.joinToString(", ")) },
        listOf(
            pluralStringResource(R.plurals.restore_profiles, summary.profiles, summary.profiles),
            pluralStringResource(R.plurals.restore_favorites, summary.favorites, summary.favorites),
            pluralStringResource(R.plurals.restore_plans, summary.plans, summary.plans),
        ).joinToString(" · "),
        stringResource(if (summary.withLogins) R.string.restore_has_logins else R.string.restore_no_logins),
    )
    Column(Modifier.padding(top = 22.px)) {
        for (line in lines) {
            PlasmaText(line, plasmaStyle(21f, 500, lineHeight = 1.5f), color = PlasmaColors.Text, maxLines = 2)
        }
    }
}

/** A backup file's type, for the file picker. */
private const val BACKUP_TYPE = "application/json"

/** "Tue 3 Nov, 20:41", as the TV's clock shows times. */
private fun madeText(madeAt: Long, zone: ZoneId): String {
    val made = Instant.ofEpochMilli(madeAt).atZone(zone)
    return "${Formats.date(made)}, ${Formats.time(made)}"
}
