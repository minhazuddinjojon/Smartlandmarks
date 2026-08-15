package com.example.smartlandmarks.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlandmarks.data.remote.ApiResult
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.domain.repository.LandmarkRepository
import com.example.smartlandmarks.services.LocationProvider
import com.example.smartlandmarks.services.LocationResult
import com.example.smartlandmarks.services.NetworkMonitor
import com.example.smartlandmarks.services.Coordinates
import com.example.smartlandmarks.ui.common.ErrorMessages
import com.example.smartlandmarks.ui.common.UiMessage
import com.example.smartlandmarks.ui.common.eventFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val landmarks: List<Landmark> = emptyList(),
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val userLocation: Coordinates? = null
) {
    /** The colour ramp is stretched across the data actually present. */
    val minScore: Double get() = landmarks.minOfOrNull { it.score } ?: 0.0
    val maxScore: Double get() = landmarks.maxOfOrNull { it.score } ?: 0.0
    val isEmpty: Boolean get() = landmarks.isEmpty() && !isRefreshing
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: LandmarkRepository,
    private val locationProvider: LocationProvider,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val userLocation = MutableStateFlow<Coordinates?>(null)

    private val _messages = eventFlow<UiMessage>()
    val messages = _messages

    val uiState: StateFlow<MapUiState> = combine(
        repository.observeLandmarks(),
        refreshing,
        networkMonitor.isOnline,
        userLocation
    ) { landmarks, isRefreshing, isOnline, location ->
        MapUiState(
            landmarks = landmarks,
            isRefreshing = isRefreshing,
            isOffline = !isOnline,
            userLocation = location
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapUiState()
    )

    init {
        refresh(showMessages = false)
        // A reconnect should repopulate the map without the user pulling to refresh.
        networkMonitor.isOnline
            .onEach { online -> if (online) refresh(showMessages = false) }
            .launchIn(viewModelScope)
    }

    fun refresh(showMessages: Boolean = true) {
        viewModelScope.launch {
            refreshing.value = true
            when (val result = repository.refreshLandmarks()) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> if (showMessages) {
                    _messages.tryEmit(ErrorMessages.from(result.error))
                }
            }
            refreshing.value = false
        }
    }

    fun locateUser() {
        viewModelScope.launch {
            when (val result = locationProvider.currentLocation()) {
                is LocationResult.Success -> userLocation.value = result.coordinates
                is LocationResult.Failure -> _messages.tryEmit(
                    com.example.smartlandmarks.ui.common.LocationMessages.from(result.reason)
                )
            }
        }
    }
}
