package com.calcite.notes.model

data class Tag(
    val id: Long,
    val name: String,
    val created_at: String
)

data class HotTagItem(
    val tag: String,
    val count: Int
)
