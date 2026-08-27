package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.InferenceExecutionMode
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.QuantityDimension
import java.time.Instant
import kotlin.math.abs

/** One family-typed observation available to chronological model evaluation. */
data class RetrospectiveObservation(
    val observationId: String,
    val sessionId: String,
    val observedAt: Instant,
    val metricFamily: MetricFamily,
    val dimension: QuantityDimension,
    val observedCanonical: Double,
) {
    init {
        require(observationId.isNotBlank())
        require(sessionId.isNotBlank())
        require(observedCanonical.isFinite()) { "Evaluation observations must be finite." }
    }
}

data class RetrospectiveTrainingWindow internal constructor(
    val observations: List<RetrospectiveObservation>,
) {
    val observationIds: Set<String> = observations.mapTo(linkedSetOf()) { it.observationId }
    val sessionIds: Set<String> = observations.mapTo(linkedSetOf()) { it.sessionId }
    val evidenceThrough: Instant? = observations.maxOfOrNull { it.observedAt }
}

data class RetrospectivePrediction(
    val holdout: RetrospectiveObservation,
    val predictivePosterior: PosteriorSummary?,
    val trainingObservationIds: Set<String>,
    val trainingSessionIds: Set<String>,
)

enum class EvaluationMetricKind {
    MAE_NATIVE_DIMENSION,
    LOG_PREDICTIVE_DENSITY,
    CREDIBLE_INTERVAL_COVERAGE,
    CALIBRATION_ERROR,
    BLANK_APPROPRIATENESS,
}

data class EvaluationScore(
    val kind: EvaluationMetricKind,
    val value: Double?,
)

data class RetrospectiveEvaluationResult(
    val protocolVersion: String,
    val executionMode: InferenceExecutionMode,
    val metricFamily: MetricFamily,
    val dimension: QuantityDimension,
    val predictions: List<RetrospectivePrediction>,
) {
    init {
        require(protocolVersion.isNotBlank())
        require(predictions.all { it.holdout.metricFamily == metricFamily }) {
            "Evaluation results cannot mix metric families."
        }
        require(predictions.all { it.holdout.dimension == dimension }) {
            "Evaluation results cannot mix physical dimensions."
        }
    }

    fun meanAbsoluteError(): Double? {
        val scored = predictions.mapNotNull { prediction ->
            prediction.predictivePosterior?.estimateMedian?.let { median ->
                abs(median - prediction.holdout.observedCanonical)
            }
        }
        return scored.takeIf { it.isNotEmpty() }?.average()
    }

    fun credibleIntervalCoverage(): Double? {
        val scored = predictions.mapNotNull { prediction ->
            prediction.predictivePosterior?.let { posterior ->
                prediction.holdout.observedCanonical in posterior.credibleLower05..posterior.credibleUpper95
            }
        }
        return scored.takeIf { it.isNotEmpty() }?.let { values -> values.count { it }.toDouble() / values.size }
    }

    val blankPredictionCount: Int get() = predictions.count { it.predictivePosterior == null }

    /**
     * Metrics that require a full predictive density or explicit calibration target stay null until
     * the relevant candidate family supplies them; 7A does not manufacture a distribution shape.
     */
    fun scoreSkeleton(): List<EvaluationScore> = listOf(
        EvaluationScore(EvaluationMetricKind.MAE_NATIVE_DIMENSION, meanAbsoluteError()),
        EvaluationScore(EvaluationMetricKind.LOG_PREDICTIVE_DENSITY, null),
        EvaluationScore(EvaluationMetricKind.CREDIBLE_INTERVAL_COVERAGE, credibleIntervalCoverage()),
        EvaluationScore(EvaluationMetricKind.CALIBRATION_ERROR, null),
        EvaluationScore(EvaluationMetricKind.BLANK_APPROPRIATENESS, null),
    )
}

/**
 * Chronological held-out skeleton. The split holds out an entire session at once, so sets from the
 * target session cannot leak into one another's training window. Sessions are ordered by their
 * earliest observation and then stable session id; observations are stable-sorted likewise.
 */
class ChronologicalRetrospectiveEvaluator {
    fun evaluate(
        observations: List<RetrospectiveObservation>,
        executionMode: InferenceExecutionMode,
        metricFamily: MetricFamily,
        dimension: QuantityDimension,
        predict: (RetrospectiveTrainingWindow, RetrospectiveObservation) -> PosteriorSummary?,
    ): RetrospectiveEvaluationResult {
        require(observations.all { it.metricFamily == metricFamily }) { "Evaluate one metric family at a time." }
        require(observations.all { it.dimension == dimension }) { "Evaluate one canonical dimension at a time." }
        require(observations.map { it.observationId }.distinct().size == observations.size) {
            "Evaluation observation ids must be unique."
        }

        val sessions = observations
            .groupBy { it.sessionId }
            .entries
            .sortedWith(compareBy<Map.Entry<String, List<RetrospectiveObservation>>>({ entry ->
                entry.value.minOf { it.observedAt }
            }, { it.key }))

        val prior = mutableListOf<RetrospectiveObservation>()
        val predictions = buildList {
            sessions.forEach { (_, heldOutSession) ->
                val training = RetrospectiveTrainingWindow(
                    prior.sortedWith(compareBy<RetrospectiveObservation>({ it.observedAt }, { it.observationId })),
                )
                heldOutSession
                    .sortedWith(compareBy<RetrospectiveObservation>({ it.observedAt }, { it.observationId }))
                    .forEach { holdout ->
                        add(
                            RetrospectivePrediction(
                                holdout = holdout,
                                predictivePosterior = predict(training, holdout),
                                trainingObservationIds = training.observationIds,
                                trainingSessionIds = training.sessionIds,
                            ),
                        )
                    }
                prior += heldOutSession
            }
        }

        return RetrospectiveEvaluationResult(
            protocolVersion = PROTOCOL_VERSION,
            executionMode = executionMode,
            metricFamily = metricFamily,
            dimension = dimension,
            predictions = predictions,
        )
    }

    companion object {
        const val PROTOCOL_VERSION = "n-bio-7a-session-held-out-v1"
    }
}
