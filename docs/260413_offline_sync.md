# 离线模式 + 数据同步 + UI 修复 实现文档

> 文档生成时间：2026-04-15  
> 对应需求：在已有项目基础上实现离线优先架构、Room 本地存储、WorkManager 定时同步、左右抽屉 UI 修复。

---

## 一、整体架构思路

本阶段核心目标为 **"离线优先"（Offline-First）**：

```
UI → ViewModel → Repository → Room (Flow) → UI 自动刷新
                ↓
         有网络时 → 调用 Retrofit API → 回写 Room
         无网络时 → 直接读取 Room，禁止网络请求
```

所有数据变更遵循：**先写服务端，再写本地 Room**；同步时则 **拉取服务端数据覆盖/合并本地**。

---

## 二、Room 数据库设计

### 2.1 Entity 设计

| Entity | 说明 | 关键字段 |
|--------|------|---------|
| `NoteEntity` | 笔记 | `id`, `title`, `content`, `summary`, `folderId`, `createdAt`, `updatedAt`, `isDeleted` |
| `TagEntity` | 标签 | `id`, `name`, `createdAt` |
| `FolderEntity` | 文件夹 | `id`, `name`, `parentId`, `createdAt` |
| `FileEntity` | 已上传文件 | `id`, `userId`, `noteId`, `fileName`, `fileType`, `fileSize`, `url`, `status`, ... |
| `NoteTagCrossRef` | 笔记-标签中间表 | `noteId`, `tagId` (联合主键) |

### 2.2 DAO 设计

每个 DAO 均提供：
- **Flow 查询**：供 UI/ViewModel 持续观察（如 `getAll(): Flow<List<T>>`）
- **Suspend 同步查询**：供同步 Worker 或协程内一次性读取（如 `getAllSync(): List<T>`）
- **写操作**：`insert()` / `insertAll()` / `update()` / `deleteById()` / `deleteAll()`

例如 `NoteDao`：

```kotlin
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getByFolderId(folderId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId AND isDeleted = 0 LIMIT 1")
    fun getById(noteId: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAllSync(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Query("UPDATE notes SET isDeleted = 1 WHERE id = :noteId")
    suspend fun markDeleted(noteId: Long)
    // ...
}
```

### 2.3 Database 定义

```kotlin
@Database(
    entities = [NoteEntity::class, TagEntity::class, FolderEntity::class, FileEntity::class, NoteTagCrossRef::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun noteTagDao(): NoteTagDao
}
```

---

## 三、DataStore 实现

使用 `androidx.datastore:datastore-preferences` 存储以下键值：

| Key | 类型 | 说明 |
|-----|------|------|
| `token` | String | JWT Token |
| `token_expire_time` | Long | Token 过期时间戳（登录/注册时写入当前时间 + 7天） |
| `current_note_id` | Long | 当前打开的笔记 ID（进入 NoteEditor 时更新） |

核心 API：

```kotlin
class AppDataStore(private val context: Context) {
    val token: Flow<String?>
    val tokenExpireTime: Flow<Long>
    val currentNoteId: Flow<Long>

    suspend fun saveToken(token: String)        // 同时写入过期时间
    suspend fun clearToken()
    suspend fun setCurrentNoteId(noteId: Long)
    suspend fun clearAll()
}
```

旧的 `TokenDataStore` 已废弃，全局统一使用 `AppDataStore`。

---

## 四、Repository（含同步逻辑）

### 4.1 设计原则

所有 Repository 同时持有 **ApiService** 和 **Room DAO**。

- **读操作**：
  - 有网络 → 请求 API → 成功后回写 Room → 返回数据
  - 无网络 → 直接读取 Room（通过 Flow 或一次性查询）
- **写操作**：
  - 有网络 → 请求 API → 成功后更新 Room
  - 无网络 → 返回错误，禁止本地伪造（保证数据一致性）

### 4.2 各 Repository 职责

#### NoteRepository
- `getNoteListLocal(folderId): Flow<List<Note>>` — 本地观察
- `getNoteDetailLocal(noteId): Flow<NoteDetail?>` — 本地观察
- `createNote(context, title, content, folderId)` — 先 POST /api/note/create，再 GET detail 回写 Room
- `updateNote(context, noteId, ...)` — 先 POST /api/note/update，再回写 Room
- `deleteNote(context, noteId)` — 先 POST /api/note/delete，再本地 `markDeleted`

