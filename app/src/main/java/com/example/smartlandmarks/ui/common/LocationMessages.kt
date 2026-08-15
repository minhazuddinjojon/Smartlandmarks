package com.example.smartlandmarks.ui.common

import com.example.smartlandmarks.services.LocationFailure

/**
 * Location failures are the ones users can actually fix, so each maps to a dialog with
 * a button that takes them to the right settings screen rather than a dead-end message.
 */
object LocationMessages {

    fun from(reason: LocationFailure): UiMessage = when (reason) {
        LocationFailure.PERMISSION_DENIED -> UiMessage.Error(
            title = "Location permission needed",
            text = "This app needs your location to record a visit and to place new " +
                "landmarks. Grant location access in app settings.",
            action = ErrorAction.OPEN_APP_SETTINGS
        )

        LocationFailure.GPS_DISABLED -> UiMessage.Error(
            title = "Location is turned off",
            text = "Turn on location services so the app can read your GPS position.",
            action = ErrorAction.OPEN_LOCATION_SETTINGS
        )

        LocationFailure.UNAVAILABLE -> UiMessage.Warning(
            "Couldn't get a GPS fix. Move somewhere with a clearer view of the sky and try again."
        )
    }
}
