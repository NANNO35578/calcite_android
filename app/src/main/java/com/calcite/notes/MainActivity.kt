package com.calcite.notes

import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.calcite.notes.databinding.ActivityMainBinding
import com.calcite.notes.ui.main.NoteListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置 NavController
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 将 NoteListFragment 嵌入左抽屉
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.left_drawer, NoteListFragment())
                .commit()
        }

        // 底部导航栏点击事件
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_notes -> {
                    binding.drawerLayout.openDrawer(GravityCompat.START)
                    true
                }
                R.id.menu_new_note -> {
                    Toast.makeText(this, "新建笔记", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_ocr -> {
                    Toast.makeText(this, "OCR", Toast.LENGTH_SHORT).show()
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

        // 根据当前页面控制 BottomNavigationView 显隐
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isAuthPage = destination.id == R.id.loginFragment || destination.id == R.id.registerFragment
            binding.bottomNav.visibility = if (isAuthPage) android.view.View.GONE else android.view.View.VISIBLE
        }

        // 全面屏适配：状态栏与导航栏 insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 返回键分发：优先关闭抽屉
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
