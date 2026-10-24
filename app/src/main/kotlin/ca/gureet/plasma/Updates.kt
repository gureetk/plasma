// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import ca.gureet.plasma.update.InstallResult
import ca.gureet.plasma.update.Signers
import ca.gureet.plasma.update.Update
import ca.gureet.plasma.update.UpdateCheck
import ca.gureet.plasma.update.UpdateClient
import ca.gureet.plasma.update.UpdateException
import ca.gureet.plasma.update.UpdateInstaller
import ca.gureet.plasma.update.UpdateProblem
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where Plasma's updater is (brief 5.18). */
sealed interface UpdateState {
    /** This build doesn't look for updates. */
    data object Off : UpdateState

    /** Nothing newer, as of [checkedAt] (null: not checked yet); [problem] says why the last check didn't work. */
    data class Current(val checkedAt: Instant?, val problem: UpdateProblem? = null) : UpdateState

    data class Checking(val checkedAt: Instant?) : UpdateState

    /** [update] is ready to download and install; [problem] says why the last try didn't. */
    data class Ready(val update: Update, val checkedAt: Instant?, val problem: UpdateProblem? = null) : UpdateState

    /** [done] of the APK has come, 0 to 1, or −1 when the server doesn't say. */
    data class Downloading(val update: Update, val done: Float) : UpdateState

    /** With Android's installer, which may be asking the user. */
    data class Installing(val update: Update) : UpdateState
}

/**
 * Plasma's own updates (brief 5.18). The update file is read once a day while Check for Updates is
 * on, and when asked. An update is downloaded when the user says Install Now, checked against its
 * SHA-256 and Plasma's signing certificate, and handed to Android's installer, never while
 * something plays. Settings shows a quiet dot while one waits.
 */
class Updates(private val context: Context, private val graph: AppGraph) {
    /** Where this build reads the update file; a local debug build doesn't. */
    val address: String? = BuildConfig.UPDATES.takeIf { it.isNotBlank() }

    /** Plasma's version, "0.1.0-dev (512)", as updates name theirs. */
    val version: String = versionOf(context)

    private val client by lazy { UpdateClient(graph.data.http) }
    private val installer = UpdateInstaller(context)
    private val memory = context.getSharedPreferences(MEMORY, Context.MODE_PRIVATE)
    private val folder = File(context.cacheDir, FOLDER)
    private val installedCode: Long = installedCode(context)

    private val _state = MutableStateFlow(if (address == null) UpdateState.Off else remembered())

    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The version Plasma updated itself to since it last ran, for a notice; once. */
    private var updated: String? = null

    private var work: Job? = null

    /** Starts the daily check, and hears Android's installer. */
    fun start() {
        address ?: return
        memory.getString(INSTALLING, null)?.let { label ->
            // The version Plasma was installing is the one running now: it worked.
            if (memory.getLong(INSTALLING_CODE, Long.MAX_VALUE) <= installedCode) updated = label
            memory.edit { remove(INSTALLING).remove(INSTALLING_CODE) }
        }
        graph.scope.launch(Dispatchers.IO) {
            runCatching { installer.tidy() }
            // Updates downloaded for this version or an older one aren't needed any more.
            folder.listFiles()?.filter { file -> codeOf(file)?.let { it <= installedCode } ?: true }?.forEach { it.delete() }
        }
        graph.scope.launch {
            UpdateInstaller.results.collect(::heard)
        }
        graph.scope.launch {
            while (true) {
                if (due()) checkNow()
                delay(LOOK_EVERY_MS)
            }
        }
    }

    /** The version Plasma just updated itself to, the first time it's asked. */
    fun takeUpdated(): String? = updated.also { updated = null }

