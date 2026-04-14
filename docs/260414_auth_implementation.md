# Calcite Android 登录注册模块实现文档

**日期：** 2026-04-14  
**后端地址：** http://127.0.0.1:8888  
**API 文档：** `./docs/api.md` (Line:60~168)

---

## 一、技术栈

- **架构：** MVVM
- **语言：** Kotlin
- **UI 绑定：** ViewBinding
- **网络：** Retrofit + OkHttp + Gson
- **本地存储：** DataStore (Preferences)
- **导航：** Navigation Component
- **异步：** Kotlin Coroutines + LiveData

---

## 二、模块结构

```
app/src/main/java/com/calcite/notes/
├── MainActivity.kt
├── data/
│   ├── local/
│   │   └── TokenDataStore.kt          # DataStore Token 管理
│   ├── remote/
│   │   ├── ApiService.kt              # Retrofit 接口定义
│   │   ├── AuthInterceptor.kt         # OkHttp 自动注入 Bearer Token
│   │   └── RetrofitClient.kt          # Retrofit 单例构建
│   └── repository/
│       └── AuthRepository.kt          # 登录/注册仓库
├── model/
│   └── AuthModels.kt                  # 请求/响应数据模型
├── ui/
│   ├── login/
│   │   ├── LoginFragment.kt
│   │   └── LoginViewModel.kt
│   ├── register/
│   │   ├── RegisterFragment.kt
│   │   └── RegisterViewModel.kt
│   └── home/
│       └── HomeFragment.kt            # 登录成功后跳转的空白主界面
├── utils/
│   └── Result.kt                      # 统一结果封装 (Success/Error/Loading)
```

---

## 三、网络层

### 3.1 数据模型 (AuthModels.kt)

```kotlin
package com.calcite.notes.model

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class LoginData(
    val user_id: Long,
    val username: String,
    val token: String
)

data class RegisterData(
    val user_id: Long,
    val token: String
)
```

### 3.2 Retrofit 接口 (ApiService.kt)

```kotlin
package com.calcite.notes.data.remote

import com.calcite.notes.model.ApiResponse
import com.calcite.notes.model.LoginData
import com.calcite.notes.model.LoginRequest
import com.calcite.notes.model.RegisterData
import com.calcite.notes.model.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginData>

    @POST("/api/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<RegisterData>
}
```

### 3.3 Token 存储 (TokenDataStore.kt)

```kotlin
package com.calcite.notes.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TokenDataStore(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")
        private val TOKEN_KEY = stringPreferencesKey("token")
    }

    val token: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
        }
    }
}
```

### 3.4 OkHttp 拦截器 (AuthInterceptor.kt)

```kotlin
package com.calcite.notes.data.remote

import com.calcite.notes.data.local.TokenDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor

class AuthInterceptor(private val tokenDataStore: TokenDataStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val token = runBlocking { tokenDataStore.token.first() }

        val newRequest = if (!token.isNullOrEmpty()) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        return chain.proceed(newRequest)
    }
}
```

### 3.5 Retrofit 单例 (RetrofitClient.kt)

```kotlin
package com.calcite.notes.data.remote

import android.content.Context
import com.calcite.notes.data.local.TokenDataStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL = "http://127.0.0.1:8888"

    fun getApiService(context: Context): ApiService {
        val tokenDataStore = TokenDataStore(context)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenDataStore))
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
```

---

## 四、Repository 层

### 4.1 AuthRepository.kt

```kotlin
package com.calcite.notes.data.repository

import com.calcite.notes.data.local.TokenDataStore
import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.LoginData
import com.calcite.notes.model.LoginRequest
import com.calcite.notes.model.RegisterData
import com.calcite.notes.model.RegisterRequest
import com.calcite.notes.utils.Result

class AuthRepository(
    private val apiService: ApiService,
    private val tokenDataStore: TokenDataStore
) {

    suspend fun login(username: String, password: String): Result<LoginData> {
        return try {
            val response = apiService.login(LoginRequest(username, password))
            if (response.code == 0 && response.data != null) {
                tokenDataStore.saveToken(response.data.token)
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun register(username: String, email: String, password: String): Result<RegisterData> {
        return try {
            val response = apiService.register(RegisterRequest(username, email, password))
            if (response.code == 0 && response.data != null) {
                tokenDataStore.saveToken(response.data.token)
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }
}
```

### 4.2 结果封装 (Result.kt)

```kotlin
package com.calcite.notes.utils

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}
```

---

## 五、ViewModel 层

### 5.1 LoginViewModel.kt

```kotlin
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
```

### 5.2 RegisterViewModel.kt

```kotlin
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
```

---

## 六、UI 层

### 6.1 MainActivity.kt

```kotlin
package com.calcite.notes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.calcite.notes.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
```

### 6.2 LoginFragment.kt

