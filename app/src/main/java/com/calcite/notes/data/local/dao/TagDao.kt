package com.calcite.notes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.calcite.notes.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name")
    fun getAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun getAllSync(): List<TagEntity>

    @Query("SELECT t.* FROM tags t INNER JOIN note_tags nt ON t.id = nt.tagId WHERE nt.noteId = :noteId ORDER BY t.name")
    fun getTagsForNote(noteId: Long): Flow<List<TagEntity>>

    @Query("SELECT t.* FROM tags t INNER JOIN note_tags nt ON t.id = nt.tagId WHERE nt.noteId = :noteId ORDER BY t.name")
    suspend fun getTagsForNoteSync(noteId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagEntity>)

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteById(tagId: Long)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}
