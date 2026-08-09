package dev.kian.mymettle.history

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.NativeWorkoutException
import dev.kian.mymettle.workout.SessionAchievement
import dev.kian.mymettle.workout.SessionAchievementScorer
import java.time.Instant

data class HistoryExercise(
    val exercise: SessionExerciseEntity,
    val sets: List<SetRecordEntity>,
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

    suspend fun recent(limit: Int = 100): List<HistorySession> =
        dao.recentCompletedSessions(limit).map { load(it) }

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
        reflection
    }

    suspend fun reflection(sessionExerciseId: String): ExerciseReflectionEntity? =
        dao.reflection(sessionExerciseId)

    private suspend fun load(session: SessionEntity): HistorySession {
        val exercises = dao.sessionExercises(session.id).map { exercise ->
            HistoryExercise(
                exercise = exercise,
                sets = dao.sets(exercise.id),
                reflection = dao.reflection(exercise.id),
            )
        }
        val activeShape = ActiveWorkout(
            session = session,
            exercises = exercises.map { item ->
                ActiveWorkoutExercise(
                    entity = item.exercise,
                    sets = item.sets,
                    previousCompletedSets = emptyList(),
                )
            },
        )
        return HistorySession(
            session = session,
            exercises = exercises,
            review = dao.sessionReview(session.id),
            achievement = SessionAchievementScorer.score(activeShape),
        )
    }
}
