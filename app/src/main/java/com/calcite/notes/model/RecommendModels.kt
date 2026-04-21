package com.calcite.notes.model

data class RecommendNoteItem(
    val id: Long,
    val title: String,
    val summary: String?,
    val created_at: String,
    val updated_at: String
)
