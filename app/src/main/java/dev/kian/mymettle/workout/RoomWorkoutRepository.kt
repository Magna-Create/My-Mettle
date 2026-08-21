package dev.kian.mymettle.workout

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.CycleCompletedDayEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ProgrammeModeConstraintEntity
import dev.kian.mymettle.data.local.entity.ProgrammeTargetEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionConstraintEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseTargetEntity
import dev.kian.mymettle.data.local.entity.SessionTargetEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.exercise.LoadResolution
import dev.kian.mymettle.domain.training.ExercisePrescription
import dev.kian.mymettle.domain.training.PrescriptionLoadEvidence
import dev.kian.mymettle.domain.training.ResolvedTrainingTarget
import dev.kian.mymettle.domain.training.SessionConstraints
import dev.kian.mymettle.domain.training.TargetSource
import dev.kian.mymettle.domain.training.TrainingTarget
import dev.kian.mymettle.domain.training.TrainingTargetId
import dev.kian.mymettle.engine.prescription.HistoryBackedPrescriptionEngine
import dev.kian.mymettle.engine.prescription.PrescriptionEngine
import dev.kian.mymettle.engine.prescription.PrescriptionRequest
import dev.kian.mymettle.engine.prescription.SameProfileLoadEvidenceResolver
import dev.kian.mymettle.engine.targeting.BudgetedTargetExerciseSelector
import dev.kian.mymettle.engine.targeting.ConstraintTargetResolver
import dev.kian.mymettle.engine.targeting.ExerciseSelectionCandidate
import dev.kian.mymettle.engine.targeting.ExerciseSelector
import dev.kian.mymettle.engine.targeting.TargetResolver
import dev.kian.mymettle.inference.RoomInferenceRepository
import java.time.Instant
import java.util.UUID
import org.json.JSONArray

private val CORE_DAYS = setOf("ψ", "φ", "π")

class NativeWorkoutException(message: String) : IllegalStateException(message)

data class PlannedWorkoutExercise(
    val slotId: String,
    val name: String,
    val importance: ExerciseImportance,
    val defaultUnit: String,
    val trackingMetric: String,
    val loadRelationship: String,
    val entryBasis: String,
    val executionProfileName: String,
    val prescription: ExercisePrescription,
    val movementReason: String,
    val estimatedDurationSeconds: Int,
)

data class WorkoutCandidateDecision(
    val slotId: String,
    val exerciseId: String,
    val exerciseName: String,
    val executionProfileId: String,
    val executionProfileName: String,
    val preferencePriority: Double,
    val targetCoverage: Map<String, Double>,
    val selected: Boolean,
    val selectedSets: Int?,
    val decisionReason: String,
)

data class NativeWorkoutPlan(
    val routineVersionId: String,
    val day: String,
    val mode: TrainingMode,
    val constraints: SessionConstraints,
    val targetResolutions: List<ResolvedTrainingTarget>,
    val exercises: List<PlannedWorkoutExercise>,
    val candidateDecisions: List<WorkoutCandidateDecision>,
) {
    val targets: List<TrainingTarget> get() = targetResolutions.filter { it.included }.map { it.target }
    val workingSetCount: Int get() = exercises.sumOf { it.prescription.sets }
    val estimatedDurationSeconds: Int get() = exercises.sumOf { it.estimatedDurationSeconds }
}

data class ExerciseSwapOption(
    val exerciseId: String,
    val exerciseName: String,
    val executionProfileId: String,
    val executionProfileName: String,
    val trackingMetric: String,
    val loadRelationship: String,
    val entryBasis: String,
    val defaultUnit: String,
    val matchedTargetIds: List<TrainingTargetId>,
    val targetCoverageScore: Double,
    val prescription: ExercisePrescription,
)

data class ActiveWorkoutExercise(
    val entity: SessionExerciseEntity,
    val targetIds: List<TrainingTargetId>,
    val sets: List<SetRecordEntity>,
    val previousCompletedSets: List<SetRecordEntity>,
    val details: WorkoutExerciseDetails = WorkoutExerciseDetails(),
)

data class WorkoutExerciseDetails(
    val setupNotes: String = "",
    val cues: List<String> = emptyList(),
    val setupMediaPaths: List<String> = emptyList(),
)

data class ActiveWorkout(
    val session: SessionEntity,
    val targets: List<TrainingTarget>,
    val exercises: List<ActiveWorkoutExercise>,
)

/**
 * Persistence boundary for the N2 workout loop.
 *
 * The UI receives resolved session prescriptions. Imported Legacy A/B/C rows have already become
 * whole-session constraints, while routine slots are merely pinned candidates. Completed session
 * exercise, target and constraint snapshots remain historically stable when resolver models change.
 */
