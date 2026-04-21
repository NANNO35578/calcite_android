# Calcite Notes Android

> 本文档面向 AI 编程助手。阅读者被假设对该项目一无所知。

## 项目概述

Calcite Notes Android 是一款基于 Kotlin 开发的 Android 笔记应用，采用 **MVVM + Repository** 架构，支持在线/离线双模式运行。应用通过 Retrofit 与后端 REST API 通信，使用 Room 作为本地缓存数据库，并通过 WorkManager 实现后台定时同步。

核心功能包括：
- JWT 用户认证（登录 / 注册）
- 笔记的增删改查与 Markdown 实时预览
- 文件夹树形层级管理
- 标签绑定与管理
- 文件上传、状态查询与删除
- OCR 图片识别生成笔记
- 全文搜索（后端 Elasticsearch 支持）
- 离线优先：无网络时自动读取 Room 本地缓存

---

## 技术栈

| 层级 | 技术 / 库 | 版本 |
|------|----------|------|
| 构建系统 | Android Gradle Plugin (AGP) | 8.13.0 |
| 语言 | Kotlin | 2.0.21 |
| 编译 SDK | Android API | 36 |
| 最低 SDK | Android API | 24 |
| 目标 SDK | Android API | 36 |
| JVM 目标 | Java 11 | — |
| UI 绑定 | ViewBinding | — |
| 导航 | Navigation Component | 2.8.9 |
| 网络 | Retrofit + OkHttp + Gson | 2.11.0 / 4.12.0 |
| 本地存储 | Room (SQLite) | 2.7.1 |
| 偏好存储 | DataStore Preferences | 1.1.4 |
| 生命周期 | ViewModel + LiveData + Lifecycle | 2.8.7 |
| 异步 | Kotlin Coroutines | 1.10.1 |
| Markdown | Markwon + Coil | 4.6.2 / 2.5.0 |
| 后台同步 | WorkManager | 2.10.0 |
| 注解处理 | KSP | 2.0.21-1.0.27 |

---

## 项目结构

```
app/src/main/java/com/calcite/notes/
├── MainActivity.kt              # 主 Activity：抽屉布局 + 底部导航 + Token 校验 + OCR 入口
├── data/
│   ├── local/
│   │   ├── AppDataStore.kt      # DataStore：token、token_expire_time、current_note_id
│   │   ├── dao/                 # Room DAO（NoteDao、TagDao、FolderDao、FileDao、NoteTagDao）
│   │   ├── database/
│   │   │   └── AppDatabase.kt   # Room 数据库定义（5 张表，版本 1）
│   │   └── entity/              # Room Entity（Note、Tag、Folder、File、NoteTagCrossRef）
│   ├── remote/
│   │   ├── ApiService.kt        # Retrofit 接口：Auth / Note / Folder / Tag / File / OCR
│   │   ├── AuthInterceptor.kt   # OkHttp 拦截器：自动注入 Bearer Token
│   │   └── RetrofitClient.kt    # Retrofit 单例，BASE_URL = http://127.0.0.1:8888
│   ├── repository/
│   │   ├── AuthRepository.kt
│   │   ├── NoteRepository.kt    # 离线优先：先调 API，成功后回写 Room
│   │   ├── FolderRepository.kt
│   │   ├── TagRepository.kt
│   │   ├── FileRepository.kt
│   │   ├── OcrRepository.kt
│   │   ├── SearchRepository.kt
│   │   └── UserRepository.kt    # Token 有效性校验、退出登录
│   └── sync/
│       └── SyncWorker.kt        # WorkManager 定时同步（每 10 分钟）
├── model/                       # 纯 Kotlin data class（请求体 / 响应体）
│   ├── AuthModels.kt
│   ├── NoteModels.kt
│   ├── FolderModels.kt
│   ├── TagModels.kt
│   ├── FileModels.kt
│   ├── SearchModels.kt
│   └── UserModels.kt
├── ui/
│   ├── login/
│   │   ├── LoginFragment.kt
│   │   └── LoginViewModel.kt
│   ├── register/
│   │   ├── RegisterFragment.kt
│   │   └── RegisterViewModel.kt
│   ├── home/
│   │   └── HomeFragment.kt      # 登录成功后进入的主界面容器
│   └── main/
│       ├── MainViewModel.kt     # 全局 ViewModel：新建笔记、OCR 轮询
│       ├── NoteEditorFragment.kt / NoteEditorViewModel.kt
│       ├── NoteListFragment.kt / NoteListViewModel.kt   # 左抽屉：树形笔记列表
│       ├── SearchFragment.kt / SearchViewModel.kt
│       ├── ToolPanelFragment.kt / ToolPanelViewModel.kt # 右抽屉：标签 + 文件
│       └── tree/
│           ├── TreeAdapter.kt
│           └── TreeNode.kt
└── utils/
    ├── NetworkUtils.kt          # 网络可用性检测
    └── Result.kt                # 统一结果封装：Success / Error / Loading

app/src/main/res/
├── navigation/nav_graph.xml     # Navigation 导航图
├── layout/                      # Fragment / item / bottom_sheet 布局
├── drawable/                    # 矢量图标（ic_folder_24、ic_note_24 等）
└── menu/bottom_nav_menu.xml     # 底部导航菜单

docs/
├── api.md                       # 后端 REST API 完整文档（~1300 行）
├── 260413_offline_sync.md       # 离线同步 + Room + WorkManager 实现文档
├── 260414_auth_implementation.md
├── 260414_bugfix.md
├── 260414_file_upload_ocr.md
├── 260414_main_framework.md
├── 260414_note_system.md
└── 260414_right_drawer.md
```

