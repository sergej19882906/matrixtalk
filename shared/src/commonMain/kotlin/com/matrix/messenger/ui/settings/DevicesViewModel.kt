package com.matrix.messenger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.DeviceInfo
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.model.VerificationUiState
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DevicesUiState(
    val devices: List<DeviceInfo> = emptyList(),
    val currentDeviceId: String? = null,
    val keyBackupVersion: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Recovery key to show once after a successful bootstrap. */
    val recoveryKey: String? = null,
    val isBootstrapping: Boolean = false
)

class DevicesViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    val verificationState: StateFlow<VerificationUiState> = matrixRepository.verificationState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VerificationUiState.Idle)

    // UIA password prompt: visibility state for the dialog
    private val _passwordPrompt = MutableStateFlow<kotlinx.coroutines.CompletableDeferred<String?>?>(null)
    private val passwordPromptVisibility = MutableStateFlow(false)
    val showPasswordPrompt: StateFlow<Boolean> = passwordPromptVisibility

    init {
        viewModelScope.launch {
            _passwordPrompt.collect { prompt ->
                passwordPromptVisibility.value = prompt != null
            }
        }
        refresh()
        viewModelScope.launch {
            matrixRepository.getKeyBackupVersionFlow().collect { version ->
                _uiState.value = _uiState.value.copy(keyBackupVersion = version)
            }
        }
    }

    fun submitPassword(password: String?) {
        _passwordPrompt.value?.complete(password)
        _passwordPrompt.value = null
    }

    private suspend fun promptPassword(): String? {
        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        _passwordPrompt.value = deferred
        return deferred.await()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val devices = matrixRepository.getDevices()
                val current = matrixRepository.currentDeviceId()
                _uiState.value = _uiState.value.copy(
                    devices = devices.sortedByDescending { it.deviceId == current },
                    currentDeviceId = current,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Не удалось загрузить устройства"
                )
            }
        }
    }

    fun bootstrapKeyBackup() {
        _uiState.value = _uiState.value.copy(isBootstrapping = true)
        viewModelScope.launch {
            try {
                val recoveryKey = matrixRepository.bootstrapKeyBackup(::promptPassword)
                _uiState.value = _uiState.value.copy(
                    isBootstrapping = false,
                    recoveryKey = recoveryKey
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = _uiState.value.copy(isBootstrapping = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBootstrapping = false,
                    error = e.message ?: "Не удалось настроить резервное копирование"
                )
            }
        }
    }

    fun dismissRecoveryKey() {
        _uiState.value = _uiState.value.copy(recoveryKey = null)
    }

    fun verify(deviceId: String) {
        viewModelScope.launch {
            try {
                matrixRepository.startDeviceVerification(deviceId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Не удалось начать верификацию: ${e.message}"
                )
            }
        }
    }

    fun confirmVerification(matches: Boolean) {
        viewModelScope.launch { matrixRepository.confirmVerification(matches) }
    }

    fun cancelVerification() {
        viewModelScope.launch { matrixRepository.cancelVerification() }
    }

    fun rename(deviceId: String, newName: String) {
        viewModelScope.launch {
            try {
                matrixRepository.renameDevice(deviceId, newName)
                _uiState.value = _uiState.value.copy(
                    devices = _uiState.value.devices.map {
                        if (it.deviceId == deviceId) it.copy(displayName = newName) else it
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Ошибка переименования: ${e.message}")
            }
        }
    }

    fun delete(deviceId: String) {
        viewModelScope.launch {
            try {
                matrixRepository.deleteDevice(deviceId, ::promptPassword)
                refresh()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // user cancelled the password prompt
            } catch (e: Throwable) {
                com.matrix.messenger.platform.logError("Devices", "Device deletion failed", e)
                _uiState.value = _uiState.value.copy(error = e.message ?: "Не удалось удалить устройство")
                _events.send(UiEvent.ShowSnackbar(e.message ?: "Не удалось удалить устройство"))
            }
        }
    }
}
