package com.calcite.notes.ui.main

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.calcite.notes.MainActivity
import com.calcite.notes.R
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.FileRepository
import com.calcite.notes.data.repository.TagRepository
import com.calcite.notes.databinding.FragmentToolPanelBinding
import com.calcite.notes.databinding.ItemTagChipBinding
import com.calcite.notes.model.FileItem
import com.calcite.notes.model.Tag
import com.calcite.notes.utils.Result
import com.google.android.material.chip.Chip

class ToolPanelFragment : Fragment() {

    private var _binding: FragmentToolPanelBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ToolPanelViewModel by viewModels {
        val api = RetrofitClient.getApiService(requireContext())
        ToolPanelViewModel.Factory(TagRepository(api), FileRepository(api))
    }

    private var currentNoteId: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddTag.setOnClickListener {
            showCreateTagDialog()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadAll()
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.tags.observe(viewLifecycleOwner) { tags ->
            renderTags(tags, viewModel.boundTagIds.value ?: emptySet())
        }

        viewModel.boundTagIds.observe(viewLifecycleOwner) { boundIds ->
            renderTags(viewModel.tags.value ?: emptyList(), boundIds)
        }

        viewModel.files.observe(viewLifecycleOwner) { files ->
            renderFiles(files)
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> {}
                is Result.Success -> Toast.makeText(requireContext(), result.data, Toast.LENGTH_SHORT).show()
                is Result.Error -> Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loadAll()
    }

    override fun onResume() {
        super.onResume()
        val noteId = (requireActivity() as? MainActivity)?.getCurrentNoteId() ?: 0L
        currentNoteId = noteId
        viewModel.setNoteId(noteId)
    }

    private fun renderTags(tags: List<Tag>, boundIds: Set<Long>) {
        binding.chipGroupTags.removeAllViews()
        for (tag in tags) {
            val chip = Chip(requireContext()).apply {
                text = tag.name
                isCheckable = true
                isChecked = boundIds.contains(tag.id)
                setOnCheckedChangeListener { _, _ ->
                    viewModel.toggleTag(tag.id)
                }
                setOnLongClickListener {
                    showTagMenu(tag)
                    true
                }
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    private fun renderFiles(files: List<FileItem>) {
        binding.layoutFiles.removeAllViews()
        if (files.isEmpty()) {
            val tv = android.widget.TextView(requireContext()).apply {
                text = "暂无文件"
                textSize = 14f
                setPadding(0, 8, 0, 8)
            }
            binding.layoutFiles.addView(tv)
            return
        }
        for (file in files) {
            val itemBinding = com.calcite.notes.databinding.ItemFileBinding.inflate(
                LayoutInflater.from(requireContext()), binding.layoutFiles, false
            )
            itemBinding.tvFileName.text = file.file_name
            val statusText = when (file.status) {
                "done" -> "完成"
                "processing" -> "处理中"
                "failed" -> "失败"
                else -> file.status
            }
            val color = when (file.status) {
                "done" -> android.R.color.holo_green_dark
                "processing" -> android.R.color.holo_orange_dark
                "failed" -> android.R.color.holo_red_dark
                else -> android.R.color.darker_gray
            }
            itemBinding.tvStatus.text = statusText
            itemBinding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), color))
            binding.layoutFiles.addView(itemBinding.root)
        }
    }

    private fun showTagMenu(tag: Tag) {
        val options = arrayOf("重命名", "删除")
        AlertDialog.Builder(requireContext())
            .setTitle(tag.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameTagDialog(tag)
                    1 -> showDeleteTagConfirm(tag)
                }
            }
            .show()
    }

    private fun showCreateTagDialog() {
        val input = EditText(requireContext()).apply { hint = "标签名称" }
        AlertDialog.Builder(requireContext())
            .setTitle("新建标签")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createTag(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenameTagDialog(tag: Tag) {
        val input = EditText(requireContext()).apply {
            setText(tag.name)
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("重命名标签")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.renameTag(tag.id, name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteTagConfirm(tag: Tag) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除标签")
            .setMessage("确定删除标签 \"${tag.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteTag(tag.id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