```kotlin
package com.calcite.notes.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.calcite.notes.R
import com.calcite.notes.data.local.TokenDataStore
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.AuthRepository
import com.calcite.notes.databinding.FragmentLoginBinding
import com.calcite.notes.utils.Result

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        val apiService = RetrofitClient.getApiService(requireContext())
        val tokenDataStore = TokenDataStore(requireContext())
        val repository = AuthRepository(apiService, tokenDataStore)
        LoginViewModel.Factory(repository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.login(username, password)
        }

        binding.tvGoRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        viewModel.loginResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> {
                    binding.btnLogin.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }

                is Result.Success -> {
                    binding.btnLogin.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                }

                is Result.Error -> {
                    binding.btnLogin.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 6.3 RegisterFragment.kt

```kotlin
package com.calcite.notes.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.calcite.notes.R
import com.calcite.notes.data.local.TokenDataStore
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.AuthRepository
import com.calcite.notes.databinding.FragmentRegisterBinding
import com.calcite.notes.utils.Result

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels {
        val apiService = RetrofitClient.getApiService(requireContext())
        val tokenDataStore = TokenDataStore(requireContext())
        val repository = AuthRepository(apiService, tokenDataStore)
        RegisterViewModel.Factory(repository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.register(username, email, password)
        }

        binding.tvGoLogin.setOnClickListener {
            findNavController().navigateUp()
        }

        viewModel.registerResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> {
                    binding.btnRegister.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }

                is Result.Success -> {
                    binding.btnRegister.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "注册成功", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_registerFragment_to_homeFragment)
                }

                is Result.Error -> {
                    binding.btnRegister.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 6.4 HomeFragment.kt

```kotlin
package com.calcite.notes.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.calcite.notes.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = "主界面（占位）"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

---

## 七、导航与布局

### 7.1 导航图 (res/navigation/nav_graph.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/loginFragment">

    <fragment
        android:id="@+id/loginFragment"
        android:name="com.calcite.notes.ui.login.LoginFragment"
        android:label="LoginFragment">
        <action
            android:id="@+id/action_loginFragment_to_registerFragment"
            app:destination="@id/registerFragment" />
        <action
            android:id="@+id/action_loginFragment_to_homeFragment"
            app:destination="@id/homeFragment"
            app:popUpTo="@id/nav_graph"
            app:popUpToInclusive="true" />
    </fragment>

    <fragment
        android:id="@+id/registerFragment"
        android:name="com.calcite.notes.ui.register.RegisterFragment"
        android:label="RegisterFragment">
        <action
            android:id="@+id/action_registerFragment_to_homeFragment"
            app:destination="@id/homeFragment"
            app:popUpTo="@id/nav_graph"
            app:popUpToInclusive="true" />
    </fragment>

    <fragment
        android:id="@+id/homeFragment"
        android:name="com.calcite.notes.ui.home.HomeFragment"
        android:label="HomeFragment" />

</navigation>
```

> **说明：** 登录/注册成功跳转主界面时，通过 `app:popUpTo="@id/nav_graph" app:popUpToInclusive="true"` 清空返回栈，防止按返回键回到登录页。

### 7.2 布局说明

- **整体：** `ConstraintLayout`，内容垂直居中，顶部标题位于屏幕 1/3 处。
- **登录页 (`fragment_login.xml`)：** 标题 `Calcite` + 用户名输入框 + 密码输入框（支持切换可见性）+ 登录按钮 + 跳转到注册文字。
- **注册页 (`fragment_register.xml`)：** 标题 `Calcite` + 用户名输入框 + 邮箱输入框 + 密码输入框 + 注册按钮 + 跳转到登录文字。
- **主界面 (`fragment_home.xml`)：** 空白占位，显示 "主界面（占位）"。

---

## 八、AndroidManifest.xml 配置

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest ...>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        ...
        android:usesCleartextTraffic="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

> **注意：**
> - `android:usesCleartextTraffic="true"` 允许明文 HTTP 请求（后端为 http://127.0.0.1:8888）。
> - 若使用 Android 模拟器运行，且后端运行在宿主机上，请将 `RetrofitClient.kt` 中的 `BASE_URL` 改为 `http://10.0.2.2:8888`。

---

## 九、Gradle 依赖变更

### 9.1 `gradle/libs.versions.toml`

新增了以下库版本：
- `navigation = "2.8.9"`
- `retrofit = "2.11.0"`
- `okhttp = "4.12.0"`
- `datastore = "1.1.4"`
- `lifecycle = "2.8.7"`
- `coroutines = "1.10.1"`
- `constraintlayout = "2.2.1"`
- `fragmentKtx = "1.8.6"`
- `activityKtx = "1.10.1"`

### 9.2 `app/build.gradle.kts`

开启 `viewBinding = true`，并引入 Navigation、Retrofit、OkHttp、DataStore、Lifecycle、Coroutines、Fragment/Activity KTX 等依赖。

---

## 十、编译结果

执行命令：
```bash
.\gradlew.bat build
```

结果：**BUILD SUCCESSFUL**
