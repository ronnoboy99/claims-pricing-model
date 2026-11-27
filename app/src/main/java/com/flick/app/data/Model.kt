package com.flick.app.data

import org.json.JSONArray
import org.json.JSONObject

enum class BlockType {
    PARAGRAPH, HEADING, CHECKLIST, BULLET, NUMBERED, QUOTE, IMAGE, DIVIDER;

    val isText: Boolean get() = this != IMAGE && this != DIVIDER
    val isList: Boolean get() = this == CHECKLIST || this == BULLET || this == NUMBERED

    companion object {
        fun from(name: String): BlockType = entries.firstOrNull { it.name == name } ?: PARAGRAPH
    }
}

enum class Fmt { BOLD, ITALIC, UNDERLINE, STRIKE }

data class Span(val start: Int, val end: Int, val fmt: Fmt)

enum class ViewMode { GRID, LIST, FLICK }
enum class SortMode { RECENT, CREATED, TITLE }

object SpanJson {
    fun encode(spans: List<Span>): String {
        val arr = JSONArray()
        spans.forEach {
            arr.put(JSONObject().put("s", it.start).put("e", it.end).put("t", it.fmt.name))
        }
        return arr.toString()
    }

    fun decode(json: String): List<Span> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val f = Fmt.entries.firstOrNull { it.name == o.getString("t") } ?: return@mapNotNull null
            Span(o.getInt("s"), o.getInt("e"), f)
        }
    }.getOrDefault(emptyList())
}
