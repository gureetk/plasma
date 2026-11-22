// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

import ca.gureet.plasma.core.model.PhonePlaylists
import java.io.File
import java.io.IOException
import java.util.UUID

/** A kept playlist's file is gone: the TV was restored from a backup, or its storage cleared (#122). */
class PlaylistFileMissing : IOException("The playlist file sent from a phone isn't on this TV")

/**
 * Playlists sent as files from a phone (#122), each kept in [folder] as "<id>.m3u" under the
 * address [PhonePlaylists] gives it. Without a folder, as in tests that don't need one, none can
 * be kept.
 */
class KeptPlaylists(private val folder: File?) {
    /** Keeps [bytes], sent as the file [name], and gives its address. */
    fun keep(name: String, bytes: ByteArray): String {
        val dir = folder ?: throw IOException("Playlists can't be kept here")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Can't make $dir")
        val id = UUID.randomUUID().toString().replace("-", "").take(ID_LENGTH)
        File(dir, "$id$EXTENSION").writeBytes(bytes)
        val shown = name.substringAfterLast('/').substringAfterLast('\\').filter { it >= ' ' }.trim().take(MAX_NAME).ifEmpty { DEFAULT_NAME }
        return "${PhonePlaylists.PREFIX}$id/$shown"
    }

    /** The file kept for [address], if it's a kept playlist's and the file is here. */
    fun fileOf(address: String): File? {
        if (!PhonePlaylists.isKept(address)) return null
        val id = address.removePrefix(PhonePlaylists.PREFIX).substringBefore('/')
        if (!ID.matches(id)) return null
        return folder?.let { File(it, "$id$EXTENSION") }?.takeIf { it.isFile }
    }

    /** The file of a source no longer using [address] goes. */
    fun forget(address: String) {
        fileOf(address)?.delete()
    }

    /** Files no source uses, of [addresses], go: ones sent from a phone and never added. */
    fun keepOnly(addresses: Collection<String>) {
        val used = addresses.mapNotNullTo(HashSet()) { fileOf(it)?.name }
        folder?.listFiles()?.filter { it.isFile && it.name !in used }?.forEach { it.delete() }
    }

    private companion object {
        const val ID_LENGTH = 12
        const val EXTENSION = ".m3u"
        const val MAX_NAME = 80
        const val DEFAULT_NAME = "playlist.m3u"
        val ID = Regex("[0-9a-f]{$ID_LENGTH}")
    }
}
