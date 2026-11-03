package com.matrix.messenger.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global flag set by the HTTP client factory when the homeserver answers
 * 401 M_UNKNOWN_TOKEN ("token is not active") — the session was deleted or
 * invalidated server-side (password change, session removal in Element /
 * synapse-admin). The repository reacts by clearing the session and forcing
 * the user back to the login screen.
 */
object TokenInvalidationDetector {

    private val _triggered = MutableStateFlow(false)
    val triggered: StateFlow<Boolean> = _triggered.asStateFlow()

    fun trigger() {
        _triggered.value = true
    }

    fun reset() {
        _triggered.value = false
    }
}
