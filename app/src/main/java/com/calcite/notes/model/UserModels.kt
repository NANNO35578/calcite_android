package com.calcite.notes.model

data class UserProfile(
    val user_id: Long,
    val username: String,
    val email: String,
    val created_at: String
)
