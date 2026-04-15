package com.calcite.notes.ui.main

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.FileRepository
import com.calcite.notes.data.repository.FolderRepository
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.data.repository.TagRepository
import com.calcite.notes.data.repository.UserRepository
import com.calcite.notes.model.Folder
import com.calcite.notes.model.Note
import com.calcite.notes.model.UserProfile
import com.calcite.notes.ui.main.tree.TreeNode
import com.calcite.notes.utils.NetworkUtils
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NoteListViewModel(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val tagRepository: TagRepository,
    private val fileRepository: FileRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _treeNodes = MutableLiveData<List<TreeNode>>(emptyList())
    val treeNodes: LiveData<List<TreeNode>> = _treeNodes

    private val _userProfile = MutableLiveData<UserProfile?>(null)
    val userProfile: LiveData<UserProfile?> = _userProfile

    private val _operationResult = MutableLiveData<Result<String>>()
    val operationResult: LiveData<Result<String>> = _operationResult

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val expandedFolders = mutableSetOf<Long>()
    private val refreshTrigger = MutableStateFlow(0)
    private var allFoldersCache = listOf<Folder>()
    private var allNotesCache = listOf<Note>()

    init {
        observeLocalData()
        loadUserProfile()
        viewModelScope.launch {
            if (NetworkUtils.isNetworkAvailable(context)) {
                refresh()
            }
        }
    }

    private fun observeLocalData() {
        viewModelScope.launch {
            combine(
                folderRepository.getAllFoldersLocal(),
                noteRepository.getAllNotesLocal(),
                refreshTrigger
            ) { folders, notes, _ ->
                allFoldersCache = folders
                allNotesCache = notes
                buildFullTree(folders, notes)
            }.collect {
                _treeNodes.value = it
            }
        }
    }

    private fun buildFullTree(folders: List<Folder>, notes: List<Note>): List<TreeNode> {
        val folderMap = folders.groupBy { it.parent_id ?: 0L }
        val noteMap = notes.groupBy { it.folder_id ?: 0L }

        fun buildForParent(parentId: Long, level: Int): List<TreeNode> {
            val result = mutableListOf<TreeNode>()
            // 先构建文件夹树
            val childFolders = folderMap[parentId]?.sortedBy { it.name } ?: emptyList()
            for (folder in childFolders) {
                val isExpanded = expandedFolders.contains(folder.id)
                result.add(TreeNode.FolderNode(folder, level, isExpanded))
                if (isExpanded) {
                    result.addAll(buildForParent(folder.id, level + 1))
                }
            }
            // 再挂载笔记
            val childNotes = noteMap[parentId]?.sortedBy { it.title } ?: emptyList()
            for (note in childNotes) {
                result.add(TreeNode.NoteNode(note, level))
            }
            return result
        }

        return buildForParent(0L, 0)
    }

    fun toggleFolder(folderNode: TreeNode.FolderNode) {
        if (folderNode.isExpanded) {
            expandedFolders.remove(folderNode.folder.id)
        } else {
            expandedFolders.add(folderNode.folder.id)
        }
        refreshTrigger.value += 1
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val folderResult = folderRepository.syncAllFolders(context)
            val tagResult = tagRepository.syncAllTags(context)
            val fileResult = fileRepository.syncAllFiles(context)
            _isRefreshing.value = false
            val allSuccess = folderResult is Result.Success
                    && tagResult is Result.Success && fileResult is Result.Success
            if (allSuccess) {
                _operationResult.value = Result.Success("已同步")
            } else {
                val msg = listOfNotNull(
                    (folderResult as? Result.Error)?.message,
                    (tagResult as? Result.Error)?.message,
                    (fileResult as? Result.Error)?.message
                ).firstOrNull() ?: "同步失败"
                _operationResult.value = Result.Error(msg)
            }
        }
    }

    fun createNote(title: String, folderId: Long = 0) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = noteRepository.createNote(context, title, "", folderId)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("创建成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun createFolder(name: String, parentId: Long = 0) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = folderRepository.createFolder(context, name, parentId)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("创建成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = folderRepository.deleteFolder(context, folderId)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("删除成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = noteRepository.deleteNote(context, noteId)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("删除成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = folderRepository.updateFolder(context, folderId, newName)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("重命名成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    fun renameNote(noteId: Long, newTitle: String) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = noteRepository.updateNote(context, noteId, title = newTitle)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("重命名成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
        }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            when (val result = userRepository.getUserProfile(context)) {
                is Result.Success -> _userProfile.value = result.data
                else -> _userProfile.value = null
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.logout()
        }
    }

    fun getAllFolders(): List<Folder> = allFoldersCache

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val api = RetrofitClient.getApiService(context)
            val db = AppDatabase.getInstance(context)
            return NoteListViewModel(
                context,
                NoteRepository(api, db.noteDao()),
                FolderRepository(api, db.folderDao(), db.noteDao()),
                TagRepository(api, db.tagDao(), db.noteTagDao()),
                FileRepository(api, db.fileDao()),
                UserRepository(api, com.calcite.notes.data.local.AppDataStore(context), db)
            ) as T
        }
    }
}
