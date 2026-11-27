package com.flick.app.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.flick.app.data.BlockType
import com.flick.app.data.Fmt
import com.flick.app.haptics.LocalHaptics
import com.flick.app.ui.components.CircleIconButton
import com.flick.app.ui.components.FlickCheckbox
import com.flick.app.ui.components.NoteOptionsSheet
import com.flick.app.ui.components.pressable
import com.flick.app.ui.theme.FIcons
import com.flick.app.ui.theme.FlickColors
import com.flick.app.ui.theme.FlickType
import com.flick.app.ui.theme.Motion
import com.flick.app.util.BiometricGate
import com.flick.app.util.findActivity
import com.flick.app.util.relativeDate
import com.flick.app.util.shareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    noteId: String,
    sharedScope: SharedTransitionScope,
    animScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
) {
    val note by vm.note.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val titleFocus = remember { FocusRequester() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var showOptions by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addImage(uri)
    }

    fun leave() {
        vm.onLeave()
        onBack()
    }
    BackHandler { leave() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }

    fun share() {
        scope.launch { shareText(context, vm.title, vm.markdown()) }
    }

    fun toggleLock() {
        val n = note ?: return
        val act = context.findActivity() ?: return
        if (!BiometricGate.canAuthenticate(context)) {
            Toast.makeText(context, "Set up a screen lock to lock notes", Toast.LENGTH_SHORT).show()
            return
        }
        if (n.locked) BiometricGate.authenticate(act, "Unlock note") { vm.setLocked(false) }
        else vm.setLocked(true)
    }

    val items = remember(vm.blocks) { buildItems(vm.blocks) }

    // new empty note: put the caret in the title (or the first line for checklist quick-capture)
    LaunchedEffect(vm.loaded) {
        if (vm.loaded && vm.title.isEmpty() && vm.blocks.all { it.type.isText && it.text.isEmpty() }) {
            delay(320)
            val first = vm.blocks.firstOrNull()
            if (first != null && first.type != BlockType.PARAGRAPH) vm.focusFirst()
            else runCatching { titleFocus.requestFocus() }
        }
    }

    // focus requests coming from the view model (after Enter, image insert, tap below the text...)
    LaunchedEffect(vm.focusRequest) {
        val req = vm.focusRequest ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it is DocItem.TextRun && it.run.key == req.runKey }
        if (idx >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == idx + 2 }) {
            listState.animateScrollToItem(idx + 2)
        }
        repeat(8) {
            val fr = requesters[req.runKey]
            if (fr != null) {
                runCatching { fr.requestFocus() }
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    with(sharedScope) {
        Box(
            Modifier
                .fillMaxSize()
                .sharedBounds(
                    rememberSharedContentState(key = "note-$noteId"),
                    animScope,
                    boundsTransform = BoundsTransform { _, _ -> Motion.expand<Rect>() },
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(24.dp)),
                )
                .background(FlickColors.Bg),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // top bar
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(FIcons.Back, "Back", { leave() })
                    Spacer(Modifier.weight(1f))
                    CircleIconButton(
                        FIcons.Pin, if (note?.pinned == true) "Unpin" else "Pin", { vm.togglePin() },
                        background = if (note?.pinned == true) FlickColors.Lavender else FlickColors.Surface,
                        tint = if (note?.pinned == true) FlickColors.OnAccent else FlickColors.TextPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(FIcons.Share, "Share", { share() })
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(FIcons.More, "More", { showOptions = true })
                }

                if (vm.loaded) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp),
                    ) {
                        item(key = "status") {
                            val dot by animateColorAsState(
                                if (vm.saved) FlickColors.Mint else FlickColors.TextSecondary,
                                Motion.settle(), label = "savedDot",
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    (note?.updatedAt?.let { relativeDate(it) } ?: "Today") + if (vm.saved) " · Saved" else " · Editing",
                                    style = FlickType.Caption, color = FlickColors.TextSecondary,
                                )
                            }
                        }
                        item(key = "title") {
                            BasicTextField(
                                value = vm.title,
                                onValueChange = vm::onTitleChange,
                                textStyle = FlickType.Title,
                                cursorBrush = SolidColor(FlickColors.Lavender),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 10.dp)
                                    .focusRequester(titleFocus)
                                    .onFocusChanged { if (it.isFocused) vm.onTitleFocused() },
                                decorationBox = { inner ->
                                    Box {
                                        if (vm.title.isEmpty()) {
                                            Text("Title", style = FlickType.Title, color = FlickColors.TextSecondary.copy(alpha = 0.6f))
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                        items(
                            items,
                            key = { item ->
                                when (item) {
                                    is DocItem.TextRun -> item.run.key
                                    is DocItem.Media -> item.block.id
                                }
                            },
                        ) { item ->
                            when (item) {
                                is DocItem.TextRun -> RunField(
                                    run = item.run,
                                    value = vm.fieldFor(item.run),
                                    showPlaceholder = vm.blocks.size == 1 && item.run.blocks[0].text.isEmpty(),
                                    requesters = requesters,
                                    onChange = { vm.onRunChange(item.run.key, it) },
                                    onFocused = { vm.onRunFocused(item.run.key) },
                                    onToggle = { vm.toggleChecked(it) },
                                    onBackspaceAtStart = { vm.backspaceAtRunStart(item.run.key) },
                                )
                                is DocItem.Media -> MediaRow(
                                    block = item.block,
                                    onRemove = { vm.removeBlock(item.block.id) },
                                )
                            }
                        }
                        // tapping below the text puts the caret at the end
                        item(key = "end") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { vm.tapEnd() }
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                AnimatedVisibility(
                    visible = vm.focusedKey != null,
                    enter = slideInVertically(Motion.settle()) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    FormatToolbar(
                        active = vm.activeFmts(),
                        type = vm.activeType(),
                        canUndo = vm.canUndo,
                        canRedo = vm.canRedo,
                        onUndo = { vm.undo() },
                        onRedo = { vm.redo() },
                        onFmt = { vm.toggleFmt(it) },
                        onType = { vm.setType(it) },
                        onIndent = { vm.changeIndent(it) },
                        onImage = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
            }
        }
    }

    if (showOptions) {
        val n = note
        if (n != null) {
            NoteOptionsSheet(
                note = n,
                inTrash = false,
                folders = folders,
                onDismiss = { showOptions = false },
                onPin = { vm.togglePin(); showOptions = false },
                onMove = { vm.move(it); showOptions = false },
                onLock = { toggleLock(); showOptions = false },
                onShare = { showOptions = false; share() },
                onDelete = { showOptions = false; vm.trash { onBack() } },
                onRestore = {},
                onDeleteForever = {},
            )
        }
    }
}

// ---------- text runs (one text field for consecutive lines, so selection can span lines) ----------

private fun markerWidthDp(type: BlockType): Int = when (type) {
    BlockType.CHECKLIST -> 34
    BlockType.BULLET -> 22
    BlockType.NUMBERED -> 30
    BlockType.QUOTE -> 15
    else -> 0
}

private fun leadDp(b: EditorBlock): Int = b.indent * 24 + markerWidthDp(b.type)

private fun numberFor(blocks: List<EditorBlock>, index: Int): Int {
    val b = blocks[index]
    if (b.type != BlockType.NUMBERED) return 0
    var n = 1
    var j = index - 1
    while (j >= 0) {
        val x = blocks[j]
        if (x.type == BlockType.NUMBERED && x.indent == b.indent) n++
        else if (x.type.isList && x.indent > b.indent) Unit
        else break
        j--
    }
    return n
}

/** Text of the whole run with per-line styling (indent for markers, headings, quotes, formatting). */
private fun buildStyled(blocks: List<EditorBlock>, density: Density): AnnotatedString {
    val canon = blocks.canonical()
    val full = canon + TERM
    val starts = blocks.lineStarts()
    val gs = blocks.globalSpans()
    return buildAnnotatedString {
        append(full)
        blocks.forEachIndexed { i, b ->
            val start = starts[i]
            val endExclusive = if (i == blocks.lastIndex) full.length else starts[i + 1]
            val lead = with(density) { leadDp(b).dp.toSp() }
            addStyle(
                ParagraphStyle(
                    textIndent = TextIndent(lead, lead),
                    lineHeight = if (b.type == BlockType.HEADING) 30.sp else 24.sp,
                ),
                start, endExclusive,
            )
            when {
                b.type == BlockType.HEADING ->
                    addStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), start, endExclusive)
                b.type == BlockType.QUOTE || (b.type == BlockType.CHECKLIST && b.checked) ->
                    addStyle(SpanStyle(color = FlickColors.TextSecondary), start, endExclusive)
            }
        }
        gs.forEach { s ->
            val a = s.start.coerceIn(0, canon.length)
            val z = s.end.coerceIn(0, canon.length)
            if (z > a) {
                addStyle(
                    when (s.fmt) {
                        Fmt.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                        Fmt.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                        Fmt.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                        Fmt.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    },
                    a, z,
                )
            }
        }
    }
}

@Composable
private fun RunField(
    run: Run,
    value: TextFieldValue,
    showPlaceholder: Boolean,
    requesters: MutableMap<String, FocusRequester>,
    onChange: (TextFieldValue) -> Unit,
    onFocused: () -> Unit,
    onToggle: (String) -> Unit,
    onBackspaceAtStart: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val fr = remember(run.key) { FocusRequester() }
    DisposableEffect(run.key) {
        requesters[run.key] = fr
        onDispose { requesters.remove(run.key) }
    }
    val density = LocalDensity.current
    val blocks = run.blocks
    val styled = remember(blocks, density) { buildStyled(blocks, density) }
    val starts = remember(blocks) { blocks.lineStarts() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value.copy(annotatedString = styled),
            onValueChange = onChange,
            textStyle = FlickType.Body,
            cursorBrush = SolidColor(FlickColors.Lavender),
            onTextLayout = { layout = it },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fr)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace &&
                        value.selection.collapsed && value.selection.start == 0
                    ) onBackspaceAtStart() else false
                },
        )
        if (showPlaceholder) {
            Text("Start writing", style = FlickType.Body, color = FlickColors.TextSecondary.copy(alpha = 0.6f))
        }
        val l = layout
        if (l != null) {
            Markers(blocks, starts, l, onToggle, Modifier.matchParentSize())
        }
    }
}

