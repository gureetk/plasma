// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.recording

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * A share that's a folder on this computer, for the tests. [refuse] turns every write down;
 * [away] makes it unreachable, as a NAS that's off is; [cutShort] writes only that much of a file.
 */
class FolderShare(private val root: File) : ShareSession {
    var closed = false
    var refuse: ShareProblem? = null

    @Volatile var away = false

    /** Writes only this much of what it's given, as a share that fills up might. */
    var cutShort: Int? = null

    /** How many times it was opened to write to anywhere in a file. */
    var openedToWrite = 0

    override fun folders(path: String): List<String> = reach { at(path).listFiles { file -> file.isDirectory }?.map { it.name }?.sorted().orEmpty() }

    override fun files(path: String): List<String> = reach { at(path).listFiles { file -> file.isFile }?.map { it.name }.orEmpty() }

    override fun exists(path: String): Boolean = reach { at(path).exists() }

    override fun makeFolders(path: String) = reach {
        at(path).mkdirs()
        Unit
    }

    override fun write(path: String, input: InputStream) = reach {
        refuse?.let { throw ShareException(it) }
        val bytes = input.readBytes()
        at(path).writeBytes(cutShort?.let { bytes.copyOf(minOf(it, bytes.size)) } ?: bytes)
    }

    override fun rename(from: String, to: String) = reach {
        at(to).delete()
        check(at(from).renameTo(at(to)))
    }

    override fun delete(path: String) = reach {
        at(path).delete()
        Unit
    }

    override fun size(path: String): Long? = reach { at(path).takeIf { it.isFile }?.length() }

    override fun freeBytes(): Long = 3_100_000_000_000

    override fun openWrite(path: String): ShareFile = reach {
        refuse?.let { throw ShareException(it) }
        openedToWrite++
        FolderFile(RandomAccessFile(at(path), "rw"))
    }

    /** [path] open to read, as the share's growing reads are. */
    fun openRead(path: String): ShareFile = reach { FolderFile(RandomAccessFile(at(path), "r")) }

    override fun close() {
        closed = true
    }

    private fun at(path: String) = if (path.isEmpty()) root else File(root, path)

    private inline fun <T> reach(work: () -> T): T {
        if (away) throw ShareException(ShareProblem.CantReach)
        return work()
    }

    private inner class FolderFile(private val file: RandomAccessFile) : ShareFile {
        override fun size(): Long = reach { file.length() }

        override fun write(bytes: ByteArray, offset: Int, count: Int, at: Long) = reach {
            refuse?.let { throw ShareException(it) }
            file.seek(at)
            file.write(bytes, offset, count)
        }

        override fun read(into: ByteArray, offset: Int, count: Int, at: Long): Int = reach {
            file.seek(at)
            file.read(into, offset, count).coerceAtLeast(0)
        }

        override fun close() = file.close()
    }
}
