package dev.kian.mymettle.engine.inference

import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Cross-session aggregation protocol for the frozen N0-vs-M0 prequential scorer.
 *
 * This layer is descriptive only. It does not select sources, change model state, define promotion
 * thresholds, or fit real history. Positive paired deltas mean M0 exceeded N0; for lower-is-better
 * scores that is harm. Interval-width deltas are reported as sharpness change only.
 */
object NBio7FPrequentialAggregationV1 {
    const val PROTOCOL_ID = "n-bio-7f-n0-m0-exact-edge-aggregate-v1"
    const val PIT_RELIABILITY_BINS = 3
    const val UPPER_TAIL_QUANTILE = 0.95
}

data class DynamicTransferPairedDeltaSummary(
    val count: Int,
    val cumulativeDelta: Double,
    val meanDelta: Double,
    val medianDelta: Double,
    val upperTailP95Delta: Double,
    val maximumDelta: Double,
    val positiveCount: Int,
    val positiveFraction: Double,
) {
    init {
        require(count > 0)
        require(
            listOf(
                cumulativeDelta,
                meanDelta,
                medianDelta,
                upperTailP95Delta,
                maximumDelta,
                positiveFraction,
            ).all { it.isFinite() },
        )
        require(positiveCount in 0..count)
        require(positiveFraction in 0.0..1.0)
    }
}

/** Observation-level and independent-session-level views of one paired delta metric. */
data class DynamicTransferMetricDeltaDiagnostics(
    val observationLevel: DynamicTransferPairedDeltaSummary,
    val sessionMeanLevel: DynamicTransferPairedDeltaSummary,
) {
    init {
        require(sessionMeanLevel.count <= observationLevel.count)
    }
}

/** Fixed descriptive three-bin PIT reliability summary. No minimum-count acceptance rule is implied. */
data class DynamicTransferPitReliabilitySummary(
    val count: Int,
    val lowThirdCount: Int,
    val middleThirdCount: Int,
    val highThirdCount: Int,
    val lowThirdRate: Double,
    val middleThirdRate: Double,
    val highThirdRate: Double,
    val meanPit: Double,
    val meanAbsoluteThreeBinDeviation: Double,
) {
    init {
        require(count > 0)
        require(lowThirdCount >= 0 && middleThirdCount >= 0 && highThirdCount >= 0)
        require(lowThirdCount + middleThirdCount + highThirdCount == count)
        require(listOf(lowThirdRate, middleThirdRate, highThirdRate, meanPit, meanAbsoluteThreeBinDeviation).all { it.isFinite() })
        require(lowThirdRate in 0.0..1.0 && middleThirdRate in 0.0..1.0 && highThirdRate in 0.0..1.0)
        require(meanPit in 0.0..1.0)
        require(meanAbsoluteThreeBinDeviation >= 0.0)
    }
}

data class DynamicTransferM0AvailabilityDiagnostics(
    val sessionCount: Int,
    val heldOutObservationCount: Int,
    val n0AvailableObservationCount: Int,
    val m0AvailableObservationCount: Int,
    val comparableObservationCount: Int,
    val n0AvailabilityRate: Double,
    val m0AvailabilityRate: Double,
    val comparableObservationRate: Double,
    val fullyComparableSessionCount: Int,
    val partiallyComparableSessionCount: Int,
    val noComparableSessionCount: Int,
    val n0NumericalFailureCount: Int,
    val m0NumericalFailureCount: Int,
    val m0OutsideDestinationRepetitionDomainCount: Int,
) {
    init {
        require(sessionCount > 0 && heldOutObservationCount > 0)
        require(n0AvailableObservationCount in 0..heldOutObservationCount)
        require(m0AvailableObservationCount in 0..heldOutObservationCount)
        require(comparableObservationCount in 0..minOf(n0AvailableObservationCount, m0AvailableObservationCount))
        require(n0AvailabilityRate in 0.0..1.0 && m0AvailabilityRate in 0.0..1.0 && comparableObservationRate in 0.0..1.0)
        require(fullyComparableSessionCount >= 0 && partiallyComparableSessionCount >= 0 && noComparableSessionCount >= 0)
        require(fullyComparableSessionCount + partiallyComparableSessionCount + noComparableSessionCount == sessionCount)
        require(n0NumericalFailureCount >= 0 && m0NumericalFailureCount >= 0)
        require(m0OutsideDestinationRepetitionDomainCount >= 0)
    }
}

