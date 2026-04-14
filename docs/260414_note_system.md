# Calcite Android 左侧抽屉与笔记系统实现文档

**日期：** 2026-04-14  
**后端 API 文档：** `./docs/api.md`（Line:170~337 笔记模块，Line:588~712 文件夹模块）

---

## 一、新增/修改文件一览

```
app/src/main/java/com/calcite/notes/
├── data/remote/ApiService.kt                    # 扩展：笔记/文件夹/用户 API
├── data/repository/
│   ├── NoteRepository.kt                        # 新增
│   ├── FolderRepository.kt                      # 新增
│   └── UserRepository.kt                        # 新增
├── model/
│   ├── NoteModels.kt                            # 新增
│   ├── FolderModels.kt                          # 新增
│   └── UserModels.kt                            # 新增
├── ui/main/
│   ├── NoteListFragment.kt                      # 重写：左侧抽屉（树形列表）
│   ├── NoteListViewModel.kt                     # 新增：树状态管理
│   ├── NoteEditorFragment.kt                    # 重写：Markdown 编辑器
│   ├── NoteEditorViewModel.kt                   # 新增：编辑状态+自动保存
│   └── tree/
│       ├── TreeNode.kt                          # 新增：树节点数据模型
│       └── TreeAdapter.kt                       # 新增：树形 RecyclerView Adapter
├── MainActivity.kt                              # 修改：底部“新建笔记”直接创建并跳转
└── gradle/libs.versions.toml                    # 修改：引入 Markwon

app/src/main/res/layout/
├── fragment_note_list.xml                       # 重写：抽屉布局（顶部按钮+RecyclerView+底部用户）
├── fragment_note_editor.xml                     # 重写：Markdown 编辑/预览布局
├── item_tree_folder.xml                         # 新增：文件夹行
└── item_tree_note.xml                           # 新增：笔记行
```

---

## 二、数据结构设计

### 2.1 统一响应与业务模型

- **`ApiResponse<T>`**：复用已有的统一包装，含 `code`、`message`、`data`。
- **`Note`**：列表返回的简略字段（`id`, `title`, `summary`, `folder_id`, `created_at`, `updated_at`）。
- **`NoteDetail`**：详情返回的完整字段，额外包含 `content`。
- **`Folder`**：`id`, `name`, `parent_id`, `created_at`。
- **`UserProfile`**：`user_id`, `username`, `email`, `created_at`。

### 2.2 树节点模型（扁平化表示）

为了适配 `RecyclerView`，采用**扁平化层级模型**，通过 `level` 控制缩进。

```kotlin
sealed class TreeNode {
    abstract val level: Int

    data class FolderNode(
        val folder: Folder,
        override val level: Int,
        var isExpanded: Boolean = false,
        val hasLoadedChildren: Boolean = false
    ) : TreeNode()

    data class NoteNode(
        val note: Note,
        override val level: Int
    ) : TreeNode()
}
```

**设计理由：**
- `RecyclerView` 本身只支持线性列表，不支持真正的嵌套树。
- 将树展开后的可见节点按顺序存入 `List<TreeNode>`，即可用 `RecyclerView` 模拟 `el-tree` 的展开/折叠效果。
- `level` 决定左侧缩进距离（`paddingStart = 16 + level * 32`），视觉上千真万确呈现层级。

---

## 三、API 接口

### 3.1 ApiService.kt 扩展内容

严格遵循 `./docs/api.md`，新增以下接口：

```kotlin
// 用户
@GET("/api/user/profile")
suspend fun getUserProfile(): ApiResponse<UserProfile>

// 笔记
data class CreateNoteRequest(...)
data class UpdateNoteRequest(...)
data class DeleteNoteRequest(...)

@POST("/api/note/create")
suspend fun createNote(@Body request: CreateNoteRequest): ApiResponse<NoteCreateData>

@POST("/api/note/update")
suspend fun updateNote(@Body request: UpdateNoteRequest): ApiResponse<Unit>

@POST("/api/note/delete")
suspend fun deleteNote(@Body request: DeleteNoteRequest): ApiResponse<Unit>

@GET("/api/note/list")
suspend fun getNoteList(@Query("folder_id") folderId: Long): ApiResponse<List<Note>>

@GET("/api/note/detail")
suspend fun getNoteDetail(@Query("note_id") noteId: Long): ApiResponse<NoteDetail>

// 文件夹
data class CreateFolderRequest(...)
data class UpdateFolderRequest(...)
data class DeleteFolderRequest(...)

@POST("/api/folder/create")
suspend fun createFolder(@Body request: CreateFolderRequest): ApiResponse<FolderCreateData>

@POST("/api/folder/update")
suspend fun updateFolder(@Body request: UpdateFolderRequest): ApiResponse<Unit>

@POST("/api/folder/delete")
suspend fun deleteFolder(@Body request: DeleteFolderRequest): ApiResponse<Unit>

@GET("/api/folder/list")
suspend fun getFolderList(@Query("folder_id") folderId: Long): ApiResponse<List<Folder>>
```

