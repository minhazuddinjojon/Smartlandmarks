package com.example.smartlandmarks.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.smartlandmarks.R
import com.example.smartlandmarks.databinding.ActivityMainBinding
import com.example.smartlandmarks.utils.visibleIf
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /**
     * Requested once at launch rather than at the moment of the first visit, so the
     * Add screen can pre-fill coordinates and the map can show "you are here" without
     * interrupting a task mid-flow.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // The splash destination is full-screen; every other destination is a tab.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.visibleIf(destination.id != R.id.splashFragment)
        }

        requestNotificationPermissionIfNeeded()
    }

    /** WorkManager may post sync notifications on API 33+, which requires a grant. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
