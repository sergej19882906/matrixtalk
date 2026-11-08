package com.matrix.messenger.ui.theme

import com.matrix.messenger.data.repository.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

/** App-wide theme mode and color palette, persisted next to the session in DataStore. */
object ThemeManager {
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    private val _palette = MutableStateFlow(ThemePalette.DEFAULT)
    val palette: StateFlow<ThemePalette> = _palette.asStateFlow()

    suspend fun load() {
        _mode.value = runCatching {
            when (SessionStore.readThemeMode()) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }.getOrDefault(ThemeMode.SYSTEM)
        _palette.value = runCatching {
            ThemePalette.fromStored(SessionStore.readThemePalette())
        }.getOrDefault(ThemePalette.DEFAULT)
    }

    suspend fun set(mode: ThemeMode) {
        SessionStore.saveThemeMode(mode.name.lowercase())
        _mode.value = mode
    }

    suspend fun set(palette: ThemePalette) {
        SessionStore.saveThemePalette(palette.name.lowercase())
        _palette.value = palette
    }
}
