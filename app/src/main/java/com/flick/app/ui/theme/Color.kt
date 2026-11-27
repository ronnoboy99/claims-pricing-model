package com.flick.app.ui.theme

import androidx.compose.ui.graphics.Color

object FlickColors {
    val Bg = Color(0xFF0F0F0F)
    val Surface = Color(0xFF1A1A1A)
    val SurfaceHigh = Color(0xFF242424)
    val Outline = Color(0xFF2E2E2E)
    val TextPrimary = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFF9A9A9A)
    val Lavender = Color(0xFFB9A8E8)
    val Mint = Color(0xFFC8F0B0)
    val Danger = Color(0xFFF2837A)
    val OnAccent = Color(0xFF1A1530)
    val OnAccentSoft = Color(0xFF3A3150)
    val Peach = Color(0xFFF4C7A1)
    val Sky = Color(0xFFA9D4F5)
    val Rose = Color(0xFFF0B4C8)
}

object Accents {
    val all: List<Pair<String, Color>> = listOf(
        "lavender" to FlickColors.Lavender,
        "mint" to FlickColors.Mint,
        "peach" to FlickColors.Peach,
        "sky" to FlickColors.Sky,
        "rose" to FlickColors.Rose,
    )

    fun color(key: String?): Color = all.firstOrNull { it.first == key }?.second ?: FlickColors.Lavender
}
