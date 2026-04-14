package com.calcite.notes.data.repository

import com.calcite.notes.data.remote.ApiService
import com.calcite.notes.model.OcrRecognizeData
import com.calcite.notes.model.OcrStatusData
import com.calcite.notes.utils.Result
import okhttp3.MultipartBody

class OcrRepository(private val apiService: ApiService) {

    suspend fun recognize(filePart: MultipartBody.Part): Result<OcrRecognizeData> {
        return try {
            val response = apiService.ocrRecognize(filePart)
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    suspend fun getStatus(fileId: Long): Result<OcrStatusData> {
        return try {
            val response = apiService.getOcrStatus(fileId.toString())
            if (response.code == 0 && response.data != null) {
                Result.Success(response.data)
            } else {
                Result.Error(response.message)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }
}
