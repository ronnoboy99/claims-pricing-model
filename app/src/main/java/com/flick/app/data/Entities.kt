package com.flick.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorKey: String,
    val position: Double,
)

@Entity(tableName = "notes", indices = [Index("folderId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String?,
    val pinned: Boolean,
    val pinnedAt: Long?,
    val locked: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long?,
    val previewText: String,
    val checklistTotal: Int,
    val checklistDone: Int,
)

@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("noteId")],
)
data class BlockEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val type: String,
    val text: String,
    val spansJson: String,
    val checked: Boolean,
    val indent: Int,
    val position: Double,
    val attachmentUri: String?,
)