class RoomWorkoutRepository(
    private val database: MyMettleDatabase,
    private val prescriptionEngine: PrescriptionEngine = HistoryBackedPrescriptionEngine(),
    private val targetResolver: TargetResolver = ConstraintTargetResolver(),
    private val exerciseSelector: ExerciseSelector = BudgetedTargetExerciseSelector(),
    private val inferenceRepository: RoomInferenceRepository = RoomInferenceRepository(database),
) {
    private val dao get() = database.workoutDao()
    private val libraryDao get() = database.libraryDao()

    suspend fun hasImportedProgramme(): Boolean = dao.appState() != null && dao.profileCount() > 0

    suspend fun latestBodyweightKg(): Double? = dao.latestBodyMeasurement()?.weightKg

    suspend fun plan(day: String, mode: TrainingMode): NativeWorkoutPlan {
        val state = dao.appState() ?: throw NativeWorkoutException("No native programme has been imported yet.")
        return planForRoutine(state.currentRoutineVersionId, day, mode)
    }

    suspend fun startSession(day: String, mode: TrainingMode): ActiveWorkout = database.withTransaction {
        var state = dao.appState() ?: throw NativeWorkoutException("No native programme has been imported yet.")
        if (state.activeSessionId != null) throw NativeWorkoutException("A workout is already active.")

        val now = timestamp()
        var cycle = dao.trainingCycle(state.currentCycleId)
            ?: throw NativeWorkoutException("The current training cycle is missing.")
        val completedCoreDays = dao.completedDays(cycle.id).mapTo(mutableSetOf()) { it.daySymbol }

        if (day == "&" && !completedCoreDays.containsAll(CORE_DAYS)) {
            throw NativeWorkoutException("The optional day remains locked until ψ, φ and π are complete.")
        }

        if (day in CORE_DAYS && completedCoreDays.containsAll(CORE_DAYS)) {
            dao.upsertTrainingCycles(listOf(cycle.copy(status = "closed", endedAt = now)))
            cycle = TrainingCycleEntity(
                id = id("cycle"),
                startedAt = now,
                endedAt = null,
                status = "active",
                andCompleted = false,
            )
            dao.upsertTrainingCycles(listOf(cycle))
            state = state.copy(currentCycleId = cycle.id, updatedAt = now)
        }

        val workoutPlan = planForRoutine(state.currentRoutineVersionId, day, mode)
        if (workoutPlan.exercises.isEmpty()) {
            throw NativeWorkoutException("$day has no exercises in ${mode.label}.")
        }

        val bodyweight = dao.latestBodyMeasurement()?.weightKg
        val sessionId = id("session")
        val session = SessionEntity(
            id = sessionId,
            cycleId = cycle.id,
            daySymbol = day,
            mode = mode.code,
            routineVersionId = workoutPlan.routineVersionId,
            status = "active",
            startedAt = now,
            completedAt = null,
            editedAt = null,
            discardedAt = null,
            excludedFromInsights = false,
            bodyweightSnapshotKg = bodyweight,
            healthExportState = "not_requested",
            healthClientRecordId = "my-mettle:$sessionId",
        )

        val sessionTargets = workoutPlan.targetResolutions.map { resolution ->
            resolution.toSessionTarget(sessionId)
        }
        val sessionTargetByProgrammeId = sessionTargets.associateBy { it.programmeTargetId }
        val sessionExercises = mutableListOf<SessionExerciseEntity>()
        val sessionExerciseTargets = mutableListOf<SessionExerciseTargetEntity>()
        val sets = mutableListOf<SetRecordEntity>()
        workoutPlan.exercises.forEachIndexed { position, planned ->
            val sessionExerciseId = id("session_exercise")
            sessionExercises += planned.toSessionExercise(
                sessionExerciseId = sessionExerciseId,
                sessionId = sessionId,
                position = position,
                mode = mode,
                bodyweight = bodyweight,
                movementReason = planned.movementReason,
            )
            sessionExerciseTargets += planned.prescription.targetIds.map { targetId ->
                val sessionTarget = sessionTargetByProgrammeId[targetId.value]
                    ?: throw NativeWorkoutException("Prescription references missing target ${targetId.value}.")
                SessionExerciseTargetEntity(sessionExerciseId, sessionTarget.id)
            }
            sets += prescribedSets(
                sessionExerciseId = sessionExerciseId,
                planned = planned,
                indices = 0 until planned.prescription.sets,
            )
        }

        dao.upsertSessions(listOf(session))
        dao.upsertSessionConstraint(workoutPlan.constraints.toSessionConstraint(sessionId))
        dao.upsertSessionTargets(sessionTargets)
        dao.upsertSessionExercises(sessionExercises)
        dao.upsertSessionExerciseTargets(sessionExerciseTargets)
        dao.upsertSets(sets)
        dao.upsertAppState(state.copy(activeSessionId = sessionId, updatedAt = now))

        activeWorkout(sessionId)
    }

    suspend fun activeWorkout(): ActiveWorkout? {
        val sessionId = dao.appState()?.activeSessionId ?: return null
        return activeWorkout(sessionId)
    }

    suspend fun activeWorkout(sessionId: String): ActiveWorkout {
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Active workout is missing.")
        val targets = dao.sessionTargets(sessionId).filter { it.included }.map(SessionTargetEntity::toDomain)
        val exercises = dao.sessionExercises(sessionId).map { exercise ->
            val memory = libraryDao.memory(exercise.exerciseId)
            ActiveWorkoutExercise(
                entity = exercise,
                targetIds = dao.sessionExerciseTargets(exercise.id).map { TrainingTargetId(it.sessionTargetId) },
                sets = dao.sets(exercise.id),
                previousCompletedSets = dao.latestCompletedSetsForExercise(
                    exerciseId = exercise.exerciseId,
                    excludeSessionId = sessionId,
                    limit = 12,
                ),
                details = WorkoutExerciseDetails(
                    setupNotes = memory?.setupNotes.orEmpty(),
                    cues = libraryDao.cues(exercise.exerciseId).map { it.cue },
                    setupMediaPaths = libraryDao.setupMedia(exercise.exerciseId).map { it.relativePath },
                ),
            )
        }
        return ActiveWorkout(session, targets, exercises)
    }

    suspend fun discardActiveSession(sessionId: String) = database.withTransaction {
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status != "active") throw NativeWorkoutException("Only an active workout can be discarded.")
        val state = dao.appState() ?: throw NativeWorkoutException("App state is missing.")
        val now = timestamp()
        dao.upsertSessions(
            listOf(
                session.copy(
                    status = "discarded",
                    discardedAt = now,
                    editedAt = now,
                    excludedFromInsights = true,
                ),
            ),
        )
        dao.upsertAppState(state.copy(activeSessionId = null, updatedAt = now))
    }

    /**
     * Re-resolve an active session against the same immutable routine version while preserving
     * everything already performed. Exercises removed by the new mode remain in the session
     * snapshot with prescriptionIncluded=false; performed surplus sets become additional sets.
     */
    suspend fun changeSessionMode(sessionId: String, mode: TrainingMode): ActiveWorkout = database.withTransaction {
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status != "active") throw NativeWorkoutException("Only an active workout can change mode.")
        if (session.mode == mode.code) return@withTransaction activeWorkout(sessionId)

        val target = planForRoutine(session.routineVersionId, session.daySymbol, mode)
        val resolvedSessionTargets = target.targetResolutions.map { resolution ->
            resolution.toSessionTarget(sessionId)
        }
        dao.upsertSessionTargets(resolvedSessionTargets)
        dao.upsertSessionConstraint(target.constraints.toSessionConstraint(sessionId))
        val sessionTargetByProgrammeId = resolvedSessionTargets.associateBy { it.programmeTargetId }
        val existing = dao.sessionExercises(sessionId)
        val existingBySlot = existing.associateBy { it.slotId }
        val targetSlots = target.exercises.mapTo(mutableSetOf()) { it.slotId }
        val bodyweight = session.bodyweightSnapshotKg

        // Temporarily move every persisted position out of the unique-key range, then write the
        // resolved target order back. This avoids transient uniqueness collisions during mode swaps.
        dao.offsetSessionExercisePositions(sessionId)

        val updatedExercises = mutableListOf<SessionExerciseEntity>()
        val updatedExerciseTargets = mutableListOf<SessionExerciseTargetEntity>()
        val updatedSets = mutableListOf<SetRecordEntity>()

        target.exercises.forEachIndexed { position, planned ->
            val current = existingBySlot[planned.slotId]
            if (current == null) {
                val sessionExerciseId = id("session_exercise")
                updatedExercises += planned.toSessionExercise(
                    sessionExerciseId = sessionExerciseId,
                    sessionId = sessionId,
                    position = position,
                    mode = mode,
                    bodyweight = bodyweight,
                    movementReason = planned.movementReason,
                )
                updatedExerciseTargets += planned.toSessionExerciseTargets(
                    sessionExerciseId,
                    sessionTargetByProgrammeId,
                )
                updatedSets += prescribedSets(
                    sessionExerciseId = sessionExerciseId,
                    planned = planned,
                    indices = 0 until planned.prescription.sets,
                )
            } else {
                val substituted = current.substitutedFromExerciseId != null
                val retainedTargetIds = if (substituted) {
                    dao.sessionExerciseTargets(current.id).map { TrainingTargetId(it.sessionTargetId) }
                } else {
                    emptyList()
                }
                val effectivePrescription = if (substituted) {
                    current.toPrescription(retainedTargetIds).copy(sets = planned.prescription.sets)
                } else {
                    planned.prescription
                }
                updatedExercises += current.copy(
                    position = position,
                    importanceSnapshot = planned.importance.name.lowercase(),
                    executionProfileId = effectivePrescription.executionProfileId.value,
                    executionProfileNameSnapshot = if (substituted) current.executionProfileNameSnapshot else planned.executionProfileName,
                    prescribedLoad = effectivePrescription.prescribedLoad,
                    prescribedLoadEvidenceSource = effectivePrescription.loadEvidence?.source,
                    prescribedLoadEvidenceSetId = effectivePrescription.loadEvidence?.sourceSetRecordId,
                    prescribedLoadInferenceRunId = effectivePrescription.loadEvidence?.inferenceRunId,
                    prescribedLoadAnchor = effectivePrescription.loadEvidence?.anchorLoad,
                    prescriptionMode = mode.code,
                    prescriptionIncluded = true,
                    prescribedSets = effectivePrescription.sets,
                    repMin = effectivePrescription.repRange.first,
                    repMax = effectivePrescription.repRange.last,
                    restSeconds = effectivePrescription.restSeconds,
                    generatedByModelVersion = effectivePrescription.generatedByModelVersion,
                    deferToAnd = false,
                    movementReason = if (substituted) USER_SUBSTITUTION_REASON else planned.movementReason,
                )
                if (!substituted) {
                    dao.deleteSessionExerciseTargets(current.id)
                    updatedExerciseTargets += planned.toSessionExerciseTargets(
                        current.id,
                        sessionTargetByProgrammeId,
                    )
                }

                val currentSets = dao.sets(current.id)
                val currentIndices = currentSets.mapTo(mutableSetOf()) { it.setIndex }
                val missingIndices = (0 until effectivePrescription.sets).filterNot { it in currentIndices }
                updatedSets += if (substituted) {
                    prescribedSets(
                        sessionExerciseId = current.id,
                        trackingMetric = current.trackingMetricSnapshot,
                        loadRelationship = current.loadRelationshipSnapshot,
                        defaultUnit = currentSets.firstOrNull()?.unit ?: "kg",
                        prescription = effectivePrescription,
                        indices = missingIndices,
                    )
                } else {
                    prescribedSets(current.id, planned, missingIndices)
                }
                updatedSets += currentSets
                    .filter { it.setIndex >= effectivePrescription.sets && it.completedAt != null && it.kind == "prescribed" }
                    .map { it.copy(kind = "additional") }
            }
        }

        // Keep excluded movements as history. Untouched ones disappear from the active UI because
        // prescriptionIncluded=false; performed ones remain available as already-done work.
        existing
            .filterNot { it.slotId in targetSlots }
            .sortedBy { it.position }
            .forEachIndexed { excludedIndex, current ->
                updatedExercises += current.copy(
                    position = target.exercises.size + excludedIndex,
                    prescriptionMode = mode.code,
                    prescriptionIncluded = false,
                )
            }

        dao.upsertSessionExercises(updatedExercises)
        if (updatedExerciseTargets.isNotEmpty()) dao.upsertSessionExerciseTargets(updatedExerciseTargets)
        if (updatedSets.isNotEmpty()) dao.upsertSets(updatedSets)
        dao.upsertSessions(listOf(session.copy(mode = mode.code, editedAt = timestamp())))
        activeWorkout(sessionId)
    }

    /**
     * Resolve replacements against the current movement's session targets. Load suggestions are
     * generated from the replacement execution profile only; the outgoing exercise contributes no
     * load evidence.
     */
    suspend fun swapOptions(sessionExerciseId: String): List<ExerciseSwapOption> =
        buildSwapOptions(sessionExerciseId)

    suspend fun swapExercise(
        sessionExerciseId: String,
        replacementExecutionProfileId: String,
    ): ActiveWorkout = database.withTransaction {
        val current = dao.sessionExercise(sessionExerciseId)
            ?: throw NativeWorkoutException("Exercise not found in the workout.")
        val session = dao.session(current.sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status != "active") throw NativeWorkoutException("Only an active workout can change exercises.")
        if (current.status == "completed" || dao.sets(current.id).any { it.completedAt != null }) {
            throw NativeWorkoutException("An exercise cannot be swapped after one of its sets has been logged.")
        }

        val replacement = buildSwapOptions(current.id)
            .firstOrNull { it.executionProfileId == replacementExecutionProfileId }
            ?: throw NativeWorkoutException("That replacement is no longer compatible with this session target.")
        val prescription = replacement.prescription
        val updated = current.copy(
            exerciseId = replacement.exerciseId,
            exerciseNameSnapshot = replacement.exerciseName,
            trackingMetricSnapshot = replacement.trackingMetric,
            loadRelationshipSnapshot = replacement.loadRelationship,
            entryBasisSnapshot = replacement.entryBasis,
            executionProfileId = replacement.executionProfileId,
            executionProfileNameSnapshot = replacement.executionProfileName,
            prescribedLoad = prescription.prescribedLoad,
            prescribedLoadEvidenceSource = prescription.loadEvidence?.source,
            prescribedLoadEvidenceSetId = prescription.loadEvidence?.sourceSetRecordId,
            prescribedLoadInferenceRunId = prescription.loadEvidence?.inferenceRunId,
            prescribedLoadAnchor = prescription.loadEvidence?.anchorLoad,
            prescribedSets = prescription.sets,
            repMin = prescription.repRange.first,
            repMax = prescription.repRange.last,
            restSeconds = prescription.restSeconds,
            generatedByModelVersion = prescription.generatedByModelVersion,
            status = "planned",
            startedAt = null,
            completedAt = null,
            movementReason = USER_SUBSTITUTION_REASON,
            substitutedFromExerciseId = current.substitutedFromExerciseId ?: current.exerciseId,
        )

        dao.deleteSessionExerciseTargets(current.id)
        dao.deleteSets(current.id)
        dao.upsertSessionExercises(listOf(updated))
        if (replacement.matchedTargetIds.isNotEmpty()) {
            dao.upsertSessionExerciseTargets(
                replacement.matchedTargetIds.map { targetId ->
                    SessionExerciseTargetEntity(current.id, targetId.value)
                },
            )
        }
        dao.upsertSets(
            prescribedSets(
                sessionExerciseId = current.id,
                trackingMetric = replacement.trackingMetric,
                loadRelationship = replacement.loadRelationship,
                defaultUnit = replacement.defaultUnit,
                prescription = prescription,
                indices = 0 until prescription.sets,
            ),
        )
        dao.upsertSessions(listOf(session.copy(editedAt = timestamp())))
        activeWorkout(session.id)
    }

    private suspend fun buildSwapOptions(sessionExerciseId: String): List<ExerciseSwapOption> {
        val current = dao.sessionExercise(sessionExerciseId)
            ?: throw NativeWorkoutException("Exercise not found in the workout.")
        val session = dao.session(current.sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status != "active") return emptyList()
        if (current.status == "completed" || dao.sets(current.id).any { it.completedAt != null }) return emptyList()

        val sessionTargets = dao.sessionTargets(session.id).associateBy { it.id }
        val currentTargetIds = dao.sessionExerciseTargets(current.id).map { it.sessionTargetId }
        val currentTargets = currentTargetIds.mapNotNull(sessionTargets::get)
        val targetBySegment = currentTargets.associateBy { it.muscleSegmentId }
        val exercises = dao.allActiveExercises().filterNot { it.id == current.exerciseId }
        if (exercises.isEmpty()) return emptyList()
        val defaultProfiles = dao.executionProfiles(exercises.map { it.id })
            .groupBy { it.exerciseId }
            .mapNotNull { (_, profiles) -> profiles.singleOrNull { it.isDefault } }
        val recruitmentByProfile = dao.recruitmentAllocations(defaultProfiles.map { it.id })
            .groupBy { it.executionProfileId }
        val exerciseById = exercises.associateBy { it.id }
        val inferenceSnapshot = inferenceRepository.latestSnapshot()
        val translationByProfile = inferenceSnapshot?.exerciseTranslationStates.orEmpty()
            .associateBy { it.executionProfileId.value }

        return defaultProfiles.mapNotNull { profile ->
            val exercise = exerciseById[profile.exerciseId] ?: return@mapNotNull null
            val matchedCoverage = matchedSwapTargetCoverage(
                targetsBySegment = targetBySegment,
                recruitment = recruitmentByProfile[profile.id].orEmpty(),
            )
            if (currentTargets.isNotEmpty() && matchedCoverage.isEmpty()) return@mapNotNull null

            val translation = translationByProfile[profile.id]
            val rawAnchor = if (translation?.observedLoadAnchor == null) {
                dao.latestCompletedLoadForExecutionProfile(profile.id, excludeSessionId = session.id)
            } else {
                null
            }
            val loadEvidence = SameProfileLoadEvidenceResolver.resolve(
                inferredLoad = translation?.observedLoadAnchor?.value,
                inferredSetRecordId = translation?.observedLoadAnchor?.sourceId,
                inferenceRunId = inferenceSnapshot?.run?.id?.value,
                rawLoad = rawAnchor?.load,
                rawSetRecordId = rawAnchor?.id,
            )
            val prescription = prescriptionEngine.generate(
                PrescriptionRequest(
                    exerciseId = ExerciseId(exercise.id),
                    executionProfileId = ExecutionProfileId(profile.id),
                    targetIds = matchedCoverage.keys.sortedBy { it.value },
                    sets = current.prescribedSets.coerceAtLeast(1),
                    repRange = current.repMin..current.repMax,
                    loadEvidence = loadEvidence,
                    permitsExternalLoad = exercise.trackingMetric == "load_reps" &&
                        exercise.loadRelationship !in setOf("bodyweight", "none"),
                    loadResolution = profile.toLoadResolution(),
                    restSeconds = current.restSeconds,
                ),
            )
            ExerciseSwapOption(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                executionProfileId = profile.id,
                executionProfileName = profile.name,
                trackingMetric = exercise.trackingMetric,
                loadRelationship = exercise.loadRelationship,
                entryBasis = exercise.entryBasis,
                defaultUnit = exercise.defaultUnit,
                matchedTargetIds = prescription.targetIds,
                targetCoverageScore = matchedCoverage.entries.sumOf { (targetId, coverage) ->
                    (sessionTargets[targetId.value]?.resolvedPriority ?: 0.0) * coverage
                },
                prescription = prescription,
            )
        }.sortedWith(
            compareByDescending<ExerciseSwapOption> { it.targetCoverageScore }
                .thenBy { it.exerciseName.lowercase() }
                .thenBy { it.executionProfileName.lowercase() },
        )
    }

    suspend fun saveSet(
        sessionExerciseId: String,
        setId: String,
        load: Double?,
        reps: Int?,
        durationSeconds: Int? = null,
        distanceMetres: Double? = null,
        logged: Boolean,
    ): SetRecordEntity = database.withTransaction {
        val current = dao.sets(sessionExerciseId).firstOrNull { it.id == setId }
            ?: throw NativeWorkoutException("Set not found.")
        val next = current.copy(
            load = load,
            reps = reps,
            durationSeconds = durationSeconds,
            distanceMetres = distanceMetres,
            completedAt = if (logged) current.completedAt ?: timestamp() else null,
        )
        dao.upsertSets(listOf(next))
        next
    }

    suspend fun setExerciseCompleted(
        sessionId: String,
        sessionExerciseId: String,
        completed: Boolean,
    ): SessionExerciseEntity = database.withTransaction {
        val current = dao.sessionExercises(sessionId).firstOrNull { it.id == sessionExerciseId }
            ?: throw NativeWorkoutException("Exercise not found in the active workout.")
        val now = timestamp()
        val next = current.copy(
            status = if (completed) "completed" else "active",
            startedAt = current.startedAt ?: now,
            completedAt = if (completed) now else null,
        )
        dao.upsertSessionExercises(listOf(next))
        next
    }

    suspend fun completeSession(sessionId: String): ActiveWorkout = database.withTransaction {
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status == "completed") return@withTransaction activeWorkout(sessionId)

        val now = timestamp()
        val exercises = dao.sessionExercises(sessionId)
        dao.upsertSessionExercises(
            exercises.map { exercise ->
                if (exercise.status == "completed") exercise
                else if (!exercise.prescriptionIncluded) exercise.copy(status = "skipped")
                else exercise.copy(status = "skipped")
            },
        )
        val completedSession = session.copy(
            status = "completed",
            completedAt = now,
            healthExportState = "queued",
        )
        dao.upsertSessions(listOf(completedSession))

        var state = dao.appState() ?: throw NativeWorkoutException("App state is missing.")
        if (session.daySymbol in CORE_DAYS) {
            dao.upsertCompletedDays(listOf(CycleCompletedDayEntity(session.cycleId, session.daySymbol)))
        } else if (session.daySymbol == "&") {
            val cycle = dao.trainingCycle(session.cycleId)
                ?: throw NativeWorkoutException("Training cycle is missing.")
            dao.upsertTrainingCycles(listOf(cycle.copy(andCompleted = true, status = "closed", endedAt = now)))
            val nextCycle = TrainingCycleEntity(
                id = id("cycle"),
                startedAt = now,
                endedAt = null,
                status = "active",
                andCompleted = false,
            )
            dao.upsertTrainingCycles(listOf(nextCycle))
            state = state.copy(currentCycleId = nextCycle.id)
        }

        dao.upsertAppState(state.copy(activeSessionId = null, updatedAt = now))
        activeWorkout(sessionId)
    }

    private suspend fun planForRoutine(
        routineVersionId: String,
        day: String,
        mode: TrainingMode,
    ): NativeWorkoutPlan {
        val slots = dao.routineSlots(routineVersionId, day)
        val targetEntities = dao.programmeTargets(routineVersionId, day)
        val targets = targetEntities.map(ProgrammeTargetEntity::toDomain)
        val constraints = dao.programmeModeConstraint(routineVersionId, day, mode.code)?.toDomain()
            ?: throw NativeWorkoutException("$day has no ${mode.label} session constraints.")
        val targetResolutions = targetResolver.resolve(targets, constraints)
        if (slots.isEmpty()) return NativeWorkoutPlan(
            routineVersionId = routineVersionId,
            day = day,
            mode = mode,
            constraints = constraints,
            targetResolutions = targetResolutions,
            exercises = emptyList(),
            candidateDecisions = emptyList(),
        )

        val exerciseById = dao.exercises(slots.map { it.exerciseId }.distinct()).associateBy { it.id }
        val profilesByExercise = dao.executionProfiles(exerciseById.keys.toList()).groupBy { it.exerciseId }
        val defaultProfileByExercise = exerciseById.keys.associateWith { exerciseId ->
            profilesByExercise[exerciseId]
                .orEmpty()
                .singleOrNull { it.isDefault }
                ?: throw NativeWorkoutException("Exercise $exerciseId must have exactly one default execution profile.")
        }
        val recruitmentByProfile = dao.recruitmentAllocations(defaultProfileByExercise.values.map { it.id })
            .groupBy { it.executionProfileId }
        val targetBySegment = targetEntities.associateBy { it.muscleSegmentId }
        val sourceByPreferenceId = linkedMapOf<String, SourceExercise>()
        val candidates = slots.filter { it.preferredSets > 0 }.map { slot ->
            val exercise = exerciseById[slot.exerciseId]
                ?: throw NativeWorkoutException("Routine slot ${slot.id} references a missing exercise.")
            val profile = defaultProfileByExercise.getValue(exercise.id)
            val source = SourceExercise(
                slot = slot,
                exercise = exercise,
                executionProfile = profile,
                recruitment = recruitmentByProfile[profile.id].orEmpty(),
            )
            sourceByPreferenceId[slot.id] = source

            val targetCoverage = linkedMapOf<TrainingTargetId, Double>()
            source.recruitment
                .filter { it.weighting > 0.0 && !it.role.equals("stabiliser", ignoreCase = true) }
                .forEach { allocation ->
                    val target = targetBySegment[allocation.muscleSegmentId] ?: return@forEach
                    val targetId = TrainingTargetId(target.id)
                    val coverage = allocation.weighting * allocation.confidence
                    targetCoverage[targetId] = maxOf(targetCoverage[targetId] ?: 0.0, coverage)
                }

            ExerciseSelectionCandidate(
                preferenceId = slot.id,
                exerciseId = ExerciseId(exercise.id),
                executionProfileId = ExecutionProfileId(profile.id),
                ordinal = slot.position,
                preferencePriority = slot.importance.toTargetPriority(),
                preferredSetCap = slot.preferredSets,
                repRange = slot.repMin..slot.repMax,
                restSeconds = slot.restSeconds,
                targetCoverage = targetCoverage,
            )
        }

        val selections = exerciseSelector.select(targetResolutions, candidates, constraints)
        val selectedByPreferenceId = selections.associateBy { it.candidate.preferenceId }
        val includedTargetIds = targetResolutions.filter { it.included }.mapTo(hashSetOf()) { it.target.id }
        val candidateDecisions = candidates.map { candidate ->
            val source = sourceByPreferenceId.getValue(candidate.preferenceId)
            val selection = selectedByPreferenceId[candidate.preferenceId]
            val coversIncludedTarget = candidate.targetCoverage.keys.any { it in includedTargetIds }
            WorkoutCandidateDecision(
                slotId = source.slot.id,
                exerciseId = source.exercise.id,
                exerciseName = source.exercise.name,
                executionProfileId = source.executionProfile.id,
                executionProfileName = source.executionProfile.name,
                preferencePriority = candidate.preferencePriority,
                targetCoverage = candidate.targetCoverage.mapKeys { it.key.value },
                selected = selection != null,
                selectedSets = selection?.sets,
                decisionReason = selection?.reason ?: when {
                    !coversIncludedTarget && candidate.targetCoverage.isNotEmpty() -> "no_included_target_coverage"
                    candidate.targetCoverage.isEmpty() && candidate.preferencePriority < constraints.targetPriorityFloor ->
                        "below_target_priority_floor"
                    else -> "not_selected_within_budget"
                },
            )
        }
        val inferenceSnapshot = inferenceRepository.latestSnapshot()
        val translationByProfile = inferenceSnapshot
            ?.exerciseTranslationStates
            .orEmpty()
            .associateBy { it.executionProfileId.value }
        val planned = selections.map { selection ->
            val candidate = selection.candidate
            val source = sourceByPreferenceId.getValue(candidate.preferenceId)
            val translation = translationByProfile[source.executionProfile.id]
            val rawAnchor = if (translation?.observedLoadAnchor == null) {
                dao.latestCompletedLoadForExecutionProfile(source.executionProfile.id)
            } else {
                null
            }
            val loadEvidence = SameProfileLoadEvidenceResolver.resolve(
                inferredLoad = translation?.observedLoadAnchor?.value,
                inferredSetRecordId = translation?.observedLoadAnchor?.sourceId,
                inferenceRunId = inferenceSnapshot?.run?.id?.value,
                rawLoad = rawAnchor?.load,
                rawSetRecordId = rawAnchor?.id,
            )
            val generated = prescriptionEngine.generate(
                PrescriptionRequest(
                    exerciseId = ExerciseId(source.exercise.id),
                    executionProfileId = ExecutionProfileId(source.executionProfile.id),
                    targetIds = selection.targetIds,
                    sets = selection.sets,
                    repRange = candidate.repRange,
                    loadEvidence = loadEvidence,
                    permitsExternalLoad = source.exercise.trackingMetric == "load_reps" &&
                        source.exercise.loadRelationship !in setOf("bodyweight", "none"),
                    loadResolution = source.executionProfile.toLoadResolution(),
                    restSeconds = candidate.restSeconds,
                ),
            )
            PlannedWorkoutExercise(
                slotId = source.slot.id,
                name = source.exercise.name,
                importance = source.slot.importance.toImportance(),
                defaultUnit = source.exercise.defaultUnit,
                trackingMetric = source.exercise.trackingMetric,
                loadRelationship = source.exercise.loadRelationship,
                entryBasis = source.exercise.entryBasis,
                executionProfileName = source.executionProfile.name,
                prescription = generated,
                movementReason = selection.reason,
                estimatedDurationSeconds = selection.estimatedDurationSeconds,
            )
        }

        return NativeWorkoutPlan(
            routineVersionId = routineVersionId,
            day = day,
            mode = mode,
            constraints = constraints,
            targetResolutions = targetResolutions,
            exercises = planned,
            candidateDecisions = candidateDecisions,
        )
    }

    private fun PlannedWorkoutExercise.toSessionExercise(
        sessionExerciseId: String,
        sessionId: String,
        position: Int,
        mode: TrainingMode,
        bodyweight: Double?,
        movementReason: String,
    ): SessionExerciseEntity = SessionExerciseEntity(
        id = sessionExerciseId,
        sessionId = sessionId,
        position = position,
        exerciseId = prescription.exerciseId.value,
        slotId = slotId,
        exerciseNameSnapshot = name,
        importanceSnapshot = importance.name.lowercase(),
        trackingMetricSnapshot = trackingMetric,
        loadRelationshipSnapshot = loadRelationship,
        entryBasisSnapshot = entryBasis,
        bodyweightSnapshotKg = bodyweight,
        executionProfileId = prescription.executionProfileId.value,
        executionProfileNameSnapshot = executionProfileName,
        prescribedLoad = prescription.prescribedLoad,
        prescribedLoadEvidenceSource = prescription.loadEvidence?.source,
        prescribedLoadEvidenceSetId = prescription.loadEvidence?.sourceSetRecordId,
        prescribedLoadInferenceRunId = prescription.loadEvidence?.inferenceRunId,
        prescribedLoadAnchor = prescription.loadEvidence?.anchorLoad,
        prescriptionMode = mode.code,
        prescriptionIncluded = true,
        prescribedSets = prescription.sets,
        repMin = prescription.repRange.first,
        repMax = prescription.repRange.last,
        restSeconds = prescription.restSeconds,
        generatedByModelVersion = prescription.generatedByModelVersion,
        deferToAnd = false,
        status = "planned",
        note = null,
        startedAt = null,
        completedAt = null,
        movementReason = movementReason,
        substitutedFromExerciseId = null,
    )

    private fun prescribedSets(
        sessionExerciseId: String,
        planned: PlannedWorkoutExercise,
        indices: Iterable<Int>,
    ): List<SetRecordEntity> = prescribedSets(
        sessionExerciseId = sessionExerciseId,
        trackingMetric = planned.trackingMetric,
        loadRelationship = planned.loadRelationship,
        defaultUnit = planned.defaultUnit,
        prescription = planned.prescription,
        indices = indices,
    )

    private fun prescribedSets(
        sessionExerciseId: String,
        trackingMetric: String,
        loadRelationship: String,
        defaultUnit: String,
        prescription: ExercisePrescription,
        indices: Iterable<Int>,
    ): List<SetRecordEntity> = indices.map { setIndex ->
        val startsWithLoad = trackingMetric == "load_reps" && loadRelationship != "bodyweight"
        SetRecordEntity(
            id = id("set"),
            sessionExerciseId = sessionExerciseId,
            setIndex = setIndex,
            load = if (startsWithLoad) prescription.prescribedLoad else null,
            reps = null,
            durationSeconds = null,
            distanceMetres = null,
            unit = defaultUnit,
            completedAt = null,
            note = null,
            warmUp = false,
            kind = "prescribed",
        )
    }

    private companion object {
        const val USER_SUBSTITUTION_REASON = "user_substitution"
    }
}

