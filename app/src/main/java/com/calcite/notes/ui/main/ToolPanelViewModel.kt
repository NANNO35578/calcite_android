package com.calcite.notes.ui.main

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.FileRepository
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.data.repository.TagRepository
import com.calcite.notes.model.FileItem
import com.calcite.notes.model.NoteDetail
import com.calcite.notes.model.Tag
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class ToolPanelViewModel(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val fileRepository: FileRepository,
    private val appDataStore: AppDataStore
) : ViewModel() {

    private val _noteDetail = MutableLiveData<NoteDetail?>(null)
    val noteDetail: LiveData<NoteDetail?> = _noteDetail

    private val _currentNoteTags = MutableLiveData<List<Tag>>(emptyList())
    val currentNoteTags: LiveData<List<Tag>> = _currentNoteTags

    private val _isOwnNote = MutableLiveData(false)
    val isOwnNote: LiveData<Boolean> = _isOwnNote

    private val _files = MutableLiveData<List<FileItem>>(emptyList())
    val files: LiveData<List<FileItem>> = _files

    private val _operationResult = MutableLiveData<Result<String>>()
    val operationResult: LiveData<Result<String>> = _operationResult

    private val _currentNoteId = MutableStateFlow(0L)
    val currentNoteId: StateFlow<Long> = _currentNoteId

    private var currentFileStatusFilter: String? = null

    init {
        observeLocalData()
    }

    private fun observeLocalData() {
        viewModelScope.launch {
            fileRepository.getFileListLocal(currentFileStatusFilter).collect {
                _files.value = it
            }
        }
    }

    fun setNoteId(noteId: Long) {
        if (_currentNoteId.value != noteId) {
            _currentNoteId.value = noteId
            if (noteId > 0) {
                loadNoteInfo(noteId)
                loadNoteTags(noteId)
            } else {
                _noteDetail.value = null
                _currentNoteTags.value = emptyList()
                _isOwnNote.value = false
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            fileRepository.getFileList(context, currentFileStatusFilter)
            val noteId = _currentNoteId.value
            if (noteId > 0) {
                loadNoteInfo(noteId)
                loadNoteTags(noteId)
            }
        }
    }

    fun loadNoteInfo(noteId: Long) {
        viewModelScope.launch {
            val result = noteRepository.getNoteDetail(context, noteId)
            if (result is Result.Success) {
                val detail = result.data
                _noteDetail.value = detail
                val currentUserId = appDataStore.userId.first()
                _isOwnNote.value = (detail.author_id == currentUserId)
            }
        }
    }

    fun loadNoteTags(noteId: Long) {
        viewModelScope.launch {
            val result = tagRepository.getTagsByNote(context, noteId)
            if (result is Result.Success) {
                _currentNoteTags.value = result.data
            }
        }
    }

    fun updateSummary(noteId: Long, summary: String) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = noteRepository.updateNote(context, noteId, summary = summary)
            if (result is Result.Success) {
                loadNoteInfo(noteId)
                _operationResult.value = Result.Success("摘要已更新")
            } else if (result is Result.Error) {
                _operationResult.value = result
            }
        }
    }

    fun togglePublic(noteId: Long, isPublic: Boolean) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = noteRepository.updateNote(context, noteId, isPublic = isPublic)
            if (result is Result.Success) {
                loadNoteInfo(noteId)
                _operationResult.value = Result.Success("公开状态已更新")
            } else if (result is Result.Error) {
                _operationResult.value = result
            }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = noteRepository.deleteNote(context, noteId)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    _noteDetail.value = null
                    _currentNoteTags.value = emptyList()
                    Result.Success("笔记已删除")
                }
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun refreshTags(noteId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = tagRepository.aiGenerateTags(context, noteId)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    _currentNoteTags.value = result.data
                    Result.Success("标签已生成")
                }
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun toggleLike() {
        val noteId = _currentNoteId.value
        if (noteId == 0L) return
        val detail = _noteDetail.value ?: return
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = if (detail.has_liked) {
                noteRepository.unlikeNote(context, noteId)
            } else {
                noteRepository.likeNote(context, noteId)
            }
            if (result is Result.Success) {
                loadNoteInfo(noteId)
                _operationResult.value = Result.Success(if (detail.has_liked) "已取消点赞" else "点赞成功")
            } else if (result is Result.Error) {
                _operationResult.value = result
            }
        }
    }

    fun toggleCollect() {
        val noteId = _currentNoteId.value
        if (noteId == 0L) return
        val detail = _noteDetail.value ?: return
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = if (detail.has_collected) {
                noteRepository.uncollectNote(context, noteId)
            } else {
                noteRepository.collectNote(context, noteId)
            }
            if (result is Result.Success) {
                loadNoteInfo(noteId)
                _operationResult.value = Result.Success(if (detail.has_collected) "已取消收藏" else "收藏成功")
            } else if (result is Result.Error) {
                _operationResult.value = result
            }
        }
    }

    // =================== 文件相关（保留） ===================

    fun setFileStatusFilter(status: String?) {
        currentFileStatusFilter = status
        viewModelScope.launch {
            fileRepository.getFileList(context, status)
        }
    }

    fun uploadFile(filePart: MultipartBody.Part, noteId: Long? = null) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = fileRepository.uploadFile(context, filePart, noteId)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    fileRepository.getFileList(context, currentFileStatusFilter)
                    Result.Success("文件上传已提交")
                }
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun deleteFile(fileId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = fileRepository.deleteFile(context, fileId)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    Result.Success("文件已删除")
                }
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val api = RetrofitClient.getApiService(context)
            val db = AppDatabase.getInstance(context)
            return ToolPanelViewModel(
                context,
                NoteRepository(api, db.noteDao()),
                TagRepository(api, db.tagDao(), db.noteTagDao()),
                FileRepository(api, db.fileDao()),
                AppDataStore(context)
            ) as T
        }
    }
}
