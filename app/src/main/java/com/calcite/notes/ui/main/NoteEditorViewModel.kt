package com.calcite.notes.ui.main

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.local.dao.NoteDao
import com.calcite.notes.data.local.entity.NoteEntity
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.model.NoteDetail
import com.calcite.notes.utils.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NoteEditorViewModel(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val noteDao: NoteDao,
    private val appDataStore: AppDataStore,
    val noteId: Long
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

    init {
        if (noteId != 0L) {
            loadNote()
            viewModelScope.launch {
                appDataStore.setCurrentNoteId(noteId)
            }
        }
        startAutoSave()
        observeLocalNote()
    }

    private fun observeLocalNote() {
        if (noteId == 0L) return
        viewModelScope.launch {
            noteDao.getById(noteId).collect { entity ->
                entity?.let {
                    val detail = it.toNoteDetail()
                    // 只要用户没有手动编辑，就用本地最新数据更新 UI，
                    // 确保网络写入 Room 后的 Flow 发射能正确刷新界面
                    if (_hasUnsavedChanges.value != true) {
                        _noteDetail.value = detail
                        lastSavedTitle = detail.title
                        lastSavedContent = detail.content
                    }
                }
            }
        }
    }

    fun loadNote() {
        if (noteId == 0L) return
        viewModelScope.launch {
            _loadResult.value = Result.Loading
            try {
                when (val result = noteRepository.getNoteDetail(context, noteId)) {
                    is Result.Success -> {
                        val data = result.data
                        _noteDetail.value = data
                        lastSavedTitle = data.title
                        lastSavedContent = data.content
                        _hasUnsavedChanges.value = false
                        _loadResult.value = Result.Success(Unit)
                    }
                    is Result.Error -> {
                        // 尝试本地
                        val local = noteDao.getByIdSync(noteId)
                        if (local != null) {
                            val data = local.toNoteDetail()
                            _noteDetail.value = data
                            lastSavedTitle = data.title
                            lastSavedContent = data.content
                            _hasUnsavedChanges.value = false
                            _loadResult.value = Result.Success(Unit)
                        } else {
                            _loadResult.value = Result.Error(result.message)
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                // 网络或数据库异常时回退本地
                val local = noteDao.getByIdSync(noteId)
                if (local != null) {
                    val data = local.toNoteDetail()
                    _noteDetail.value = data
                    lastSavedTitle = data.title
                    lastSavedContent = data.content
                    _hasUnsavedChanges.value = false
                    _loadResult.value = Result.Success(Unit)
                } else {
                    _loadResult.value = Result.Error(e.message ?: "加载失败")
                }
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
                context = context,
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

    private fun NoteEntity.toNoteDetail(): NoteDetail = NoteDetail(
        id = id,
        title = title,
        content = content,
        summary = summary,
        folder_id = folderId,
        created_at = createdAt,
        updated_at = updatedAt
    )

    class Factory(
        private val context: Context,
        private val noteId: Long
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val api = RetrofitClient.getApiService(context)
            val db = com.calcite.notes.data.local.database.AppDatabase.getInstance(context)
            return NoteEditorViewModel(
                context,
                NoteRepository(api, db.noteDao()),
                db.noteDao(),
                AppDataStore(context),
                noteId
            ) as T
        }
    }
}
