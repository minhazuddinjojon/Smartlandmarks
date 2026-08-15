package com.example.smartlandmarks.ui.map

import android.Manifest
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.smartlandmarks.databinding.FragmentMapBinding
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.ui.common.ErrorAction
import com.example.smartlandmarks.ui.common.UiMessage
import com.example.smartlandmarks.ui.details.LandmarkDetailsSheet
import com.example.smartlandmarks.utils.MapConstants
import com.example.smartlandmarks.utils.ScoreColor
import com.example.smartlandmarks.utils.openAppSettings
import com.example.smartlandmarks.utils.openLocationSettings
import com.example.smartlandmarks.utils.showErrorDialog
import com.example.smartlandmarks.utils.showSnackbar
import com.example.smartlandmarks.utils.visibleIf
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

/**
 * OpenStreetMap view of every active landmark.
 *
 * osmdroid is used rather than Google Maps because it needs no API key or billing
 * account — the project stays extract-and-run for anyone who opens it.
 */
@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: MapViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.locateUser()
        } else {
            binding.root.showSnackbar("Location permission denied.")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupActions()
        observeState()
    }

    private fun setupMap() = with(binding.mapView) {
        setTileSource(TileSourceFactory.USGS_TOPO)
        setMultiTouchControls(true)
        // Explicit setters: osmdroid's getter returns a primitive double while the
        // setter takes a boxed Double, so Kotlin does not synthesise a property here.
        setMinZoomLevel(MapConstants.MIN_ZOOM)
        setMaxZoomLevel(MapConstants.MAX_ZOOM)
        controller.setZoom(MapConstants.DEFAULT_ZOOM)
        controller.setCenter(GeoPoint(MapConstants.BANGLADESH_LAT, MapConstants.BANGLADESH_LON))
    }

    private fun setupActions() {
        binding.fabLocate.setOnClickListener {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        binding.fabRefresh.setOnClickListener { viewModel.refresh() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        renderMarkers(state)
                        binding.progressBar.visibleIf(state.isRefreshing)
                        binding.offlineBanner.visibleIf(state.isOffline)
                        binding.emptyView.visibleIf(state.isEmpty)
                        state.userLocation?.let { location ->
                            binding.mapView.controller.animateTo(
                                GeoPoint(location.latitude, location.longitude)
                            )
                            binding.mapView.controller.setZoom(MapConstants.FOCUSED_ZOOM)
                        }
                    }
                }
                launch { viewModel.messages.collectLatest { handleMessage(it) } }
            }
        }
    }

    private fun renderMarkers(state: MapUiState) {
        val map = binding.mapView
        map.overlays.clear()

        state.landmarks.forEach { landmark ->
            val marker = Marker(map).apply {
                position = GeoPoint(landmark.latitude, landmark.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = landmark.title
                icon = buildMarkerIcon(landmark, state.minScore, state.maxScore)
                setOnMarkerClickListener { _, _ ->
                    openDetails(landmark)
                    true
                }
            }
            map.overlays.add(marker)
        }

        map.invalidate()
    }

    /**
     * Markers are drawn at runtime so the fill colour can encode the score. A static
     * drawable could not express a continuous scale.
     */
    private fun buildMarkerIcon(landmark: Landmark, minScore: Double, maxScore: Double) =
        run {
            val size = (resources.displayMetrics.density * MARKER_SIZE_DP).toInt().coerceAtLeast(24)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = size / 2f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ScoreColor.forScore(landmark.score, minScore, maxScore)
                style = Paint.Style.FILL
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = size * 0.12f
            }
            canvas.drawCircle(radius, radius, radius - stroke.strokeWidth / 2f, fill)
            canvas.drawCircle(radius, radius, radius - stroke.strokeWidth / 2f, stroke)
            bitmap.toDrawable(resources)
        }

    private fun openDetails(landmark: Landmark) {
        LandmarkDetailsSheet.newInstance(landmark.id)
            .show(childFragmentManager, LandmarkDetailsSheet.TAG)
    }

    private fun handleMessage(message: UiMessage) {
        when (message) {
            is UiMessage.Success, is UiMessage.Warning -> binding.root.showSnackbar(message.text)
            is UiMessage.Error -> showErrorDialog(
                title = message.title,
                message = message.text,
                positiveLabel = when (message.action) {
                    ErrorAction.NONE -> getString(android.R.string.ok)
                    else -> "Open settings"
                }
            ) {
                when (message.action) {
                    ErrorAction.OPEN_LOCATION_SETTINGS -> requireContext().openLocationSettings()
                    ErrorAction.OPEN_APP_SETTINGS -> requireActivity().openAppSettings()
                    ErrorAction.NONE -> Unit
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // osmdroid holds native tile handles; detaching prevents a leak across rotations.
        binding.mapView.onDetach()
        _binding = null
    }

    private companion object {
        const val MARKER_SIZE_DP = 22
    }
}
