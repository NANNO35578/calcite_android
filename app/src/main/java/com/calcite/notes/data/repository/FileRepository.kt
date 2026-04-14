package com.calcite.notes.data.repository

import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.DeleteFileRequest
import com.calcite.notes.model.FileItem
import com.calcite.notes.model.FileStatusData
import com.calcite.notes.model.FileUploadData
import com.calcite.notes.utils.Result
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class FileRepository(private val apiService: ApiService) {

    suspend fun uploadFile(filePart: MultipartBody.Part, noteId: Long? = null): Result<FileUploadData> {
        return try {
            val noteBody = noteId?.let {
                it.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            }
            val response = apiService.uploadFile(filePart, noteBody)
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getFileList(status: String? = null): Result<List<FileItem>> {
        return try {
            val response = apiService.getFileList(status = status)
            if (response.code == 0) {
                Result.Success(response.data ?: emptyList())
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun deleteFile(fileId: Long): Result<Unit> {
        return try {
            val response = apiService.deleteFile(DeleteFileRequest(fileId))
            if (response.code == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getFileStatus(fileId: Long): Result<FileStatusData> {
        return try {
            val response = apiService.getFileStatus(fileId.toString())
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getFileInfo(fileId: Long): Result<FileItem> {
        return try {
            val response = apiService.getFileInfo(fileId.toString())
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }
}
