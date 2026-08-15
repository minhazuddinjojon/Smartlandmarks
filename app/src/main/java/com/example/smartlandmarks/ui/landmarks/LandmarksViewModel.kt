package com.example.smartlandmarks.ui.landmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlandmarks.data.remote.ApiResult
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.domain.repository.LandmarkRepository
import com.example.smartlandmarks.services.NetworkMonitor
import com.example.smartlandmarks.ui.common.ErrorMessages
import com.example.smartlandmarks.ui.common.UiMessage
import com.example.smartlandmarks.ui.common.eventFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder(val label: String) {
    SCORE_DESC("Score: high to low"),
    SCORE_ASC("Score: low to high"),
    TITLE_ASC("Title: A to Z"),
    VISITS_DESC("Most visited")
}

data class LandmarksUiState(
    val landmarks: List<Landmark> = emptyList(),
    val sortOrder: SortOrder = SortOrder.SCORE_DESC,
    val minimumScore: Double = 0.0,
    val scoreCeiling: Double = 0.0,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val totalCount: Int = 0
) {
    val isFiltered: Boolean get() = minimumScore > 0.0
    val isEmpty: Boolean get() = landmarks.isEmpty() && !isRefreshing
}

@HiltViewModel
class LandmarksViewModel @Inject constructor(
    private val repository: LandmarkRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val sortOrder = MutableStateFlow(SortOrder.SCORE_DESC)
    private val minimumScore = MutableStateFlow(0.0)
    private val refreshing = MutableStateFlow(false)

    private val _messages = eventFlow<UiMessage>()
    val messages = _messages

    val uiState: StateFlow<LandmarksUiState> = combine(
        repository.observeLandmarks(),
        sortOrder,
        minimumScore,
        refreshing,
        networkMonitor.isOnline
    ) { landmarks, sort, minScore, isRefreshing, isOnline ->
        // Sorting and filtering happen here rather than in SQL so the slider can react
        // instantly without re-querying the database on every drag.
        val filtered = landmarks.filter { it.score >= minScore }
        val sorted = when (sort) {
            SortOrder.SCORE_DESC -> filtered.sortedByDescending { it.score }
            SortOrder.SCORE_ASC -> filtered.sortedBy { it.score }
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.VISITS_DESC -> filtered.sortedByDescending { it.visitCount }
        }

        LandmarksUiState(
            landmarks = sorted,
            sortOrder = sort,
            minimumScore = minScore,
            scoreCeiling = landmarks.maxOfOrNull { it.score } ?: 0.0,
            isRefreshing = isRefreshing,
            isOffline = !isOnline,
            totalCount = landmarks.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LandmarksUiState()
    )

    init {
        refresh(showMessages = false)
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

    fun setSortOrder(order: SortOrder) {
        sortOrder.value = order
    }

    fun setMinimumScore(score: Double) {
        minimumScore.value = score
    }

    fun clearFilter() {
        minimumScore.value = 0.0
    }

    /** Soft delete, offered with an Undo that calls restore_landmark. */
    fun delete(landmark: Landmark) {
        viewModelScope.launch {
            when (val result = repository.deleteLandmark(landmark.id)) {
                is ApiResult.Success ->
                    _messages.tryEmit(UiMessage.Success("Deleted \"${landmark.title}\""))

                is ApiResult.Failure -> _messages.tryEmit(ErrorMessages.from(result.error))
            }
        }
    }

    fun restore(landmark: Landmark) {
        viewModelScope.launch {
            when (val result = repository.restoreLandmark(landmark.id)) {
                is ApiResult.Success ->
                    _messages.tryEmit(UiMessage.Success("Restored \"${landmark.title}\""))

                is ApiResult.Failure -> _messages.tryEmit(ErrorMessages.from(result.error))
            }
        }
    }
}
