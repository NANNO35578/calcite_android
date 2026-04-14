package com.calcite.notes.data.repository

import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.BindTagRequest
import com.calcite.notes.model.CreateTagRequest
import com.calcite.notes.model.DeleteTagRequest
import com.calcite.notes.model.Tag
import com.calcite.notes.model.TagCreateData
import com.calcite.notes.model.UpdateTagRequest
import com.calcite.notes.utils.Result

class TagRepository(private val apiService: ApiService) {

    suspend fun createTag(name: String): Result<TagCreateData> {
        return try {
            val response = apiService.createTag(CreateTagRequest(name))
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun updateTag(tagId: Long, name: String): Result<Unit> {
        return try {
            val response = apiService.updateTag(UpdateTagRequest(tagId, name))
            if (response.code == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun deleteTag(tagId: Long): Result<Unit> {
        return try {
            val response = apiService.deleteTag(DeleteTagRequest(tagId))
            if (response.code == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getAllTags(): Result<List<Tag>> {
        return try {
            val response = apiService.getTagList()
            if (response.code == 0) {
                Result.Success(response.data ?: emptyList())
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getTagsByNote(noteId: Long): Result<List<Tag>> {
        return try {
            val response = apiService.getTagList(noteId)
            if (response.code == 0) {
                Result.Success(response.data ?: emptyList())
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun bindTags(noteId: Long, tagIds: List<Long>): Result<Unit> {
        return try {
            val response = apiService.bindTags(BindTagRequest(noteId, tagIds))
            if (response.code == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }
}