/** Checkboxes, bullets, numbers, quote bars and the strike-through, drawn on top of the text. */
@Composable
private fun Markers(
    blocks: List<EditorBlock>,
    starts: IntArray,
    layout: TextLayoutResult,
    onToggle: (String) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val textLen = layout.layoutInput.text.length
    fun safe(off: Int) = off.coerceIn(0, max(0, textLen - 1))

    Box(modifier) {
        blocks.forEachIndexed { i, b ->
            val line = layout.getLineForOffset(safe(starts[i]))
            val top = layout.getLineTop(line)
            val xPx = with(density) { (b.indent * 24).dp.roundToPx() }
            when (b.type) {
                BlockType.CHECKLIST -> {
                    Box(Modifier.offset { IntOffset(xPx, top.roundToInt()) }) {
                        FlickCheckbox(
                            checked = b.checked,
                            onToggle = { onToggle(b.id) },
                            fill = FlickColors.Mint,
                            check = Color(0xFF10200A),
                            outline = Color(0xFF6A6A6A),
                        )
                    }
                    StrikeOverlay(b.checked, layout, starts[i], b.text.length, Modifier.matchParentSize())
                }
                BlockType.BULLET -> Box(
                    Modifier.offset { IntOffset(xPx, top.roundToInt()) }.width(22.dp).height(24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        listOf("•", "◦", "▪", "•")[b.indent.coerceIn(0, 3)],
                        style = FlickType.Body, color = FlickColors.Lavender,
                    )
                }
                BlockType.NUMBERED -> Box(
                    Modifier.offset { IntOffset(xPx, top.roundToInt()) }.width(30.dp).height(24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("${numberFor(blocks, i)}.", style = FlickType.Body, color = FlickColors.TextSecondary)
                }
                BlockType.QUOTE -> {
                    val last = layout.getLineForOffset(safe(starts[i] + b.text.length))
                    val bottom = layout.getLineBottom(last)
                    Box(
                        Modifier
                            .offset { IntOffset(xPx, top.roundToInt()) }
                            .width(3.dp)
                            .height(with(density) { (bottom - top).toDp() })
                            .clip(CircleShape)
                            .background(FlickColors.Lavender)
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun StrikeOverlay(checked: Boolean, layout: TextLayoutResult, start: Int, length: Int, modifier: Modifier) {
    val p by animateFloatAsState(if (checked) 1f else 0f, tween(320), label = "strike")
    if (p <= 0f || length == 0) return
    Canvas(modifier) {
        val textLen = layout.layoutInput.text.length
        fun safe(off: Int) = off.coerceIn(0, max(0, textLen - 1))
        val first = layout.getLineForOffset(safe(start))
        val last = layout.getLineForOffset(safe(start + length - 1))
        for (line in first..last) {
            val l = layout.getLineLeft(line)
            val r = layout.getLineRight(line)
            if (r - l > 2f) {
                val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
                drawLine(
                    FlickColors.TextSecondary,
                    Offset(l, y),
                    Offset(l + (r - l) * p, y),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }
    }
}

// ---------- images and dividers ----------

@Composable
private fun MediaRow(block: EditorBlock, onRemove: () -> Unit) {
    when (block.type) {
        BlockType.DIVIDER -> Box(
            Modifier
                .fillMaxWidth()
                .pressable(haptic = false, onLongClick = onRemove) {}
                .padding(vertical = 14.dp)
                .height(1.dp)
                .background(FlickColors.Outline)
        )
        else -> Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(18.dp)),
        ) {
            AsyncImage(
                model = block.attachment?.let { File(it) },
                contentDescription = "Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 400.dp),
            )
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                CircleIconButton(
                    FIcons.Close, "Remove image", onRemove,
                    background = Color(0xAA000000), size = 32.dp, iconSize = 14.dp,
                )
            }
        }
    }
}

// ---------- toolbar ----------

@Composable
private fun TbButton(
    active: Boolean,
    onClick: () -> Unit,
    wide: Boolean = false,
    enabled: Boolean = true,
    content: @Composable (Color) -> Unit,
) {
    val bg by animateColorAsState(if (active) FlickColors.Lavender else Color.Transparent, Motion.settle(), label = "tbBg")
    val fg by animateColorAsState(if (active) FlickColors.OnAccent else FlickColors.TextPrimary, Motion.settle(), label = "tbFg")
    Box(
        Modifier
            .width(if (wide) 48.dp else 42.dp)
            .height(40.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(CircleShape)
            .background(bg)
            .pressable { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) { content(fg) }
}

@Composable
private fun TbDivider() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(FlickColors.Outline))
}

@Composable
private fun FormatToolbar(
    active: Set<Fmt>,
    type: BlockType?,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFmt: (Fmt) -> Unit,
    onType: (BlockType) -> Unit,
    onIndent: (Int) -> Unit,
    onImage: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(FlickColors.SurfaceHigh)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TbButton(false, onUndo, enabled = canUndo) { c -> Icon(FIcons.Undo, "Undo", tint = c, modifier = Modifier.size(20.dp)) }
        TbButton(false, onRedo, enabled = canRedo) { c -> Icon(FIcons.Redo, "Redo", tint = c, modifier = Modifier.size(20.dp)) }
        TbDivider()
        TbButton(Fmt.BOLD in active, { onFmt(Fmt.BOLD) }) { c -> Text("B", color = c, fontWeight = FontWeight.Bold, style = FlickType.Body) }
        TbButton(Fmt.ITALIC in active, { onFmt(Fmt.ITALIC) }) { c -> Text("I", color = c, fontStyle = FontStyle.Italic, style = FlickType.Body) }
        TbButton(Fmt.UNDERLINE in active, { onFmt(Fmt.UNDERLINE) }) { c -> Text("U", color = c, textDecoration = TextDecoration.Underline, style = FlickType.Body) }
        TbButton(Fmt.STRIKE in active, { onFmt(Fmt.STRIKE) }) { c -> Text("S", color = c, textDecoration = TextDecoration.LineThrough, style = FlickType.Body) }
        TbDivider()
        TbButton(type == BlockType.HEADING, { onType(BlockType.HEADING) }) { c -> Text("H", color = c, fontWeight = FontWeight.SemiBold, style = FlickType.Body) }
        TbButton(type == BlockType.BULLET, { onType(BlockType.BULLET) }) { c -> Icon(FIcons.Bullet, "Bulleted list", tint = c, modifier = Modifier.size(20.dp)) }
        TbButton(type == BlockType.CHECKLIST, { onType(BlockType.CHECKLIST) }, wide = true) { c -> Icon(FIcons.Check, "Checklist", tint = c, modifier = Modifier.size(20.dp)) }
        TbButton(type == BlockType.NUMBERED, { onType(BlockType.NUMBERED) }) { c -> Text("1.", color = c, style = FlickType.Label) }
        TbButton(type == BlockType.QUOTE, { onType(BlockType.QUOTE) }) { c -> Icon(FIcons.Quote, "Quote", tint = c, modifier = Modifier.size(20.dp)) }
        TbDivider()
        if (type != null && type.isList) {
            TbButton(false, { onIndent(-1) }) { c -> Icon(FIcons.IndentOut, "Outdent", tint = c, modifier = Modifier.size(20.dp)) }
            TbButton(false, { onIndent(1) }) { c -> Icon(FIcons.IndentIn, "Indent", tint = c, modifier = Modifier.size(20.dp)) }
        }
        TbButton(false, onImage) { c -> Icon(FIcons.Image, "Add image", tint = c, modifier = Modifier.size(20.dp)) }
    }
}
