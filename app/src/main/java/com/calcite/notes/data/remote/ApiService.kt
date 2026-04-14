package com.calcite.notes.data.remote

import com.calcite.notes.model.ApiResponse
import com.calcite.notes.model.LoginData
import com.calcite.notes.model.LoginRequest
import com.calcite.notes.model.RegisterData
import com.calcite.notes.model.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginData>

    @POST("/api/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<RegisterData>
}
