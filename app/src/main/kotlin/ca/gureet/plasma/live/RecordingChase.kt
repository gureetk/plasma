// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.live

import ca.gureet.plasma.player.Chase
import ca.gureet.plasma.recording.RecordingFiles
import ca.gureet.plasma.stream.LocalSession
import java.io.File

/**
 * A recording being written, as chase play reads it (brief 5.6): the hub's [session] serves it from
 * a byte on, and its keyframe [index] says which byte a moment is at. Times count from [startedAt],
 * when its first keyframe came, or before there's one, when it started; [writtenAt] says when it
 * was last written to.
 */
class RecordingChase(
    private val session: LocalSession,
    private val index: File,
    private val startedAt: Long,
    private val writtenAt: () -> Long,
) : Chase {
    /** A recording's [file] on this TV: its index beside it, and the file changes as each chunk is written. */
    constructor(session: LocalSession, file: File, startedAt: Long) : this(session, RecordingFiles.indexOf(file), startedAt, file::lastModified)

    override fun urlAt(position: Long): String = if (position <= 0) session.url else session.urlAt(position)

    override fun seek(ms: Long): Pair<Long, Long>? =
        RecordingFiles.keyframeIn(index, startedAt + ms)?.let { (position, at) -> position to (at - startedAt).coerceAtLeast(0) }

    override fun writtenMs(): Long = (writtenAt() - startedAt).coerceAtLeast(0)
}
