package com.calcite.notes.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.calcite.notes.databinding.FragmentNotePreviewBinding
import com.google.android.material.chip.Chip
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.coil.CoilImagesPlugin

class NotePreviewFragment : Fragment() {

    private var _binding: FragmentNotePreviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotePreviewViewModel by viewModels {
        NotePreviewViewModel.Factory(requireContext(), arguments?.getLong("noteId") ?: 0L)
    }

    private lateinit var markwon: Markwon

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        markwon = Markwon.builder(requireContext())
            .usePlugin(CoilImagesPlugin.create(requireContext()))
            .usePlugin(TablePlugin.create(requireContext()))
            .build()

        binding.btnLike.setOnClickListener { viewModel.toggleLike() }
        binding.btnCollect.setOnClickListener { viewModel.toggleCollect() }

        viewModel.noteDetail.observe(viewLifecycleOwner) { detail ->
            detail?.let { renderNote(it) }
        }

        viewModel.tags.observe(viewLifecycleOwner) { tags ->
            renderTags(tags)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderNote(detail: com.calcite.notes.model.NoteDetail) {
        binding.tvTitle.text = detail.title
        binding.tvAuthorInfo.text = "作者ID: ${detail.author_id}"
        binding.tvTimeInfo.text = "创建: ${detail.created_at ?: ""}  更新: ${detail.updated_at ?: ""}"
        binding.tvSummary.text = detail.summary ?: ""
        binding.tvSummary.visibility = if (detail.summary.isNullOrBlank()) View.GONE else View.VISIBLE

        markwon.setMarkdown(binding.tvPreview, detail.content)

        binding.btnLike.text = if (detail.has_liked) "已点赞 (${detail.like_count})" else "点赞 (${detail.like_count})"
        binding.btnCollect.text = if (detail.has_collected) "已收藏 (${detail.collect_count})" else "收藏 (${detail.collect_count})"
    }

    private fun renderTags(tags: List<com.calcite.notes.model.Tag>) {
        binding.chipGroupTags.removeAllViews()
        if (tags.isEmpty()) {
            val chip = Chip(requireContext()).apply {
                text = "暂无标签"
                isClickable = false
            }
            binding.chipGroupTags.addView(chip)
            return
        }
        for (tag in tags) {
            val chip = Chip(requireContext()).apply {
                text = tag.name
                isClickable = false
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
