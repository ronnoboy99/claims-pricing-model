package com.flick.app.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.BoundsTransform
import androidx.compose.ui.geometry.Rect
import com.flick.app.data.BlockEntity
import com.flick.app.data.BlockType
import com.flick.app.data.NoteEntity
import com.flick.app.haptics.LocalHaptics
import com.flick.app.ui.components.FlickCheckbox
import com.flick.app.ui.components.pressable
import com.flick.app.ui.theme.FIcons
import com.flick.app.ui.theme.FlickColors
import com.flick.app.ui.theme.FlickType
import com.flick.app.ui.theme.Motion
import com.flick.app.util.relativeDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Deck(val key: String, val label: String, val accent: Color, val notes: List<NoteEntity>)

/**
 * Flick mode: horizontal flick = notes in the folder, vertical flick = other folders.
 * Position is an Animatable in pixels, released with spline decay, then snapped with a spring.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FlickDeck(
    decks: List<Deck>,
    folderNameOf: (NoteEntity) -> String?,
    blocksFor: (String) -> Flow<List<BlockEntity>>,
    onOpen: (NoteEntity) -> Unit,
    onLongPress: (NoteEntity) -> Unit,
    onToggleBlock: (String) -> Unit,
    onCreate: () -> Unit,
    sharedScope: SharedTransitionScope,
    animScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    if (decks.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing to flick through yet", style = FlickType.CardTitle, color = FlickColors.TextPrimary)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.height(48.dp).clip(CircleShape).background(FlickColors.Lavender)
                        .pressable { onCreate() }.padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Write something", style = FlickType.Label, color = FlickColors.OnAccent) }
            }
        }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current

    var deckIdx by rememberSaveable { mutableIntStateOf(0) }
    val di = deckIdx.coerceIn(0, decks.lastIndex)
    val deck = decks[di]
    val last = (deck.notes.size - 1).coerceAtLeast(0)

    val stepPx = with(density) { 268.dp.toPx() }
    val positions = remember { mutableStateMapOf<String, Int>() }
    val pos = remember(deck.key) { Animatable(((positions[deck.key] ?: 0).coerceIn(0, last)) * stepPx) }
    val rowOffset = remember { Animatable(0f) }
    // Opacity of the side cards only. Drops to 0 when the folder changes, then fades back in.
    val sideAlpha = remember { Animatable(1f) }
    val fadeJob = remember { arrayOfNulls<Job>(1) }

    /** Side cards vanish as soon as a swipe starts (no glitchy cards sliding around)... */
    fun hideSides() {
        fadeJob[0]?.cancel()
        fadeJob[0] = scope.launch { sideAlpha.animateTo(0f, tween(90)) }
    }

    /** ...and fade back in shortly after the deck has settled. */
    fun showSidesSoon() {
        fadeJob[0]?.cancel()
        fadeJob[0] = scope.launch {
            delay(420)
            sideAlpha.animateTo(1f, tween(260))
        }
    }
    var heightPx by remember { mutableIntStateOf(1) }
    var idle by remember { mutableStateOf(true) }

    val center by remember(deck.key, last) {
        derivedStateOf { (pos.value / stepPx).roundToInt().coerceIn(0, last) }
    }

    LaunchedEffect(deck.key) {
        snapshotFlow { (pos.value / stepPx).roundToInt() }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptics.lowTick() }
    }
    LaunchedEffect(center, deck.key) { positions[deck.key] = center }

    suspend fun settleHorizontal(velocity: Float) {
        val start = pos.value / stepPx
        if (abs(velocity) < 1600f) {
            val target = when {
                velocity < -250f -> ceil(start - 0.02f).toInt()
                velocity > 250f -> floor(start + 0.02f).toInt()
                else -> start.roundToInt()
            }.coerceIn(0, last)
            pos.animateTo(target * stepPx, Motion.settle())
        } else {
            pos.updateBounds(0f, last * stepPx)
            pos.animateDecay(-velocity, splineBasedDecay(density))
            pos.updateBounds(null, null)
            val target = (pos.value / stepPx).roundToInt().coerceIn(0, last)
            pos.animateTo(target * stepPx, Motion.settle())
        }
        haptics.click()
        showSidesSoon()
    }

    suspend fun settleVertical(velocity: Float) {
        val h = heightPx.toFloat()
        val off = rowOffset.value
        val goNext = (off < -h * 0.12f || velocity < -1100f) && di < decks.lastIndex
        val goPrev = (off > h * 0.12f || velocity > 1100f) && di > 0
        when {
            goNext -> {
                rowOffset.animateTo(-h, tween(160))
                fadeJob[0]?.cancel()
                sideAlpha.snapTo(0f)
                deckIdx = di + 1
                rowOffset.snapTo(h)
                haptics.rise()
                showSidesSoon()
                rowOffset.animateTo(0f, Motion.expand())
            }
            goPrev -> {
                rowOffset.animateTo(h, tween(160))
                fadeJob[0]?.cancel()
                sideAlpha.snapTo(0f)
                deckIdx = di - 1
                rowOffset.snapTo(-h)
                haptics.rise()
                showSidesSoon()
                rowOffset.animateTo(0f, Motion.expand())
            }
            else -> {
                rowOffset.animateTo(0f, Motion.settle())
                showSidesSoon()
            }
        }
    }

    val hDrag = rememberDraggableState { d ->
        scope.launch { pos.snapTo((pos.value - d).coerceIn(-0.25f * stepPx, (last + 0.25f) * stepPx)) }
    }
    val vDrag = rememberDraggableState { d ->
        scope.launch { rowOffset.snapTo(rowOffset.value + d * 0.7f) }
    }

    val dotsAlpha by animateFloatAsState(if (idle) 0.35f else 1f, tween(300), label = "dots")

    Column(modifier.fillMaxSize()) {
        // folder above
        Row(
            Modifier.fillMaxWidth().height(32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (di > 0) {
                Icon(FIcons.ChevronUp, null, tint = FlickColors.TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(decks[di - 1].label, style = FlickType.Small, color = FlickColors.TextSecondary)
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { heightPx = it.height }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = hDrag,
                    onDragStarted = { idle = false; hideSides(); pos.stop() },
                    onDragStopped = { v -> scope.launch { settleHorizontal(v); idle = true } },
                )
                .draggable(
                    orientation = Orientation.Vertical,
                    state = vDrag,
                    onDragStarted = { idle = false; hideSides() },
                    onDragStopped = { v -> scope.launch { settleVertical(v); idle = true } },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = rowOffset.value
                        alpha = 1f - (abs(rowOffset.value) / heightPx.toFloat()).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                val from = max(0, center - 2)
                val to = min(last, center + 2)
                for (i in from..to) {
                    val note = deck.notes[i]
                    key(note.id) {
                        val isCenter = i == center
                        val cardModifier = Modifier
                            .zIndex(10f - abs(i - center))
                            .graphicsLayer {
                                val d = i - pos.value / stepPx
                                val ad = min(abs(d), 2f)
                                translationX = d * stepPx
                                val s = 1f - 0.07f * ad
                                scaleX = s
                                scaleY = s
                                rotationZ = d.coerceIn(-2f, 2f) * 4f
                                // center card is always fully visible, side cards follow sideAlpha
                                val near = 1f - min(abs(d), 1f)
                                alpha = (1f - 0.3f * ad) * (sideAlpha.value + (1f - sideAlpha.value) * near)
                            }
                            .width(300.dp)
                            .fillMaxHeight(0.94f)
                            .heightIn(max = 500.dp)
                        val sharedModifier = if (isCenter) {
                            with(sharedScope) {
                                Modifier.sharedBounds(
                                    rememberSharedContentState(key = "note-${note.id}"),
                                    animScope,
                                    boundsTransform = BoundsTransform { _, _ -> Motion.expand<Rect>() },
                                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(28.dp)),
                                )
                            }
                        } else Modifier
                        DeckCard(
                            note = note,
                            accent = deck.accent,
                            folderName = folderNameOf(note),
                            live = isCenter,
                            blocksFor = blocksFor,
                            onToggleBlock = onToggleBlock,
                            modifier = cardModifier
                                .then(sharedModifier)
                                .pressable(onLongClick = { onLongPress(note) }) {
                                    if (isCenter) onOpen(note)
                                },
                        )
                    }
                }
            }
        }

        // dot strip + folder below
        Column(
            Modifier.fillMaxWidth().height(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val n = deck.notes.size
            val windowStart = (center - 3).coerceAtLeast(0).coerceAtMost((n - 7).coerceAtLeast(0))
            val windowEnd = min(n - 1, windowStart + 6)
            Row(
                Modifier.graphicsLayer { alpha = dotsAlpha },
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in windowStart..windowEnd) {
                    val active = i == center
                    val w by animateFloatAsState(if (active) 22f else 6f, Motion.settle(), label = "dotW")
                    Box(
                        Modifier.width(w.dp).height(6.dp).clip(CircleShape)
                            .background(if (active) deck.accent else Color(0xFF4A4A4A))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (di < decks.lastIndex) {
                    Text(decks[di + 1].label, style = FlickType.Small, color = FlickColors.TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Icon(FIcons.ChevronDown, null, tint = FlickColors.TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun DeckCard(
    note: NoteEntity,
    accent: Color,
    folderName: String?,
    live: Boolean,
    blocksFor: (String) -> Flow<List<BlockEntity>>,
    onToggleBlock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(accent)
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(18.dp)) {
            if (folderName != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(FlickColors.OnAccentSoft))
                Spacer(Modifier.width(8.dp))
                Text(folderName, style = FlickType.Label, color = FlickColors.OnAccentSoft)
            }
            if (note.pinned) {
                Spacer(Modifier.weight(1f))
                Icon(FIcons.Pin, null, tint = FlickColors.OnAccent, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (note.title.isBlank()) "Untitled" else note.title,
            style = FlickType.DeckTitle,
            color = FlickColors.OnAccent,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                note.locked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(FIcons.Lock, null, tint = FlickColors.OnAccentSoft, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Locked note", style = FlickType.Body, color = FlickColors.OnAccentSoft)
                }
                live -> LiveBlocks(note.id, blocksFor, onToggleBlock)
                else -> Text(
                    note.previewText,
                    style = FlickType.Body,
                    color = FlickColors.OnAccentSoft,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(relativeDate(note.updatedAt), style = FlickType.Caption, color = FlickColors.OnAccentSoft)
            if (note.checklistTotal > 0 && !note.locked) {
                Text("${note.checklistDone} of ${note.checklistTotal} done", style = FlickType.Caption, color = FlickColors.OnAccentSoft)
            }
        }
    }
}

@Composable
private fun LiveBlocks(
    noteId: String,
    blocksFor: (String) -> Flow<List<BlockEntity>>,
    onToggleBlock: (String) -> Unit,
) {
    val blocks by remember(noteId) { blocksFor(noteId) }.collectAsState(initial = emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.filter { BlockType.from(it.type).isText && (it.text.isNotBlank()) }.take(8).forEach { b ->
            when (BlockType.from(b.type)) {
                BlockType.CHECKLIST -> Row(verticalAlignment = Alignment.Top) {
                    FlickCheckbox(
                        checked = b.checked,
                        onToggle = { onToggleBlock(b.id) },
                        fill = FlickColors.OnAccent,
                        check = Color.White,
                        outline = FlickColors.OnAccent,
                    )
                    Text(
                        b.text,
                        style = FlickType.Body,
                        color = if (b.checked) FlickColors.OnAccentSoft else FlickColors.OnAccent,
                        textDecoration = if (b.checked) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BlockType.BULLET -> Text("•  ${b.text}", style = FlickType.Body, color = FlickColors.OnAccent, maxLines = 2, overflow = TextOverflow.Ellipsis)
                BlockType.HEADING -> Text(b.text, style = FlickType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = FlickColors.OnAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else -> Text(b.text, style = FlickType.Body, color = FlickColors.OnAccentSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
