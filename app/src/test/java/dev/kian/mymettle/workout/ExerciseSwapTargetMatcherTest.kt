package dev.kian.mymettle.workout

import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.SessionTargetEntity
import dev.kian.mymettle.domain.training.TrainingTargetId
import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseSwapTargetMatcherTest {
    @Test
    fun `replacement coverage uses matching non-stabiliser segments and confidence weighting`() {
        val targets = listOf(
            target("target_chest", "pectoralis_major_clavicular_part", priority = 1.0),
            target("target_triceps", "triceps_brachii_long_head", priority = 0.7),
        ).associateBy { it.muscleSegmentId }
        val coverage = matchedSwapTargetCoverage(
            targetsBySegment = targets,
            recruitment = listOf(
                recruitment("profile_press", "pectoralis_major_clavicular_part", "prime", 0.8, 0.75),
                recruitment("profile_press", "triceps_brachii_long_head", "stabiliser", 0.9, 1.0),
                recruitment("profile_press", "deltoid_acromial_part", "synergist", 0.5, 0.8),
            ),
        )

        assertEquals(setOf(TrainingTargetId("target_chest")), coverage.keys)
        assertEquals(0.6, coverage.getValue(TrainingTargetId("target_chest")), absoluteTolerance = 0.000001)
    }

    private fun target(id: String, segmentId: String, priority: Double) = SessionTargetEntity(
        id = id,
        sessionId = "session_1",
        programmeTargetId = "programme_$id",
        muscleSegmentId = segmentId,
        priority = priority,
        desiredStimulus = null,
        source = "test",
        included = true,
        resolvedPriority = priority,
        resolutionModelVersion = "test",
    )

    private fun recruitment(
        profileId: String,
        segmentId: String,
        role: String,
        weighting: Double,
        confidence: Double,
    ) = RecruitmentAllocationEntity(
        executionProfileId = profileId,
        muscleSegmentId = segmentId,
        role = role,
        weighting = weighting,
        confidence = confidence,
        source = "test",
    )
}
