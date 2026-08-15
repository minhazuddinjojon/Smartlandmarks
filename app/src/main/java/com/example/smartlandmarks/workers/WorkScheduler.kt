package com.example.smartlandmarks.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.smartlandmarks.utils.SyncConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every WorkManager interaction, so no ViewModel has to know about work requests.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Runs a sync as soon as there is a connection.
     *
     * APPEND_OR_REPLACE rather than REPLACE: replacing would cancel a sync that is
     * already mid-flight, which could drop a visit that had just been posted but not
     * yet recorded. Appending guarantees the new work runs after the current pass.
     */
    fun enqueueSyncNow() {
        val request = OneTimeWorkRequestBuilder<VisitSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                SyncConstants.BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(VisitSyncWorker.WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            SyncConstants.UNIQUE_ONE_OFF_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /**
     * Safety net, registered at app start.
     *
     * If the app is killed while visits are still pending, no in-process listener will
     * ever fire again — this periodic worker is what actually delivers the "survives
     * process death and app restart" requirement.
     */
    fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<VisitSyncWorker>(
            SyncConstants.PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                SyncConstants.BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(VisitSyncWorker.WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncConstants.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** True while a sync is queued or running — used to drive the syncing indicator. */
    fun observeSyncRunning(): Flow<Boolean> =
        workManager.getWorkInfosByTagFlow(VisitSyncWorker.WORK_TAG)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
}
