package com.example.smartlandmarks.ui.common

import com.example.smartlandmarks.data.remote.ApiError

/**
 * A message destined for the user, tagged with how forcefully to present it.
 *
 * The lab asks for successes via toast/snackbar and failures via dialogs. Encoding that
 * decision in the ViewModel (where the cause is known) rather than in the fragment
 * keeps every screen's presentation consistent.
 */
sealed interface UiMessage {
    val text: String

    /** Transient confirmation. Snackbar. */
    data class Success(override val text: String) : UiMessage

    /** Recoverable problem. Snackbar, usually with a Retry action. */
    data class Warning(override val text: String) : UiMessage

    /** Blocking problem the user must acknowledge or act on. Dialog. */
    data class Error(
        override val text: String,
        val title: String = "Something went wrong",
        val action: ErrorAction = ErrorAction.NONE
    ) : UiMessage
}

/** An optional follow-up the dialog can offer. */
enum class ErrorAction { NONE, OPEN_LOCATION_SETTINGS, OPEN_APP_SETTINGS }

/**
 * Turns a transport-level failure into something a person can act on.
 *
 * Deliberately avoids leaking raw HTTP codes into the UI — "403" means nothing to a
 * user, whereas "your API key is invalid" tells them exactly what to fix.
 */
object ErrorMessages {

    fun from(error: ApiError): UiMessage = when (error) {
        ApiError.Network -> UiMessage.Warning(
            "You're offline. Showing saved data — changes will sync automatically."
        )

        ApiError.Timeout -> UiMessage.Warning(
            "The server took too long to respond. Pull down to try again."
        )

        ApiError.InvalidKey -> UiMessage.Error(
            title = "Invalid API key",
            text = "The server rejected your student key (HTTP 403). Check the " +
                "SMART_LANDMARKS_API_KEY value in gradle.properties and rebuild."
        )

        is ApiError.NotFound -> UiMessage.Error(
            title = "Not found",
            text = error.message ?: "That item no longer exists on the server."
        )

        is ApiError.BadRequest -> UiMessage.Error(
            title = "Request rejected",
            text = error.message ?: "Some required information was missing."
        )

        is ApiError.Server -> UiMessage.Warning(
            "The server had a problem (${error.code}). Please try again shortly."
        )

        is ApiError.Parse -> UiMessage.Warning(
            "The server sent a response the app couldn't read. Showing saved data."
        )

        is ApiError.Unknown -> UiMessage.Warning(
            error.message ?: "Something went wrong. Please try again."
        )
    }
}
