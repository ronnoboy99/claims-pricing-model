@file:OptIn(ExperimentalFoundationApi::class)

package com.flick.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flick.app.haptics.LocalHaptics
import com.flick.app.ui.theme.FlickColors
import com.flick.app.ui.theme.Motion
import androidx.compose.ui.composed

/** Press-down scale with a springy release. Optional long click. */
fun Modifier.pressable(
    haptic: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.pop(), label = "press")
    val haptics = LocalHaptics.current
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick?.let { l ->
                {
                    haptics.click()
                    l()
                }
            },
            onClick = {
                if (haptic) haptics.tick()
                onClick()
            },
        )
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = FlickColors.Surface,
    tint: Color = FlickColors.TextPrimary,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
) {
    val bg by animateColorAsState(background, Motion.settle(), label = "iconBg")
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}
