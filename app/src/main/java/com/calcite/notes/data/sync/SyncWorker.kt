package com.calcite.notes.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.FileRepository
import com.calcite.notes.data.repository.FolderRepository
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.data.repository.TagRepository
import com.calcite.notes.model.NoteDetail
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "sync_worker"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(10, TimeUnit.MINUTES)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    private val appDataStore = AppDataStore(context)
    private val db = AppDatabase.getInstance(context)
    private val apiService = RetrofitClient.getApiService(context)

    override suspend fun doWork(): Result {
        // 1. 检查 token 是否有效
        val token = appDataStore.token.first()
        val expireTime = appDataStore.tokenExpireTime.first()
        if (token.isNullOrEmpty() || System.currentTimeMillis() > expireTime) {
            // Token 无效或过期，清除数据并退出
            appDataStore.clearAll()
            db.noteDao().deleteAll()
            db.tagDao().deleteAll()
            db.folderDao().deleteAll()
            db.fileDao().deleteAll()
            db.noteTagDao().deleteAll()
            return Result.failure()
        }

        // 2. 拉取并同步数据
        val noteRepo = NoteRepository(apiService, db.noteDao())
        val tagRepo = TagRepository(apiService, db.tagDao(), db.noteTagDao())
        val folderRepo = FolderRepository(apiService, db.folderDao())
        val fileRepo = FileRepository(apiService, db.fileDao())

        syncNotes(noteRepo)
        syncTags(tagRepo)
        syncFolders(folderRepo)
        syncFiles(fileRepo)

        return Result.success()
    }

    private suspend fun syncNotes(repo: NoteRepository) {
        // 获取根目录笔记列表
        when (val result = repo.getNoteListFromRemote(0)) {
            is com.calcite.notes.utils.Result.Success -> {
                val notes = result.data
                val allNoteIds = mutableSetOf<Long>()
                // 递归获取所有笔记
                val noteQueue = mutableListOf<Long>()
                val detailList = mutableListOf<NoteDetail>()

                // 先保存列表（无content）
                for (note in notes) {
                    allNoteIds.add(note.id)
                }

                // 获取所有文件夹下的笔记
                val folders = db.folderDao().getAllSync()
                for (folder in folders) {
                    when (val folderNotes = repo.getNoteListFromRemote(folder.id)) {
                        is com.calcite.notes.utils.Result.Success -> {
                            for (note in folderNotes.data) {
                                allNoteIds.add(note.id)
                            }
                        }
                        else -> {}
                    }
                }

                // 获取每个笔记的详情（包含content）
                for (noteId in allNoteIds) {
                    when (val detail = repo.getNoteDetailFromRemote(noteId)) {
                        is com.calcite.notes.utils.Result.Success -> {
                            detailList.add(detail.data)
                        }
                        else -> {}
                    }
                }

                // 写入数据库
                val entities = detailList.map {
                    com.calcite.notes.data.local.entity.NoteEntity(
                        id = it.id,
                        title = it.title,
                        content = it.content,
                        summary = it.summary,
                        folderId = it.folder_id,
                        createdAt = it.created_at ?: "",
                        updatedAt = it.updated_at ?: ""
                    )
                }
                db.noteDao().insertAll(entities)
            }
            else -> {}
        }
    }

    private suspend fun syncTags(repo: TagRepository) {
        when (val result = repo.getAllTagsFromRemote()) {
            is com.calcite.notes.utils.Result.Success -> {
                val tags = result.data.map {
                    com.calcite.notes.data.local.entity.TagEntity(
                        id = it.id,
                        name = it.name,
                        createdAt = it.created_at
                    )
                }
                db.tagDao().insertAll(tags)

                // 同步每个笔记的标签绑定关系
                val notes = db.noteDao().getAllSync()
                for (note in notes) {
                    when (val bindResult = repo.getTagsByNoteFromRemote(note.id)) {
                        is com.calcite.notes.utils.Result.Success -> {
                            db.noteTagDao().deleteByNoteId(note.id)
                            val crossRefs = bindResult.data.map { tag ->
                                com.calcite.notes.data.local.entity.NoteTagCrossRef(
                                    noteId = note.id,
                                    tagId = tag.id
                                )
                            }
                            db.noteTagDao().insertAll(crossRefs)
                        }
                        else -> {}
                    }
                }
            }
            else -> {}
        }
    }

    private suspend fun syncFolders(repo: FolderRepository) {
        val queue = mutableListOf(0L)
        val allFolders = mutableListOf<com.calcite.notes.data.local.entity.FolderEntity>()
        while (queue.isNotEmpty()) {
            val parentId = queue.removeAt(0)
            when (val result = repo.getFolderListFromRemote(parentId)) {
                is com.calcite.notes.utils.Result.Success -> {
                    for (folder in result.data) {
                        allFolders.add(
                            com.calcite.notes.data.local.entity.FolderEntity(
                                id = folder.id,
                                name = folder.name,
                                parentId = folder.parent_id,
                                createdAt = folder.created_at
                            )
                        )
                        queue.add(folder.id)
                    }
                }
                else -> {}
            }
        }
        db.folderDao().insertAll(allFolders)
    }

    private suspend fun syncFiles(repo: FileRepository) {
        when (val result = repo.getFileListFromRemote(null)) {
            is com.calcite.notes.utils.Result.Success -> {
                val files = result.data.map {
                    com.calcite.notes.data.local.entity.FileEntity(
                        id = it.id,
                        userId = it.user_id,
                        noteId = it.note_id,
                        fileName = it.file_name,
                        fileType = it.file_type,
                        fileSize = it.file_size,
                        fileSizeFormatted = it.file_size_formatted,
                        objectKey = it.object_key,
                        url = it.url,
                        status = it.status,
                        createdAt = it.created_at,
                        updatedAt = it.updated_at
                    )
                }
                db.fileDao().insertAll(files)
            }
            else -> {}
        }
    }
}
