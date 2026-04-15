package com.calcite.notes.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.data.repository.OcrRepository
import com.calcite.notes.model.NoteCreateData
import com.calcite.notes.utils.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class MainViewModel(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val ocrRepository: OcrRepository
) : ViewModel() {

    private val _createNoteResult = MutableLiveData<Result<NoteCreateData>>()
    val createNoteResult: LiveData<Result<NoteCreateData>> = _createNoteResult

    private val _ocrStatus = MutableLiveData<Result<OcrStatus>>()
    val ocrStatus: LiveData<Result<OcrStatus>> = _ocrStatus

    data class OcrStatus(val fileId: Long, val done: Boolean, val noteId: Long?, val error: String?)

    fun createNewNote(title: String = "未命名笔记", content: String = "", folderId: Long = 0) {
        viewModelScope.launch {
            _createNoteResult.value = Result.Loading
            val result = noteRepository.createNote(context, title, content, folderId)
            _createNoteResult.value = result
        }
    }

    fun startOcr(uri: Uri) {
        viewModelScope.launch {
            _ocrStatus.value = Result.Loading
            val part = uriToMultipartPart(uri)
            if (part == null) {
                _ocrStatus.value = Result.Error("文件读取失败")
                return@launch
            }
            when (val result = ocrRepository.recognize(part)) {
                is Result.Success -> {
                    pollOcrStatus(result.data.file_id)
                }
                is Result.Error -> {
                    _ocrStatus.value = result
                }
                else -> {}
            }
        }
    }

    private suspend fun pollOcrStatus(fileId: Long) {
        repeat(60) {
            delay(2500)
            when (val result = ocrRepository.getStatus(fileId)) {
                is Result.Success -> {
                    when (result.data.status) {
                        "done" -> {
                            _ocrStatus.postValue(
                                Result.Success(OcrStatus(fileId, true, result.data.note_id, null))
                            )
                            return
                        }
                        "failed" -> {
                            _ocrStatus.postValue(
                                Result.Success(OcrStatus(fileId, true, null, "OCR 处理失败"))
                            )
                            return
                        }
                        else -> { }
                    }
                }
                is Result.Error -> {
                    _ocrStatus.postValue(result)
                    return
                }
                else -> {}
            }
        }
        _ocrStatus.postValue(Result.Error("OCR 处理超时，请稍后手动查询"))
    }

    private fun uriToMultipartPart(uri: Uri): MultipartBody.Part? {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = getFileNameFromUri(uri, contentResolver) ?: "unknown"
        val tempFile = File(context.cacheDir, fileName)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }
        val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", fileName, requestFile)
    }

    private fun getFileNameFromUri(uri: Uri, contentResolver: android.content.ContentResolver): String? {
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

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val api = RetrofitClient.getApiService(context)
            val db = AppDatabase.getInstance(context)
            return MainViewModel(
                context,
                NoteRepository(api, db.noteDao()),
                OcrRepository(api)
            ) as T
        }
    }
}
