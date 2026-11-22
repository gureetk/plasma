// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import java.nio.file.Files
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Crashes and ANRs in Plasma's own log, for Export Diagnostics (#131). */
class CrashesTest {
    private val dir = Files.createTempDirectory("logs").toFile()

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    @Test fun anUncaughtExceptionIsWrittenBeforeAndroidEndsTheApp() {
        val file = RotatingLog(dir, "plasma", maxBytes = 100_000)
        val log = DiagnosticLog(file, clock = { 1_793_652_060_000L }, zone = { ZoneOffset.UTC })
        var passedOn: Throwable? = null
        val handler = Crashes.handler(log) { _, error -> passedOn = error }

        val error = IllegalStateException("No channel 105 at http://viewer:s3cret@line.example.org/live")
        handler.uncaughtException(Thread.currentThread(), error)

        // Written already, without its credentials, and then Android's own handler takes it.
        val text = String(file.read(), Charsets.UTF_8)
        assertTrue(text.contains("[crash] Uncaught on ${Thread.currentThread().name}: java.lang.IllegalStateException"))
        assertTrue(text.contains("CrashesTest.anUncaughtExceptionIsWrittenBeforeAndroidEndsTheApp"))
        assertTrue("s3cret" !in text)
        assertEquals(error, passedOn)
    }

    @Test fun androidsRecordOfHowPlasmaEndedReadsPlainly() {
        val foreground = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        val cached = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        assertEquals("Not responding (ANR), on screen: Input dispatching timed out", Crashes.lineOf(ApplicationExitInfo.REASON_ANR, "Input dispatching timed out", foreground))
        assertEquals("Crashed in native code, in the background", Crashes.lineOf(ApplicationExitInfo.REASON_CRASH_NATIVE, null, cached))
        assertEquals("Stopped by Android for memory, in the background", Crashes.lineOf(ApplicationExitInfo.REASON_LOW_MEMORY, " ", cached))
        // Ends that are nothing wrong say nothing.
        assertNull(Crashes.lineOf(ApplicationExitInfo.REASON_USER_REQUESTED, null, foreground))
        assertNull(Crashes.lineOf(ApplicationExitInfo.REASON_EXIT_SELF, null, cached))
    }

    @Test fun eachExitIsNotedOnce() {
        assertEquals(0L, Crashes.seen(dir))
        Crashes.markSeen(dir, 1_793_652_060_000L)
        assertEquals(1_793_652_060_000L, Crashes.seen(dir))
    }
}
