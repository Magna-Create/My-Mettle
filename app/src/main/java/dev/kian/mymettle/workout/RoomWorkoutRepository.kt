package dev.kian.mymettle.workout

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.dao.MetricEvidenceRow
import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.CycleCompletedDayEntity
import dev.kian.mymettle.data.local.entity.ExecutionProfileVersionEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ProgrammeModeConstraintEntity
import dev.kian.mymettle.data.local.entity.ProgrammeTargetEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.SessionConstraintEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseTargetEntity
import dev.kian.mymettle.data.local.entity.SessionMetricTargetEntity
import dev.kian.mymettle.data.local.entity.SessionSetPrescriptionEntity
import dev.kian.mymettle.data.local.entity.SessionTargetEntity
import dev.kian.mymettle.data.local.entity.SetDraftMetricValueEntity
import dev.kian.mymettle.data.local.entity.SetObservationEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.data.local.toDomain
import dev.kian.mymettle.data.local.toEntity
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.evidence.AcquisitionMethod
import dev.kian.mymettle.domain.evidence.EvidenceGranularity
import dev.kian.mymettle.domain.evidence.EvidenceQuality
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.MetricTarget
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.PerformanceObservation
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.PerformanceTargetTemplate
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.SchemaMetric
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitConverter
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.domain.evidence.TimingQuality
import dev.kian.mymettle.domain.training.ExercisePrescription
import dev.kian.mymettle.domain.training.ResolvedTrainingTarget
import dev.kian.mymettle.domain.training.SessionConstraints
import dev.kian.mymettle.domain.training.SetPrescription
import dev.kian.mymettle.domain.training.TargetSource
import dev.kian.mymettle.domain.training.TrainingTarget
import dev.kian.mymettle.domain.training.TrainingTargetId
import dev.kian.mymettle.engine.prescription.HistoryBackedPrescriptionEngine
import dev.kian.mymettle.engine.prescription.PrescriptionEngine
import dev.kian.mymettle.engine.prescription.PrescriptionRequest
import dev.kian.mymettle.engine.prescription.SameProfileMetricEvidenceResolver
import dev.kian.mymettle.engine.targeting.BudgetedTargetExerciseSelector
import dev.kian.mymettle.engine.targeting.ConstraintTargetResolver
import dev.kian.mymettle.engine.targeting.ExerciseSelectionCandidate
import dev.kian.mymettle.engine.targeting.ExerciseSelector
import dev.kian.mymettle.engine.targeting.TargetResolver
import dev.kian.mymettle.inference.RoomInferenceRepository
import java.time.Instant
import java.util.UUID

private val CORE_DAYS = setOf("ψ", "φ", "π")

class NativeWorkoutException(message: String) : IllegalStateException(message)

data class PlannedWorkoutExercise(
    val slotId: String,
    val name: String,
    val importance: ExerciseImportance,
    val executionProfileName: String,
    val schema: PerformanceSchema,
    val resistanceSemantics: ResistanceSemantics,
    val entryBasis: EntryBasis,
    val prescription: ExercisePrescription,
    val movementReason: String,
    val estimatedDurationSeconds: Int,
) {
    val defaultUnit: String get() = schema.metrics.first().defaultUnit.storageValue
    val trackingMetric: String get() = schema.compatibilityTrackingMetric()
    val loadRelationship: String get() = resistanceSemantics.storageValue
}

data class WorkoutCandidateDecision(
    val slotId: String,
    val exerciseId: String,
    val exerciseName: String,
    val executionProfileId: String,
    val executionProfileVersionId: String,
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
    val executionProfileVersionId: String,
    val executionProfileName: String,
    val schema: PerformanceSchema,
    val resistanceSemantics: ResistanceSemantics,
    val entryBasis: EntryBasis,
    val matchedTargetIds: List<TrainingTargetId>,
    val targetCoverageScore: Double,
    val prescription: ExercisePrescription,
) {
    val trackingMetric: String get() = schema.compatibilityTrackingMetric()
    val loadRelationship: String get() = resistanceSemantics.storageValue
    val defaultUnit: String get() = schema.metrics.first().defaultUnit.storageValue
}

data class ActiveWorkoutExercise(
    val entity: SessionExerciseEntity,
    val targetIds: List<TrainingTargetId>,
    val schema: PerformanceSchema,
    val resistanceSemantics: ResistanceSemantics,
    val entryBasis: EntryBasis,
    val lateralityMode: LateralityMode,
    val prescription: ExercisePrescription,
    val sets: List<PerformanceSetRecord>,
    val previousCompletedSets: List<PerformanceSetRecord>,
)

