package com.flick.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flick.app.data.FolderEntity
import com.flick.app.data.NoteEntity
import com.flick.app.data.SortMode
import com.flick.app.haptics.LocalHaptics
import com.flick.app.ui.theme.Accents
import com.flick.app.ui.theme.FIcons
import com.flick.app.ui.theme.FlickColors
import com.flick.app.ui.theme.FlickType
import com.flick.app.ui.theme.Motion
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlickSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FlickColors.Surface,
        contentColor = FlickColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(FlickColors.Outline)
            )
        },
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            content()
        }
    }
}

@Composable
fun SheetRow(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    tint: Color = FlickColors.TextPrimary,
    trailing: @Composable (() -> Unit)? = null,
    leadingDot: Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .pressable { onClick() }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        } else if (leadingDot != null) {
            Box(Modifier.padding(start = 6.dp).size(12.dp).clip(CircleShape).background(leadingDot))
        }
        Spacer(Modifier.size(16.dp))
        Text(label, style = FlickType.Body, color = tint, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun NoteOptionsSheet(
    note: NoteEntity,
    inTrash: Boolean,
    folders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onMove: (String?) -> Unit,
    onLock: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    var moving by remember { mutableStateOf(false) }
    FlickSheet(onDismiss) {
        if (!moving) {
            Text(
                if (note.title.isBlank()) "Untitled" else note.title,
                style = FlickType.CardTitle, color = FlickColors.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
            )
            if (inTrash) {
                SheetRow(FIcons.Restore, "Restore", onRestore)
                SheetRow(FIcons.Trash, "Delete forever", onDeleteForever, tint = FlickColors.Danger)
            } else {
                SheetRow(FIcons.Pin, if (note.pinned) "Unpin" else "Pin", onPin)
                SheetRow(FIcons.Folder, "Move to folder", { moving = true })
                SheetRow(FIcons.Lock, if (note.locked) "Unlock" else "Lock", onLock)
                SheetRow(FIcons.Share, "Share", onShare)
                SheetRow(FIcons.Trash, "Delete", onDelete, tint = FlickColors.Danger)
            }
        } else {
            Text("Move to folder", style = FlickType.CardTitle, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            SheetRow(
                null, "No folder", { onMove(null) }, leadingDot = FlickColors.Outline,
                trailing = { if (note.folderId == null) Icon(FIcons.Check, null, tint = FlickColors.Mint, modifier = Modifier.size(18.dp)) },
            )
            folders.forEach { f ->
                SheetRow(
                    null, f.name, { onMove(f.id) }, leadingDot = Accents.color(f.colorKey),
                    trailing = { if (note.folderId == f.id) Icon(FIcons.Check, null, tint = FlickColors.Mint, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
fun FolderSheet(
    folder: FolderEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onMoveFolder: ((Int) -> Unit)?,
) {
    var name by remember { mutableStateOf(folder?.name ?: "") }
    var color by remember { mutableStateOf(folder?.colorKey ?: "lavender") }
    FlickSheet(onDismiss) {
        Text(if (folder == null) "New folder" else "Edit folder", style = FlickType.CardTitle, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = FlickType.Body,
            cursorBrush = SolidColor(FlickColors.Lavender),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(FlickColors.SurfaceHigh)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (name.isEmpty()) Text("Folder name", style = FlickType.Body, color = FlickColors.TextSecondary)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(start = 4.dp)) {
            Accents.all.forEach { (key, c) ->
                val selected = key == color
                val ring by animateColorAsState(if (selected) FlickColors.TextPrimary else Color.Transparent, Motion.settle(), label = "ring")
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(2.dp, ring, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(c)
                        .pressable { color = key }
                )
            }
        }
        if (folder != null && onMoveFolder != null) {
            Spacer(Modifier.height(8.dp))
            SheetRow(FIcons.IndentOut, "Move left", { onMoveFolder(-1) })
            SheetRow(FIcons.IndentIn, "Move right", { onMoveFolder(1) })
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onDelete != null) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(FlickColors.SurfaceHigh)
                        .pressable { onDelete() },
                    contentAlignment = Alignment.Center,
                ) { Text("Delete", style = FlickType.Label, color = FlickColors.Danger) }
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(FlickColors.Lavender)
                    .pressable { onSave(name, color) },
                contentAlignment = Alignment.Center,
            ) { Text("Save", style = FlickType.Label, color = FlickColors.OnAccent) }
        }
    }
}

@Composable
fun FlickSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (checked) 20f else 0f, Motion.pop(), label = "switch")
    val d = androidx.compose.ui.platform.LocalDensity.current.density
    val bg by animateColorAsState(if (checked) FlickColors.Lavender else FlickColors.SurfaceHigh, Motion.settle(), label = "switchBg")
    Box(
        Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset((x * d).roundToInt(), 0) }
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) FlickColors.OnAccent else FlickColors.TextSecondary)
        )
    }
}

@Composable
fun SettingsSheet(
    sort: SortMode,
    haptics: Boolean,
    onSort: (SortMode) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onExportAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val h = LocalHaptics.current
    FlickSheet(onDismiss) {
        Text("Settings", style = FlickType.CardTitle, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
        Text("Sort notes by", style = FlickType.Caption, color = FlickColors.TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(FlickColors.SurfaceHigh).padding(3.dp),
        ) {
            SortMode.entries.forEach { m ->
                val selected = m == sort
                val bg by animateColorAsState(if (selected) FlickColors.Lavender else Color.Transparent, Motion.settle(), label = "seg")
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .pressable { onSort(m) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (m) { SortMode.RECENT -> "Recent"; SortMode.CREATED -> "Created"; SortMode.TITLE -> "Title" },
                        style = FlickType.Label,
                        color = if (selected) FlickColors.OnAccent else FlickColors.TextPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SheetRow(null, "Haptics", { onHaptics(!haptics); if (!haptics) h.click() }, trailing = { FlickSwitch(haptics) { onHaptics(it) } })
        SheetRow(FIcons.Share, "Export all notes", onExportAll)
    }
}