private data class SourceExercise(
    val slot: RoutineSlotEntity,
    val exercise: ExerciseEntity,
    val executionProfile: ExerciseExecutionProfileEntity,
    val recruitment: List<RecruitmentAllocationEntity>,
)

internal fun matchedSwapTargetCoverage(
    targetsBySegment: Map<String, SessionTargetEntity>,
    recruitment: List<RecruitmentAllocationEntity>,
): Map<TrainingTargetId, Double> {
    val matched = linkedMapOf<TrainingTargetId, Double>()
    recruitment
        .filter { it.weighting > 0.0 && !it.role.equals("stabiliser", ignoreCase = true) }
        .forEach { allocation ->
            val target = targetsBySegment[allocation.muscleSegmentId] ?: return@forEach
            val targetId = TrainingTargetId(target.id)
            val coverage = allocation.weighting * allocation.confidence
            matched[targetId] = maxOf(matched[targetId] ?: 0.0, coverage)
        }
    return matched
}

private fun ProgrammeTargetEntity.toDomain(): TrainingTarget = TrainingTarget(
    id = TrainingTargetId(id),
    segmentId = MuscleSegmentId(muscleSegmentId),
    priority = priority,
    desiredStimulus = desiredStimulus,
    source = TargetSource(source),
)

private fun SessionTargetEntity.toDomain(): TrainingTarget = TrainingTarget(
    id = TrainingTargetId(id),
    segmentId = MuscleSegmentId(muscleSegmentId),
    priority = resolvedPriority,
    desiredStimulus = desiredStimulus,
    source = TargetSource(source),
)

