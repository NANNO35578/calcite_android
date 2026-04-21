package com.calcite.notes.data.repository

import android.content.Context
import com.calcite.notes.data.local.dao.NoteDao
import com.calcite.notes.data.local.entity.NoteEntity
import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.CollectNoteRequest
import com.calcite.notes.model.CreateNoteRequest
import com.calcite.notes.model.DeleteNoteRequest
import com.calcite.notes.model.LikeNoteRequest
import com.calcite.notes.model.Note
import com.calcite.notes.model.NoteCreateData
import com.calcite.notes.model.NoteDetail
import com.calcite.notes.model.RecommendNoteItem
import com.calcite.notes.model.UpdateNoteRequest
import com.calcite.notes.model.ViewNoteRequest
import com.calcite.notes.utils.NetworkUtils
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class NoteRepository(
    private val apiService: ApiService,
    private val noteDao: NoteDao
) {

    // =================== 本地数据源 ===================
    fun getNoteListLocal(folderId: Long): Flow<List<Note>> {
        return noteDao.getByFolderId(folderId).map { list ->
            list.map { it.toNote() }
        }
    }

    fun getNoteDetailLocal(noteId: Long): Flow<NoteDetail?> {
        return noteDao.getById(noteId).map { it?.toNoteDetail() }
    }

    fun getAllNotesLocal(): Flow<List<Note>> {
        return noteDao.getAll().map { list ->
            list.map { it.toNote() }
        }
    }

    // =================== 远程数据源（供 SyncWorker 使用） ===================
    suspend fun getNoteListFromRemote(folderId: Long): Result<List<Note>> {
        return try {
            val response = apiService.getNoteList(folderId)
            if (response.code == 0) {
                Result.Success(response.data ?: emptyList())
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getNoteDetailFromRemote(noteId: Long): Result<NoteDetail> {
        return try {
            val response = apiService.getNoteDetail(noteId)
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    // =================== 统一接口 ===================
    suspend fun createNote(context: Context, title: String, content: String, folderId: Long = 0): Result<NoteCreateData> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法创建笔记")
        }
        return try {
            val response = apiService.createNote(CreateNoteRequest(title, content, folder_id = folderId))
            if (response.code == 0 && response.data != null) {
                // 拉取详情并写入本地
                val detailRes = apiService.getNoteDetail(response.data.note_id)
                detailRes.data?.let {
                    noteDao.insert(it.toEntity())
                }
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun updateNote(context: Context, noteId: Long, title: String? = null, content: String? = null, summary: String? = null, folderId: Long? = null, isPublic: Boolean? = null): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法更新笔记")
        }
        return try {
            val response = apiService.updateNote(UpdateNoteRequest(noteId, title, content, summary, folderId, isPublic))
            if (response.code == 0) {
                // 更新本地
                val detailRes = apiService.getNoteDetail(noteId)
                detailRes.data?.let {
                    noteDao.insert(it.toEntity())
                }
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun deleteNote(context: Context, noteId: Long): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法删除笔记")
        }
        return try {
            val response = apiService.deleteNote(DeleteNoteRequest(noteId))
            if (response.code == 0) {
                noteDao.markDeleted(noteId)
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getNoteList(context: Context, folderId: Long = 0): Result<List<Note>> {
        return if (NetworkUtils.isNetworkAvailable(context)) {
            val result = getNoteListFromRemote(folderId)
            if (result is Result.Success) {
                noteDao.insertAll(result.data.map {
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
            result
        } else {
            Result.Success(noteDao.getByFolderId(folderId).first().map { it.toNote() })
        }
    }

    suspend fun syncAllNotes(context: Context): Result<Unit> {
        return syncAllNotesAndChildNotes(context, emptyList())
    }

    suspend fun syncAllNotesAndChildNotes(context: Context, folderIds: List<Long>): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val allNotes = mutableListOf<Note>()
            // 1. 拉取根目录笔记
            val rootResponse = apiService.getNoteList(0)
            if (rootResponse.code == 0) {
                allNotes.addAll(rootResponse.data ?: emptyList())
            } else {
                return Result.Error(rootResponse.message)
            }
            // 2. 拉取每个子文件夹下的笔记
            for (fid in folderIds) {
                val resp = apiService.getNoteList(fid)
                if (resp.code == 0) {
                    allNotes.addAll(resp.data ?: emptyList())
                }
            }
            // 3. 去重并写入 Room
            noteDao.insertAll(allNotes.distinctBy { it.id }.map {
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
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getNoteDetail(context: Context, noteId: Long): Result<NoteDetail> {
        return if (NetworkUtils.isNetworkAvailable(context)) {
            val result = getNoteDetailFromRemote(noteId)
            if (result is Result.Success) {
                noteDao.insert(result.data.toEntity())
            }
            result
        } else {
            val local = noteDao.getByIdSync(noteId)
            if (local != null) {
                Result.Success(local.toNoteDetail())
            } else {
                Result.Error("本地无此笔记缓存")
            }
        }
    }

    // =================== 笔记交互 ===================
    suspend fun viewNote(context: Context, noteId: Long): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val response = apiService.viewNote(ViewNoteRequest(noteId))
            if (response.code == 0) Result.Success(Unit) else Result.Error(response.message)
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun likeNote(context: Context, noteId: Long): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val response = apiService.likeNote(LikeNoteRequest(noteId))
            if (response.code == 0) Result.Success(Unit) else Result.Error(response.message)
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun collectNote(context: Context, noteId: Long): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val response = apiService.collectNote(CollectNoteRequest(noteId))
            if (response.code == 0) Result.Success(Unit) else Result.Error(response.message)
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun unlikeNote(context: Context, noteId: Long): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val response = apiService.unlikeNote(noteId)
            if (response.code == 0) Result.Success(Unit) else Result.Error(response.message)
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun uncollectNote(context: Context, noteId: Long): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val response = apiService.uncollectNote(noteId)
            if (response.code == 0) Result.Success(Unit) else Result.Error(response.message)
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getRecommendNotes(context: Context, page: Int = 1, pageSize: Int = 10): Result<List<RecommendNoteItem>> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val response = apiService.getRecommendNotes(page, pageSize)
            if (response.code == 0) {
                Result.Success(response.data ?: emptyList())
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    private fun NoteEntity.toNote(): Note = Note(
        id = id,
        title = title,
        summary = summary,
        folder_id = folderId,
        created_at = createdAt,
        updated_at = updatedAt
    )

    private fun NoteEntity.toNoteDetail(): NoteDetail = NoteDetail(
        id = id,
        title = title,
        content = content,
        summary = summary,
        folder_id = folderId,
        created_at = createdAt,
        updated_at = updatedAt,
        author_id = authorId,
        is_public = if (isPublic) 1 else 0
    )

    private fun NoteDetail.toEntity(): NoteEntity = NoteEntity(
        id = id,
        title = title,
        content = content,
        summary = summary,
        folderId = folder_id ?: 0L,
        createdAt = created_at ?: "",
        updatedAt = updated_at ?: "",
        authorId = author_id,
        isPublic = is_public == 1
    )
}
