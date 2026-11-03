// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.feature.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test

/** Back Up Settings' and Restore from Backup's steps (#108), against an app that does as it's told. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupModelTest {
    private val scope = TestScope(UnconfinedTestDispatcher())

    private class FakeApp : BackupActions {
        var saved: BackupSaved = BackupSaved.Saved("plasma-backup-20261103-2041.json", "Android/data/ca.gureet.plasma/files/Backups")
        val saves = mutableListOf<String?>()
        var opened: BackupOpened = BackupOpened.Ready(BackupActions.Sample.summary)
        var correct = "correct horse"
        val restores = mutableListOf<String?>()
        var busy = false
        var restarts = 0

        override suspend fun saveToUsb(passphrase: String?): BackupSaved = saved.also { saves += passphrase }

        override fun toPhone(passphrase: String?): Flow<PhonePairing> = emptyFlow()

        override suspend fun found(): List<FoundBackup> = BackupActions.Sample.found()

        override suspend fun open(found: FoundBackup): BackupOpened = opened

        override fun fromPhone(): Flow<PhoneRestore> = emptyFlow()

        override suspend fun restore(passphrase: String?): RestoreOutcome {
            restores += passphrase
            return when {
                busy -> RestoreOutcome.Busy
                passphrase != null && passphrase != correct -> RestoreOutcome.WrongPassphrase
                else -> RestoreOutcome.Restored(if (passphrase == null) listOf("Home Server") else emptyList())
            }
        }

        override fun restart() {
            restarts++
        }
    }

    @Test fun aBackupWithoutLoginsGoesStraightToTheDrive() {
        val app = FakeApp()
        val model = BackupModel(app, scope, toPhone = false)
        assertEquals(BackupStage.Logins, model.stage)

        model.withoutLogins()

        assertEquals(listOf<String?>(null), app.saves)
        assertEquals(BackupStage.Saved("plasma-backup-20261103-2041.json", "Android/data/ca.gureet.plasma/files/Backups"), model.stage)
    }

    @Test fun aPassphraseIsTypedTwiceAndLongEnough() {
        val app = FakeApp()
        val model = BackupModel(app, scope, toPhone = false)
        model.withLogins()
        assertEquals(BackupStage.Passphrase, model.stage)

        model.passphrase("short", "short")
        assertEquals(BackupStage.Mismatch(tooShort = true), model.stage)
        model.withLogins()
        model.passphrase("correct horse", "correct hose")
        assertEquals(BackupStage.Mismatch(tooShort = false), model.stage)
        assertEquals(emptyList<String?>(), app.saves)

        model.withLogins()
        model.passphrase("correct horse", "correct horse")
        assertEquals(listOf<String?>("correct horse"), app.saves)
    }

    @Test fun noDriveIsSaidAndTryAgainKeepsThePassphrase() {
        val app = FakeApp().apply { saved = BackupSaved.NoDrive }
        val model = BackupModel(app, scope, toPhone = false)
        model.withLogins()
        model.passphrase("correct horse", "correct horse")
        val notSaved = model.stage as BackupStage.NotSaved
        assertEquals(BackupStage.NotSaved("correct horse", failed = false), notSaved)

        app.saved = BackupSaved.Failed
        model.tryAgain(notSaved)
        assertEquals(listOf<String?>("correct horse", "correct horse"), app.saves)
        assertEquals(BackupStage.NotSaved("correct horse", failed = true), model.stage)
    }

    @Test fun toAPhoneTheCodeShowsWithThePassphraseChosen() {
        val model = BackupModel(FakeApp(), scope, toPhone = true)
        model.withLogins()
        model.passphrase("correct horse", "correct horse")
        assertEquals(BackupStage.Phone("correct horse"), model.stage)
    }

    @Test fun aBackupFoundIsShownThenRestoredWithItsPassphrase() {
        val app = FakeApp()
        val model = RestoreModel(app, scope)
        model.look()
        val found = (model.stage as RestoreStage.Found).backups
        assertEquals(2, found.size)

        model.choose(found.first())
        val summary = BackupActions.Sample.summary
        assertEquals(RestoreStage.Summary(summary), model.stage)
        model.withLogins(summary)
        assertEquals(RestoreStage.Passphrase(summary), model.stage)

        model.passphrase(summary, "wrong horse")
        val wrong = model.stage as RestoreStage.WrongPassphrase
        model.tryAgain(wrong)
        model.passphrase(summary, "correct horse")
        assertEquals(listOf<String?>("wrong horse", "correct horse"), app.restores)
        assertEquals(RestoreStage.Done(emptyList()), model.stage)

        model.restart()
        assertEquals(1, app.restarts)
    }

    @Test fun thePassphraseAPhoneSentIsTriedFirstAndAskedForWhenWrong() {
        val app = FakeApp()
        val model = RestoreModel(app, scope)
        model.received(BackupActions.Sample.summary.copy(passphrase = "correct hose"))

        model.withLogins((model.stage as RestoreStage.Summary).summary)
        assertEquals(listOf<String?>("correct hose"), app.restores)
        // The TV asks, rather than trying the phone's again.
        val wrong = model.stage as RestoreStage.WrongPassphrase
        assertEquals(null, wrong.summary.passphrase)
        model.tryAgain(wrong)
        assertEquals(RestoreStage.Passphrase(BackupActions.Sample.summary), model.stage)
    }

    @Test fun withoutLoginsTheSourcesThatNeedThemAreNamed() {
        val app = FakeApp()
        val model = RestoreModel(app, scope)
        model.received(BackupActions.Sample.summary)
        model.withoutLogins()
        assertEquals(listOf<String?>(null), app.restores)
        assertEquals(RestoreStage.Done(listOf("Home Server")), model.stage)
    }

    @Test fun whatCantBeRestoredSaysWhy() {
        val app = FakeApp().apply { opened = BackupOpened.Unreadable(newer = true) }
        val model = RestoreModel(app, scope)
        model.look()
        model.choose((model.stage as RestoreStage.Found).backups.first())
        assertEquals(RestoreStage.Unreadable(newer = true), model.stage)

        app.busy = true
        model.received(BackupActions.Sample.summary)
        model.withoutLogins()
        assertEquals(RestoreStage.Busy, model.stage)
    }
}