data class ActiveWorkout(
    val session: SessionEntity,
    val targets: List<TrainingTarget>,
    val exercises: List<ActiveWorkoutExercise>,
)

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
            cycle = TrainingCycleEntity(id("cycle"), now, null, "active", false)
            dao.upsertTrainingCycles(listOf(cycle))
            state = state.copy(currentCycleId = cycle.id, updatedAt = now)
        }

        val workoutPlan = planForRoutine(state.currentRoutineVersionId, day, mode)
        if (workoutPlan.exercises.isEmpty()) throw NativeWorkoutException("$day has no exercises in ${mode.label}.")
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
        val sessionTargets = workoutPlan.targetResolutions.map { it.toSessionTarget(sessionId) }
        val sessionTargetByProgrammeId = sessionTargets.associateBy { it.programmeTargetId }

        dao.upsertSessions(listOf(session))
        dao.upsertSessionConstraint(workoutPlan.constraints.toSessionConstraint(sessionId))
        dao.upsertSessionTargets(sessionTargets)
        workoutPlan.exercises.forEachIndexed { position, planned ->
            val sessionExerciseId = id("session_exercise")
            dao.upsertSessionExercises(
                listOf(planned.toSessionExercise(sessionExerciseId, sessionId, position, mode, planned.movementReason)),
            )
            dao.upsertSessionExerciseTargets(planned.toSessionExerciseTargets(sessionExerciseId, sessionTargetByProgrammeId))
            persistPrescription(sessionExerciseId, planned.prescription)
            dao.upsertSets(planned.prescription.setPrescriptions.map { prescribedSet ->
                SetRecordEntity(
                    id = id("set"),
                    sessionExerciseId = sessionExerciseId,
                    setIndex = prescribedSet.index,
                    note = null,
                    warmUp = prescribedSet.kind == "warm_up",
                    kind = prescribedSet.kind,
                    createdAt = now,
                )
            })
        }
        dao.upsertAppState(state.copy(activeSessionId = sessionId, updatedAt = now))
        activeWorkout(sessionId)
    }

    suspend fun activeWorkout(): ActiveWorkout? = dao.appState()?.activeSessionId?.let { activeWorkout(it) }

    suspend fun activeWorkout(sessionId: String): ActiveWorkout {
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Active workout is missing.")
        val targets = dao.sessionTargets(sessionId).filter { it.included }.map(SessionTargetEntity::toDomain)
        val entities = dao.sessionExercises(sessionId)
        val bundles = loadVersionBundles(entities.map { it.executionProfileVersionId })
        val exercises = entities.map { entity ->
            val bundle = bundles.getValue(entity.executionProfileVersionId)
            val prescription = loadPrescription(entity, bundle.schema)
            val setRecords = dao.sets(entity.id)
            ActiveWorkoutExercise(
                entity = entity,
                targetIds = dao.sessionExerciseTargets(entity.id).map { TrainingTargetId(it.sessionTargetId) },
                schema = bundle.schema,
                resistanceSemantics = bundle.resistanceSemantics,
                entryBasis = bundle.entryBasis,
                lateralityMode = bundle.lateralityMode,
                prescription = prescription,
                sets = loadPerformanceSets(setRecords, prescription),
                previousCompletedSets = loadPerformanceSets(
                    dao.latestCompletedSetsForExercise(entity.exerciseId, excludeSessionId = sessionId, limit = 12),
                    prescription = null,
                ),
            )
        }
        return ActiveWorkout(session, targets, exercises)
    }

    /** Shared generic read model for workout and history surfaces. */
    suspend fun performanceSets(sessionExerciseId: String): List<PerformanceSetRecord> {
        val exercise = dao.sessionExercise(sessionExerciseId)
            ?: throw NativeWorkoutException("Exercise not found.")
        val bundle = loadVersionBundles(listOf(exercise.executionProfileVersionId))
            .getValue(exercise.executionProfileVersionId)
        return loadPerformanceSets(dao.sets(sessionExerciseId), loadPrescription(exercise, bundle.schema))
    }

    suspend fun changeSessionMode(sessionId: String, mode: TrainingMode): ActiveWorkout = database.withTransaction {
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status != "active") throw NativeWorkoutException("Only an active workout can change mode.")
        if (session.mode == mode.code) return@withTransaction activeWorkout(sessionId)

        val target = planForRoutine(session.routineVersionId, session.daySymbol, mode)
        val resolvedTargets = target.targetResolutions.map { it.toSessionTarget(sessionId) }
        dao.upsertSessionTargets(resolvedTargets)
        dao.upsertSessionConstraint(target.constraints.toSessionConstraint(sessionId))
        val targetByProgrammeId = resolvedTargets.associateBy { it.programmeTargetId }
        val existing = dao.sessionExercises(sessionId)
        val existingBySlot = existing.associateBy { it.slotId }
        val targetSlots = target.exercises.mapTo(mutableSetOf()) { it.slotId }
        dao.offsetSessionExercisePositions(sessionId)

        target.exercises.forEachIndexed { position, planned ->
            val current = existingBySlot[planned.slotId]
            if (current == null) {
                val exerciseId = id("session_exercise")
                dao.upsertSessionExercises(listOf(planned.toSessionExercise(exerciseId, sessionId, position, mode, planned.movementReason)))
                dao.upsertSessionExerciseTargets(planned.toSessionExerciseTargets(exerciseId, targetByProgrammeId))
                persistPrescription(exerciseId, planned.prescription)
                dao.upsertSets(planned.prescription.setPrescriptions.map { set ->
                    SetRecordEntity(id("set"), exerciseId, set.index, null, set.kind == "warm_up", set.kind, timestamp())
                })
            } else {
                val substituted = current.substitutedFromExerciseId != null
                val currentBundle = loadVersionBundles(listOf(current.executionProfileVersionId)).getValue(current.executionProfileVersionId)
                val existingPrescription = loadPrescription(current, currentBundle.schema)
                val effective = if (substituted) existingPrescription.resized(planned.prescription.sets) else planned.prescription
                dao.upsertSessionExercises(
                    listOf(
                        current.copy(
                            position = position,
                            importanceSnapshot = planned.importance.name.lowercase(),
                            executionProfileId = effective.executionProfileId.value,
                            executionProfileVersionId = effective.executionProfileVersionId.value,
                            executionProfileNameSnapshot = if (substituted) current.executionProfileNameSnapshot else planned.executionProfileName,
                            prescriptionMode = mode.code,
                            prescriptionIncluded = true,
                            restSeconds = effective.restSeconds,
                            generatedByModelVersion = effective.generatedByModelVersion,
                            deferToAnd = false,
                            movementReason = if (substituted) USER_SUBSTITUTION_REASON else planned.movementReason,
                        ),
                    ),
                )
                if (!substituted) {
                    dao.deleteSessionExerciseTargets(current.id)
                    dao.upsertSessionExerciseTargets(planned.toSessionExerciseTargets(current.id, targetByProgrammeId))
                }
                persistPrescription(current.id, effective)
                val currentSets = dao.sets(current.id)
                val currentIndices = currentSets.mapTo(mutableSetOf()) { it.setIndex }
                val missing = effective.setPrescriptions.filterNot { it.index in currentIndices }
                if (missing.isNotEmpty()) {
                    dao.upsertSets(missing.map { set ->
                        SetRecordEntity(id("set"), current.id, set.index, null, set.kind == "warm_up", set.kind, timestamp())
                    })
                }
            }
        }

        existing.filterNot { it.slotId in targetSlots }.sortedBy { it.position }.forEachIndexed { index, current ->
            dao.upsertSessionExercises(
                listOf(current.copy(position = target.exercises.size + index, prescriptionMode = mode.code, prescriptionIncluded = false)),
            )
        }
        dao.upsertSessions(listOf(session.copy(mode = mode.code, editedAt = timestamp())))
        activeWorkout(sessionId)
    }

    suspend fun swapOptions(sessionExerciseId: String): List<ExerciseSwapOption> = buildSwapOptions(sessionExerciseId)

    suspend fun swapExercise(
        sessionExerciseId: String,
        replacementExecutionProfileId: String,
    ): ActiveWorkout = database.withTransaction {
        val current = dao.sessionExercise(sessionExerciseId)
            ?: throw NativeWorkoutException("Exercise not found in the workout.")
        val session = dao.session(current.sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status != "active") throw NativeWorkoutException("Only an active workout can change exercises.")
        if (current.status == "completed" || hasCurrentObservations(dao.sets(current.id))) {
            throw NativeWorkoutException("An exercise cannot be swapped after one of its sets has been logged.")
        }
        val replacement = buildSwapOptions(current.id)
            .firstOrNull { it.executionProfileId == replacementExecutionProfileId }
            ?: throw NativeWorkoutException("That replacement is no longer compatible with this session target.")
        val prescription = replacement.prescription
        dao.deleteSessionExerciseTargets(current.id)
        dao.deleteSets(current.id)
        dao.upsertSessionExercises(
            listOf(
                current.copy(
                    exerciseId = replacement.exerciseId,
                    exerciseNameSnapshot = replacement.exerciseName,
                    executionProfileId = replacement.executionProfileId,
                    executionProfileVersionId = replacement.executionProfileVersionId,
                    executionProfileNameSnapshot = replacement.executionProfileName,
                    restSeconds = prescription.restSeconds,
                    generatedByModelVersion = prescription.generatedByModelVersion,
                    status = "planned",
                    startedAt = null,
                    completedAt = null,
                    movementReason = USER_SUBSTITUTION_REASON,
                    substitutedFromExerciseId = current.substitutedFromExerciseId ?: current.exerciseId,
                ),
            ),
        )
        if (replacement.matchedTargetIds.isNotEmpty()) {
            dao.upsertSessionExerciseTargets(replacement.matchedTargetIds.map { SessionExerciseTargetEntity(current.id, it.value) })
        }
        persistPrescription(current.id, prescription)
        dao.upsertSets(prescription.setPrescriptions.map { set ->
            SetRecordEntity(id("set"), current.id, set.index, null, set.kind == "warm_up", set.kind, timestamp())
        })
        dao.upsertSessions(listOf(session.copy(editedAt = timestamp())))
        activeWorkout(session.id)
    }

    suspend fun saveSet(
        sessionExerciseId: String,
        setId: String,
        load: Double?,
        reps: Int?,
        durationSeconds: Int? = null,
        distanceMetres: Double? = null,
        additionalValues: List<PerformanceMetricValue> = emptyList(),
        laterality: Laterality? = null,
        logged: Boolean,
    ): PerformanceSetRecord = database.withTransaction {
        val exercise = dao.sessionExercise(sessionExerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        val record = dao.sets(sessionExerciseId).firstOrNull { it.id == setId }
            ?: throw NativeWorkoutException("Set not found.")
        val bundle = loadVersionBundles(listOf(exercise.executionProfileVersionId)).getValue(exercise.executionProfileVersionId)
        val values = (
            buildLegacyUiValues(bundle.schema, bundle.resistanceSemantics, load, reps, durationSeconds, distanceMetres) +
                additionalValues
            ).associateBy { it.metric }.values.map { value ->
                value.copy(evidenceQuality = EvidenceQuality(EvidenceGranularity.SUMMARY, AcquisitionMethod.USER_REPORTED))
            }
        if (!logged) {
            dao.deleteSetDraftMetricValues(setId)
            if (values.isNotEmpty()) {
                dao.upsertSetDraftMetricValues(values.map { value ->
                    SetDraftMetricValueEntity(setId, value.metric.storageValue, value.entered.value, value.entered.unit.storageValue, timestamp())
                })
            }
        } else {
            saveObservationInternal(
                record = record,
                exercise = exercise,
                schema = bundle.schema,
                lateralityMode = bundle.lateralityMode,
                laterality = laterality ?: bundle.defaultObservationLaterality(),
                values = values,
                source = NATIVE_SOURCE,
                bodyMassContextKg = null,
                startedAt = null,
                endedAt = null,
                timingQuality = TimingQuality.COMPLETION_ONLY,
                sourceZoneOffsetMinutes = null,
                completedAt = null,
            )
        }
        loadPerformanceSets(listOf(record), loadPrescription(exercise, bundle.schema)).single()
    }

    /** Generic entry path used by heterogeneous/side-resolved controls and acceptance tests. */
    suspend fun saveObservation(
        sessionExerciseId: String,
        setId: String,
        laterality: Laterality,
        values: List<PerformanceMetricValue>,
        source: String = NATIVE_SOURCE,
        bodyMassContextKg: Double? = null,
        startedAt: Instant? = null,
        endedAt: Instant? = null,
        timingQuality: TimingQuality = TimingQuality.COMPLETION_ONLY,
        sourceZoneOffsetMinutes: Int? = null,
        completedAt: Instant? = null,
    ): PerformanceObservation = database.withTransaction {
        val exercise = dao.sessionExercise(sessionExerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        val record = dao.sets(sessionExerciseId).firstOrNull { it.id == setId }
            ?: throw NativeWorkoutException("Set not found.")
        val bundle = loadVersionBundles(listOf(exercise.executionProfileVersionId)).getValue(exercise.executionProfileVersionId)
        saveObservationInternal(
            record,
            exercise,
            bundle.schema,
            bundle.lateralityMode,
            laterality,
            values,
            source,
            bodyMassContextKg,
            startedAt,
            endedAt,
            timingQuality,
            sourceZoneOffsetMinutes,
            completedAt,
        )
    }

    suspend fun setExerciseCompleted(
        sessionId: String,
        sessionExerciseId: String,
        completed: Boolean,
    ): SessionExerciseEntity = database.withTransaction {
        val current = dao.sessionExercises(sessionId).firstOrNull { it.id == sessionExerciseId }
            ?: throw NativeWorkoutException("Exercise not found in the active workout.")
        val now = timestamp()
        current.copy(
            status = if (completed) "completed" else "active",
            // No dedicated exercise-start event exists yet; completion must not fabricate one.
            startedAt = current.startedAt,
            completedAt = if (completed) now else null,
        ).also { dao.upsertSessionExercises(listOf(it)) }
    }

    suspend fun completeSession(sessionId: String): ActiveWorkout = database.withTransaction {
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status == "completed") return@withTransaction activeWorkout(sessionId)
        val now = timestamp()
        dao.upsertSessionExercises(
            dao.sessionExercises(sessionId).map { exercise ->
                if (exercise.status == "completed") exercise else exercise.copy(status = "skipped")
            },
        )
        dao.upsertSessions(listOf(session.copy(status = "completed", completedAt = now, healthExportState = "queued")))
        var state = dao.appState() ?: throw NativeWorkoutException("App state is missing.")
        if (session.daySymbol in CORE_DAYS) {
            dao.upsertCompletedDays(listOf(CycleCompletedDayEntity(session.cycleId, session.daySymbol)))
        } else if (session.daySymbol == "&") {
            val cycle = dao.trainingCycle(session.cycleId) ?: throw NativeWorkoutException("Training cycle is missing.")
            dao.upsertTrainingCycles(listOf(cycle.copy(andCompleted = true, status = "closed", endedAt = now)))
            val next = TrainingCycleEntity(id("cycle"), now, null, "active", false)
            dao.upsertTrainingCycles(listOf(next))
            state = state.copy(currentCycleId = next.id)
        }
        dao.upsertAppState(state.copy(activeSessionId = null, updatedAt = now))
        activeWorkout(sessionId)
    }

    private suspend fun buildSwapOptions(sessionExerciseId: String): List<ExerciseSwapOption> {
        val current = dao.sessionExercise(sessionExerciseId) ?: throw NativeWorkoutException("Exercise not found.")
        val session = dao.session(current.sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status != "active" || hasCurrentObservations(dao.sets(current.id))) return emptyList()
        val sessionTargets = dao.sessionTargets(session.id).associateBy { it.id }
        val currentTargets = dao.sessionExerciseTargets(current.id).mapNotNull { sessionTargets[it.sessionTargetId] }
        val targetBySegment = currentTargets.associateBy { it.muscleSegmentId }
        val exercises = dao.allActiveExercises().filterNot { it.id == current.exerciseId }
        val profiles = dao.executionProfiles(exercises.map { it.id }).groupBy { it.exerciseId }
            .mapNotNull { (_, values) -> values.singleOrNull { it.isDefault } }
        val versions = currentVersions(profiles.map { it.id })
        val bundles = loadVersionBundles(versions.values.map { it.id })
        val recruitment = dao.recruitmentAllocations(versions.values.map { it.recruitmentProfileVersionId })
            .groupBy { it.recruitmentProfileVersionId }
        val exerciseById = exercises.associateBy { it.id }
        val snapshot = inferenceRepository.latestSnapshot()
        val translation = snapshot?.exerciseTranslationStates.orEmpty()
            .associateBy { it.executionProfileVersionId.value to it.laterality }
        val currentBundle = loadVersionBundles(listOf(current.executionProfileVersionId)).getValue(current.executionProfileVersionId)
        val currentPrescription = loadPrescription(current, currentBundle.schema)

        return profiles.mapNotNull { profile ->
            val exercise = exerciseById[profile.exerciseId] ?: return@mapNotNull null
            val version = versions.getValue(profile.id)
            val bundle = bundles.getValue(version.id)
            val coverage = matchedSwapTargetCoverage(targetBySegment, recruitment[version.recruitmentProfileVersionId].orEmpty())
            if (currentTargets.isNotEmpty() && coverage.isEmpty()) return@mapNotNull null
            val prescriptionLaterality = bundle.defaultPrescriptionLaterality()
            val evidence = evidenceForSchema(
                bundle.schema,
                version.id,
                prescriptionLaterality,
                translation[version.id to prescriptionLaterality],
                snapshot?.run?.id?.value,
                session.id,
            )
            val prescription = prescriptionEngine.generate(
                PrescriptionRequest(
                    exerciseId = ExerciseId(exercise.id),
                    executionProfileId = ExecutionProfileId(profile.id),
                    executionProfileVersionId = ExecutionProfileVersionId(version.id),
                    targetIds = coverage.keys.sortedBy { it.value },
                    sets = currentPrescription.sets.coerceAtLeast(1),
                    schema = bundle.schema,
                    preferredTemplate = PerformanceTargetTemplate(emptyList()),
                    evidenceByMetric = evidence,
                    laterality = prescriptionLaterality,
                    restSeconds = current.restSeconds,
                ),
            )
            ExerciseSwapOption(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                executionProfileId = profile.id,
                executionProfileVersionId = version.id,
                executionProfileName = profile.name,
                schema = bundle.schema,
                resistanceSemantics = bundle.resistanceSemantics,
                entryBasis = bundle.entryBasis,
                matchedTargetIds = prescription.targetIds,
                targetCoverageScore = coverage.entries.sumOf { (targetId, value) ->
                    (sessionTargets[targetId.value]?.resolvedPriority ?: 0.0) * value
                },
                prescription = prescription,
            )
        }.sortedWith(compareByDescending<ExerciseSwapOption> { it.targetCoverageScore }.thenBy { it.exerciseName.lowercase() })
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
        val resolutions = targetResolver.resolve(targets, constraints)
        if (slots.isEmpty()) return NativeWorkoutPlan(routineVersionId, day, mode, constraints, resolutions, emptyList(), emptyList())

        val exercises = dao.exercises(slots.map { it.exerciseId }.distinct()).associateBy { it.id }
        val profiles = dao.executionProfiles(exercises.keys.toList()).groupBy { it.exerciseId }
        val defaultProfile = exercises.keys.associateWith { exerciseId ->
            profiles[exerciseId].orEmpty().singleOrNull { it.isDefault }
                ?: throw NativeWorkoutException("Exercise $exerciseId must have exactly one default execution profile.")
        }
        val versions = currentVersions(defaultProfile.values.map { it.id })
        val bundles = loadVersionBundles(versions.values.map { it.id })
        val recruitmentByVersion = dao.recruitmentAllocations(versions.values.map { it.recruitmentProfileVersionId })
            .groupBy { it.recruitmentProfileVersionId }
        val preferences = dao.routineMetricTargets(routineVersionId, slots.map { it.id }).groupBy { it.slotId }
        val targetBySegment = targetEntities.associateBy { it.muscleSegmentId }
        val sourceById = linkedMapOf<String, SourceExercise>()
        val candidates = slots.filter { it.preferredSets > 0 }.map { slot ->
            val exercise = exercises[slot.exerciseId] ?: throw NativeWorkoutException("Routine slot ${slot.id} references a missing exercise.")
            val profile = defaultProfile.getValue(exercise.id)
            val version = versions.getValue(profile.id)
            val bundle = bundles.getValue(version.id)
            val recruitment = recruitmentByVersion[version.recruitmentProfileVersionId].orEmpty()
            val source = SourceExercise(slot, exercise, profile, version, bundle, recruitment)
            sourceById[slot.id] = source
            val coverage = linkedMapOf<TrainingTargetId, Double>()
            recruitment.filter { it.weighting > 0.0 && !it.role.equals("stabiliser", true) }.forEach { allocation ->
                val target = targetBySegment[allocation.muscleSegmentId] ?: return@forEach
                val id = TrainingTargetId(target.id)
                coverage[id] = maxOf(coverage[id] ?: 0.0, allocation.weighting * allocation.confidence)
            }
            ExerciseSelectionCandidate(
                preferenceId = slot.id,
                exerciseId = ExerciseId(exercise.id),
                executionProfileId = ExecutionProfileId(profile.id),
                executionProfileVersionId = ExecutionProfileVersionId(version.id),
                ordinal = slot.position,
                preferencePriority = slot.importance.toTargetPriority(),
                preferredSetCap = slot.preferredSets,
                preferredTemplate = PerformanceTargetTemplate(preferences[slot.id].orEmpty().map { it.toDomain() }),
                restSeconds = slot.restSeconds,
                targetCoverage = coverage,
            )
        }
        val selections = exerciseSelector.select(resolutions, candidates, constraints)
        val selected = selections.associateBy { it.candidate.preferenceId }
        val includedTargetIds = resolutions.filter { it.included }.mapTo(hashSetOf()) { it.target.id }
        val decisions = candidates.map { candidate ->
            val source = sourceById.getValue(candidate.preferenceId)
            val result = selected[candidate.preferenceId]
            WorkoutCandidateDecision(
                slotId = source.slot.id,
                exerciseId = source.exercise.id,
                exerciseName = source.exercise.name,
                executionProfileId = source.profile.id,
                executionProfileVersionId = source.version.id,
                executionProfileName = source.profile.name,
                preferencePriority = candidate.preferencePriority,
                targetCoverage = candidate.targetCoverage.mapKeys { it.key.value },
                selected = result != null,
                selectedSets = result?.sets,
                decisionReason = result?.reason ?: when {
                    candidate.targetCoverage.keys.none { it in includedTargetIds } && candidate.targetCoverage.isNotEmpty() -> "no_included_target_coverage"
                    candidate.targetCoverage.isEmpty() && candidate.preferencePriority < constraints.targetPriorityFloor -> "below_target_priority_floor"
                    else -> "not_selected_within_budget"
                },
            )
        }
        val snapshot = inferenceRepository.latestSnapshot()
        val translation = snapshot?.exerciseTranslationStates.orEmpty()
            .associateBy { it.executionProfileVersionId.value to it.laterality }
        val planned = selections.map { selection ->
            val source = sourceById.getValue(selection.candidate.preferenceId)
            val prescriptionLaterality = source.bundle.defaultPrescriptionLaterality()
            val evidence = evidenceForSchema(
                source.bundle.schema,
                source.version.id,
                prescriptionLaterality,
                translation[source.version.id to prescriptionLaterality],
                snapshot?.run?.id?.value,
                excludeSessionId = null,
            )
            val generated = prescriptionEngine.generate(
                PrescriptionRequest(
                    exerciseId = ExerciseId(source.exercise.id),
                    executionProfileId = ExecutionProfileId(source.profile.id),
                    executionProfileVersionId = ExecutionProfileVersionId(source.version.id),
                    targetIds = selection.targetIds,
                    sets = selection.sets,
                    schema = source.bundle.schema,
                    preferredTemplate = selection.candidate.preferredTemplate,
                    evidenceByMetric = evidence,
                    laterality = prescriptionLaterality,
                    restSeconds = selection.candidate.restSeconds,
                ),
            )
            PlannedWorkoutExercise(
                slotId = source.slot.id,
                name = source.exercise.name,
                importance = source.slot.importance.toImportance(),
                executionProfileName = source.profile.name,
                schema = source.bundle.schema,
                resistanceSemantics = source.bundle.resistanceSemantics,
                entryBasis = source.bundle.entryBasis,
                prescription = generated,
                movementReason = selection.reason,
                estimatedDurationSeconds = selection.estimatedDurationSeconds,
            )
        }
        return NativeWorkoutPlan(routineVersionId, day, mode, constraints, resolutions, planned, decisions)
    }

    private suspend fun evidenceForSchema(
        schema: PerformanceSchema,
        profileVersionId: String,
        laterality: Laterality,
        translation: dev.kian.mymettle.domain.inference.ExerciseTranslationState?,
        inferenceRunId: String?,
        excludeSessionId: String?,
    ) = schema.metrics.mapNotNull { definition ->
        val inferred = translation?.anchor(definition.metric)
        val raw = if (inferred == null) {
            dao.latestCompletedMetricForExecutionProfileVersion(
                profileVersionId,
                definition.metric.storageValue,
                laterality.storageValue,
                excludeSessionId,
            )
        } else null
        SameProfileMetricEvidenceResolver.resolve(
            inferredCanonical = inferred?.estimate?.value,
            inferredObservationId = inferred?.sourceObservationId,
            inferredSetRecordId = inferred?.sourceSetRecordId,
            inferenceRunId = inferenceRunId,
            rawCanonical = raw?.canonicalValue,
            rawObservationId = raw?.observationId,
            rawSetRecordId = raw?.setRecordId,
        )?.let { definition.metric to it }
    }.toMap()

    private suspend fun saveObservationInternal(
        record: SetRecordEntity,
        exercise: SessionExerciseEntity,
        schema: PerformanceSchema,
        lateralityMode: LateralityMode,
        laterality: Laterality,
        values: List<PerformanceMetricValue>,
        source: String,
        bodyMassContextKg: Double?,
        startedAt: Instant?,
        endedAt: Instant?,
        timingQuality: TimingQuality,
        sourceZoneOffsetMinutes: Int?,
        completedAt: Instant?,
    ): PerformanceObservation {
        schema.validate(values)
        validateObservationLaterality(lateralityMode, laterality)
        if (source.isBlank()) throw NativeWorkoutException("Observation source cannot be blank.")
        if (bodyMassContextKg != null && bodyMassContextKg <= 0.0) {
            throw NativeWorkoutException("Observation body mass must be positive.")
        }
        if (startedAt != null && endedAt != null && startedAt.isAfter(endedAt)) {
            throw NativeWorkoutException("Observation start cannot be after its end.")
        }
        if (sourceZoneOffsetMinutes != null && sourceZoneOffsetMinutes !in -18 * 60..18 * 60) {
            throw NativeWorkoutException("Observation source-zone offset is invalid.")
        }
        if (timingQuality == TimingQuality.COMPLETION_ONLY && startedAt != null) {
            throw NativeWorkoutException("Completion-only timing cannot claim a start bound.")
        }
        val currentForSide = currentObservations(listOf(record.id))
            .filter { it.setRecordId == record.id && it.side == laterality.storageValue }
        if (currentForSide.size > 1) {
            throw NativeWorkoutException("Set ${record.id} has ambiguous current evidence for ${laterality.storageValue}.")
        }
        val existing = currentForSide.singleOrNull()
        val all = dao.observations(listOf(record.id))
        val recordedAt = Instant.now()
        val completionEvent = completedAt ?: endedAt ?: recordedAt
        val observableEnd = endedAt ?: completionEvent
        val observationId = id("observation")
        ObservationSupersedingPolicy.validateAppend(
            newObservationId = observationId,
            predecessor = existing,
            existing = all,
            setRecordId = record.id,
            executionProfileVersionId = exercise.executionProfileVersionId,
            side = laterality.storageValue,
        )
        val observation = SetObservationEntity(
            id = observationId,
            setRecordId = record.id,
            executionProfileVersionId = exercise.executionProfileVersionId,
            ordinal = (all.maxOfOrNull { it.ordinal } ?: -1) + 1,
            side = laterality.storageValue,
            completedAt = completionEvent.toString(),
            recordedAt = recordedAt.toString(),
            source = source,
            bodyMassContextKg = bodyMassContextKg,
            bodyMassContextSource = bodyMassContextKg?.let { "observation_explicit" },
            supersedesObservationId = existing?.id,
            startedAtEpochSecond = startedAt?.epochSecond,
            startedAtNano = startedAt?.nano,
            endedAtEpochSecond = observableEnd.epochSecond,
            endedAtNano = observableEnd.nano,
            timingQuality = timingQuality.storageValue,
            sourceZoneOffsetMinutes = sourceZoneOffsetMinutes,
        )
        dao.insertSetObservations(listOf(observation))
        dao.insertSetMetricValues(values.map { it.toEntity(observation.id) })
        dao.deleteSetDraftMetricValues(record.id)
        return PerformanceObservation(
            id = observation.id,
            setRecordId = record.id,
            executionProfileVersionId = ExecutionProfileVersionId(exercise.executionProfileVersionId),
            ordinal = observation.ordinal,
            laterality = laterality,
            completedAt = completionEvent,
            source = source,
            bodyMassContextKg = bodyMassContextKg,
            values = values,
            supersedesObservationId = existing?.id,
            startedAt = startedAt,
            endedAt = observableEnd,
            timingQuality = timingQuality,
            sourceZoneOffsetMinutes = sourceZoneOffsetMinutes,
        )
    }

    private suspend fun persistPrescription(sessionExerciseId: String, prescription: ExercisePrescription) {
        dao.deleteSessionSetPrescriptions(sessionExerciseId)
        val setRows = prescription.setPrescriptions.map { set ->
            SessionSetPrescriptionEntity(
                id = prescriptionSetId(sessionExerciseId, set.index),
                sessionExerciseId = sessionExerciseId,
                setIndex = set.index,
                kind = set.kind,
                laterality = set.laterality.storageValue,
            )
        }
        dao.upsertSessionSetPrescriptions(setRows)
        val metricRows = prescription.setPrescriptions.flatMap { set ->
            set.metricTargets.map { it.toEntity(prescriptionSetId(sessionExerciseId, set.index)) }
        }
        if (metricRows.isNotEmpty()) dao.upsertSessionMetricTargets(metricRows)
    }

    private suspend fun loadPrescription(entity: SessionExerciseEntity, schema: PerformanceSchema): ExercisePrescription {
        val setRows = dao.sessionSetPrescriptions(entity.id)
        val targets = if (setRows.isEmpty()) emptyMap() else {
            dao.sessionMetricTargets(setRows.map { it.id }).groupBy { it.sessionSetPrescriptionId }
        }
        return ExercisePrescription(
            exerciseId = ExerciseId(entity.exerciseId),
            executionProfileId = ExecutionProfileId(entity.executionProfileId),
            executionProfileVersionId = ExecutionProfileVersionId(entity.executionProfileVersionId),
            targetIds = dao.sessionExerciseTargets(entity.id).map { TrainingTargetId(it.sessionTargetId) },
            setPrescriptions = setRows.map { row ->
                SetPrescription(
                    index = row.setIndex,
                    kind = row.kind,
                    laterality = Laterality.fromStorage(row.laterality),
                    metricTargets = targets[row.id].orEmpty().map { it.toDomain() }.filter { target ->
                        schema.metrics.any { it.metric == target.metric }
                    },
                )
            },
            restSeconds = entity.restSeconds,
            generatedByModelVersion = entity.generatedByModelVersion,
        )
    }

    private suspend fun loadPerformanceSets(
        records: List<SetRecordEntity>,
        prescription: ExercisePrescription?,
    ): List<PerformanceSetRecord> {
        if (records.isEmpty()) return emptyList()
        val current = currentObservations(records.map { it.id })
        val valuesByObservation = if (current.isEmpty()) emptyMap() else {
            dao.metricValues(current.map { it.id }).groupBy { it.observationId }
        }
        val observationsBySet = current.map { it.toDomain(valuesByObservation[it.id].orEmpty()) }.groupBy { it.setRecordId }
        val draftsBySet = dao.draftMetricValues(records.map { it.id }).groupBy { it.setRecordId }
        val targetsByIndex = prescription?.setPrescriptions.orEmpty().associate { it.index to it.metricTargets }
        return records.map { record ->
            PerformanceSetRecord(
                record = record,
                observations = observationsBySet[record.id].orEmpty(),
                draftValues = draftsBySet[record.id].orEmpty(),
                metricTargets = targetsByIndex[record.setIndex].orEmpty(),
            )
        }
    }

    private suspend fun currentObservations(setRecordIds: List<String>): List<SetObservationEntity> {
        if (setRecordIds.isEmpty()) return emptyList()
        return ObservationSupersedingPolicy.current(dao.observations(setRecordIds))
    }

    private suspend fun hasCurrentObservations(records: List<SetRecordEntity>): Boolean =
        currentObservations(records.map { it.id }).isNotEmpty()

    private suspend fun currentVersions(profileIds: List<String>): Map<String, ExecutionProfileVersionEntity> =
        dao.executionProfileVersions(profileIds).groupBy { it.executionProfileId }.mapValues { (profileId, versions) ->
            versions.filter { it.supersededAt == null }.singleOrNull()
                ?: versions.maxByOrNull { it.version }
                ?: throw NativeWorkoutException("Execution profile $profileId has no semantic version.")
        }

    private suspend fun loadVersionBundles(versionIds: List<String>): Map<String, VersionBundle> {
        if (versionIds.isEmpty()) return emptyMap()
        val versions = dao.executionProfileVersionsById(versionIds.distinct()).associateBy { it.id }
        val schemas = dao.performanceSchemas(versions.values.map { it.performanceSchemaId }.distinct()).associateBy { it.id }
        val metrics = dao.performanceSchemaMetrics(schemas.keys.toList()).groupBy { it.performanceSchemaId }
        return versions.mapValues { (_, version) ->
            val schema = schemas[version.performanceSchemaId]
                ?: throw NativeWorkoutException("Execution profile version ${version.id} references a missing performance schema.")
            VersionBundle(
                version = version,
                schema = schema.toDomain(metrics[schema.id].orEmpty()),
                resistanceSemantics = ResistanceSemantics.entries.first { it.storageValue == version.resistanceSemantics },
                entryBasis = EntryBasis.fromStorage(version.entryBasis),
                lateralityMode = LateralityMode.entries.first { it.storageValue == version.lateralityMode },
            )
        }
    }

    private fun buildLegacyUiValues(
        schema: PerformanceSchema,
        resistanceSemantics: ResistanceSemantics,
        load: Double?,
        reps: Int?,
        durationSeconds: Int?,
        distanceMetres: Double?,
    ): List<PerformanceMetricValue> = buildList {
        val loadMetric = when {
            schema.metrics.any { it.metric == PerformanceMetric.ASSISTANCE } || resistanceSemantics == ResistanceSemantics.ASSISTANCE -> PerformanceMetric.ASSISTANCE
            schema.metrics.any { it.metric == PerformanceMetric.EXTERNAL_LOAD } -> PerformanceMetric.EXTERNAL_LOAD
            else -> null
        }
        loadMetric?.let { metric -> load?.let { add(schema.metricValue(metric, it)) } }
        reps?.let { if (schema.has(PerformanceMetric.REPETITIONS)) add(schema.metricValue(PerformanceMetric.REPETITIONS, it.toDouble())) }
        durationSeconds?.let { if (schema.has(PerformanceMetric.DURATION)) add(PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(it.toDouble(), UnitId.SECOND))) }
        distanceMetres?.let { if (schema.has(PerformanceMetric.DISTANCE)) add(PerformanceMetricValue(PerformanceMetric.DISTANCE, Quantity(it, UnitId.METRE))) }
    }

    private companion object {
        const val USER_SUBSTITUTION_REASON = "user_substitution"
        const val NATIVE_SOURCE = "native_manual_entry"
    }
}

