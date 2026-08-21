package dev.kian.mymettle.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.kian.mymettle.data.local.DatabaseProvider
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.data.migration.LegacyImportReport
import dev.kian.mymettle.data.migration.LegacyV6Importer
import dev.kian.mymettle.history.HistoryRepository
import dev.kian.mymettle.timer.RestTimerController
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.ExerciseSwapOption
import dev.kian.mymettle.workout.NativeWorkoutPlan
import dev.kian.mymettle.workout.RoomWorkoutRepository
import dev.kian.mymettle.workout.SessionAchievement
import dev.kian.mymettle.workout.SessionAchievementScorer
import dev.kian.mymettle.workout.SessionOutcomeRepository
import dev.kian.mymettle.workout.TrainingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WorkoutSurface {
    SETS,
    SETUP,
    QUICK_SELECT,
    FINISH,
    DELETE_CONFIRM,
}

data class N2WorkoutUiState(
    val loading: Boolean = true,
    val hasProgramme: Boolean = false,
    val selectedDay: String = "π",
    val selectedMode: TrainingMode = TrainingMode.B,
    val bodyweightKg: Double? = null,
    val plans: Map<TrainingMode, NativeWorkoutPlan> = emptyMap(),
    val workout: ActiveWorkout? = null,
    val workoutSurface: WorkoutSurface = WorkoutSurface.SETS,
    val focusedExerciseId: String? = null,
    val swapTarget: ActiveWorkoutExercise? = null,
    val swapOptions: List<ExerciseSwapOption> = emptyList(),
    val loadingSwapOptions: Boolean = false,
    val reflectionTarget: ActiveWorkoutExercise? = null,
    val reflection: ExerciseReflectionEntity? = null,
    val savingReflection: Boolean = false,
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
    private val historyRepository: HistoryRepository,
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
                        bodyweightKg = active.session.bodyweightSnapshotKg,
                        workout = active,
                        workoutSurface = WorkoutSurface.SETS,
                        focusedExerciseId = active.exercises.firstOrNull { it.entity.status != "completed" }?.entity?.id
                            ?: active.exercises.firstOrNull()?.entity?.id,
                        reflectionTarget = null,
                        reflection = null,
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
                reflectionTarget = null,
                reflection = null,
                sessionCompleted = false,
                achievement = null,
                sessionReview = null,
            )
            runCatching { repository.startSession(uiState.selectedDay, uiState.selectedMode) }
                .onSuccess { workout ->
                    uiState = uiState.copy(
                        loading = false,
                        workout = workout,
                        workoutSurface = WorkoutSurface.SETS,
                        focusedExerciseId = workout.exercises.firstOrNull()?.entity?.id,
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
        durationSeconds: Int? = null,
        distanceMetres: Double? = null,
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
                    durationSeconds = durationSeconds,
                    distanceMetres = distanceMetres,
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

    fun requestExerciseSwap(exercise: ActiveWorkoutExercise) {
        val workout = uiState.workout ?: return
        if (workout.session.status != "active" || exercise.sets.any { it.completedAt != null }) return
        uiState = uiState.copy(
            swapTarget = exercise,
            swapOptions = emptyList(),
            loadingSwapOptions = true,
            error = null,
        )
        viewModelScope.launch {
            runCatching { repository.swapOptions(exercise.entity.id) }
                .onSuccess { options ->
                    uiState = uiState.copy(swapOptions = options, loadingSwapOptions = false)
                }
                .onFailure { error ->
                    uiState = uiState.copy(swapTarget = null, loadingSwapOptions = false)
                    showError(error)
                }
        }
    }

    fun showWorkoutSets(exerciseId: String? = uiState.focusedExerciseId) {
        uiState = uiState.copy(
            workoutSurface = WorkoutSurface.SETS,
            focusedExerciseId = exerciseId ?: uiState.workout?.exercises?.firstOrNull()?.entity?.id,
        )
    }

    fun showExerciseSetup() {
        if (uiState.workout == null) return
        uiState = uiState.copy(workoutSurface = WorkoutSurface.SETUP)
    }

    fun showQuickSelect() {
        if (uiState.workout == null) return
        uiState = uiState.copy(workoutSurface = WorkoutSurface.QUICK_SELECT)
    }

    fun showFinishSheet() {
        if (uiState.workout?.session?.status != "active") return
        uiState = uiState.copy(workoutSurface = WorkoutSurface.FINISH)
    }

    fun showDeleteConfirmation() {
        if (uiState.workout?.session?.status != "active") return
        uiState = uiState.copy(workoutSurface = WorkoutSurface.DELETE_CONFIRM)
    }

    fun dismissWorkoutSheet() {
        if (uiState.workoutSurface == WorkoutSurface.FINISH || uiState.workoutSurface == WorkoutSurface.DELETE_CONFIRM) {
            uiState = uiState.copy(workoutSurface = WorkoutSurface.SETS)
        }
    }

    fun rateExercise(exercise: ActiveWorkoutExercise) {
        uiState.workout ?: return
        viewModelScope.launch {
            runCatching { historyRepository.reflection(exercise.entity.id) }
                .onSuccess { reflection ->
                    uiState = uiState.copy(reflectionTarget = exercise, reflection = reflection, error = null)
                }
                .onFailure(::showError)
        }
    }

    fun discardActiveSession() {
        val workout = uiState.workout ?: return
        if (workout.session.status != "active") return
        restTimer.stop()
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching { repository.discardActiveSession(workout.session.id) }
                .onSuccess {
                    uiState = uiState.copy(
                        loading = false,
                        workout = null,
                        workoutSurface = WorkoutSurface.SETS,
                        focusedExerciseId = null,
                    )
                    loadProgrammeDay(uiState.selectedDay, uiState.selectedMode)
                }
                .onFailure(::showError)
        }
    }

    fun swapExercise(option: ExerciseSwapOption) {
        val target = uiState.swapTarget ?: return
        viewModelScope.launch {
            uiState = uiState.copy(loadingSwapOptions = true, error = null)
            runCatching { repository.swapExercise(target.entity.id, option.executionProfileId) }
                .onSuccess { workout ->
                    uiState = uiState.copy(
                        workout = workout,
                        swapTarget = null,
                        swapOptions = emptyList(),
                        loadingSwapOptions = false,
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(loadingSwapOptions = false)
                    showError(error)
                }
        }
    }

    fun dismissExerciseSwap() {
        if (uiState.loadingSwapOptions) return
        uiState = uiState.copy(swapTarget = null, swapOptions = emptyList())
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
                val refreshed = repository.activeWorkout(workout.session.id)
                val target = refreshed.exercises.firstOrNull { it.entity.id == exercise.entity.id }
                val reflection = if (complete && target != null) historyRepository.reflection(target.entity.id) else null
                Triple(refreshed, target, reflection)
            }.onSuccess { (refreshed, target, reflection) ->
                uiState = uiState.copy(
                    workout = refreshed,
                    reflectionTarget = if (complete) target else null,
                    reflection = if (complete) reflection else null,
                    error = null,
                )
            }.onFailure(::showError)
        }
    }

    fun dismissReflection() {
        uiState = uiState.copy(reflectionTarget = null, reflection = null, savingReflection = false)
    }

    fun saveExerciseReflection(
        targetMuscleEngagement: Int?,
        execution: String?,
        enjoyment: Int?,
        comfort: String?,
        note: String?,
    ) {
        val workout = uiState.workout ?: return
        val target = uiState.reflectionTarget ?: return
        viewModelScope.launch {
            uiState = uiState.copy(savingReflection = true, error = null)
            runCatching {
                historyRepository.saveExerciseReflection(
                    sessionId = workout.session.id,
                    sessionExerciseId = target.entity.id,
                    targetMuscleEngagement = targetMuscleEngagement,
                    execution = execution,
                    enjoyment = enjoyment,
                    comfort = comfort,
                    note = note,
                )
            }.onSuccess { reflection ->
                uiState = uiState.copy(
                    savingReflection = false,
                    reflection = reflection,
                    reflectionTarget = null,
                )
            }.onFailure { error ->
                uiState = uiState.copy(savingReflection = false)
                showError(error)
            }
        }
    }

    fun completeSession(skipReview: Boolean = false) {
        val workout = uiState.workout ?: return
        if (workout.session.status != "active") return
        restTimer.stop()
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null, reflectionTarget = null, reflection = null)
            runCatching {
                val completed = repository.completeSession(workout.session.id)
                val review = if (skipReview) null else outcomeRepository.review(completed.session.id)
                completed to review
            }.onSuccess { (completed, review) ->
                if (skipReview) {
                    uiState = uiState.copy(
                        loading = false,
                        workout = null,
                        workoutSurface = WorkoutSurface.SETS,
                        focusedExerciseId = null,
                        sessionCompleted = false,
                        achievement = null,
                        sessionReview = null,
                    )
                    loadProgrammeDay(uiState.selectedDay, uiState.selectedMode)
                } else {
                    uiState = uiState.copy(
                        loading = false,
                        workout = completed,
                        sessionCompleted = true,
                        achievement = SessionAchievementScorer.score(completed),
                        sessionReview = review,
                    )
                }
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
            reflectionTarget = null,
            reflection = null,
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
        // Planning resolves targets, candidates, inference and prescriptions. Keep that work off the
        // Compose/main thread; Room moves its own queries to its executor as each plan is built.
        val plans = withContext(Dispatchers.Default) {
            TrainingMode.entries.associateWith { mode -> repository.plan(day, mode) }
        }
        val bodyweightKg = repository.latestBodyweightKg()
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
            bodyweightKg = bodyweightKg,
            plans = plans,
            workout = null,
            reflectionTarget = null,
            reflection = null,
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
            savingReflection = false,
            savingReview = false,
            loadingSwapOptions = false,
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
            historyRepository = HistoryRepository(database),
            importer = LegacyV6Importer(appContext, database),
            restTimer = RestTimerController.get(appContext),
        ) as T
    }
}

private fun LegacyImportReport.summary(): String =
    "$exercises exercises · $sessions sessions · $sets sets · $setupPhotos setup photos"
