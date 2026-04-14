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
    val status: String, // done / processing / failed
    val created_at: String,
    val updated_at: String
)

data class DeleteFileRequest(
    val file_id: Long
)

data class FileUploadData(
    val file_id: Long,
    val status: String
)

data class FileStatusData(
    val file_id: Long,
    val file_name: String,
    val status: String,
    val url: String? = null,
    val file_size: Long? = null,
    val file_size_formatted: String? = null
)

data class OcrRecognizeData(
    val file_id: Long,
    val status: String
)

data class OcrStatusData(
    val file_id: Long,
    val file_name: String,
    val status: String,
    val note_id: Long? = null,
    val url: String? = null,
    val file_size: Long? = null,
    val file_size_formatted: String? = null
)
