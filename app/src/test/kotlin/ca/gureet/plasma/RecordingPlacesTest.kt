// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Where recordings are kept, so they outlast Plasma (#161). */
class RecordingPlacesTest {
    private val tvOwn = File("/storage/emulated/0/Android/data/ca.gureet.plasma/files")
    private val usbOwn = File("/storage/1A2B-3C4D/Android/data/ca.gureet.plasma/files")

    @Test fun aDriveIsWherePlasmasOwnFolderIs() {
        assertEquals(File("/storage/emulated/0"), RecordingPlaces.driveOf(tvOwn))
        assertEquals(File("/storage/1A2B-3C4D"), RecordingPlaces.driveOf(usbOwn))
        // Plasma's storage inside the TV isn't on a drive anything else sees.
        assertNull(RecordingPlaces.driveOf(File("/data/user/0/ca.gureet.plasma/files")))
    }

    @Test fun recordingsOnADriveShareItsRoomWhereverTheyAreOnIt() {
        val usb = RecordingPlaces.driveOfFolder(File("/storage/1A2B-3C4D/${RecordingPlaces.SHARED}"))
        assertEquals("/storage/1A2B-3C4D", usb)
        assertEquals(usb, RecordingPlaces.driveOfFolder(File(usbOwn, "Recordings")))

        assertTrue(RecordingPlaces.isOn("/storage/1A2B-3C4D/Download/Plasma/Recordings/Arena Sports 1 – Harbour City v Redmoor – 2026-10-21 1945.ts", usb))
        assertTrue(RecordingPlaces.isOn("$usbOwn/Recordings/Arena Sports 1 – Harbour City v Redmoor – 2026-10-21 1945.ts", usb))
        assertFalse(RecordingPlaces.isOn("/storage/emulated/0/Download/Plasma/Recordings/Wildframe 4K – Kingdoms of Ice – 2026-10-20 2000.ts", usb))
        // Inside the TV, in Plasma's own storage.
        assertEquals("/data/user/0/ca.gureet.plasma/files", RecordingPlaces.driveOfFolder(File("/data/user/0/ca.gureet.plasma/files/Recordings")))
    }

    @Test fun recordingsGoToDownloadWhereAndroidAllows() {
        // From Android 11, on any drive, without asking.
        assertTrue(RecordingPlaces.canShare(30, onTv = false, allowed = false))
        assertTrue(RecordingPlaces.canShare(36, onTv = true, allowed = false))
        // Android 10: only through the media store, which recordings can't use.
        assertFalse(RecordingPlaces.canShare(29, onTv = true, allowed = true))
        // Before: the TV's own storage, with the storage permission.
        assertTrue(RecordingPlaces.canShare(28, onTv = true, allowed = true))
        assertFalse(RecordingPlaces.canShare(28, onTv = true, allowed = false))
        assertFalse(RecordingPlaces.canShare(28, onTv = false, allowed = true))
    }

    @Test fun findRecordingsAsksToSeeVideos() {
        assertEquals("android.permission.READ_MEDIA_VIDEO", RecordingPlaces.findPermission(33))
        assertEquals("android.permission.READ_EXTERNAL_STORAGE", RecordingPlaces.findPermission(30))
        assertNull(RecordingPlaces.findPermission(29))
        assertEquals("android.permission.WRITE_EXTERNAL_STORAGE", RecordingPlaces.findPermission(26))
    }
}
