// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.RecordingStore
import ca.gureet.plasma.stream.HubSession
import java.io.File
import java.time.ZoneId

/** What recording needs from the rest of Plasma (brief 5.6): the app's graph provides it. */
interface RecordingHost {
    val recordings: RecordingStore

    /** The zone a recording's file name gives its start in. */
    val zone: ZoneId

    /**
     * Opens [channelId]'s live stream through the stream hub, sharing the connection when the
     * channel is already open, as when someone's watching it (brief 5.6).
     */
    suspend fun openForRecording(channelId: String): Opened

    /** The folder recordings go in now, as Record To says; null when there's nowhere to write. */
    fun recordingFolder(): File?

    /**
     * The drive [folder] is on is nearly full: When Space Runs Low lets the oldest watched
     * recordings go until there's room, or says to stop. Whether there's room now.
     */
    suspend fun makeRoom(folder: File): Boolean

    /** What opening a channel for a recording came to. */
    sealed interface Opened {
        class Stream(val session: HubSession) : Opened

        /** It couldn't be opened, and why ([ca.gureet.plasma.core.data.RecordingProblems]). */
        data class Problem(val problem: String) : Opened
    }

    /** Plasma's application, which holds the host. */
    interface Owner {
        val recordingHost: RecordingHost
    }
}
