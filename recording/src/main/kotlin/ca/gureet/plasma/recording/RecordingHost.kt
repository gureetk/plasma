// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.data.Recording
import ca.gureet.plasma.core.data.RecordingStore
import ca.gureet.plasma.stream.HubSession
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

    /**
     * Local catch-up as Settings has it, as it changes (brief 5.5): the channels the service
     * writes all the time.
     */
    val localCatchUp: Flow<LocalCatchUpPlan> get() = flowOf(LocalCatchUpPlan.None)

    /**
     * Copy to Network Folder (brief 5.6): where finished recordings are copied, and whether they're
     * named for Jellyfin; null while it's off.
     */
    suspend fun copyTo(): CopyTo? = null

    /** The guide's artwork, for a copy; null when it can't be had. */
    suspend fun fetch(url: String): ByteArray? = null

    /**
     * [recording]'s copy is done, with [freeBytes] left on the share: Jellyfin can be asked to look
     * for it (brief 5.6).
     */
    suspend fun copied(recording: Recording, freeBytes: Long?) = Unit

    /** [folder] can't be reached: copies wait, and Plasma says so once (brief 5.6 and 4.35). */
    suspend fun copyWaiting(folder: NetworkFolder) = Unit

    /**
     * Record To › Network folder (brief 5.6): the network folder recordings are written straight
     * to, named for Jellyfin as Settings says; null when they go to a USB drive or this TV, or no
     * folder is set up.
     */
    suspend fun recordStraightTo(): CopyTo? = null

    /** Where finished recordings are copied: [folder], named for Jellyfin when [jellyfin]. */
    data class CopyTo(val folder: NetworkFolder, val jellyfin: Boolean)

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