data class DynamicTransferM0CrossSessionNegativeTransferDiagnostics(
    val comparableObservationCount: Int,
    val comparableSessionCount: Int,
    val negativeLogScore: DynamicTransferMetricDeltaDiagnostics,
    val crpsLogResistance: DynamicTransferMetricDeltaDiagnostics,
    val weightedIntervalScoreLogResistance: DynamicTransferMetricDeltaDiagnostics,
    val medianAbsoluteErrorKg: DynamicTransferMetricDeltaDiagnostics,
    val intervalLogWidth: DynamicTransferMetricDeltaDiagnostics,
    val catastrophicOverconfidenceObservationCount: Int,
    val catastrophicOverconfidenceObservationFraction: Double,
    val catastrophicOverconfidenceSessionCount: Int,
    val catastrophicOverconfidenceSessionFraction: Double,
) {
    init {
        require(comparableObservationCount > 0 && comparableSessionCount > 0)
        require(
            listOf(
                negativeLogScore,
                crpsLogResistance,
                weightedIntervalScoreLogResistance,
                medianAbsoluteErrorKg,
                intervalLogWidth,
            ).all {
                it.observationLevel.count == comparableObservationCount &&
                    it.sessionMeanLevel.count == comparableSessionCount
            },
        )
        require(catastrophicOverconfidenceObservationCount in 0..comparableObservationCount)
        require(catastrophicOverconfidenceObservationFraction in 0.0..1.0)
        require(catastrophicOverconfidenceSessionCount in 0..comparableSessionCount)
        require(catastrophicOverconfidenceSessionFraction in 0.0..1.0)
    }
}

data class DynamicTransferM0EdgePrequentialAggregate(
    val aggregateProtocolId: String,
    val scoringProtocolId: String,
    val distributionProjectionId: String,
    val edgeKey: DynamicTransferM0DirectedEdgeKey,
    val sourceSelectionPolicyIdentity: String,
    val sourceSelectionMode: DynamicTransferM0SourceSelectionMode,
    val targetSessionIds: List<String>,
    val firstHeldOutSessionTime: Instant,
    val lastHeldOutSessionTime: Instant,
    val n0Aggregate: DynamicTransferContinuousAggregate?,
    val m0Aggregate: DynamicTransferContinuousAggregate?,
    val n0PitReliability: DynamicTransferPitReliabilitySummary?,
    val m0PitReliability: DynamicTransferPitReliabilitySummary?,
    val availability: DynamicTransferM0AvailabilityDiagnostics,
    val negativeTransferDiagnostics: DynamicTransferM0CrossSessionNegativeTransferDiagnostics?,
) {
    init {
        require(aggregateProtocolId == NBio7FPrequentialAggregationV1.PROTOCOL_ID)
        require(scoringProtocolId == NBio7FPrequentialScoringV1.PROTOCOL_ID)
        require(distributionProjectionId == NBio7FPrequentialScoringV1.DISTRIBUTION_PROJECTION_ID)
        require(sourceSelectionPolicyIdentity.isNotBlank())
        require(targetSessionIds.isNotEmpty() && targetSessionIds.distinct().size == targetSessionIds.size)
        require(!lastHeldOutSessionTime.isBefore(firstHeldOutSessionTime))
        require(targetSessionIds.size == availability.sessionCount)
        require((negativeTransferDiagnostics?.comparableObservationCount ?: 0) == availability.comparableObservationCount)
    }
}

