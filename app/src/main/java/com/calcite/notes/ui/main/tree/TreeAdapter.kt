package com.calcite.notes.ui.main.tree

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.calcite.notes.databinding.ItemTreeFolderBinding
import com.calcite.notes.databinding.ItemTreeNoteBinding

class TreeAdapter(
    private val onFolderClick: (TreeNode.FolderNode, Int) -> Unit,
    private val onNoteClick: (TreeNode.NoteNode) -> Unit,
    private val onFolderLongClick: (TreeNode.FolderNode, View) -> Boolean,
    private val onNoteLongClick: (TreeNode.NoteNode, View) -> Boolean
) : ListAdapter<TreeNode, RecyclerView.ViewHolder>(TreeDiffCallback()) {

    companion object {
        private const val TYPE_FOLDER = 1
        private const val TYPE_NOTE = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TreeNode.FolderNode -> TYPE_FOLDER
            is TreeNode.NoteNode -> TYPE_NOTE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_FOLDER -> FolderViewHolder(
                ItemTreeFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> NoteViewHolder(
                ItemTreeNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val node = getItem(position)) {
            is TreeNode.FolderNode -> (holder as FolderViewHolder).bind(node)
            is TreeNode.NoteNode -> (holder as NoteViewHolder).bind(node)
        }
    }

    inner class FolderViewHolder(private val binding: ItemTreeFolderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(node: TreeNode.FolderNode) {
            val paddingStart = 16 + node.level * 32
            binding.root.setPaddingRelative(paddingStart, 0, 0, 0)
            binding.tvName.text = node.folder.name
            binding.ivArrow.rotation = if (node.isExpanded) 90f else 0f
            binding.root.setOnClickListener { onFolderClick(node, bindingAdapterPosition) }
            binding.root.setOnLongClickListener { onFolderLongClick(node, it) }
        }
    }

    inner class NoteViewHolder(private val binding: ItemTreeNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(node: TreeNode.NoteNode) {
            val paddingStart = 16 + node.level * 32
            binding.root.setPaddingRelative(paddingStart, 0, 0, 0)
            binding.tvName.text = node.note.title
            binding.root.setOnClickListener { onNoteClick(node) }
            binding.root.setOnLongClickListener { onNoteLongClick(node, it) }
        }
    }
}

class TreeDiffCallback : DiffUtil.ItemCallback<TreeNode>() {
    override fun areItemsTheSame(oldItem: TreeNode, newItem: TreeNode): Boolean {
        return when {
            oldItem is TreeNode.FolderNode && newItem is TreeNode.FolderNode ->
                oldItem.folder.id == newItem.folder.id && oldItem.level == newItem.level
            oldItem is TreeNode.NoteNode && newItem is TreeNode.NoteNode ->
                oldItem.note.id == newItem.note.id && oldItem.level == newItem.level
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: TreeNode, newItem: TreeNode): Boolean {
        return oldItem == newItem
    }
}
