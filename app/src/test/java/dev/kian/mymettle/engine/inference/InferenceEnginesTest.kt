package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.RecruitmentRole
import dev.kian.mymettle.domain.inference.BodySide
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.InferenceRunId
import dev.kian.mymettle.domain.inference.RecruitmentEvidence
import dev.kian.mymettle.domain.inference.StimulusEstimate
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InferenceEnginesTest {
    @Test
    fun `stimulus v0 projects weighted working-set evidence without inventing an effort curve`() {
        val estimator = WeightedWorkingSetStimulusEstimator()
        val allocation = RecruitmentEvidence(
            segmentId = MuscleSegmentId("pectoralis_major_clavicular_part"),
            role = RecruitmentRole.PRIME,
            weighting = 0.7,
            confidence = 0.8,
        )

        val estimate = estimator.estimate(set(), listOf(allocation)).single()

        assertEquals(0.7, estimate.estimatedStimulus)
        assertEquals(0.32, estimate.confidence, absoluteTolerance = 0.000001)
        assertTrue(estimator.estimate(set(warmUp = true), listOf(allocation)).isEmpty())
    }

    @Test
    fun `muscle-state v0 keeps unresolved fields separate without relabelling all-time evidence`() {
        val runId = InferenceRunId("run_1")
        val segmentA = MuscleSegmentId("segment_a")
        val segmentB = MuscleSegmentId("segment_b")
        val updater = NeutralPriorMuscleStateUpdater()
        val states = updater.update(
            MuscleStateUpdateRequest(
                inferenceRunId = runId,
                trackedSegmentIds = listOf(segmentA, segmentB),
                stimuli = listOf(
                    stimulus("set_1", segmentA, estimated = 0.6, confidence = 0.6),
                    stimulus("set_2", segmentA, estimated = 0.4, confidence = 0.2),
                ),
                calculatedAt = Instant.parse("2026-08-11T12:00:00Z"),
            ),
        )

        val trained = states.single { it.segmentId == segmentA }
        val unseen = states.single { it.segmentId == segmentB }
        assertEquals(BodySide.BILATERAL, trained.side)
        assertEquals(1.0, trained.developmentIndex.value)
        assertEquals(2, trained.evidenceCount)
        assertNull(trained.volumeScale)
        assertNull(trained.structuralCapacityScale)
        assertNull(trained.recentStimulus)
        assertNull(trained.recovery)
        assertNull(unseen.recentStimulus)
        assertEquals(0, unseen.evidenceCount)
    }

    @Test
    fun `translation v0 stores the latest same-profile anchor and sample count`() {
        val model = ObservedPerformanceTranslationModel()
        val states = model.infer(
            listOf(
                set(id = "set_1", completedAt = "2026-08-10T10:00:00Z", load = 65.0, reps = 9),
                set(id = "set_2", completedAt = "2026-08-11T10:00:00Z", load = 70.0, reps = 8),
                set(id = "warmup", completedAt = "2026-08-11T11:00:00Z", load = 40.0, reps = 10, warmUp = true),
            ),
        )

        val state = states.single()
        val load = state.anchor(PerformanceMetric.EXTERNAL_LOAD)
        val reps = state.anchor(PerformanceMetric.REPETITIONS)
        assertEquals(70.0, load?.estimate?.value)
        assertEquals("kg", load?.canonicalUnit)
        assertEquals(8.0, reps?.estimate?.value)
        assertEquals(1.0, load?.estimate?.uncertainty)
        assertEquals("observation_set_2", load?.sourceObservationId)
        assertEquals(2, state.sampleCount)
    }

    private fun set(
        id: String = "set_1",
        completedAt: String = "2026-08-11T10:00:00Z",
        load: Double? = 70.0,
        reps: Int? = 8,
        warmUp: Boolean = false,
    ): CompletedSetEvidence = CompletedSetEvidence(
        setRecordId = id,
        observationId = "observation_$id",
        sessionExerciseId = "session_exercise_1",
        executionProfileVersionId = ExecutionProfileVersionId("execution_profile_1:v1"),
        laterality = Laterality.BILATERAL,
        completedAt = Instant.parse(completedAt),
        metricValues = listOfNotNull(
            load?.let { PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(it, UnitId.KILOGRAM)) },
            reps?.let { PerformanceMetricValue(PerformanceMetric.REPETITIONS, Quantity(it.toDouble(), UnitId.REPETITION)) },
        ),
        bodyMassContextKg = null,
        warmUp = warmUp,
        kind = if (warmUp) "warm_up" else "prescribed",
    )

    private fun stimulus(
        setId: String,
        segmentId: MuscleSegmentId,
        estimated: Double,
        confidence: Double,
    ): StimulusEstimate = StimulusEstimate(
        setRecordId = setId,
        observationId = "observation_$setId",
        sessionExerciseId = "session_exercise_1",
        segmentId = segmentId,
        side = BodySide.BILATERAL,
        role = RecruitmentRole.PRIME,
        recruitmentWeighting = estimated,
        estimatedStimulus = estimated,
        confidence = confidence,
        modelVersion = "test",
    )
}
