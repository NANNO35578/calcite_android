package com.calcite.notes.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.model.NoteDetail
import com.calcite.notes.utils.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NoteEditorViewModel(
    private val noteRepository: NoteRepository,
    noteId: Long
) : ViewModel() {

    private val _noteDetail = MutableLiveData<NoteDetail?>(null)
    val noteDetail: LiveData<NoteDetail?> = _noteDetail

    private val _isPreview = MutableLiveData(false)
    val isPreview: LiveData<Boolean> = _isPreview

    private val _hasUnsavedChanges = MutableLiveData(false)
    val hasUnsavedChanges: LiveData<Boolean> = _hasUnsavedChanges

    private val _saveResult = MutableLiveData<Result<Unit>>()
    val saveResult: LiveData<Result<Unit>> = _saveResult

    private val _loadResult = MutableLiveData<Result<Unit>>()
    val loadResult: LiveData<Result<Unit>> = _loadResult

    private var autoSaveJob: Job? = null
    private var lastSavedTitle: String = ""
    private var lastSavedContent: String = ""

    val noteId: Long = noteId

    init {
        if (noteId != 0L) {
            loadNote()
        }
        startAutoSave()
    }

    fun loadNote() {
        if (noteId == 0L) return
        viewModelScope.launch {
            _loadResult.value = Result.Loading
            when (val result = noteRepository.getNoteDetail(noteId)) {
                is Result.Success -> {
                    _noteDetail.value = result.data
                    lastSavedTitle = result.data.title
                    lastSavedContent = result.data.content
                    _hasUnsavedChanges.value = false
                    _loadResult.value = Result.Success(Unit)
                }
                is Result.Error -> {
                    _loadResult.value = Result.Error(result.message)
                }
                else -> {}
            }
        }
    }

    fun setPreview(preview: Boolean) {
        _isPreview.value = preview
    }

    fun updateContent(title: String, content: String) {
        val current = _noteDetail.value
        if (current != null) {
            _noteDetail.value = current.copy(title = title, content = content)
        } else {
            // 新建笔记时 noteDetail 可能为空，需要临时存储
            _noteDetail.value = NoteDetail(
                id = noteId,
                title = title,
                content = content,
                summary = null,
                folder_id = 0,
                created_at = "",
                updated_at = ""
            )
        }
        _hasUnsavedChanges.value = (title != lastSavedTitle || content != lastSavedContent)
    }

    fun saveNote() {
        val current = _noteDetail.value ?: return
        if (noteId == 0L) return
        viewModelScope.launch {
            _saveResult.value = Result.Loading
            val result = noteRepository.updateNote(
                noteId = noteId,
                title = current.title,
                content = current.content,
                summary = current.summary
            )
            if (result is Result.Success) {
                lastSavedTitle = current.title
                lastSavedContent = current.content
                _hasUnsavedChanges.value = false
            }
            _saveResult.value = result
        }
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                if (_hasUnsavedChanges.value == true && noteId != 0L) {
                    saveNote()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
    }

    class Factory(
        private val noteRepository: NoteRepository,
        private val noteId: Long
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return NoteEditorViewModel(noteRepository, noteId) as T
        }
    }
}
