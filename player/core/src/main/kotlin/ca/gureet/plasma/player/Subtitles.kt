// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.player

import android.graphics.Bitmap

/** Which edge of a subtitle, or its middle, sits on the place it's given. */
enum class CueAnchor { Start, Middle, End }

/** How a subtitle's lines line up with each other. */
enum class CueAlign { Start, Center, End }

/**
 * Where a subtitle goes in the picture, in fractions of its width and height. With nothing set,
 * it sits at the bottom, in the middle, as subtitles usually do.
 */
data class CuePlace(
    /** How far across the picture its [xAnchor] edge or middle sits, 0 to 1; null centers it. */
    val x: Float? = null,
    val xAnchor: CueAnchor = CueAnchor.Start,
    /** How far down the picture its [yAnchor] edge or middle sits, 0 to 1. */
    val y: Float? = null,
    val yAnchor: CueAnchor = CueAnchor.Start,
    /** Or, for captions, its row in lines of text: 0 is the top line, −1 the bottom one. */
    val row: Int? = null,
    /** Its width, 0 to 1; null for as wide as its text needs. */
    val width: Float? = null,
    /** A picture's height, 0 to 1; null keeps its shape. */
    val height: Float? = null,
)

/**
 * A subtitle an engine hands out for Plasma to draw over the picture (brief 5.4.4): the Android
 * player's cues. VLC and mpv draw their own.
 */
sealed interface SubtitleCue {
    val place: CuePlace

    data class Text(val text: String, override val place: CuePlace = CuePlace(), val align: CueAlign = CueAlign.Center) : SubtitleCue

    /** A subtitle that comes as a picture: DVB and Blu-ray (PGS) subtitles. */
    data class Picture(val bitmap: Bitmap, override val place: CuePlace) : SubtitleCue
}
