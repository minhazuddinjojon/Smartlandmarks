package com.example.smartlandmarks.ui.details

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.example.smartlandmarks.R
import com.example.smartlandmarks.databinding.SheetLandmarkDetailsBinding
import com.example.smartlandmarks.ui.common.ErrorAction
import com.example.smartlandmarks.ui.common.UiMessage
import com.example.smartlandmarks.utils.Formatters
import com.example.smartlandmarks.utils.openAppSettings
import com.example.smartlandmarks.utils.openLocationSettings
import com.example.smartlandmarks.utils.showSnackbar
import com.example.smartlandmarks.utils.visibleIf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Landmark detail, shown from both the map and the list. Hosts the Visit action.
 */
@AndroidEntryPoint
class LandmarkDetailsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetLandmarkDetailsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: LandmarkDetailsViewModel by viewModels()

    private val landmarkId: Int
        get() = requireArguments().getInt(ARG_LANDMARK_ID)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.visit()
        } else {
            binding.root.showSnackbar(getString(R.string.error_location_permission_denied))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetLandmarkDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.load(landmarkId)

        binding.visitButton.setOnClickListener {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        binding.deleteButton.setOnClickListener { confirmDelete() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        val landmark = state.landmark
                        if (landmark != null) {
                            binding.titleText.text = landmark.title
                            binding.scoreText.text = getString(
                                R.string.format_score, Formatters.score(landmark.score)
                            )
                            binding.visitCountText.text = getString(
                                R.string.format_visit_count, landmark.visitCount
                            )
                            binding.averageDistanceText.text = getString(
                                R.string.format_average_distance,
                                Formatters.distance(landmark.averageDistance)
                            )
                            binding.coordinatesText.text = getString(
                                R.string.format_coordinates,
                                Formatters.coordinate(landmark.latitude),
                                Formatters.coordinate(landmark.longitude)
                            )
                            binding.landmarkImage.load(landmark.imageUrl) {
                                crossfade(true)
                                placeholder(R.drawable.ic_image_placeholder)
                                error(R.drawable.ic_image_placeholder)
                            }
                        }

                        binding.visitButton.isEnabled = !state.isVisiting && landmark != null
                        binding.visitProgress.visibleIf(state.isVisiting)
                    }
                }

                launch { viewModel.messages.collectLatest(::handleMessage) }
            }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_delete_landmark)
            .setMessage(R.string.message_delete_landmark)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.delete()
                dismissAllowingStateLoss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun handleMessage(message: UiMessage) {
        when (message) {
            is UiMessage.Success -> {
                binding.root.showSnackbar(message.text)
                dismissAllowingStateLoss()
            }

            is UiMessage.Warning -> {
                binding.root.showSnackbar(message.text)
                dismissAllowingStateLoss()
            }

            is UiMessage.Error -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(message.title)
                .setMessage(message.text)
                .setPositiveButton(
                    if (message.action == ErrorAction.NONE) {
                        getString(android.R.string.ok)
                    } else {
                        getString(R.string.action_open_settings)
                    }
                ) { _, _ ->
                    when (message.action) {
                        ErrorAction.OPEN_LOCATION_SETTINGS -> requireContext().openLocationSettings()
                        ErrorAction.OPEN_APP_SETTINGS -> requireActivity().openAppSettings()
                        ErrorAction.NONE -> Unit
                    }
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LandmarkDetailsSheet"
        private const val ARG_LANDMARK_ID = "arg_landmark_id"

        fun newInstance(landmarkId: Int): LandmarkDetailsSheet =
            LandmarkDetailsSheet().apply {
                arguments = Bundle().apply { putInt(ARG_LANDMARK_ID, landmarkId) }
            }
    }
}
