package com.flick.app.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flick.app.data.BlockType
import com.flick.app.data.FolderEntity
import com.flick.app.data.Fmt
import com.flick.app.data.NoteEntity
import com.flick.app.data.NotesRepository
import com.flick.app.data.Span
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

data class FocusReq(val runKey: String, val token: Long = System.nanoTime())

private data class Snapshot(val title: String, val blocks: List<EditorBlock>)

private data class Diff(val prefix: Int, val removed: Int, val inserted: Int)

private fun diff(a: String, b: String): Diff {
    val m = min(a.length, b.length)
    var p = 0
    while (p < m && a[p] == b[p]) p++
    var s = 0
    while (s < m - p && a[a.length - 1 - s] == b[b.length - 1 - s]) s++
    return Diff(p, a.length - p - s, b.length - p - s)
}

/**
 * A plain diff can be ambiguous when characters repeat (for example two newlines in a row).
 * The caret position before the edit tells us where the edit really happened.
 */
private fun refine(oldText: String, newText: String, d: Diff, oldSel: TextRange?): Diff {
    if (oldSel == null || !oldSel.collapsed) return d
    val c = oldSel.start
    if (d.removed == 1 && d.inserted == 0) {
        for (idx in intArrayOf(c - 1, c)) {
            if (idx >= 0 && idx < oldText.length && oldText.removeRange(idx, idx + 1) == newText) {
                return Diff(idx, 1, 0)
            }
        }
    }
    if (d.removed == 0 && d.inserted > 0 && c >= 0 && c <= oldText.length && c + d.inserted <= newText.length) {
        if (oldText.substring(0, c) + newText.substring(c, c + d.inserted) + oldText.substring(c) == newText) {
            return Diff(c, 0, d.inserted)
        }
    }
    return d
}

private val shortcuts = listOf(
    "# " to BlockType.HEADING,
    "- " to BlockType.BULLET,
    "* " to BlockType.BULLET,
    "[] " to BlockType.CHECKLIST,
    "[ ] " to BlockType.CHECKLIST,
    "1. " to BlockType.NUMBERED,
    "> " to BlockType.QUOTE,
)

private fun continuation(t: BlockType) =
    if (t == BlockType.HEADING || t == BlockType.QUOTE) BlockType.PARAGRAPH else t

