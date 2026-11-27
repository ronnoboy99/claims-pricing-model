package com.flick.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flick.app.haptics.LocalHaptics
import com.flick.app.ui.theme.FIcons
import com.flick.app.ui.theme.FlickColors
import com.flick.app.ui.theme.FlickType
import com.flick.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class ChipItem(val key: String, val label: String, val dot: Color? = null)

/** Folder chips with a lavender pill that slides to the selected chip. Key "add" is the + button. */
@Composable
fun ChipRow(
    items: List<ChipItem>,
    selectedKey: String,
    onSelect: (ChipItem) -> Unit,
    onLongPress: (ChipItem) -> Unit,
    modifier: Modifier = Modifier,
    trashBump: () -> Float = { 1f },
    onTrashCoords: (LayoutCoordinates) -> Unit = {},
) {
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    val sel = bounds[selectedKey]
    val x by animateFloatAsState(sel?.first ?: 0f, Motion.settle(), label = "pillX")
    val w by animateFloatAsState(sel?.second ?: 0f, Motion.settle(), label = "pillW")
    val density = LocalDensity.current

    Box(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        if (sel != null) {
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), 0) }
                    .width(with(density) { w.toDp() })
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(FlickColors.Lavender)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val textColor by animateColorAsState(
                    if (selected) FlickColors.OnAccent else FlickColors.TextPrimary.copy(alpha = 0.85f),
                    Motion.settle(), label = "chipText",
                )
                Box(
                    Modifier
                        .onGloballyPositioned {
                            bounds[item.key] = it.positionInParent().x to it.size.width.toFloat()
                            if (item.key == "trash") onTrashCoords(it)
                        }
                        .graphicsLayer {
                            if (item.key == "trash") {
                                val b = trashBump()
                                scaleX = b
                                scaleY = b
                            }
                        }
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.Transparent else FlickColors.Surface)
                        .pressable(onLongClick = { onLongPress(item) }) { onSelect(item) }
                        .padding(horizontal = if (item.key == "add") 12.dp else 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.dot != null) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(item.dot))
                            Spacer(Modifier.size(8.dp))
                        }
                        if (item.key == "add") {
                            Icon(FIcons.Plus, "New folder", tint = textColor, modifier = Modifier.size(16.dp))
                        } else {
                            Text(item.label, style = FlickType.Label, color = textColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        if (focused) FlickColors.Lavender else Color.Transparent, Motion.settle(), label = "searchBorder",
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(FlickColors.Surface)
            .border(1.5.dp, border, CircleShape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FIcons.Search, null, tint = FlickColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = FlickType.Body.copy(fontSize = 15.sp),
                cursorBrush = SolidColor(FlickColors.Lavender),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text("Search notes", style = FlickType.Body.copy(fontSize = 15.sp), color = FlickColors.TextSecondary)
                        }
                        inner()
                    }
                },
            )
        }
        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = scaleIn(Motion.pop()) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).pressable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(FIcons.Close, "Clear", tint = FlickColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Center button. Tap = [onClick]. Press and hold opens the radial menu; keep the finger down and
 * slide to an option, then release to pick it. [onHoldMove] gets the finger position relative to
 * the button's center, in pixels.
 */
@Composable
fun FlickFab(
    onClick: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldMove: (Offset) -> Unit,
    onHoldRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, Motion.pop(), label = "fabPress")
    val latestClick by rememberUpdatedState(onClick)
    val latestStart by rememberUpdatedState(onHoldStart)
    val latestMove by rememberUpdatedState(onHoldMove)
    val latestRelease by rememberUpdatedState(onHoldRelease)

    Box(
        modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                drawCircle(FlickColors.Lavender.copy(alpha = 0.22f), radius = size.minDimension / 2 + 10.dp.toPx())
            }
            .clip(CircleShape)
            .background(FlickColors.Lavender)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    var up: PointerInputChange? = null
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        up = waitForUpOrCancellation()
                        Unit
                    } == null
                    if (!held) {
                        pressed = false
                        if (up != null) latestClick()
                    } else {
                        latestStart()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                change.consume()
                                break
                            }
                            latestMove(change.position - center)
                            change.consume()
                        }
                        pressed = false
                        latestRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(FIcons.Plus, "New note", tint = FlickColors.OnAccent, modifier = Modifier.size(26.dp))
    }
}

data class RadialItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Offsets (in dp, x right / y down) of radial options relative to the center button. */
fun radialOffsets(count: Int, radiusDp: Float = 96f): List<Offset> {
    val angles = when (count) {
        1 -> listOf(90.0)
        2 -> listOf(125.0, 55.0)
        else -> listOf(150.0, 90.0, 30.0)
    }
    return angles.take(count).map { a ->
        val r = Math.toRadians(a)
        Offset((radiusDp * cos(r)).toFloat(), (-radiusDp * sin(r)).toFloat())
    }
}

/**
 * Fans out around the center button. Place inside a Box aligned to the bottom center.
 * [hoverIndex] is the option the finger is currently over (slide-and-release), or -1.
 */
@Composable
fun RadialMenu(
    items: List<RadialItem>,
    hoverIndex: Int = -1,
    radiusDp: Float = 96f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val offsets = radialOffsets(items.size, radiusDp)
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        items.forEachIndexed { i, item ->
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(i * Motion.StaggerMs)
                shown = true
            }
            val s by animateFloatAsState(if (shown) 1f else 0f, Motion.pop(), label = "radial")
            val hovered = i == hoverIndex
            val hoverScale by animateFloatAsState(if (hovered) 1.22f else 1f, Motion.pop(), label = "radialHover")
            val bg by animateColorAsState(if (hovered) FlickColors.Lavender else FlickColors.SurfaceHigh, Motion.settle(), label = "radialBg")
            val fg by animateColorAsState(if (hovered) FlickColors.OnAccent else FlickColors.Lavender, Motion.settle(), label = "radialFg")
            val o = offsets[i]
            androidx.compose.foundation.layout.Column(
                Modifier
                    .offset { IntOffset((o.x * s * density.density).roundToInt(), (o.y * s * density.density).roundToInt()) }
                    .graphicsLayer {
                        scaleX = s * hoverScale
                        scaleY = s * hoverScale
                        alpha = s.coerceIn(0f, 1f)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .border(1.dp, FlickColors.Outline, CircleShape)
                        .pressable { item.onClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, item.label, tint = fg, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(item.label, style = FlickType.Caption, color = FlickColors.TextPrimary)
            }
        }
    }
}

@Composable
fun StatusPill(text: String, actionLabel: String?, onAction: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(Color(0xFF1F2A1B))
            .border(1.dp, FlickColors.Mint.copy(alpha = 0.35f), CircleShape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FIcons.Check, null, tint = FlickColors.Mint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Text(text, style = FlickType.Label, color = FlickColors.TextPrimary)
        Spacer(Modifier.size(12.dp))
        if (actionLabel != null && onAction != null) {
            val haptics = LocalHaptics.current
            Box(
                Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .pressable(haptic = false) {
                        haptics.click()
                        onAction()
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionLabel, style = FlickType.Label.copy(fontSize = 14.sp), color = FlickColors.Mint)
            }
        } else {
            Spacer(Modifier.size(10.dp))
        }
    }
}
