package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.HistoricalCompletedSetEvidenceRevision
import java.time.Instant

data class NBio7DCorrectionBoundaryValidationReport(
    val latePriorCorrectionDoesNotLeakBackward: Boolean,
    val targetCorrectionRepairsTargetWithoutChangingBaseline: Boolean,
    val priorCorrectionEntersLaterSessionBaseline: Boolean,
    val simultaneousSessionExcludedFromPriorBaseline: Boolean,
) {
    val passed: Boolean get() = latePriorCorrectionDoesNotLeakBackward &&
        targetCorrectionRepairsTargetWithoutChangingBaseline &&
        priorCorrectionEntersLaterSessionBaseline && simultaneousSessionExcludedFromPriorBaseline
}

object NBio7DCorrectionBoundaryValidation {
    fun run(): NBio7DCorrectionBoundaryValidationReport {
        val priorOld = revision("prior_old", "prior", "2026-01-01T10:30:00Z", "2026-01-01T11:00:00Z", "2026-01-01T11:00:00Z", 50.0)
        val priorNew = revision(
            "prior_new", "prior", "2026-01-01T10:30:00Z", "2026-01-01T11:00:00Z", "2026-01-03T13:00:00Z", 55.0,
            supersedes = "prior_old",
        )
        val targetOld = revision("target_old", "target", "2026-01-03T12:30:00Z", "2026-01-03T13:30:00Z", "2026-01-03T12:30:00Z", 60.0)
        val targetNew = revision(
            "target_new", "target", "2026-01-03T12:30:00Z", "2026-01-03T13:30:00Z", "2026-01-04T09:00:00Z", 62.0,
            supersedes = "target_old",
        )
        val targetSlice = NBio7DCausalHistory.slice(
            listOf(priorOld, priorNew, targetOld, targetNew),
            "target",
            Instant.parse("2026-01-03T12:00:00Z"),
            Instant.parse("2026-01-05T00:00:00Z"),
        )
        val lateDoesNotLeak = targetSlice.training.map { it.observationId } == listOf("prior_old")
        val targetRepair = targetSlice.target.map { it.observationId } == listOf("target_new") && lateDoesNotLeak

        val later = revision("later", "later", "2026-01-05T10:30:00Z", "2026-01-05T11:00:00Z", "2026-01-05T10:30:00Z", 65.0)
        val laterSlice = NBio7DCausalHistory.slice(
            listOf(priorOld, priorNew, later),
            "later",
            Instant.parse("2026-01-05T10:00:00Z"),
            Instant.parse("2026-01-05T12:00:00Z"),
        )
        val priorEntersLater = laterSlice.training.map { it.observationId } == listOf("prior_new")

        val simultaneous = revision(
            "simultaneous", "simultaneous", "2026-01-06T11:30:00Z", "2026-01-06T12:00:00Z", "2026-01-06T12:00:00Z", 55.0,
        )
        val simultaneousTarget = revision(
            "sim_target", "sim_target", "2026-01-06T12:30:00Z", "2026-01-06T13:00:00Z", "2026-01-06T12:30:00Z", 65.0,
        )
        val simultaneousSlice = NBio7DCausalHistory.slice(
            listOf(simultaneous, simultaneousTarget),
            "sim_target",
            Instant.parse("2026-01-06T12:00:00Z"),
            Instant.parse("2026-01-06T14:00:00Z"),
        )
        return NBio7DCorrectionBoundaryValidationReport(
            latePriorCorrectionDoesNotLeakBackward = lateDoesNotLeak,
            targetCorrectionRepairsTargetWithoutChangingBaseline = targetRepair,
            priorCorrectionEntersLaterSessionBaseline = priorEntersLater,
            simultaneousSessionExcludedFromPriorBaseline = simultaneousSlice.training.isEmpty(),
        )
    }

    private fun revision(
        observationId: String,
        sessionId: String,
        setCompletedAt: String,
        sessionCompletedAt: String,
        recordedAt: String,
        load: Double,
        supersedes: String? = null,
    ) = HistoricalCompletedSetEvidenceRevision(
        evidence = CompletedSetEvidence(
            setRecordId = "set_$observationId",
            observationId = observationId,
            sessionExerciseId = "se_$sessionId",
            executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            laterality = Laterality.BILATERAL,
            completedAt = Instant.parse(setCompletedAt),
            metricValues = listOf(
                PerformanceMetricValue(
                    PerformanceMetric.EXTERNAL_LOAD,
                    Quantity(load, UnitId.KILOGRAM),
                    Quantity(load, UnitId.KILOGRAM),
                ),
                PerformanceMetricValue(
                    PerformanceMetric.REPETITIONS,
                    Quantity(8.0, UnitId.REPETITION),
                    Quantity(8.0, UnitId.REPETITION),
                ),
            ),
            bodyMassContextKg = null,
            warmUp = false,
            kind = "working",
            sessionId = sessionId,
        ),
        recordedAt = Instant.parse(recordedAt),
        sessionCompletedAt = Instant.parse(sessionCompletedAt),
        supersedesObservationId = supersedes,
    )
}
