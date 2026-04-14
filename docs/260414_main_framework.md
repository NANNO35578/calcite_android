# Calcite Android 主界面框架实现文档

**日期：** 2026-04-14  
**需求：** 在已有登录功能基础上，实现主界面框架（Single Activity + DrawerLayout + BottomNavigationView + Navigation）

---

## 一、架构设计

- **Single Activity：** 仅 `MainActivity` 一个 Activity
- **Navigation Component：** `nav_graph.xml` 管理所有 Fragment 路由
- **Fragment 架构：** 登录页与主界面共用同一个 NavHost，通过 destination 控制底部栏显隐

### 主界面结构

```
DrawerLayout
 ├── 主内容（ConstraintLayout）
 │    ├── FragmentContainerView（NavHost）
 │    └── BottomNavigationView
 ├── 左抽屉（FrameLayout）→ 嵌入 NoteListFragment
 └── 右抽屉（FrameLayout）→ 工具占位
```

---

## 二、新增/修改文件

```
app/src/main/java/com/calcite/notes/
├── MainActivity.kt                          # 修改：集成 DrawerLayout、BottomNav、WindowInsets
├── ui/main/
│   ├── NoteListFragment.kt                  # 新增：左抽屉内容（笔记列表占位）
│   ├── NoteEditorFragment.kt                # 新增：笔记编辑器占位（登录后默认首页）
│   └── SearchFragment.kt                    # 新增：搜索占位页
├── ui/login/LoginFragment.kt                # 修改：跳转目标改为 noteEditorFragment
├── ui/register/RegisterFragment.kt          # 修改：跳转目标改为 noteEditorFragment

app/src/main/res/
├── layout/
│   ├── activity_main.xml                    # 修改：DrawerLayout + 双抽屉 + BottomNav
│   ├── fragment_note_list.xml               # 新增
│   ├── fragment_note_editor.xml             # 新增
│   └── fragment_search.xml                  # 新增
├── menu/
│   └── bottom_nav_menu.xml                  # 新增：5 个底部菜单项
└── navigation/
    └── nav_graph.xml                        # 修改：新增主界面 Fragment 与全局搜索 action
```

---

## 三、代码实现

### 3.1 MainActivity.kt

```kotlin
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
```

### 3.2 activity_main.xml（DrawerLayout + BottomNavigationView）

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.drawerlayout.widget.DrawerLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/drawer_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true">

    <!-- 主内容区 -->
    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/main_content"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.fragment.app.FragmentContainerView
            android:id="@+id/nav_host_fragment"
            android:name="androidx.navigation.fragment.NavHostFragment"
            android:layout_width="0dp"
            android:layout_height="0dp"
            app:defaultNavHost="true"
            app:layout_constraintBottom_toTopOf="@id/bottom_nav"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            app:navGraph="@navigation/nav_graph" />

        <com.google.android.material.bottomnavigation.BottomNavigationView
            android:id="@+id/bottom_nav"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:background="?android:attr/windowBackground"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:menu="@menu/bottom_nav_menu" />

    </androidx.constraintlayout.widget.ConstraintLayout>

    <!-- 左抽屉：笔记树 -->
    <FrameLayout
        android:id="@+id/left_drawer"
        android:layout_width="300dp"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:background="?android:attr/colorBackground"
        android:fitsSystemWindows="true" />

    <!-- 右抽屉：工具 -->
    <FrameLayout
        android:id="@+id/right_drawer"
        android:layout_width="260dp"
        android:layout_height="match_parent"
        android:layout_gravity="end"
        android:background="?android:attr/colorBackground"
        android:fitsSystemWindows="true">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:text="工具面板（占位）"
            android:textSize="18sp" />
    </FrameLayout>

</androidx.drawerlayout.widget.DrawerLayout>
```

### 3.3 bottom_nav_menu.xml（BottomNavigationView 配置）

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">

    <item
        android:id="@+id/menu_notes"
        android:icon="@drawable/ic_launcher_foreground"
        android:title="笔记列表" />

    <item
        android:id="@+id/menu_new_note"
        android:icon="@drawable/ic_launcher_foreground"
        android:title="新建笔记" />

    <item
        android:id="@+id/menu_ocr"
        android:icon="@drawable/ic_launcher_foreground"
        android:title="OCR" />

    <item
        android:id="@+id/menu_search"
        android:icon="@drawable/ic_launcher_foreground"
        android:title="搜索" />

    <item
        android:id="@+id/menu_tools"
        android:icon="@drawable/ic_launcher_foreground"
        android:title="工具" />

</menu>
```

