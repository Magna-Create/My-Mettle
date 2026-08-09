package dev.kian.mymettle.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.settings.RestTimerPreferences
import dev.kian.mymettle.data.settings.SettingsStore
import dev.kian.mymettle.timer.RestTimerController
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val restTimer: RestTimerPreferences = RestTimerPreferences(),
    val error: String? = null,
)

class SettingsViewModel(
    private val store: SettingsStore,
    private val restTimer: RestTimerController,
) : ViewModel() {
    var uiState by mutableStateOf(SettingsUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { store.restTimerPreferences() }
                .onSuccess { uiState = SettingsUiState(loading = false, restTimer = it) }
                .onFailure(::showError)
        }
    }

    fun update(transform: (RestTimerPreferences) -> RestTimerPreferences) {
        val next = transform(uiState.restTimer)
        uiState = uiState.copy(restTimer = next, saving = true, error = null)
        viewModelScope.launch {
            runCatching { store.writeRestTimer(next) }
                .onSuccess { uiState = uiState.copy(saving = false) }
                .onFailure(::showError)
        }
    }

    fun testRestAlert() {
        restTimer.testAlert()
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun showError(error: Throwable) {
        uiState = uiState.copy(
            loading = false,
            saving = false,
            error = error.message ?: error::class.java.simpleName,
        )
    }
}

class SettingsViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(
            store = SettingsStore(appContext),
            restTimer = RestTimerController.get(appContext),
        ) as T
}
