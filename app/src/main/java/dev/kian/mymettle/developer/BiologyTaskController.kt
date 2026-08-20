package dev.kian.mymettle.developer

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.inference.RoomInferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BiologyTaskPhase { IDLE, RUNNING, SUCCEEDED, FAILED }

data class BiologyTaskState(
    val phase: BiologyTaskPhase = BiologyTaskPhase.IDLE,
    val label: String? = null,
    val detail: String? = null,
)

/**
 * Process-level controller for an explicitly user-started inference replay. It survives navigation
 * away from Settings and exposes one visible state stream for both the developer screen and the
 * app-wide task lozenge.
 */
object BiologyTaskController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(BiologyTaskState())
    private var activeJob: Job? = null

    val state: StateFlow<BiologyTaskState> = mutableState.asStateFlow()

    @Synchronized
    fun recompute(database: MyMettleDatabase) {
        if (activeJob?.isActive == true) return
        mutableState.value = BiologyTaskState(
            phase = BiologyTaskPhase.RUNNING,
            label = "Recomputing biological state",
            detail = "Replaying completed set evidence",
        )
        activeJob = scope.launch {
            runCatching { RoomInferenceRepository(database).recomputeFromRawHistory() }
                .onSuccess { snapshot ->
                    mutableState.value = BiologyTaskState(
                        phase = BiologyTaskPhase.SUCCEEDED,
                        label = "Biological state recomputed",
                        detail = "${snapshot.run.evidenceSetCount} evidence sets · " +
                            "${snapshot.stimulusEstimates.size} stimulus estimates · " +
                            "${snapshot.exerciseTranslationStates.size} performance anchors",
                    )
                }
                .onFailure { error ->
                    mutableState.value = BiologyTaskState(
                        phase = BiologyTaskPhase.FAILED,
                        label = "Biological recompute failed",
                        detail = error.message ?: error::class.java.simpleName,
                    )
                }
        }
    }

    fun dismissResult() {
        if (mutableState.value.phase != BiologyTaskPhase.RUNNING) {
            mutableState.value = BiologyTaskState()
        }
    }
}
