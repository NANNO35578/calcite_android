package com.calcite.notes.data.repository

import android.content.Context
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.local.dao.NoteTagDao
import com.calcite.notes.data.local.dao.TagDao
import com.calcite.notes.data.local.entity.NoteTagCrossRef
import com.calcite.notes.data.local.entity.TagEntity
import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.BindTagRequest
import com.calcite.notes.model.CreateTagRequest
import com.calcite.notes.model.DeleteTagRequest
import com.calcite.notes.model.Tag
import com.calcite.notes.model.TagCreateData
import com.calcite.notes.model.UpdateTagRequest
import com.calcite.notes.utils.NetworkUtils
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TagRepository(
    private val apiService: ApiService,
    private val tagDao: TagDao,
    private val noteTagDao: NoteTagDao
) {

    // =================== 本地数据源 ===================
    fun getAllTagsLocal(): Flow<List<Tag>> {
        return tagDao.getAll().map { list -> list.map { it.toTag() } }
    }

    fun getTagsByNoteLocal(noteId: Long): Flow<List<Tag>> {
        return tagDao.getTagsForNote(noteId).map { list -> list.map { it.toTag() } }
    }

    // =================== 远程数据源（供 SyncWorker 使用） ===================
    suspend fun getAllTagsFromRemote(): Result<List<Tag>> {
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

    suspend fun getTagsByNoteFromRemote(noteId: Long): Result<List<Tag>> {
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

    // =================== 统一接口 ===================
    suspend fun createTag(context: Context, name: String): Result<TagCreateData> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法创建标签")
        }
        return try {
            val response = apiService.createTag(CreateTagRequest(name))
            if (response.code == 0 && response.data != null) {
                tagDao.insert(TagEntity(response.data.tag_id, name, ""))
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun updateTag(context: Context, tagId: Long, name: String): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法更新标签")
        }
        return try {
            val response = apiService.updateTag(UpdateTagRequest(tagId, name))
            if (response.code == 0) {
                tagDao.update(TagEntity(tagId, name, ""))
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun deleteTag(context: Context, tagId: Long): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法删除标签")
        }
        return try {
            val response = apiService.deleteTag(DeleteTagRequest(tagId))
            if (response.code == 0) {
                tagDao.deleteById(tagId)
                noteTagDao.deleteByTagId(tagId)
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun syncAllTags(context: Context): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接")
        }
        return try {
            val response = apiService.getTagList()
            if (response.code == 0) {
                val tags = response.data ?: emptyList()
                tagDao.insertAll(tags.map { TagEntity(it.id, it.name, it.created_at) })
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getAllTags(context: Context): Result<List<Tag>> {
        return if (NetworkUtils.isNetworkAvailable(context)) {
            val result = getAllTagsFromRemote()
            if (result is Result.Success) {
                tagDao.insertAll(result.data.map { TagEntity(it.id, it.name, it.created_at) })
            }
            result
        } else {
            Result.Success(tagDao.getAllSync().map { it.toTag() })
        }
    }

    suspend fun getTagsByNote(context: Context, noteId: Long): Result<List<Tag>> {
        return if (NetworkUtils.isNetworkAvailable(context)) {
            val result = getTagsByNoteFromRemote(noteId)
            if (result is Result.Success) {
                noteTagDao.deleteByNoteId(noteId)
                val crossRefs = result.data.map { NoteTagCrossRef(noteId, it.id) }
                noteTagDao.insertAll(crossRefs)
            }
            result
        } else {
            Result.Success(tagDao.getTagsForNoteSync(noteId).map { it.toTag() })
        }
    }

    suspend fun bindTags(context: Context, noteId: Long, tagIds: List<Long>): Result<Unit> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法绑定标签")
        }
        return try {
            val response = apiService.bindTags(BindTagRequest(noteId, tagIds))
            if (response.code == 0) {
                noteTagDao.deleteByNoteId(noteId)
                noteTagDao.insertAll(tagIds.map { NoteTagCrossRef(noteId, it) })
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    private fun TagEntity.toTag(): Tag = Tag(id, name, createdAt)
}
