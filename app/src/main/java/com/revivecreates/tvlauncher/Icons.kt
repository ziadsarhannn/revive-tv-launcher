package com.revivecreates.tvlauncher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Thin line icons drawn from 24 x 24 path data, tinted where they are used. */
object Icons {
    private fun line(d: String, width: Float = 1.9f): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    val ScreenSaver = line("M12 3v8 M6.3 6.3a8 8 0 1 0 11.4 0")
    val Wifi = line("M2 8.5a15 15 0 0 1 20 0 M5 12a10 10 0 0 1 14 0 M8.5 15.5a5 5 0 0 1 7 0 M12 19h.01")
    val Speaker = line("M11 5L6 9H3v6h3l5 4z M15.5 8.5a5 5 0 0 1 0 7 M18.5 5.5a9 9 0 0 1 0 13")
    val Display = line("M4.5 4h15a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-15a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M8 21h8 M12 17v4")
    val Bluetooth = line("M7 7l10 10-5 4V3l5 4L7 17")
    val Search = line("M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0 M20 20l-4-4")
    val Settings = line("M4 6h10 M18 6h2 M4 12h4 M12 12h8 M4 18h12 M14 6a2 2 0 1 0 4 0a2 2 0 1 0-4 0 M8 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0 M16 18a2 2 0 1 0 4 0a2 2 0 1 0-4 0")
    val Sun = line("M7 12a5 5 0 1 0 10 0a5 5 0 1 0-10 0 M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4")
    val Cloud = line("M5 8a3 3 0 1 0 6 0a3 3 0 1 0-6 0 M8 2v1.5 M2 8h1.5 M3.8 3.8l1 1 M12.2 3.8l-1 1 M7 20h10a4 4 0 0 0 0-8 5 5 0 0 0-9.6 1.6A3.2 3.2 0 0 0 7 20z")
    val Home = line("M3 11l9-7 9 7 M5 10v10h5v-6h4v6h5V10")
}
