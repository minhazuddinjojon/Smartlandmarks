package com.example.smartlandmarks.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlandmarks.domain.model.Visit
import com.example.smartlandmarks.domain.model.VisitStatus
import com.example.smartlandmarks.domain.repository.LandmarkRepository
import com.example.smartlandmarks.services.NetworkMonitor
import com.example.smartlandmarks.workers.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ActivityUiState(
    val visits: List<Visit> = emptyList(),
    val isOffline: Boolean = false,
    val isSyncing: Boolean = false
) {
    val pendingCount: Int
        get() = visits.count { it.status == VisitStatus.QUEUED || it.status == VisitStatus.PENDING }

    val isEmpty: Boolean get() = visits.isEmpty()
}

@HiltViewModel
class ActivityViewModel @Inject constructor(
    repository: LandmarkRepository,
    private val workScheduler: WorkScheduler,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val uiState: StateFlow<ActivityUiState> = combine(
        repository.observeVisits(),
        networkMonitor.isOnline,
        workScheduler.observeSyncRunning()
    ) { visits, isOnline, isSyncing ->
        ActivityUiState(
            visits = visits,
            isOffline = !isOnline,
            isSyncing = isSyncing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityUiState()
    )

    /**
     * Manual nudge. WorkManager still owns the actual execution and its constraints —
     * this only asks for a pass to be enqueued sooner than the periodic schedule.
     */
    fun syncNow() {
        workScheduler.enqueueSyncNow()
    }
}
