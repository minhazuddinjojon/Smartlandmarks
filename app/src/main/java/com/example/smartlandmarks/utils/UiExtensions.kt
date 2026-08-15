package com.example.smartlandmarks.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.smartlandmarks.R
import com.google.android.material.snackbar.Snackbar

/** Small view helpers so fragments stay focused on state rendering. */

fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

fun View.showSnackbar(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()
}

fun View.showSnackbarWithAction(
    message: String,
    actionLabel: String,
    action: () -> Unit
) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG)
        .setAction(actionLabel) { action() }
        .show()
}

/** Blocking problems the user has to resolve get a dialog, not a transient snackbar. */
fun Fragment.showErrorDialog(
    title: String,
    message: String,
    positiveLabel: String = getString(android.R.string.ok),
    onPositive: (() -> Unit)? = null
) {
    val ctx = context ?: return
    AlertDialog.Builder(ctx)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveLabel) { dialog, _ ->
            dialog.dismiss()
            onPositive?.invoke()
        }
        .apply { if (onPositive != null) setNegativeButton(R.string.action_cancel, null) }
        .show()
}

fun Context.openLocationSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun Activity.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", packageName, null))
        )
    }
}
