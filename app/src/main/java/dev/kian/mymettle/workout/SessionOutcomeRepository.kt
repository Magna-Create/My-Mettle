package dev.kian.mymettle.workout

import androidx.room.withTransaction
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import java.time.Instant

class SessionOutcomeRepository(
    private val database: MyMettleDatabase,
) {
    private val dao get() = database.workoutDao()

    suspend fun review(sessionId: String): SessionReviewEntity? = dao.sessionReview(sessionId)

    suspend fun saveReview(
        sessionId: String,
        exerciseOrder: Int?,
        organisation: Int?,
        pacing: Int?,
        delayImpact: Int?,
        note: String?,
    ): SessionReviewEntity = database.withTransaction {
        val session = dao.session(sessionId) ?: throw NativeWorkoutException("Workout not found.")
        if (session.status != "completed") {
            throw NativeWorkoutException("Whole-session review is available after the workout is completed.")
        }

        validateRating("Exercise order", exerciseOrder)
        validateRating("Organisation", organisation)
        validateRating("Pacing", pacing)
        validateRating("Delay impact", delayImpact)

        val now = Instant.now().toString()
        val existing = dao.sessionReview(sessionId)
        val review = SessionReviewEntity(
            sessionId = sessionId,
            exerciseOrder = exerciseOrder,
            organisation = organisation,
            pacing = pacing,
            delayImpact = delayImpact,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            recordedAt = existing?.recordedAt ?: now,
            updatedAt = now,
        )
        dao.upsertSessionReview(review)
        review
    }

    private fun validateRating(label: String, value: Int?) {
        if (value != null && value !in 1..5) {
            throw NativeWorkoutException("$label must be between 1 and 5.")
        }
    }
}
