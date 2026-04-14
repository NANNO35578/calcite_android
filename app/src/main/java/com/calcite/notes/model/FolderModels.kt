package com.calcite.notes.model

data class Folder(
    val id: Long,
    val name: String,
    val parent_id: Long,
    val created_at: String
)

data class CreateFolderRequest(
    val name: String,
    val parent_id: Long = 0
)

data class UpdateFolderRequest(
    val folder_id: Long,
    val name: String? = null,
    val parent_id: Long? = null
)

data class DeleteFolderRequest(
    val folder_id: Long
)

data class FolderCreateData(
    val folder_id: Long
)
