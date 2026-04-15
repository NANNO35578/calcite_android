package com.calcite.notes.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.repository.AuthRepository
import com.calcite.notes.model.LoginData
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: AuthRepository,
    private val appDataStore: AppDataStore
) : ViewModel() {

    private val _loginResult = MutableLiveData<Result<LoginData>>()
    val loginResult: LiveData<Result<LoginData>> = _loginResult

    private val _navigateToNoteId = MutableLiveData<Long?>(null)
    val navigateToNoteId: LiveData<Long?> = _navigateToNoteId

    init {
        viewModelScope.launch {
            val noteId = appDataStore.currentNoteId.first()
            _navigateToNoteId.value = noteId.takeIf { it > 0 }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginResult.value = Result.Error("用户名和密码不能为空")
            return
        }

        _loginResult.value = Result.Loading
        viewModelScope.launch {
            val result = repository.login(username, password)
            _loginResult.value = result
            if (result is Result.Success) {
                val noteId = appDataStore.currentNoteId.first()
                _navigateToNoteId.value = noteId.takeIf { it > 0 }
            }
        }
    }

    class Factory(
        private val repository: AuthRepository,
        private val appDataStore: AppDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(repository, appDataStore) as T
        }
    }
}
