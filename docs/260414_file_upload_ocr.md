# Calcite Android 文件上传与 OCR 功能实现文档

**日期：** 2026-04-14  
**后端 API 文档：** `./docs/api.md`
- 文件上传/删除/状态：Line:755~982
- OCR 识别/状态：Line:983~1115

---

## 一、新增/修改文件一览

```
app/src/main/java/com/calcite/notes/
├── data/remote/ApiService.kt                    # 扩展：Multipart 上传接口（file/upload、ocr/recognize）
├── data/repository/
│   ├── FileRepository.kt                        # 扩展：上传、删除、状态查询、详情查询
│   └── OcrRepository.kt                         # 新增：OCR 提交与状态查询
├── model/
│   └── FileModels.kt                            # 扩展：新增 FileUploadData、FileStatusData、OcrRecognizeData、OcrStatusData、DeleteFileRequest
├── ui/main/
│   ├── ToolPanelFragment.kt                     # 重写：添加文件上传、状态筛选、复制链接、删除文件
│   ├── ToolPanelViewModel.kt                    # 扩展：支持文件上传、删除、状态筛选
│   └── MainActivity.kt                          # 修改：OCR 底部按钮实现完整流程

app/src/main/res/layout/
├── fragment_tool_panel.xml                      # 修改：添加上传按钮+状态筛选 Spinner
└── item_file.xml                                # 已有：文件列表项（文件名+状态）
```

---

## 二、实现思路概括

### 1. 上传实现

#### Multipart 接口定义
Retrofit 接口中使用 `@Multipart` + `@Part` 注解：

```kotlin
@Multipart
@POST("/api/file/upload")
suspend fun uploadFile(
    @Part file: MultipartBody.Part,
    @Part("note_id") noteId: RequestBody? = null
): ApiResponse<FileUploadData>
```

`file` 参数为文件本身，`note_id` 为可选的文本表单字段（关联当前笔记）。

#### Uri → MultipartBody.Part 转换
在 `ToolPanelFragment` 和 `MainActivity` 中统一封装了 `uriToMultipartPart(uri)` 方法：
1. 通过 `ContentResolver.openInputStream(uri)` 读取文件内容。
2. 将内容拷贝到 `cacheDir` 下的临时文件（解决部分 URI 不支持直接 seek 的问题）。
3. 使用 `tempFile.asRequestBody(mimeType.toMediaTypeOrNull())` 创建 `RequestBody`。
4. 用 `MultipartBody.Part.createFormData("file", fileName, requestBody)` 生成 Part。

#### 右抽屉 UI
- **"+ 上传文件" 按钮**：点击后调用 `ActivityResultContracts.GetContent()` 启动系统文件选择器（`"*/*"` 任意文件）。
- **状态筛选 Spinner**：选项为"全部 / 完成 / 处理中 / 失败"，选择后调用 `viewModel.setFileStatusFilter(status)`，重新请求 `/api/file/list?status=xxx`。
- **文件列表长按菜单**：
  - 复制链接：将 `file.url` 写入系统剪贴板，Toast 提示。
  - 删除文件：弹出确认 Dialog，确认后调用 `FileRepository.deleteFile(fileId)`，成功后刷新列表。

---

### 2. OCR 轮询逻辑

#### 流程图
```
底部导航点击 OCR
    ↓
注册 ActivityResultContracts.GetContent("image/*") 选择图片
    ↓
Uri → MultipartBody.Part
    ↓
POST /api/ocr/recognize
    ↓
返回 file_id 与 processing 状态
    ↓
启动协程轮询：delay(2500ms) → GET /api/ocr/status?file_id=xxx
    ↓
    ├─ status == "done"   → 提取 note_id → Toast "OCR完成" → 跳转 NoteEditorFragment
    ├─ status == "failed" → Toast "OCR处理失败" → 停止轮询
    └─ 其他               → 继续轮询（最多 60 次，约 2.5 分钟）
```

#### MainActivity.kt 核心代码
```kotlin
private val ocrImagePicker = registerForActivityResult(
    ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let { startOcrProcess(it) }
}

private fun startOcrProcess(uri: Uri) {
    val part = uriToMultipartPart(uri) ?: return
    lifecycleScope.launch {
        Toast.makeText(this@MainActivity, "OCR 任务已提交", Toast.LENGTH_SHORT).show()
        val repo = OcrRepository(RetrofitClient.getApiService(this@MainActivity))
        when (val result = repo.recognize(part)) {
            is Result.Success -> pollOcrStatus(result.data.file_id)
            is Result.Error -> Toast.makeText(this@MainActivity, "OCR 提交失败: ${result.message}", Toast.LENGTH_LONG).show()
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
                        result.data.note_id?.let { noteId ->
                            Toast.makeText(this, "OCR 完成，已生成笔记", Toast.LENGTH_SHORT).show()
                            navController.navigate(R.id.noteEditorFragment, Bundle().apply { putLong("noteId", noteId) })
                        } ?: Toast.makeText(this, "OCR 完成但未获取到笔记", Toast.LENGTH_SHORT).show()
                        return
                    }
                    "failed" -> {
                        Toast.makeText(this, "OCR 处理失败", Toast.LENGTH_LONG).show()
                        return
                    }
                    else -> { /* continue polling */ }
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
```

