package com.calcite.notes.data.repository

import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.SearchResultItem
import com.calcite.notes.utils.Result

class SearchRepository(private val apiService: ApiService) {

    suspend fun search(keyword: String, from: Int = 0, size: Int = 20): Result<List<SearchResultItem>> {
        return try {
            val response = apiService.searchNotes(keyword, from, size)
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
