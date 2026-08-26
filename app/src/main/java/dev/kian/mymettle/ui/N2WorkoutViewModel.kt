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
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.history.HistoryRepository
import dev.kian.mymettle.library.ExerciseLibraryRepository
import dev.kian.mymettle.library.WorkoutSetupDetails
import dev.kian.mymettle.timer.RestTimerController
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.ExerciseSwapOption
import dev.kian.mymettle.workout.NativeWorkoutPlan
import dev.kian.mymettle.workout.PerformanceSetRecord
import dev.kian.mymettle.workout.RoomWorkoutRepository
import dev.kian.mymettle.workout.SessionAchievement
import dev.kian.mymettle.workout.SessionAchievementScorer
import dev.kian.mymettle.workout.SessionOutcomeRepository
import dev.kian.mymettle.workout.TrainingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val savingSetupPhoto: Boolean = false,
    val setupDetails: WorkoutSetupDetails? = null,
    val loadingSetupDetails: Boolean = false,
    val savingSetupDetails: Boolean = false,
    val reflectionTarget: ActiveWorkoutExercise? = null,
    val reflection: ExerciseReflectionEntity? = null,
    val savingReflection: Boolean = false,
    val sessionCompleted: Boolean = false,
    val achievement: SessionAchievement? = null,
    val sessionReview: SessionReviewEntity? = null,
    val savingReview: Boolean = false,
    val error: String? = null,
)

