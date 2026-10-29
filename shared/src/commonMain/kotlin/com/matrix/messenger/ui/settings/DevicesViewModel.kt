package com.matrix.messenger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.DeviceInfo
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DevicesUiState(
    val devices: List<DeviceInfo> = emptyList(),
    val currentDeviceId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class DevicesViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val devices = matrixRepository.getDevices()
                val current = matrixRepository.currentDeviceId()
                _uiState.value = DevicesUiState(
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
                matrixRepository.deleteDevice(deviceId)
                _uiState.value = _uiState.value.copy(
                    devices = _uiState.value.devices.filterNot { it.deviceId == deviceId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Не удалось удалить устройство")
            }
        }
    }
}
