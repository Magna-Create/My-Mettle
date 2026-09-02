package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.performance.MetricFamily
import kotlin.math.ln

/**
 * Family-specific projection of an already-fit contemporaneous capability posterior into the
 * N-BIO-7D frontier coordinate. No capability fit, action-policy model, or RIR reconstruction occurs
 * here. A 7D causal replay supplies a fit built only from sessions before the performed session.
 */
object NBio7DCapabilityProjection {
    const val PRE_SESSION_TARGET_OFFSET = 1.0

    fun dynamicResistanceLogFrontier(
        fit: DynamicTrendFrontierFit,
        observedRepetitions: Int,
    ): List<WeightedScalarNode> {
        require(observedRepetitions > 0)
        val x = ln(observedRepetitions.toDouble() / fit.referenceRepetitions)
        return fit.posteriorNodes.mapIndexed { index, node ->
            WeightedScalarNode(
                key = "dynamic-$index",
                value = node.logFrontierAtLatestSession +
                    node.frontierTrend * PRE_SESSION_TARGET_OFFSET -
                    node.slope * x,
                weight = node.posteriorWeight,
            )
        }
    }

    fun loadedHoldLogFrontier(
        fit: NonDynamicCapabilityFit,
        observedDurationSeconds: Double,
    ): List<WeightedScalarNode> {
        require(fit.family == MetricFamily.LOADED_HOLD)
        require(observedDurationSeconds.isFinite() && observedDurationSeconds > 0.0)
        val reference = requireNotNull(fit.referenceCoordinate)
        val x = ln(observedDurationSeconds / reference)
        return fit.posteriorNodes.mapIndexed { index, node ->
            WeightedScalarNode(
                key = "loaded-hold-$index",
                value = node.logFrontierAtReference +
                    node.trajectory * PRE_SESSION_TARGET_OFFSET -
                    requireNotNull(node.slope) * x,
                weight = node.posteriorWeight,
            )
        }
    }

    fun repeatedContractionLogFrontier(
        fit: NonDynamicCapabilityFit,
        observedCycles: Int,
    ): List<WeightedScalarNode> {
        require(fit.family == MetricFamily.REPEATED_CONTRACTION)
        require(observedCycles > 0)
        val reference = requireNotNull(fit.referenceCoordinate)
        val x = ln(observedCycles.toDouble() / reference)
        return fit.posteriorNodes.mapIndexed { index, node ->
            WeightedScalarNode(
                key = "repeated-contraction-$index",
                value = node.logFrontierAtReference +
                    node.trajectory * PRE_SESSION_TARGET_OFFSET -
                    requireNotNull(node.slope) * x,
                weight = node.posteriorWeight,
            )
        }
    }

    fun durationOnlyLogFrontier(
        fit: NonDynamicCapabilityFit,
    ): List<WeightedScalarNode> {
        require(fit.family == MetricFamily.DURATION_ONLY)
        return fit.posteriorNodes.mapIndexed { index, node ->
            WeightedScalarNode(
                key = "duration-only-$index",
                value = node.logFrontierAtReference + node.trajectory * PRE_SESSION_TARGET_OFFSET,
                weight = node.posteriorWeight,
            )
        }
    }
}
