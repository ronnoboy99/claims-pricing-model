package com.flick.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (!filled) SolidColor(Color.Black) else null,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object FIcons {
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Search = icon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14", "M20 20l-3.5-3.5")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Pin = icon("pin", "M9 3h6l-1 6 3 3v2h-4v7h-2v-7H7v-2l3-3z", filled = true)
    val Share = icon("share", "M12 15V4M8 8l4-4 4 4M5 13v6h14v-6")
    val More = icon(
        "more",
        "M4.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0",
        "M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0",
        "M16.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0",
        filled = true,
    )
    val Lock = icon("lock", "M6 11h12v9H6z", "M8 11V8a4 4 0 0 1 8 0v3")
    val Image = icon("image", "M4 5h16v14H4z", "M4 17l5-5 4 4 3-3 4 4")
    val IndentIn = icon("indent_in", "M5 12h14M13 6l6 6-6 6")
    val IndentOut = icon("indent_out", "M19 12H5M11 6l-6 6 6 6")
    val ChevronUp = icon("chevron_up", "M6 15l6-6 6 6")
    val ChevronDown = icon("chevron_down", "M6 9l6 6 6-6")
    val Folder = icon("folder", "M3 7h6l2 2h10v10H3z")
    val Trash = icon("trash", "M5 7h14M10 4h4M7 7l1 13h8l1-13")
    val Restore = icon("restore", "M4 12a8 8 0 1 0 3-6.2", "M4 4v5h5")
    val Quote = icon("quote", "M6 8v8M10 8v8")
    val Bullet = icon("bullet", "M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4", filled = true)
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
    val Redo = icon("redo", "M15 14l5-5-5-5", "M20 9H10a6 6 0 0 0 0 12h3")
    val Settings = icon("settings", "M4 7h10M18 7h2M4 17h2M10 17h10", "M16 5v4M8 15v4")
}
