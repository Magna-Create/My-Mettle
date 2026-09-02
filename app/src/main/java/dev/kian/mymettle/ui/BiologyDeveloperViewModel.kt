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
import dev.kian.mymettle.developer.NBio7BAcceptanceProgress
import dev.kian.mymettle.developer.NBio7BAcceptanceReport
import dev.kian.mymettle.developer.NBio7BClosureAcceptanceReport
import dev.kian.mymettle.developer.NBio7BClosureAcceptanceRunner
import dev.kian.mymettle.developer.NBio7CCapabilityAcceptanceReport
import dev.kian.mymettle.developer.NBio7CCapabilityAcceptanceRunner
import dev.kian.mymettle.developer.NBio7DCompleteAcceptanceReport
import dev.kian.mymettle.developer.NBio7DCompleteAcceptanceRunner
import dev.kian.mymettle.developer.NBioAdaptiveInferenceAcceptanceReport
import dev.kian.mymettle.developer.NBioAdaptiveInferenceAcceptanceRunner
import dev.kian.mymettle.workout.TrainingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val nBio7BAcceptanceRunning: Boolean = false,
    val nBio7BAcceptanceProgress: NBio7BAcceptanceProgress? = null,
    val nBio7BAcceptanceReport: NBio7BAcceptanceReport? = null,
    val nBio7BClosureReport: NBio7BClosureAcceptanceReport? = null,
    val adaptiveInferenceRunning: Boolean = false,
    val adaptiveInferenceProgress: NBio7BAcceptanceProgress? = null,
    val adaptiveInferenceReport: NBioAdaptiveInferenceAcceptanceReport? = null,
    val nBio7CCapabilityRunning: Boolean = false,
    val nBio7CCapabilityProgress: NBio7BAcceptanceProgress? = null,
    val nBio7CCapabilityReport: NBio7CCapabilityAcceptanceReport? = null,
    val nBio7DRunning: Boolean = false,
    val nBio7DProgress: NBio7BAcceptanceProgress? = null,
    val nBio7DReport: NBio7DCompleteAcceptanceReport? = null,
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
    private val nBio7BClosureRunner: NBio7BClosureAcceptanceRunner,
    private val adaptiveInferenceRunner: NBioAdaptiveInferenceAcceptanceRunner,
    private val nBio7CCapabilityRunner: NBio7CCapabilityAcceptanceRunner,
    private val nBio7DRunner: NBio7DCompleteAcceptanceRunner,
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

    private fun anyLongAcceptanceRunning(): Boolean =
        uiState.nBio7BAcceptanceRunning || uiState.adaptiveInferenceRunning || uiState.nBio7CCapabilityRunning ||
            uiState.nBio7DRunning || uiState.nBio6VerificationRunning || uiState.nBio6LiteVerificationRunning

    fun runNBio6DeviceVerification() {
        if (anyLongAcceptanceRunning()) return
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
        if (anyLongAcceptanceRunning()) return
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

    fun runNBio7BAcceptance() {
        if (anyLongAcceptanceRunning()) return
        viewModelScope.launch {
            uiState = uiState.copy(
                nBio7BAcceptanceRunning = true,
                nBio7BAcceptanceProgress = NBio7BAcceptanceProgress(0, 0, "Reading installed Room14 history"),
                nBio7BAcceptanceReport = null,
                nBio7BClosureReport = null,
                error = null,
            )
            runCatching {
                withContext(Dispatchers.Default) {
                    nBio7BClosureRunner.run { progress ->
                        viewModelScope.launch {
                            uiState = uiState.copy(nBio7BAcceptanceProgress = progress)
                        }
                    }
                }
            }.onSuccess { closure ->
                val backupStatus = closure.closureChecks.first { it.id == "native_backup_candidate_rows" }.status.storageValue
                uiState = uiState.copy(
                    nBio7BAcceptanceRunning = false,
                    nBio7BAcceptanceProgress = null,
                    nBio7BAcceptanceReport = closure.acceptance,
                    nBio7BClosureReport = closure,
                    message = "N-BIO-7B acceptance complete; isolated Native backup candidate coverage: $backupStatus.",
                )
                refresh()
            }.onFailure { error ->
                uiState = uiState.copy(nBio7BAcceptanceRunning = false, nBio7BAcceptanceProgress = null)
                showError(error)
            }
        }
    }

    fun nBio7BAcceptanceJson(): String = uiState.nBio7BClosureReport?.toJson()
        ?: error("Run N-BIO-7B real-history acceptance before exporting.")

    fun markNBio7BAcceptanceExported() {
        uiState = uiState.copy(message = "N-BIO-7B acceptance report exported.")
    }

    fun runAdaptiveInferenceAcceptance() {
        if (anyLongAcceptanceRunning()) return
        viewModelScope.launch {
            uiState = uiState.copy(
                adaptiveInferenceRunning = true,
                adaptiveInferenceProgress = NBio7BAcceptanceProgress(0, 0, "Reading installed Room14 adaptive-inference history"),
                adaptiveInferenceReport = null,
                error = null,
            )
            runCatching {
                withContext(Dispatchers.Default) {
                    adaptiveInferenceRunner.run { progress ->
                        viewModelScope.launch {
                            uiState = uiState.copy(adaptiveInferenceProgress = progress)
                        }
                    }
                }
            }.onSuccess { report ->
                uiState = uiState.copy(
                    adaptiveInferenceRunning = false,
                    adaptiveInferenceProgress = null,
                    adaptiveInferenceReport = report,
                    message = "N-BIO Adaptive Inference acceptance complete · safety ${if (report.safetyPassed) "PASS" else "FAIL"} · ${report.profiles.size} profile/side groups.",
                )
                refresh()
            }.onFailure { error ->
                uiState = uiState.copy(adaptiveInferenceRunning = false, adaptiveInferenceProgress = null)
                showError(error)
            }
        }
    }

    fun adaptiveInferenceJson(): String = uiState.adaptiveInferenceReport?.toJson()
        ?: error("Run N-BIO Adaptive Inference Acceptance before exporting.")

    fun markAdaptiveInferenceExported() {
        uiState = uiState.copy(message = "N-BIO Adaptive Inference acceptance report exported.")
    }

    fun runNBio7CCapabilityAcceptance() {
        if (anyLongAcceptanceRunning()) return
        viewModelScope.launch {
            uiState = uiState.copy(
                nBio7CCapabilityRunning = true,
                nBio7CCapabilityProgress = NBio7BAcceptanceProgress(0, 0, "Preparing N-BIO-7C structural capability acceptance"),
                nBio7CCapabilityReport = null,
                error = null,
            )
            runCatching {
                withContext(Dispatchers.Default) {
                    nBio7CCapabilityRunner.run { progress ->
                        viewModelScope.launch {
                            uiState = uiState.copy(nBio7CCapabilityProgress = progress)
                        }
                    }
                }
            }.onSuccess { report ->
                uiState = uiState.copy(
                    nBio7CCapabilityRunning = false,
                    nBio7CCapabilityProgress = null,
                    nBio7CCapabilityReport = report,
                    message = "N-BIO-7C capability acceptance complete · structural ${report.structuralVerdict.storageValue} · empirical ${report.empiricalAccuracyStatus.storageValue}.",
                )
                refresh()
            }.onFailure { error ->
                uiState = uiState.copy(nBio7CCapabilityRunning = false, nBio7CCapabilityProgress = null)
                showError(error)
            }
        }
    }

    fun nBio7CCapabilityJson(): String = uiState.nBio7CCapabilityReport?.toJson()
        ?: error("Run N-BIO 7C Capability Acceptance before exporting.")

    fun markNBio7CCapabilityExported() {
        uiState = uiState.copy(message = "N-BIO-7C capability acceptance report exported.")
    }

    fun runNBio7DAcceptance() {
        if (anyLongAcceptanceRunning()) return
        viewModelScope.launch {
            uiState = uiState.copy(
                nBio7DRunning = true,
                nBio7DProgress = NBio7BAcceptanceProgress(0, 0, "Preparing N-BIO-7D Demand & Dose Acceptance"),
                nBio7DReport = null,
                error = null,
            )
            runCatching {
                withContext(Dispatchers.Default) {
                    nBio7DRunner.run { progress ->
                        viewModelScope.launch {
                            uiState = uiState.copy(nBio7DProgress = progress)
                        }
                    }
                }
            }.onSuccess { report ->
                uiState = uiState.copy(
                    nBio7DRunning = false,
                    nBio7DProgress = null,
                    nBio7DReport = report,
                    message = "N-BIO-7D Demand & Dose Acceptance complete · structural ${report.structuralVerdict.storageValue} · empirical ${report.empiricalCalibrationStatus.storageValue}.",
                )
                refresh()
            }.onFailure { error ->
                uiState = uiState.copy(nBio7DRunning = false, nBio7DProgress = null)
                showError(error)
            }
        }
    }

    fun nBio7DAcceptanceJson(): String = uiState.nBio7DReport?.toJson()
        ?: error("Run N-BIO 7D Demand & Dose Acceptance before exporting.")

    fun markNBio7DAcceptanceExported() {
        uiState = uiState.copy(message = "N-BIO-7D Demand & Dose acceptance report exported.")
    }

    fun resetDatabase() {
        if (uiState.task.phase == BiologyTaskPhase.RUNNING || anyLongAcceptanceRunning()) return
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
            nBio7BClosureRunner = NBio7BClosureAcceptanceRunner(appContext, database),
            adaptiveInferenceRunner = NBioAdaptiveInferenceAcceptanceRunner(appContext, database),
            nBio7CCapabilityRunner = NBio7CCapabilityAcceptanceRunner(appContext, database),
            nBio7DRunner = NBio7DCompleteAcceptanceRunner(appContext, database),
        ) as T
    }
}
