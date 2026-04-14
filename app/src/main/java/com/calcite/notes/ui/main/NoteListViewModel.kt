package com.calcite.notes.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.calcite.notes.data.repository.FolderRepository
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.data.repository.UserRepository
import com.calcite.notes.model.Folder
import com.calcite.notes.model.Note
import com.calcite.notes.model.NoteCreateData
import com.calcite.notes.model.UserProfile
import com.calcite.notes.ui.main.tree.TreeNode
import com.calcite.notes.utils.Result
import kotlinx.coroutines.launch

class NoteListViewModel(
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

    // 缓存已加载的子节点
    private val childrenCache = mutableMapOf<Long, List<TreeNode>>()
    private val expandedFolders = mutableSetOf<Long>()

    init {
        loadRoot()
        loadUserProfile()
    }

    fun loadRoot() {
        viewModelScope.launch {
            childrenCache.clear()
            expandedFolders.clear()
            val foldersResult = folderRepository.getFolderList(0)
            val notesResult = noteRepository.getNoteList(0)
            val nodes = mutableListOf<TreeNode>()
            if (foldersResult is Result.Success) {
                nodes.addAll(foldersResult.data.map { TreeNode.FolderNode(it, 0) })
            }
            if (notesResult is Result.Success) {
                nodes.addAll(notesResult.data.map { TreeNode.NoteNode(it, 0) })
            }
            _treeNodes.value = nodes.sortedWith(compareBy {
                when (it) {
                    is TreeNode.FolderNode -> it.folder.name
                    is TreeNode.NoteNode -> it.note.title
                }
            })
        }
    }

    fun toggleFolder(folderNode: TreeNode.FolderNode) {
        val currentList = _treeNodes.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst {
            it is TreeNode.FolderNode && it.folder.id == folderNode.folder.id && it.level == folderNode.level
        }
        if (index == -1) return

        if (folderNode.isExpanded) {
            // 折叠：移除该节点后所有 level 更大的节点
            val removeCount = currentList.drop(index + 1).takeWhile { it.level > folderNode.level }.size
            repeat(removeCount) { currentList.removeAt(index + 1) }
            expandedFolders.remove(folderNode.folder.id)
            currentList[index] = folderNode.copy(isExpanded = false)
            _treeNodes.value = currentList
        } else {
            // 展开
            expandedFolders.add(folderNode.folder.id)
            val cached = childrenCache[folderNode.folder.id]
            if (cached != null) {
                currentList.addAll(index + 1, cached)
                currentList[index] = folderNode.copy(isExpanded = true)
                _treeNodes.value = currentList
            } else {
                viewModelScope.launch {
                    val foldersRes = folderRepository.getFolderList(folderNode.folder.id)
                    val notesRes = noteRepository.getNoteList(folderNode.folder.id)
                    val childNodes = mutableListOf<TreeNode>()
                    if (foldersRes is Result.Success) {
                        childNodes.addAll(foldersRes.data.map {
                            TreeNode.FolderNode(it, folderNode.level + 1)
                        })
                    }
                    if (notesRes is Result.Success) {
                        childNodes.addAll(notesRes.data.map {
                            TreeNode.NoteNode(it, folderNode.level + 1)
                        })
                    }
                    val sorted = childNodes.sortedWith(compareBy {
                        when (it) {
                            is TreeNode.FolderNode -> it.folder.name
                            is TreeNode.NoteNode -> it.note.title
                        }
                    })
                    childrenCache[folderNode.folder.id] = sorted
                    val updatedList = _treeNodes.value?.toMutableList() ?: return@launch
                    val currentIndex = updatedList.indexOfFirst {
                        it is TreeNode.FolderNode && it.folder.id == folderNode.folder.id && it.level == folderNode.level
                    }
                    if (currentIndex != -1) {
                        updatedList.addAll(currentIndex + 1, sorted)
                        updatedList[currentIndex] = folderNode.copy(isExpanded = true, hasLoadedChildren = true)
                        _treeNodes.value = updatedList
                    }
                }
            }
        }
    }

    fun createNote(title: String, folderId: Long = 0) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = noteRepository.createNote(title, "", folderId)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("创建成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
            refreshAffectedFolder(folderId)
        }
    }

    fun createFolder(name: String, parentId: Long = 0) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = folderRepository.createFolder(name, parentId)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("创建成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
            refreshAffectedFolder(parentId)
        }
    }

    fun deleteFolder(folderId: Long, parentId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = folderRepository.deleteFolder(folderId)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("删除成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
            refreshAffectedFolder(parentId)
        }
    }

    fun deleteNote(noteId: Long, folderId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = noteRepository.deleteNote(noteId)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("删除成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
            refreshAffectedFolder(folderId)
        }
    }

    fun renameFolder(folderId: Long, newName: String, parentId: Long) {
        viewModelScope.launch {
            _operationResult.value = Result.Loading
            val result = folderRepository.updateFolder(folderId, newName)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success("重命名成功")
                is Result.Error -> result
                else -> Result.Error("未知错误")
            }
            refreshAffectedFolder(parentId)
        }
    }

    private fun refreshAffectedFolder(folderId: Long) {
        viewModelScope.launch {
            if (folderId == 0L) {
                loadRoot()
            } else {
                childrenCache.remove(folderId)
                val currentList = _treeNodes.value ?: return@launch
                val node = currentList.find {
                    it is TreeNode.FolderNode && it.folder.id == folderId
                } as? TreeNode.FolderNode ?: return@launch
                if (node.isExpanded) {
                    toggleFolder(node) // 折叠
                    toggleFolder(node) // 重新展开并加载
                }
            }
        }
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            when (val result = userRepository.getUserProfile()) {
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
        val folders = mutableListOf<Folder>()
        fun collect(nodes: List<TreeNode>) {
            for (node in nodes) {
                if (node is TreeNode.FolderNode) {
                    folders.add(node.folder)
                    val children = childrenCache[node.folder.id]
                    if (children != null) collect(children)
                }
            }
        }
        _treeNodes.value?.let { collect(it) }
        return folders
    }

    class Factory(
        private val noteRepository: NoteRepository,
        private val folderRepository: FolderRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return NoteListViewModel(noteRepository, folderRepository, userRepository) as T
        }
    }
}
