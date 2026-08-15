package com.example.smartlandmarks.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlandmarks.domain.repository.CreateOutcome
import com.example.smartlandmarks.domain.repository.LandmarkRepository
import com.example.smartlandmarks.services.LocationProvider
import com.example.smartlandmarks.services.LocationResult
import com.example.smartlandmarks.ui.common.ErrorMessages
import com.example.smartlandmarks.ui.common.LocationMessages
import com.example.smartlandmarks.ui.common.UiMessage
import com.example.smartlandmarks.ui.common.eventFlow
import com.example.smartlandmarks.utils.ApiConstants
import com.example.smartlandmarks.utils.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class AddLandmarkUiState(
    val title: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val imagePath: String? = null,
    val isLocating: Boolean = false,
    val isSubmitting: Boolean = false,
    val titleError: String? = null,
    val latitudeError: String? = null,
    val longitudeError: String? = null,
    val imageError: String? = null
) {
    val canSubmit: Boolean get() = !isSubmitting
}

/** Emitted once the landmark is stored (locally or remotely) so the form can reset. */
data object FormSubmitted

@HiltViewModel
class AddLandmarkViewModel @Inject constructor(
    private val repository: LandmarkRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddLandmarkUiState())
    val uiState: StateFlow<AddLandmarkUiState> = _uiState.asStateFlow()

    private val _messages = eventFlow<UiMessage>()
    val messages = _messages

    private val _submitted = eventFlow<FormSubmitted>()
    val submitted = _submitted

    /** Survives fragment recreation, so a rotation does not re-trigger the auto-fill. */
    private var hasAutoLocated = false

    /**
     * Pre-fills the coordinates from GPS the first time the screen is opened, which is
     * what the "auto-fetch GPS location for new entry" requirement asks for. Guarded so
     * it never overwrites coordinates the user has already typed or already fetched.
     */
    fun autoFetchLocationIfNeeded() {
        if (hasAutoLocated) return
        val state = _uiState.value
        if (state.latitude.isNotBlank() || state.longitude.isNotBlank()) return
        hasAutoLocated = true
        fetchCurrentLocation()
    }

    fun onTitleChanged(value: String) {
        _uiState.value = _uiState.value.copy(title = value, titleError = null)
    }

    fun onLatitudeChanged(value: String) {
        _uiState.value = _uiState.value.copy(latitude = value, latitudeError = null)
    }

    fun onLongitudeChanged(value: String) {
        _uiState.value = _uiState.value.copy(longitude = value, longitudeError = null)
    }

    /**
     * The picked image is already copied into app-private cache by the fragment, so the
     * size check here is on a file the app owns and can still read hours later.
     */
    fun onImageSelected(file: File?) {
        if (file == null) {
            _uiState.value = _uiState.value.copy(
                imagePath = null,
                imageError = "Couldn't read that image. Try another one."
            )
            return
        }
        if (!FileUtils.isWithinSizeLimit(file)) {
            FileUtils.deleteQuietly(file.absolutePath)
            _uiState.value = _uiState.value.copy(
                imagePath = null,
                imageError = "Image is larger than 2 MB. Pick a smaller one."
            )
            return
        }
        _uiState.value = _uiState.value.copy(imagePath = file.absolutePath, imageError = null)
    }

    fun clearImage() {
        FileUtils.deleteQuietly(_uiState.value.imagePath)
        _uiState.value = _uiState.value.copy(imagePath = null, imageError = null)
    }

    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLocating = true)
            when (val result = locationProvider.currentLocation()) {
                is LocationResult.Success -> _uiState.value = _uiState.value.copy(
                    latitude = result.coordinates.latitude.toString(),
                    longitude = result.coordinates.longitude.toString(),
                    latitudeError = null,
                    longitudeError = null
                )

                is LocationResult.Failure ->
                    _messages.tryEmit(LocationMessages.from(result.reason))
            }
            _uiState.value = _uiState.value.copy(isLocating = false)
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        val validated = validate(state)
        if (validated != null) {
            _uiState.value = validated
            return
        }

        val latitude = state.latitude.trim().toDouble()
        val longitude = state.longitude.trim().toDouble()
        val image = state.imagePath?.let { File(it) }?.takeIf { it.exists() }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)

            val outcome = repository.createLandmark(
                title = state.title.trim(),
                latitude = latitude,
                longitude = longitude,
                image = image
            )

            when (outcome) {
                is CreateOutcome.Created -> {
                    _messages.tryEmit(UiMessage.Success("Landmark added."))
                    reset()
                    _submitted.tryEmit(FormSubmitted)
                }

                CreateOutcome.Queued -> {
                    _messages.tryEmit(
                        UiMessage.Warning(
                            "You're offline. This landmark is saved and will upload automatically."
                        )
                    )
                    reset()
                    _submitted.tryEmit(FormSubmitted)
                }

                is CreateOutcome.Rejected -> {
                    _messages.tryEmit(ErrorMessages.from(outcome.error))
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                }
            }
        }
    }

    private fun reset() {
        _uiState.value = AddLandmarkUiState()
    }

    /**
     * Returns a state carrying field errors, or null when the form is valid.
     *
     * Coordinates are range-checked because the API accepts anything numeric, and a
     * typo like 233.7 would silently place a landmark nowhere.
     */
    private fun validate(state: AddLandmarkUiState): AddLandmarkUiState? {
        var invalid = false
        var result = state

        if (state.title.isBlank()) {
            result = result.copy(titleError = "Title is required")
            invalid = true
        } else if (state.title.trim().length < 3) {
            result = result.copy(titleError = "Title must be at least 3 characters")
            invalid = true
        }

        val lat = state.latitude.trim().toDoubleOrNull()
        when {
            lat == null -> {
                result = result.copy(latitudeError = "Enter a valid latitude")
                invalid = true
            }
            lat < -90.0 || lat > 90.0 -> {
                result = result.copy(latitudeError = "Latitude must be between -90 and 90")
                invalid = true
            }
        }

        val lon = state.longitude.trim().toDoubleOrNull()
        when {
            lon == null -> {
                result = result.copy(longitudeError = "Enter a valid longitude")
                invalid = true
            }
            lon < -180.0 || lon > 180.0 -> {
                result = result.copy(longitudeError = "Longitude must be between -180 and 180")
                invalid = true
            }
        }

        state.imagePath?.let { path ->
            val file = File(path)
            if (file.exists() && file.length() > ApiConstants.MAX_IMAGE_BYTES) {
                result = result.copy(imageError = "Image is larger than 2 MB")
                invalid = true
            }
        }

        return if (invalid) result else null
    }
}