**鉴权说明：** Retrofit 已配置 `AuthInterceptor`，会自动注入 `Authorization: Bearer {token}`，新增接口无需额外处理 Header。

---

## 四、Repository 实现思路

按模块拆分为三个 Repository，统一返回 `Result<T>`：

1. **`NoteRepository`**：
   - `createNote(title, content, folderId)` → 创建空白笔记
   - `updateNote(noteId, title?, content?, summary?, folderId?)` → 自动保存调用
   - `deleteNote(noteId)`
   - `getNoteList(folderId)` → 获取某文件夹下笔记列表
   - `getNoteDetail(noteId)` → 获取完整内容

2. **`FolderRepository`**：
   - `createFolder(name, parentId)`
   - `updateFolder(folderId, name?, parentId?)`
   - `deleteFolder(folderId)`
   - `getFolderList(parentId)` → 获取某文件夹的直接子文件夹

3. **`UserRepository`**：
   - `getUserProfile()` → 获取用户名展示在抽屉底部
   - `logout()` → 调用 `TokenDataStore.clearToken()`

所有方法均包裹 `try-catch`，网络异常时返回 `Result.Error(e.message ?: "网络请求失败")`。

---

## 五、ViewModel 设计

### 5.1 NoteListViewModel（左侧抽屉）

**核心状态：**
- `_treeNodes: MutableLiveData<List<TreeNode>>` —— RecyclerView 的数据源。
- `_userProfile: MutableLiveData<UserProfile?>` —— 底部用户信息。
- `_operationResult: MutableLiveData<Result<String>>` —— 新建/删除/重命名操作的结果提示。

**树状态管理：**
- `childrenCache: Map<Long, List<TreeNode>>` —— 缓存已加载过的文件夹子节点，避免重复请求。
- `expandedFolders: Set<Long>` —— 记录当前处于展开状态的文件夹 ID。

**关键方法：**
- `loadRoot()`：
  1. 清空缓存与展开状态。
  2. 并行调用 `getFolderList(0)` 和 `getNoteList(0)`。
  3. 将结果转换为 `TreeNode`（level=0），按名称排序后提交给 LiveData。

- `toggleFolder(folderNode)`：
  - **折叠**：遍历当前列表，删除该文件夹之后所有 `level > folderNode.level` 的节点，直到遇到同级或更高级节点；将该节点 `isExpanded` 置为 `false`。
  - **展开**：
    - 若 `childrenCache` 中存在该文件夹的缓存子节点，直接插入列表并标记展开。
    - 否则异步加载该文件夹下的子文件夹和笔记（`getFolderList(folderId)` + `getNoteList(folderId)`），转换为 `TreeNode`（level = parentLevel + 1），排序后存入缓存并插入列表。

- `createNote(title, folderId)` / `createFolder(name, parentId)`：
  1. 调用对应 Repository 方法。
  2. 成功后调用 `refreshAffectedFolder(folderId)`，清除该文件夹的缓存并触发重新加载（若该文件夹当前是展开状态，则先折叠再展开）。

- `getAllFolders()`：
  递归遍历当前 `treeNodes` 及 `childrenCache` 中所有 `FolderNode`，收集全部文件夹供 Dialog 中的 Spinner 选择父文件夹使用。

### 5.2 NoteEditorViewModel（笔记编辑）

**核心状态：**
- `_noteDetail: MutableLiveData<NoteDetail?>` —— 当前编辑的笔记内容（标题+正文）。
- `_isPreview: MutableLiveData<Boolean>` —— 是否处于 Markdown 预览模式。
- `_hasUnsavedChanges: MutableLiveData<Boolean>` —— 是否存在未保存的修改。
- `_saveResult / _loadResult` —— 保存/加载结果。

**自动保存机制：**
```kotlin
private fun startAutoSave() {
    autoSaveJob = viewModelScope.launch {
        while (true) {
            delay(5000)
            if (_hasUnsavedChanges.value == true && noteId != 0L) {
                saveNote()
            }
        }
    }
}
```
- 每 5 秒检查一次 `_hasUnsavedChanges`。
- 判断方式：将当前 `title` / `content` 与 `lastSavedTitle` / `lastSavedContent` 比较。
- `onPause` 时也会强制触发一次保存。

---

## 六、RecyclerView Adapter（树结构）

### 6.1 TreeAdapter

继承 `ListAdapter<TreeNode, RecyclerView.ViewHolder>`，使用 `DiffUtil` 优化刷新性能。

```kotlin
class TreeAdapter(
    private val onFolderClick: (TreeNode.FolderNode, Int) -> Unit,
    private val onNoteClick: (TreeNode.NoteNode) -> Unit,
    private val onFolderLongClick: (TreeNode.FolderNode, View) -> Boolean
) : ListAdapter<TreeNode, RecyclerView.ViewHolder>(TreeDiffCallback())
```

