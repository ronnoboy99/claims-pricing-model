package com.flick.app.ui.editor

import com.flick.app.data.BlockEntity
import com.flick.app.data.BlockType
import com.flick.app.data.Span
import com.flick.app.data.SpanJson
import java.util.UUID

/**
 * Zero-width terminator appended to the text of every text run. It keeps the last (possibly empty)
 * line from being an empty paragraph, so list indentation and the caret position stay correct.
 * It is never saved and never part of a block's text.
 */
const val TERM = "\u200B"

data class EditorBlock(
    val id: String,
    val type: BlockType,
    val text: String,
    /** Formatting spans, local to this block's text. */
    val spans: List<Span>,
    val checked: Boolean,
    val indent: Int,
    val attachment: String?,
)

fun newBlock(
    type: BlockType,
    text: String = "",
    spans: List<Span> = emptyList(),
    checked: Boolean = false,
    indent: Int = 0,
    attachment: String? = null,
) = EditorBlock(UUID.randomUUID().toString(), type, text, spans, checked, indent, attachment)

fun BlockEntity.toEditor(): EditorBlock {
    val t = BlockType.from(type)
    return EditorBlock(
        id = id,
        type = t,
        text = if (t.isText) text else "",
        spans = if (t.isText) SpanJson.decode(spansJson) else emptyList(),
        checked = checked,
        indent = indent,
        attachment = attachmentUri,
    )
}

fun EditorBlock.toEntity(noteId: String, position: Int) = BlockEntity(
    id = id,
    noteId = noteId,
    type = type.name,
    text = if (type.isText) text else "",
    spansJson = SpanJson.encode(if (type.isText) spans else emptyList()),
    checked = checked,
    indent = indent,
    position = position.toDouble(),
    attachmentUri = attachment,
)

/**
 * A run is a group of consecutive text blocks that share ONE text field, so a selection can span
 * several lines. Images and dividers sit between runs. The run key is the id of its first block.
 */
data class Run(val key: String, val blocks: List<EditorBlock>)

sealed interface DocItem {
    data class TextRun(val run: Run) : DocItem
    data class Media(val block: EditorBlock) : DocItem
}

fun buildItems(blocks: List<EditorBlock>): List<DocItem> {
    val out = mutableListOf<DocItem>()
    var cur = mutableListOf<EditorBlock>()
    for (b in blocks) {
        if (b.type.isText) {
            cur.add(b)
        } else {
            if (cur.isNotEmpty()) {
                out.add(DocItem.TextRun(Run(cur[0].id, cur.toList())))
                cur = mutableListOf()
            }
            out.add(DocItem.Media(b))
        }
    }
    if (cur.isNotEmpty()) out.add(DocItem.TextRun(Run(cur[0].id, cur.toList())))
    return out
}

// ---- helpers on a list of consecutive text blocks (one run) ----

/** The run's text: one line per block. */
fun List<EditorBlock>.canonical(): String = joinToString("\n") { it.text }

/** Offset where each block's line starts in [canonical]. */
fun List<EditorBlock>.lineStarts(): IntArray {
    val a = IntArray(size)
    var off = 0
    forEachIndexed { i, b ->
        a[i] = off
        off += b.text.length + 1
    }
    return a
}

/** Index of the line containing [off] (a line owns its trailing newline position). */
fun lineAt(starts: IntArray, off: Int): Int {
    var i = starts.size - 1
    while (i > 0 && starts[i] > off) i--
    return i.coerceAtLeast(0)
}

/** All spans of the run in run coordinates. */
fun List<EditorBlock>.globalSpans(): List<Span> {
    val starts = lineStarts()
    return Spans.merge(flatMapIndexed { i, b -> Spans.shift(b.spans, starts[i]) })
}

/** Re-distributes run-wide spans back onto the blocks (dropping the newline characters). */
fun List<EditorBlock>.withSpans(gs: List<Span>): List<EditorBlock> {
    val starts = lineStarts()
    return mapIndexed { i, b -> b.copy(spans = Spans.slice(gs, starts[i], starts[i] + b.text.length)) }
}
