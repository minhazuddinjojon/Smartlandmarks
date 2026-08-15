package com.example.smartlandmarks.ui.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * One-shot events (snackbars, dialogs, navigation) must not replay on rotation, so they
 * use a SharedFlow with no replay rather than living in the screen's state object.
 */
fun <T> eventFlow(): MutableSharedFlow<T> = MutableSharedFlow(
    replay = 0,
    extraBufferCapacity = 8,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
