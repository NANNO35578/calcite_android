package com.calcite.notes.data.repository

import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.CreateFolderRequest
import com.calcite.notes.model.DeleteFolderRequest
import com.calcite.notes.model.Folder
import com.calcite.notes.model.FolderCreateData
import com.calcite.notes.model.UpdateFolderRequest
import com.calcite.notes.utils.Result

class FolderRepository(private val apiService: ApiService) {

    suspend fun createFolder(name: String, parentId: Long = 0): Result<FolderCreateData> {
        return try {
            val response = apiService.createFolder(CreateFolderRequest(name, parentId))
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun updateFolder(folderId: Long, name: String? = null, parentId: Long? = null): Result<Unit> {
        return try {
            val response = apiService.updateFolder(UpdateFolderRequest(folderId, name, parentId))
            if (response.code == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun deleteFolder(folderId: Long): Result<Unit> {
        return try {
            val response = apiService.deleteFolder(DeleteFolderRequest(folderId))
            if (response.code == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getFolderList(parentId: Long = 0): Result<List<Folder>> {
        return try {
            val response = apiService.getFolderList(parentId)
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