/** Pure exact-edge aggregation over already-frozen, already-scored destination sessions. */
object DynamicTransferM0PrequentialAggregator {
    fun aggregate(sessionScores: List<DynamicTransferM0PrequentialSessionScore>): DynamicTransferM0EdgePrequentialAggregate {
        require(sessionScores.isNotEmpty())
        require(sessionScores.map { it.targetSessionId }.distinct().size == sessionScores.size) {
            "Cross-session aggregation cannot count the same destination session twice."
        }
        val ordered = sessionScores.sortedWith(
            compareBy<DynamicTransferM0PrequentialSessionScore> { it.firstObservationTime }
                .thenBy { it.targetSessionId },
        )
        val first = ordered.first()
        require(ordered.all { it.protocolId == first.protocolId }) { "Scoring protocol identities cannot be mixed." }
        require(ordered.all { it.distributionProjectionId == first.distributionProjectionId }) {
            "Distribution-projection identities cannot be mixed."
        }
        require(ordered.all { it.edgeKey == first.edgeKey }) {
            "One aggregate may contain only one exact directed M0 edge identity."
        }
        require(ordered.all { it.sourceSelectionPolicyIdentity == first.sourceSelectionPolicyIdentity }) {
            "Source-selection policy identities cannot be mixed inside one exact-edge aggregate."
        }
        require(ordered.all { it.sourceSelectionMode == first.sourceSelectionMode }) {
            "Source-selection modes cannot be mixed inside one exact-edge aggregate."
        }
        ordered.forEach { session ->
            require(session.comparisons.all { it.observation.sessionId == session.targetSessionId }) {
                "Every comparison must belong to its declared held-out destination session."
            }
        }
        val comparisons = ordered.flatMap { it.comparisons }
        require(comparisons.map { it.observation.observationId }.distinct().size == comparisons.size) {
            "Cross-session aggregation cannot count the same held-out observation twice."
        }

        val n0Scores = comparisons.mapNotNull { (it.n0 as? DynamicTransferPredictiveScoreResult.Available)?.score }
        val m0Scores = comparisons.mapNotNull { (it.m0 as? DynamicTransferPredictiveScoreResult.Available)?.score }
        val comparableBySession = ordered.mapNotNull { session ->
            val deltas = session.comparisons.mapNotNull { it.deltaM0MinusN0 }
            deltas.takeIf { it.isNotEmpty() }?.let { session.targetSessionId to deltas }
        }
        val negative = if (comparableBySession.isEmpty()) null else {
            negativeDiagnostics(ordered, comparableBySession)
        }

        return DynamicTransferM0EdgePrequentialAggregate(
            aggregateProtocolId = NBio7FPrequentialAggregationV1.PROTOCOL_ID,
            scoringProtocolId = first.protocolId,
            distributionProjectionId = first.distributionProjectionId,
            edgeKey = first.edgeKey,
            sourceSelectionPolicyIdentity = first.sourceSelectionPolicyIdentity,
            sourceSelectionMode = first.sourceSelectionMode,
            targetSessionIds = ordered.map { it.targetSessionId },
            firstHeldOutSessionTime = ordered.first().firstObservationTime,
            lastHeldOutSessionTime = ordered.last().firstObservationTime,
            n0Aggregate = continuousAggregate(n0Scores),
            m0Aggregate = continuousAggregate(m0Scores),
            n0PitReliability = pitReliability(n0Scores),
            m0PitReliability = pitReliability(m0Scores),
            availability = availability(ordered, comparisons),
            negativeTransferDiagnostics = negative,
        )
    }

