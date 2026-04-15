package com.calcite.notes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val parentId: Long,
    val createdAt: String
)