#### TagRepository
- `getAllTagsLocal(): Flow<List<Tag>>`
- `getTagsByNoteLocal(noteId): Flow<List<Tag>>`
- `bindTags(context, noteId, tagIds)` — 先 POST /api/tag/bind，再更新 `note_tags` 中间表
- `createTag` / `updateTag` / `deleteTag` — 均先调 API 再写 Room

#### FolderRepository
- `getFolderListLocal(parentId): Flow<List<Folder>>`
- `createFolder` / `updateFolder` / `deleteFolder` — 均先调 API 再写 Room

#### FileRepository
- `getFileListLocal(status): Flow<List<FileItem>>`
- `uploadFile` / `deleteFile` / `getFileList` — 均先调 API 再写 Room

### 4.3 网络检测

通过 `NetworkUtils.isNetworkAvailable(context)` 判断网络状态：

```kotlin
object NetworkUtils {
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
```

---

## 五、WorkManager 同步实现

### 5.1 SyncWorker

使用 `CoroutineWorker`，**每 10 分钟** 执行一次：

```kotlin
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(10, TimeUnit.MINUTES)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync_worker", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
```

### 5.2 同步流程

1. **Token 校验**：读取 DataStore 的 `token` 和 `token_expire_time`
   - 若过期/不存在 → `clearAll()` + `deleteAll()` 所有 Room 表 → `Result.failure()`
2. **拉取笔记**：递归调用 `GET /api/note/list`（根目录 + 所有子文件夹），再逐个 `GET /api/note/detail` 获取完整 content，批量写入 Room
3. **拉取标签**：`GET /api/tag/list` 写入 `TagEntity`；遍历所有笔记 `GET /api/tag/list?note_id=xxx` 更新 `NoteTagCrossRef`
4. **拉取文件夹**：递归 BFS 调用 `GET /api/folder/list`，批量写入 `FolderEntity`
5. **拉取文件**：`GET /api/file/list`，批量写入 `FileEntity`

### 5.3 生命周期

- **登录成功/启动验证通过** → `SyncWorker.enqueue(this)`
- **退出登录** → `SyncWorker.cancel(this)`（可选，因退出后 Token 已清空，Worker 下次执行会自行失败）

---

## 六、登录后行为修复

### 6.1 逻辑

`LoginFragment` 登录成功后：

```kotlin
val noteId = appDataStore.currentNoteId.first()
if (noteId > 0) {
    // 有上次打开的笔记 → 直接进入 NoteEditorFragment
    findNavController().navigate(R.id.action_loginFragment_to_noteEditorFragment, bundleOf("noteId" to noteId))
} else {
    // 无记录 → 进入 HomeFragment（主界面，左抽屉显示根目录笔记树）
    findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
}
```

### 6.2 禁止自动新建空笔记

原 `MainActivity.createNewNoteAndEdit()` 仅在用户点击底部导航栏 **"新建笔记"** 时调用，登录流程中不再自动触发。

---

## 七、左抽屉 UI 优化（NoteListFragment）

### 7.1 文件树优化

- **图标区分**：`item_tree_folder.xml` 使用 `ic_folder_24`，`item_tree_note.xml` 使用 `ic_note_24`
- **根目录笔记显示**：根目录同时加载 `folderRepository.getFolderListLocal(0)` 和 `noteRepository.getNoteListLocal(0)`，文件夹在前、笔记在后
- **展开/折叠**：点击文件夹切换状态，子节点通过递归 `buildSubTree()` 动态加载并插入 RecyclerView

### 7.2 长按菜单（BottomSheetDialog）

使用 `BottomSheetDialog` 替代原来的 `AlertDialog`：

- **文件夹长按**：`bottom_sheet_folder_menu.xml`
  - 新建笔记
  - 重命名
  - 删除
- **笔记长按**：`bottom_sheet_note_menu.xml`
  - 重命名
  - 删除

`TreeAdapter` 新增 `onNoteLongClick` 回调，Fragment 中分别处理两种菜单。

---

## 八、右抽屉 UI 优化（ToolPanelFragment）

### 8.1 顶部 Tab 切换

布局新增 `TabLayout`，两个 Tab：
- **标签**
- **已上传文件**

通过 `tabLayout.addOnTabSelectedListener` 控制 `layoutTags` 与 `layoutFilesContainer` 的显隐。

### 8.2 标签管理逻辑

