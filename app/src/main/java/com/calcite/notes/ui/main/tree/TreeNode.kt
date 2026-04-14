package com.calcite.notes.ui.main.tree

import com.calcite.notes.model.Folder
import com.calcite.notes.model.Note

sealed class TreeNode {
    abstract val level: Int

    data class FolderNode(
        val folder: Folder,
        override val level: Int,
        var isExpanded: Boolean = false,
        val hasLoadedChildren: Boolean = false
    ) : TreeNode()

    data class NoteNode(
        val note: Note,
        override val level: Int
    ) : TreeNode()
}