---

## 架构与数据流

应用采用 **离线优先（Offline-First）** 架构：

```
UI (Fragment)
  ↓ 观察
ViewModel (LiveData / StateFlow)
  ↓ 调用
Repository (ApiService + Room DAO)
  ↓ 读取
Room Database (Flow) → UI 自动刷新
  ↓ 有网络时
Retrofit → 后端 API → 成功后回写 Room
```

### 核心原则

1. **读操作**：优先从 Room 读取（通过 `Flow` 持续观察）；有网络时调 API 回写 Room，UI 随 Flow 自动刷新。
2. **写操作**：必须先调后端 API，成功后再更新 Room；**无网络时禁止本地伪造写入**，直接返回错误提示，保证数据一致性。
3. **同步策略**：`SyncWorker` 每 10 分钟全量拉取服务端数据，覆盖写入本地 Room。当前为“服务端优先覆盖”策略，未实现增量同步或冲突合并。
4. **Token 生命周期**：登录时写入 DataStore 并设置 7 天有效期；启动时 `MainActivity` 校验 Token，过期则跳转登录页；`SyncWorker` 执行前也会校验 Token，过期则清空所有本地数据并发送广播通知 UI。

---

## 构建与运行

### 环境要求
- Android Studio（推荐最新稳定版）
- JDK 11+
- Android SDK API 36

### 常用命令

```bash
# Windows
.\gradlew.bat build

# 安装调试 APK
.\gradlew.bat installDebug

# 运行单元测试
.\gradlew.bat test

# 运行 instrumentation 测试
.\gradlew.bat connectedAndroidTest
```

### 关键配置
- `gradle/libs.versions.toml`：集中管理所有依赖版本。
- `app/build.gradle.kts`：开启 `viewBinding = true`，使用 KSP 处理 Room 注解。
- `gradle.properties`：`android.useAndroidX=true`，`kotlin.code.style=official`，`android.nonTransitiveRClass=true`。

---

## 代码规范

- **语言**：全部源代码使用 **Kotlin**；注释和文档以 **中文** 为主。
- **代码风格**：`kotlin.code.style=official`。
- **非传递 R 类**：已开启 `android.nonTransitiveRClass=true`，各模块 R 类只包含自身资源。
- **异步**：UI 层统一使用 Kotlin Coroutines + `lifecycleScope` / `viewModelScope`；禁止在主线程直接执行网络或数据库操作。
- **结果封装**：所有 Repository 方法返回自定义 `Result<T>`（`Success` / `Error` / `Loading`），ViewModel 通过 `LiveData` 暴露给 Fragment。
- **ViewModel 工厂**：由于需要传入 `Context` 或 `noteId` 等参数，各 ViewModel 均提供内部 `Factory` 类，通过 `by viewModels { Factory(...) }` 创建。

