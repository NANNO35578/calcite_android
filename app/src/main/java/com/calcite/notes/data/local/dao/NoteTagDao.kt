package com.calcite.notes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.calcite.notes.data.local.entity.NoteTagCrossRef

@Dao
interface NoteTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossRef: NoteTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: Long)

    @Query("DELETE FROM note_tags WHERE tagId = :tagId")
    suspend fun deleteByTagId(tagId: Long)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun delete(noteId: Long, tagId: Long)

    @Query("DELETE FROM note_tags")
    suspend fun deleteAll()
}
