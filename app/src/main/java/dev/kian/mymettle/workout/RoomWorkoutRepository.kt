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
import dev.kian.mymettle.domain.training.ResolvedTrainingTarget
import dev.kian.mymettle.domain.training.SessionConstraints
import dev.kian.mymettle.domain.training.TargetSource
import dev.kian.mymettle.domain.training.TrainingTarget
import dev.kian.mymettle.domain.training.TrainingTargetId
import dev.kian.mymettle.engine.prescription.HistoryBackedPrescriptionEngine
import dev.kian.mymettle.engine.prescription.PrescriptionEngine
import dev.kian.mymettle.engine.prescription.PrescriptionRequest
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

data class NativeWorkoutPlan(
    val routineVersionId: String,
    val day: String,
    val mode: TrainingMode,
    val constraints: SessionConstraints,
    val targetResolutions: List<ResolvedTrainingTarget>,
    val exercises: List<PlannedWorkoutExercise>,
) {
    val targets: List<TrainingTarget> get() = targetResolutions.filter { it.included }.map { it.target }
    val workingSetCount: Int get() = exercises.sumOf { it.prescription.sets }
    val estimatedDurationSeconds: Int get() = exercises.sumOf { it.estimatedDurationSeconds }
}

data class ActiveWorkoutExercise(
    val entity: SessionExerciseEntity,
    val targetIds: List<TrainingTargetId>,
    val sets: List<SetRecordEntity>,
    val previousCompletedSets: List<SetRecordEntity>,
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

    suspend fun hasImportedProgramme(): Boolean = dao.appState() != null && dao.profileCount() > 0

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
            ActiveWorkoutExercise(
                entity = exercise,
                targetIds = dao.sessionExerciseTargets(exercise.id).map { TrainingTargetId(it.sessionTargetId) },
                sets = dao.sets(exercise.id),
                previousCompletedSets = dao.latestCompletedSetsForExercise(
                    exerciseId = exercise.exerciseId,
                    excludeSessionId = sessionId,
                    limit = 12,
                ),
            )
        }
        return ActiveWorkout(session, targets, exercises)
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
                updatedExercises += current.copy(
                    position = position,
                    importanceSnapshot = planned.importance.name.lowercase(),
                    executionProfileId = planned.prescription.executionProfileId.value,
                    executionProfileNameSnapshot = planned.executionProfileName,
                    prescribedLoad = planned.prescription.prescribedLoad,
                    prescriptionMode = mode.code,
                    prescriptionIncluded = true,
                    prescribedSets = planned.prescription.sets,
                    repMin = planned.prescription.repRange.first,
                    repMax = planned.prescription.repRange.last,
                    targetRir = planned.prescription.targetRir,
                    restSeconds = planned.prescription.restSeconds,
                    generatedByModelVersion = planned.prescription.generatedByModelVersion,
                    deferToAnd = false,
                    movementReason = planned.movementReason,
                )
                dao.deleteSessionExerciseTargets(current.id)
                updatedExerciseTargets += planned.toSessionExerciseTargets(
                    current.id,
                    sessionTargetByProgrammeId,
                )

                val currentSets = dao.sets(current.id)
                val currentIndices = currentSets.mapTo(mutableSetOf()) { it.setIndex }
                val missingIndices = (0 until planned.prescription.sets).filterNot { it in currentIndices }
                updatedSets += prescribedSets(current.id, planned, missingIndices)
                updatedSets += currentSets
                    .filter { it.setIndex >= planned.prescription.sets && it.completedAt != null && it.kind == "prescribed" }
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

    suspend fun saveSet(
        sessionExerciseId: String,
        setId: String,
        load: Double?,
        reps: Int?,
        durationSeconds: Int? = null,
        distanceMetres: Double? = null,
        logged: Boolean,
        rir: Double? = null,
        effortSource: String? = null,
    ): SetRecordEntity = database.withTransaction {
        if (rir != null && rir !in 0.0..10.0) {
            throw NativeWorkoutException("RIR must be between 0 and 10.")
        }
        val current = dao.sets(sessionExerciseId).firstOrNull { it.id == setId }
            ?: throw NativeWorkoutException("Set not found.")
        val next = current.copy(
            load = load,
            reps = reps,
            durationSeconds = durationSeconds,
            distanceMetres = distanceMetres,
            completedAt = if (logged) current.completedAt ?: timestamp() else null,
            rir = rir,
            effortSource = effortSource,
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
                targetRir = null,
                restSeconds = slot.restSeconds,
                targetCoverage = targetCoverage,
            )
        }

        val selections = exerciseSelector.select(targetResolutions, candidates, constraints)
        val translationByProfile = inferenceRepository.latestSnapshot()
            ?.exerciseTranslationStates
            .orEmpty()
            .associateBy { it.executionProfileId.value }
        val planned = selections.map { selection ->
            val candidate = selection.candidate
            val source = sourceByPreferenceId.getValue(candidate.preferenceId)
            val observedLoadAnchor = translationByProfile[source.executionProfile.id]
                ?.observedLoadAnchor
                ?.value
                ?: dao.latestCompletedLoadForExecutionProfile(source.executionProfile.id)?.load
            val generated = prescriptionEngine.generate(
                PrescriptionRequest(
                    exerciseId = ExerciseId(source.exercise.id),
                    executionProfileId = ExecutionProfileId(source.executionProfile.id),
                    targetIds = selection.targetIds,
                    sets = selection.sets,
                    repRange = candidate.repRange,
                    targetRir = candidate.targetRir,
                    previousPerformedLoad = observedLoadAnchor,
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
        prescriptionMode = mode.code,
        prescriptionIncluded = true,
        prescribedSets = prescription.sets,
        repMin = prescription.repRange.first,
        repMax = prescription.repRange.last,
        targetRir = prescription.targetRir,
        restSeconds = prescription.restSeconds,
        generatedByModelVersion = prescription.generatedByModelVersion,
        deferToAnd = false,
        status = "planned",
        note = null,
        startedAt = null,
        completedAt = null,
        movementReason = movementReason,
    )

    private fun prescribedSets(
        sessionExerciseId: String,
        planned: PlannedWorkoutExercise,
        indices: Iterable<Int>,
    ): List<SetRecordEntity> = indices.map { setIndex ->
        val startsWithLoad = planned.trackingMetric == "load_reps" && planned.loadRelationship != "bodyweight"
        SetRecordEntity(
            id = id("set"),
            sessionExerciseId = sessionExerciseId,
            setIndex = setIndex,
            load = if (startsWithLoad) planned.prescription.prescribedLoad else null,
            reps = null,
            durationSeconds = null,
            distanceMetres = null,
            unit = planned.defaultUnit,
            completedAt = null,
            note = null,
            rir = null,
            effortSource = null,
            warmUp = false,
            kind = "prescribed",
        )
    }
}

private data class SourceExercise(
    val slot: RoutineSlotEntity,
    val exercise: ExerciseEntity,
    val executionProfile: ExerciseExecutionProfileEntity,
    val recruitment: List<RecruitmentAllocationEntity>,
)

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
