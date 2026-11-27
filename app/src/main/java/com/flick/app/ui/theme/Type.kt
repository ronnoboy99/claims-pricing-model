package com.flick.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.flick.app.R

val Serif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val Sans = FontFamily(
    Font(R.font.inter_tight_regular, FontWeight.Normal),
    Font(R.font.inter_tight_medium, FontWeight.Medium),
    Font(R.font.inter_tight_semibold, FontWeight.SemiBold),
)

object FlickType {
    val Display = TextStyle(fontFamily = Serif, fontSize = 40.sp, lineHeight = 44.sp, color = FlickColors.TextPrimary)
    val Title = TextStyle(fontFamily = Serif, fontSize = 32.sp, lineHeight = 38.sp, color = FlickColors.TextPrimary)
    val DeckTitle = TextStyle(fontFamily = Serif, fontSize = 38.sp, lineHeight = 42.sp)
    val CardTitle = TextStyle(fontFamily = Serif, fontSize = 22.sp, lineHeight = 26.sp)
    val Body = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp, color = FlickColors.TextPrimary)
    val Heading = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp, color = FlickColors.TextPrimary)
    val Label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp)
    val Small = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp)
    val Overline = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp)
}