### 3.4 nav_graph.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/loginFragment">

    <!-- 认证页 -->
    <fragment
        android:id="@+id/loginFragment"
        android:name="com.calcite.notes.ui.login.LoginFragment"
        android:label="LoginFragment">
        <action
            android:id="@+id/action_loginFragment_to_registerFragment"
            app:destination="@id/registerFragment" />
        <action
            android:id="@+id/action_loginFragment_to_noteEditorFragment"
            app:destination="@id/noteEditorFragment"
            app:popUpTo="@id/nav_graph"
            app:popUpToInclusive="true" />
    </fragment>

    <fragment
        android:id="@+id/registerFragment"
        android:name="com.calcite.notes.ui.register.RegisterFragment"
        android:label="RegisterFragment">
        <action
            android:id="@+id/action_registerFragment_to_noteEditorFragment"
            app:destination="@id/noteEditorFragment"
            app:popUpTo="@id/nav_graph"
            app:popUpToInclusive="true" />
    </fragment>

    <!-- 主界面 Fragment -->
    <fragment
        android:id="@+id/noteEditorFragment"
        android:name="com.calcite.notes.ui.main.NoteEditorFragment"
        android:label="NoteEditorFragment" />

    <fragment
        android:id="@+id/noteListFragment"
        android:name="com.calcite.notes.ui.main.NoteListFragment"
        android:label="NoteListFragment" />

    <fragment
        android:id="@+id/searchFragment"
        android:name="com.calcite.notes.ui.main.SearchFragment"
        android:label="SearchFragment" />

    <!-- 全局搜索跳转 -->
    <action
        android:id="@+id/action_global_searchFragment"
        app:destination="@id/searchFragment" />

</navigation>
```

### 3.5 登录/注册跳转更新

**LoginFragment.kt（关键变更）**
```kotlin
// 登录成功后跳转到主界面默认页（noteEditorFragment），并清空返回栈
findNavController().navigate(R.id.action_loginFragment_to_noteEditorFragment)
```

**RegisterFragment.kt（关键变更）**
```kotlin
// 注册成功后跳转到主界面默认页（noteEditorFragment），并清空返回栈
findNavController().navigate(R.id.action_registerFragment_to_noteEditorFragment)
```

---

## 四、占位 Fragment 代码

### NoteListFragment.kt
```kotlin
package com.calcite.notes.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.calcite.notes.databinding.FragmentNoteListBinding

class NoteListFragment : Fragment() {

    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = "笔记列表（占位）"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### NoteEditorFragment.kt
```kotlin
package com.calcite.notes.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.calcite.notes.databinding.FragmentNoteEditorBinding

class NoteEditorFragment : Fragment() {

    private var _binding: FragmentNoteEditorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = "笔记编辑器（占位）"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### SearchFragment.kt
```kotlin
package com.calcite.notes.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.calcite.notes.databinding.FragmentSearchBinding

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = "搜索（占位）"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

---

## 五、交互逻辑说明

| 底部按钮 | 行为 |
|---------|------|
| 笔记列表 | `drawerLayout.openDrawer(GravityCompat.START)`，左抽屉滑出（显示 `NoteListFragment`） |
| 新建笔记 | Toast "新建笔记" |
| OCR | Toast "OCR" |
| 搜索 | `navController.navigate(R.id.action_global_searchFragment)`，跳转到 `SearchFragment` |
| 工具 | `drawerLayout.openDrawer(GravityCompat.END)`，右抽屉滑出（显示占位文本） |

### 返回键处理
通过 `OnBackPressedDispatcher` 注册回调，优先级：
1. 若左抽屉打开 → 关闭左抽屉
2. 若右抽屉打开 → 关闭右抽屉
3. 否则 → 执行默认返回逻辑

### 全面屏适配
在 `MainActivity` 中为 `main_content` 设置 `WindowInsets` 监听器，将系统栏（状态栏 + 导航栏）的 insets 作为 padding 应用到主内容区，避免内容被系统栏遮挡。

---

## 六、编译结果

执行命令：
```bash
.\gradlew.bat build
```

结果：**BUILD SUCCESSFUL**
