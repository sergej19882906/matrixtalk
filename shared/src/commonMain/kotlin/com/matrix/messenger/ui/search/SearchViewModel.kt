package com.matrix.messenger.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.SearchResultItem
import com.matrix.messenger.data.repository.MatrixRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val isSearching: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null
)

class SearchViewModel(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query, searched = false)
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            try {
                val results = matrixRepository.searchMessages(query.trim())
                _uiState.value = _uiState.value.copy(
                    results = results,
                    isSearching = false,
                    searched = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searched = true,
                    error = e.message ?: "Ошибка поиска"
                )
            }
        }
    }
}
