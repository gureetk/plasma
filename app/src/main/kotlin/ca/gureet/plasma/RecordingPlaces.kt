// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import java.io.File

/**
 * Where recordings are kept (#161, the owner's choice of 6 November 2026): in
 * Download/Plasma/Recordings on the drive they're recorded to, the USB drive's or the TV's own,
 * beside the backups in Download/Plasma. Android deletes Plasma's own folders, on the TV and on
 * every drive plugged in, when Plasma is uninstalled, and empties them when its storage is
 * cleared; what's in Download stays.
 *
 * From Android 11 Plasma writes there as it does in its own folder, without asking. Android 10
 * lets an app there only through its media store, which a recording being written, played as it
 * grows and found again by its index can't go through, so there recordings stay in Plasma's own
 * folder. Before Android 10 it takes the storage permission, and only on the TV's own storage: a
 * USB drive is Plasma's to write to only in its own folder there.
 */
internal object RecordingPlaces {
    /** The folder on a drive, as the TV and a computer show it. */
    const val SHARED = "Download/Plasma/Recordings"

    /** Plasma's own folder on a drive: "…/Android/data/<package>/files". */
    private const val OWN = "/Android/data/"

    /** The drive Plasma's own folder [appFolder] is on ("/storage/emulated/0"); null for a folder that isn't on one. */
    fun driveOf(appFolder: File): File? = appFolder.absolutePath.substringBefore(OWN, "").takeIf { it.isNotEmpty() }?.let(::File)

    /**
     * The drive a recordings [folder] is on, by its path: the root of the USB drive's or the TV's
     * shared storage, for Plasma's own folder or Download/Plasma/Recordings there; for one in
     * Plasma's storage inside the TV, that storage. Recordings on the same drive take its room.
     */
    fun driveOfFolder(folder: File): String {
        val path = folder.absolutePath
        path.substringBefore(OWN, "").takeIf { it.isNotEmpty() }?.let { return it }
        if (path.endsWith("/$SHARED")) return path.removeSuffix("/$SHARED")
        return folder.parentFile?.absolutePath ?: path
    }

    /** Whether a recording's [file] is on [drive] ([driveOfFolder]). */
    fun isOn(file: String, drive: String): Boolean = File(file).parentFile?.let(::driveOfFolder) == drive

    /**
     * Whether recordings can go to Download/Plasma/Recordings on a drive, on Android [sdk]: [onTv]
     * for the TV's own storage, where before Android 10 the storage permission ([allowed]) does it.
     */
    fun canShare(sdk: Int, onTv: Boolean, allowed: Boolean): Boolean = when {
        sdk >= Build.VERSION_CODES.R -> true
        sdk == Build.VERSION_CODES.Q -> false
        else -> onTv && allowed
    }

    /**
     * What Find Recordings asks Android for on [sdk]: to see videos, which shows Plasma the
     * recordings it saved before it was reinstalled. Before Android 10, the storage permission,
     * which also lets new recordings go to the TV's Download folder. Null on Android 10, where
     * recordings never left Plasma's own folder.
     */
    @SuppressLint("InlinedApi") // READ_MEDIA_VIDEO only from Android 13, as it says.
    fun findPermission(sdk: Int): String? = when {
        sdk >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_VIDEO
        sdk >= Build.VERSION_CODES.R -> Manifest.permission.READ_EXTERNAL_STORAGE
        sdk == Build.VERSION_CODES.Q -> null
        else -> Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
}
