package com.calcite.notes

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.data.repository.OcrRepository
import com.calcite.notes.data.repository.UserRepository
import com.calcite.notes.data.sync.SyncWorker
import com.calcite.notes.databinding.ActivityMainBinding
import com.calcite.notes.ui.main.NoteListFragment
import com.calcite.notes.ui.main.ToolPanelFragment
import com.calcite.notes.utils.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val appDataStore by lazy { AppDataStore(this) }
    private val db by lazy { AppDatabase.getInstance(this) }

    private val ocrImagePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { startOcrProcess(it) }
    }

    fun setCurrentNoteId(noteId: Long) {
        lifecycleScope.launch {
            appDataStore.setCurrentNoteId(noteId)
        }
    }

    fun getCurrentNoteId(): Long {
        // 由于需要同步返回值，这里返回内存中的值或通过 runBlocking 读取
        // 实际使用场景中，Fragment 会在 onResume 时重新获取
        return 0L
    }

    suspend fun getCurrentNoteIdAsync(): Long {
        return appDataStore.currentNoteId.first()
    }

    private fun createNewNoteAndEdit() {
        lifecycleScope.launch {
            val repo = NoteRepository(RetrofitClient.getApiService(this@MainActivity), db.noteDao())
            val result = repo.createNote(this@MainActivity, "未命名笔记", "")
            if (result is Result.Success) {
                val noteId = result.data.note_id
                setCurrentNoteId(noteId)
                val bundle = Bundle().apply { putLong("noteId", noteId) }
                if (navController.currentDestination?.id != R.id.noteEditorFragment) {
                    navController.navigate(R.id.noteEditorFragment, bundle)
                } else {
                    navController.navigate(R.id.noteEditorFragment, bundle)
                }
            } else {
                Toast.makeText(this@MainActivity, "创建笔记失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startOcrProcess(uri: Uri) {
        val part = uriToMultipartPart(uri) ?: return
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "OCR 任务已提交", Toast.LENGTH_SHORT).show()
            val repo = OcrRepository(RetrofitClient.getApiService(this@MainActivity))
            when (val result = repo.recognize(part)) {
                is Result.Success -> {
                    val fileId = result.data.file_id
                    pollOcrStatus(fileId)
                }
                is Result.Error -> {
                    Toast.makeText(this@MainActivity, "OCR 提交失败: ${result.message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private suspend fun pollOcrStatus(fileId: Long) {
        val repo = OcrRepository(RetrofitClient.getApiService(this@MainActivity))
        repeat(60) {
            delay(2500)
            when (val result = repo.getStatus(fileId)) {
                is Result.Success -> {
                    when (result.data.status) {
                        "done" -> {
                            val noteId = result.data.note_id
                            if (noteId != null && noteId > 0) {
                                Toast.makeText(this, "OCR 完成，已生成笔记", Toast.LENGTH_SHORT).show()
                                setCurrentNoteId(noteId)
                                val bundle = Bundle().apply { putLong("noteId", noteId) }
                                navController.navigate(R.id.noteEditorFragment, bundle)
                            } else {
                                Toast.makeText(this, "OCR 完成但未获取到笔记", Toast.LENGTH_SHORT).show()
                            }
                            return
                        }
                        "failed" -> {
                            Toast.makeText(this, "OCR 处理失败", Toast.LENGTH_LONG).show()
                            return
                        }
                        else -> { }
                    }
                }
                is Result.Error -> {
                    Toast.makeText(this, "查询 OCR 状态失败: ${result.message}", Toast.LENGTH_SHORT).show()
                    return
                }
                else -> {}
            }
        }
        Toast.makeText(this, "OCR 处理超时，请稍后手动查询", Toast.LENGTH_LONG).show()
    }

    private fun uriToMultipartPart(uri: Uri): MultipartBody.Part? {
        val contentResolver = contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = getFileNameFromUri(uri) ?: "unknown"
        val tempFile = File(cacheDir, fileName)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }
        val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", fileName, requestFile)
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 启动时检查 token
        lifecycleScope.launch {
            val userRepo = UserRepository(
                RetrofitClient.getApiService(this@MainActivity),
                appDataStore,
                db
            )
            val isValid = userRepo.isTokenValid()
            if (!isValid) {
                if (navController.currentDestination?.id != R.id.loginFragment) {
                    navController.navigate(R.id.loginFragment)
                }
            } else {
                // 启动同步 Worker
                SyncWorker.enqueue(this@MainActivity)
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.left_drawer, NoteListFragment())
                .replace(R.id.right_drawer, ToolPanelFragment())
                .commit()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_notes -> {
                    binding.drawerLayout.openDrawer(GravityCompat.START)
                    true
                }
                R.id.menu_new_note -> {
                    createNewNoteAndEdit()
                    true
                }
                R.id.menu_ocr -> {
                    ocrImagePicker.launch("image/*")
                    true
                }
                R.id.menu_search -> {
                    if (navController.currentDestination?.id != R.id.searchFragment) {
                        navController.navigate(R.id.action_global_searchFragment)
                    }
                    true
                }
                R.id.menu_tools -> {
                    binding.drawerLayout.openDrawer(GravityCompat.END)
                    true
                }
                else -> false
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isAuthPage = destination.id == R.id.loginFragment || destination.id == R.id.registerFragment
            binding.bottomNav.visibility = if (isAuthPage) android.view.View.GONE else android.view.View.VISIBLE
            if (destination.id != R.id.noteEditorFragment) {
                lifecycleScope.launch {
                    appDataStore.setCurrentNoteId(0L)
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                binding.drawerLayout.isDrawerOpen(GravityCompat.END) -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                }
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
    }
}
