package com.matrix.messenger.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.UiEvent
import com.matrix.messenger.data.model.UiState
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.platform.logError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class LoginUiState(
    val homeServer: String = "matrix.org",
    val username: String = "",
    val password: String = "",
    val useAccessToken: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class LoginViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiStateSealed = MutableStateFlow<UiState<String>>(UiState.Loading)
    val uiStateSealed: StateFlow<UiState<String>> = _uiStateSealed.asStateFlow()

    private val _events = Channel<UiEvent>()
    val events = _events.receiveAsFlow()

    fun onHomeServerChange(server: String) {
        _uiState.value = _uiState.value.copy(homeServer = server)
    }

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun onUseAccessTokenChange(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            useAccessToken = enabled,
            password = ""
        )
    }

    fun login() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isLoading) return@launch

            if (state.username.isBlank() || state.password.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    error = if (state.useAccessToken) {
                        "Введите user ID и access token"
                    } else {
                        "Введите логин и пароль"
                    }
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            _uiStateSealed.value = UiState.Loading

            try {
                val result = withTimeout(LOGIN_TIMEOUT_MS) {
                    if (state.useAccessToken) {
                        matrixRepository.loginWithToken(
                            homeServer = state.homeServer,
                            userId = state.username.trim(),
                            accessToken = state.password
                        )
                    } else {
                        matrixRepository.login(
                            homeServer = state.homeServer,
                            username = state.username.trim(),
                            password = state.password
                        )
                    }
                }

                when (result) {
                    is LoginResult.Success -> {
                        _uiStateSealed.value = UiState.Success(result.userId)
                        _events.send(UiEvent.NavigateToHome)
                    }
                    is LoginResult.Error -> showLoginError(result.message)
                }
            } catch (e: TimeoutCancellationException) {
                showLoginError("Сервер не ответил за 60 секунд. Проверьте адрес сервера и подключение к сети.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkageError) {
                logError("Login", "Matrix client dependency failed to load", e)
                showLoginError("Не удалось загрузить компонент Matrix-клиента. Переустановите приложение.")
            } catch (e: Exception) {
                showLoginError(e.message ?: "Не удалось выполнить вход")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun showLoginError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
        _uiStateSealed.value = UiState.Error(message)
        _events.send(UiEvent.ShowSnackbar(message))
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val LOGIN_TIMEOUT_MS = 60_000L
    }
}
