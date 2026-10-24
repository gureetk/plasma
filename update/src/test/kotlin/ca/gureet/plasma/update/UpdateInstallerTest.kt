// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.update

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Handing an update to Android's installer, and hearing what it says (brief 5.18). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UpdateInstallerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val installer = context.packageManager.packageInstaller

    @Test fun handsTheApkToAndroidsInstaller() {
        val apk = File(context.cacheDir, "plasma-512.apk").apply { writeBytes(ByteArray(4_096) { it.toByte() }) }

        UpdateInstaller(context).install(apk)

        val session = installer.mySessions.single()
        assertEquals(context.packageName, session.appPackageName)

        // Sessions left from an earlier try go.
        UpdateInstaller(context).tidy()
        assertEquals(emptyList<PackageInstaller.SessionInfo>(), installer.mySessions)
    }

    private fun status(status: Int, extras: Intent.() -> Unit = {}) {
        InstallStatusReceiver().onReceive(context, Intent(UpdateInstaller.ACTION_STATUS).putExtra(PackageInstaller.EXTRA_STATUS, status).apply(extras))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun showsTheInstallersQuestionAndPassesOnItsAnswers() = runTest {
        val heard = mutableListOf<InstallResult>()
        val listening = launch(UnconfinedTestDispatcher(testScheduler)) { UpdateInstaller.results.toList(heard) }

        status(PackageInstaller.STATUS_PENDING_USER_ACTION) { putExtra(Intent.EXTRA_INTENT, Intent(CONFIRM)) }
        status(PackageInstaller.STATUS_FAILURE_ABORTED) { putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "Cancelled") }
        status(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
        status(PackageInstaller.STATUS_FAILURE_STORAGE)
        status(PackageInstaller.STATUS_SUCCESS)
        // Asking, with nothing to show.
        status(PackageInstaller.STATUS_PENDING_USER_ACTION)

        assertEquals(CONFIRM, shadowOf(context).nextStartedActivity.action)
        assertEquals(
            listOf(
                InstallResult.Asking,
                InstallResult.Failed(UpdateProblem.Refused, "Cancelled"),
                InstallResult.Failed(UpdateProblem.NotPlasma, null),
                InstallResult.Failed(UpdateProblem.NoRoom, null),
                InstallResult.Done,
                InstallResult.Failed(UpdateProblem.Refused, null),
            ),
            heard,
        )
        listening.cancel()
    }

    private companion object {
        const val CONFIRM = "android.content.pm.action.CONFIRM_INSTALL"
    }
}
