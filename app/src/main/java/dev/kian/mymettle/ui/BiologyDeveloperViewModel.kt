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
import dev.kian.mymettle.developer.ContextDeveloperDiagnosticsRepository
import dev.kian.mymettle.developer.ContextDeveloperSnapshot
import dev.kian.mymettle.developer.NBio6DeviceVerificationReport
import dev.kian.mymettle.developer.NBio6DeviceVerificationRepository
import dev.kian.mymettle.developer.NBio6LiteBackupVerificationReport
import dev.kian.mymettle.workout.TrainingMode
import kotlinx.coroutines.launch

data class BiologyDeveloperUiState(
    val loading: Boolean = true,
    val snapshot: BiologyDeveloperSnapshot? = null,
    val contextSnapshot: ContextDeveloperSnapshot? = null,
    val selectedDay: String? = null,
    val selectedMode: TrainingMode = TrainingMode.B,
    val task: BiologyTaskState = BiologyTaskState(),
    val nBio6VerificationRunning: Boolean = false,
    val nBio6DeviceReport: NBio6DeviceVerificationReport? = null,
    val nBio6LiteVerificationRunning: Boolean = false,
    val nBio6LiteReport: NBio6LiteBackupVerificationReport? = null,
    val resetComplete: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class BiologyDeveloperViewModel(
    private val appContext: Context,
    private val database: MyMettleDatabase,
    private val repository: BiologyDeveloperRepository,
    private val contextDiagnosticsRepository: ContextDeveloperDiagnosticsRepository,
    private val nBio6Verifier: NBio6DeviceVerificationRepository,
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
            runCatching { repository.snapshot() to contextDiagnosticsRepository.snapshot() }
                .onSuccess { (snapshot, contextSnapshot) ->
                    val selectedDay = uiState.selectedDay
                        ?.takeIf { selected -> snapshot.days.any { it.day == selected } }
                        ?: snapshot.days.firstOrNull()?.day
                    uiState = uiState.copy(
                        loading = false,
                        snapshot = snapshot,
                        contextSnapshot = contextSnapshot,
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

    fun runNBio6DeviceVerification() {
        if (uiState.nBio6VerificationRunning || uiState.nBio6LiteVerificationRunning) return
        viewModelScope.launch {
            uiState = uiState.copy(nBio6VerificationRunning = true, error = null)
            runCatching { nBio6Verifier.runAutomatedChecks() }
                .onSuccess { report ->
                    uiState = uiState.copy(nBio6VerificationRunning = false, nBio6DeviceReport = report)
                }
                .onFailure { error ->
                    uiState = uiState.copy(nBio6VerificationRunning = false)
                    showError(error)
                }
        }
    }

    fun verifyLiteBackup(fileName: String, json: String) {
        if (uiState.nBio6VerificationRunning || uiState.nBio6LiteVerificationRunning) return
        viewModelScope.launch {
            uiState = uiState.copy(nBio6LiteVerificationRunning = true, error = null)
            runCatching { nBio6Verifier.verifyLiteBackup(fileName, json) }
                .onSuccess { report ->
                    uiState = uiState.copy(nBio6LiteVerificationRunning = false, nBio6LiteReport = report)
                }
                .onFailure { error ->
                    uiState = uiState.copy(nBio6LiteVerificationRunning = false)
                    showError(error)
                }
        }
    }

    fun nBio6ClosureJson(): String {
        check(uiState.nBio6DeviceReport != null || uiState.nBio6LiteReport != null) {
            "Run at least one N-BIO-6 closure check before exporting."
        }
        return nBio6Verifier.combinedJson(uiState.nBio6DeviceReport, uiState.nBio6LiteReport)
    }

    fun markNBio6Exported() {
        uiState = uiState.copy(message = "N-BIO-6 closure report exported.")
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
            contextDiagnosticsRepository = ContextDeveloperDiagnosticsRepository(database),
            nBio6Verifier = NBio6DeviceVerificationRepository(appContext),
        ) as T
    }
}
