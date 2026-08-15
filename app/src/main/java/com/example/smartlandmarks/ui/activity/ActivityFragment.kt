package com.example.smartlandmarks.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartlandmarks.R
import com.example.smartlandmarks.databinding.FragmentActivityBinding
import com.example.smartlandmarks.utils.visibleIf
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Visit history: landmark name, visit time, and distance, as the lab requires. */
@AndroidEntryPoint
class ActivityFragment : Fragment() {

    private var _binding: FragmentActivityBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: ActivityViewModel by viewModels()
    private val adapter by lazy { VisitAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.syncNow()
            binding.swipeRefresh.isRefreshing = false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    adapter.submitList(state.visits)
                    binding.emptyView.visibleIf(state.isEmpty)
                    binding.offlineBanner.visibleIf(state.isOffline)
                    binding.syncStatusBar.visibleIf(state.pendingCount > 0 || state.isSyncing)
                    binding.syncStatusText.text = when {
                        state.pendingCount > 0 -> resources.getQuantityString(
                            R.plurals.pending_visits, state.pendingCount, state.pendingCount
                        )
                        else -> getString(R.string.syncing)
                    }
                    binding.syncProgress.visibleIf(state.isSyncing)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerView.adapter = null
        _binding = null
    }
}
