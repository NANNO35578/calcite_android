package com.calcite.notes.data.repository

import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.FileItem
import com.calcite.notes.utils.Result

class FileRepository(private val apiService: ApiService) {

    suspend fun getFileList(): Result<List<FileItem>> {
        return try {
            val response = apiService.getFileList()
            if (response.code == 0) {
                Result.Success(response.data ?: emptyList())
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }
}
