package com.example.smartlandmarks.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smartlandmarks.domain.repository.LandmarkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The single background mechanism behind both async requirements in the lab.
 *
 * Requirement 8 (drain the offline visit queue when connectivity returns) and
 * requirement 10 (poll get_job_status until visits resolve) are the same underlying
 * problem — guaranteed work over an unreliable network that must survive process death
 * — so one worker handles both rather than two competing schedulers.
 *
 * WorkManager is doing real work here, not decoration: the CONNECTED constraint means
 * the queue drains itself without the app listening for connectivity, and the
 * exponential backoff means a pending job is re-polled on a widening schedule instead
 * of in a tight loop.
 */
@HiltWorker
class VisitSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: LandmarkRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val report = try {
            repository.runSyncPass()
        } catch (e: Exception) {
            // A crash here would silently kill the queue, so fail into a retry instead.
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }

        return when {
            // A bad key will never succeed by retrying; stop and let the UI surface it.
            report.invalidKey -> Result.failure()

            // Jobs still pending, or a transient failure: come back with backoff.
            report.stillUnresolved > 0 || report.hadTransientFailure ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()

            else -> Result.success()
        }
    }

    companion object {
        const val WORK_TAG = "visit_sync_worker"

        /**
         * Caps a single work chain. The periodic worker still runs afterwards, so a job
         * that stalls for a long time is picked up again rather than abandoned.
         */
        private const val MAX_ATTEMPTS = 12
    }
}