private fun ResolvedTrainingTarget.toSessionTarget(sessionId: String): SessionTargetEntity = SessionTargetEntity(
    id = "session_target:$sessionId:${target.id.value}",
    sessionId = sessionId,
    programmeTargetId = target.id.value,
    muscleSegmentId = target.segmentId.value,
    priority = target.priority,
    desiredStimulus = target.desiredStimulus,
    source = target.source.description,
    included = included,
    resolvedPriority = resolvedPriority,
    resolutionModelVersion = resolutionModelVersion,
)

private fun ProgrammeModeConstraintEntity.toDomain(): SessionConstraints = SessionConstraints(
    mode = mode,
    workingSetBudget = workingSetBudget,
    exerciseBudget = exerciseBudget,
    minimumSetsPerExercise = minimumSetsPerExercise,
    targetPriorityFloor = targetPriorityFloor,
    timeBudgetSeconds = timeBudgetSeconds,
    source = source,
    resolverModelVersion = resolverModelVersion,
)

private fun SessionConstraints.toSessionConstraint(sessionId: String): SessionConstraintEntity =
    SessionConstraintEntity(
        sessionId = sessionId,
        mode = mode,
        workingSetBudget = workingSetBudget,
        exerciseBudget = exerciseBudget,
        minimumSetsPerExercise = minimumSetsPerExercise,
        targetPriorityFloor = targetPriorityFloor,
        timeBudgetSeconds = timeBudgetSeconds,
        source = source,
        resolverModelVersion = resolverModelVersion,
    )

