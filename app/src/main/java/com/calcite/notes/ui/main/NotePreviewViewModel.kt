package com.calcite.notes.ui.main

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.data.repository.TagRepository
import com.calcite.notes.model.NoteDetail
import com.calcite.notes.model.Tag
import com.calcite.notes.utils.Result
import kotlinx.coroutines.launch

class NotePreviewViewModel(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    val noteId: Long
) : ViewModel() {

    private val _noteDetail = MutableLiveData<NoteDetail?>(null)
    val noteDetail: LiveData<NoteDetail?> = _noteDetail

    private val _tags = MutableLiveData<List<Tag>>(emptyList())
    val tags: LiveData<List<Tag>> = _tags

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadNote()
    }

    fun loadNote() {
        if (noteId == 0L) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 加载笔记详情
                when (val detailResult = noteRepository.getNoteDetail(context, noteId)) {
                    is Result.Success -> {
                        _noteDetail.value = detailResult.data
                        // 发送浏览请求
                        noteRepository.viewNote(context, noteId)
                    }
                    is Result.Error -> {
                        _errorMessage.value = detailResult.message
                    }
                    else -> {}
                }
                // 加载标签
                when (val tagResult = tagRepository.getTagsByNote(context, noteId)) {
                    is Result.Success -> _tags.value = tagResult.data
                    else -> {}
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleLike() {
        val detail = _noteDetail.value ?: return
        viewModelScope.launch {
            val result = if (detail.has_liked) {
                noteRepository.unlikeNote(context, noteId)
            } else {
                noteRepository.likeNote(context, noteId)
            }
            if (result is Result.Success) {
                // 刷新详情以获取最新状态
                when (val refreshResult = noteRepository.getNoteDetailFromRemote(noteId)) {
                    is Result.Success -> _noteDetail.value = refreshResult.data
                    else -> {}
                }
            }
        }
    }

    fun toggleCollect() {
        val detail = _noteDetail.value ?: return
        viewModelScope.launch {
            val result = if (detail.has_collected) {
                noteRepository.uncollectNote(context, noteId)
            } else {
                noteRepository.collectNote(context, noteId)
            }
            if (result is Result.Success) {
                when (val refreshResult = noteRepository.getNoteDetailFromRemote(noteId)) {
                    is Result.Success -> _noteDetail.value = refreshResult.data
                    else -> {}
                }
            }
        }
    }

    class Factory(
        private val context: Context,
        private val noteId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val api = RetrofitClient.getApiService(context)
            val db = AppDatabase.getInstance(context)
            return NotePreviewViewModel(
                context,
                NoteRepository(api, db.noteDao()),
                TagRepository(api, db.tagDao(), db.noteTagDao()),
                noteId
            ) as T
        }
    }
}
