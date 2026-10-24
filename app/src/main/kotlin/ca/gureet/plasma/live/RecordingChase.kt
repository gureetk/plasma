// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.player.Chase
import ca.gureet.plasma.recording.RecordingFiles
import ca.gureet.plasma.stream.LocalSession
import java.io.File

/**
 * A recording being written, as chase play reads it (brief 5.6): the hub's [session] serves its
 * [file] from a byte on, and its keyframe index says which byte a moment is at. Times count from
 * [startedAt], when its first keyframe came, or before there's one, when it started.
 */
class RecordingChase(private val session: LocalSession, private val file: File, private val startedAt: Long) : Chase {
    override fun urlAt(position: Long): String = if (position <= 0) session.url else session.urlAt(position)

    override fun seek(ms: Long): Pair<Long, Long>? =
        RecordingFiles.keyframeAt(file, startedAt + ms)?.let { (position, at) -> position to (at - startedAt).coerceAtLeast(0) }

    // The file's changed as each chunk is written.
    override fun writtenMs(): Long = (file.lastModified() - startedAt).coerceAtLeast(0)
}
