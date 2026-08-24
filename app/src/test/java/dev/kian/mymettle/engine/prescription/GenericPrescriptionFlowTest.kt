package dev.kian.mymettle.engine.prescription

import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.MetricTarget
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.PerformanceObservation
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.PerformanceTargetTemplate
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.SchemaMetric
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.domain.training.SessionConstraints
import dev.kian.mymettle.engine.targeting.BudgetedTargetExerciseSelector
import dev.kian.mymettle.engine.targeting.ExerciseSelectionCandidate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GenericPrescriptionFlowTest {
    private val selector = BudgetedTargetExerciseSelector()
    private val engine = HistoryBackedPrescriptionEngine()

    @Test
    fun `dead hang flows from rep-free preference through selection prescription and recording`() {
        val schema = schema(
            MetricFamily.DURATION_ONLY,
            SchemaMetric(PerformanceMetric.DURATION, required = true),
        )
        val duration = MetricTarget(
            PerformanceMetric.DURATION,
            TargetKind.RANGE,
            lowerCanonical = 35.0,
            upperCanonical = 45.0,
        )
        val selected = selector.select(
            targets = emptyList(),
            candidates = listOf(candidate("dead_hang", schema, PerformanceTargetTemplate(listOf(duration)))),
            constraints = constraints(),
        ).single()
        val prescription = engine.generate(request(selected.sets, selected.candidate, schema))
        val observation = observation(
            "dead_hang",
            Laterality.BILATERAL,
            schema,
            PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(42.0, UnitId.SECOND)),
        )

        assertNull(prescription.repRange)
        assertEquals(35.0, prescription.setPrescriptions.first().metricTargets.single().lowerCanonical)
        assertEquals(42.0, observation.values.single().canonical.value)
        assertEquals(emptyList(), observation.values.filter { it.metric == PerformanceMetric.REPETITIONS })
    }

    @Test
    fun `grip hold keeps load duration and left right observations as separate facts`() {
        val schema = schema(
            MetricFamily.LOADED_HOLD,
            SchemaMetric(PerformanceMetric.EXTERNAL_LOAD, required = true),
            SchemaMetric(PerformanceMetric.DURATION, required = true),
        )
        val left = observation(
            "grip_left",
            Laterality.LEFT,
            schema,
            PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(20.0, UnitId.KILOGRAM)),
            PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(35.0, UnitId.SECOND)),
        )
        val right = observation(
            "grip_right",
            Laterality.RIGHT,
            schema,
            PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(20.0, UnitId.KILOGRAM)),
            PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(32.0, UnitId.SECOND)),
        )

        assertEquals(listOf(Laterality.LEFT, Laterality.RIGHT), listOf(left, right).map { it.laterality })
        assertEquals(listOf(35.0, 32.0), listOf(left, right).map { observation ->
            observation.values.single { it.metric == PerformanceMetric.DURATION }.canonical.value
        })
    }

    private fun candidate(
        name: String,
        schema: PerformanceSchema,
        template: PerformanceTargetTemplate,
    ) = ExerciseSelectionCandidate(
        preferenceId = "slot_$name",
        exerciseId = ExerciseId("exercise_$name"),
        executionProfileId = ExecutionProfileId("profile_$name"),
        executionProfileVersionId = ExecutionProfileVersionId("profile_$name:v1"),
        ordinal = 0,
        preferencePriority = 1.0,
        preferredSetCap = 1,
        preferredTemplate = template,
        restSeconds = 60,
        targetCoverage = emptyMap(),
    )

    private fun request(
        sets: Int,
        candidate: ExerciseSelectionCandidate,
        schema: PerformanceSchema,
    ) = PrescriptionRequest(
        exerciseId = candidate.exerciseId,
        executionProfileId = candidate.executionProfileId,
        executionProfileVersionId = candidate.executionProfileVersionId,
        targetIds = emptyList(),
        sets = sets,
        schema = schema,
        preferredTemplate = candidate.preferredTemplate,
        evidenceByMetric = emptyMap(),
        laterality = Laterality.BILATERAL,
        restSeconds = candidate.restSeconds,
    )

    private fun observation(
        id: String,
        side: Laterality,
        schema: PerformanceSchema,
        vararg values: PerformanceMetricValue,
    ): PerformanceObservation {
        schema.validate(values.toList())
        return PerformanceObservation(
            id = "observation_$id",
            setRecordId = "set_$id",
            executionProfileVersionId = ExecutionProfileVersionId("profile_$id:v1"),
            ordinal = 0,
            laterality = side,
            completedAt = Instant.parse("2026-08-20T10:00:00Z"),
            source = "test",
            bodyMassContextKg = null,
            values = values.toList(),
        )
    }

    private fun schema(family: MetricFamily, vararg metrics: SchemaMetric) = PerformanceSchema(
        id = "schema_${family.storageValue}",
        version = 1,
        family = family,
        metrics = metrics.toList(),
        provenance = "test",
    )

    private fun constraints() = SessionConstraints(
        mode = "test",
        workingSetBudget = 1,
        exerciseBudget = 1,
        minimumSetsPerExercise = 1,
        targetPriorityFloor = 0.0,
        timeBudgetSeconds = null,
        source = "test",
        resolverModelVersion = "test",
    )
}
