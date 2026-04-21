package com.calcite.notes.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.calcite.notes.R
import com.calcite.notes.data.local.AppDataStore
import com.calcite.notes.data.remote.RetrofitClient
import com.calcite.notes.data.repository.SearchRepository
import com.calcite.notes.databinding.FragmentSearchBinding
import com.calcite.notes.databinding.ItemSearchResultBinding
import com.calcite.notes.model.SearchResultItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels {
        val api = RetrofitClient.getApiService(requireContext())
        SearchViewModel.Factory(SearchRepository(api))
    }

    private lateinit var resultAdapter: SearchResultAdapter
    private var currentUserId: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            currentUserId = AppDataStore(requireContext()).userId.first() ?: 0L
        }

        resultAdapter = SearchResultAdapter { item ->
            onSearchResultClick(item)
        }

        binding.recyclerResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerResults.adapter = resultAdapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString()?.trim() ?: "")
            }
        })

        binding.switchPublicSearch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.togglePublicSearch(isChecked)
            val keyword = binding.etSearch.text.toString().trim()
            if (keyword.isNotEmpty()) {
                viewModel.search(keyword)
            }
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            resultAdapter.submitList(results)
            binding.tvEmpty.visibility = if (results.isEmpty() && binding.etSearch.text.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun onSearchResultClick(item: SearchResultItem) {
        val isOwnNote = item.author_id == currentUserId
        if (isOwnNote) {
            val bundle = bundleOf("noteId" to item.id)
            findNavController().navigate(R.id.noteEditorFragment, bundle)
        } else {
            val bundle = bundleOf("noteId" to item.id)
            findNavController().navigate(R.id.notePreviewFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SearchResultAdapter(
    private val onItemClick: (SearchResultItem) -> Unit
) : androidx.recyclerview.widget.ListAdapter<SearchResultItem, SearchResultAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<SearchResultItem>() {
        override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem) =
            oldItem == newItem
    }
) {

    inner class VH(val binding: ItemSearchResultBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchResultItem) {
            val titleHtml = item.highlight_title ?: item.title
            val contentHtml = item.highlight_content ?: item.summary ?: ""

            binding.tvTitle.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(titleHtml)
            }

            binding.tvContent.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(contentHtml, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(contentHtml)
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
