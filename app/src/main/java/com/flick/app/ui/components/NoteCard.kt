package com.flick.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.flick.app.data.FolderEntity
import com.flick.app.data.NoteEntity
import com.flick.app.ui.theme.Accents
import com.flick.app.ui.theme.FIcons
import com.flick.app.ui.theme.FlickColors
import com.flick.app.ui.theme.FlickType
import com.flick.app.ui.theme.Motion
import com.flick.app.ui.theme.Sans
import com.flick.app.util.relativeDate
import kotlinx.coroutines.delay

fun highlight(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    val lower = text.lowercase()
    // Match the start of words, like the full-text search does, and merge overlapping hits.
    val ranges = mutableListOf<IntRange>()
    for (t in tokens) {
        var i = lower.indexOf(t)
        while (i >= 0) {
            if (i == 0 || !lower[i - 1].isLetterOrDigit()) ranges += i until (i + t.length)
            i = lower.indexOf(t, i + 1)
        }
    }
    val merged = mutableListOf<IntRange>()
    for (r in ranges.sortedBy { it.first }) {
        val last = merged.lastOrNull()
        if (last != null && r.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
        else merged += r
    }
    var pos = 0
    for (r in merged) {
        if (r.first > text.length) break
        val end = minOf(r.last + 1, text.length)
        append(text.substring(pos, r.first))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(r.first, end))
        }
        pos = end
    }
    append(text.substring(pos))
}

@Composable
fun ProgressRing(done: Int, total: Int, track: Color, progress: Color, modifier: Modifier = Modifier) {
    val target = if (total == 0) 0f else done.toFloat() / total
    val p by animateFloatAsState(target, Motion.settle(), label = "ring")
    Canvas(modifier.size(22.dp)) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = Stroke(stroke))
        drawArc(
            progress, -90f, 360f * p, false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Card used on Home grid/list. Pinned cards get a solid accent fill. */
@Composable
fun NoteCard(
    note: NoteEntity,
    folder: FolderEntity?,
    query: String,
    inTrash: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (folder != null) Accents.color(folder.colorKey) else FlickColors.Lavender
    val filled = note.pinned && !inTrash
    val container = if (filled) accent else FlickColors.Surface
    val primary = if (filled) FlickColors.OnAccent else FlickColors.TextPrimary
    val secondary = if (filled) FlickColors.OnAccentSoft else FlickColors.TextSecondary
    val hl = if (filled) FlickColors.OnAccent else FlickColors.Lavender

    val pinScale by animateFloatAsState(if (note.pinned) 1f else 0f, Motion.pop(), label = "pin")

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (inTrash) 0.7f else 1f }
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .padding(16.dp),
    ) {
        if (folder != null && !filled) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.size(6.dp))
                Text(folder.name, style = FlickType.Caption, color = secondary)
            }
        }
        Text(
            text = if (note.title.isBlank()) AnnotatedString("Untitled") else highlight(note.title, query, hl),
            style = FlickType.CardTitle,
            color = if (note.title.isBlank()) secondary else primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        if (note.locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(FIcons.Lock, null, tint = secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Locked note", style = FlickType.Small, color = secondary)
            }
        } else if (note.previewText.isNotBlank()) {
            Text(
                text = highlight(note.previewText, query, hl),
                style = FlickType.Small,
                color = secondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val label = if (inTrash && note.trashedAt != null) daysLeft(note.trashedAt) else relativeDate(note.updatedAt)
            Text(label, style = FlickType.Caption, color = secondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (note.pinned && !inTrash) {
                    Icon(
                        FIcons.Pin, null, tint = primary,
                        modifier = Modifier.size(14.dp).graphicsLayer { scaleX = pinScale; scaleY = pinScale },
                    )
                }
                if (note.checklistTotal > 0 && !note.locked) {
                    ProgressRing(
                        note.checklistDone, note.checklistTotal,
                        track = if (filled) FlickColors.OnAccent.copy(alpha = 0.2f) else FlickColors.Outline,
                        progress = if (filled) FlickColors.OnAccent else FlickColors.Mint,
                    )
                }
            }
        }
    }
}

private fun daysLeft(trashedAt: Long): String {
    val left = 30 - ((System.currentTimeMillis() - trashedAt) / (24L * 60 * 60 * 1000)).toInt()
    return if (left <= 1) "Deletes today" else "$left days left"
}

/** Fades and slides a card in once per id, staggered. */
fun Modifier.staggerIn(id: String, index: Int, played: MutableSet<String>): Modifier = composed {
    val already = remember { played.contains(id) }
    val a = remember { Animatable(if (already) 1f else 0f) }
    LaunchedEffect(id) {
        if (!already) {
            played.add(id)
            delay(minOf(index, 8) * Motion.StaggerMs)
            a.animateTo(1f, Motion.settle())
        }
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 24.dp.toPx()
    }
}
