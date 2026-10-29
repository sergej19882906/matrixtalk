package com.matrix.messenger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.data.repository.SessionStore
import com.matrix.messenger.platform.setMessageNotificationsEnabled
import com.matrix.messenger.ui.theme.ThemeManager
import com.matrix.messenger.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val user: MatrixUser? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val displayNameInput: String = "",
    val isSavingName: Boolean = false
)

class SettingsViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            matrixRepository.currentUser.collect { user ->
                _uiState.value = _uiState.value.copy(
                    user = user,
                    displayNameInput = user?.displayName ?: ""
                )
            }
        }
        viewModelScope.launch {
            ThemeManager.mode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            val enabled = SessionStore.readNotificationsEnabled() ?: true
            _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { ThemeManager.set(mode) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
        viewModelScope.launch {
            SessionStore.saveNotificationsEnabled(enabled)
            setMessageNotificationsEnabled(enabled)
        }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.value = _uiState.value.copy(displayNameInput = value)
    }

    fun saveDisplayName() {
        val name = _uiState.value.displayNameInput.trim()
        if (name.isBlank()) return
        _uiState.value = _uiState.value.copy(isSavingName = true)
        viewModelScope.launch {
            try {
                matrixRepository.setDisplayName(name)
            } catch (_: Exception) {
                // keep input; user can retry
            } finally {
                _uiState.value = _uiState.value.copy(isSavingName = false)
            }
        }
    }

    fun uploadAvatar(filePath: String, mimeType: String) {
        viewModelScope.launch {
            try {
                matrixRepository.uploadAvatar(filePath, mimeType)
            } catch (_: Exception) {
                // surfaced by repository logs; profile keeps old avatar
            }
        }
    }
}