**ViewHolder 类型：**
- **`FolderViewHolder`**：
  - 左侧箭头图标，通过 `rotation = 90f / 0f` 表示展开/折叠。
  - 文件夹图标 + 名称。
  - `setPaddingRelative(paddingStart, 0, 0, 0)` 实现缩进。
  - 点击事件 → `onFolderClick`（触发 ViewModel 的 `toggleFolder`）。
  - 长按事件 → `onFolderLongClick`（弹出重命名/删除菜单）。

- **`NoteViewHolder`**：
  - 文档图标 + 笔记标题。
  - 同样有 `level` 缩进。
  - 点击事件 → `onNoteClick`（跳转到 `NoteEditorFragment`）。

**DiffUtil 策略：**
- `areItemsTheSame`：通过 `id + level` 判断是否是同一个可见项（同一文件夹在不同层级可出现多次，因此必须联合 `level`）。
- `areContentsTheSame`：直接比较 `TreeNode` 的 `equals`（data class 自动生成）。

---

## 七、Fragment 实现

### 7.1 NoteListFragment（左侧抽屉）

**布局结构（`fragment_note_list.xml`）：**
- **顶部** `LinearLayout`（横向）：`新建笔记` | `新建文件夹` | `OCR`
- **中部** `RecyclerView`：`treeAdapter` 展示文件树
- **底部** `LinearLayout`（横向）：用户头像（占位）+ 用户名 `TextView` + `退出` 按钮

**主要交互：**
1. **点击笔记**：调用 `findNavController().navigate(R.id.noteEditorFragment, bundleOf("noteId" to noteId))` 跳转到编辑器。
2. **点击文件夹**：调用 `viewModel.toggleFolder(node)` 展开/折叠。
3. **长按文件夹**：弹出 `AlertDialog` 菜单（`重命名` / `删除`）。
4. **顶部“新建笔记”/“新建文件夹”**：弹出自定义 Dialog，内含：
   - `EditText` 输入名称
   - `Spinner` 选择父文件夹（数据来自 `viewModel.getAllFolders()`，首项为“根目录”）
5. **底部“退出”**：调用 `viewModel.logout()` → `findNavController().navigate(R.id.loginFragment)`。

### 7.2 NoteEditorFragment（Markdown 编辑器）

**布局结构（`fragment_note_editor.xml`）：**
- **顶部标题栏**：左侧可点击的 `TextView`（显示标题），右侧 `Button`（预览/编辑切换）。
- **未保存提示**：右上角红色 `TextView` "未保存"，默认 `gone`。
- **中部容器** `FrameLayout`：
  - `EditText`（编辑模式，多行，默认可见）
  - `NestedScrollView + TextView`（预览模式，默认 `gone`）

**Markdown 渲染：**
- 引入 `io.noties.markwon:core`（及 tables、strikethrough 扩展）。
- 初始化：`Markwon.create(requireContext())`。
- 预览时：`markwon.setMarkdown(binding.tvPreview, content)`。

**自动保存与未保存提示：**
- `EditText` 的 `afterTextChanged` 中调用 `viewModel.updateContent(title, content)`，ViewModel 内部比对上次保存状态，更新 `_hasUnsavedChanges`。
- Fragment 观察 `_hasUnsavedChanges`，为 `true` 时显示红色“未保存”标签。
- `onPause` 中检查未保存状态，有则立即触发 `saveNote()`。

**标题修改：**
- 点击顶部标题弹出 `AlertDialog` + `EditText`，输入新标题后同步更新 `TextView` 并调用 `viewModel.updateContent()`。

---

## 八、MainActivity 整合

### 8.1 底部“新建笔记”

```kotlin
private fun createNewNoteAndEdit() {
    lifecycleScope.launch {
        val repo = NoteRepository(RetrofitClient.getApiService(this@MainActivity))
        val result = repo.createNote("未命名笔记", "")
        if (result is Result.Success) {
            val bundle = Bundle().apply { putLong("noteId", result.data.note_id) }
            navController.navigate(R.id.noteEditorFragment, bundle)
        } else {
            Toast.makeText(this@MainActivity, "创建笔记失败", Toast.LENGTH_SHORT).show()
        }
    }
}
```

- 直接在根目录创建一篇空白笔记（`folder_id=0`）。
- 成功后携带 `noteId` 跳转到 `NoteEditorFragment`。

### 8.2 左抽屉嵌入

```kotlin
if (savedInstanceState == null) {
    supportFragmentManager.beginTransaction()
        .replace(R.id.left_drawer, NoteListFragment())
        .commit()
}
```

---

## 九、编译结果

执行命令：
```bash
.\gradlew.bat build
```

结果：**BUILD SUCCESSFUL**
