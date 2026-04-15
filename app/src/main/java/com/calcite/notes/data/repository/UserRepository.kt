package com.calcite.notes.data.repository

import android.content.Context
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.UserProfile
import com.calcite.notes.utils.NetworkUtils
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.first

class UserRepository(
    private val apiService: ApiService,
    private val appDataStore: AppDataStore,
    private val db: AppDatabase
) {

    suspend fun getUserProfile(context: Context): Result<UserProfile> {
        return if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                val response = apiService.getUserProfile()
                if (response.code == 0 && response.data != null) {
                    Result.Success(response.data)
                } else {
                    Result.Error(response.message)
                }
            } catch (e: Exception) {
                Result.Error(e.message ?: "网络请求失败")
            }
        } else {
            Result.Error("无网络连接")
        }
    }

    suspend fun logout() {
        appDataStore.clearAll()
        db.noteDao().deleteAll()
        db.tagDao().deleteAll()
        db.folderDao().deleteAll()
        db.fileDao().deleteAll()
        db.noteTagDao().deleteAll()
    }

    suspend fun isTokenValid(): Boolean {
        val token = appDataStore.token.first()
        val expireTime = appDataStore.tokenExpireTime.first()
        return !token.isNullOrEmpty() && System.currentTimeMillis() <= expireTime
    }
}
