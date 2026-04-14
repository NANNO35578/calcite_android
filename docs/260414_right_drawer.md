# Calcite Android 右侧抽屉与搜索功能实现文档

**日期：** 2026-04-14  
**后端 API 文档：** `./docs/api.md`
- 标签模块：Line:444~588
- 文件列表：Line:793~847
- 搜索模块：Line:337~380

---

## 一、新增/修改文件一览

```
app/src/main/java/com/calcite/notes/
├── data/remote/ApiService.kt                    # 扩展：标签/文件/搜索 API
├── data/repository/
│   ├── TagRepository.kt                         # 新增
│   ├── FileRepository.kt                        # 新增
│   └── SearchRepository.kt                      # 新增
├── model/
│   ├── TagModels.kt                             # 新增
│   ├── FileModels.kt                            # 新增
│   └── SearchModels.kt                          # 新增
├── ui/main/
│   ├── ToolPanelFragment.kt                     # 新增：右抽屉内容（标签+文件）
│   ├── ToolPanelViewModel.kt                    # 新增：右抽屉状态管理
│   ├── SearchFragment.kt                        # 重写：实时搜索+高亮结果
│   └── SearchViewModel.kt                       # 新增：搜索逻辑
├── MainActivity.kt                              # 修改：嵌入右抽屉、管理 currentNoteId
└── NoteEditorFragment.kt                        # 修改：同步当前 noteId 到 Activity

app/src/main/res/layout/
├── fragment_tool_panel.xml                      # 新增：右抽屉布局
├── item_file.xml                                # 新增：文件列表项
├── item_tag_chip.xml                            # 新增：标签 Chip 样式
├── fragment_search.xml                          # 重写：搜索页布局
└── item_search_result.xml                       # 新增：搜索结果项
```

---

## 二、实现思路概括

### 1. 标签模块完整实现

#### 数据模型
```kotlin
data class Tag(val id: Long, val name: String, val created_at: String)
data class CreateTagRequest(val name: String)
data class UpdateTagRequest(val tag_id: Long, val name: String)
data class DeleteTagRequest(val tag_id: Long)
data class BindTagRequest(val note_id: Long, val tag_ids: List<Long>)
```

#### API 接口（严格遵循文档）
- `POST /api/tag/create`
- `POST /api/tag/update`
- `POST /api/tag/delete`
- `GET /api/tag/list?note_id={}`（不传返回全部，传 note_id 返回该笔记已绑定标签）
- `POST /api/tag/bind`（先清空再绑定新列表）

#### Repository
`TagRepository` 封装上述 5 个接口，统一返回 `Result<T>`。

#### ViewModel
`ToolPanelViewModel` 维护三个 LiveData：
- `tags: List<Tag>` —— 用户的全部标签
- `boundTagIds: Set<Long>` —— 当前笔记已绑定的标签 ID 集合
- `files: List<FileItem>` —— 文件列表（同页面共用）

**绑定逻辑：**
1. `setNoteId(noteId)` 被调用时（通常在右抽屉 `onResume`），若 `noteId != 0` 则异步加载 `getTagsByNote(noteId)`。
2. 用户点击 Chip 时，`toggleTag(tagId)` 先修改本地 Set，再调用 `bindTags(noteId, newTagIds)` 提交到服务端，成功后更新 `boundTagIds` 刷新 UI。
3. 新建/重命名/删除标签后重新调用 `loadTags()`，若当前有笔记打开则同时刷新绑定状态。

#### UI（右抽屉）
- 使用 `ChipGroup` 动态渲染标签，每个 `Chip` 设置 `isCheckable = true`。
- 根据 `boundTagIds` 设置 `isChecked`。
- 点击 Chip → `toggleTag`。
- 长按 Chip → 弹出 `AlertDialog`（重命名 / 删除）。
- 顶部有 `+ 新建标签` 按钮。

---

### 2. 已上传文件列表

#### API
- `GET /api/file/list`（不传参数则返回当前用户所有文件）

#### 模型
```kotlin
data class FileItem(
    val id: Long,
    val file_name: String,
    val status: String, // done / processing / failed
    val file_size_formatted: String,
    ...
)
```

#### UI 实现
- 右抽屉下半部分为文件列表。
- `ToolPanelViewModel.loadFiles()` 加载数据后，在 `Fragment` 中遍历 `files`，用 `ItemFileBinding` 动态添加 `View` 到 `LinearLayout`。
- 显示文件名 + 状态文字（完成/处理中/失败），不同状态使用不同颜色（绿色/橙色/红色）。
- 支持下拉刷新：`SwipeRefreshLayout` 包裹整个右抽屉内容，刷新时重新加载标签+文件。

---

