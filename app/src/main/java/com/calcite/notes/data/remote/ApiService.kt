package com.calcite.notes.data.remote

import com.calcite.notes.model.ApiResponse
import com.calcite.notes.model.BindTagRequest
import com.calcite.notes.model.CreateFolderRequest
import com.calcite.notes.model.CreateNoteRequest
import com.calcite.notes.model.CreateTagRequest
import com.calcite.notes.model.DeleteFileRequest
import com.calcite.notes.model.DeleteFolderRequest
import com.calcite.notes.model.DeleteNoteRequest
import com.calcite.notes.model.DeleteTagRequest
import com.calcite.notes.model.FileItem
import com.calcite.notes.model.FileStatusData
import com.calcite.notes.model.FileUploadData
import com.calcite.notes.model.Folder
import com.calcite.notes.model.FolderCreateData
import com.calcite.notes.model.LoginData
import com.calcite.notes.model.LoginRequest
import com.calcite.notes.model.Note
import com.calcite.notes.model.NoteCreateData
import com.calcite.notes.model.NoteDetail
import com.calcite.notes.model.OcrRecognizeData
import com.calcite.notes.model.OcrStatusData
import com.calcite.notes.model.RegisterData
import com.calcite.notes.model.RegisterRequest
import com.calcite.notes.model.SearchResultItem
import com.calcite.notes.model.Tag
import com.calcite.notes.model.TagCreateData
import com.calcite.notes.model.UpdateFolderRequest
import com.calcite.notes.model.UpdateNoteRequest
import com.calcite.notes.model.UpdateTagRequest
import com.calcite.notes.model.UserProfile
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {

    // Auth
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginData>

    @POST("/api/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<RegisterData>

    // User
    @GET("/api/user/profile")
    suspend fun getUserProfile(): ApiResponse<UserProfile>

    // Note
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

    @GET("/api/note/search")
    suspend fun searchNotes(
        @Query("keyword") keyword: String,
        @Query("from") from: Int = 0,
        @Query("size") size: Int = 20
    ): ApiResponse<List<SearchResultItem>>

    // Folder
    @POST("/api/folder/create")
    suspend fun createFolder(@Body request: CreateFolderRequest): ApiResponse<FolderCreateData>

    @POST("/api/folder/update")
    suspend fun updateFolder(@Body request: UpdateFolderRequest): ApiResponse<Unit>

    @POST("/api/folder/delete")
    suspend fun deleteFolder(@Body request: DeleteFolderRequest): ApiResponse<Unit>

    @GET("/api/folder/list")
    suspend fun getFolderList(@Query("folder_id") folderId: Long): ApiResponse<List<Folder>>

    // Tag
    @POST("/api/tag/create")
    suspend fun createTag(@Body request: CreateTagRequest): ApiResponse<TagCreateData>

    @POST("/api/tag/update")
    suspend fun updateTag(@Body request: UpdateTagRequest): ApiResponse<Unit>

    @POST("/api/tag/delete")
    suspend fun deleteTag(@Body request: DeleteTagRequest): ApiResponse<Unit>

    @GET("/api/tag/list")
    suspend fun getTagList(@Query("note_id") noteId: Long? = null): ApiResponse<List<Tag>>

    @POST("/api/tag/bind")
    suspend fun bindTags(@Body request: BindTagRequest): ApiResponse<Unit>

    // File
    @Multipart
    @POST("/api/file/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("note_id") noteId: RequestBody? = null
    ): ApiResponse<FileUploadData>

    @POST("/api/file/delete")
    suspend fun deleteFile(@Body request: DeleteFileRequest): ApiResponse<Unit>

    @GET("/api/file/list")
    suspend fun getFileList(
        @Query("user_id") userId: Long? = null,
        @Query("note_id") noteId: Long? = null,
        @Query("status") status: String? = null
    ): ApiResponse<List<FileItem>>

    @GET("/api/file/status")
    suspend fun getFileStatus(@Query("file_id") fileId: String): ApiResponse<FileStatusData>

    @GET("/api/file/info")
    suspend fun getFileInfo(@Query("file_id") fileId: String): ApiResponse<FileItem>

    // OCR
    @Multipart
    @POST("/api/ocr/recognize")
    suspend fun ocrRecognize(@Part file: MultipartBody.Part): ApiResponse<OcrRecognizeData>

    @GET("/api/ocr/status")
    suspend fun getOcrStatus(@Query("file_id") fileId: String): ApiResponse<OcrStatusData>
}