    private fun negativeDiagnostics(
        sessions: List<DynamicTransferM0PrequentialSessionScore>,
        comparableBySession: List<Pair<String, List<DynamicTransferM0ScoreDelta>>>,
    ): DynamicTransferM0CrossSessionNegativeTransferDiagnostics {
        val comparable = sessions.flatMap { it.comparisons }.filter { it.deltaM0MinusN0 != null }
        val catastrophic = comparable.filter(::catastrophicOverconfidence)
        val catastrophicSessionIds = catastrophic.map { it.observation.sessionId }.toSet()
        val comparableSessionCount = comparableBySession.size
        return DynamicTransferM0CrossSessionNegativeTransferDiagnostics(
            comparableObservationCount = comparable.size,
            comparableSessionCount = comparableSessionCount,
            negativeLogScore = metricDiagnostics(comparableBySession) { it.negativeLogScore },
            crpsLogResistance = metricDiagnostics(comparableBySession) { it.crpsLogResistance },
            weightedIntervalScoreLogResistance = metricDiagnostics(comparableBySession) { it.weightedIntervalScoreLogResistance },
            medianAbsoluteErrorKg = metricDiagnostics(comparableBySession) { it.medianAbsoluteErrorKg },
            intervalLogWidth = metricDiagnostics(comparableBySession) { it.intervalLogWidth },
            catastrophicOverconfidenceObservationCount = catastrophic.size,
            catastrophicOverconfidenceObservationFraction = catastrophic.size.toDouble() / comparable.size,
            catastrophicOverconfidenceSessionCount = catastrophicSessionIds.size,
            catastrophicOverconfidenceSessionFraction = catastrophicSessionIds.size.toDouble() / comparableSessionCount,
        )
    }

    /**
     * Catastrophic-overconfidence diagnostic anchored to the already-frozen 90% interval: transfer
     * loses champion coverage, is no wider, and has a worse exact log score. This is descriptive and
     * is not a promotion threshold.
     */
    private fun catastrophicOverconfidence(comparison: DynamicTransferM0ObservationScoreComparison): Boolean {
        val n0 = (comparison.n0 as? DynamicTransferPredictiveScoreResult.Available)?.score ?: return false
        val m0 = (comparison.m0 as? DynamicTransferPredictiveScoreResult.Available)?.score ?: return false
        val delta = comparison.deltaM0MinusN0 ?: return false
        return n0.coverage90 && !m0.coverage90 &&
            m0.intervalLogWidth <= n0.intervalLogWidth &&
            delta.negativeLogScore > 0.0
    }

    private fun metricDiagnostics(
        comparableBySession: List<Pair<String, List<DynamicTransferM0ScoreDelta>>>,
        select: (DynamicTransferM0ScoreDelta) -> Double,
    ): DynamicTransferMetricDeltaDiagnostics {
        val observations = comparableBySession.flatMap { (_, deltas) -> deltas.map(select) }
        val sessionMeans = comparableBySession.map { (_, deltas) -> deltas.map(select).average() }
        return DynamicTransferMetricDeltaDiagnostics(
            observationLevel = deltaSummary(observations),
            sessionMeanLevel = deltaSummary(sessionMeans),
        )
    }

    private fun availability(
        sessions: List<DynamicTransferM0PrequentialSessionScore>,
        comparisons: List<DynamicTransferM0ObservationScoreComparison>,
    ): DynamicTransferM0AvailabilityDiagnostics {
        val n0Available = comparisons.count { it.n0 is DynamicTransferPredictiveScoreResult.Available }
        val m0Available = comparisons.count { it.m0 is DynamicTransferPredictiveScoreResult.Available }
        val comparable = comparisons.count { it.deltaM0MinusN0 != null }
        var fullyComparable = 0
        var partiallyComparable = 0
        var noComparable = 0
        sessions.forEach { session ->
            val count = session.comparisons.count { it.deltaM0MinusN0 != null }
            when {
                count == 0 -> noComparable += 1
                count == session.comparisons.size -> fullyComparable += 1
                else -> partiallyComparable += 1
            }
        }
        return DynamicTransferM0AvailabilityDiagnostics(
            sessionCount = sessions.size,
            heldOutObservationCount = comparisons.size,
            n0AvailableObservationCount = n0Available,
            m0AvailableObservationCount = m0Available,
            comparableObservationCount = comparable,
            n0AvailabilityRate = n0Available.toDouble() / comparisons.size,
            m0AvailabilityRate = m0Available.toDouble() / comparisons.size,
            comparableObservationRate = comparable.toDouble() / comparisons.size,
            fullyComparableSessionCount = fullyComparable,
            partiallyComparableSessionCount = partiallyComparable,
            noComparableSessionCount = noComparable,
            n0NumericalFailureCount = comparisons.count {
                (it.n0 as? DynamicTransferPredictiveScoreResult.Unavailable)?.reason ==
                    DynamicTransferPredictiveUnavailableReason.NUMERICAL_FAILURE
            },
            m0NumericalFailureCount = comparisons.count {
                (it.m0 as? DynamicTransferPredictiveScoreResult.Unavailable)?.reason ==
                    DynamicTransferPredictiveUnavailableReason.NUMERICAL_FAILURE
            },
            m0OutsideDestinationRepetitionDomainCount = comparisons.count {
                (it.m0 as? DynamicTransferPredictiveScoreResult.Unavailable)?.reason ==
                    DynamicTransferPredictiveUnavailableReason.OUTSIDE_M0_DESTINATION_REPETITION_DOMAIN
            },
        )
    }

