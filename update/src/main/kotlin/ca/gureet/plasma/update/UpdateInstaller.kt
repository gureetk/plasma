// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Hands a checked update to Android's installer (brief 5.18), through a `PackageInstaller` session.
 * The TV asks the user to confirm, unless Android lets Plasma update itself without asking (Android
 * 12 and later, once Plasma installed the version in place). Android then stops Plasma, puts the new
 * version in, and the TV's Home starts it again.
 */
class UpdateInstaller(private val context: Context) {
    /** Android lets Plasma install apps: "Install unknown apps" is allowed for it. */
    fun allowed(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Lets go of sessions left from an earlier try. */
    fun tidy() {
        val installer = context.packageManager.packageInstaller
        for (session in installer.mySessions) runCatching { installer.abandonSession(session.sessionId) }
    }

    /** Hands [apk] to Android's installer; what it says comes to [results]. */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = try {
            installer.createSession(params)
        } catch (refused: IOException) {
            throw UpdateException(UpdateProblem.NoRoom, refused)
        }
        try {
            installer.openSession(id).use { session ->
                session.openWrite(APK_NAME, 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output, BUFFER_BYTES) }
                    session.fsync(output)
                }
                session.commit(statusOf(id))
            }
        } catch (failed: IOException) {
            runCatching { installer.abandonSession(id) }
            throw UpdateException(UpdateProblem.NoRoom, failed)
        } catch (failed: SecurityException) {
            runCatching { installer.abandonSession(id) }
            throw UpdateException(UpdateProblem.NotAllowed, failed)
        }
    }

    /** Where the installer says how it went: [InstallStatusReceiver]. */
    private fun statusOf(session: Int): IntentSender {
        val intent = Intent(context, InstallStatusReceiver::class.java).setAction(ACTION_STATUS)
        // Android adds the status to the intent, so it has to be mutable.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, session, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable).intentSender
    }

    companion object {
        internal const val ACTION_STATUS = "ca.gureet.plasma.update.STATUS"
        private const val APK_NAME = "plasma.apk"
        private const val BUFFER_BYTES = 64 * 1024

        private val said = MutableSharedFlow<InstallResult>(extraBufferCapacity = 8)

        /** What Android's installer says, while Plasma runs. */
        val results: SharedFlow<InstallResult> = said.asSharedFlow()

        internal fun tell(result: InstallResult) {
            said.tryEmit(result)
        }
    }
}

/** What Android's installer said about an update. */
sealed interface InstallResult {
    /** The TV is asking the user to confirm. */
    data object Asking : InstallResult

    /** Installed: Android is about to start the new version. */
    data object Done : InstallResult

    /** Not installed: the user said no, or the installer couldn't. */
    data class Failed(val problem: UpdateProblem, val message: String?) : InstallResult
}

/** Hears the installer: shows its question when it has one, and passes the rest on. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        UpdateInstaller.tell(
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> ask(context, intent)
                PackageInstaller.STATUS_SUCCESS -> InstallResult.Done
                PackageInstaller.STATUS_FAILURE_STORAGE -> InstallResult.Failed(UpdateProblem.NoRoom, message)
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallResult.Failed(UpdateProblem.NotPlasma, message)
                else -> InstallResult.Failed(UpdateProblem.Refused, message)
            },
        )
    }

    /** The installer's own screen, asking the user to confirm. */
    private fun ask(context: Context, intent: Intent): InstallResult {
        val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            ?: return InstallResult.Failed(UpdateProblem.Refused, null)
        return try {
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            InstallResult.Asking
        } catch (missing: ActivityNotFoundException) {
            InstallResult.Failed(UpdateProblem.Refused, missing.message)
        } catch (blocked: SecurityException) {
            InstallResult.Failed(UpdateProblem.Refused, blocked.message)
        }
    }
}
