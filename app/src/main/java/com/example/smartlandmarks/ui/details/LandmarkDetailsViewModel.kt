package com.example.smartlandmarks.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlandmarks.data.remote.ApiResult
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.domain.repository.CreateOutcome
import com.example.smartlandmarks.domain.repository.LandmarkRepository
import com.example.smartlandmarks.domain.repository.VisitOutcome
import com.example.smartlandmarks.services.LocationProvider
import com.example.smartlandmarks.services.LocationResult
import com.example.smartlandmarks.ui.common.ErrorMessages
import com.example.smartlandmarks.ui.common.LocationMessages
import com.example.smartlandmarks.ui.common.UiMessage
import com.example.smartlandmarks.ui.common.eventFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val landmark: Landmark? = null,
    val isVisiting: Boolean = false
)

/**
 * Owns the visit flow, which both the map and the list route into. Keeping it in one
 * place means the GPS-then-post-then-poll sequence is implemented exactly once.
 */
@HiltViewModel
class LandmarkDetailsViewModel @Inject constructor(
    private val repository: LandmarkRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _messages = eventFlow<UiMessage>()
    val messages = _messages

    fun load(landmarkId: Int) {
        repository.observeLandmark(landmarkId)
            .onEach { landmark -> _uiState.value = _uiState.value.copy(landmark = landmark) }
            .launchIn(viewModelScope)
    }

    /**
     * Reads GPS, then hands the visit to the repository.
     *
     * Note what is deliberately absent: any waiting for the distance. The server only
     * returns a job_id here, so the UI confirms acceptance and the sync worker resolves
     * the distance later, writing it into Room where the Activity screen picks it up.
     */
    fun visit() {
        val landmark = _uiState.value.landmark ?: return
        if (_uiState.value.isVisiting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVisiting = true)

            when (val location = locationProvider.currentLocation()) {
                is LocationResult.Failure -> {
                    _messages.tryEmit(LocationMessages.from(location.reason))
                }

                is LocationResult.Success -> {
                    val outcome = repository.recordVisit(
                        landmarkId = landmark.id,
                        landmarkTitle = landmark.title,
                        userLatitude = location.coordinates.latitude,
                        userLongitude = location.coordinates.longitude
                    )
                    _messages.tryEmit(outcome.toMessage())
                }
            }

            _uiState.value = _uiState.value.copy(isVisiting = false)
        }
    }

    fun delete() {
        val landmark = _uiState.value.landmark ?: return
        viewModelScope.launch {
            when (val result = repository.deleteLandmark(landmark.id)) {
                is ApiResult.Success ->
                    _messages.tryEmit(UiMessage.Success("Deleted \"${landmark.title}\""))

                is ApiResult.Failure -> _messages.tryEmit(ErrorMessages.from(result.error))
            }
        }
    }

    private fun VisitOutcome.toMessage(): UiMessage = when (this) {
        is VisitOutcome.Accepted -> UiMessage.Success(
            "Visit recorded. Calculating distance in the background — check the Activity tab."
        )

        is VisitOutcome.Queued -> UiMessage.Warning(
            "You're offline. This visit is queued and will be sent automatically."
        )

        is VisitOutcome.Rejected -> ErrorMessages.from(error)
    }
}