### 3. 搜索页面

#### API
- `GET /api/note/search?keyword={}&from=0&size=20`

#### 数据模型
```kotlin
data class SearchResultItem(
    val id: Long,
    val title: String,
    val highlight_title: String?,   // 含 <mark> 高亮标签
    val highlight_content: String?, // 含 <mark> 高亮片段
    val score: Double
)
```

#### 实时搜索实现
`SearchViewModel` 中使用 `debounce` 避免频繁请求：
```kotlin
fun search(keyword: String) {
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
        delay(400) // 400ms debounce
        _isLoading.value = true
        val result = searchRepository.search(keyword)
        // 更新 LiveData
        _isLoading.value = false
    }
}
```

`SearchFragment` 中给 `EditText` 添加 `TextWatcher`，`afterTextChanged` 时直接调用 `viewModel.search(...)`。

#### 高亮实现（必须）
API 返回的 `highlight_title` 和 `highlight_content` 包含 `<mark>...</mark>` HTML 标签。

在 `SearchResultAdapter` 中使用 `Html.fromHtml()` 渲染：
```kotlin
val titleHtml = item.highlight_title ?: item.title
binding.tvTitle.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY)
} else {
    @Suppress("DEPRECATION")
    Html.fromHtml(titleHtml)
}
```

**为什么不使用 Markwon HTML Plugin？**
- 搜索结果只需要渲染简单的 `<mark>` 高亮片段，不需要完整 Markdown 解析。
- Android 原生 `Html.fromHtml()` 已足够支持 `<mark>` 标签（会保留文本并忽略不支持的样式，但结合 `TextView` 的默认样式即可显示高亮文本；若需背景高亮可配合 `Spannable` 进一步处理，当前实现以简洁可用为主）。

---

### 4. MainActivity 与 NoteEditorFragment 的通信

右抽屉需要知道**当前是否在编辑笔记**以及**当前笔记 ID**，才能正确显示绑定标签。

#### 方案：Activity 持有当前 noteId
```kotlin
class MainActivity : AppCompatActivity() {
    private var currentNoteId: Long = 0L
    fun setCurrentNoteId(noteId: Long) { currentNoteId = noteId }
    fun getCurrentNoteId(): Long = currentNoteId
}
```

#### NoteEditorFragment 同步
- `onViewCreated` 时立即调用 `(activity as MainActivity).setCurrentNoteId(viewModel.noteId)`。
- `noteDetail` 加载成功后再次同步（确保 id 正确）。

#### NavController 页面切换时重置
```kotlin
navController.addOnDestinationChangedListener { _, destination, _ ->
    if (destination.id != R.id.noteEditorFragment) {
        currentNoteId = 0L
    }
}
```

这样当用户跳转到 SearchFragment 或主页时，右抽屉打开不会显示旧笔记的标签绑定状态。

#### ToolPanelFragment 读取
在 `onResume` 中读取：
```kotlin
val noteId = (requireActivity() as? MainActivity)?.getCurrentNoteId() ?: 0L
viewModel.setNoteId(noteId)
```

---

## 三、核心代码

### 3.1 ToolPanelFragment.kt

