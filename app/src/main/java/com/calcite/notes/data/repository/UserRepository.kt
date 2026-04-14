package com.calcite.notes.data.repository

import com.calcite.notes.data.local.TokenDataStore
import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.UserProfile
import com.calcite.notes.utils.Result

class UserRepository(
    private val apiService: ApiService,
    private val tokenDataStore: TokenDataStore
) {

    suspend fun getUserProfile(): Result<UserProfile> {
        return try {
            val response = apiService.getUserProfile()
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun logout() {
        tokenDataStore.clearToken()
    }
}
