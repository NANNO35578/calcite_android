package com.calcite.notes.ui.main

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.local.database.AppDatabase
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.FolderRepository
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.data.repository.UserRepository
import com.calcite.notes.model.Folder
import com.calcite.notes.model.Note
import com.calcite.notes.model.UserProfile
import com.calcite.notes.ui.main.tree.TreeNode
import com.calcite.notes.utils.Result
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NoteListViewModel(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _treeNodes = MutableLiveData<List<TreeNode>>(emptyList())
    val treeNodes: LiveData<List<TreeNode>> = _treeNodes

    private val _userProfile = MutableLiveData<UserProfile?>(null)
    val userProfile: LiveData<UserProfile?> = _userProfile

    private val _operationResult = MutableLiveData<Result<String>>()
    val operationResult: LiveData<Result<String>> = _operationResult

    private val expandedFolders = mutableSetOf<Long>()
    private val folderToChildren = mutableMapOf<Long, Pair<List<Folder>, List<Note>>>()

    init {
        observeLocalData()
        loadUserProfile()
    }

    private fun observeLocalData() {
        viewModelScope.launch {
            combine(
                folderRepository.getFolderListLocal(0),
                noteRepository.getNoteListLocal(0)
            ) { folders, notes ->
                buildTree(folders, notes)
            }.collect {
                _treeNodes.value = it
            }
        }
    }

    private fun buildTree(folders: List<Folder>, notes: List<Note>): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        // 文件夹在前，笔记在后
        folders.sortedBy { it.name }.forEach {
            result.add(TreeNode.FolderNode(it, 0, expandedFolders.contains(it.id)))
        }
        notes.sortedBy { it.title }.forEach {
            result.add(TreeNode.NoteNode(it, 0))
        }
        return result
    }

    private suspend fun buildSubTree(folderId: Long, level: Int): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        val folders = folderRepository.getFolderListLocal(folderId).first()
        val notes = noteRepository.getNoteListLocal(folderId).first()
        folderToChildren[folderId] = folders to notes

        folders.sortedBy { it.name }.forEach {
            val isExpanded = expandedFolders.contains(it.id)
            result.add(TreeNode.FolderNode(it, level, isExpanded))
            if (isExpanded) {
                result.addAll(buildSubTree(it.id, level + 1))
            }
        }
        notes.sortedBy { it.title }.forEach {
            result.add(TreeNode.NoteNode(it, level))
        }
        return result
    }

    fun toggleFolder(folderNode: TreeNode.FolderNode) {
        val currentList = _treeNodes.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst {
            it is TreeNode.FolderNode && it.folder.id == folderNode.folder.id && it.level == folderNode.level
        }
        if (index == -1) return

        if (folderNode.isExpanded) {
            // 折叠
            val removeCount = currentList.drop(index + 1).takeWhile { it.level > folderNode.level }.size
            repeat(removeCount) { currentList.removeAt(index + 1) }
            expandedFolders.remove(folderNode.folder.id)
            currentList[index] = folderNode.copy(isExpanded = false)
            _treeNodes.value = currentList
        } else {
            // 展开
            expandedFolders.add(folderNode.folder.id)
            viewModelScope.launch {
                val children = buildSubTree(folderNode.folder.id, folderNode.level + 1)
                currentList.addAll(index + 1, children)
                currentList[index] = folderNode.copy(isExpanded = true, hasLoadedChildren = true)
                _treeNodes.value = currentList
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

    fun getAllFolders(): List<Folder> {
        val result = mutableListOf<Folder>()
        fun collect(folderId: Long) {
            val pair = folderToChildren[folderId] ?: return
            pair.first.forEach {
                result.add(it)
                collect(it.id)
            }
        }
        // 根目录
        val rootPair = folderToChildren[0L]
        rootPair?.first?.forEach {
            result.add(it)
            collect(it.id)
        }
        return result
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val api = RetrofitClient.getApiService(context)
            val db = AppDatabase.getInstance(context)
            return NoteListViewModel(
                context,
                NoteRepository(api, db.noteDao()),
                FolderRepository(api, db.folderDao()),
                UserRepository(api, com.calcite.notes.data.local.AppDataStore(context), db)
            ) as T
        }
    }
}
