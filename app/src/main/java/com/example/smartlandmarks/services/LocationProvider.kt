package com.example.smartlandmarks.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** A single GPS reading, in the form the rest of the app cares about. */
data class Coordinates(val latitude: Double, val longitude: Double)

/** Why a location request could not be satisfied. */
enum class LocationFailure { PERMISSION_DENIED, GPS_DISABLED, UNAVAILABLE }

sealed interface LocationResult {
    data class Success(val coordinates: Coordinates) : LocationResult
    data class Failure(val reason: LocationFailure) : LocationResult
}

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Requests a fresh fix, falling back to the last known location.
     *
     * The fallback matters in practice: indoors, `getCurrentLocation` can return null
     * even with GPS enabled, and failing the whole visit for that would be hostile.
     */
    @Suppress("MissingPermission")
    suspend fun currentLocation(): LocationResult {
        if (!hasPermission()) return LocationResult.Failure(LocationFailure.PERMISSION_DENIED)
        if (!isLocationEnabled()) return LocationResult.Failure(LocationFailure.GPS_DISABLED)

        val fresh = requestFresh()
        if (fresh != null) return LocationResult.Success(fresh)

        val last = requestLastKnown()
        return if (last != null) {
            LocationResult.Success(last)
        } else {
            LocationResult.Failure(LocationFailure.UNAVAILABLE)
        }
    }

    @Suppress("MissingPermission")
    private suspend fun requestFresh(): Coordinates? = suspendCancellableCoroutine { cont ->
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(30_000)
            .setDurationMillis(15_000)
            .build()

        runCatching {
            fusedClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (cont.isActive) {
                        cont.resume(location?.let { Coordinates(it.latitude, it.longitude) })
                    }
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    @Suppress("MissingPermission")
    private suspend fun requestLastKnown(): Coordinates? = suspendCancellableCoroutine { cont ->
        runCatching {
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (cont.isActive) {
                        cont.resume(location?.let { Coordinates(it.latitude, it.longitude) })
                    }
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }
}
