package com.calcite.notes.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.calcite.notes.MainActivity
import com.calcite.notes.R
import com.calcite.notes.databinding.FragmentToolPanelBinding
import com.calcite.notes.databinding.ItemFileBinding
import com.calcite.notes.model.FileItem
import com.calcite.notes.utils.Result
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class ToolPanelFragment : Fragment() {

    private var _binding: FragmentToolPanelBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ToolPanelViewModel by viewModels {
        ToolPanelViewModel.Factory(requireContext())
    }

    private var currentNoteId: Long = 0L

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadFile(it) }
    }

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

        setupTabs()
        setupStatusFilter()

        binding.btnUploadFile.setOnClickListener { filePickerLauncher.launch("*/*") }
        binding.btnRefreshTags.setOnClickListener {
            if (currentNoteId > 0) viewModel.refreshTags(currentNoteId)
        }
        binding.btnDeleteNote.setOnClickListener { showDeleteNoteConfirm() }
        binding.btnLike.setOnClickListener { viewModel.toggleLike() }
        binding.btnCollect.setOnClickListener { viewModel.toggleCollect() }

        binding.etNoteSummary.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && currentNoteId > 0) {
                val summary = binding.etNoteSummary.text.toString().trim()
                val currentSummary = viewModel.noteDetail.value?.summary ?: ""
                if (summary != currentSummary) {
                    viewModel.updateSummary(currentNoteId, summary)
                }
            }
        }

        binding.switchIsPublic.setOnCheckedChangeListener { _, isChecked ->
            if (currentNoteId > 0) {
                viewModel.togglePublic(currentNoteId, isChecked)
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadAll()
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.noteDetail.observe(viewLifecycleOwner) { detail ->
            renderNoteInfo(detail)
        }
        viewModel.isOwnNote.observe(viewLifecycleOwner) {
            renderNoteInfo(viewModel.noteDetail.value)
        }
        viewModel.currentNoteTags.observe(viewLifecycleOwner) {
            renderTags(it)
        }
        viewModel.files.observe(viewLifecycleOwner) {
            renderFiles(it)
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
        lifecycleScope.launch {
            val noteId = (requireActivity() as? MainActivity)?.getCurrentNoteIdAsync() ?: 0L
            currentNoteId = noteId
            viewModel.setNoteId(noteId)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showNoteInfoPanel()
                    1 -> showFilePanel()
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun showNoteInfoPanel() {
        binding.layoutNoteInfo.visibility = View.VISIBLE
        binding.layoutFilesContainer.visibility = View.GONE
    }

    private fun showFilePanel() {
        binding.layoutNoteInfo.visibility = View.GONE
        binding.layoutFilesContainer.visibility = View.VISIBLE
    }

    private fun renderNoteInfo(detail: com.calcite.notes.model.NoteDetail?) {
        val hasNote = currentNoteId > 0 && detail != null

        if (!hasNote) {
            binding.tvNoNoteHint.visibility = View.VISIBLE
            binding.layoutNoteInfoContent.visibility = View.GONE
            return
        }

        binding.tvNoNoteHint.visibility = View.GONE
        binding.layoutNoteInfoContent.visibility = View.VISIBLE

        binding.tvNoteTitle.text = detail.title
        binding.etNoteSummary.setText(detail.summary ?: "")
        binding.tvCreatedAt.text = detail.created_at ?: ""
        binding.tvUpdatedAt.text = detail.updated_at ?: ""

        val isOwn = viewModel.isOwnNote.value == true

        if (isOwn) {
            binding.etNoteSummary.isEnabled = true
            binding.layoutPublicSwitch.visibility = View.VISIBLE
            binding.switchIsPublic.isChecked = detail.is_public == 1
            binding.labelAuthorId.visibility = View.GONE
            binding.tvAuthorId.visibility = View.GONE
            binding.btnDeleteNote.visibility = View.VISIBLE
        } else {
            binding.etNoteSummary.isEnabled = false
            binding.layoutPublicSwitch.visibility = View.GONE
            binding.labelAuthorId.visibility = View.VISIBLE
            binding.tvAuthorId.visibility = View.VISIBLE
            binding.tvAuthorId.text = detail.author_id.toString()
            binding.btnDeleteNote.visibility = View.GONE
        }

        // 点赞/收藏按钮状态
        binding.btnLike.text = if (detail.has_liked) "已点赞 (${detail.like_count})" else "点赞 (${detail.like_count})"
        binding.btnCollect.text = if (detail.has_collected) "已收藏 (${detail.collect_count})" else "收藏 (${detail.collect_count})"
    }

    private fun renderTags(tags: List<com.calcite.notes.model.Tag>) {
        binding.chipGroupNoteTags.removeAllViews()
        if (tags.isEmpty()) {
            val chip = Chip(requireContext()).apply {
                text = "暂无标签"
                isClickable = false
            }
            binding.chipGroupNoteTags.addView(chip)
            return
        }
        for (tag in tags) {
            val chip = Chip(requireContext()).apply {
                text = tag.name
                isClickable = false
            }
            binding.chipGroupNoteTags.addView(chip)
        }
    }

    private fun setupStatusFilter() {
        val options = listOf("全部", "完成", "处理中", "失败")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStatusFilter.adapter = adapter
        binding.spinnerStatusFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val status = when (position) {
                    1 -> "done"
                    2 -> "processing"
                    3 -> "failed"
                    else -> null
                }
                viewModel.setFileStatusFilter(status)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
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
            val itemBinding = ItemFileBinding.inflate(
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
            itemBinding.root.setOnLongClickListener {
                showFileMenu(file)
                true
            }
            binding.layoutFiles.addView(itemBinding.root)
        }
    }

    private fun showFileMenu(file: FileItem) {
        val options = arrayOf("复制链接", "删除")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(file.file_name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> copyToClipboard(file.url)
                    1 -> showDeleteFileConfirm(file)
                }
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) {
            Toast.makeText(requireContext(), "链接为空", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("链接", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "链接已复制", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteFileConfirm(file: FileItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除文件")
            .setMessage("确定删除文件 \"${file.file_name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteFile(file.id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteNoteConfirm() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除笔记")
            .setMessage("确定删除这篇笔记吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                if (currentNoteId > 0) {
                    viewModel.deleteNote(currentNoteId)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun uploadFile(uri: Uri) {
        val part = uriToMultipartPart(uri) ?: return
        viewModel.uploadFile(part, currentNoteId.takeIf { it != 0L })
    }

    private fun uriToMultipartPart(uri: Uri): MultipartBody.Part? {
        val context = requireContext()
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = getFileNameFromUri(uri) ?: "unknown"
        val tempFile = File(context.cacheDir, fileName)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }
        val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", fileName, requestFile)
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
