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
    val saving: Boolean = false,
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
                .onFailure(::showError)
        }
    }

    fun updateSet(
        sessionId: String,
        sessionExerciseId: String,
        setId: String,
        load: Double?,
        reps: Int?,
        durationSeconds: Int?,
        distanceMetres: Double?,
        onSaved: (HistorySession) -> Unit,
    ) {
        mutate {
            repository.updateSet(
                sessionId = sessionId,
                sessionExerciseId = sessionExerciseId,
                setId = setId,
                load = load,
                reps = reps,
                durationSeconds = durationSeconds,
                distanceMetres = distanceMetres,
            )
        } onSuccess@{ updated ->
            replace(updated)
            onSaved(updated)
        }
    }

    fun saveSessionReview(
        sessionId: String,
        exerciseOrder: Int?,
        organisation: Int?,
        pacing: Int?,
        delayImpact: Int?,
        note: String?,
        onSaved: (HistorySession) -> Unit,
    ) {
        mutate {
            repository.saveSessionReview(
                sessionId = sessionId,
                exerciseOrder = exerciseOrder,
                organisation = organisation,
                pacing = pacing,
                delayImpact = delayImpact,
                note = note,
            )
        } onSuccess@{ updated ->
            replace(updated)
            onSaved(updated)
        }
    }

    fun discardSession(sessionId: String, onDiscarded: () -> Unit) {
        viewModelScope.launch {
            uiState = uiState.copy(saving = true, error = null)
            runCatching { repository.discardSession(sessionId) }
                .onSuccess {
                    uiState = uiState.copy(
                        saving = false,
                        sessions = uiState.sessions.filterNot { it.session.id == sessionId },
                    )
                    onDiscarded()
                }
                .onFailure(::showError)
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun mutate(
        block: suspend () -> HistorySession,
    ): MutationHandle {
        val handle = MutationHandle()
        viewModelScope.launch {
            uiState = uiState.copy(saving = true, error = null)
            runCatching { block() }
                .onSuccess { updated ->
                    uiState = uiState.copy(saving = false)
                    handle.success?.invoke(updated)
                }
                .onFailure(::showError)
        }
        return handle
    }

    private infix fun MutationHandle.onSuccess(callback: (HistorySession) -> Unit) {
        success = callback
    }

    private fun replace(updated: HistorySession) {
        uiState = uiState.copy(
            sessions = uiState.sessions.map { current ->
                if (current.session.id == updated.session.id) updated else current
            },
        )
    }

    private fun showError(error: Throwable) {
        uiState = uiState.copy(
            loading = false,
            saving = false,
            error = error.message ?: error::class.java.simpleName,
        )
    }

    private class MutationHandle {
        var success: ((HistorySession) -> Unit)? = null
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
