package dev.kian.mymettle.history

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.domain.evidence.AcquisitionMethod
import dev.kian.mymettle.domain.evidence.EvidenceGranularity
import dev.kian.mymettle.domain.evidence.EvidenceQuality
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.workout.NativeWorkoutException
import dev.kian.mymettle.workout.PerformanceSetRecord
import dev.kian.mymettle.workout.RoomWorkoutRepository
import dev.kian.mymettle.workout.SessionAchievement
import dev.kian.mymettle.workout.SessionAchievementScorer
import java.time.Instant

data class HistoryExercise(
    val exercise: SessionExerciseEntity,
    val schema: PerformanceSchema,
    val resistanceSemantics: ResistanceSemantics,
    val sets: List<PerformanceSetRecord>,
    val reflection: ExerciseReflectionEntity?,
)

data class HistorySession(
    val session: SessionEntity,
    val exercises: List<HistoryExercise>,
    val review: SessionReviewEntity?,
    val achievement: SessionAchievement,
)

class HistoryRepository(
    private val database: MyMettleDatabase,
) {
    private val dao get() = database.historyDao()
    private val workoutRepository = RoomWorkoutRepository(database)

    suspend fun recent(limit: Int = 100): List<HistorySession> =
        dao.recentCompletedSessions(limit).map { load(it) }

    suspend fun updateSet(
        sessionId: String,
        sessionExerciseId: String,
        setId: String,
        load: Double?,
        reps: Int?,
        durationSeconds: Int?,
        distanceMetres: Double?,
    ): HistorySession = database.withTransaction {
        val session = completedSession(sessionId)
        val exercise = workoutRepository.activeWorkout(sessionId).exercises.firstOrNull { it.entity.id == sessionExerciseId }
            ?: throw NativeWorkoutException("Exercise not found in this session.")
        val current = exercise.sets.firstOrNull { it.id == setId }
            ?: throw NativeWorkoutException("Set not found in this exercise.")
        if (current.completedAt == null) {
            throw NativeWorkoutException("Only logged historical sets can be edited.")
        }
        if (reps != null && reps < 0) throw NativeWorkoutException("Reps cannot be negative.")
        if (durationSeconds != null && durationSeconds < 0) throw NativeWorkoutException("Duration cannot be negative.")
        if (distanceMetres != null && distanceMetres < 0.0) throw NativeWorkoutException("Distance cannot be negative.")

        if (current.observations.size != 1) {
            throw NativeWorkoutException("Side-resolved history must be corrected one observation at a time.")
        }
        val original = current.observations.single()
        val editableMetrics = setOf(
            PerformanceMetric.EXTERNAL_LOAD,
            PerformanceMetric.ASSISTANCE,
            PerformanceMetric.REPETITIONS,
            PerformanceMetric.DURATION,
            PerformanceMetric.DISTANCE,
        )
        val values = original.values.filterNot { it.metric in editableMetrics }.toMutableList()
        val loadMetric = when {
            exercise.schema.metrics.any { it.metric == PerformanceMetric.ASSISTANCE } -> PerformanceMetric.ASSISTANCE
            exercise.schema.metrics.any { it.metric == PerformanceMetric.EXTERNAL_LOAD } -> PerformanceMetric.EXTERNAL_LOAD
            else -> null
        }
        loadMetric?.let { metric ->
            load?.let { entered ->
                val unit = original.values.firstOrNull { it.metric == metric }?.entered?.unit
                    ?: exercise.schema.metrics.first { it.metric == metric }.defaultUnit
                values += userCorrection(metric, Quantity(entered, unit))
            }
        }
        reps?.let { values += userCorrection(PerformanceMetric.REPETITIONS, Quantity(it.toDouble(), UnitId.REPETITION)) }
        durationSeconds?.let { values += userCorrection(PerformanceMetric.DURATION, Quantity(it.toDouble(), UnitId.SECOND)) }
        distanceMetres?.let { values += userCorrection(PerformanceMetric.DISTANCE, Quantity(it, UnitId.METRE)) }
        workoutRepository.saveObservation(
            sessionExerciseId = exercise.entity.id,
            setId = current.id,
            laterality = original.laterality,
            values = values,
            source = "native_history_correction",
            bodyMassContextKg = original.bodyMassContextKg,
            startedAt = original.startedAt,
            endedAt = original.endedAt,
            timingQuality = original.timingQuality,
            sourceZoneOffsetMinutes = original.sourceZoneOffsetMinutes,
            completedAt = original.completedAt,
        )
        val edited = session.copy(editedAt = Instant.now().toString())
        dao.upsertSession(edited)
        load(edited)
    }

    private fun userCorrection(metric: PerformanceMetric, entered: Quantity) = PerformanceMetricValue(
        metric = metric,
        entered = entered,
        evidenceQuality = EvidenceQuality(EvidenceGranularity.SUMMARY, AcquisitionMethod.USER_REPORTED),
    )

    suspend fun discardSession(sessionId: String) = database.withTransaction {
        val session = completedSession(sessionId)
        val now = Instant.now().toString()
        dao.upsertSession(
            session.copy(
                status = "discarded",
                discardedAt = now,
                editedAt = now,
                excludedFromInsights = true,
            ),
        )
    }

    suspend fun saveSessionReview(
        sessionId: String,
        exerciseOrder: Int?,
        organisation: Int?,
        pacing: Int?,
        delayImpact: Int?,
        note: String?,
    ): HistorySession = database.withTransaction {
        val session = completedSession(sessionId)
        listOf(exerciseOrder, organisation, pacing, delayImpact)
            .filterNotNull()
            .forEach { value ->
                if (value !in 1..5) throw NativeWorkoutException("Session ratings must be between 1 and 5.")
            }
        val existing = dao.sessionReview(sessionId)
        val now = Instant.now().toString()
        dao.upsertSessionReview(
            SessionReviewEntity(
                sessionId = sessionId,
                exerciseOrder = exerciseOrder,
                organisation = organisation,
                pacing = pacing,
                delayImpact = delayImpact,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                recordedAt = existing?.recordedAt ?: now,
                updatedAt = now,
            ),
        )
        val edited = session.copy(editedAt = now)
        dao.upsertSession(edited)
        load(edited)
    }

    suspend fun saveExerciseReflection(
        sessionId: String,
        sessionExerciseId: String,
        targetMuscleEngagement: Int?,
        execution: String?,
        enjoyment: Int?,
        comfort: String?,
        note: String?,
    ): ExerciseReflectionEntity = database.withTransaction {
        if (targetMuscleEngagement != null && targetMuscleEngagement !in 1..7) {
            throw NativeWorkoutException("Target-muscle engagement must be between 1 and 7.")
        }
        if (enjoyment != null && enjoyment !in 1..7) {
            throw NativeWorkoutException("Enjoyment must be between 1 and 7.")
        }
        val executionValue = execution ?: "unrated"
        if (executionValue !in setOf("unrated", "clean", "mixed", "poor")) {
            throw NativeWorkoutException("Unknown execution rating.")
        }
        val comfortValue = comfort ?: "unrated"
        if (comfortValue !in setOf("unrated", "good", "fine", "uncomfortable", "pain")) {
            throw NativeWorkoutException("Unknown comfort rating.")
        }

        val exercise = dao.sessionExercises(sessionId).firstOrNull { it.id == sessionExerciseId }
            ?: throw NativeWorkoutException("Exercise not found in this workout.")
        val existing = dao.reflection(exercise.id)
        val now = Instant.now().toString()
        val reflection = ExerciseReflectionEntity(
            sessionExerciseId = exercise.id,
            targetMuscleEngagement = targetMuscleEngagement?.toString() ?: "unrated",
            execution = executionValue,
            enjoyment = enjoyment?.toString() ?: "unrated",
            comfort = comfortValue,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            recordedAt = existing?.recordedAt ?: now,
            updatedAt = now,
        )
        dao.upsertReflection(reflection)
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Session not found.")
        if (session.status == "discarded") throw NativeWorkoutException("A discarded session cannot be rated.")
        dao.upsertSession(session.copy(editedAt = now))
        reflection
    }

    suspend fun reflection(sessionExerciseId: String): ExerciseReflectionEntity? =
        dao.reflection(sessionExerciseId)

    private suspend fun completedSession(sessionId: String): SessionEntity {
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Session not found.")
        if (session.status != "completed") throw NativeWorkoutException("Only completed sessions can be edited here.")
        return session
    }

    private suspend fun load(session: SessionEntity): HistorySession {
        val workout = workoutRepository.activeWorkout(session.id)
        val exercises = workout.exercises.map { exercise ->
            HistoryExercise(
                exercise = exercise.entity,
                schema = exercise.schema,
                resistanceSemantics = exercise.resistanceSemantics,
                sets = exercise.sets,
                reflection = dao.reflection(exercise.entity.id),
            )
        }
        return HistorySession(
            session = session,
            exercises = exercises,
            review = dao.sessionReview(session.id),
            achievement = SessionAchievementScorer.score(workout),
        )
    }
}
