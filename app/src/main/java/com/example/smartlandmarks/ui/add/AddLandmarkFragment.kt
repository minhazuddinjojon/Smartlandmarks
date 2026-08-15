package com.example.smartlandmarks.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.example.smartlandmarks.R
import com.example.smartlandmarks.databinding.FragmentAddLandmarkBinding
import com.example.smartlandmarks.ui.common.ErrorAction
import com.example.smartlandmarks.ui.common.UiMessage
import com.example.smartlandmarks.utils.FileUtils
import com.example.smartlandmarks.utils.openAppSettings
import com.example.smartlandmarks.utils.openLocationSettings
import com.example.smartlandmarks.utils.showErrorDialog
import com.example.smartlandmarks.utils.showSnackbar
import com.example.smartlandmarks.utils.visibleIf
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Add a landmark: title, coordinates (pre-filled from GPS), and an optional image.
 *
 * Uses the system Photo Picker, which needs no storage permission on any API level.
 */
@AndroidEntryPoint
class AddLandmarkFragment : Fragment() {

    private var _binding: FragmentAddLandmarkBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AddLandmarkViewModel by viewModels()

    /** Guards against the text watchers echoing programmatic updates back in. */
    private var isBindingText = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.fetchCurrentLocation()
        } else {
            binding.root.showSnackbar(getString(R.string.error_location_permission_denied))
        }
    }

    /**
     * Same launcher, quieter failure path: this one fires unprompted when the screen
     * opens, so a denial is the user answering a dialog they did not ask for — the
     * "Use my location" button is still there to retry deliberately.
     */
    private val autoLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.autoFetchLocationIfNeeded()
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        // Copied immediately: the content URI grant does not survive to upload time.
        val cached = FileUtils.copyToCache(requireContext(), uri)
        viewModel.onImageSelected(cached)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddLandmarkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInputs()
        setupActions()
        observeState()
        autoFillLocation()
    }

    /**
     * Requirement 6: the coordinates fill themselves in when the screen opens rather
     * than waiting for a tap. If permission is missing it is asked for once here; the
     * ViewModel guard means this costs nothing on a return visit to the tab.
     */
    private fun autoFillLocation() {
        val granted = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).any {
            ContextCompat.checkSelfPermission(requireContext(), it) ==
                PackageManager.PERMISSION_GRANTED
        }

        if (granted) {
            viewModel.autoFetchLocationIfNeeded()
        } else {
            autoLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun setupInputs() {
        binding.titleInput.doAfterTextChanged {
            if (!isBindingText) viewModel.onTitleChanged(it?.toString().orEmpty())
        }
        binding.latitudeInput.doAfterTextChanged {
            if (!isBindingText) viewModel.onLatitudeChanged(it?.toString().orEmpty())
        }
        binding.longitudeInput.doAfterTextChanged {
            if (!isBindingText) viewModel.onLongitudeChanged(it?.toString().orEmpty())
        }
    }

    private fun setupActions() {
        binding.useGpsButton.setOnClickListener {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        binding.pickImageButton.setOnClickListener {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.removeImageButton.setOnClickListener { viewModel.clearImage() }

        binding.submitButton.setOnClickListener { viewModel.submit() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collectLatest(::render) }
                launch { viewModel.messages.collectLatest(::handleMessage) }
                launch {
                    viewModel.submitted.collectLatest {
                        isBindingText = true
                        binding.titleInput.setText("")
                        binding.latitudeInput.setText("")
                        binding.longitudeInput.setText("")
                        isBindingText = false
                    }
                }
            }
        }
    }

    private fun render(state: AddLandmarkUiState) {
        // Only push text back into fields that are actually out of sync (GPS fill),
        // otherwise the cursor jumps to the start on every keystroke.
        isBindingText = true
        if (binding.latitudeInput.text?.toString() != state.latitude) {
            binding.latitudeInput.setText(state.latitude)
        }
        if (binding.longitudeInput.text?.toString() != state.longitude) {
            binding.longitudeInput.setText(state.longitude)
        }
        isBindingText = false

        binding.titleLayout.error = state.titleError
        binding.latitudeLayout.error = state.latitudeError
        binding.longitudeLayout.error = state.longitudeError

        binding.imageErrorText.visibleIf(state.imageError != null)
        binding.imageErrorText.text = state.imageError.orEmpty()

        val hasImage = state.imagePath != null
        binding.imagePreview.visibleIf(hasImage)
        binding.removeImageButton.visibleIf(hasImage)
        if (hasImage) {
            binding.imagePreview.load(File(state.imagePath!!)) {
                crossfade(true)
                error(R.drawable.ic_image_placeholder)
            }
        }

        binding.gpsProgress.visibleIf(state.isLocating)
        binding.useGpsButton.isEnabled = !state.isLocating

        binding.submitButton.isEnabled = state.canSubmit
        binding.submitProgress.visibleIf(state.isSubmitting)
    }

    private fun handleMessage(message: UiMessage) {
        when (message) {
            is UiMessage.Success, is UiMessage.Warning -> binding.root.showSnackbar(message.text)
            is UiMessage.Error -> showErrorDialog(
                title = message.title,
                message = message.text,
                positiveLabel = if (message.action == ErrorAction.NONE) {
                    getString(android.R.string.ok)
                } else {
                    getString(R.string.action_open_settings)
                }
            ) {
                when (message.action) {
                    ErrorAction.OPEN_LOCATION_SETTINGS -> requireContext().openLocationSettings()
                    ErrorAction.OPEN_APP_SETTINGS -> requireActivity().openAppSettings()
                    ErrorAction.NONE -> Unit
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
