package dev.kian.mymettle.workout

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.CycleCompletedDayEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ModePrescriptionEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import java.time.Instant
import java.util.UUID

private val CORE_DAYS = setOf("ψ", "φ", "π")

class NativeWorkoutException(message: String) : IllegalStateException(message)

data class PlannedWorkoutExercise(
    val slotId: String,
    val exerciseId: String,
    val name: String,
    val importance: ExerciseImportance,
    val plannedLoad: Double,
    val defaultUnit: String,
    val trackingMetric: String,
    val loadRelationship: String,
    val entryBasis: String,
    val prescription: BasePrescription,
)

data class NativeWorkoutPlan(
    val routineVersionId: String,
    val day: String,
    val mode: TrainingMode,
    val exercises: List<PlannedWorkoutExercise>,
) {
    val workingSetCount: Int get() = exercises.sumOf { it.prescription.sets }
}

data class ActiveWorkoutExercise(
    val entity: SessionExerciseEntity,
    val sets: List<SetRecordEntity>,
    val previousCompletedSets: List<SetRecordEntity>,
)

data class ActiveWorkout(
    val session: SessionEntity,
    val exercises: List<ActiveWorkoutExercise>,
)

/**
 * Persistence boundary for the N2 workout loop.
 *
 * The UI receives resolved session prescriptions and does not need to know that the imported
 * programme still stores three Legacy A/B/C anchors. That lets mode semantics evolve without a
 * Room schema migration and keeps completed SessionExerciseEntity snapshots historically stable.
 */
class RoomWorkoutRepository(
    private val database: MyMettleDatabase,
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

        val sessionExercises = mutableListOf<SessionExerciseEntity>()
        val sets = mutableListOf<SetRecordEntity>()
        workoutPlan.exercises.forEachIndexed { position, planned ->
            val sessionExerciseId = id("session_exercise")
            sessionExercises += planned.toSessionExercise(
                sessionExerciseId = sessionExerciseId,
                sessionId = sessionId,
                position = position,
                mode = mode,
                bodyweight = bodyweight,
                movementReason = "base_routine",
            )
            sets += prescribedSets(
                sessionExerciseId = sessionExerciseId,
                planned = planned,
                indices = 0 until planned.prescription.sets,
            )
        }

        dao.upsertSessions(listOf(session))
        dao.upsertSessionExercises(sessionExercises)
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
        val exercises = dao.sessionExercises(sessionId).map { exercise ->
            ActiveWorkoutExercise(
                entity = exercise,
                sets = dao.sets(exercise.id),
                previousCompletedSets = dao.latestCompletedSetsForExercise(
                    exerciseId = exercise.exerciseId,
                    excludeSessionId = sessionId,
                    limit = 12,
                ),
            )
        }
        return ActiveWorkout(session, exercises)
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
        val existing = dao.sessionExercises(sessionId)
        val existingBySlot = existing.associateBy { it.slotId }
        val targetSlots = target.exercises.mapTo(mutableSetOf()) { it.slotId }
        val bodyweight = session.bodyweightSnapshotKg

        // Temporarily move every persisted position out of the unique-key range, then write the
        // resolved target order back. This avoids transient uniqueness collisions during mode swaps.
        dao.offsetSessionExercisePositions(sessionId)

        val updatedExercises = mutableListOf<SessionExerciseEntity>()
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
                    movementReason = "mode_switch_addition",
                )
                updatedSets += prescribedSets(
                    sessionExerciseId = sessionExerciseId,
                    planned = planned,
                    indices = 0 until planned.prescription.sets,
                )
            } else {
                updatedExercises += current.copy(
                    position = position,
                    plannedLoad = planned.plannedLoad,
                    importanceSnapshot = planned.importance.name.lowercase(),
                    prescriptionMode = mode.code,
                    prescriptionIncluded = true,
                    prescribedSets = planned.prescription.sets,
                    repMin = planned.prescription.repMin,
                    repMax = planned.prescription.repMax,
                    restSeconds = planned.prescription.restSeconds,
                    deferToAnd = false,
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
        val exerciseById = dao.exercises(slots.map { it.exerciseId }.distinct()).associateBy { it.id }
        val prescriptions = dao.modePrescriptions(routineVersionId).groupBy { it.slotId }

        val modeExercises = slots.map { slot ->
            val exercise = exerciseById[slot.exerciseId]
                ?: throw NativeWorkoutException("Routine slot ${slot.id} references a missing exercise.")
            val byMode = prescriptions[slot.id].orEmpty().associateBy { it.mode }
            ModeExercise(
                id = slot.id,
                ordinal = slot.position,
                importance = slot.importance.toImportance(),
                legacyA = byMode["A"].toBasePrescription(),
                legacyB = byMode["B"].toBasePrescription(),
                legacyC = byMode["C"].toBasePrescription(),
                payload = SourceExercise(slot, exercise),
            )
        }

        val planned = WorkoutModePolicy.plan(modeExercises, mode).map { item ->
            PlannedWorkoutExercise(
                slotId = item.payload.slot.id,
                exerciseId = item.payload.exercise.id,
                name = item.payload.exercise.name,
                importance = item.importance,
                plannedLoad = item.payload.slot.plannedLoad,
                defaultUnit = item.payload.exercise.defaultUnit,
                trackingMetric = item.payload.exercise.trackingMetric,
                loadRelationship = item.payload.exercise.loadRelationship,
                entryBasis = item.payload.exercise.entryBasis,
                prescription = item.prescription,
            )
        }

        return NativeWorkoutPlan(
            routineVersionId = routineVersionId,
            day = day,
            mode = mode,
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
        exerciseId = exerciseId,
        slotId = slotId,
        exerciseNameSnapshot = name,
        importanceSnapshot = importance.name.lowercase(),
        trackingMetricSnapshot = trackingMetric,
        loadRelationshipSnapshot = loadRelationship,
        entryBasisSnapshot = entryBasis,
        bodyweightSnapshotKg = bodyweight,
        plannedLoad = plannedLoad,
        prescriptionMode = mode.code,
        prescriptionIncluded = true,
        prescribedSets = prescription.sets,
        repMin = prescription.repMin,
        repMax = prescription.repMax,
        restSeconds = prescription.restSeconds,
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
            load = if (startsWithLoad) planned.plannedLoad else null,
            reps = null,
            durationSeconds = null,
            distanceMetres = null,
            unit = planned.defaultUnit,
            completedAt = null,
            note = null,
            warmUp = false,
            kind = "prescribed",
        )
    }
}

private data class SourceExercise(
    val slot: RoutineSlotEntity,
    val exercise: ExerciseEntity,
)

private fun ModePrescriptionEntity?.toBasePrescription(): BasePrescription = if (this == null) {
    BasePrescription(included = false, sets = 0, repMin = 1, repMax = 1, restSeconds = 0)
} else {
    BasePrescription(
        included = included,
        sets = sets,
        repMin = repMin,
        repMax = repMax,
        restSeconds = restSeconds,
    )
}

private fun String.toImportance(): ExerciseImportance = when (lowercase()) {
    "principal" -> ExerciseImportance.PRINCIPAL
    "core" -> ExerciseImportance.CORE
    "accessory" -> ExerciseImportance.ACCESSORY
    else -> ExerciseImportance.CORE
}

private fun id(prefix: String): String = "${prefix}_${UUID.randomUUID()}"

private fun timestamp(): String = Instant.now().toString()
