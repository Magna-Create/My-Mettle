package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** Marginal and dependence fidelity of one Candidate-v2 solver against a same-math reference. */
data class DynamicTrendMarginalFidelity(
    val parameter: String,
    val referenceP05: Double,
    val referenceP50: Double,
    val referenceP95: Double,
    val candidateP05: Double,
    val candidateP50: Double,
    val candidateP95: Double,
    val referenceVariance: Double,
    val candidateVariance: Double,
    /** 1-Wasserstein approximation from 101 weighted quantiles, in the parameter's native units. */
    val quantileWasserstein1: Double,
    /** W1 divided by max(reference posterior SD, tiny epsilon), for cross-parameter inspection. */
    val standardisedQuantileWasserstein1: Double,
)

data class DynamicTrendCovarianceFidelity(
    val leftParameter: String,
    val rightParameter: String,
    val referenceCovariance: Double,
    val candidateCovariance: Double,
    val absoluteError: Double,
    /** Error divided by sqrt(reference variance products), null only for degenerate reference axes. */
    val correlationScaleError: Double?,
)

data class DynamicTrendPosteriorFidelityResult(
    val referenceSolver: String,
    val candidateSolver: String,
    val referenceNodeCount: Int,
    val candidateNodeCount: Int,
    val marginals: List<DynamicTrendMarginalFidelity>,
    val covariances: List<DynamicTrendCovarianceFidelity>,
    val referenceTrendPositiveProbability: Double,
    val candidateTrendPositiveProbability: Double,
    val trendPositiveProbabilityAbsoluteError: Double,
    val referenceNextFrontierP05Kg: Double,
    val referenceNextFrontierP50Kg: Double,
    val referenceNextFrontierP95Kg: Double,
    val candidateNextFrontierP05Kg: Double,
    val candidateNextFrontierP50Kg: Double,
    val candidateNextFrontierP95Kg: Double,
    val nextFrontierMedianRelativeError: Double,
    val maxStandardisedMarginalWasserstein1: Double,
    val maxCovarianceCorrelationScaleError: Double?,
)

/**
 * Solver-fidelity diagnostics for Candidate-v2 joint posterior state.
 *
 * This intentionally inspects more than posterior means: p05/p50/p95, variance, c/b/g/nuisance
 * covariance, the P(g>0) tail event, and next-session frontier quantiles all participate. No pass
 * threshold is embedded here; production solver policy is a separate evidence-backed decision.
 */
object DynamicTrendPosteriorFidelity {
    private val parameterNames = listOf(
        "logFrontierAtLatestSession",
        "slope",
        "frontierTrend",
        "slackScale",
        "noiseScale",
    )

    fun compare(
        reference: DynamicTrendFrontierFit,
        candidate: DynamicTrendFrontierFit,
        nextSessionOffset: Double = 1.0,
    ): DynamicTrendPosteriorFidelityResult {
        require(reference.mathematicalModelIdentity == candidate.mathematicalModelIdentity) {
            "Posterior solver fidelity is meaningful only for the identical mathematical model."
        }
        require(reference.executionProfileVersionId == candidate.executionProfileVersionId)
        require(reference.side == candidate.side)
        require(reference.referenceRepetitions == candidate.referenceRepetitions)
        require(nextSessionOffset.isFinite())

        val referenceAxes = axes(reference)
        val candidateAxes = axes(candidate)
        val marginals = parameterNames.map { parameter ->
            marginal(parameter, referenceAxes.getValue(parameter), candidateAxes.getValue(parameter))
        }
        val covariances = buildList {
            for (left in parameterNames.indices) {
                for (right in left + 1 until parameterNames.size) {
                    val a = parameterNames[left]
                    val b = parameterNames[right]
                    add(covarianceFidelity(a, b, reference, candidate))
                }
            }
        }
        val referenceTrendPositive = reference.posteriorNodes
            .filter { it.frontierTrend > 0.0 }
            .sumOf { it.posteriorWeight }
            .coerceIn(0.0, 1.0)
        val candidateTrendPositive = candidate.posteriorNodes
            .filter { it.frontierTrend > 0.0 }
            .sumOf { it.posteriorWeight }
            .coerceIn(0.0, 1.0)
        val referenceNext = weightedSummary(
            reference.posteriorNodes.map {
                WeightedValue(exp(it.logFrontierAtLatestSession + it.frontierTrend * nextSessionOffset), it.posteriorWeight)
            },
        )
        val candidateNext = weightedSummary(
            candidate.posteriorNodes.map {
                WeightedValue(exp(it.logFrontierAtLatestSession + it.frontierTrend * nextSessionOffset), it.posteriorWeight)
            },
        )
        val covarianceErrors = covariances.mapNotNull { it.correlationScaleError }
        return DynamicTrendPosteriorFidelityResult(
            referenceSolver = reference.solverDiagnostics.solverIdentity.semanticVersion,
            candidateSolver = candidate.solverDiagnostics.solverIdentity.semanticVersion,
            referenceNodeCount = reference.posteriorNodes.size,
            candidateNodeCount = candidate.posteriorNodes.size,
            marginals = marginals,
            covariances = covariances,
            referenceTrendPositiveProbability = referenceTrendPositive,
            candidateTrendPositiveProbability = candidateTrendPositive,
            trendPositiveProbabilityAbsoluteError = abs(referenceTrendPositive - candidateTrendPositive),
            referenceNextFrontierP05Kg = referenceNext.p05,
            referenceNextFrontierP50Kg = referenceNext.p50,
            referenceNextFrontierP95Kg = referenceNext.p95,
            candidateNextFrontierP05Kg = candidateNext.p05,
            candidateNextFrontierP50Kg = candidateNext.p50,
            candidateNextFrontierP95Kg = candidateNext.p95,
            nextFrontierMedianRelativeError = abs(candidateNext.p50 - referenceNext.p50) / referenceNext.p50,
            maxStandardisedMarginalWasserstein1 = marginals.maxOf { it.standardisedQuantileWasserstein1 },
            maxCovarianceCorrelationScaleError = covarianceErrors.maxOrNull(),
        )
    }