private data class VersionBundle(
    val version: ExecutionProfileVersionEntity,
    val schema: PerformanceSchema,
    val resistanceSemantics: ResistanceSemantics,
    val entryBasis: EntryBasis,
    val lateralityMode: LateralityMode,
) {
    fun defaultPrescriptionLaterality(): Laterality = when (lateralityMode) {
        LateralityMode.BILATERAL_ONLY -> Laterality.BILATERAL
        LateralityMode.UNILATERAL -> Laterality.UNKNOWN
        LateralityMode.ALTERNATING_ALLOWED -> Laterality.ALTERNATING
        LateralityMode.NOT_APPLICABLE -> Laterality.NOT_APPLICABLE
        LateralityMode.UNKNOWN -> Laterality.UNKNOWN
    }

    fun defaultObservationLaterality(): Laterality = when (lateralityMode) {
        LateralityMode.BILATERAL_ONLY -> Laterality.BILATERAL
        LateralityMode.UNILATERAL -> Laterality.UNKNOWN // UI must request LEFT/RIGHT explicitly.
        LateralityMode.ALTERNATING_ALLOWED -> Laterality.ALTERNATING
        LateralityMode.NOT_APPLICABLE -> Laterality.NOT_APPLICABLE
        LateralityMode.UNKNOWN -> Laterality.UNKNOWN
    }
}

