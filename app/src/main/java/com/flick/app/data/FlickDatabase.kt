package com.flick.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, NoteEntity::class, BlockEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FlickDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun noteDao(): NoteDao
    abstract fun blockDao(): BlockDao

    companion object {
        /**
         * Full-text index. It lives outside Room's schema so existing installs keep their data:
         * the table is created on open if it does not exist yet. Prefix matching, unicode-aware.
         */
        private const val CREATE_FTS =
            "CREATE VIRTUAL TABLE IF NOT EXISTS note_search USING fts4(noteId, title, body, notindexed=noteId, tokenize=unicode61)"

        fun build(context: Context): FlickDatabase =
            Room.databaseBuilder(context, FlickDatabase::class.java, "flick.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(CREATE_FTS)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL(CREATE_FTS)
                    }
                })
                .build()
    }
}
