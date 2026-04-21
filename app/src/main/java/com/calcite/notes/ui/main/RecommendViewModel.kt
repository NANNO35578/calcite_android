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
import com.calcite.notes.model.RecommendNoteItem
import com.calcite.notes.utils.Result
import kotlinx.coroutines.launch

class RecommendViewModel(
    private val context: Context,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _recommendList = MutableLiveData<List<RecommendNoteItem>>(emptyList())
    val recommendList: LiveData<List<RecommendNoteItem>> = _recommendList

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadRecommendations()
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            when (val result = noteRepository.getRecommendNotes(context)) {
                is Result.Success -> _recommendList.value = result.data
                is Result.Error -> {
                    _recommendList.value = emptyList()
                    _errorMessage.value = result.message
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val api = RetrofitClient.getApiService(context)
            val db = AppDatabase.getInstance(context)
            return RecommendViewModel(
                context,
                NoteRepository(api, db.noteDao())
            ) as T
        }
    }
}
