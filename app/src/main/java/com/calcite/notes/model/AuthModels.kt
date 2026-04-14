package com.calcite.notes.model

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class LoginData(
    val user_id: Long,
    val username: String,
    val token: String
)

data class RegisterData(
    val user_id: Long,
    val token: String
)
