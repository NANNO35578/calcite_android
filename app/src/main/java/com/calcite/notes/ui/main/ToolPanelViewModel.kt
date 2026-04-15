package com.calcite.notes.ui.main

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.FileRepository
import com.calcite.notes.data.repository.TagRepository
import com.calcite.notes.model.FileItem
import com.calcite.notes.model.Tag
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class ToolPanelViewModel(
    private val context: Context,
    private val tagRepository: TagRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _allTags = MutableLiveData<List<Tag>>(emptyList())
    val allTags: LiveData<List<Tag>> = _allTags

    private val _currentNoteTags = MutableLiveData<List<Tag>>(emptyList())
    val currentNoteTags: LiveData<List<Tag>> = _currentNoteTags

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
            tagRepository.getAllTagsLocal().collect {
                _allTags.value = it
            }
        }
        viewModelScope.launch {
            _currentNoteId.collect { noteId ->
                if (noteId > 0) {
                    tagRepository.getTagsByNoteLocal(noteId).collect {
                        _currentNoteTags.value = it
                    }
                } else {
                    _currentNoteTags.value = emptyList()
                }
            }
        }
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
                viewModelScope.launch {
                    tagRepository.getTagsByNote(context, noteId)
                }
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            tagRepository.getAllTags(context)
            fileRepository.getFileList(context, currentFileStatusFilter)
            val noteId = _currentNoteId.value
            if (noteId > 0) {
                tagRepository.getTagsByNote(context, noteId)
            }
        }
    }

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

    fun bindTag(tagId: Long) {
        val noteId = _currentNoteId.value
        if (noteId == 0L) {
            _operationResult.value = Result.Error("请先打开一篇笔记")
            return
        }
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val currentIds = _currentNoteTags.value?.map { it.id }?.toMutableSet() ?: mutableSetOf()
            currentIds.add(tagId)
            val result = tagRepository.bindTags(context, noteId, currentIds.toList())
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("标签已绑定")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun unbindTag(tagId: Long) {
        val noteId = _currentNoteId.value
        if (noteId == 0L) return
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val currentIds = _currentNoteTags.value?.map { it.id }?.toMutableSet() ?: mutableSetOf()
            currentIds.remove(tagId)
            val result = tagRepository.bindTags(context, noteId, currentIds.toList())
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("标签已取消绑定")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = tagRepository.createTag(context, name)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    Result.Success("创建标签成功")
                }
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun renameTag(tagId: Long, newName: String) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = tagRepository.updateTag(context, tagId, newName)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("重命名成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = tagRepository.deleteTag(context, tagId)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("删除标签成功")
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
                TagRepository(api, db.tagDao(), db.noteTagDao()),
                FileRepository(api, db.fileDao())
            ) as T
        }
    }
}
