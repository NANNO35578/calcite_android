package com.calcite.notes.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.calcite.notes.databinding.FragmentNoteEditorBinding

class NoteEditorFragment : Fragment() {

    private var _binding: FragmentNoteEditorBinding? = null
    private val binding get() = _binding!!

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
        binding.tvTitle.text = "笔记编辑器（占位）"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
