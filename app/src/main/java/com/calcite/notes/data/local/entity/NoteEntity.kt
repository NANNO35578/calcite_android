package com.calcite.notes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: Long,
    val title: String,
    val content: String,
    val summary: String?,
    val folderId: Long,
    val createdAt: String,
    val updatedAt: String,
    val isDeleted: Boolean = false
)
