package com.calcite.notes.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.repository.AuthRepository
import com.calcite.notes.model.LoginData
import com.calcite.notes.utils.Result
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _loginResult = MutableLiveData<Result<LoginData>>()
    val loginResult: LiveData<Result<LoginData>> = _loginResult

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginResult.value = Result.Error("用户名和密码不能为空")
            return
        }

        _loginResult.value = Result.Loading
        viewModelScope.launch {
            _loginResult.value = repository.login(username, password)
        }
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(repository) as T
        }
    }
}
