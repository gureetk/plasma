// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import ca.gureet.plasma.core.data.NetworkFolder
import ca.gureet.plasma.core.design.icon.PlasmaIcons
import ca.gureet.plasma.feature.settings.PhotoDrive
import ca.gureet.plasma.feature.settings.PhotoFolderList
import ca.gureet.plasma.feature.settings.PhotoFolders
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ca.gureet.plasma.feature.settings.R as SettingsR

/**
 * Screensaver Pictures › Photos From › On This TV or a USB Drive (#170): this TV's own storage
 * and each USB drive that's in, and their folders, once Android lets Plasma see photos.
 */
class PhotoPlaces(private val context: Context, private val graph: AppGraph) : PhotoFolders {
    override val permission: String? = photosPermission(Build.VERSION.SDK_INT)

    override suspend fun drives(): List<PhotoDrive> = withContext(Dispatchers.IO) { drivesNow() }

    /** This TV first, then the USB drives, by the names Android gives them. */
    private fun drivesNow(): List<PhotoDrive> {
        val storage = context.getSystemService(StorageManager::class.java)
        return context.getExternalFilesDirs(null).filterNotNull().mapNotNull(RecordingPlaces::driveOf).distinctBy { it.path }.mapIndexed { i, root ->
            val name = if (i == 0) {
                context.getString(SettingsR.string.this_tv)
            } else {
                runCatching { storage?.getStorageVolume(root)?.getDescription(context) }.getOrNull() ?: context.getString(SettingsR.string.usb_drive)
            }
            PhotoDrive(name, root.path)
        }
    }

    override suspend fun folders(path: String): PhotoFolderList = withContext(Dispatchers.IO) {
        val inside = File(path).listFiles().orEmpty()
        PhotoFolderList(
            // Android's folder of apps' own files has no photos to show.
            inside.filter { it.isDirectory && !it.name.startsWith('.') && it.name != ANDROID }.map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER),
            inside.count { it.isFile && ScreensaverPhotos.isPhoto(it.name) },
        )
    }

    override fun use(drive: PhotoDrive, path: String) {
        graph.scope.launch {
            graph.data.settings.setScreensaverPhotos(path)
            graph.notice(Notice(context.getString(R.string.photos_folder_saved, shownOn(drive, path)), PlasmaIcons.Photo, Notice.LONG_MS))
        }
    }

    /**
     * [place] as Screensaver Pictures shows it: "Pictures · This TV", or for the network
     * [folder]'s "nas.home.lan › Photos"; empty for none.
     */
    fun shown(place: String?, folder: NetworkFolder?): String = when {
        place == null -> ""
        place.startsWith(SMB) -> folder?.shown.orEmpty()
        else -> drivesNow().filter { place.startsWith(it.path) }.maxByOrNull { it.path.length }?.let { shownOn(it, place) } ?: File(place).name
    }

    /** A folder on [drive]: its name and the drive's, or the drive's alone for all of it. */
    private fun shownOn(drive: PhotoDrive, path: String): String =
        if (path.trimEnd('/') == drive.path.trimEnd('/')) drive.name else context.getString(SettingsR.string.photos_on_drive, File(path).name, drive.name)

    companion object {
        private const val ANDROID = "Android"
        private const val SMB = "smb://"

        /**
         * What Plasma asks Android for to see the photos on the TV and its drives (#170), on
         * [sdk]: from Android 13, to see pictures; before it, to read its storage.
         */
        @SuppressLint("InlinedApi") // READ_MEDIA_IMAGES only from Android 13, as it says.
        fun photosPermission(sdk: Int): String =
            if (sdk >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
