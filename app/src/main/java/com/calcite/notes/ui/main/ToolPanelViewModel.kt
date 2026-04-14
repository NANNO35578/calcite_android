package com.calcite.notes.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.repository.FileRepository
import com.calcite.notes.data.repository.TagRepository
import com.calcite.notes.model.FileItem
import com.calcite.notes.model.Tag
import com.calcite.notes.utils.Result
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class ToolPanelViewModel(
    private val tagRepository: TagRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _tags = MutableLiveData<List<Tag>>(emptyList())
    val tags: LiveData<List<Tag>> = _tags

    private val _boundTagIds = MutableLiveData<Set<Long>>(emptySet())
    val boundTagIds: LiveData<Set<Long>> = _boundTagIds

    private val _files = MutableLiveData<List<FileItem>>(emptyList())
    val files: LiveData<List<FileItem>> = _files

    private val _operationResult = MutableLiveData<Result<String>>()
    val operationResult: LiveData<Result<String>> = _operationResult

    private var currentNoteId: Long = 0L
    private var currentFileStatusFilter: String? = null

    fun setNoteId(noteId: Long) {
        if (currentNoteId != noteId) {
            currentNoteId = noteId
            loadTags()
            if (noteId != 0L) {
                loadBoundTags()
            } else {
                _boundTagIds.value = emptySet()
            }
        }
    }

    fun loadAll() {
        loadTags()
        loadFiles()
        if (currentNoteId != 0L) loadBoundTags()
    }

    private fun loadTags() {
        viewModelScope.launch {
            when (val result = tagRepository.getAllTags()) {
                is Result.Success -> _tags.value = result.data
                else -> {}
            }
        }
    }

    private fun loadBoundTags() {
        if (currentNoteId == 0L) return
        viewModelScope.launch {
            when (val result = tagRepository.getTagsByNote(currentNoteId)) {
                is Result.Success -> _boundTagIds.value = result.data.map { it.id }.toSet()
                else -> {}
            }
        }
    }

    private fun loadFiles() {
        viewModelScope.launch {
            when (val result = fileRepository.getFileList(currentFileStatusFilter)) {
                is Result.Success -> _files.value = result.data
                else -> {}
            }
        }
    }

    fun setFileStatusFilter(status: String?) {
        currentFileStatusFilter = status
        loadFiles()
    }

    fun uploadFile(filePart: MultipartBody.Part, noteId: Long? = null) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = fileRepository.uploadFile(filePart, noteId)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    loadFiles()
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
            val result = fileRepository.deleteFile(fileId)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    loadFiles()
                    Result.Success("文件已删除")
                }
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun toggleTag(tagId: Long) {
        if (currentNoteId == 0L) {
            _operationResult.value = Result.Error("请先打开一篇笔记")
            return
        }
        val currentSet = _boundTagIds.value?.toMutableSet() ?: mutableSetOf()
        if (currentSet.contains(tagId)) {
            currentSet.remove(tagId)
        } else {
            currentSet.add(tagId)
        }
        val newList = currentSet.toList()
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = tagRepository.bindTags(currentNoteId, newList)
            if (result is Result.Success) {
                _boundTagIds.value = currentSet
                _operationResult.value = Result.Success("标签绑定已更新")
            } else if (result is Result.Error) {
                _operationResult.value = result
            }
        }
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = tagRepository.createTag(name)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    loadTags()
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
            val result = tagRepository.updateTag(tagId, newName)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    loadTags()
                    Result.Success("重命名成功")
                }
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = tagRepository.deleteTag(tagId)
            _operationResult.value = when (result) {
                is Result.Success -> {
                    loadTags()
                    if (currentNoteId != 0L) loadBoundTags()
                    Result.Success("删除标签成功")
                }
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    class Factory(
        private val tagRepository: TagRepository,
        private val fileRepository: FileRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return ToolPanelViewModel(tagRepository, fileRepository) as T
        }
    }
}
