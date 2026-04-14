package com.calcite.notes.model

data class FileItem(
    val id: Long,
    val user_id: Long,
    val note_id: Long,
    val file_name: String,
    val file_type: String,
    val file_size: Long,
    val file_size_formatted: String,
    val object_key: String,
    val url: String,
    val status: String, // processing / done / failed
    val created_at: String,
    val updated_at: String
)
