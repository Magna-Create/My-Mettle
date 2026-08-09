package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity

@Dao
interface HistoryDao {
    @Query("SELECT * FROM session WHERE status = 'completed' ORDER BY completedAt DESC LIMIT :limit")
    suspend fun recentCompletedSessions(limit: Int = 100): List<SessionEntity>

    @Query("SELECT * FROM session WHERE id = :sessionId LIMIT 1")
    suspend fun session(sessionId: String): SessionEntity?

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY position")
    suspend fun sessionExercises(sessionId: String): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_record WHERE sessionExerciseId = :sessionExerciseId ORDER BY setIndex")
    suspend fun sets(sessionExerciseId: String): List<SetRecordEntity>

    @Query("SELECT * FROM exercise_reflection WHERE sessionExerciseId = :sessionExerciseId LIMIT 1")
    suspend fun reflection(sessionExerciseId: String): ExerciseReflectionEntity?

    @Upsert
    suspend fun upsertReflection(value: ExerciseReflectionEntity)

    @Upsert
    suspend fun upsertSet(value: SetRecordEntity)

    @Upsert
    suspend fun upsertSession(value: SessionEntity)

    @Query("SELECT * FROM session_review WHERE sessionId = :sessionId LIMIT 1")
    suspend fun sessionReview(sessionId: String): SessionReviewEntity?

    @Upsert
    suspend fun upsertSessionReview(value: SessionReviewEntity)
}
