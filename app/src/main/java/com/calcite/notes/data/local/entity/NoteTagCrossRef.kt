package com.calcite.notes.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"]
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long
)
