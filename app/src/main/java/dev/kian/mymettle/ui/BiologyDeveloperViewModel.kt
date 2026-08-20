package dev.kian.mymettle.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.local.DatabaseProvider
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.developer.BiologyDeveloperRepository
import dev.kian.mymettle.developer.BiologyDeveloperSnapshot
import dev.kian.mymettle.developer.BiologyTaskController
import dev.kian.mymettle.developer.BiologyTaskPhase
import dev.kian.mymettle.developer.BiologyTaskState
import dev.kian.mymettle.workout.TrainingMode
import kotlinx.coroutines.launch

data class BiologyDeveloperUiState(
    val loading: Boolean = true,
    val snapshot: BiologyDeveloperSnapshot? = null,
    val selectedDay: String? = null,
    val selectedMode: TrainingMode = TrainingMode.B,
    val task: BiologyTaskState = BiologyTaskState(),
    val resetComplete: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class BiologyDeveloperViewModel(
    private val appContext: Context,
    private val database: MyMettleDatabase,
    private val repository: BiologyDeveloperRepository,
) : ViewModel() {
    var uiState by mutableStateOf(BiologyDeveloperUiState())
        private set

    init {
        viewModelScope.launch {
            BiologyTaskController.state.collect { task ->
                val shouldRefresh = task.phase == BiologyTaskPhase.SUCCEEDED &&
                    uiState.task.phase == BiologyTaskPhase.RUNNING
                uiState = uiState.copy(task = task)
                if (shouldRefresh) refresh()
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repository.snapshot() }
                .onSuccess { snapshot ->
                    val selectedDay = uiState.selectedDay
                        ?.takeIf { selected -> snapshot.days.any { it.day == selected } }
                        ?: snapshot.days.firstOrNull()?.day
                    uiState = uiState.copy(
                        loading = false,
                        snapshot = snapshot,
                        selectedDay = selectedDay,
                    )
                }
                .onFailure(::showError)
        }
    }

    fun selectDay(day: String) {
        uiState = uiState.copy(selectedDay = day)
    }

    fun selectMode(mode: TrainingMode) {
        uiState = uiState.copy(selectedMode = mode)
    }

    fun recompute() {
        BiologyTaskController.recompute(database)
    }

    fun dismissTaskResult() {
        BiologyTaskController.dismissResult()
    }

    fun diagnosticJson(): String {
        val snapshot = uiState.snapshot ?: error("Biological diagnostics have not loaded yet.")
        return repository.diagnosticJson(snapshot)
    }

    fun markExported() {
        uiState = uiState.copy(message = "Diagnostic JSON exported.")
    }

    fun resetDatabase() {
        if (uiState.task.phase == BiologyTaskPhase.RUNNING) return
        viewModelScope.launch {
            runCatching { DatabaseProvider.resetDevelopmentDatabase(appContext) }
                .onSuccess { uiState = uiState.copy(resetComplete = true) }
                .onFailure(::showError)
        }
    }

    fun dismissMessage() {
        uiState = uiState.copy(message = null)
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    fun reportError(error: Throwable) {
        showError(error)
    }

    private fun showError(error: Throwable) {
        uiState = uiState.copy(
            loading = false,
            error = error.message ?: error::class.java.simpleName,
        )
    }
}

class BiologyDeveloperViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = DatabaseProvider.get(appContext)
        return BiologyDeveloperViewModel(
            appContext = appContext,
            database = database,
            repository = BiologyDeveloperRepository(database),
        ) as T
    }
}
