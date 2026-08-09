package dev.kian.mymettle.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.local.DatabaseProvider
import dev.kian.mymettle.history.HistoryRepository
import dev.kian.mymettle.history.HistorySession
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loading: Boolean = true,
    val sessions: List<HistorySession> = emptyList(),
    val error: String? = null,
)

class HistoryViewModel(
    private val repository: HistoryRepository,
) : ViewModel() {
    var uiState by mutableStateOf(HistoryUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repository.recent() }
                .onSuccess { sessions -> uiState = HistoryUiState(loading = false, sessions = sessions) }
                .onFailure { error ->
                    uiState = uiState.copy(
                        loading = false,
                        error = error.message ?: error::class.java.simpleName,
                    )
                }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }
}

class HistoryViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = DatabaseProvider.get(appContext)
        return HistoryViewModel(HistoryRepository(database)) as T
    }
}
