package com.calcite.notes.data.repository

import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.CreateNoteRequest
import com.calcite.notes.model.DeleteNoteRequest
import com.calcite.notes.model.Note
import com.calcite.notes.model.NoteCreateData
import com.calcite.notes.model.NoteDetail
import com.calcite.notes.model.UpdateNoteRequest
import com.calcite.notes.utils.Result

class NoteRepository(private val apiService: ApiService) {

    suspend fun createNote(title: String, content: String, folderId: Long = 0): Result<NoteCreateData> {
        return try {
            val response = apiService.createNote(CreateNoteRequest(title, content, folder_id = folderId))
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun updateNote(noteId: Long, title: String? = null, content: String? = null, summary: String? = null, folderId: Long? = null): Result<Unit> {
        return try {
            val response = apiService.updateNote(UpdateNoteRequest(noteId, title, content, summary, folderId))
            if (response.code == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun deleteNote(noteId: Long): Result<Unit> {
        return try {
            val response = apiService.deleteNote(DeleteNoteRequest(noteId))
            if (response.code == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getNoteList(folderId: Long = 0): Result<List<Note>> {
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

    suspend fun getNoteDetail(noteId: Long): Result<NoteDetail> {
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
}