---

## 测试策略

> **现状**：项目目前仅有 Android Studio 模板生成的占位测试，尚未建立实际业务测试覆盖。

| 测试类型 | 位置 | 框架 | 说明 |
|---------|------|------|------|
| 本地单元测试 | `app/src/test/` | JUnit 4 | 在 JVM 上运行，可测试纯 Kotlin 逻辑 |
| Instrumented 测试 | `app/src/androidTest/` | AndroidJUnit4 + Espresso | 需真机/模拟器，可测试 Android 组件 |

### 建议补充方向
- Repository 层：使用 Mock Web Server 测试 Retrofit 接口异常与正常路径。
- ViewModel 层：使用 `kotlinx-coroutines-test` 测试协程逻辑与 `LiveData` 状态流转。
- DAO 层：使用 Room 的内存数据库实例测试 SQL 查询。

---

## 安全与注意事项

1. **明文 HTTP**：`AndroidManifest.xml` 中设置了 `android:usesCleartextTraffic="true"`，允许明文 HTTP 请求。当前后端地址为 `http://127.0.0.1:8888`，若使用 Android 模拟器需改为 `http://10.0.2.2:8888`。
2. **Token 存储**：JWT Token 以明文形式存储在 DataStore Preferences 中，未加密。生产环境建议迁移至 EncryptedSharedPreferences 或 Android Keystore。
3. **硬编码后端地址**：`RetrofitClient.kt` 中 `BASE_URL` 为硬编码的 `http://127.0.0.1:8888`，生产环境应改为可配置项（如 `BuildConfig` 或 `local.properties`）。
4. **数据库 Schema**：Room 数据库当前版本为 1，`exportSchema = false`。若后续修改 Entity，需手动处理迁移或增加版本号。
5. **依赖冲突排除**：`app/build.gradle.kts` 末尾全局排除了 `org.jetbrains:annotations-java5`，若引入新库出现注解冲突可检查此处。

---

## 关键业务逻辑速查

### 启动流程
1. `MainActivity.onCreate` → 检查 `UserRepository.isTokenValid()`。
2. Token 有效 → 清空回退栈并导航至 `HomeFragment`，启动 `SyncWorker`。
3. Token 无效 → 停留在 `LoginFragment`（`nav_graph` 的 `startDestination`）。

### 返回键统一处理
- 优先关闭左右抽屉。
- 当前页为 `loginFragment` / `registerFragment` / `noteEditorFragment` → `finish()` 退出应用。
- 其他页面 → 导航至 `noteEditorFragment`（最近编辑的笔记），并清空中间回退栈，防止多层返回。

### OCR 流程
1. 底部导航点击 OCR → `ActivityResultContracts.GetContent("image/*")` 选图。
2. `MainViewModel.startOcr(uri)` → 提交 `POST /api/ocr/recognize`。
3. 获取 `file_id` 后启动协程轮询：`GET /api/ocr/status?file_id=xxx`，每 2.5 秒一次，最多 60 次。
4. 状态为 `done` 且返回 `note_id` → 跳转 `NoteEditorFragment`；`failed` → Toast 提示失败。

### 文件上传
- 右抽屉（`ToolPanelFragment`）提供“+ 上传文件”按钮，支持任意文件类型（`*/*`）。
- 上传接口为 Multipart：`POST /api/file/upload`，可选 `note_id` 字段关联当前笔记。
- URI 通过 `ContentResolver` 读取并拷贝到 `cacheDir` 临时文件，再转换为 `MultipartBody.Part`。

### 同步 Worker
- `SyncWorker` 使用 `PeriodicWorkRequestBuilder<SyncWorker>(10, TimeUnit.MINUTES)`。
- 同步内容依次包括：笔记（递归拉取所有文件夹下的笔记并逐个获取详情）、标签、文件夹（BFS 递归）、文件。
- Token 过期时发送广播 `com.calcite.notes.ACTION_TOKEN_EXPIRED`，`MainActivity` 接收后清空 DataStore 并跳转登录页。
