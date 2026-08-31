package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicCandidateDistributionMetrics
import dev.kian.mymettle.domain.inference.DynamicCandidateV2DevelopmentPolicy
import dev.kian.mymettle.domain.inference.DynamicCandidateV2DevelopmentVerdictPolicy
import dev.kian.mymettle.domain.inference.DynamicHeldOutEvaluation
import dev.kian.mymettle.domain.inference.DynamicHeldOutStatus
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.performance.Laterality
import kotlin.math.abs

/**
 * Corrected retrospective protocol layered over the immutable first solver bake-off implementation.
 *
 * Historical protocol v1 accidentally populated `demonstrationMedianMaeKg` with the arithmetic mean
 * absolute error. The held-out predictions themselves remain valid/frozen. This wrapper recomputes
 * the intended median absolute error from those immutable predictions and reruns the Candidate-v2
 * development comparison under a new policy identity rather than silently changing old results.
 */
class DynamicTrendSolverHistoricalBakeoffCorrected(
    solvers: List<DynamicTrendCandidateV2Solver>,
    private val correctedDevelopmentPolicy: DynamicCandidateV2DevelopmentPolicy = DynamicCandidateV2DevelopmentPolicy(
        semanticVersion = "n-bio-7b-candidate-v2-development-comparison-v2-median-absolute-error",
    ),
    private val delegate: DynamicTrendSolverHistoricalBakeoff = DynamicTrendSolverHistoricalBakeoff(solvers),
) {
    fun evaluate(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
        revisions: Collection<HistoricalCompletedSetEvidenceRevision>,
    ): DynamicTrendSolverHistoricalBakeoffResult {
        val original = delegate.evaluate(profile, side, revisions)
        val correctedV1 = original.v1PredictiveMetrics.withCorrectMedianAbsoluteError(original.v1Observations)
        val correctedCandidates = original.candidates.map { candidate ->
            val correctedMetrics = candidate.predictiveMetrics.withCorrectMedianAbsoluteError(candidate.observations)
            candidate.copy(
                predictiveMetrics = correctedMetrics,
                developmentComparisonAgainstV1 = DynamicCandidateV2DevelopmentVerdictPolicy.compare(
                    correctedV1.distribution,
                    correctedMetrics.distribution,
                    candidate.absoluteValidationVerdict,
                    correctedDevelopmentPolicy,
                ),
            )
        }
        return original.copy(
            protocolVersion = original.protocolVersion + "|pointMetric=median_absolute_error_v2",
            v1PredictiveMetrics = correctedV1,
            candidates = correctedCandidates,
        )
    }

    private fun DynamicTrendSolverPredictiveMetrics.withCorrectMedianAbsoluteError(
        observations: List<DynamicHeldOutEvaluation>,
    ): DynamicTrendSolverPredictiveMetrics {
        val absoluteErrors = observations.asSequence()
            .filter { it.status == DynamicHeldOutStatus.EVALUABLE }
            .mapNotNull { observation ->
                observation.candidatePredictive?.let { predictive ->
                    abs(predictive.p50ResistanceKg - observation.observedResistanceKg)
                }
            }
            .sorted()
            .toList()
        val medianAbsoluteError = absoluteErrors.medianOrNull()
        return copy(
            distribution = distribution.copy(demonstrationMedianMaeKg = medianAbsoluteError),
        )
    }

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val middle = size / 2
        return if (size % 2 == 1) this[middle] else (this[middle - 1] + this[middle]) / 2.0
    }
}
