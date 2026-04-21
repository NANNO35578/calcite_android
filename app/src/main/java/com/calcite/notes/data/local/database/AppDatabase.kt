package com.calcite.notes.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.calcite.notes.data.local.dao.FileDao
import com.calcite.notes.data.local.dao.FolderDao
import com.calcite.notes.data.local.dao.NoteDao
import com.calcite.notes.data.local.dao.NoteTagDao
import com.calcite.notes.data.local.dao.TagDao
import com.calcite.notes.data.local.entity.FileEntity
import com.calcite.notes.data.local.entity.FolderEntity
import com.calcite.notes.data.local.entity.NoteEntity
import com.calcite.notes.data.local.entity.NoteTagCrossRef
import com.calcite.notes.data.local.entity.TagEntity

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        FolderEntity::class,
        FileEntity::class,
        NoteTagCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun noteTagDao(): NoteTagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN authorId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN isPublic INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calcite_notes.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
