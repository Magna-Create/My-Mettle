package dev.kian.mymettle.engine.prescription

import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricTarget
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceSchema
import dev.kian.mymettle.domain.performance.PerformanceTargetTemplate
import dev.kian.mymettle.domain.performance.PrescriptionEvidence
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.training.ExercisePrescription
import dev.kian.mymettle.domain.training.SetPrescription
import dev.kian.mymettle.domain.training.TrainingTargetId
import kotlin.math.abs
import kotlin.math.round

data class PrescriptionRequest(
    val exerciseId: ExerciseId,
    val executionProfileId: ExecutionProfileId,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val targetIds: List<TrainingTargetId>,
    val sets: Int,
    val schema: PerformanceSchema,
    val preferredTemplate: PerformanceTargetTemplate,
    val evidenceByMetric: Map<PerformanceMetric, PrescriptionEvidence>,
    val laterality: Laterality,
    val restSeconds: Int,
)

interface PrescriptionEngine {
    val modelVersion: String

    fun generate(request: PrescriptionRequest): ExercisePrescription
}

/**
 * Metric-general N-BIO-6 benchmark engine.
 *
 * It carries compatible preference targets forward and may anchor otherwise-unset metrics to the
 * latest same-version observation. It does not progress, translate between profiles or manufacture
 * a target when neither a preference nor compatible evidence exists.
 */
class HistoryBackedPrescriptionEngine : PrescriptionEngine {
    override val modelVersion: String = MODEL_VERSION

    override fun generate(request: PrescriptionRequest): ExercisePrescription {
        require(request.sets > 0)
        val schemaByMetric = request.schema.metrics.associateBy { it.metric }
        val preferred = request.preferredTemplate.metricTargets
            .filter { it.metric in schemaByMetric && schemaByMetric.getValue(it.metric).targetable }
            .associateBy { it.metric }
        val evidenceTargets = request.evidenceByMetric.mapNotNull { (metric, evidence) ->
            val definition = schemaByMetric[metric] ?: return@mapNotNull null
            if (!definition.targetable || metric in preferred) return@mapNotNull null
            val anchor = evidence.anchorCanonical ?: return@mapNotNull null
            MetricTarget(
                metric = metric,
                kind = TargetKind.EXACT,
                lowerCanonical = definition.conform(anchor),
                canonicalUnit = metric.canonicalUnit,
                displayUnit = definition.defaultUnit,
                evidence = evidence,
            )
        }.associateBy { it.metric }
        val targets = (preferred + evidenceTargets).values.sortedBy { it.metric.ordinal }

        return ExercisePrescription(
            exerciseId = request.exerciseId,
            executionProfileId = request.executionProfileId,
            executionProfileVersionId = request.executionProfileVersionId,
            targetIds = request.targetIds.distinct(),
            setPrescriptions = List(request.sets) { index ->
                SetPrescription(
                    index = index,
                    kind = "prescribed",
                    laterality = request.laterality,
                    metricTargets = targets,
                )
            },
            restSeconds = request.restSeconds,
            generatedByModelVersion = modelVersion,
        )
    }

    private fun dev.kian.mymettle.domain.performance.SchemaMetric.conform(value: Double): Double {
        val implicitMinimum = if (metric == PerformanceMetric.INCLINE_GRADE) {
            Double.NEGATIVE_INFINITY
        } else {
            0.0
        }
        val lowerBound = minimumCanonical ?: implicitMinimum
        val constrained = value
            .coerceAtLeast(lowerBound)
            .let { candidate -> maximumCanonical?.let { minOf(candidate, it) } ?: candidate }
        if (allowedCanonicalValues.isNotEmpty()) {
            return allowedCanonicalValues.minWith(compareBy<Double> { abs(it - constrained) }.thenBy { it })
        }
        val step = incrementCanonical?.takeIf { it > 0.0 } ?: return constrained
        val origin = minimumCanonical ?: 0.0
        return (origin + round((constrained - origin) / step) * step)
            .coerceAtLeast(lowerBound)
            .let { candidate -> maximumCanonical?.let { minOf(candidate, it) } ?: candidate }
    }

    companion object {
        const val MODEL_VERSION = "n-bio-6-same-profile-metric-prescription-v1"
    }
}

object SameProfileMetricEvidenceResolver {
    const val INFERENCE_SOURCE = "inference_same_profile_version_anchor"
    const val RAW_HISTORY_SOURCE = "raw_same_profile_version_history"

    fun resolve(
        inferredCanonical: Double?,
        inferredObservationId: String?,
        inferredSetRecordId: String?,
        inferenceRunId: String?,
        rawCanonical: Double?,
        rawObservationId: String?,
        rawSetRecordId: String?,
    ): PrescriptionEvidence? = when {
        inferredCanonical != null -> PrescriptionEvidence(
            source = INFERENCE_SOURCE,
            sourceObservationId = inferredObservationId,
            sourceSetRecordId = inferredSetRecordId,
            inferenceRunId = inferenceRunId,
            anchorCanonical = inferredCanonical,
            modelVersion = MODEL_VERSION,
        )
        rawCanonical != null -> PrescriptionEvidence(
            source = RAW_HISTORY_SOURCE,
            sourceObservationId = rawObservationId,
            sourceSetRecordId = rawSetRecordId,
            inferenceRunId = null,
            anchorCanonical = rawCanonical,
            modelVersion = MODEL_VERSION,
        )
        else -> null
    }

    const val MODEL_VERSION = "n-bio-6-same-profile-evidence-selection-v1"
}
