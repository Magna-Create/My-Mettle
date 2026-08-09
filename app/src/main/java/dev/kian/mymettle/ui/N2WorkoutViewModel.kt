package dev.kian.mymettle.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.local.DatabaseProvider
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.data.migration.LegacyImportReport
import dev.kian.mymettle.data.migration.LegacyV6Importer
import dev.kian.mymettle.timer.RestTimerController
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.NativeWorkoutPlan
import dev.kian.mymettle.workout.RoomWorkoutRepository
import dev.kian.mymettle.workout.SessionAchievement
import dev.kian.mymettle.workout.SessionAchievementScorer
import dev.kian.mymettle.workout.SessionOutcomeRepository
import dev.kian.mymettle.workout.TrainingMode
import kotlinx.coroutines.launch

data class N2WorkoutUiState(
    val loading: Boolean = true,
    val hasProgramme: Boolean = false,
    val selectedDay: String = "φ",
    val selectedMode: TrainingMode = TrainingMode.B,
    val plans: Map<TrainingMode, NativeWorkoutPlan> = emptyMap(),
    val workout: ActiveWorkout? = null,
    val sessionCompleted: Boolean = false,
    val achievement: SessionAchievement? = null,
    val sessionReview: SessionReviewEntity? = null,
    val savingReview: Boolean = false,
    val importing: Boolean = false,
    val importSummary: String? = null,
    val error: String? = null,
)

