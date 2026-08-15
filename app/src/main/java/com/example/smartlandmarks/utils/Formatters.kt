package com.example.smartlandmarks.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Presentation-only helpers. Nothing here touches business rules. */
object Formatters {

    private val timeFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

    /** The API reports distances in metres; show metres below 1 km and kilometres above. */
    fun distance(metres: Double?): String = when {
        metres == null -> "—"
        metres < 1_000 -> "${metres.roundToInt()} m"
        else -> String.format(Locale.getDefault(), "%.2f km", metres / 1_000.0)
    }

    fun score(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

    fun coordinate(value: Double): String = String.format(Locale.getDefault(), "%.5f", value)

    fun timestamp(epochMillis: Long): String = timeFormat.format(Date(epochMillis))
}
