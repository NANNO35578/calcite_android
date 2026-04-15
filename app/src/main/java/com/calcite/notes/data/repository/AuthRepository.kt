package com.calcite.notes.data.repository

import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.LoginData
import com.calcite.notes.model.LoginRequest
import com.calcite.notes.model.RegisterData
import com.calcite.notes.model.RegisterRequest
import com.calcite.notes.utils.Result

class AuthRepository(
    private val apiService: ApiService,
    private val appDataStore: AppDataStore
) {

    suspend fun login(username: String, password: String): Result<LoginData> {
        return try {
            val response = apiService.login(LoginRequest(username, password))
            if (response.code == 0 && response.data != null) {
                appDataStore.saveToken(response.data.token)
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun register(username: String, email: String, password: String): Result<RegisterData> {
        return try {
            val response = apiService.register(RegisterRequest(username, email, password))
            if (response.code == 0 && response.data != null) {
                appDataStore.saveToken(response.data.token)
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun logout() {
        appDataStore.clearToken()
        appDataStore.clearCurrentNoteId()
    }
}