class N2WorkoutViewModel(
    private val repository: RoomWorkoutRepository,
    private val outcomeRepository: SessionOutcomeRepository,
    private val historyRepository: HistoryRepository,
    private val libraryRepository: ExerciseLibraryRepository,
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
                        setupDetails = null,
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
                        setupDetails = null,
                        loadingSetupDetails = false,
                        savingSetupDetails = false,
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

    fun addCapturedSetupPhoto(exercise: ActiveWorkoutExercise, captureFile: File) {
        if (uiState.savingSetupPhoto) {
            captureFile.delete()
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(savingSetupPhoto = true, error = null)
            runCatching {
                libraryRepository.addCapturedSetupPhoto(exercise.entity.exerciseId, captureFile)
                repository.activeWorkout()
            }.onSuccess { refreshed ->
                uiState = uiState.copy(
                    savingSetupPhoto = false,
                    workout = refreshed ?: uiState.workout,
                )
            }.onFailure { error ->
                captureFile.delete()
                uiState = uiState.copy(savingSetupPhoto = false)
                showError(error)
            }
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
                setupDetails = null,
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
        additionalValues: List<PerformanceMetricValue> = emptyList(),
        laterality: Laterality? = null,
        logged: Boolean,
        onSaved: (() -> Unit)? = null,
    ) {
        val sessionId = uiState.workout?.session?.id ?: return
        val resolvedLateralityMode = exercise.resolvedWorkoutLateralityMode()
        val wasLogicalSetComplete = exercise.sets
            .firstOrNull { it.id == setId }
            ?.isCompleteFor(resolvedLateralityMode) == true
        viewModelScope.launch {
            runCatching {
                repository.saveSet(
                    sessionExerciseId = exercise.entity.id,
                    setId = setId,
                    load = load,
                    reps = reps,
                    durationSeconds = durationSeconds,
                    distanceMetres = distanceMetres,
                    additionalValues = additionalValues,
                    laterality = laterality,
                    logged = logged,
                )
                repository.activeWorkout(sessionId)
            }.onSuccess { workout ->
                uiState = uiState.copy(workout = workout, error = null)
                val refreshedSet = workout.exercises
                    .firstOrNull { it.entity.id == exercise.entity.id }
                    ?.sets
                    ?.firstOrNull { it.id == setId }
                val logicalSetJustCompleted = logged &&
                    !wasLogicalSetComplete &&
                    refreshedSet?.isCompleteFor(resolvedLateralityMode) == true
                if (logicalSetJustCompleted) {
                    restTimer.start(
                        exerciseName = exercise.entity.exerciseNameSnapshot,
                        seconds = exercise.entity.restSeconds,
                    )
                } else if (!logged) {
                    onSaved?.invoke()
                }
            }.onFailure(::showError)
        }
    }

    fun requestExerciseSwap(exercise: ActiveWorkoutExercise) {
        val workout = uiState.workout ?: return
        if (workout.session.status != "active" || exercise.sets.any { it.observations.isNotEmpty() }) return
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
            setupDetails = null,
            loadingSetupDetails = false,
            savingSetupDetails = false,
        )
    }

    fun showExerciseSetup() {
        val workout = uiState.workout ?: return
        val target = workout.exercises.firstOrNull { it.entity.id == uiState.focusedExerciseId }
            ?: workout.exercises.firstOrNull()
            ?: return
        uiState = uiState.copy(
            workoutSurface = WorkoutSurface.SETUP,
            focusedExerciseId = target.entity.id,
            setupDetails = null,
            loadingSetupDetails = true,
            error = null,
        )
        viewModelScope.launch {
            runCatching { libraryRepository.workoutSetupDetails(target.entity.exerciseId) }
                .onSuccess { details ->
                    if (uiState.workoutSurface == WorkoutSurface.SETUP && uiState.focusedExerciseId == target.entity.id) {
                        uiState = uiState.copy(setupDetails = details, loadingSetupDetails = false)
                    }
                }
                .onFailure { error ->
                    uiState = uiState.copy(loadingSetupDetails = false)
                    showError(error)
                }
        }
    }

    fun saveWorkoutSetup(
        exercise: ActiveWorkoutExercise,
        exerciseInstructions: String,
        setupInstructions: String,
        videoReferenceUrl: String,
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(savingSetupDetails = true, error = null)
            runCatching {
                libraryRepository.updateWorkoutSetupDetails(
                    exerciseId = exercise.entity.exerciseId,
                    exerciseInstructions = exerciseInstructions,
                    setupInstructions = setupInstructions,
                    videoReferenceUrl = videoReferenceUrl,
                )
            }.onSuccess { details ->
                uiState = uiState.copy(
                    setupDetails = details,
                    savingSetupDetails = false,
                )
            }.onFailure { error ->
                uiState = uiState.copy(savingSetupDetails = false)
                showError(error)
            }
        }
    }

    fun showQuickSelect() {
        if (uiState.workout == null) return
        uiState = uiState.copy(workoutSurface = WorkoutSurface.QUICK_SELECT, setupDetails = null)
    }

    fun showFinishSheet() {
        if (uiState.workout?.session?.status != "active") return
        uiState = uiState.copy(workoutSurface = WorkoutSurface.FINISH, setupDetails = null)
    }

    fun showDeleteConfirmation() {
        if (uiState.workout?.session?.status != "active") return
        uiState = uiState.copy(workoutSurface = WorkoutSurface.DELETE_CONFIRM, setupDetails = null)
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
                        setupDetails = null,
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

    fun saveExerciseReview(note: String?) {
        saveExerciseReflection(
            targetMuscleEngagement = null,
            execution = null,
            enjoyment = null,
            comfort = null,
            note = note,
        )
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
            uiState = uiState.copy(loading = true, error = null, reflectionTarget = null, reflection = null, setupDetails = null)
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
            setupDetails = null,
            reflectionTarget = null,
            reflection = null,
            sessionCompleted = false,
            achievement = null,
            sessionReview = null,
        )
        selectDay(uiState.selectedDay)
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
            setupDetails = null,
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
            savingReflection = false,
            savingReview = false,
            loadingSwapOptions = false,
            savingSetupPhoto = false,
            loadingSetupDetails = false,
            savingSetupDetails = false,
            error = error.message ?: error::class.java.simpleName,
        )
    }

    private fun modeFromCode(code: String): TrainingMode =
        TrainingMode.entries.firstOrNull { it.code == code } ?: TrainingMode.B
}

private fun PerformanceSetRecord.isCompleteFor(mode: LateralityMode): Boolean {
    if (observations.isEmpty()) return false
    val sides = observations.mapTo(mutableSetOf()) { it.laterality }
    return when (mode) {
        LateralityMode.UNILATERAL -> Laterality.RIGHT in sides && Laterality.LEFT in sides
        LateralityMode.BILATERAL_ONLY -> Laterality.BILATERAL in sides || Laterality.UNKNOWN in sides
        LateralityMode.ALTERNATING_ALLOWED -> sides.any {
            it in setOf(Laterality.ALTERNATING, Laterality.BILATERAL, Laterality.LEFT, Laterality.RIGHT, Laterality.UNKNOWN)
        }
        LateralityMode.NOT_APPLICABLE -> Laterality.NOT_APPLICABLE in sides || Laterality.UNKNOWN in sides
        LateralityMode.UNKNOWN -> true
    }
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
            libraryRepository = ExerciseLibraryRepository(appContext, database),
            restTimer = RestTimerController.get(appContext),
        ) as T
    }
}
