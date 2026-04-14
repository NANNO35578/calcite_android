package com.calcite.notes.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.repository.SearchRepository
import com.calcite.notes.model.SearchResultItem
import com.calcite.notes.utils.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(private val searchRepository: SearchRepository) : ViewModel() {

    private val _searchResults = MutableLiveData<List<SearchResultItem>>(emptyList())
    val searchResults: LiveData<List<SearchResultItem>> = _searchResults

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var searchJob: Job? = null

    fun search(keyword: String) {
        if (keyword.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400) // debounce
            _isLoading.value = true
            when (val result = searchRepository.search(keyword)) {
                is Result.Success -> _searchResults.value = result.data
                is Result.Error -> _searchResults.value = emptyList()
                else -> {}
            }
            _isLoading.value = false
        }
    }

    class Factory(private val searchRepository: SearchRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(searchRepository) as T
        }
    }
}
