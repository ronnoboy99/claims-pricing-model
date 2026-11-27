package com.flick.app.ui.editor

import com.flick.app.data.Fmt
import com.flick.app.data.Span
import kotlin.math.max
import kotlin.math.min

/** Inline formatting spans, in model coordinates (no sentinel offset). */
object Spans {
    fun merge(list: List<Span>): List<Span> {
        val out = mutableListOf<Span>()
        for (fmt in Fmt.entries) {
            val sorted = list.filter { it.fmt == fmt && it.end > it.start }.sortedBy { it.start }
            var cur: Span? = null
            for (s in sorted) {
                val c = cur
                if (c == null) cur = s
                else if (s.start <= c.end) cur = c.copy(end = max(c.end, s.end))
                else {
                    out += c
                    cur = s
                }
            }
            cur?.let { out += it }
        }
        return out
    }

    fun covers(spans: List<Span>, fmt: Fmt, start: Int, end: Int): Boolean =
        spans.any { it.fmt == fmt && it.start <= start && it.end >= end }

    fun apply(spans: List<Span>, fmt: Fmt, start: Int, end: Int, on: Boolean): List<Span> {
        if (end <= start) return spans
        val others = spans.filter { it.fmt != fmt }
        val mine = mutableListOf<Span>()
        for (s in spans.filter { it.fmt == fmt }) {
            if (s.start < start) mine += Span(s.start, min(s.end, start), fmt)
            if (s.end > end) mine += Span(max(s.start, end), s.end, fmt)
        }
        if (on) mine += Span(start, end, fmt)
        return merge(others + mine)
    }

    fun shift(spans: List<Span>, by: Int): List<Span> = spans.map { it.copy(start = it.start + by, end = it.end + by) }

    /** Spans intersecting [from, to), shifted so [from] becomes 0. */
    fun slice(spans: List<Span>, from: Int, to: Int): List<Span> = spans.mapNotNull { s ->
        val a = max(s.start, from)
        val b = min(s.end, to)
        if (b > a) Span(a - from, b - from, s.fmt) else null
    }

    /** Formats active at a caret: covering the character before it. */
    fun activeAt(spans: List<Span>, caret: Int): Set<Fmt> =
        spans.filter { it.start < caret && it.end >= caret }.map { it.fmt }.toSet()

    /**
     * Adjust spans after a text edit. The edit replaced [removed] characters at [prefix]
     * with [inserted] characters. [inherit] are formats the new text should carry.
     */
    fun adjust(spans: List<Span>, prefix: Int, removed: Int, inserted: Int, inherit: Set<Fmt>): List<Span> {
        val delta = inserted - removed
        val tail = prefix + removed
        var out = spans.mapNotNull { s ->
            val ns = when {
                s.start < prefix -> s.start
                s.start >= tail -> s.start + delta
                else -> prefix + inserted
            }
            val ne = when {
                s.end <= prefix -> s.end
                s.end >= tail -> s.end + delta
                else -> prefix
            }
            if (ne > ns) Span(ns, ne, s.fmt) else null
        }
        if (inserted > 0) {
            for (f in inherit) out = apply(out, f, prefix, prefix + inserted, true)
        }
        return merge(out)
    }
}
