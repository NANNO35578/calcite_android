package com.calcite.notes.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.calcite.notes.R
import com.calcite.notes.databinding.FragmentRecommendBinding
import com.calcite.notes.databinding.ItemRecommendNoteBinding
import com.calcite.notes.model.RecommendNoteItem

class RecommendFragment : Fragment() {

    private var _binding: FragmentRecommendBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecommendViewModel by viewModels {
        RecommendViewModel.Factory(requireContext())
    }

    private lateinit var adapter: RecommendAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecommendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecommendAdapter { item ->
            val bundle = bundleOf("noteId" to item.id)
            findNavController().navigate(R.id.notePreviewFragment, bundle)
        }

        binding.recyclerRecommend.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecommend.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadRecommendations()
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.recommendList.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class RecommendAdapter(
    private val onItemClick: (RecommendNoteItem) -> Unit
) : androidx.recyclerview.widget.ListAdapter<RecommendNoteItem, RecommendAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<RecommendNoteItem>() {
        override fun areItemsTheSame(oldItem: RecommendNoteItem, newItem: RecommendNoteItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RecommendNoteItem, newItem: RecommendNoteItem) =
            oldItem == newItem
    }
) {

    inner class VH(val binding: ItemRecommendNoteBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecommendNoteItem) {
            binding.tvTitle.text = item.title
            binding.tvSummary.text = item.summary ?: ""
            binding.tvTime.text = item.created_at
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemRecommendNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
