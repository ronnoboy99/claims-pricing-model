package com.flick.app.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import com.flick.app.ui.components.radialOffsets
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flick.app.data.BlockType
import com.flick.app.data.FolderEntity
import com.flick.app.data.NoteEntity
import com.flick.app.data.ViewMode
import com.flick.app.haptics.LocalHaptics
import com.flick.app.ui.components.ChipItem
import com.flick.app.ui.components.ChipRow
import com.flick.app.ui.components.CircleIconButton
import com.flick.app.ui.components.FlickFab
import com.flick.app.ui.components.FolderSheet
import com.flick.app.ui.components.NoteCard
import com.flick.app.ui.components.NoteOptionsSheet
import com.flick.app.ui.components.RadialItem
import com.flick.app.ui.components.RadialMenu
import com.flick.app.ui.components.SearchBar
import com.flick.app.ui.components.SettingsSheet
import com.flick.app.ui.components.StatusPill
import com.flick.app.ui.components.pressable
import com.flick.app.ui.components.staggerIn
import com.flick.app.ui.theme.Accents
import com.flick.app.ui.theme.FIcons
import com.flick.app.ui.theme.FlickColors
import com.flick.app.ui.theme.FlickType
import com.flick.app.ui.theme.Motion
import com.flick.app.haptics.HapticEngine
import com.flick.app.util.BiometricGate
import com.flick.app.util.findActivity
import com.flick.app.util.shareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface HomeSheet {
    data object None : HomeSheet
    data class Options(val note: NoteEntity) : HomeSheet
    data class FolderEdit(val folder: FolderEntity?) : HomeSheet
    data object Settings : HomeSheet
}

