// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Color tokens from the design brief, section 2.2. Feature code uses these, never raw colors. */
object PlasmaColors {
    /** App background. Pure black is used only behind video. */
    val Bg = Color(0xFF0B0B0D)
    val Black = Color(0xFF000000)

    /** Settings rows, cards, input fields, storage card, sheet rows. */
    val Surface1 = Color(0xFF161618)
    /** Keys, tiles, image placeholders. */
    val Surface2 = Color(0xFF1B1B1E)
    /** Raised elements, the guide's "now" cells, top of graphite tiles. */
    val Surface3 = Color(0xFF232327)
    /** Channel-logo plates. */
    val Plate = Color(0xFF18181B)
    val GuidePast = Color(0xFF131315)
    val GuideFuture = Color(0xFF19191C)
    /** The search bar's keyboard strip. */
    val KeyboardStrip = Color(0xFF141416)
    /** Monospace command blocks. */
    val Code = Color(0xFF111113)

    /** Primary text, and the focus fill. */
    val Text = Color(0xFFF5F5F7)
    /** Secondary text (7.6:1 on Bg). */
    val Text2 = Color(0xFFA1A1A8)
    /** Tertiary text and small captions (5.7:1). */
    val Text3 = Color(0xFF8A8A91)
    /** Large text, dividers and disabled items only (3.9:1). */
    val Text4 = Color(0xFF6E6E75)
    /** Unfocused titles on imagery, key labels. */
    val TextSoft = Color(0xFFC7C7CC)
    val TextSoft2 = Color(0xFFD1D1D6)
    val TextSoft3 = Color(0xFFE5E5EA)

    /** Text on white (focused) surfaces. */
    val OnFocus = Color(0xFF0B0B0D)
    val OnFocus2 = Color(0xFF3A3A40)
    val OnFocus3 = Color(0xFF4A4A50)

    /** Secondary text in the player's white playhead bubble. */
    val OnFocusSoft = Color(0xFF5A5A60)

    /** Current tab, selected segment, unfocused buttons. */
    val Selected = Color(0x1FFFFFFF)
    /** Selected list item that doesn't have focus. */
    val SelectedSoft = Color(0x14FFFFFF)
    /** Buttons without focus (`.14` white). */
    val ButtonFill = Color(0x24FFFFFF)
    val Divider = Color(0x14FFFFFF)
    val DividerStrong = Color(0x1AFFFFFF)
    val Track = Color(0x38FFFFFF)

    /** LIVE dot, REC, recording progress, live-edge marker. */
    val Live = Color(0xFFFF453A)
    /** The recording dot on a focused (white) guide cell. */
    val RecordingDot = Color(0xFFE5342A)

    /** The word "Recording" on dark backgrounds. */
    val LiveText = Color(0xFFFF6B61)
    val Ok = Color(0xFF3DD68C)
    val OkText = Color(0xFF7FE3AE)
    /** The green status dot on a focused (white) row. */
    val OkOnFocus = Color(0xFF1FA463)
    /** Text on a green-tinted chip (a supported audio format, board 28). */
    val OkChipText = Color(0xFFBDF0D4)
    val Warn = Color(0xFFF5A623)
    val WarnText = Color(0xFFF5C26B)

    /** The favourite star, on dark rows and on a focused (white) row. */
    val Favorite = Color(0xFFF5C26B)
    val FavoriteOnFocus = Color(0xFFC98A00)

    /** Behind a video preview while it has no picture yet. */
    val VideoWell = Color(0xFF141416)
    /** Behind a Multiview tile while it has no picture yet. */
    val VideoTile = Color(0xFF111113)

    /** The Multiview toolbar's fill over the tiles (board 12). */
    val ToolbarFill = Color(0xDB18181C)
    /** The soft light behind Who's Watching (board 05). */
    val Glow = Color(0xFF17171A)

    /** The two layers peeking above a series folder in Recordings (board 17): the far one, then the near one. */
    val StackFar = Color(0xFF222226)
    val StackNear = Color(0xFF323238)

    /** The cut-out around the front dot of the series-rule icon, in its tinted square's colour. */
    val RuleDotRing = Color(0xFF2B1918)

    /** A sheet's glassy fill (board 10). */
    val SheetFill = Color(0xD11E1E21)
    /** The sheet's fill as it reads over the darkened screen, for a ring drawn in it. */
    val SheetSolid = Color(0xFF2A2A2D)

    /** Colour-key hint squares only. */
    val KeyRed = Color(0xFFE5484D)
    val KeyGreen = Color(0xFF3DD68C)
    val KeyYellow = Color(0xFFFFD60A)
    val KeyBlue = Color(0xFF3B82F6)

    /** Graphite tiles (the Home dock, All Apps, See All): a soft top-to-bottom gradient. */
    val GraphiteTop = Color(0xFF242428)
    val GraphiteBottom = Color(0xFF1B1B1F)
    val GraphiteFocusedTop = Color(0xFF3A3A40)
    val GraphiteFocusedBottom = Color(0xFF28282D)

    /** The boards' `rgba(255,255,255,a)`: tracks, hairlines, translucent fills. */
    fun white(alpha: Float): Color = Color.White.copy(alpha = alpha)

    /** The boards' `rgba(0,0,0,a)`: scrims and badge plates on imagery. */
    fun black(alpha: Float): Color = Color.Black.copy(alpha = alpha)

    /** The boards' `rgba(11,11,13,a)`: scrims that fade into the background. */
    fun bg(alpha: Float): Color = Bg.copy(alpha = alpha)

    /** Accent options (Settings › Appearance). White is the default. */
    val AccentWhite = Text
    val AccentBlue = Color(0xFF4C8DFF)
    val AccentOrange = Color(0xFFFF9F45)
    val AccentGreen = Color(0xFF3DD68C)
}

/** The user's accent colour: progress bars and the guide's now-line. */
val LocalAccent = staticCompositionLocalOf { PlasmaColors.AccentWhite }