#### 情况 1：无 `current_note_id`（noteId == 0）
- 只显示 **全部标签** 区域
- 每个标签 Chip 支持 **长按** → 弹出菜单（重命名 / 删除）
- 提供 "+ 新建标签" 按钮

#### 情况 2：有 `current_note_id`
- **上半部分**：当前笔记已绑定标签（带关闭图标的 Chip），点击关闭图标调用 `viewModel.unbindTag(tagId)`
- **中间分隔线**
- **下半部分**：全部未绑定标签（普通 Chip），点击调用 `viewModel.bindTag(tagId)`
- 标签操作均先调用 `/api/tag/bind`，再更新 Room，`Flow` 自动驱动 UI 刷新

### 8.3 文件列表

保留原有文件列表逻辑，增加状态过滤 Spinner，文件项长按支持 "复制链接 / 删除"。

---

## 九、Token 过期与数据一致性

### 9.1 Token 过期处理

- **MainActivity.onCreate**：启动时检查 `UserRepository.isTokenValid()`，无效则导航到 `loginFragment`
- **SyncWorker**：每次执行前校验 Token，过期则 `clearAll()` + 清空 Room 所有表 → `Result.failure()`
- **退出登录**：`UserRepository.logout()` 清空 DataStore 与 Room

### 9.2 当前笔记记录

`NoteEditorViewModel.init` 中：

```kotlin
if (noteId != 0L) {
    loadNote()
    viewModelScope.launch { appDataStore.setCurrentNoteId(noteId) }
}
```

`MainActivity` 的 `navController.addOnDestinationChangedListener` 中，当离开 `noteEditorFragment` 时：

```kotlin
if (destination.id != R.id.noteEditorFragment) {
    lifecycleScope.launch { appDataStore.setCurrentNoteId(0L) }
}
```

### 9.3 数据一致性策略

| 场景 | 策略 |
|------|------|
| 用户增删改 | 先调 API，成功后更新 Room |
| SyncWorker 运行 | 拉取全量服务端数据，覆盖写入 Room |
| 无网络 | 只读 Room，禁止写操作调 API |
| 本地缓存失效 | SyncWorker / 手动刷新重新拉取 |

---

## 十、关键文件变更清单

### 新增文件

```
data/local/entity/     NoteEntity.kt, TagEntity.kt, FolderEntity.kt, FileEntity.kt, NoteTagCrossRef.kt
data/local/dao/        NoteDao.kt, TagDao.kt, FolderDao.kt, FileDao.kt, NoteTagDao.kt
data/local/database/   AppDatabase.kt
data/local/            AppDataStore.kt
data/sync/             SyncWorker.kt
utils/                 NetworkUtils.kt
res/layout/            bottom_sheet_folder_menu.xml, bottom_sheet_note_menu.xml
res/drawable/          ic_folder_24.xml, ic_note_24.xml
```

### 修改文件

```
gradle/libs.versions.toml          (+ Room, WorkManager)
app/build.gradle.kts               (+ ksp plugin, Room, WorkManager deps)
MainActivity.kt                    (+ Token 检查, SyncWorker 启动, DataStore 集成)
LoginFragment.kt / LoginViewModel.kt   (+ 登录后读取 current_note_id 决定导航)
RegisterFragment.kt                  (+ 使用 AppDataStore)
NoteListFragment.kt / NoteListViewModel.kt   (+ 本地 Flow 驱动, BottomSheet, 图标区分)
ToolPanelFragment.kt / ToolPanelViewModel.kt (+ Tab 切换, 标签分区 bind/unbind)
NoteEditorFragment.kt / NoteEditorViewModel.kt (+ 本地优先加载, 自动保存)
nav_graph.xml                      (+ homeFragment, 更新 action)
TreeAdapter.kt                     (+ onNoteLongClick)
item_tree_folder.xml / item_tree_note.xml    (+ 新图标)
所有 Repository.kt 文件             (+ 本地+远程双写, 离线判断)
RetrofitClient.kt / AuthInterceptor.kt       (+ 改用 AppDataStore)
```

---

## 十一、后续建议

1. **冲突处理**：当前同步策略为服务端优先覆盖本地。若后续需要多端协作，可引入 `sync_version` 或 `last_modified` 字段做三方合并。
2. **增量同步**：当前 SyncWorker 为全量拉取，数据量大时可改为按 `updated_at` 增量拉取。
3. **离线写队列**：当前无网络时禁止写操作。若需支持"离线编辑后续同步"，可引入 `PendingOperation` 表 + Worker 重试机制。
