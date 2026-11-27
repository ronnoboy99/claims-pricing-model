package com.flick.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class NotesRepository(
    private val db: FlickDatabase,
    private val scope: CoroutineScope,
    private val context: Context,
) {
    private val notes = db.noteDao()
    private val blocks = db.blockDao()
    private val folders = db.folderDao()

    /** Emits the id of a note whenever it is moved to Trash, so Home can offer Undo. */
    val trashEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun observeNotes(): Flow<List<NoteEntity>> = notes.observeActive()
    fun observeTrash(): Flow<List<NoteEntity>> = notes.observeTrashed()
    fun observeNote(id: String): Flow<NoteEntity?> = notes.observe(id)
    fun observeFolders(): Flow<List<FolderEntity>> = folders.observeAll()
    fun observeBlocks(noteId: String): Flow<List<BlockEntity>> = blocks.observe(noteId)
    /**
     * Full-text search (SQLite FTS4). Every word in the query must match the start of a word
     * in the title or body, so "rail pa" finds "Rail pass". Locked notes match on title only.
     */
    fun searchIds(query: String): Flow<List<String>> {
        val tokens = query.lowercase()
            .split(Regex("\\s+"))
            .map { t -> t.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return flowOf(emptyList())
        val match = tokens.joinToString(" ") { "$it*" }
        return notes.searchRaw(
            SimpleSQLiteQuery(
                "SELECT id FROM notes WHERE trashedAt IS NULL AND id IN " +
                    "(SELECT noteId FROM note_search WHERE note_search MATCH ?)",
                arrayOf<Any>(match),
            )
        )
    }

    // ---------- search index (kept in sync inside the same transactions as the notes) ----------

    private val sqlDb get() = db.openHelper.writableDatabase

    private fun indexNote(id: String, title: String, list: List<BlockEntity>, locked: Boolean) {
        val body = if (locked) "" else list
            .filter { BlockType.from(it.type).isText && it.text.isNotBlank() }
            .joinToString(" ") { it.text }
        sqlDb.execSQL("DELETE FROM note_search WHERE noteId = ?", arrayOf<Any>(id))
        sqlDb.execSQL("INSERT INTO note_search(noteId, title, body) VALUES (?, ?, ?)", arrayOf<Any>(id, title, body))
    }

    private fun unindex(id: String) {
        sqlDb.execSQL("DELETE FROM note_search WHERE noteId = ?", arrayOf<Any>(id))
    }

    private suspend fun reindex(id: String) {
        val n = notes.get(id) ?: return
        indexNote(id, n.title, blocks.get(id), n.locked)
    }

    /** Builds the index for existing notes (first launch after this update, or after seeding). */
    suspend fun rebuildSearchIndexIfNeeded() {
        db.withTransaction {
            val all = notes.getAll()
            var count = 0
            sqlDb.query("SELECT COUNT(*) FROM note_search").use { c ->
                if (c.moveToFirst()) count = c.getInt(0)
            }
            if (count != all.size) {
                sqlDb.execSQL("DELETE FROM note_search")
                all.forEach { n -> indexNote(n.id, n.title, blocks.get(n.id), n.locked) }
            }
        }
    }

    suspend fun getNote(id: String): NoteEntity? = notes.get(id)
    suspend fun getBlocks(id: String): List<BlockEntity> = blocks.get(id)

    private fun newId() = UUID.randomUUID().toString()

    private fun block(
        noteId: String,
        type: BlockType,
        text: String,
        position: Double,
        checked: Boolean = false,
        indent: Int = 0,
        attachment: String? = null,
    ) = BlockEntity(newId(), noteId, type.name, text, "[]", checked, indent, position, attachment)

    // ---------- notes ----------

    suspend fun createNote(
        folderId: String?,
        first: BlockType = BlockType.PARAGRAPH,
        imagePath: String? = null,
    ): String {
        val id = newId()
        val now = System.currentTimeMillis()
        val list = if (imagePath != null) {
            listOf(
                block(id, BlockType.IMAGE, "", 0.0, attachment = imagePath),
                block(id, BlockType.PARAGRAPH, "", 1.0),
            )
        } else {
            listOf(block(id, first, "", 0.0))
        }
        db.withTransaction {
            notes.upsert(
                NoteEntity(
                    id = id, title = "", folderId = folderId, pinned = false, pinnedAt = null,
                    locked = false, createdAt = now, updatedAt = now, trashedAt = null,
                    previewText = "", checklistTotal = 0, checklistDone = 0,
                )
            )
            blocks.insertAll(list)
        }
        return id
    }

    suspend fun saveNote(id: String, title: String, list: List<BlockEntity>) {
        db.withTransaction {
            val existing = notes.get(id) ?: return@withTransaction
            val checks = list.filter { it.type == BlockType.CHECKLIST.name }
            val preview = list
                .filter { BlockType.from(it.type).isText && it.text.isNotBlank() }
                .joinToString("\n") { it.text }
                .take(240)
            notes.upsert(
                existing.copy(
                    title = title,
                    updatedAt = System.currentTimeMillis(),
                    previewText = preview,
                    checklistTotal = checks.size,
                    checklistDone = checks.count { it.checked },
                )
            )
            blocks.deleteFor(id)
            blocks.insertAll(list.mapIndexed { i, b -> b.copy(noteId = id, position = i.toDouble()) })
            indexNote(id, title, list, existing.locked)
        }
    }

    /** Runs on the app scope so it survives the editor being closed. */
    fun finishEditing(id: String, title: String, list: List<BlockEntity>, isEmpty: Boolean) {
        scope.launch {
            if (isEmpty) {
                db.withTransaction {
                    unindex(id)
                    notes.delete(id)
                }
            } else {
                saveNote(id, title, list)
            }
        }
    }

    fun saveInBackground(id: String, title: String, list: List<BlockEntity>) {
        scope.launch { saveNote(id, title, list) }
    }

    suspend fun toggleBlockChecked(blockId: String) {
        db.withTransaction {
            val b = blocks.getById(blockId) ?: return@withTransaction
            blocks.setChecked(blockId, !b.checked)
            val n = notes.get(b.noteId) ?: return@withTransaction
            val checks = blocks.get(b.noteId).filter { it.type == BlockType.CHECKLIST.name }
            notes.upsert(
                n.copy(
                    updatedAt = System.currentTimeMillis(),
                    checklistTotal = checks.size,
                    checklistDone = checks.count { it.checked },
                )
            )
        }
    }

    suspend fun trashNote(id: String) {
        notes.setTrashed(id, System.currentTimeMillis())
        trashEvents.tryEmit(id)
    }

    suspend fun restoreNote(id: String) = notes.setTrashed(id, null)
    suspend fun deleteForever(id: String) {
        db.withTransaction {
            unindex(id)
            notes.delete(id)
        }
    }

    suspend fun emptyTrash() {
        db.withTransaction {
            sqlDb.execSQL("DELETE FROM note_search WHERE noteId IN (SELECT id FROM notes WHERE trashedAt IS NOT NULL)")
            notes.deleteAllTrashed()
        }
    }

    suspend fun purgeOldTrash() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        db.withTransaction {
            sqlDb.execSQL(
                "DELETE FROM note_search WHERE noteId IN (SELECT id FROM notes WHERE trashedAt IS NOT NULL AND trashedAt < ?)",
                arrayOf<Any>(cutoff),
            )
            notes.deleteTrashedBefore(cutoff)
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) =
        notes.setPinned(id, pinned, if (pinned) System.currentTimeMillis() else null)

    suspend fun setLocked(id: String, locked: Boolean) {
        db.withTransaction {
            notes.setLocked(id, locked)
            reindex(id)
        }
    }
    suspend fun moveNote(id: String, folderId: String?) = notes.setFolder(id, folderId)

    // ---------- folders ----------

    suspend fun createFolder(name: String, colorKey: String) {
        val pos = (folders.getAll().maxOfOrNull { it.position } ?: 0.0) + 1.0
        folders.upsert(FolderEntity(newId(), name.trim().ifBlank { "Folder" }, colorKey, pos))
    }

    suspend fun updateFolder(folder: FolderEntity) = folders.upsert(folder)

    suspend fun deleteFolder(id: String) {
        db.withTransaction {
            folders.clearFolder(id)
            folders.delete(id)
        }
    }

    suspend fun moveFolder(id: String, dir: Int) {
        val all = folders.getAll()
        val i = all.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j < 0 || j >= all.size) return
        val a = all[i]
        val b = all[j]
        folders.upsert(a.copy(position = b.position))
        folders.upsert(b.copy(position = a.position))
    }

    // ---------- images ----------

    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val file = File(dir, "${newId()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { out -> input.copyTo(out) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

    // ---------- export ----------

    suspend fun noteAsMarkdown(id: String): String {
        val n = notes.get(id) ?: return ""
        return toMarkdown(n.title, blocks.get(id))
    }

    suspend fun exportAllMarkdown(): String =
        notes.getExportable().map { toMarkdown(it.title, blocks.get(it.id)) }.joinToString("\n\n---\n\n")

    private fun toMarkdown(title: String, list: List<BlockEntity>): String {
        val sb = StringBuilder()
        if (title.isNotBlank()) sb.append("# ").append(title).append("\n\n")
        var num = 0
        list.forEach { b ->
            val type = BlockType.from(b.type)
            if (type != BlockType.NUMBERED) num = 0
            sb.append("  ".repeat(b.indent))
            when (type) {
                BlockType.PARAGRAPH -> sb.append(b.text)
                BlockType.HEADING -> sb.append("## ").append(b.text)
                BlockType.CHECKLIST -> sb.append(if (b.checked) "- [x] " else "- [ ] ").append(b.text)
                BlockType.BULLET -> sb.append("- ").append(b.text)
                BlockType.NUMBERED -> {
                    num++
                    sb.append(num).append(". ").append(b.text)
                }
                BlockType.QUOTE -> sb.append("> ").append(b.text)
                BlockType.DIVIDER -> sb.append("---")
                BlockType.IMAGE -> sb.append("[image]")
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    // ---------- seed ----------

    private data class Seed(val type: BlockType, val text: String, val checked: Boolean = false)

    private suspend fun insertSeed(
        title: String,
        folderId: String?,
        pinned: Boolean,
        ageHours: Long,
        items: List<Seed>,
    ) {
        val id = newId()
        val now = System.currentTimeMillis() - ageHours * 60 * 60 * 1000
        val list = items.mapIndexed { i, s -> block(id, s.type, s.text, i.toDouble(), s.checked) }
        val checks = list.filter { it.type == BlockType.CHECKLIST.name }
        notes.upsert(
            NoteEntity(
                id = id, title = title, folderId = folderId, pinned = pinned,
                pinnedAt = if (pinned) now else null, locked = false, createdAt = now, updatedAt = now,
                trashedAt = null,
                previewText = list.filter { BlockType.from(it.type).isText }.joinToString("\n") { it.text }.take(240),
                checklistTotal = checks.size, checklistDone = checks.count { it.checked },
            )
        )
        blocks.insertAll(list)
    }

    suspend fun seedIfEmpty() {
        if (notes.count() > 0 || folders.count() > 0) return
        val travel = newId()
        val health = newId()
        val ideas = newId()
        folders.upsert(FolderEntity(travel, "Travel", "sky", 1.0))
        folders.upsert(FolderEntity(health, "Health", "mint", 2.0))
        folders.upsert(FolderEntity(ideas, "Ideas", "peach", 3.0))
        val p = BlockType.PARAGRAPH
        val c = BlockType.CHECKLIST
        val b = BlockType.BULLET
        val h = BlockType.HEADING
        insertSeed(
            "Trip to Japan", travel, true, 1,
            listOf(
                Seed(p, "Two weeks in April. Landing in Tokyo, ending in Osaka."),
                Seed(h, "Packing"),
                Seed(c, "Passport + visa", true),
                Seed(c, "Pocket wifi", true),
                Seed(c, "Rail pass"),
                Seed(c, "Power adapters"),
                Seed(h, "Places"),
                Seed(b, "Shibuya Sky at sunset"),
                Seed(b, "teamLab Planets"),
                Seed(b, "Fushimi Inari, early"),
            ),
        )
        insertSeed(
            "Gym split", health, true, 26,
            listOf(
                Seed(p, "Push, pull, legs. Add deadlifts on Friday."),
                Seed(c, "Push day", true),
                Seed(c, "Pull day", true),
                Seed(c, "Leg day", true),
                Seed(c, "Mobility"),
                Seed(c, "Deadlifts Friday"),
            ),
        )
        insertSeed(
            "App ideas", ideas, false, 50,
            listOf(
                Seed(b, "Haptic-first timer"),
                Seed(b, "Tiny habit tracker"),
                Seed(b, "Flashcards that flick"),
            ),
        )
        insertSeed(
            "Groceries", null, false, 74,
            listOf(
                Seed(c, "Oats"),
                Seed(c, "Eggs"),
                Seed(c, "Rice"),
                Seed(c, "Bananas"),
            ),
        )
        insertSeed(
            "Welcome to Flick", null, false, 98,
            listOf(
                Seed(p, "Pull down on the list to start a new note."),
                Seed(p, "Long-press a note for options. Long-press the + button for quick capture."),
                Seed(p, "Switch to Flick view (top right) and flick through your notes. Flick up or down to change folder."),
                Seed(c, "Try checking this off"),
            ),
        )
    }
}
