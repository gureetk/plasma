// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The phone remote's icons, drawn from the same 24 × 24 paths as its web page (board 33), so
 * the apps and the page look the same. Each is tinted where it's shown.
 */
object RemoteIcons {
    val Up = stroked(2.4f, "m6 15 6-6 6 6")
    val Down = stroked(2.4f, "m6 9 6 6 6-6")
    val Left = stroked(2.4f, "m15 6-6 6 6 6")
    val Right = stroked(2.4f, "m9 6 6 6-6 6")
    val Back = stroked(2f, "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    val Home = stroked(2f, "M3.5 10.5 12 3.5l8.5 7", "M5.5 9v10.5h13V9")
    val Menu = stroked(2f, "M4 7h16M4 12h16M4 17h16")
    val Rewind = filled("M11 6.5v11L3.5 12z", "M20 6.5v11L12.5 12z")
    val FastForward = filled("M13 6.5v11l7.5-5.5z", "M4 6.5v11l7.5-5.5z")
    val Pause = filled("M7.2 4.5h1.6a1.2 1.2 0 0 1 1.2 1.2v12.6a1.2 1.2 0 0 1-1.2 1.2H7.2A1.2 1.2 0 0 1 6 18.3V5.7a1.2 1.2 0 0 1 1.2-1.2z", "M15.2 4.5h1.6a1.2 1.2 0 0 1 1.2 1.2v12.6a1.2 1.2 0 0 1-1.2 1.2h-1.6a1.2 1.2 0 0 1-1.2-1.2V5.7a1.2 1.2 0 0 1 1.2-1.2z")
    val Play = filled("M8 5.2v13.6a.8.8 0 0 0 1.2.7l10.6-6.8a.8.8 0 0 0 0-1.4L9.2 4.5A.8.8 0 0 0 8 5.2z")
    val TabRemote = stroked(1.9f, "M20.5 12a8.5 8.5 0 1 1-17 0 8.5 8.5 0 0 1 17 0z", "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M12 3.5v2M12 18.5v2M3.5 12h2M18.5 12h2")
    val TabChannels = stroked(2f, "M9 6h11", "M9 12h11", "M9 18h11", "M4.5 6h.01", "M4.5 12h.01", "M4.5 18h.01")
    val TabType = stroked(1.9f, "M4.7 6h14.6a2.2 2.2 0 0 1 2.2 2.2v7.6a2.2 2.2 0 0 1-2.2 2.2H4.7a2.2 2.2 0 0 1-2.2-2.2V8.2A2.2 2.2 0 0 1 4.7 6z", "M6.5 10h.01M10 10h.01M13.5 10h.01M17 10h.01", "M7.5 14h9")

    val Search = stroked(2.2f, "M4.5 11a6.5 6.5 0 1 0 13 0 6.5 6.5 0 1 0-13 0z", "m20 20-4.2-4.2")
    val ClearField = stroked(2f, "M3.5 12a8.5 8.5 0 1 0 17 0 8.5 8.5 0 1 0-17 0z", "m9 9 6 6M15 9l-6 6")
    val Speaker = stroked(2.2f, "M11 5 6 9H3v6h3l5 4z", "M15.5 8.5a5 5 0 0 1 0 7")
    val Keyboard = TabType

    private fun stroked(width: Float, vararg paths: String): ImageVector = icon {
        paths.forEach { path ->
            addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }

    private fun filled(vararg paths: String): ImageVector = icon {
        paths.forEach { path -> addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)) }
    }

    private fun icon(build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply(build).build()
}
