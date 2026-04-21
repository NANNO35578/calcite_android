package com.calcite.notes.model

data class SearchResultItem(
    val id: Long,
    val title: String,
    val summary: String?,
    val folder_id: Long,
    val created_at: String,
    val updated_at: String,
    val highlight_title: String?,
    val highlight_content: String?,
    val score: Double,
    val author_id: Long = 0,
    val is_public: Int = 0
)