    /**
     * Android has put an update in: when Plasma's own updater asked for it, Plasma opens again, as
     * the TV's Home would (5.18).
     */
    fun reopenIfUpdated() {
        if (updated == null) return
        runCatching { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Whether the daily check is due: Check for Updates is on, and the last was a day ago. */
    private fun due(): Boolean {
        if (!graph.choices.checkUpdatesDaily) return false
        if (_state.value !is UpdateState.Current && _state.value !is UpdateState.Ready) return false
        val last = memory.getLong(CHECKED_AT, 0)
        return last == 0L || Duration.between(Instant.ofEpochMilli(last), Instant.now()) >= DAILY
    }

    /** Reads the update file now (About › Plasma Update, or the daily check). */
    fun checkNow() {
        val address = address ?: return
        val before = _state.value
        if (before is UpdateState.Checking || before is UpdateState.Downloading || before is UpdateState.Installing) return
        val checkedAt = checkedAt()
        _state.value = UpdateState.Checking(checkedAt)
        work = graph.scope.launch {
            val now = Instant.now()
            _state.value = try {
                when (val check = client.check(address, installedCode, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList())) {
                    is UpdateCheck.Available -> {
                        remember(check.update)
                        UpdateState.Ready(check.update, now)
                    }
                    // Nothing this TV can take is up to date for it.
                    UpdateCheck.UpToDate, is UpdateCheck.NotForThisTv -> {
                        remember(null)
                        UpdateState.Current(now)
                    }
                }
            } catch (stopped: CancellationException) {
                _state.value = before
                throw stopped
            } catch (failed: Exception) {
                val problem = (failed as? UpdateException)?.problem ?: UpdateProblem.CantReach
                // An update found before is still there to install.
                (before as? UpdateState.Ready)?.copy(checkedAt = now, problem = null) ?: UpdateState.Current(now, problem)
            }
            memory.edit { putLong(CHECKED_AT, now.toEpochMilli()) }
        }
    }

    /**
     * Install Now: downloads the update unless it's here already, checks it, and hands it to
     * Android's installer, which asks the user to confirm. If something plays by then, it waits
     * for the next Install Now, downloaded. While Android has it, this asks again, as when its
     * question went away without an answer.
     */
    fun install() {
        val ready = when (val now = _state.value) {
            is UpdateState.Ready -> now
            is UpdateState.Installing -> UpdateState.Ready(now.update, checkedAt())
            else -> return
        }
        val update = ready.update
        if (!installer.allowed()) {
            _state.value = ready.copy(problem = UpdateProblem.NotAllowed)
            return
        }
        work = graph.scope.launch {
            try {
                val apk = File(folder, "$PREFIX${update.versionCode}$SUFFIX")
                if (!isWhole(apk, update)) {
                    _state.value = UpdateState.Downloading(update, 0f)
                    client.download(update, apk) { done -> _state.value = UpdateState.Downloading(update, done) }
                }
                if (!fits(apk, update)) {
                    apk.delete()
                    throw UpdateException(UpdateProblem.NotPlasma)
                }
                // Never in the middle of a channel or a movie (5.18).
                if (graph.playingNow() != null) {
                    _state.value = ready.copy(problem = null)
                    return@launch
                }
                memory.edit { putString(INSTALLING, update.label).putLong(INSTALLING_CODE, update.versionCode) }
                _state.value = UpdateState.Installing(update)
                withContext(Dispatchers.IO) {
                    // A question from before goes with its session.
                    installer.tidy()
                    installer.install(apk)
                }
            } catch (stopped: CancellationException) {
                _state.value = ready.copy(problem = null)
                throw stopped
            } catch (failed: Exception) {
                memory.edit { remove(INSTALLING).remove(INSTALLING_CODE) }
                _state.value = ready.copy(problem = (failed as? UpdateException)?.problem ?: UpdateProblem.Refused)
            }
        }
    }

    /** "Install unknown apps" is allowed for Plasma now. */
    fun allowed(): Boolean = installer.allowed()

    /** What Android's installer said. */
    private fun heard(result: InstallResult) {
        val installing = _state.value as? UpdateState.Installing ?: return
        when (result) {
            InstallResult.Asking, InstallResult.Done -> Unit
            is InstallResult.Failed -> {
                memory.edit { remove(INSTALLING).remove(INSTALLING_CODE) }
                _state.value = UpdateState.Ready(installing.update, checkedAt(), result.problem)
            }
        }
    }

    /** The update found last, until it's installed or a check finds nothing newer. */
    private fun remembered(): UpdateState {
        val checkedAt = checkedAt()
        val code = memory.getLong(FOUND_CODE, 0)
        if (code <= installedCode) return UpdateState.Current(checkedAt)
        val update = Update(
            versionCode = code,
            versionName = memory.getString(FOUND_NAME, null) ?: return UpdateState.Current(checkedAt),
            notes = memory.getString(FOUND_NOTES, null).orEmpty(),
            url = memory.getString(FOUND_URL, null) ?: return UpdateState.Current(checkedAt),
            sha256 = memory.getString(FOUND_SHA256, null) ?: return UpdateState.Current(checkedAt),
        )
        return UpdateState.Ready(update, checkedAt)
    }

    private fun remember(update: Update?) = memory.edit {
        if (update == null) {
            remove(FOUND_CODE).remove(FOUND_NAME).remove(FOUND_NOTES).remove(FOUND_URL).remove(FOUND_SHA256)
        } else {
            putLong(FOUND_CODE, update.versionCode)
            putString(FOUND_NAME, update.versionName)
            putString(FOUND_NOTES, update.notes)
            putString(FOUND_URL, update.url)
            putString(FOUND_SHA256, update.sha256)
        }
    }

    private fun checkedAt(): Instant? = memory.getLong(CHECKED_AT, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    /** Whether [apk] is [update]'s, whole: downloaded before, and Install Now pressed again. */
    private suspend fun isWhole(apk: File, update: Update): Boolean = withContext(Dispatchers.IO) {
        apk.isFile && try {
            val digest = MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            UpdateClient.hex(digest.digest()) == update.sha256
        } catch (unreadable: IOException) {
            false
        }
    }

    /** Whether [apk] is this Plasma at [update]'s version, signed with its key. */
    private suspend fun fits(apk: File, update: Update): Boolean = withContext(Dispatchers.IO) {
        val facts = Signers.ofApk(context, apk) ?: return@withContext false
        val installed = Signers.installed(context) ?: return@withContext false
        Signers.fits(facts, installed, update.versionCode)
    }

    private fun codeOf(file: File): Long? = file.name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull()

    companion object {
        private const val MEMORY = "updates"
        private const val FOLDER = "updates"
        private const val PREFIX = "plasma-"
        private const val SUFFIX = ".apk"
        private const val CHECKED_AT = "checkedAt"
        private const val FOUND_CODE = "found.code"
        private const val FOUND_NAME = "found.name"
        private const val FOUND_NOTES = "found.notes"
        private const val FOUND_URL = "found.url"
        private const val FOUND_SHA256 = "found.sha256"
        private const val INSTALLING = "installing"
        private const val INSTALLING_CODE = "installing.code"
        private const val BUFFER_BYTES = 64 * 1024

        /** How often Plasma sees whether the daily check is due. */
        private const val LOOK_EVERY_MS = 60 * 60 * 1000L
        private val DAILY: Duration = Duration.ofHours(24)

        /** "0.1.0-dev (512)": Plasma's version, as updates name theirs. */
        fun versionOf(context: Context): String = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        } catch (gone: Exception) {
            ""
        }

        private fun installedCode(context: Context): Long = try {
            PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        } catch (gone: Exception) {
            0
        }
    }
}
