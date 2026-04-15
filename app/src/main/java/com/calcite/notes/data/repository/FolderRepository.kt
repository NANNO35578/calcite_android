package com.calcite.notes.data.repository

import android.content.Context
import com.calcite.notes.data.local.dao.FolderDao
import com.calcite.notes.data.local.dao.NoteDao
import com.calcite.notes.data.local.entity.FolderEntity
import com.calcite.notes.data.local.entity.NoteEntity
import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.CreateFolderRequest
import com.calcite.notes.model.DeleteFolderRequest
import com.calcite.notes.model.Folder
import com.calcite.notes.model.FolderCreateData
import com.calcite.notes.model.UpdateFolderRequest
import com.calcite.notes.utils.NetworkUtils
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class FolderRepository(
    private val apiService: ApiService,
    private val folderDao: FolderDao,
    private val noteDao: NoteDao? = null
) {

    // =================== 本地数据源 ===================
    fun getFolderListLocal(parentId: Long): Flow<List<Folder>> {
        return folderDao.getByParentId(parentId).map { list -> list.map { it.toFolder() } }
    }

    fun getAllFoldersLocal(): Flow<List<Folder>> {
        return folderDao.getAll().map { list -> list.map { it.toFolder() } }
    }

    // =================== 远程数据源（供 SyncWorker 使用） ===================
    suspend fun getFolderListFromRemote(parentId: Long): Result<List<Folder>> {
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

    // =================== 统一接口 ===================
    suspend fun createFolder(context: Context, name: String, parentId: Long = 0): Result<FolderCreateData> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法创建文件夹")
        }
        return try {
            val response = apiService.createFolder(CreateFolderRequest(name, parentId))
            if (response.code == 0 && response.data != null) {
                folderDao.insert(FolderEntity(response.data.folder_id, name, parentId, ""))
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun updateFolder(context: Context, folderId: Long, name: String? = null, parentId: Long? = null): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法更新文件夹")
        }
        return try {
            val response = apiService.updateFolder(UpdateFolderRequest(folderId, name, parentId))
            if (response.code == 0) {
                val local = folderDao.getByIdSync(folderId)
                if (local != null) {
                    folderDao.update(
                        local.copy(
                            name = name ?: local.name,
                            parentId = parentId ?: local.parentId
                        )
                    )
                }
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun deleteFolder(context: Context, folderId: Long): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法删除文件夹")
        }
        return try {
            val response = apiService.deleteFolder(DeleteFolderRequest(folderId))
            if (response.code == 0) {
                folderDao.deleteById(folderId)
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun syncAllFolders(context: Context): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val queue = ArrayDeque<Long>()
            queue.add(0L)
            val visited = mutableSetOf<Long>()
            val allFolders = mutableListOf<FolderEntity>()
            while (queue.isNotEmpty()) {
                val pid = queue.removeFirst()
                if (!visited.add(pid)) continue
                // 1. 同步当前文件夹下的笔记
                noteDao?.let { nd ->
                    try {
                        val noteResp = apiService.getNoteList(pid)
                        if (noteResp.code == 0) {
                            val notes = noteResp.data ?: emptyList()
                            nd.insertAll(notes.map {
                                NoteEntity(
                                    id = it.id,
                                    title = it.title,
                                    content = "",
                                    summary = it.summary,
                                    folderId = it.folder_id ?: 0L,
                                    createdAt = it.created_at,
                                    updatedAt = it.updated_at
                                )
                            })
                        }
                    } catch (_: Exception) { }
                }
                // 2. 同步子文件夹
                val response = apiService.getFolderList(pid)
                if (response.code == 0) {
                    val folders = response.data ?: emptyList()
                    for (folder in folders) {
                        allFolders.add(
                            FolderEntity(
                                folder.id,
                                folder.name,
                                folder.parent_id ?: 0L,
                                folder.created_at ?: ""
                            )
                        )
                        if (folder.id !in visited) {
                            queue.add(folder.id)
                        }
                    }
                }
            }
            folderDao.insertAll(allFolders)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getFolderList(context: Context, parentId: Long = 0): Result<List<Folder>> {
        return if (NetworkUtils.isNetworkAvailable(context)) {
            val result = getFolderListFromRemote(parentId)
            if (result is Result.Success) {
                folderDao.insertAll(result.data.map {
                    FolderEntity(it.id, it.name, it.parent_id ?: 0L, it.created_at ?: "")
                })
            }
            result
        } else {
            Result.Success(folderDao.getByParentId(parentId).first().map { it.toFolder() })
        }
    }

    private fun FolderEntity.toFolder(): Folder = Folder(id, name, parentId, createdAt)
}
