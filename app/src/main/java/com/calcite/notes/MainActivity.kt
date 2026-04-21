package com.calcite.notes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.UserRepository
import com.calcite.notes.data.sync.SyncWorker
import com.calcite.notes.databinding.ActivityMainBinding
import com.calcite.notes.ui.main.MainViewModel
import com.calcite.notes.ui.main.NoteListFragment
import com.calcite.notes.ui.main.ToolPanelFragment
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var tokenExpiredReceiver: BroadcastReceiver

    private val appDataStore by lazy { AppDataStore(this) }
    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory(this) }

    fun setCurrentNoteId(noteId: Long) {
        lifecycleScope.launch {
            appDataStore.setCurrentNoteId(noteId)
        }
    }

    suspend fun getCurrentNoteIdAsync(): Long {
        return appDataStore.currentNoteId.first()
    }

    private val ocrImagePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { mainViewModel.startOcr(it) }
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
                AppDatabase.getInstance(this@MainActivity)
            )
            val isValid = userRepo.isTokenValid()
            if (isValid) {
                // token 有效，直接进入主界面
                if (navController.currentDestination?.id == R.id.loginFragment) {
                    navController.navigate(
                        R.id.homeFragment,
                        null,
                        NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                    )
                }
                SyncWorker.enqueue(this@MainActivity)
            } else {
                // token 无效或过期，确保停留在登录页
                if (navController.currentDestination?.id != R.id.loginFragment) {
                    navController.navigate(
                        R.id.loginFragment,
                        null,
                        NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                    )
                }
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
                    mainViewModel.createNewNote()
                    true
                }
                R.id.menu_recommend -> {
                    if (navController.currentDestination?.id != R.id.recommendFragment) {
                        navController.navigate(R.id.recommendFragment)
                    }
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
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 统一返回逻辑
        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                binding.drawerLayout.isDrawerOpen(GravityCompat.END) -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                }
                else -> {
                    val currentId = navController.currentDestination?.id
                    when (currentId) {
                        R.id.loginFragment, R.id.registerFragment, R.id.noteEditorFragment -> {
                            finish()
                        }
                        R.id.recommendFragment, R.id.notePreviewFragment, R.id.searchFragment -> {
                            // 返回最近编辑的笔记，清空中间回退栈
                            lifecycleScope.launch {
                                val noteId = appDataStore.currentNoteId.first()
                                if (noteId > 0) {
                                    val bundle = Bundle().apply { putLong("noteId", noteId) }
                                    navController.navigate(
                                        R.id.noteEditorFragment,
                                        bundle,
                                        NavOptions.Builder()
                                            .setPopUpTo(R.id.nav_graph, true)
                                            .build()
                                    )
                                } else {
                                    navController.navigate(
                                        R.id.homeFragment,
                                        null,
                                        NavOptions.Builder()
                                            .setPopUpTo(R.id.nav_graph, true)
                                            .build()
                                    )
                                }
                            }
                        }
                        else -> {
                            // 其他页面返回最近编辑的笔记，禁止多层返回
                            lifecycleScope.launch {
                                val noteId = appDataStore.currentNoteId.first()
                                if (noteId > 0) {
                                    val bundle = Bundle().apply { putLong("noteId", noteId) }
                                    navController.navigate(
                                        R.id.noteEditorFragment,
                                        bundle,
                                        NavOptions.Builder()
                                            .setPopUpTo(R.id.nav_graph, true)
                                            .build()
                                    )
                                } else {
                                    finish()
                                }
                            }
                        }
                    }
                }
            }
        }

        // ViewModel 观察
        mainViewModel.createNoteResult.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    val noteId = result.data.note_id
                    setCurrentNoteId(noteId)
                    val bundle = Bundle().apply { putLong("noteId", noteId) }
                    navController.navigate(R.id.noteEditorFragment, bundle)
                }
                is Result.Error -> Toast.makeText(this, "创建笔记失败", Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }

        mainViewModel.ocrStatus.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    val status = result.data
                    if (status.error != null) {
                        Toast.makeText(this, status.error, Toast.LENGTH_LONG).show()
                    } else if (status.done && status.noteId != null && status.noteId > 0) {
                        Toast.makeText(this, "OCR 完成，已生成笔记", Toast.LENGTH_SHORT).show()
                        setCurrentNoteId(status.noteId)
                        val bundle = Bundle().apply { putLong("noteId", status.noteId) }
                        navController.navigate(R.id.noteEditorFragment, bundle)
                    } else if (status.done) {
                        Toast.makeText(this, "OCR 完成但未获取到笔记", Toast.LENGTH_SHORT).show()
                    }
                }
                is Result.Error -> {
                    Toast.makeText(this, "OCR 失败: ${result.message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        // Token 过期广播接收器
        tokenExpiredReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.calcite.notes.ACTION_TOKEN_EXPIRED") {
                    lifecycleScope.launch {
                        appDataStore.clearAll()
                        if (navController.currentDestination?.id != R.id.loginFragment) {
                            navController.navigate(
                                R.id.loginFragment,
                                null,
                                NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, true)
                                    .build()
                            )
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("com.calcite.notes.ACTION_TOKEN_EXPIRED")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            tokenExpiredReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tokenExpiredReceiver.isInitialized) {
            unregisterReceiver(tokenExpiredReceiver)
        }
    }
}
