package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFit
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityQuery
import dev.kian.mymettle.engine.performance.NonDynamicCapabilitySolver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class NonDynamicMarginalFidelity(
    val parameter: String,
    val referenceP05: Double,
    val referenceP50: Double,
    val referenceP95: Double,
    val referenceVariance: Double,
    val candidateP05: Double,
    val candidateP50: Double,
    val candidateP95: Double,
    val candidateVariance: Double,
)

data class NonDynamicCovarianceFidelity(
    val leftParameter: String,
    val rightParameter: String,
    val referenceCovariance: Double,
    val candidateCovariance: Double,
    val correlationScaleError: Double?,
)

data class NonDynamicQueryFidelity(
    val queryLabel: String,
    val referenceP05: Double,
    val referenceP50: Double,
    val referenceP95: Double,
    val candidateP05: Double,
    val candidateP50: Double,
    val candidateP95: Double,
    val p05RelativeError: Double,
    val p50RelativeError: Double,
    val p95RelativeError: Double,
)

data class NonDynamicPosteriorFidelityResult(
    val referenceSolver: String,
    val candidateSolver: String,
    val referenceNodeCount: Int,
    val candidateNodeCount: Int,
    val referenceRetainedBaseNodeCount: Int,
    val candidateRetainedBaseNodeCount: Int,
    val referencePositiveTrajectoryProbability: Double,
    val candidatePositiveTrajectoryProbability: Double,
    val positiveTrajectoryProbabilityAbsoluteError: Double,
    val marginals: List<NonDynamicMarginalFidelity>,
    val covariances: List<NonDynamicCovarianceFidelity>,
    val queries: List<NonDynamicQueryFidelity>,
) {
    val maximumQueryTailRelativeError: Double
        get() = queries.maxOfOrNull { max(it.p05RelativeError, it.p95RelativeError) } ?: 0.0
}

object NonDynamicPosteriorFidelity {
    fun compare(
        referenceSolver: NonDynamicCapabilitySolver,
        reference: NonDynamicCapabilityFit,
        candidateSolver: NonDynamicCapabilitySolver,
        candidate: NonDynamicCapabilityFit,
        queries: List<Pair<String, NonDynamicCapabilityQuery>>,
    ): NonDynamicPosteriorFidelityResult {
        require(reference.mathematicalModelIdentity == candidate.mathematicalModelIdentity) {
            "7C fidelity requires identical mathematical model identity."
        }
        require(reference.family == candidate.family)
        val marginals = buildList {
            add(marginal("frontier", reference.frontierAtReference.summary!!, candidate.frontierAtReference.summary!!))
            if (reference.slope != null && candidate.slope != null) add(marginal("slope", reference.slope.summary, candidate.slope.summary))
            add(marginal("trajectory", reference.trajectory.summary, candidate.trajectory.summary))
            add(marginal("slackScale", reference.slackScale.summary, candidate.slackScale.summary))
            add(marginal("noiseScale", reference.noiseScale.summary, candidate.noiseScale.summary))
        }
        val covariancePairs = buildList {
            add("frontier" to "trajectory")
            if (reference.slope != null && candidate.slope != null) {
                add("frontier" to "slope")
                add("slope" to "trajectory")
            }
        }
        val covariances = covariancePairs.map { (left, right) ->
            val refCov = covariance(reference, left, right)
            val candCov = covariance(candidate, left, right)
            val refSd = sqrt(max(0.0, variance(reference, left) * variance(reference, right)))
            val error = if (refSd > 1e-12) abs(candCov - refCov) / refSd else null
            NonDynamicCovarianceFidelity(left, right, refCov, candCov, error)
        }
        val queryResults = queries.map { (label, query) ->
            val ref = requireNotNull(referenceSolver.predict(reference, query).summary)
            val cand = requireNotNull(candidateSolver.predict(candidate, query).summary)
            NonDynamicQueryFidelity(
                label,
                ref.p05, ref.p50, ref.p95,
                cand.p05, cand.p50, cand.p95,
                relativeError(cand.p05, ref.p05),
                relativeError(cand.p50, ref.p50),
                relativeError(cand.p95, ref.p95),
            )
        }
        return NonDynamicPosteriorFidelityResult(
            referenceSolver = reference.solverDiagnostics.solverIdentity.identity,
            candidateSolver = candidate.solverDiagnostics.solverIdentity.identity,
            referenceNodeCount = reference.posteriorNodes.size,
            candidateNodeCount = candidate.posteriorNodes.size,
            referenceRetainedBaseNodeCount = reference.retainedBaseNodeCount,
            candidateRetainedBaseNodeCount = candidate.retainedBaseNodeCount,
            referencePositiveTrajectoryProbability = reference.positiveTrajectoryProbability,
            candidatePositiveTrajectoryProbability = candidate.positiveTrajectoryProbability,
            positiveTrajectoryProbabilityAbsoluteError = abs(reference.positiveTrajectoryProbability - candidate.positiveTrajectoryProbability),
            marginals = marginals,
            covariances = covariances,
            queries = queryResults,
        )
    }

    private fun marginal(
        name: String,
        reference: dev.kian.mymettle.domain.inference.PosteriorSummary,
        candidate: dev.kian.mymettle.domain.inference.PosteriorSummary,
    ) = NonDynamicMarginalFidelity(
        name,
        reference.p05, reference.p50, reference.p95, reference.posteriorVariance,
        candidate.p05, candidate.p50, candidate.p95, candidate.posteriorVariance,
    )

    private fun relativeError(candidate: Double, reference: Double): Double = abs(candidate - reference) / max(1e-12, abs(reference))

    private fun covariance(fit: NonDynamicCapabilityFit, left: String, right: String): Double {
        val leftMean = fit.posteriorNodes.sumOf { value(it, left) * it.posteriorWeight }
        val rightMean = fit.posteriorNodes.sumOf { value(it, right) * it.posteriorWeight }
        return fit.posteriorNodes.sumOf {
            it.posteriorWeight * (value(it, left) - leftMean) * (value(it, right) - rightMean)
        }
    }

    private fun variance(fit: NonDynamicCapabilityFit, parameter: String): Double {
        val mean = fit.posteriorNodes.sumOf { value(it, parameter) * it.posteriorWeight }
        return fit.posteriorNodes.sumOf { it.posteriorWeight * (value(it, parameter) - mean) * (value(it, parameter) - mean) }
    }

    private fun value(node: dev.kian.mymettle.domain.inference.NonDynamicPosteriorNode, parameter: String): Double = when (parameter) {
        "frontier" -> node.logFrontierAtReference
        "slope" -> requireNotNull(node.slope)
        "trajectory" -> node.trajectory
        "slackScale" -> node.slackScale
        "noiseScale" -> node.noiseScale
        else -> error("Unsupported 7C posterior parameter $parameter")
    }
}