private fun PlannedWorkoutExercise.toSessionExerciseTargets(
    sessionExerciseId: String,
    sessionTargetByProgrammeId: Map<String?, SessionTargetEntity>,
): List<SessionExerciseTargetEntity> = prescription.targetIds.map { targetId ->
    val sessionTarget = sessionTargetByProgrammeId[targetId.value]
        ?: throw NativeWorkoutException("Prescription references missing target ${targetId.value}.")
    SessionExerciseTargetEntity(sessionExerciseId, sessionTarget.id)
}

private fun SessionExerciseEntity.toPrescription(
    targetIds: List<TrainingTargetId>,
): ExercisePrescription = ExercisePrescription(
    exerciseId = ExerciseId(exerciseId),
    executionProfileId = ExecutionProfileId(executionProfileId),
    targetIds = targetIds.distinct(),
    sets = prescribedSets,
    repRange = repMin..repMax,
    prescribedLoad = prescribedLoad,
    loadEvidence = prescribedLoadEvidenceSource?.let { source ->
        prescribedLoadAnchor?.let { anchor ->
            PrescriptionLoadEvidence(
                source = source,
                anchorLoad = anchor,
                sourceSetRecordId = prescribedLoadEvidenceSetId,
                inferenceRunId = prescribedLoadInferenceRunId,
            )
        }
    },
    restSeconds = restSeconds,
    generatedByModelVersion = generatedByModelVersion,
)

private fun ExerciseExecutionProfileEntity.toLoadResolution(): LoadResolution? {
    val allowedValues = allowedLoadsJson?.let { encoded ->
        JSONArray(encoded).let { array -> List(array.length()) { array.getDouble(it) } }
    }.orEmpty()
    return if (
        minimumLoad != null || maximumLoad != null || loadIncrement != null || allowedValues.isNotEmpty()
    ) {
        LoadResolution(
            minimumLoad = minimumLoad,
            maximumLoad = maximumLoad,
            increment = loadIncrement,
            allowedValues = allowedValues,
        )
    } else {
        null
    }
}

private fun String.toImportance(): ExerciseImportance = when (lowercase()) {
    "principal" -> ExerciseImportance.PRINCIPAL
    "core" -> ExerciseImportance.CORE
    "accessory" -> ExerciseImportance.ACCESSORY
    else -> ExerciseImportance.CORE
}

private fun String.toTargetPriority(): Double = when (lowercase()) {
    "principal" -> 1.0
    "core" -> 0.7
    "accessory" -> 0.4
    else -> 0.7
}

private fun id(prefix: String): String = "${prefix}_${UUID.randomUUID()}"

private fun timestamp(): String = Instant.now().toString()
