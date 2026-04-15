package com.calcite.notes.model

data class Note(
    val id: Long,
    val title: String,
    val summary: String?,
    val folder_id: Long?,
    val created_at: String,
    val updated_at: String
)

data class NoteDetail(
    val id: Long,
    val title: String,
    val content: String,
    val summary: String?,
    val folder_id: Long?,
    val created_at: String,
    val updated_at: String?
)

data class CreateNoteRequest(
    val title: String,
    val content: String,
    val summary: String? = null,
    val folder_id: Long = 0
)

data class UpdateNoteRequest(
    val note_id: Long,
    val title: String? = null,
    val content: String? = null,
    val summary: String? = null,
    val folder_id: Long? = null
)

data class DeleteNoteRequest(
    val note_id: Long
)

data class NoteCreateData(
    val note_id: Long
)
