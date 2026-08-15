package com.example.smartlandmarks.ui.landmarks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartlandmarks.R
import com.example.smartlandmarks.databinding.FragmentLandmarksBinding
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.ui.common.UiMessage
import com.example.smartlandmarks.ui.details.LandmarkDetailsSheet
import com.example.smartlandmarks.utils.showErrorDialog
import com.example.smartlandmarks.utils.showSnackbar
import com.example.smartlandmarks.utils.showSnackbarWithAction
import com.example.smartlandmarks.utils.visibleIf
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LandmarksFragment : Fragment() {

    private var _binding: FragmentLandmarksBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: LandmarksViewModel by viewModels()

    private val adapter by lazy {
        LandmarkAdapter(onClick = ::openDetails)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLandmarksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupList()
        setupControls()
        observeState()
    }

    private fun setupList() = with(binding.recyclerView) {
        layoutManager = LinearLayoutManager(requireContext())
        adapter = this@LandmarksFragment.adapter
        setHasFixedSize(true)
    }

    private fun setupControls() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        binding.sortButton.setOnClickListener { showSortDialog() }

        binding.scoreSlider.addOnChangeListener(
            Slider.OnChangeListener { _, value, fromUser ->
                if (fromUser) viewModel.setMinimumScore(value.toDouble())
            }
        )

        binding.clearFilterButton.setOnClickListener {
            binding.scoreSlider.value = 0f
            viewModel.clearFilter()
        }

        binding.retryButton.setOnClickListener { viewModel.refresh() }
    }

    private fun showSortDialog() {
        val options = SortOrder.entries.toTypedArray()
        val labels = options.map { it.label }.toTypedArray()
        val current = options.indexOf(viewModel.uiState.value.sortOrder)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_sort_by)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                viewModel.setSortOrder(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collectLatest(::render) }
                launch { viewModel.messages.collectLatest(::handleMessage) }
            }
        }
    }

    private fun render(state: LandmarksUiState) {
        adapter.submitWithScale(state.landmarks)

        binding.swipeRefresh.isRefreshing = state.isRefreshing
        binding.offlineBanner.visibleIf(state.isOffline)
        binding.emptyView.visibleIf(state.isEmpty)
        binding.clearFilterButton.visibleIf(state.isFiltered)

        binding.emptyText.text = if (state.isFiltered && state.totalCount > 0) {
            getString(R.string.empty_filtered)
        } else {
            getString(R.string.empty_landmarks)
        }

        binding.resultCountText.text = getString(
            R.string.format_result_count, state.landmarks.size, state.totalCount
        )

        // The slider's range depends on the loaded data, so it is configured reactively.
        // valueTo must stay strictly above valueFrom or the Slider throws.
        val ceiling = maxOf(state.scoreCeiling, 1.0).toFloat()
        if (binding.scoreSlider.valueTo != ceiling) {
            binding.scoreSlider.valueFrom = 0f
            binding.scoreSlider.valueTo = ceiling
            binding.scoreSlider.value = state.minimumScore.toFloat().coerceIn(0f, ceiling)
        }

        binding.filterLabel.text = getString(
            R.string.format_min_score, state.minimumScore.toFloat()
        )
    }

    private fun openDetails(landmark: Landmark) {
        LandmarkDetailsSheet.newInstance(landmark.id)
            .show(childFragmentManager, LandmarkDetailsSheet.TAG)
    }

    private fun handleMessage(message: UiMessage) {
        when (message) {
            is UiMessage.Success -> binding.root.showSnackbar(message.text)
            is UiMessage.Warning -> binding.root.showSnackbarWithAction(
                message.text, getString(R.string.action_retry)
            ) { viewModel.refresh() }

            is UiMessage.Error -> showErrorDialog(message.title, message.text)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerView.adapter = null
        _binding = null
    }
}
