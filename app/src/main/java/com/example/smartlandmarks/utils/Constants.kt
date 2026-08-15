package com.example.smartlandmarks.utils

/**
 * Every literal the CSE 489 API depends on, in one place.
 *
 * The backend is a single PHP script routed by an `action` query parameter rather
 * than by REST paths, so the "endpoint" is always `api.php` and the action names
 * below are what actually distinguish one call from another.
 */
object ApiConstants {
    const val PATH = "api.php"

    const val PARAM_ACTION = "action"
    const val PARAM_KEY = "key"
    const val PARAM_JOB_ID = "job_id"

    const val ACTION_GET_LANDMARKS = "get_landmarks"
    const val ACTION_CREATE_LANDMARK = "create_landmark"
    const val ACTION_DELETE_LANDMARK = "delete_landmark"
    const val ACTION_RESTORE_LANDMARK = "restore_landmark"
    const val ACTION_VISIT_LANDMARK = "visit_landmark"
    const val ACTION_GET_JOB_STATUS = "get_job_status"

    const val STATUS_PENDING = "pending"
    const val STATUS_DONE = "done"
    const val STATUS_FAILED = "failed"

    const val HTTP_BAD_REQUEST = 400
    const val HTTP_FORBIDDEN = 403
    const val HTTP_NOT_FOUND = 404

    /** Server rejects images above this size. */
    const val MAX_IMAGE_BYTES = 2L * 1024 * 1024
}

/** Map defaults. Bangladesh's approximate geographic centre. */
object MapConstants {
    const val BANGLADESH_LAT = 23.6850
    const val BANGLADESH_LON = 90.3563
    const val DEFAULT_ZOOM = 7.0
    const val FOCUSED_ZOOM = 14.0
    const val MIN_ZOOM = 4.0
    const val MAX_ZOOM = 19.0
}

/** Work / sync tuning. */
object SyncConstants {
    const val UNIQUE_ONE_OFF_WORK = "smart_landmarks_sync_once"
    const val UNIQUE_PERIODIC_WORK = "smart_landmarks_sync_periodic"
    const val PERIODIC_INTERVAL_MINUTES = 15L
    const val BACKOFF_DELAY_SECONDS = 10L

    /** A visit job that never resolves is abandoned after this many worker passes. */
    const val MAX_POLL_ATTEMPTS = 40

    /** A queued request that keeps failing is abandoned after this many attempts. */
    const val MAX_UPLOAD_ATTEMPTS = 10
}