    private fun axes(fit: DynamicTrendFrontierFit): Map<String, List<WeightedValue>> = mapOf(
        "logFrontierAtLatestSession" to fit.posteriorNodes.map { WeightedValue(it.logFrontierAtLatestSession, it.posteriorWeight) },
        "slope" to fit.posteriorNodes.map { WeightedValue(it.slope, it.posteriorWeight) },
        "frontierTrend" to fit.posteriorNodes.map { WeightedValue(it.frontierTrend, it.posteriorWeight) },
        "slackScale" to fit.posteriorNodes.map { WeightedValue(it.slackScale, it.posteriorWeight) },
        "noiseScale" to fit.posteriorNodes.map { WeightedValue(it.noiseScale, it.posteriorWeight) },
    )

    private fun marginal(
        name: String,
        reference: List<WeightedValue>,
        candidate: List<WeightedValue>,
    ): DynamicTrendMarginalFidelity {
        val referenceSummary = weightedSummary(reference)
        val candidateSummary = weightedSummary(candidate)
        var wasserstein = 0.0
        val intervals = 100
        for (index in 0..intervals) {
            val probability = index.toDouble() / intervals.toDouble()
            wasserstein += abs(weightedQuantile(reference, probability) - weightedQuantile(candidate, probability))
        }
        wasserstein /= (intervals + 1).toDouble()
        val referenceSd = sqrt(max(referenceSummary.variance, 0.0))
        return DynamicTrendMarginalFidelity(
            parameter = name,
            referenceP05 = referenceSummary.p05,
            referenceP50 = referenceSummary.p50,
            referenceP95 = referenceSummary.p95,
            candidateP05 = candidateSummary.p05,
            candidateP50 = candidateSummary.p50,
            candidateP95 = candidateSummary.p95,
            referenceVariance = referenceSummary.variance,
            candidateVariance = candidateSummary.variance,
            quantileWasserstein1 = wasserstein,
            standardisedQuantileWasserstein1 = wasserstein / max(referenceSd, 1e-12),
        )
    }

    private fun covarianceFidelity(
        left: String,
        right: String,
        reference: DynamicTrendFrontierFit,
        candidate: DynamicTrendFrontierFit,
    ): DynamicTrendCovarianceFidelity {
        val ref = covariance(reference, left, right)
        val cand = covariance(candidate, left, right)
        val leftVariance = covariance(reference, left, left)
        val rightVariance = covariance(reference, right, right)
        val scale = sqrt(max(leftVariance * rightVariance, 0.0))
        return DynamicTrendCovarianceFidelity(
            leftParameter = left,
            rightParameter = right,
            referenceCovariance = ref,
            candidateCovariance = cand,
            absoluteError = abs(ref - cand),
            correlationScaleError = if (scale <= 1e-15) null else abs(ref - cand) / scale,
        )
    }

    private fun covariance(fit: DynamicTrendFrontierFit, left: String, right: String): Double {
        fun value(node: dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode, parameter: String): Double = when (parameter) {
            "logFrontierAtLatestSession" -> node.logFrontierAtLatestSession
            "slope" -> node.slope
            "frontierTrend" -> node.frontierTrend
            "slackScale" -> node.slackScale
            "noiseScale" -> node.noiseScale
            else -> error("Unknown Candidate-v2 parameter $parameter")
        }
        val meanLeft = fit.posteriorNodes.sumOf { value(it, left) * it.posteriorWeight }
        val meanRight = fit.posteriorNodes.sumOf { value(it, right) * it.posteriorWeight }
        return fit.posteriorNodes.sumOf {
            it.posteriorWeight * (value(it, left) - meanLeft) * (value(it, right) - meanRight)
        }
    }

    private data class WeightedValue(val value: Double, val weight: Double)
    private data class Summary(val p05: Double, val p50: Double, val p95: Double, val variance: Double)

    private fun weightedSummary(values: List<WeightedValue>): Summary {
        require(values.isNotEmpty())
        require(values.all { it.value.isFinite() && it.weight.isFinite() && it.weight >= 0.0 }) {
            "Posterior fidelity summary requires finite values and non-negative finite weights."
        }
        val total = values.sumOf { it.weight }
        require(total > 0.0 && total.isFinite())
        val normalised = values.map { WeightedValue(it.value, it.weight / total) }
        val mean = normalised.sumOf { it.value * it.weight }
        val variance = normalised.sumOf { it.weight * (it.value - mean) * (it.value - mean) }
        return Summary(
            p05 = weightedQuantile(normalised, 0.05),
            p50 = weightedQuantile(normalised, 0.50),
            p95 = weightedQuantile(normalised, 0.95),
            variance = variance,
        )
    }

    private fun weightedQuantile(values: List<WeightedValue>, probability: Double): Double {
        require(probability in 0.0..1.0)
        val ordered = values.sortedBy { it.value }
        val total = ordered.sumOf { it.weight }
        val target = probability * total
        var cumulative = 0.0
        ordered.forEach {
            cumulative += it.weight
            if (cumulative >= target) return it.value
        }
        return ordered.last().value
    }
}
