package com.calcite.notes.ui.main

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.calcite.notes.MainActivity
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.databinding.FragmentNoteEditorBinding
import com.calcite.notes.utils.Result
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.coil.CoilImagesPlugin

class NoteEditorFragment : Fragment() {

    private var _binding: FragmentNoteEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NoteEditorViewModel by viewModels {
        NoteEditorViewModel.Factory(requireContext(), arguments?.getLong("noteId") ?: 0L)
    }

    private lateinit var markwon: Markwon
    private var isUserEditing = false
    private var contentTextWatcher: TextWatcher? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        markwon = Markwon.create(requireContext())
        markwon = Markwon.builder(requireContext())
            .usePlugin(CoilImagesPlugin.create(requireContext()))
            .usePlugin(TablePlugin.create(requireContext()))
            .build()
        (activity as? MainActivity)?.setCurrentNoteId(viewModel.noteId)

        binding.btnPreview.setOnClickListener {
            val next = viewModel.isPreview.value != true
            viewModel.setPreview(next)
        }

        binding.tvTitle.setOnClickListener {
            showEditTitleDialog()
        }

        contentTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUserEditing) {
                    viewModel.updateContent(binding.tvTitle.text.toString(), s?.toString() ?: "")
                }
            }
        }
        binding.etContent.addTextChangedListener(contentTextWatcher!!)

        viewModel.noteDetail.observe(viewLifecycleOwner) { detail ->
            detail?.let {
                binding.tvTitle.text = it.title
                if (!isUserEditing) {
                    binding.etContent.removeTextChangedListener(contentTextWatcher!!)
                    binding.etContent.setText(it.content)
                    binding.etContent.addTextChangedListener(contentTextWatcher!!)
                }
                if (viewModel.isPreview.value == true) {
                    markwon.setMarkdown(binding.tvPreview, it.content)
                }
                (activity as? MainActivity)?.setCurrentNoteId(it.id)
            }
        }

        viewModel.isPreview.observe(viewLifecycleOwner) { preview ->
            binding.btnPreview.text = if (preview) "编辑" else "预览"
            if (preview) {
                binding.etContent.visibility = View.GONE
                binding.previewScroll.visibility = View.VISIBLE
                val content = binding.etContent.text.toString()
                markwon.setMarkdown(binding.tvPreview, content)
            } else {
                binding.etContent.visibility = View.VISIBLE
                binding.previewScroll.visibility = View.GONE
            }
        }

        viewModel.hasUnsavedChanges.observe(viewLifecycleOwner) { unsaved ->
            binding.tvUnsaved.visibility = if (unsaved) View.VISIBLE else View.GONE
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Success -> Toast.makeText(requireContext(), "已自动保存", Toast.LENGTH_SHORT).show()
                is Result.Error -> Toast.makeText(requireContext(), "保存失败: ${result.message}", Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }

        viewModel.loadResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Error -> Toast.makeText(requireContext(), "加载失败: ${result.message}", Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    private fun showEditTitleDialog() {
        val input = EditText(requireContext()).apply {
            setText(binding.tvTitle.text)
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("修改标题")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    binding.tvTitle.text = newTitle
                    viewModel.updateContent(newTitle, binding.etContent.text.toString())
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.hasUnsavedChanges.value == true) {
            viewModel.saveNote()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun createArgs(noteId: Long): Bundle {
            return bundleOf("noteId" to noteId)
        }
    }
}