private fun Modifier.fullBleed(extra: Dp): Modifier = layout { measurable, constraints ->
    val e = extra.roundToPx()
    val w = constraints.maxWidth + 2 * e
    val p = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, p.height) { p.place(-e, 0) }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    sharedScope: SharedTransitionScope,
    animScope: AnimatedVisibilityScope,
    onOpenNote: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queryText by vm.queryUi.collectAsStateWithLifecycle()
    val pill by vm.pill.collectAsStateWithLifecycle()
    val hapticsOn by vm.hapticsOn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<HomeSheet>(HomeSheet.None) }
    var radialOpen by remember { mutableStateOf(false) }
    val played = remember { mutableSetOf<String>() }
    val inTrash = state.selection is Selection.Trash
    val folderById = remember(state.folders) { state.folders.associateBy { it.id } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.createNoteWithImage(uri) { onOpenNote(it) }
    }

    // radial menu (slide-and-release) ----------------------------------------------------------
    val radialItems = listOf(
        RadialItem(FIcons.Check, "Checklist") {
            radialOpen = false
            vm.createNote(BlockType.CHECKLIST) { id -> onOpenNote(id) }
        },
        RadialItem(FIcons.Image, "Photo") {
            radialOpen = false
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
    var radialHover by remember { mutableIntStateOf(-1) }
    val hitRadiusPx = with(density) { 44.dp.toPx() }

    // delete: the card is "snapped" away (Thanos style) ------------------------------------------------
    val cardCoords = remember { HashMap<String, LayoutCoordinates>() }
    var snapping by remember { mutableStateOf<SnapCard?>(null) }

    fun deleteWithSnap(note: NoteEntity) {
        val from = cardCoords[note.id]?.takeIf { it.isAttached }?.boundsInRoot()
        if (from != null && state.viewMode != ViewMode.FLICK) {
            snapping = SnapCard(note = note, folder = note.folderId?.let { folderById[it] }, from = from)
            haptics.thud()
        }
        vm.trash(note.id)
    }

    fun open(note: NoteEntity) {
        if (note.locked) {
            val act = context.findActivity()
            if (act != null) BiometricGate.authenticate(act, "Unlock note") { onOpenNote(note.id) }
        } else {
            onOpenNote(note.id)
        }
    }

    fun toggleLock(note: NoteEntity) {
        val act = context.findActivity() ?: return
        if (!BiometricGate.canAuthenticate(context)) {
            Toast.makeText(context, "Set up a screen lock to lock notes", Toast.LENGTH_SHORT).show()
            return
        }
        if (note.locked) {
            BiometricGate.authenticate(act, "Unlock note") { vm.setLocked(note.id, false) }
        } else {
            vm.setLocked(note.id, true)
        }
    }

    // pull-to-create
    val thresholdPx = with(density) { 120.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    var crossed by remember { mutableStateOf(false) }
    val pullEnabled = state.viewMode != ViewMode.FLICK && !inTrash && queryText.isBlank()
    val connection = remember(pullEnabled, thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullEnabled && pull > 0f && available.y < 0f) {
                    val consume = maxOf(available.y, -pull)
                    pull += consume
                    if (crossed && pull < thresholdPx) crossed = false
                    return Offset(0f, consume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (pullEnabled && source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull += available.y * 0.5f
                    if (!crossed && pull >= thresholdPx) {
                        crossed = true
                        haptics.rise()
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull > 0f) {
                    if (pull >= thresholdPx) vm.createNote { id -> onOpenNote(id) }
                    crossed = false
                    animate(pull, 0f, animationSpec = Motion.settle()) { v, _ -> pull = v }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    // undo pill
    val lastPill = remember { mutableStateOf<Pill?>(null) }
    if (pill != null) lastPill.value = pill
    LaunchedEffect(pill?.id) {
        val p = pill ?: return@LaunchedEffect
        haptics.thud()
        delay(6000)
        vm.dismissPill(p.id)
    }

    val chips = remember(state.folders) {
        buildList {
            add(ChipItem("all", "All"))
            state.folders.forEach { add(ChipItem(it.id, it.name, Accents.color(it.colorKey))) }
            add(ChipItem("add", "+"))
            add(ChipItem("trash", "Trash"))
        }
    }
    val selectedKey = when (val s = state.selection) {
        Selection.All -> "all"
        Selection.Trash -> "trash"
        is Selection.Folder -> s.id
    }

    @Composable
    fun Header(compact: Boolean) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text("Notes", style = FlickType.Display)
                    Text(
                        if (inTrash) "${state.trashCount} in Trash" else "${state.activeCount} notes",
                        style = FlickType.Small,
                        color = FlickColors.TextSecondary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewToggle(state.viewMode) { vm.setViewMode(it) }
                    CircleIconButton(FIcons.Settings, "Settings", { sheet = HomeSheet.Settings }, size = 40.dp, iconSize = 18.dp)
                }
            }
            if (!compact) {
                Spacer(Modifier.height(20.dp))
                SearchBar(queryText, { vm.setQuery(it) })
                Spacer(Modifier.height(16.dp))
                ChipRow(
                    items = chips,
                    selectedKey = selectedKey,
                    onSelect = { item ->
                        when (item.key) {
                            "add" -> sheet = HomeSheet.FolderEdit(null)
                            "all" -> vm.select(Selection.All)
                            "trash" -> vm.select(Selection.Trash)
                            else -> vm.select(Selection.Folder(item.key))
                        }
                    },
                    onLongPress = { item ->
                        folderById[item.key]?.let { sheet = HomeSheet.FolderEdit(it) }
                    },
                    modifier = Modifier.fullBleed(20.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(FlickColors.Bg)) {
        if (state.viewMode == ViewMode.FLICK) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
                Header(compact = true)
                val decks = remember(state.allActive, state.folders) {
                    buildList {
                        if (state.allActive.isNotEmpty()) add(Deck("all", "All", FlickColors.Lavender, state.allActive))
                        state.folders.forEach { f ->
                            val list = state.allActive.filter { it.folderId == f.id }
                            if (list.isNotEmpty()) add(Deck(f.id, f.name, Accents.color(f.colorKey), list))
                        }
                    }
                }
                FlickDeck(
                    decks = decks,
                    folderNameOf = { n -> n.folderId?.let { folderById[it]?.name } },
                    blocksFor = vm::blocksFor,
                    onOpen = { open(it) },
                    onLongPress = { sheet = HomeSheet.Options(it) },
                    onToggleBlock = { vm.toggleBlock(it) },
                    onCreate = { vm.createNote { id -> onOpenNote(id) } },
                    sharedScope = sharedScope,
                    animScope = animScope,
                    modifier = Modifier.padding(bottom = 96.dp),
                )
            }
        } else {
            val gridState = rememberLazyStaggeredGridState()
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val pinned = remember(state.notes) { state.notes.filter { it.pinned } }
            val others = remember(state.notes) { state.notes.filter { !it.pinned } }
            val showSections = !inTrash && pinned.isNotEmpty()

            Box(Modifier.fillMaxSize()) {
            // The ghost card sits behind the list and is revealed as the whole list slides down.
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = topInset + 8.dp)) {
                PullGhost({ pull }, thresholdPx)
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(if (state.viewMode == ViewMode.GRID) 2 else 1),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(connection)
                    // translate the entire list (header + cards) together instead of relayouting items
                    .graphicsLayer { translationY = pull },
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topInset + 8.dp, bottom = 170.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                    Header(compact = false)
                }
                if (inTrash) {
                    item(key = "trashbar", span = StaggeredGridItemSpan.FullLine) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Notes are deleted after 30 days", style = FlickType.Small, color = FlickColors.TextSecondary)
                            if (state.trashCount > 0) {
                                Box(
                                    Modifier.height(36.dp).clip(CircleShape).background(FlickColors.Surface)
                                        .pressable { vm.emptyTrash() }.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Empty trash", style = FlickType.Label, color = FlickColors.Danger) }
                            }
                        }
                    }
                }
                if (showSections) {
                    item(key = "lbl-pinned", span = StaggeredGridItemSpan.FullLine) {
                        Text("PINNED", style = FlickType.Overline, color = FlickColors.Lavender)
                    }
                }
                items(pinned, key = { it.id }) { note ->
                    NoteItem(
                        note, state, inTrash, folderById, played, pinned.indexOf(note), sharedScope, animScope,
                        onClick = { if (inTrash) sheet = HomeSheet.Options(note) else open(note) },
                        onLong = { sheet = HomeSheet.Options(note) },
                        onCoords = { cardCoords[note.id] = it },
                        modifier = Modifier.animateItem(placementSpec = Motion.settle(), fadeOutSpec = tween(200)),
                    )
                }
                if (showSections && others.isNotEmpty()) {
                    item(key = "lbl-earlier", span = StaggeredGridItemSpan.FullLine) {
                        Text("EARLIER", style = FlickType.Overline, color = FlickColors.TextSecondary)
                    }
                }
                items(others, key = { it.id }) { note ->
                    NoteItem(
                        note, state, inTrash, folderById, played, pinned.size + others.indexOf(note), sharedScope, animScope,
                        onClick = { if (inTrash) sheet = HomeSheet.Options(note) else open(note) },
                        onLong = { sheet = HomeSheet.Options(note) },
                        onCoords = { cardCoords[note.id] = it },
                        modifier = Modifier.animateItem(placementSpec = Motion.settle(), fadeOutSpec = tween(200)),
                    )
                }
                if (state.notes.isEmpty()) {
                    item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                            Text(
                                when {
                                    inTrash -> "Trash is empty"
                                    queryText.isNotBlank() -> "No notes match"
                                    else -> "Nothing here yet. Pull down or tap +"
                                },
                                style = FlickType.Body, color = FlickColors.TextSecondary,
                            )
                        }
                    }
                }
            }
            }
        }

        // bottom fade
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, FlickColors.Bg)))
        )

        if (radialOpen) {
            Box(
                Modifier.fillMaxSize().background(FlickColors.Bg.copy(alpha = 0.65f))
                    .pointerInput(Unit) { detectTapGestures { radialOpen = false } }
            )
        }

        // FAB + radial menu
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            if (radialOpen) {
                RadialMenu(
                    items = radialItems,
                    hoverIndex = radialHover,
                    modifier = Modifier.padding(bottom = 32.dp).align(Alignment.BottomCenter),
                )
            }
            FlickFab(
                onClick = {
                    if (radialOpen) radialOpen = false
                    else {
                        haptics.click()
                        vm.createNote { id -> onOpenNote(id) }
                    }
                },
                onHoldStart = {
                    radialOpen = true
                    radialHover = -1
                    haptics.click()
                },
                onHoldMove = { finger ->
                    val offsets = radialOffsets(radialItems.size)
                    var hit = -1
                    offsets.forEachIndexed { i, o ->
                        val center = Offset(o.x * density.density, o.y * density.density)
                        if ((finger - center).getDistance() < hitRadiusPx) hit = i
                    }
                    if (hit != radialHover) {
                        radialHover = hit
                        if (hit >= 0) haptics.tick()
                    }
                },
                onHoldRelease = {
                    val picked = radialHover
                    radialHover = -1
                    // released on an option: run it. Otherwise the menu stays open (tap to pick).
                    if (picked >= 0) radialItems[picked].onClick()
                },
                modifier = Modifier.padding(bottom = 28.dp),
            )
        }

        // undo pill
        AnimatedVisibility(
            visible = pill != null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 108.dp),
            enter = slideInVertically(Motion.pop()) { it } + fadeIn(),
            exit = fadeOut(),
        ) {
            lastPill.value?.let { p -> StatusPill(p.text, p.actionLabel, p.action?.let { a -> { a(); vm.dismissPill(p.id) } }) }
        }

        // the deleted card turning to dust
        snapping?.let { f ->
            SnapOverlay(f = f, onDone = { snapping = null })
        }
    }

    // sheets
    when (val s = sheet) {
        HomeSheet.None -> Unit
        is HomeSheet.Options -> NoteOptionsSheet(
            note = s.note,
            inTrash = s.note.trashedAt != null,
            folders = state.folders,
            onDismiss = { sheet = HomeSheet.None },
            onPin = { vm.togglePin(s.note); sheet = HomeSheet.None },
            onMove = { fid -> vm.moveNote(s.note.id, fid); sheet = HomeSheet.None },
            onLock = { toggleLock(s.note); sheet = HomeSheet.None },
            onShare = {
                sheet = HomeSheet.None
                scope.launch { shareText(context, s.note.title, vm.markdown(s.note.id)) }
            },
            onDelete = { deleteWithSnap(s.note); sheet = HomeSheet.None },
            onRestore = { vm.restore(s.note.id); sheet = HomeSheet.None },
            onDeleteForever = { vm.deleteForever(s.note.id); haptics.thud(); sheet = HomeSheet.None },
        )
        is HomeSheet.FolderEdit -> FolderSheet(
            folder = s.folder,
            onDismiss = { sheet = HomeSheet.None },
            onSave = { name, color ->
                val f = s.folder
                if (f == null) vm.createFolder(name, color)
                else vm.updateFolder(f.copy(name = name.trim().ifBlank { f.name }, colorKey = color))
                sheet = HomeSheet.None
            },
            onDelete = s.folder?.let { f -> { vm.deleteFolder(f.id); sheet = HomeSheet.None } },
            onMoveFolder = s.folder?.let { f -> { dir: Int -> vm.moveFolder(f.id, dir); Unit } },
        )
        HomeSheet.Settings -> SettingsSheet(
            sort = state.sort,
            haptics = hapticsOn,
            onSort = { vm.setSort(it) },
            onHaptics = { vm.setHaptics(it) },
            onExportAll = {
                sheet = HomeSheet.None
                scope.launch { shareText(context, "Flick notes", vm.exportAll()) }
            },
            onDismiss = { sheet = HomeSheet.None },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NoteItem(
    note: NoteEntity,
    state: HomeState,
    inTrash: Boolean,
    folderById: Map<String, FolderEntity>,
    played: MutableSet<String>,
    index: Int,
    sharedScope: SharedTransitionScope,
    animScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onLong: () -> Unit,
    onCoords: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    with(sharedScope) {
        NoteCard(
            note = note,
            folder = note.folderId?.let { folderById[it] },
            query = state.query,
            inTrash = inTrash,
            modifier = modifier
                .onGloballyPositioned { onCoords(it) }
                .staggerIn(note.id, index, played)
                .sharedBounds(
                    rememberSharedContentState(key = "note-${note.id}"),
                    animScope,
                    boundsTransform = BoundsTransform { _, _ -> Motion.expand<Rect>() },
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(24.dp)),
                )
                .pressable(onLongClick = onLong, onClick = onClick),
        )
    }
}

private data class SnapCard(
    val note: NoteEntity,
    val folder: FolderEntity?,
    val from: Rect,
    val key: Long = System.nanoTime(),
)

/** Cheap deterministic pseudo-random number in 0..1 for particle [i] and channel [k]. */
private fun rand01(seed: Int, i: Int, k: Int): Float {
    var x = seed * 31 + i * 374761393 + k * 668265263
    x = (x xor (x shr 13)) * 1274126177
    x = x xor (x shr 16)
    return (x and 0xFFFF) / 65535f
}

/**
 * Thanos snap. The deleted card is captured as an image, cut into small squares, and the squares
 * drift up and to the right while fading, sweeping across the card from left to right.
 */
@Composable
private fun SnapOverlay(f: SnapCard, onDone: () -> Unit) {
    val density = LocalDensity.current
    val layer = rememberGraphicsLayer()
    var image by remember(f.key) { mutableStateOf<ImageBitmap?>(null) }
    val progress = remember(f.key) { Animatable(0f) }
    val latestDone by rememberUpdatedState(onDone)
    val seed = f.key.toInt()
    val cell = with(density) { 6.dp.roundToPx() }.coerceAtLeast(4)

    LaunchedEffect(f.key) {
        // let the card draw into the layer first, then grab it as a bitmap
        withFrameNanos { }
        withFrameNanos { }
        val bmp = runCatching { layer.toImageBitmap() }.getOrNull()
        if (bmp == null) {
            latestDone()
            return@LaunchedEffect
        }
        image = bmp
        progress.animateTo(1f, tween(1100, easing = LinearEasing))
        latestDone()
    }

    val wDp = with(density) { f.from.width.toDp() }
    val hDp = with(density) { f.from.height.toDp() }
    Box(
        Modifier
            .offset { IntOffset(f.from.left.roundToInt(), f.from.top.roundToInt()) }
            .size(wDp, hDp),
    ) {
        val img = image
        if (img == null) {
            NoteCard(
                note = f.note,
                folder = f.folder,
                query = "",
                inTrash = false,
                modifier = Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            )
        } else {
            val cols = (img.width + cell - 1) / cell
            val rows = (img.height + cell - 1) / cell
            Canvas(Modifier.fillMaxSize()) {
                val p = progress.value
                val d = density.density
                for (cy in 0 until rows) {
                    for (cx in 0 until cols) {
                        val sx = cx * cell
                        val sy = cy * cell
                        if (sx >= img.width || sy >= img.height) continue
                        val sw = minOf(cell, img.width - sx)
                        val sh = minOf(cell, img.height - sy)
                        val i = cy * cols + cx
                        // the dust starts at the left edge of the card and sweeps to the right
                        val delay = 0.5f * (cx.toFloat() / cols) + 0.15f * rand01(seed, i, 0)
                        val t = ((p - delay) / 0.35f).coerceIn(0f, 1f)
                        if (t >= 1f) continue
                        val e = t * t
                        val dx = (30f + 110f * rand01(seed, i, 1)) * d * e
                        val dy = (-70f + 100f * rand01(seed, i, 2)) * d * e
                        val rot = (rand01(seed, i, 3) - 0.5f) * 140f * t
                        rotate(rot, pivot = Offset(sx + sw / 2f + dx, sy + sh / 2f + dy)) {
                            drawImage(
                                img,
                                srcOffset = IntOffset(sx, sy),
                                srcSize = IntSize(sw, sh),
                                dstOffset = IntOffset((sx + dx).roundToInt(), (sy + dy).roundToInt()),
                                dstSize = IntSize(sw, sh),
                                alpha = 1f - t,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PullGhost(pullProvider: () -> Float, thresholdPx: Float) {
    val pullPx = pullProvider()
    val density = LocalDensity.current
    val h = with(density) { pullPx.toDp() }
    val ready = pullPx >= thresholdPx
    val p = (pullPx / thresholdPx).coerceIn(0f, 1f)
    val bg by animateColorAsState(if (ready) FlickColors.Lavender else FlickColors.Surface, Motion.settle(), label = "ghostBg")
    Box(Modifier.fillMaxWidth().height(h), contentAlignment = Alignment.Center) {
        if (pullPx > 4f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((h - 12.dp).coerceAtLeast(0.dp))
                    .graphicsLayer {
                        scaleX = 0.9f + 0.1f * p
                        scaleY = 0.9f + 0.1f * p
                        alpha = p
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (ready) "Release to write" else "Pull to write",
                    style = FlickType.Label,
                    color = if (ready) FlickColors.OnAccent else FlickColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ViewToggle(mode: ViewMode, onChange: (ViewMode) -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(FlickColors.Surface).padding(3.dp),
    ) {
        ViewMode.entries.forEach { m ->
            val selected = m == mode
            val bg by animateColorAsState(if (selected) FlickColors.Lavender else Color.Transparent, Motion.settle(), label = "viewBg")
            Box(
                Modifier.height(34.dp).clip(CircleShape).background(bg)
                    .pressable { onChange(m) }.padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (m) { ViewMode.GRID -> "Grid"; ViewMode.LIST -> "List"; ViewMode.FLICK -> "Flick" },
                    style = FlickType.Small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = if (selected) FlickColors.OnAccent else Color(0xFFB0B0B0),
                )
            }
        }
    }
}
