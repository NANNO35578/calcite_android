package com.calcite.notes.ui.main

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.calcite.notes.R
import com.calcite.notes.data.local.TokenDataStore
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.FolderRepository
import com.calcite.notes.data.repository.NoteRepository
import com.calcite.notes.data.repository.UserRepository
import com.calcite.notes.databinding.FragmentNoteListBinding
import com.calcite.notes.ui.main.tree.TreeAdapter
import com.calcite.notes.ui.main.tree.TreeNode
import com.calcite.notes.utils.Result

class NoteListFragment : Fragment() {

    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NoteListViewModel by viewModels {
        val api = RetrofitClient.getApiService(requireContext())
        NoteListViewModel.Factory(
            NoteRepository(api),
            FolderRepository(api),
            UserRepository(api, TokenDataStore(requireContext()))
        )
    }

    private lateinit var treeAdapter: TreeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        treeAdapter = TreeAdapter(
            onFolderClick = { node, _ -> viewModel.toggleFolder(node) },
            onNoteClick = { node ->
                openNoteEditor(node.note.id)
            },
            onFolderLongClick = { node, anchor ->
                showFolderPopup(node)
                true
            }
        )

        binding.recyclerTree.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTree.adapter = treeAdapter

        binding.btnNewNote.setOnClickListener { showCreateNoteDialog() }
        binding.btnNewFolder.setOnClickListener { showCreateFolderDialog() }
        binding.btnOcr.setOnClickListener {
            Toast.makeText(requireContext(), "OCR 功能暂未实现", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            findNavController().navigate(R.id.loginFragment)
        }

        viewModel.treeNodes.observe(viewLifecycleOwner) {
            treeAdapter.submitList(it)
        }

        viewModel.userProfile.observe(viewLifecycleOwner) {
            binding.tvUsername.text = it?.username ?: "用户"
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> {}
                is Result.Success -> Toast.makeText(requireContext(), result.data, Toast.LENGTH_SHORT).show()
                is Result.Error -> Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openNoteEditor(noteId: Long) {
        val bundle = Bundle().apply { putLong("noteId", noteId) }
        findNavController().navigate(R.id.noteEditorFragment, bundle)
    }

    private fun showFolderPopup(node: TreeNode.FolderNode) {
        val options = arrayOf("重命名", "删除")
        AlertDialog.Builder(requireContext())
            .setTitle(node.folder.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameFolderDialog(node)
                    1 -> showDeleteFolderConfirm(node)
                }
            }
            .show()
    }

    private fun showRenameFolderDialog(node: TreeNode.FolderNode) {
        val input = EditText(requireContext()).apply {
            setText(node.folder.name)
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("重命名文件夹")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.renameFolder(node.folder.id, name, node.folder.parent_id)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteFolderConfirm(node: TreeNode.FolderNode) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除文件夹")
            .setMessage("确定删除文件夹 \"${node.folder.name}\" 及其所有内容吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteFolder(node.folder.id, node.folder.parent_id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCreateNoteDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val titleInput = EditText(requireContext()).apply {
            hint = "笔记标题"
        }
        val folderSpinner = Spinner(requireContext())
        container.addView(titleInput)
        container.addView(folderSpinner)

        val folders = viewModel.getAllFolders()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("根目录") + folders.map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        folderSpinner.adapter = adapter

        AlertDialog.Builder(requireContext())
            .setTitle("新建笔记")
            .setView(container)
            .setPositiveButton("创建") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) return@setPositiveButton
                val selectedIndex = folderSpinner.selectedItemPosition
                val folderId = if (selectedIndex == 0) 0L else folders[selectedIndex - 1].id
                viewModel.createNote(title, folderId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCreateFolderDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val nameInput = EditText(requireContext()).apply {
            hint = "文件夹名称"
        }
        val folderSpinner = Spinner(requireContext())
        container.addView(nameInput)
        container.addView(folderSpinner)

        val folders = viewModel.getAllFolders()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("根目录") + folders.map { it.name }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        folderSpinner.adapter = adapter

        AlertDialog.Builder(requireContext())
            .setTitle("新建文件夹")
            .setView(container)
            .setPositiveButton("创建") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val selectedIndex = folderSpinner.selectedItemPosition
                val parentId = if (selectedIndex == 0) 0L else folders[selectedIndex - 1].id
                viewModel.createFolder(name, parentId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
