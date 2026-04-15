package com.calcite.notes.data.repository

import android.content.Context
import com.calcite.notes.data.local.dao.FileDao
import com.calcite.notes.data.local.entity.FileEntity
import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.DeleteFileRequest
import com.calcite.notes.model.FileItem
import com.calcite.notes.model.FileStatusData
import com.calcite.notes.model.FileUploadData
import com.calcite.notes.utils.NetworkUtils
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class FileRepository(
    private val apiService: ApiService,
    private val fileDao: FileDao
) {

    // =================== 本地数据源 ===================
    fun getFileListLocal(status: String? = null): Flow<List<FileItem>> {
        val flow = if (status != null) fileDao.getByStatus(status) else fileDao.getAll()
        return flow.map { list -> list.map { it.toFileItem() } }
    }

    // =================== 远程数据源（供 SyncWorker 使用） ===================
    suspend fun getFileListFromRemote(status: String? = null): Result<List<FileItem>> {
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

    // =================== 统一接口 ===================
    suspend fun uploadFile(context: Context, filePart: MultipartBody.Part, noteId: Long? = null): Result<FileUploadData> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法上传文件")
        }
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

    suspend fun syncAllFiles(context: Context): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val response = apiService.getFileList()
            if (response.code == 0) {
                val files = response.data ?: emptyList()
                fileDao.insertAll(files.map {
                    FileEntity(
                        id = it.id,
                        userId = it.user_id,
                        noteId = it.note_id,
                        fileName = it.file_name,
                        fileType = it.file_type,
                        fileSize = it.file_size,
                        fileSizeFormatted = it.file_size_formatted,
                        objectKey = it.object_key,
                        url = it.url,
                        status = it.status,
                        createdAt = it.created_at,
                        updatedAt = it.updated_at
                    )
                })
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getFileList(context: Context, status: String? = null): Result<List<FileItem>> {
        return if (NetworkUtils.isNetworkAvailable(context)) {
            val result = getFileListFromRemote(status)
            if (result is Result.Success) {
                fileDao.insertAll(result.data.map {
                    FileEntity(
                        id = it.id,
                        userId = it.user_id,
                        noteId = it.note_id,
                        fileName = it.file_name,
                        fileType = it.file_type,
                        fileSize = it.file_size,
                        fileSizeFormatted = it.file_size_formatted,
                        objectKey = it.object_key,
                        url = it.url,
                        status = it.status,
                        createdAt = it.created_at,
                        updatedAt = it.updated_at
                    )
                })
            }
            result
        } else {
            val flow = if (status != null) fileDao.getByStatus(status) else fileDao.getAll()
            Result.Success(flow.first().map { it.toFileItem() })
        }
    }

    suspend fun deleteFile(context: Context, fileId: Long): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法删除文件")
        }
        return try {
            val response = apiService.deleteFile(DeleteFileRequest(fileId))
            if (response.code == 0) {
                fileDao.deleteById(fileId)
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getFileStatus(context: Context, fileId: Long): Result<FileStatusData> {
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

    suspend fun getFileInfo(context: Context, fileId: Long): Result<FileItem> {
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

    private fun FileEntity.toFileItem(): FileItem = FileItem(
        id = id,
        user_id = userId,
        note_id = noteId,
        file_name = fileName,
        file_type = fileType,
        file_size = fileSize,
        file_size_formatted = fileSizeFormatted,
        object_key = objectKey,
        url = url,
        status = status,
        created_at = createdAt,
        updated_at = updatedAt
    )
}
