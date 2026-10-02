package com.matrix.messenger.data.model

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

sealed class UiEvent {
    data class NavigateToChat(val roomId: String) : UiEvent()
    data object NavigateBack : UiEvent()
    data object NavigateToHome : UiEvent()
    data object NavigateToLogin : UiEvent()
    data class ShowSnackbar(val message: String) : UiEvent()
    data class ShowDialog(val title: String, val message: String) : UiEvent()
}
