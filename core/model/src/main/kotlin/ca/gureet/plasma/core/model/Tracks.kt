// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.model

/**
 * A sound or subtitle track picked on a channel in Audio and Subtitles, as it's kept so the
 * channel's next tune finds it again (brief 5.4.6): its language, format, channels and name.
 */
data class TrackPick(val language: String?, val codec: String? = null, val channels: Int? = null, val title: String? = null)

/**
 * What was picked on a channel, which comes before the language rules (5.4.6): its sound, and its
 * subtitles, or [subtitlesOff] when they were turned off on it.
 */
data class ChannelTracks(val audio: TrackPick? = null, val subtitles: TrackPick? = null, val subtitlesOff: Boolean = false)
