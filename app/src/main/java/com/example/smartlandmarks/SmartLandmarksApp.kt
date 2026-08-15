package com.example.smartlandmarks

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.smartlandmarks.workers.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfiguration
import javax.inject.Inject

@HiltAndroidApp
class SmartLandmarksApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var workScheduler: WorkScheduler

    /**
     * Supplying the Hilt factory here is what lets @HiltWorker workers be constructed
     * with injected dependencies. It is paired with removing WorkManagerInitializer in
     * the manifest — without both halves, every enqueue fails at runtime.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
        // Re-arms the safety-net worker on every cold start, including after a reboot.
        workScheduler.ensurePeriodicSync()
    }

    /**
     * osmdroid requires an explicit user agent; tile servers reject the default and the
     * map silently renders blank tiles otherwise.
     */
    private fun configureOsmdroid() {
        val prefs = getSharedPreferences(OSMDROID_PREFS, MODE_PRIVATE)
        val config = OsmConfiguration.getInstance()
        // Descriptive, unique User-Agent to comply with OSM policy.
        val ua = "SmartLandmarksStudentProject/1.0 (cse489.student.24241197@example.com)"
        config.userAgentValue = ua
        config.load(this, prefs)
        config.userAgentValue = ua
        config.osmdroidBasePath = cacheDir
        config.osmdroidTileCache = java.io.File(cacheDir, "osmdroid_tiles_final")
    }

    private companion object {
        const val OSMDROID_PREFS = "osmdroid"
    }
}
