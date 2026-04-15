package com.calcite.notes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey
    val id: Long,
    val userId: Long,
    val noteId: Long,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val fileSizeFormatted: String,
    val objectKey: String,
    val url: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String
)