---

### 3. UI 更新逻辑

#### 右抽屉（ToolPanelFragment）
- **标签区域**：保持不变，ChipGroup 动态渲染，支持点击绑定/长按菜单。
- **文件区域**：
  - `renderFiles(files)` 方法在 `viewModel.files.observe` 回调中执行。
  - 每次渲染前调用 `binding.layoutFiles.removeAllViews()` 清空旧列表。
  - 遍历 `files`，用 `ItemFileBinding` 动态生成行，设置文件名、状态文字、状态颜色。
  - 每行注册 `setOnLongClickListener` 弹出菜单（复制链接 / 删除）。
  - 删除成功后，`ViewModel` 内部自动调用 `loadFiles()`，触发 Observer 重新渲染。

#### 搜索页面（SearchFragment）
- 未在本次任务中修改，但保持与之前一致：实时搜索 + `Html.fromHtml()` 高亮渲染。

---

## 三、关键代码

### 3.1 ApiService.kt（Multipart 扩展）

```kotlin
@Multipart
@POST("/api/file/upload")
suspend fun uploadFile(
    @Part file: MultipartBody.Part,
    @Part("note_id") noteId: RequestBody? = null
): ApiResponse<FileUploadData>

@POST("/api/file/delete")
suspend fun deleteFile(@Body request: DeleteFileRequest): ApiResponse<Unit>

@GET("/api/file/status")
suspend fun getFileStatus(@Query("file_id") fileId: String): ApiResponse<FileStatusData>

@GET("/api/file/info")
suspend fun getFileInfo(@Query("file_id") fileId: String): ApiResponse<FileItem>

@Multipart
@POST("/api/ocr/recognize")
suspend fun ocrRecognize(@Part file: MultipartBody.Part): ApiResponse<OcrRecognizeData>

@GET("/api/ocr/status")
suspend fun getOcrStatus(@Query("file_id") fileId: String): ApiResponse<OcrStatusData>
```

### 3.2 FileRepository.kt

```kotlin
class FileRepository(private val apiService: ApiService) {

    suspend fun uploadFile(filePart: MultipartBody.Part, noteId: Long? = null): Result<FileUploadData> {
        return try {
            val noteBody = noteId?.let {
                it.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            }
            val response = apiService.uploadFile(filePart, noteBody)
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getFileList(status: String? = null): Result<List<FileItem>> {
        return try {
            val response = apiService.getFileList(status = status)
            if (response.code == 0) {
                Result.Success(response.data ?: emptyList())
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun deleteFile(fileId: Long): Result<Unit> { /* ... */ }
    suspend fun getFileStatus(fileId: Long): Result<FileStatusData> { /* ... */ }
    suspend fun getFileInfo(fileId: Long): Result<FileItem> { /* ... */ }
}
```

### 3.3 ToolPanelFragment.kt（文件相关片段）

```kotlin
private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let { uploadFile(it) }
}

binding.btnUploadFile.setOnClickListener { filePickerLauncher.launch("*/*") }

private fun setupStatusFilter() {
    val options = listOf("全部", "完成", "处理中", "失败")
    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    binding.spinnerStatusFilter.adapter = adapter
    binding.spinnerStatusFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val status = when (position) {
                1 -> "done"
                2 -> "processing"
                3 -> "failed"
                else -> null
            }
            viewModel.setFileStatusFilter(status)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
}

private fun renderFiles(files: List<FileItem>) {
    binding.layoutFiles.removeAllViews()
    if (files.isEmpty()) { /* 显示暂无文件 */ return }
    for (file in files) {
        val itemBinding = ItemFileBinding.inflate(LayoutInflater.from(requireContext()), binding.layoutFiles, false)
        itemBinding.tvFileName.text = file.file_name
        val (statusText, color) = when (file.status) {
            "done" -> "完成" to android.R.color.holo_green_dark
            "processing" -> "处理中" to android.R.color.holo_orange_dark
            "failed" -> "失败" to android.R.color.holo_red_dark
            else -> file.status to android.R.color.darker_gray
        }
        itemBinding.tvStatus.text = statusText
        itemBinding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), color))
        itemBinding.root.setOnLongClickListener {
            showFileMenu(file)
            true
        }
        binding.layoutFiles.addView(itemBinding.root)
    }
}

private fun showFileMenu(file: FileItem) {
    val options = arrayOf("复制链接", "删除")
    AlertDialog.Builder(requireContext())
        .setTitle(file.file_name)
        .setItems(options) { _, which ->
            when (which) {
                0 -> copyToClipboard(file.url)
                1 -> showDeleteFileConfirm(file)
            }
        }
        .show()
}

private fun copyToClipboard(text: String) {
    if (text.isBlank()) { Toast.makeText(requireContext(), "链接为空", Toast.LENGTH_SHORT).show(); return }
    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("链接", text))
    Toast.makeText(requireContext(), "链接已复制", Toast.LENGTH_SHORT).show()
}
```

---

## 四、编译结果

执行命令：
```bash
.\gradlew.bat build
```

结果：**BUILD SUCCESSFUL**
