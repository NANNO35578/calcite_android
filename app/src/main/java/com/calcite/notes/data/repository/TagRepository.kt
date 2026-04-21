package com.calcite.notes.data.repository

import android.content.Context
import com.calcite.notes.data.local.dao.NoteTagDao
import com.calcite.notes.data.local.dao.TagDao
import com.calcite.notes.data.local.entity.NoteTagCrossRef
import com.calcite.notes.data.local.entity.TagEntity
import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.Tag
import com.calcite.notes.utils.NetworkUtils
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TagRepository(
    private val apiService: ApiService,
    private val tagDao: TagDao,
    private val noteTagDao: NoteTagDao
) {

    // =================== 本地数据源 ===================
    fun getTagsByNoteLocal(noteId: Long): Flow<List<Tag>> {
        return tagDao.getTagsForNote(noteId).map { list -> list.map { it.toTag() } }
    }

    // =================== 远程数据源 ===================
    suspend fun getTagsByNoteFromRemote(noteId: Long): Result<List<Tag>> {
        return try {
            val response = apiService.getNoteTags(noteId)
            if (response.code == 0) {
                Result.Success(response.data ?: emptyList())
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun aiGenerateTags(context: Context, noteId: Long): Result<List<Tag>> {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            return Result.Error("无网络连接，无法生成标签")
        }
        return try {
            val response = apiService.aiGenerateTags(noteId)
            if (response.code == 0) {
                val tags = response.data ?: emptyList()
                // 更新本地缓存：先删除旧绑定，再插入新标签和新绑定
                noteTagDao.deleteByNoteId(noteId)
                tagDao.insertAll(tags.map { TagEntity(it.id, it.name, it.created_at) })
                noteTagDao.insertAll(tags.map { NoteTagCrossRef(noteId, it.id) })
                Result.Success(tags)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getTagsByNote(context: Context, noteId: Long): Result<List<Tag>> {
        return if (NetworkUtils.isNetworkAvailable(context)) {
            val result = getTagsByNoteFromRemote(noteId)
            if (result is Result.Success) {
                noteTagDao.deleteByNoteId(noteId)
                val tags = result.data
                tagDao.insertAll(tags.map { TagEntity(it.id, it.name, it.created_at) })
                noteTagDao.insertAll(tags.map { NoteTagCrossRef(noteId, it.id) })
            }
            result
        } else {
            Result.Success(tagDao.getTagsForNoteSync(noteId).map { it.toTag() })
        }
    }

    suspend fun syncTagsForNote(noteId: Long): Result<Unit> {
        return try {
            val result = getTagsByNoteFromRemote(noteId)
            if (result is Result.Success) {
                noteTagDao.deleteByNoteId(noteId)
                val tags = result.data
                tagDao.insertAll(tags.map { TagEntity(it.id, it.name, it.created_at) })
                noteTagDao.insertAll(tags.map { NoteTagCrossRef(noteId, it.id) })
                Result.Success(Unit)
            } else if (result is Result.Error) {
                result
            } else {
                Result.Error("同步标签失败")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    private fun TagEntity.toTag(): Tag = Tag(id, name, createdAt)
}
