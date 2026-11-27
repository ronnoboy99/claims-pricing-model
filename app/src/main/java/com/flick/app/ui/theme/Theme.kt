package com.flick.app.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.flick.app.haptics.HapticEngine
import com.flick.app.haptics.LocalHaptics

@Composable
fun FlickTheme(haptics: HapticEngine, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = FlickColors.Lavender,
        onPrimary = FlickColors.OnAccent,
        background = FlickColors.Bg,
        onBackground = FlickColors.TextPrimary,
        surface = FlickColors.Surface,
        onSurface = FlickColors.TextPrimary,
        surfaceVariant = FlickColors.SurfaceHigh,
        onSurfaceVariant = FlickColors.TextSecondary,
        outline = FlickColors.Outline,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalHaptics provides haptics,
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = FlickColors.Lavender,
                backgroundColor = FlickColors.Lavender.copy(alpha = 0.3f),
            ),
            content = content,
        )
    }
}
