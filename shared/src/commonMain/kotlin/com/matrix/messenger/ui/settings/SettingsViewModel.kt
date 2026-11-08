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
import com.matrix.messenger.ui.theme.ThemeManager
import com.matrix.messenger.ui.theme.ThemeMode
import com.matrix.messenger.ui.theme.ThemePalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val user: MatrixUser? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePalette: ThemePalette = ThemePalette.DEFAULT,
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
    val proxyMessage: String? = null,
    val isLoggingOut: Boolean = false,
    val isDeactivating: Boolean = false,
    val isChangingPassword: Boolean = false,
    val isRemovingIdentifier: Boolean = false,
    /** Third-party identifiers (email / msisdn) bound to the account. */
    val thirdPartyIds: List<com.matrix.messenger.data.model.ThirdPartyId> = emptyList(),
    /** One-shot confirmation for account actions (password change, identifier removal). */
    val accountMessage: String? = null,
    val isSavingPin: Boolean = false,
    val isSavingProxy: Boolean = false,
    val isSavingNotifications: Boolean = false,
    val isSavingNotificationRules: Boolean = false,
    val errorMessage: String? = null
)

sealed interface SettingsEvent {
    data object LoggedOut : SettingsEvent
    data object PinSaved : SettingsEvent
}

private const val PROXY_SAVED = "Прокси сохранён — применится после перезапуска приложения"

class SettingsViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val appLock: AppLockController = AppLockManager

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

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
            appLock.initialize()
            appLock.state.collect { lock ->
                _uiState.update { it.copy(pinEnabled = lock.enabled) }
                if (lock.error != null) reportError(lock.error)
            }
        }
        viewModelScope.launch {
            attempt("Не удалось загрузить настройки уведомлений") {
                val level = com.matrix.messenger.data.model.RoomNotificationLevel.fromStored(
                    SessionStore.readDefaultNotificationLevel()
                )
                _uiState.update { it.copy(defaultNotificationLevel = level) }
            }
        }
        viewModelScope.launch {
            attempt("Не удалось загрузить настройки прокси") {
                val proxy = SessionStore.readProxySettings()
                _uiState.update { it.copy(proxy = proxy) }
            }
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
            ThemeManager.palette.collect { palette ->
                _uiState.value = _uiState.value.copy(themePalette = palette)
            }
        }
        viewModelScope.launch {
            attempt("Не удалось загрузить настройки уведомлений") {
                val enabled = SessionStore.readNotificationsEnabled() ?: true
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
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
        viewModelScope.launch {
            attempt("Не удалось сохранить устройство ввода") {
                SessionStore.saveAudioInputDevice(deviceId)
                _uiState.update { it.copy(selectedAudioInput = deviceId) }
            }
        }
    }

    fun selectVideoDevice(deviceId: String) {
        viewModelScope.launch {
            attempt("Не удалось сохранить камеру") {
                SessionStore.saveVideoDevice(deviceId)
                _uiState.update { it.copy(selectedVideoDevice = deviceId) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            attempt("Не удалось сохранить тему") { ThemeManager.set(mode) }
        }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch {
            attempt("Не удалось сохранить палитру") { ThemeManager.set(palette) }
        }
    }

    fun updateProxy(proxy: ProxySettings?) {
        _uiState.value = _uiState.value.copy(proxy = proxy, proxyDirty = true)
    }

    fun saveProxy() {
        if (_uiState.value.isSavingProxy) return
        val current = _uiState.value.proxy
        _uiState.update { it.copy(isSavingProxy = true, proxyMessage = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                attempt("Не удалось сохранить прокси") {
                    SessionStore.saveProxySettings(current)
                    _uiState.update {
                        it.copy(proxyDirty = it.proxy != current, proxyMessage = PROXY_SAVED)
                    }
                }
            } finally {
                _uiState.update { it.copy(isSavingProxy = false) }
            }
        }
    }

    private suspend fun loadPushRules() {
        attempt("Не удалось загрузить правила уведомлений") {
            val mode = matrixRepository.getAccountNotificationMode()
            val keywords = matrixRepository.getKeywords()
            val rules = matrixRepository.getPushRulesDebug()
            val pushers = matrixRepository.getPushersDebug()
            _uiState.update {
                it.copy(accountMode = mode, keywords = keywords, pushRules = rules, pushers = pushers)
            }
        }
    }

    private fun changeNotificationRule(block: suspend () -> Unit) {
        if (_uiState.value.isSavingNotificationRules) return
        _uiState.update { it.copy(isSavingNotificationRules = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                attempt("Не удалось изменить настройки уведомлений") { block() }
            } finally {
                _uiState.update { it.copy(isSavingNotificationRules = false) }
            }
        }
    }

    fun setAccountMode(mode: com.matrix.messenger.data.repository.AccountNotificationMode) {
        changeNotificationRule {
            matrixRepository.setAccountNotificationMode(mode)
            _uiState.update { it.copy(accountMode = mode) }
            loadPushRules()
        }
    }

    fun addKeyword(keyword: String) {
        if (keyword.isBlank()) return
        changeNotificationRule {
            matrixRepository.addKeyword(keyword.trim())
            loadPushRules()
        }
    }

    fun removeKeyword(ruleId: String) {
        changeNotificationRule {
            matrixRepository.removeKeyword(ruleId)
            loadPushRules()
        }
    }

    fun toggleRule(kind: net.folivo.trixnity.core.model.push.PushRuleKind, ruleId: String, enabled: Boolean) {
        changeNotificationRule {
            matrixRepository.setRuleEnabled(kind, ruleId, enabled)
            loadPushRules()
        }
    }

    fun togglePushRulesExpanded() {
        _uiState.update { it.copy(pushRulesExpanded = !it.pushRulesExpanded) }
    }

    fun setDefaultNotificationLevel(level: com.matrix.messenger.data.model.RoomNotificationLevel) {
        changeNotificationRule {
            matrixRepository.setDefaultNotificationLevel(level)
            _uiState.update { it.copy(defaultNotificationLevel = level) }
        }
    }

    fun setPin(pin: String, oldPin: String? = null) = changePinSettings { appLock.setPin(pin, oldPin) }
    fun changePin(oldPin: String, newPin: String) = changePinSettings { appLock.changePin(oldPin, newPin) }
    fun clearPin(oldPin: String) = changePinSettings { appLock.disablePin(oldPin) }

    private fun changePinSettings(block: suspend () -> PinOperationResult) {
        if (_uiState.value.isSavingPin) return
        _uiState.update { it.copy(isSavingPin = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                when (val result = block()) {
                    PinOperationResult.Success -> eventChannel.send(SettingsEvent.PinSaved)
                    PinOperationResult.WrongPin -> reportError("Неверный PIN")
                    is PinOperationResult.Throttled -> reportError("Слишком много попыток. Подождите перед повтором")
                    is PinOperationResult.Error -> reportError(result.message)
                }
            } finally {
                _uiState.update { it.copy(isSavingPin = false) }
            }
        }
    }

    fun logout() {
        if (_uiState.value.isLoggingOut) return
        _uiState.update { it.copy(isLoggingOut = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                attempt("Не удалось выйти из аккаунта. Повторите попытку") {
                    matrixRepository.logout()
                    eventChannel.send(SettingsEvent.LoggedOut)
                }
            } finally {
                _uiState.update { it.copy(isLoggingOut = false) }
            }
        }
    }

    /**
     * Permanently deactivates the account (irreversible, POST /account/deactivate).
     * On success emits the same [SettingsEvent.LoggedOut] as the logout flow so
     * navigation resets to the login screen.
     */
    fun deactivateAccount(eraseData: Boolean, password: String) {
        if (_uiState.value.isDeactivating) return
        _uiState.update { it.copy(isDeactivating = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                attempt("Не удалось деактивировать аккаунт. Повторите попытку") {
                    matrixRepository.deactivateAccount(eraseData) { password }
                    eventChannel.send(SettingsEvent.LoggedOut)
                }
            } finally {
                _uiState.update { it.copy(isDeactivating = false) }
            }
        }
    }

    /**
     * Changes the account password (UIA walks with the current password).
     * With [logoutDevices] the session is invalidated server-side: emits the
     * same [SettingsEvent.LoggedOut] as the logout flow so navigation resets.
     */
    fun changePassword(newPassword: String, currentPassword: String, logoutDevices: Boolean) {
        if (_uiState.value.isChangingPassword) return
        _uiState.update { it.copy(isChangingPassword = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                attempt("Не удалось изменить пароль. Повторите попытку") {
                    matrixRepository.changePassword(
                        newPassword = newPassword,
                        currentPasswordProvider = { currentPassword },
                        logoutDevices = logoutDevices
                    )
                    if (logoutDevices) {
                        eventChannel.send(SettingsEvent.LoggedOut)
                    } else {
                        _uiState.update { it.copy(accountMessage = "Пароль изменён") }
                    }
                }
            } finally {
                _uiState.update { it.copy(isChangingPassword = false) }
            }
        }
    }

    /** Loads the account's third-party identifiers (for the Email и телефон dialog). */
    fun loadThirdPartyIdentifiers() {
        viewModelScope.launch {
            attempt("Не удалось загрузить привязанные адреса") {
                val ids = matrixRepository.getThirdPartyIdentifiers()
                _uiState.update { it.copy(thirdPartyIds = ids) }
            }
        }
    }

    fun removeThirdPartyIdentifier(address: String) {
        if (_uiState.value.isRemovingIdentifier) return
        _uiState.update { it.copy(isRemovingIdentifier = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                attempt("Не удалось отвязать адрес") {
                    matrixRepository.removeThirdPartyIdentifier(address)
                    val ids = matrixRepository.getThirdPartyIdentifiers()
                    _uiState.update { it.copy(thirdPartyIds = ids, accountMessage = "Удалено") }
                }
            } finally {
                _uiState.update { it.copy(isRemovingIdentifier = false) }
            }
        }
    }

    fun consumeAccountMessage() {
        if (_uiState.value.accountMessage != null) {
            _uiState.value = _uiState.value.copy(accountMessage = null)
        }
    }

    fun consumeProxyMessage() {
        if (_uiState.value.proxyMessage != null) {
            _uiState.value = _uiState.value.copy(proxyMessage = null)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        if (_uiState.value.isSavingNotifications) return
        _uiState.update { it.copy(isSavingNotifications = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                attempt("Не удалось сохранить настройки уведомлений") {
                    matrixRepository.setNotificationsEnabled(enabled)
                    _uiState.update { it.copy(notificationsEnabled = enabled) }
                }
            } finally {
                _uiState.update { it.copy(isSavingNotifications = false) }
            }
        }
    }

    fun consumeError() { _uiState.update { it.copy(errorMessage = null) } }

    private fun reportError(message: String) { _uiState.update { it.copy(errorMessage = message) } }

    private suspend fun attempt(message: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        reportError(message)
        false
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
                attempt("Не удалось сохранить имя") { matrixRepository.setDisplayName(name) }
            } finally {
                _uiState.update { it.copy(isSavingName = false) }
            }
        }
    }

    fun uploadAvatar(filePath: String, mimeType: String) {
        viewModelScope.launch {
            attempt("Не удалось загрузить аватар") { matrixRepository.uploadAvatar(filePath, mimeType) }
        }
    }
}
