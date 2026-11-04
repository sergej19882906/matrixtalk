package com.matrix.messenger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.ProxySettings
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.data.repository.SessionStore
import com.matrix.messenger.platform.MediaDeviceInfo
import com.matrix.messenger.platform.listAudioInputDevices
import com.matrix.messenger.platform.listVideoInputDevices
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
    val isSavingName: Boolean = false,
    val audioInputDevices: List<MediaDeviceInfo> = emptyList(),
    val videoDevices: List<MediaDeviceInfo> = emptyList(),
    val selectedAudioInput: String? = null,
    val selectedVideoDevice: String? = null,
    /** Server-side key backup; null when no backup exists. */
    val keyBackupVersion: String? = null,
    /** Background push status (Android); null on desktop. */
    val pushStatus: String? = null,
    /** App lock configured (PIN). */
    val pinEnabled: Boolean = false,
    /** Default notification level applied to newly joined rooms. */
    val defaultNotificationLevel: com.matrix.messenger.data.model.RoomNotificationLevel =
        com.matrix.messenger.data.model.RoomNotificationLevel.ALL_MESSAGES,
    /** Account-wide notification mode (server rules). */
    val accountMode: com.matrix.messenger.data.repository.AccountNotificationMode =
        com.matrix.messenger.data.repository.AccountNotificationMode.ALL_MESSAGES,
    /** Keyword notification rules (content rules). */
    val keywords: List<net.folivo.trixnity.core.model.push.PushRule.Content> = emptyList(),
    /** Server push rules (debug list). */
    val pushRules: List<com.matrix.messenger.data.repository.PushRuleView> = emptyList(),
    /** Registered pushers (debug list). */
    val pushers: List<com.matrix.messenger.data.repository.PushersView> = emptyList(),
    val pushRulesExpanded: Boolean = false,
    /** Outbound proxy (applied after an app restart). */
    val proxy: ProxySettings? = null,
    val proxyDirty: Boolean = false,
    /** One-shot confirmation for the proxy save. */
    val proxyMessage: String? = null
)

private const val PROXY_SAVED = "Прокси сохранён — применится после перезапуска приложения"

class SettingsViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                keyBackupVersion = runCatching { matrixRepository.getKeyBackupStatus() }.getOrNull()?.version
            )
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pushStatus = runCatching { com.matrix.messenger.platform.pushServiceStatusText() }.getOrNull()
            )
        }
        viewModelScope.launch { loadPushRules() }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pinEnabled = runCatching { SessionStore.readPinHash() } != null,
                defaultNotificationLevel = runCatching {
                    com.matrix.messenger.data.model.RoomNotificationLevel.fromStored(
                        SessionStore.readDefaultNotificationLevel()
                    )
                }.getOrDefault(com.matrix.messenger.data.model.RoomNotificationLevel.ALL_MESSAGES)
            )
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                proxy = runCatching { SessionStore.readProxySettings() }.getOrNull()
            )
        }
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
        viewModelScope.launch {
            // Media devices (desktop: real enumeration; Android: cameras only)
            val audio = listAudioInputDevices()
            val video = listVideoInputDevices()
            val savedAudio = SessionStore.readAudioInputDevice()
            val savedVideo = SessionStore.readVideoDevice()
            _uiState.value = _uiState.value.copy(
                audioInputDevices = audio,
                videoDevices = video,
                // keep the saved selection only if the device is still present
                selectedAudioInput = savedAudio?.takeIf { saved -> audio.any { it.id == saved } },
                selectedVideoDevice = savedVideo?.takeIf { saved -> video.any { it.id == saved } }
            )
        }
    }

    fun selectAudioInputDevice(deviceId: String) {
        _uiState.value = _uiState.value.copy(selectedAudioInput = deviceId)
        viewModelScope.launch { SessionStore.saveAudioInputDevice(deviceId) }
    }

    fun selectVideoDevice(deviceId: String) {
        _uiState.value = _uiState.value.copy(selectedVideoDevice = deviceId)
        viewModelScope.launch { SessionStore.saveVideoDevice(deviceId) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { ThemeManager.set(mode) }
    }

    fun updateProxy(proxy: ProxySettings?) {
        _uiState.value = _uiState.value.copy(proxy = proxy, proxyDirty = true)
    }

    fun saveProxy() {
        val current = _uiState.value.proxy
        viewModelScope.launch {
            runCatching { SessionStore.saveProxySettings(current) }
            _uiState.value = _uiState.value.copy(proxyDirty = false, proxyMessage = PROXY_SAVED)
        }
    }

    private suspend fun loadPushRules() {
        runCatching {
            val mode = matrixRepository.getAccountNotificationMode()
            val keywords = matrixRepository.getKeywords()
            val rules = matrixRepository.getPushRulesDebug()
            val pushers = matrixRepository.getPushersDebug()
            _uiState.value = _uiState.value.copy(
                accountMode = mode, keywords = keywords,
                pushRules = rules, pushers = pushers
            )
        }
    }

    fun setAccountMode(mode: com.matrix.messenger.data.repository.AccountNotificationMode) {
        viewModelScope.launch {
            runCatching { matrixRepository.setAccountNotificationMode(mode) }
            _uiState.value = _uiState.value.copy(accountMode = mode)
            loadPushRules()
        }
    }

    fun addKeyword(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            runCatching { matrixRepository.addKeyword(keyword.trim()) }
            loadPushRules()
        }
    }

    fun removeKeyword(ruleId: String) {
        viewModelScope.launch {
            runCatching { matrixRepository.removeKeyword(ruleId) }
            loadPushRules()
        }
    }

    fun toggleRule(kind: net.folivo.trixnity.core.model.push.PushRuleKind, ruleId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { matrixRepository.setRuleEnabled(kind, ruleId, enabled) }
            loadPushRules()
        }
    }

    fun togglePushRulesExpanded() {
        _uiState.value = _uiState.value.copy(pushRulesExpanded = !_uiState.value.pushRulesExpanded)
    }

    fun setDefaultNotificationLevel(level: com.matrix.messenger.data.model.RoomNotificationLevel) {
        viewModelScope.launch {
            runCatching { matrixRepository.setDefaultNotificationLevel(level) }
            _uiState.value = _uiState.value.copy(defaultNotificationLevel = level)
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            runCatching { SessionStore.savePinHash(PinHash.of(pin)) }
            _uiState.value = _uiState.value.copy(pinEnabled = true)
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            runCatching { SessionStore.savePinHash(null) }
            _uiState.value = _uiState.value.copy(pinEnabled = false)
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { matrixRepository.logout() }
        }
    }

    fun consumeProxyMessage() {
        if (_uiState.value.proxyMessage != null) {
            _uiState.value = _uiState.value.copy(proxyMessage = null)
        }
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