    private fun continuousAggregate(scores: List<DynamicTransferContinuousPredictiveScore>): DynamicTransferContinuousAggregate? =
        scores.takeIf { it.isNotEmpty() }?.let { values ->
            DynamicTransferContinuousAggregate(
                count = values.size,
                meanNegativeLogScore = values.map { it.negativeLogScore }.average(),
                meanCrpsLogResistance = values.map { it.crpsLogResistance }.average(),
                meanWeightedIntervalScoreLogResistance = values.map { it.weightedIntervalScoreLogResistance }.average(),
                coverage90 = values.count { it.coverage90 }.toDouble() / values.size,
                meanIntervalLogWidth = values.map { it.intervalLogWidth }.average(),
                meanMedianAbsoluteErrorKg = values.map { it.medianAbsoluteErrorKg }.average(),
                meanSignedLogResidual = values.map { it.signedLogResidual }.average(),
            )
        }

    private fun pitReliability(scores: List<DynamicTransferContinuousPredictiveScore>): DynamicTransferPitReliabilitySummary? {
        if (scores.isEmpty()) return null
        val pits = scores.map { it.pit }
        val low = pits.count { it < 1.0 / 3.0 }
        val middle = pits.count { it >= 1.0 / 3.0 && it < 2.0 / 3.0 }
        val high = pits.size - low - middle
        val lowRate = low.toDouble() / pits.size
        val middleRate = middle.toDouble() / pits.size
        val highRate = high.toDouble() / pits.size
        val expected = 1.0 / NBio7FPrequentialAggregationV1.PIT_RELIABILITY_BINS
        return DynamicTransferPitReliabilitySummary(
            count = pits.size,
            lowThirdCount = low,
            middleThirdCount = middle,
            highThirdCount = high,
            lowThirdRate = lowRate,
            middleThirdRate = middleRate,
            highThirdRate = highRate,
            meanPit = pits.average(),
            meanAbsoluteThreeBinDeviation =
                (abs(lowRate - expected) + abs(middleRate - expected) + abs(highRate - expected)) /
                    NBio7FPrequentialAggregationV1.PIT_RELIABILITY_BINS,
        )
    }

    private fun deltaSummary(values: List<Double>): DynamicTransferPairedDeltaSummary {
        require(values.isNotEmpty() && values.all { it.isFinite() })
        val ordered = values.sorted()
        val cumulative = values.sum()
        val positive = values.count { it > 0.0 }
        return DynamicTransferPairedDeltaSummary(
            count = values.size,
            cumulativeDelta = cumulative,
            meanDelta = cumulative / values.size,
            medianDelta = quantile(ordered, 0.50),
            upperTailP95Delta = quantile(ordered, NBio7FPrequentialAggregationV1.UPPER_TAIL_QUANTILE),
            maximumDelta = ordered.last(),
            positiveCount = positive,
            positiveFraction = positive.toDouble() / values.size,
        )
    }

    /** Deterministic linear interpolation on the ordered empirical sample. */
    private fun quantile(sorted: List<Double>, probability: Double): Double {
        require(sorted.isNotEmpty() && probability in 0.0..1.0)
        if (sorted.size == 1) return sorted.single()
        val position = (sorted.size - 1) * probability
        val lower = floor(position).toInt()
        val upper = ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        val fraction = position - lower
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower])
    }
}