private data class SourceExercise(
    val slot: RoutineSlotEntity,
    val exercise: ExerciseEntity,
    val profile: ExerciseExecutionProfileEntity,
    val version: ExecutionProfileVersionEntity,
    val bundle: VersionBundle,
    val recruitment: List<RecruitmentAllocationEntity>,
)

internal fun matchedSwapTargetCoverage(
    targetsBySegment: Map<String, SessionTargetEntity>,
    recruitment: List<RecruitmentAllocationEntity>,
): Map<TrainingTargetId, Double> {
    val matched = linkedMapOf<TrainingTargetId, Double>()
    recruitment.filter { it.weighting > 0.0 && !it.role.equals("stabiliser", true) }.forEach { allocation ->
        val target = targetsBySegment[allocation.muscleSegmentId] ?: return@forEach
        val targetId = TrainingTargetId(target.id)
        matched[targetId] = maxOf(matched[targetId] ?: 0.0, allocation.weighting * allocation.confidence)
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

private fun SessionConstraints.toSessionConstraint(sessionId: String): SessionConstraintEntity = SessionConstraintEntity(
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

private fun PlannedWorkoutExercise.toSessionExercise(
    id: String,
    sessionId: String,
    position: Int,
    mode: TrainingMode,
    reason: String,
): SessionExerciseEntity = SessionExerciseEntity(
    id = id,
    sessionId = sessionId,
    position = position,
    exerciseId = prescription.exerciseId.value,
    slotId = slotId,
    exerciseNameSnapshot = name,
    importanceSnapshot = importance.name.lowercase(),
    executionProfileId = prescription.executionProfileId.value,
    executionProfileVersionId = prescription.executionProfileVersionId.value,
    executionProfileNameSnapshot = executionProfileName,
    prescriptionMode = mode.code,
    prescriptionIncluded = true,
    restSeconds = prescription.restSeconds,
    generatedByModelVersion = prescription.generatedByModelVersion,
    deferToAnd = false,
    status = "planned",
    note = null,
    startedAt = null,
    completedAt = null,
    movementReason = reason,
    substitutedFromExerciseId = null,
)

private fun PlannedWorkoutExercise.toSessionExerciseTargets(
    sessionExerciseId: String,
    targets: Map<String?, SessionTargetEntity>,
): List<SessionExerciseTargetEntity> = prescription.targetIds.map { targetId ->
    val target = targets[targetId.value] ?: throw NativeWorkoutException("Prescription references missing target ${targetId.value}.")
    SessionExerciseTargetEntity(sessionExerciseId, target.id)
}

private fun ExercisePrescription.resized(size: Int): ExercisePrescription {
    require(size > 0)
    val source = setPrescriptions.firstOrNull() ?: error("Cannot resize an empty prescription.")
    return copy(setPrescriptions = List(size) { index -> source.copy(index = index) })
}

private fun PerformanceSchema.has(metric: PerformanceMetric): Boolean = metrics.any { it.metric == metric }

private fun PerformanceSchema.metricValue(metric: PerformanceMetric, enteredValue: Double): PerformanceMetricValue {
    val definition = metrics.firstOrNull { it.metric == metric }
        ?: throw NativeWorkoutException("${metric.storageValue} is not valid for this execution profile version.")
    return PerformanceMetricValue(metric, Quantity(enteredValue, definition.defaultUnit))
}

private fun PerformanceSchema.compatibilityTrackingMetric(): String = when (family) {
    MetricFamily.DYNAMIC_RESISTANCE -> "load_reps"
    MetricFamily.BODYWEIGHT_RESISTANCE, MetricFamily.REPEATED_CONTRACTION -> "reps"
    MetricFamily.LOADED_HOLD, MetricFamily.DURATION_ONLY -> "duration"
    MetricFamily.POWER_DURATION, MetricFamily.SPEED_DURATION, MetricFamily.DEVICE_ORDINAL -> "generic"
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

private fun validateObservationLaterality(mode: LateralityMode, laterality: Laterality) {
    val allowed = when (mode) {
        LateralityMode.BILATERAL_ONLY -> setOf(Laterality.BILATERAL)
        LateralityMode.UNILATERAL -> setOf(Laterality.LEFT, Laterality.RIGHT)
        LateralityMode.ALTERNATING_ALLOWED -> setOf(Laterality.LEFT, Laterality.RIGHT, Laterality.ALTERNATING)
        LateralityMode.NOT_APPLICABLE -> setOf(Laterality.NOT_APPLICABLE)
        LateralityMode.UNKNOWN -> Laterality.entries.toSet()
    }
    if (laterality !in allowed) {
        throw NativeWorkoutException("${laterality.storageValue} is not valid for ${mode.storageValue}.")
    }
}

/** Enforces append-only, single-successor correction chains before Room's unique index is reached. */
internal object ObservationSupersedingPolicy {
    fun current(existing: List<SetObservationEntity>): List<SetObservationEntity> {
        validateExisting(existing)
        val superseded = existing.mapNotNullTo(hashSetOf()) { it.supersedesObservationId }
        return existing.filterNot { it.id in superseded }
    }

    fun validateAppend(
        newObservationId: String,
        predecessor: SetObservationEntity?,
        existing: List<SetObservationEntity>,
        setRecordId: String,
        executionProfileVersionId: String,
        side: String,
    ) {
        validateExisting(existing)
        if (existing.any { it.id == newObservationId }) {
            throw NativeWorkoutException("Observation ids are immutable and must be unique.")
        }
        if (predecessor == null) return
        if (predecessor !in existing) {
            throw NativeWorkoutException("A correction must supersede an observation stored on the same set.")
        }
        if (
            predecessor.setRecordId != setRecordId ||
            predecessor.executionProfileVersionId != executionProfileVersionId ||
            predecessor.side != side
        ) {
            throw NativeWorkoutException("A correction cannot change set, profile version, or side semantics.")
        }
        if (existing.any { it.supersedesObservationId == predecessor.id }) {
            throw NativeWorkoutException("An observation can have only one direct correction.")
        }
    }

    private fun validateExisting(existing: List<SetObservationEntity>) {
        val byId = existing.associateBy { it.id }
        if (byId.size != existing.size) throw NativeWorkoutException("Observation ids must be unique.")
        val successors = existing.mapNotNull { it.supersedesObservationId }
        if (successors.size != successors.distinct().size) {
            throw NativeWorkoutException("An observation cannot have multiple direct corrections.")
        }
        existing.forEach { start ->
            val visited = hashSetOf<String>()
            var cursor: SetObservationEntity? = start
            while (cursor != null) {
                if (!visited.add(cursor.id)) {
                    throw NativeWorkoutException("Observation correction chains cannot contain cycles.")
                }
                val parentId = cursor.supersedesObservationId ?: break
                val parent = byId[parentId]
                    ?: throw NativeWorkoutException("Observation ${cursor.id} supersedes a missing observation.")
                if (
                    parent.setRecordId != cursor.setRecordId ||
                    parent.executionProfileVersionId != cursor.executionProfileVersionId ||
                    parent.side != cursor.side
                ) {
                    throw NativeWorkoutException("Correction chains must preserve set, profile version, and side.")
                }
                cursor = parent
            }
        }
    }
}

private fun prescriptionSetId(sessionExerciseId: String, setIndex: Int): String =
    "$sessionExerciseId:prescription:set:$setIndex"

private fun id(prefix: String): String = "${prefix}_${UUID.randomUUID()}"
private fun timestamp(): String = Instant.now().toString()
