package com.matrix.messenger.ui.theme

import com.matrix.messenger.data.repository.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

/** App-wide theme mode, persisted next to the session in DataStore. */
object ThemeManager {
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    suspend fun load() {
        _mode.value = runCatching {
            when (SessionStore.readThemeMode()) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun set(mode: ThemeMode) {
        _mode.value = mode
        runCatching { SessionStore.saveThemeMode(mode.name.lowercase()) }
    }
}