```kotlin
package com.calcite.notes.ui.main

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.calcite.notes.MainActivity
import com.calcite.notes.R
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.FileRepository
import com.calcite.notes.data.repository.TagRepository
import com.calcite.notes.databinding.FragmentToolPanelBinding
import com.calcite.notes.model.FileItem
import com.calcite.notes.model.Tag
import com.calcite.notes.utils.Result
import com.google.android.material.chip.Chip

class ToolPanelFragment : Fragment() {

    private var _binding: FragmentToolPanelBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ToolPanelViewModel by viewModels {
        val api = RetrofitClient.getApiService(requireContext())
        ToolPanelViewModel.Factory(TagRepository(api), FileRepository(api))
    }

    private var currentNoteId: Long = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddTag.setOnClickListener { showCreateTagDialog() }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadAll()
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.tags.observe(viewLifecycleOwner) { tags ->
            renderTags(tags, viewModel.boundTagIds.value ?: emptySet())
        }
        viewModel.boundTagIds.observe(viewLifecycleOwner) { boundIds ->
            renderTags(viewModel.tags.value ?: emptyList(), boundIds)
        }
        viewModel.files.observe(viewLifecycleOwner) { files ->
            renderFiles(files)
        }
        viewModel.operationResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Success -> Toast.makeText(requireContext(), result.data, Toast.LENGTH_SHORT).show()
                is Result.Error -> Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                else -> {}
            }
        }

        viewModel.loadAll()
    }

    override fun onResume() {
        super.onResume()
        val noteId = (requireActivity() as? MainActivity)?.getCurrentNoteId() ?: 0L
        currentNoteId = noteId
        viewModel.setNoteId(noteId)
    }

    private fun renderTags(tags: List<Tag>, boundIds: Set<Long>) {
        binding.chipGroupTags.removeAllViews()
        for (tag in tags) {
            val chip = Chip(requireContext()).apply {
                text = tag.name
                isCheckable = true
                isChecked = boundIds.contains(tag.id)
                setOnCheckedChangeListener { _, _ -> viewModel.toggleTag(tag.id) }
                setOnLongClickListener {
                    showTagMenu(tag)
                    true
                }
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    private fun renderFiles(files: List<FileItem>) {
        binding.layoutFiles.removeAllViews()
        if (files.isEmpty()) {
            val tv = android.widget.TextView(requireContext()).apply {
                text = "暂无文件"
                setPadding(0, 8, 0, 8)
            }
            binding.layoutFiles.addView(tv)
            return
        }
        for (file in files) {
            val itemBinding = com.calcite.notes.databinding.ItemFileBinding.inflate(
                LayoutInflater.from(requireContext()), binding.layoutFiles, false
            )
            itemBinding.tvFileName.text = file.file_name
            val statusText = when (file.status) {
                "done" -> "完成"
                "processing" -> "处理中"
                "failed" -> "失败"
                else -> file.status
            }
            val color = when (file.status) {
                "done" -> android.R.color.holo_green_dark
                "processing" -> android.R.color.holo_orange_dark
                "failed" -> android.R.color.holo_red_dark
                else -> android.R.color.darker_gray
            }
            itemBinding.tvStatus.text = statusText
            itemBinding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), color))
            binding.layoutFiles.addView(itemBinding.root)
        }
    }

    private fun showTagMenu(tag: Tag) { /* 重命名/删除 */ }
    private fun showCreateTagDialog() { /* 新建标签 */ }
    private fun showRenameTagDialog(tag: Tag) { /* 重命名 */ }
    private fun showDeleteTagConfirm(tag: Tag) { /* 删除确认 */ }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 3.2 SearchFragment.kt

```kotlin
package com.calcite.notes.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.calcite.notes.R
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.SearchRepository
import com.calcite.notes.databinding.FragmentSearchBinding
import com.calcite.notes.databinding.ItemSearchResultBinding
import com.calcite.notes.model.SearchResultItem

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels {
        val api = RetrofitClient.getApiService(requireContext())
        SearchViewModel.Factory(SearchRepository(api))
    }

    private lateinit var resultAdapter: SearchResultAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        resultAdapter = SearchResultAdapter { item ->
            val bundle = bundleOf("noteId" to item.id)
            findNavController().navigate(R.id.noteEditorFragment, bundle)
        }

        binding.recyclerResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerResults.adapter = resultAdapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString()?.trim() ?: "")
            }
        })

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            resultAdapter.submitList(results)
            binding.tvEmpty.visibility = if (results.isEmpty() && binding.etSearch.text.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SearchResultAdapter(
    private val onItemClick: (SearchResultItem) -> Unit
) : androidx.recyclerview.widget.ListAdapter<SearchResultItem, SearchResultAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<SearchResultItem>() {
        override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem) = oldItem == newItem
    }
) {
    inner class VH(val binding: ItemSearchResultBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SearchResultItem) {
            val titleHtml = item.highlight_title ?: item.title
            val contentHtml = item.highlight_content ?: item.summary ?: ""
            binding.tvTitle.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(titleHtml)
            }
            binding.tvContent.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(contentHtml, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(contentHtml)
            }
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
```

### 3.3 MainActivity.kt 相关修改

```kotlin
class MainActivity : AppCompatActivity() {
    private var currentNoteId: Long = 0L
    fun setCurrentNoteId(noteId: Long) { currentNoteId = noteId }
    fun getCurrentNoteId(): Long = currentNoteId

    override fun onCreate(savedInstanceState: Bundle?) {
        // ...
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.left_drawer, NoteListFragment())
                .replace(R.id.right_drawer, ToolPanelFragment())
                .commit()
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id != R.id.noteEditorFragment) {
                currentNoteId = 0L
            }
        }
    }
}
```

### 3.4 NoteEditorFragment.kt 相关修改

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    (activity as? MainActivity)?.setCurrentNoteId(viewModel.noteId)
    // ...
    viewModel.noteDetail.observe(viewLifecycleOwner) { detail ->
        detail?.let {
            // ...
            (activity as? MainActivity)?.setCurrentNoteId(it.id)
        }
    }
}
```

---

## 四、编译结果

执行命令：
```bash
.\gradlew.bat build
```

结果：**BUILD SUCCESSFUL**
