package dev.kian.mymettle.engine.prescription

import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.MetricTarget
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.PerformanceTargetTemplate
import dev.kian.mymettle.domain.performance.PrescriptionEvidence
import dev.kian.mymettle.domain.performance.SchemaMetric
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.domain.training.TrainingTargetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryBackedPrescriptionEngineTest {
    private val engine = HistoryBackedPrescriptionEngine()

    @Test
    fun `dynamic prescription retains rep preference and conforms same-version load evidence`() {
        val result = engine.generate(
            request(
                schema = schema(
                    MetricFamily.DYNAMIC_RESISTANCE,
                    SchemaMetric(
                        PerformanceMetric.EXTERNAL_LOAD,
                        required = true,
                        allowedCanonicalValues = listOf(5.0, 7.5, 10.0, 12.5),
                    ),
                    SchemaMetric(PerformanceMetric.REPETITIONS, required = true),
                ),
                preferred = MetricTarget(
                    PerformanceMetric.REPETITIONS,
                    TargetKind.RANGE,
                    lowerCanonical = 8.0,
                    upperCanonical = 12.0,
                    displayUnit = UnitId.REPETITION,
                ),
                evidence = PerformanceMetric.EXTERNAL_LOAD to evidence(8.6),
            ),
        )

        assertEquals(7.5, result.prescribedLoad)
        assertEquals("set_anchor", result.loadEvidence?.sourceSetRecordId)
        assertEquals(listOf(TrainingTargetId("target_chest")), result.targetIds)
        assertEquals(3, result.sets)
        assertEquals(8..12, result.repRange)
    }

    @Test
    fun `duration-only prescription carries no fake rep range`() {
        val result = engine.generate(
            request(
                schema = schema(
                    MetricFamily.DURATION_ONLY,
                    SchemaMetric(PerformanceMetric.DURATION, required = true, defaultUnit = UnitId.SECOND),
                ),
                preferred = MetricTarget(
                    PerformanceMetric.DURATION,
                    TargetKind.RANGE,
                    lowerCanonical = 35.0,
                    upperCanonical = 45.0,
                    displayUnit = UnitId.SECOND,
                ),
            ),
        )

        assertNull(result.repRange)
        assertNull(result.prescribedLoad)
        assertEquals(35.0, result.setPrescriptions.first().metricTargets.single().lowerCanonical)
    }

    @Test
    fun `per-metric evidence is retained independently`() {
        val result = engine.generate(
            request(
                schema = schema(
                    MetricFamily.SPEED_DURATION,
                    SchemaMetric(PerformanceMetric.SPEED, required = true, defaultUnit = UnitId.MILES_PER_HOUR),
                    SchemaMetric(PerformanceMetric.DURATION, required = true, defaultUnit = UnitId.MINUTE),
                ),
                evidence = PerformanceMetric.SPEED to evidence(4.4704),
            ),
        )
        val speed = result.setPrescriptions.first().metricTargets.single()
        assertEquals(PerformanceMetric.SPEED, speed.metric)
        assertEquals("observation_anchor", speed.evidence?.sourceObservationId)
        assertEquals("run_1", speed.evidence?.inferenceRunId)
    }

    @Test
    fun `same-profile evidence resolver prefers inference then raw observation`() {
        val inferred = SameProfileMetricEvidenceResolver.resolve(
            inferredCanonical = 72.5,
            inferredObservationId = "observation_inferred",
            inferredSetRecordId = "set_inferred",
            inferenceRunId = "run_1",
            rawCanonical = 70.0,
            rawObservationId = "observation_raw",
            rawSetRecordId = "set_raw",
        )
        val raw = SameProfileMetricEvidenceResolver.resolve(
            inferredCanonical = null,
            inferredObservationId = null,
            inferredSetRecordId = null,
            inferenceRunId = null,
            rawCanonical = 70.0,
            rawObservationId = "observation_raw",
            rawSetRecordId = "set_raw",
        )

        assertEquals(SameProfileMetricEvidenceResolver.INFERENCE_SOURCE, inferred?.source)
        assertEquals("observation_inferred", inferred?.sourceObservationId)
        assertEquals(SameProfileMetricEvidenceResolver.RAW_HISTORY_SOURCE, raw?.source)
        assertEquals("observation_raw", raw?.sourceObservationId)
    }

    private fun request(
        schema: PerformanceSchema,
        preferred: MetricTarget? = null,
        evidence: Pair<PerformanceMetric, PrescriptionEvidence>? = null,
    ) = PrescriptionRequest(
        exerciseId = ExerciseId("exercise_press"),
        executionProfileId = ExecutionProfileId("execution_press_default"),
        executionProfileVersionId = ExecutionProfileVersionId("execution_press_default:v1"),
        targetIds = listOf(TrainingTargetId("target_chest")),
        sets = 3,
        schema = schema,
        preferredTemplate = PerformanceTargetTemplate(listOfNotNull(preferred)),
        evidenceByMetric = evidence?.let { mapOf(it) }.orEmpty(),
        laterality = Laterality.BILATERAL,
        restSeconds = 120,
    )

    private fun schema(family: MetricFamily, vararg metrics: SchemaMetric) = PerformanceSchema(
        id = "schema_${family.storageValue}",
        version = 1,
        family = family,
        metrics = metrics.toList(),
        provenance = "test",
    )

    private fun evidence(value: Double) = PrescriptionEvidence(
        source = "test_same_profile",
        sourceObservationId = "observation_anchor",
        sourceSetRecordId = "set_anchor",
        inferenceRunId = "run_1",
        anchorCanonical = value,
        modelVersion = "test-v1",
    )
}
