package com.calcite.notes.ui.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.repository.AuthRepository
import com.calcite.notes.model.RegisterData
import com.calcite.notes.utils.Result
import kotlinx.coroutines.launch

class RegisterViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _registerResult = MutableLiveData<Result<RegisterData>>()
    val registerResult: LiveData<Result<RegisterData>> = _registerResult

    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _registerResult.value = Result.Error("用户名和密码不能为空")
            return
        }
        if (email.isBlank()) {
            _registerResult.value = Result.Error("邮箱不能为空")
            return
        }

        _registerResult.value = Result.Loading
        viewModelScope.launch {
            _registerResult.value = repository.register(username, email, password)
        }
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RegisterViewModel(repository) as T
        }
    }
}
