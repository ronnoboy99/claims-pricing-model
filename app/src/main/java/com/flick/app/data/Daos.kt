package com.flick.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY position")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY position")
    suspend fun getAll(): List<FolderEntity>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :id")
    suspend fun clearFolder(id: String)

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun count(): Int
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE trashedAt IS NULL")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun observeTrashed(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: String): Flow<NoteEntity?>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM notes WHERE trashedAt IS NOT NULL")
    suspend fun deleteAllTrashed()

    @Query("DELETE FROM notes WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun deleteTrashedBefore(before: Long)

    @Query("UPDATE notes SET trashedAt = :ts WHERE id = :id")
    suspend fun setTrashed(id: String, ts: Long?)

    @Query("UPDATE notes SET pinned = :pinned, pinnedAt = :at WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, at: Long?)

    @Query("UPDATE notes SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: String, locked: Boolean)

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: String, folderId: String?)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    /** Full-text search. Runs against the note_search FTS4 table (created in FlickDatabase). */
    @RawQuery(observedEntities = [NoteEntity::class])
    fun searchRaw(query: SupportSQLiteQuery): Flow<List<String>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE trashedAt IS NULL AND locked = 0 ORDER BY updatedAt DESC")
    suspend fun getExportable(): List<NoteEntity>
}

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks WHERE noteId = :noteId ORDER BY position")
    suspend fun get(noteId: String): List<BlockEntity>

    @Query("SELECT * FROM blocks WHERE noteId = :noteId ORDER BY position")
    fun observe(noteId: String): Flow<List<BlockEntity>>

    @Query("DELETE FROM blocks WHERE noteId = :noteId")
    suspend fun deleteFor(noteId: String)

    @Insert
    suspend fun insertAll(blocks: List<BlockEntity>)

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun getById(id: String): BlockEntity?

    @Query("UPDATE blocks SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: String, checked: Boolean)
}
