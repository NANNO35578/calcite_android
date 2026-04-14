package com.calcite.notes.model

data class Tag(
    val id: Long,
    val name: String,
    val created_at: String
)

data class CreateTagRequest(
    val name: String
)

data class UpdateTagRequest(
    val tag_id: Long,
    val name: String
)

data class DeleteTagRequest(
    val tag_id: Long
)

data class BindTagRequest(
    val note_id: Long,
    val tag_ids: List<Long>
)

data class TagCreateData(
    val tag_id: Long
)