class N2WorkoutViewModel(
    private val repository: RoomWorkoutRepository,
    private val outcomeRepository: SessionOutcomeRepository,
    private val importer: LegacyV6Importer,
    private val restTimer: RestTimerController,
) : ViewModel() {
    var uiState by mutableStateOf(N2WorkoutUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val hasProgramme = repository.hasImportedProgramme()
                if (!hasProgramme) {
                    uiState = uiState.copy(
                        loading = false,
                        hasProgramme = false,
                        plans = emptyMap(),
                        workout = null,
                    )
                    return@runCatching
                }

                val active = repository.activeWorkout()
                if (active != null) {
                    uiState = uiState.copy(
                        loading = false,
                        hasProgramme = true,
                        selectedDay = active.session.daySymbol,
                        selectedMode = modeFromCode(active.session.mode),
                        workout = active,
                        sessionCompleted = false,
                        achievement = null,
                        sessionReview = null,
                        plans = emptyMap(),
                        error = null,
                    )
                } else {
                    loadProgrammeDay(uiState.selectedDay, uiState.selectedMode)
                }
            }.onFailure(::showError)
        }
    }

    fun selectDay(day: String) {
        if (uiState.workout != null) return
        viewModelScope.launch {
            runCatching { loadProgrammeDay(day, uiState.selectedMode) }.onFailure(::showError)
        }
    }

    fun selectMode(mode: TrainingMode) {
        val workout = uiState.workout
        if (workout == null) {
            uiState = uiState.copy(selectedMode = mode)
            return
        }
        if (workout.session.status != "active" || mode.code == workout.session.mode) return

        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repository.changeSessionMode(workout.session.id, mode) }
                .onSuccess { changed ->
                    uiState = uiState.copy(
                        loading = false,
                        selectedMode = mode,
                        workout = changed,
                    )
                }
                .onFailure(::showError)
        }
    }

    fun startSession() {
        if (!uiState.hasProgramme || uiState.workout != null) return
        viewModelScope.launch {
            uiState = uiState.copy(
                loading = true,
                error = null,
                sessionCompleted = false,
                achievement = null,
                sessionReview = null,
            )
            runCatching { repository.startSession(uiState.selectedDay, uiState.selectedMode) }
                .onSuccess { workout ->
                    uiState = uiState.copy(
                        loading = false,
                        workout = workout,
                        selectedDay = workout.session.daySymbol,
                        selectedMode = modeFromCode(workout.session.mode),
                        plans = emptyMap(),
                    )
                }
                .onFailure(::showError)
        }
    }

    fun saveSet(
        exercise: ActiveWorkoutExercise,
        setId: String,
        load: Double?,
        reps: Int?,
        logged: Boolean,
        onSaved: (() -> Unit)? = null,
    ) {
        val sessionId = uiState.workout?.session?.id ?: return
        viewModelScope.launch {
            runCatching {
                repository.saveSet(
                    sessionExerciseId = exercise.entity.id,
                    setId = setId,
                    load = load,
                    reps = reps,
                    logged = logged,
                )
                repository.activeWorkout(sessionId)
            }.onSuccess { workout ->
                uiState = uiState.copy(workout = workout, error = null)
                if (logged) {
                    restTimer.start(
                        exerciseName = exercise.entity.exerciseNameSnapshot,
                        seconds = exercise.entity.restSeconds,
                    )
                } else {
                    onSaved?.invoke()
                }
            }.onFailure(::showError)
        }
    }

    fun toggleExercise(exercise: ActiveWorkoutExercise) {
        val workout = uiState.workout ?: return
        if (workout.session.status != "active") return
        val complete = exercise.entity.status != "completed"
        viewModelScope.launch {
            runCatching {
                repository.setExerciseCompleted(
                    sessionId = workout.session.id,
                    sessionExerciseId = exercise.entity.id,
                    completed = complete,
                )
                repository.activeWorkout(workout.session.id)
            }.onSuccess { refreshed ->
                uiState = uiState.copy(workout = refreshed, error = null)
            }.onFailure(::showError)
        }
    }

    fun completeSession() {
        val workout = uiState.workout ?: return
        if (workout.session.status != "active") return
        restTimer.stop()
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching {
                val completed = repository.completeSession(workout.session.id)
                val review = outcomeRepository.review(completed.session.id)
                completed to review
            }.onSuccess { (completed, review) ->
                uiState = uiState.copy(
                    loading = false,
                    workout = completed,
                    sessionCompleted = true,
                    achievement = SessionAchievementScorer.score(completed),
                    sessionReview = review,
                )
            }.onFailure(::showError)
        }
    }

    fun saveSessionReview(
        exerciseOrder: Int?,
        organisation: Int?,
        pacing: Int?,
        delayImpact: Int?,
        note: String?,
    ) {
        val workout = uiState.workout ?: return
        if (workout.session.status != "completed") return
        viewModelScope.launch {
            uiState = uiState.copy(savingReview = true, error = null)
            runCatching {
                outcomeRepository.saveReview(
                    sessionId = workout.session.id,
                    exerciseOrder = exerciseOrder,
                    organisation = organisation,
                    pacing = pacing,
                    delayImpact = delayImpact,
                    note = note,
                )
            }.onSuccess { review ->
                uiState = uiState.copy(savingReview = false, sessionReview = review)
            }.onFailure { error ->
                uiState = uiState.copy(savingReview = false)
                showError(error)
            }
        }
    }

    fun leaveCompletedSession() {
        if (uiState.workout?.session?.status != "completed") return
        uiState = uiState.copy(
            workout = null,
            sessionCompleted = false,
            achievement = null,
            sessionReview = null,
        )
        selectDay(uiState.selectedDay)
    }

    fun importBackup(json: String) {
        if (uiState.hasProgramme || uiState.importing) return
        viewModelScope.launch {
            uiState = uiState.copy(importing = true, error = null, importSummary = null)
            runCatching { importer.importJson(json) }
                .onSuccess { report ->
                    uiState = uiState.copy(
                        importing = false,
                        importSummary = report.summary(),
                    )
                    refresh()
                }
                .onFailure { error ->
                    uiState = uiState.copy(importing = false)
                    showError(error)
                }
        }
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private suspend fun loadProgrammeDay(day: String, preferredMode: TrainingMode) {
        val plans = TrainingMode.entries.associateWith { mode -> repository.plan(day, mode) }
        val selected = if (plans[preferredMode]?.exercises?.isNotEmpty() == true) {
            preferredMode
        } else {
            TrainingMode.entries.firstOrNull { plans[it]?.exercises?.isNotEmpty() == true } ?: preferredMode
        }
        uiState = uiState.copy(
            loading = false,
            hasProgramme = true,
            selectedDay = day,
            selectedMode = selected,
            plans = plans,
            workout = null,
            sessionCompleted = false,
            achievement = null,
            sessionReview = null,
            error = null,
        )
    }

    private fun showError(error: Throwable) {
        uiState = uiState.copy(
            loading = false,
            importing = false,
            savingReview = false,
            error = error.message ?: error::class.java.simpleName,
        )
    }

    private fun modeFromCode(code: String): TrainingMode =
        TrainingMode.entries.firstOrNull { it.code == code } ?: TrainingMode.B
}

class N2WorkoutViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = DatabaseProvider.get(appContext)
        return N2WorkoutViewModel(
            repository = RoomWorkoutRepository(database),
            outcomeRepository = SessionOutcomeRepository(database),
            importer = LegacyV6Importer(appContext, database),
            restTimer = RestTimerController.get(appContext),
        ) as T
    }
}

private fun LegacyImportReport.summary(): String =
    "$exercises exercises · $sessions sessions · $sets sets · $setupPhotos setup photos"
