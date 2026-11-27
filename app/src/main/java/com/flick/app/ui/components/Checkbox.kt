package com.flick.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flick.app.haptics.LocalHaptics
import com.flick.app.ui.theme.Motion

/**
 * Checkbox with a pop, a fill and a check mark that draws itself.
 * Touch area is 34x24dp so it lines up with a 24dp text line.
 */
@Composable
fun FlickCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    fill: Color,
    check: Color,
    outline: Color,
    modifier: Modifier = Modifier,
    boxSize: Dp = 20.dp,
) {
    val progress by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 380f),
        label = "checkProgress",
    )
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) {
            first = false
        } else {
            scale.snapTo(0.8f)
            scale.animateTo(1f, Motion.pop())
        }
    }
    val haptics = LocalHaptics.current
    Box(
        modifier
            .width(34.dp)
            .height(24.dp)
            .pressable(haptic = false) {
                haptics.tick()
                onToggle()
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(
            Modifier
                .size(boxSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        ) {
            val w = size.width
            val r = CornerRadius(w * 0.32f)
            if (progress < 1f) {
                val sw = 2.dp.toPx()
                drawRoundRect(
                    outline.copy(alpha = 1f - progress),
                    topLeft = Offset(sw / 2, sw / 2),
                    size = Size(w - sw, w - sw),
                    cornerRadius = r,
                    style = Stroke(sw),
                )
            }
            if (progress > 0f) {
                drawRoundRect(fill.copy(alpha = progress), size = size, cornerRadius = r)
                val path = Path().apply {
                    moveTo(w * 0.25f, w * 0.52f)
                    lineTo(w * 0.43f, w * 0.70f)
                    lineTo(w * 0.77f, w * 0.31f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val seg = Path()
                measure.getSegment(0f, measure.length * progress, seg, true)
                drawPath(seg, check, style = Stroke(w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