@OptIn(FlowPreview::class)
class EditorViewModel(
    private val noteId: String,
    private val repo: NotesRepository,
) : ViewModel() {

    var title by mutableStateOf("")
        private set
    var blocks by mutableStateOf<List<EditorBlock>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    /** Key of the text run that has focus (null when the title or nothing is focused). */
    var focusedKey by mutableStateOf<String?>(null)
        private set
    var focusRequest by mutableStateOf<FocusReq?>(null)
        private set
    var saved by mutableStateOf(true)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Field value (text + selection) of every text run, by run key. */
    private val fields = mutableStateMapOf<String, TextFieldValue>()

    val note: StateFlow<NoteEntity?> =
        repo.observeNote(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val folders: StateFlow<List<FolderEntity>> =
        repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Typing-style toggles made with a collapsed caret (apply to the next typed text). */
    private val pending = mutableStateMapOf<Fmt, Boolean>()
    private val dirty = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val burst = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var left = false

    // undo / redo (a burst of typing counts as one step)
    private var restoring = false
    private var inBurst = false
    private var baseline = Snapshot("", emptyList())
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    init {
        viewModelScope.launch {
            val n = repo.getNote(noteId)
            val bs = repo.getBlocks(noteId).map { it.toEditor() }
            title = n?.title ?: ""
            blocks = if (bs.isEmpty()) listOf(newBlock(BlockType.PARAGRAPH)) else bs
            baseline = Snapshot(title, blocks)
            loaded = true
        }
        viewModelScope.launch { dirty.debounce(400).collect { saveNow() } }
        viewModelScope.launch {
            burst.debounce(700).collect {
                inBurst = false
                baseline = Snapshot(title, blocks)
            }
        }
    }

    // ---------- saving ----------

    private fun entities() = blocks.mapIndexed { i, b -> b.toEntity(noteId, i) }

    private suspend fun saveNow() {
        if (left || !loaded) return
        repo.saveNote(noteId, title, entities())
        saved = true
    }

    private fun markDirty(trackUndo: Boolean = true) {
        if (trackUndo && !restoring && !inBurst) {
            undoStack.addLast(baseline)
            if (undoStack.size > 60) undoStack.removeFirst()
            redoStack.clear()
            inBurst = true
            syncUndo()
        }
        saved = false
        dirty.tryEmit(Unit)
        burst.tryEmit(Unit)
    }

    private fun syncUndo() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun flush() {
        if (!left && loaded) repo.saveInBackground(noteId, title, entities())
    }

    private fun isEmptyNote() = title.isBlank() && blocks.all { it.type.isText && it.text.isBlank() }

    /** Saves, or deletes the note if nothing was written. Safe to call more than once. */
    fun onLeave() {
        if (left || !loaded) return
        left = true
        repo.finishEditing(noteId, title, entities(), isEmptyNote())
    }

    override fun onCleared() {
        onLeave()
    }

    // ---------- undo / redo ----------

    private fun restore(s: Snapshot) {
        restoring = true
        title = s.title
        blocks = s.blocks
        restoring = false
        baseline = s
        inBurst = false
        pending.clear()
        markDirty(trackUndo = false)
        syncUndo()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        redoStack.addLast(Snapshot(title, blocks))
        restore(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(Snapshot(title, blocks))
        restore(next)
    }

    // ---------- runs ----------

    /** The run that starts with the block [key], or empty if the key is stale. */
    private fun runBlocks(key: String): List<EditorBlock> {
        val s = blocks.indexOfFirst { it.id == key }
        if (s < 0 || !blocks[s].type.isText) return emptyList()
        if (s > 0 && blocks[s - 1].type.isText) return emptyList()
        var e = s
        while (e + 1 < blocks.size && blocks[e + 1].type.isText) e++
        return blocks.subList(s, e + 1).toList()
    }

    private fun replaceRun(s0: Int, oldCount: Int, newRun: List<EditorBlock>) {
        blocks = blocks.take(s0) + newRun + blocks.drop(s0 + oldCount)
    }

    /** The text field value to show for a run (always consistent with the blocks). */
    fun fieldFor(run: Run): TextFieldValue {
        val expected = run.blocks.canonical() + TERM
        val cur = fields[run.key]
        if (cur != null && cur.text == expected) return cur
        val len = expected.length - 1
        val sel = cur?.selection ?: TextRange.Zero
        return TextFieldValue(expected, TextRange(sel.start.coerceIn(0, len), sel.end.coerceIn(0, len)))
    }

    // ---------- focus ----------

    fun onRunFocused(key: String) {
        focusedKey = key
        pending.clear()
    }

    fun onTitleFocused() {
        focusedKey = null
    }

    /** Puts the caret in block [blockId], [caret] characters into its line, and requests focus. */
    private fun focusBlock(blockId: String, caret: Int = 0) {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0 || !blocks[idx].type.isText) return
        var s = idx
        while (s > 0 && blocks[s - 1].type.isText) s--
        val run = runBlocks(blocks[s].id)
        if (run.isEmpty()) return
        val starts = run.lineStarts()
        val canon = run.canonical()
        val off = (starts[idx - s] + caret).coerceIn(0, canon.length)
        fields[run[0].id] = TextFieldValue(canon + TERM, TextRange(off))
        focusRequest = FocusReq(run[0].id)
    }

    fun focusFirst() {
        blocks.firstOrNull { it.type.isText }?.let { focusBlock(it.id, 0) }
    }

    /** Tap below the text: caret to the end of the note. */
    fun tapEnd() {
        val last = blocks.lastOrNull()
        if (last != null && last.type.isText) {
            focusBlock(last.id, last.text.length)
        } else {
            val p = newBlock(BlockType.PARAGRAPH)
            blocks = blocks + p
            markDirty()
            focusBlock(p.id, 0)
        }
    }

    // ---------- title ----------

    fun onTitleChange(s: String) {
        if (s.contains('\n')) {
            title = s.replace("\n", "")
            markDirty()
            focusFirst()
            return
        }
        title = s
        markDirty()
    }

    // ---------- text editing ----------

    fun onRunChange(runKey: String, incoming: TextFieldValue) {
        val s0 = blocks.indexOfFirst { it.id == runKey }
        val run = runBlocks(runKey)
        if (s0 < 0 || run.isEmpty()) return

        val oldCanon = run.canonical()
        val raw = incoming.text
        val newCanon = raw.replace(TERM, "")
        val clean = raw == newCanon + TERM

        fun mapOff(off: Int): Int {
            val o = off.coerceIn(0, raw.length)
            var removed = 0
            for (i in 0 until o) if (raw[i] == TERM[0]) removed++
            return (o - removed).coerceIn(0, newCanon.length)
        }

        val selS = mapOff(incoming.selection.start)
        val selE = mapOff(incoming.selection.end)
        val oldSel = fields[runKey]?.selection

        // only the selection changed
        if (newCanon == oldCanon) {
            if (oldSel == null || oldSel != TextRange(selS, selE)) pending.clear()
            fields[runKey] = TextFieldValue(
                oldCanon + TERM, TextRange(selS, selE), if (clean) incoming.composition else null,
            )
            return
        }

        val starts = run.lineStarts()
        val d = refine(oldCanon, newCanon, diff(oldCanon, newCanon), oldSel)
        val l1 = lineAt(starts, d.prefix)
        val endOld = d.prefix + d.removed
        val l2 = lineAt(starts, endOld)
        val insertedText = newCanon.substring(d.prefix, d.prefix + d.inserted)

        // Backspace at the start of a list item / heading / quote: turn it into a paragraph first
        if (d.removed == 1 && d.inserted == 0 && oldCanon[d.prefix] == '\n' && l1 + 1 < run.size) {
            val bi = l1 + 1
            val b = run[bi]
            val fixed = when {
                b.indent > 0 && b.type.isList -> b.copy(indent = b.indent - 1)
                b.type != BlockType.PARAGRAPH -> b.copy(type = BlockType.PARAGRAPH, checked = false, indent = 0)
                else -> null
            }
            if (fixed != null) {
                replaceRun(s0, run.size, run.toMutableList().also { it[bi] = fixed })
                fields[runKey] = TextFieldValue(oldCanon + TERM, TextRange(starts[bi]))
                pending.clear()
                markDirty()
                return
            }
        }

        if (d.removed == 0 && insertedText == "\n") {
            val b = run[l1]
            // Enter on an empty list item / heading / quote: back to a paragraph
            if (b.text.isEmpty() && b.type != BlockType.PARAGRAPH) {
                replaceRun(
                    s0, run.size,
                    run.toMutableList().also { it[l1] = b.copy(type = BlockType.PARAGRAPH, indent = 0, checked = false) },
                )
                fields[runKey] = TextFieldValue(oldCanon + TERM, TextRange(starts[l1]))
                markDirty()
                return
            }
            // "---" + Enter becomes a divider
            if (b.type == BlockType.PARAGRAPH && b.text == "---" && d.prefix == starts[l1] + 3) {
                val divider = newBlock(BlockType.DIVIDER)
                val para = newBlock(BlockType.PARAGRAPH)
                blocks = blocks.take(s0) + run.take(l1) + divider + para + run.drop(l1 + 1) + blocks.drop(s0 + run.size)
                markDirty()
                focusBlock(para.id, 0)
                return
            }
        }

        // general case: the edited region of lines [l1..l2] is rebuilt from its pieces
        val head = oldCanon.substring(starts[l1], d.prefix)
        val tail = oldCanon.substring(endOld, starts[l2] + run[l2].text.length)
        val pieces = (head + insertedText + tail).split('\n')
        val first = run[l1]
        val cont = continuation(first.type)
        var newRun = run.take(l1) +
            pieces.mapIndexed { i, t -> if (i == 0) first.copy(text = t) else newBlock(cont, t, indent = first.indent) } +
            run.drop(l2 + 1)

        // formatting: inherit what the caret was in (bold, italic, ...) for newly typed text
        val gs = run.globalSpans()
        var inherit: Set<Fmt> = emptySet()
        if (d.inserted > 0) {
            inherit = Spans.activeAt(gs, d.prefix)
            pending.forEach { (f, on) -> inherit = if (on) inherit + f else inherit - f }
        }
        val ngs = Spans.adjust(gs, d.prefix, d.removed, d.inserted, inherit)
        newRun = newRun.withSpans(ngs)

        if (newRun.canonical() != newCanon) {
            // safety net, should not happen: rebuild line by line from the new text
            val lines = newCanon.split('\n')
            newRun = lines.mapIndexed { i, t ->
                val m = run.getOrNull(i)
                if (m != null) m.copy(text = t, spans = emptyList()) else newBlock(cont, t, indent = first.indent)
            }.withSpans(ngs)
        }

        // markdown shortcuts: "- ", "# ", "[] ", "1. ", "> " typed at the start of a paragraph
        var converted = false
        if (d.inserted in 1..3 && !insertedText.contains('\n') && l1 == l2 && l1 < newRun.size) {
            val b = newRun[l1]
            if (b.type == BlockType.PARAGRAPH) {
                val hit = shortcuts.firstOrNull { it.first == b.text }
                if (hit != null) {
                    newRun = newRun.toMutableList().also {
                        it[l1] = b.copy(type = hit.second, text = "", spans = emptyList(), checked = false)
                    }
                    converted = true
                }
            }
        }

        val finalCanon = newRun.canonical()
        val sel = if (converted) {
            TextRange(newRun.lineStarts()[l1])
        } else {
            TextRange(selS.coerceAtMost(finalCanon.length), selE.coerceAtMost(finalCanon.length))
        }
        fields[runKey] = TextFieldValue(
            finalCanon + TERM, sel, if (clean && finalCanon == newCanon) incoming.composition else null,
        )
        replaceRun(s0, run.size, newRun)
        pending.clear()
        markDirty()
    }

    /**
     * Backspace pressed with the caret at the very start of a run (nothing before it in the field).
     * Returns true if it did something.
     */
    fun backspaceAtRunStart(runKey: String): Boolean {
        val s0 = blocks.indexOfFirst { it.id == runKey }
        if (s0 < 0) return false
        val b = blocks[s0]
        when {
            b.indent > 0 && b.type.isList -> {
                blocks = blocks.mapIndexed { i, x -> if (i == s0) x.copy(indent = x.indent - 1) else x }
            }
            b.type != BlockType.PARAGRAPH -> {
                blocks = blocks.mapIndexed { i, x ->
                    if (i == s0) x.copy(type = BlockType.PARAGRAPH, checked = false, indent = 0) else x
                }
            }
            s0 > 0 && blocks[s0 - 1].type == BlockType.DIVIDER -> {
                blocks = blocks.filterIndexed { i, _ -> i != s0 - 1 }
                markDirty()
                focusBlock(b.id, 0)
                return true
            }
            else -> return false
        }
        markDirty()
        return true
    }

    // ---------- formatting (works across all selected lines) ----------

    private fun selectionOf(key: String): TextRange? = fields[key]?.selection

    private fun covered(run: List<EditorBlock>, gs: List<Span>, fmt: Fmt, s: Int, e: Int): Boolean {
        val starts = run.lineStarts()
        var any = false
        run.forEachIndexed { i, b ->
            val a = max(s, starts[i])
            val z = min(e, starts[i] + b.text.length)
            if (z > a) {
                any = true
                if (!Spans.covers(gs, fmt, a, z)) return false
            }
        }
        return any
    }

    fun toggleFmt(fmt: Fmt) {
        val key = focusedKey ?: return
        val s0 = blocks.indexOfFirst { it.id == key }
        val run = runBlocks(key)
        val sel = selectionOf(key)
        if (s0 < 0 || run.isEmpty() || sel == null) return
        val s = min(sel.start, sel.end)
        val e = max(sel.start, sel.end)
        if (e > s) {
            val gs = run.globalSpans()
            val on = !covered(run, gs, fmt, s, e)
            replaceRun(s0, run.size, run.withSpans(Spans.apply(gs, fmt, s, e, on)))
            markDirty()
        } else {
            val now = pending[fmt] ?: Spans.activeAt(run.globalSpans(), s).contains(fmt)
            pending[fmt] = !now
        }
    }

    fun activeFmts(): Set<Fmt> {
        val key = focusedKey ?: return emptySet()
        val run = runBlocks(key)
        val sel = selectionOf(key)
        if (run.isEmpty() || sel == null) return emptySet()
        val s = min(sel.start, sel.end)
        val e = max(sel.start, sel.end)
        val gs = run.globalSpans()
        if (e > s) return Fmt.entries.filter { covered(run, gs, it, s, e) }.toSet()
        var out = Spans.activeAt(gs, s)
        pending.forEach { (f, on) -> out = if (on) out + f else out - f }
        return out
    }

    /** Global block indices covered by the current selection. */
    private fun selectedBlockIndices(): IntRange? {
        val key = focusedKey ?: return null
        val s0 = blocks.indexOfFirst { it.id == key }
        val run = runBlocks(key)
        val sel = selectionOf(key)
        if (s0 < 0 || run.isEmpty() || sel == null) return null
        val starts = run.lineStarts()
        val lo = min(sel.start, sel.end)
        val hi = max(sel.start, sel.end)
        val a = lineAt(starts, lo)
        var b = lineAt(starts, hi)
        if (b > a && hi == starts[b]) b--
        return (s0 + a)..(s0 + b)
    }

    fun activeType(): BlockType? {
        val r = selectedBlockIndices() ?: return null
        val t = blocks.getOrNull(r.first)?.type ?: return null
        return if (r.all { blocks.getOrNull(it)?.type == t }) t else null
    }

    fun setType(type: BlockType) {
        val r = selectedBlockIndices() ?: return
        val allSame = r.all { blocks.getOrNull(it)?.type == type }
        val target = if (allSame) BlockType.PARAGRAPH else type
        blocks = blocks.mapIndexed { i, b ->
            if (i in r && b.type.isText) {
                b.copy(type = target, checked = false, indent = if (target.isList) b.indent else 0)
            } else b
        }
        markDirty()
    }

    fun changeIndent(delta: Int) {
        val r = selectedBlockIndices() ?: return
        blocks = blocks.mapIndexed { i, b ->
            if (i in r && b.type.isList) b.copy(indent = (b.indent + delta).coerceIn(0, 3)) else b
        }
        markDirty()
    }

    fun toggleChecked(blockId: String) {
        blocks = blocks.map { if (it.id == blockId) it.copy(checked = !it.checked) else it }
        markDirty()
    }

    // ---------- images ----------

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val path = repo.importImage(uri) ?: return@launch
            insertImage(path)
        }
    }

    private fun insertImage(path: String) {
        val img = newBlock(BlockType.IMAGE, attachment = path)
        val list = blocks.toMutableList()
        val key = focusedKey
        var insertAt = list.size
        if (key != null) {
            val s = list.indexOfFirst { it.id == key }
            val run = runBlocks(key)
            if (s >= 0 && run.isNotEmpty()) {
                val sel = selectionOf(key) ?: TextRange(run.canonical().length)
                insertAt = s + lineAt(run.lineStarts(), min(sel.start, sel.end)) + 1
            }
        }
        list.add(insertAt, img)
        if (insertAt + 1 >= list.size || !list[insertAt + 1].type.isText) list.add(insertAt + 1, newBlock(BlockType.PARAGRAPH))
        blocks = list
        markDirty()
        focusBlock(list[insertAt + 1].id, 0)
    }

    fun removeBlock(id: String) {
        val list = blocks.filter { it.id != id }.toMutableList()
        if (list.isEmpty() || !list.last().type.isText) list.add(newBlock(BlockType.PARAGRAPH))
        blocks = list
        markDirty()
    }

    // ---------- note actions ----------

    fun togglePin() {
        val n = note.value ?: return
        viewModelScope.launch { repo.setPinned(noteId, !n.pinned) }
    }

    fun setLocked(locked: Boolean) {
        viewModelScope.launch { repo.setLocked(noteId, locked) }
    }

    fun move(folderId: String?) {
        viewModelScope.launch { repo.moveNote(noteId, folderId) }
    }

    suspend fun markdown(): String {
        saveNow()
        return repo.noteAsMarkdown(noteId)
    }

    /** Moves the note to Trash (Home shows Undo), then calls [onDone]. */
    fun trash(onDone: () -> Unit) {
        viewModelScope.launch {
            if (isEmptyNote()) {
                onLeave()
            } else {
                saveNow()
                left = true
                repo.trashNote(noteId)
            }
            onDone()
        }
    }
}
