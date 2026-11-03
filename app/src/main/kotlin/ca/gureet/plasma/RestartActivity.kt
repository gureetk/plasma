// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process

/**
 * Starts Plasma again (#108, after Restore from Backup), so everything loads afresh from what was
 * restored. This activity runs in a process of its own: Plasma's goes, then this opens Plasma's
 * screen in a new one and goes too, as Jake Wharton's ProcessPhoenix does.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Plasma's process, should it still be there.
        intent.getIntExtra(KEY_PID, -1).takeIf { it > 0 }?.let(Process::killProcess)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val KEY_PID = "plasma.restart.pid"
        private const val PROCESS = ":restart"

        /** Starts Plasma again: this activity first, in its own process; then Plasma's process ends. */
        fun now(context: Context) {
            context.startActivity(
                Intent(context, RestartActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(KEY_PID, Process.myPid()),
            )
            Runtime.getRuntime().exit(0)
        }

        /** Whether this is the restart's own process, where nothing of Plasma's starts. */
        fun isOwnProcess(context: Context): Boolean = processName(context)?.endsWith(PROCESS) == true

        private fun processName(context: Context): String? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return Application.getProcessName()
            val pid = Process.myPid()
            return context.getSystemService(ActivityManager::class.java)?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
    }
}
