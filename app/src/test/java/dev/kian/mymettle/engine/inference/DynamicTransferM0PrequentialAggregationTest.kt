package dev.kian.mymettle.engine.inference

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicTransferM0PrequentialAggregationTest {
    @Test
    fun `exact edge aggregation reports paired harm calibration and availability deterministically`() {
        val first = session(
            id = "session-a",
            at = BASE.plusSeconds(100),
            comparisons = listOf(
                comparison(
                    id = "a-1",
                    at = BASE.plusSeconds(101),
                    n0 = predictive(negativeLogScore = 1.0, crps = 0.20, wis = 0.25, width = 0.40, mae = 2.0, pit = 0.50, coverage = true),
                    m0 = predictive(negativeLogScore = 1.5, crps = 0.30, wis = 0.40, width = 0.30, mae = 3.0, pit = 0.95, coverage = false),
                    sessionId = "session-a",
                ),
                comparison(
                    id = "a-2",
                    at = BASE.plusSeconds(102),
                    n0 = predictive(negativeLogScore = 1.2, crps = 0.25, wis = 0.30, width = 0.50, mae = 2.5, pit = 0.20, coverage = true),
                    m0 = predictive(negativeLogScore = 0.9, crps = 0.18, wis = 0.22, width = 0.45, mae = 2.0, pit = 0.40, coverage = true),
                    sessionId = "session-a",
                ),
            ),
        )
        val second = session(
            id = "session-b",
            at = BASE.plusSeconds(200),
            comparisons = listOf(
                comparison(
                    id = "b-1",
                    at = BASE.plusSeconds(201),
                    n0 = predictive(negativeLogScore = 1.1, crps = 0.22, wis = 0.28, width = 0.42, mae = 2.2, pit = 0.70, coverage = true),
                    m0 = predictive(negativeLogScore = 1.2, crps = 0.24, wis = 0.30, width = 0.44, mae = 2.3, pit = 0.70, coverage = true),
                    sessionId = "session-b",
                ),
                unavailableComparison(
                    id = "b-2",
                    at = BASE.plusSeconds(202),
                    sessionId = "session-b",
                    n0 = predictive(negativeLogScore = 1.3, crps = 0.30, wis = 0.34, width = 0.48, mae = 2.8, pit = 0.80, coverage = false),
                    m0Reason = DynamicTransferPredictiveUnavailableReason.OUTSIDE_M0_DESTINATION_REPETITION_DOMAIN,
                ),
            ),
        )

        val aggregate = DynamicTransferM0PrequentialAggregator.aggregate(listOf(second, first))
        val replay = DynamicTransferM0PrequentialAggregator.aggregate(listOf(first, second))

        assertEquals(aggregate, replay)
        assertEquals(NBio7FPrequentialAggregationV1.PROTOCOL_ID, aggregate.aggregateProtocolId)
        assertEquals(listOf("session-a", "session-b"), aggregate.targetSessionIds)
        assertEquals(4, aggregate.availability.heldOutObservationCount)
        assertEquals(4, aggregate.availability.n0AvailableObservationCount)
        assertEquals(3, aggregate.availability.m0AvailableObservationCount)
        assertEquals(3, aggregate.availability.comparableObservationCount)
        assertEquals(1.0, aggregate.availability.n0AvailabilityRate, 0.0)
        assertEquals(0.75, aggregate.availability.m0AvailabilityRate, 0.0)
        assertEquals(0.75, aggregate.availability.comparableObservationRate, 0.0)
        assertEquals(1, aggregate.availability.fullyComparableSessionCount)
        assertEquals(1, aggregate.availability.partiallyComparableSessionCount)
        assertEquals(0, aggregate.availability.noComparableSessionCount)
        assertEquals(1, aggregate.availability.m0OutsideDestinationRepetitionDomainCount)
        assertEquals(4, aggregate.n0Aggregate?.count)
        assertEquals(3, aggregate.m0Aggregate?.count)

        val n0Pit = assertNotNull(aggregate.n0PitReliability)
        assertEquals(1, n0Pit.lowThirdCount)
        assertEquals(1, n0Pit.middleThirdCount)
        assertEquals(2, n0Pit.highThirdCount)
        val m0Pit = assertNotNull(aggregate.m0PitReliability)
        assertEquals(0, m0Pit.lowThirdCount)
        assertEquals(1, m0Pit.middleThirdCount)
        assertEquals(2, m0Pit.highThirdCount)

        val negative = assertNotNull(aggregate.negativeTransferDiagnostics)
        val logScore = negative.negativeLogScore.observationLevel
        assertEquals(3, logScore.count)
        assertEquals(0.3, logScore.cumulativeDelta, 1e-12)
        assertEquals(0.1, logScore.meanDelta, 1e-12)
        assertEquals(0.1, logScore.medianDelta, 1e-12)
        assertEquals(0.46, logScore.upperTailP95Delta, 1e-12)
        assertEquals(0.5, logScore.maximumDelta, 1e-12)
        assertEquals(2, logScore.positiveCount)
        assertEquals(2.0 / 3.0, logScore.positiveFraction, 1e-12)
        assertEquals(2, negative.negativeLogScore.sessionMeanLevel.count)
        assertEquals(2, negative.negativeLogScore.sessionMeanLevel.positiveCount)
        assertEquals(1, negative.catastrophicOverconfidenceObservationCount)
        assertEquals(1.0 / 3.0, negative.catastrophicOverconfidenceObservationFraction, 1e-12)
        assertEquals(1, negative.catastrophicOverconfidenceSessionCount)
        assertEquals(0.5, negative.catastrophicOverconfidenceSessionFraction, 1e-12)
        assertTrue(negative.intervalLogWidth.observationLevel.count == 3)
    }

    @Test
    fun `aggregation fails closed on mixed edge policy duplicate session or duplicate observation`() {
        val base = session(
            id = "session-a",
            at = BASE.plusSeconds(100),
            comparisons = listOf(
                comparison(
                    id = "same-observation",
                    at = BASE.plusSeconds(101),
                    sessionId = "session-a",
                    n0 = predictive(1.0, 0.2, 0.2, 0.4, 2.0, 0.5, true),
                    m0 = predictive(0.9, 0.18, 0.18, 0.4, 1.8, 0.5, true),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0PrequentialAggregator.aggregate(listOf(base, base))
        }

        val second = session(
            id = "session-b",
            at = BASE.plusSeconds(200),
            comparisons = listOf(
                comparison(
                    id = "second-observation",
                    at = BASE.plusSeconds(201),
                    sessionId = "session-b",
                    n0 = predictive(1.0, 0.2, 0.2, 0.4, 2.0, 0.5, true),
                    m0 = predictive(0.9, 0.18, 0.18, 0.4, 1.8, 0.5, true),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0PrequentialAggregator.aggregate(
                listOf(base, second.copy(edgeKey = EDGE.copy(relationshipFingerprint = "sha256_other"))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0PrequentialAggregator.aggregate(
                listOf(base, second.copy(sourceSelectionPolicyIdentity = "other-policy")),
            )
        }

        val duplicateObservation = second.copy(
            comparisons = listOf(
                comparison(
                    id = "same-observation",
                    at = BASE.plusSeconds(201),
                    sessionId = "session-b",
                    n0 = predictive(1.0, 0.2, 0.2, 0.4, 2.0, 0.5, true),
                    m0 = predictive(0.9, 0.18, 0.18, 0.4, 1.8, 0.5, true),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            DynamicTransferM0PrequentialAggregator.aggregate(listOf(base, duplicateObservation))
        }
    }

    @Test
    fun `n0 remains aggregatable when transfer is unavailable and refusal reasons stay explicit`() {
        val score = session(
            id = "session-only-n0",
            at = BASE.plusSeconds(300),
            comparisons = listOf(
                unavailableComparison(
                    id = "outside",
                    at = BASE.plusSeconds(301),
                    sessionId = "session-only-n0",
                    n0 = predictive(1.0, 0.2, 0.2, 0.4, 2.0, 0.2, true),
                    m0Reason = DynamicTransferPredictiveUnavailableReason.OUTSIDE_M0_DESTINATION_REPETITION_DOMAIN,
                ),
                unavailableComparison(
                    id = "numerical",
                    at = BASE.plusSeconds(302),
                    sessionId = "session-only-n0",
                    n0 = predictive(1.1, 0.22, 0.24, 0.45, 2.2, 0.8, false),
                    m0Reason = DynamicTransferPredictiveUnavailableReason.NUMERICAL_FAILURE,
                ),
            ),
        )

        val aggregate = DynamicTransferM0PrequentialAggregator.aggregate(listOf(score))

        assertEquals(2, aggregate.n0Aggregate?.count)
        assertNull(aggregate.m0Aggregate)
        assertNotNull(aggregate.n0PitReliability)
        assertNull(aggregate.m0PitReliability)
        assertNull(aggregate.negativeTransferDiagnostics)
        assertEquals(0, aggregate.availability.comparableObservationCount)
        assertEquals(1, aggregate.availability.noComparableSessionCount)
        assertEquals(1, aggregate.availability.m0OutsideDestinationRepetitionDomainCount)
        assertEquals(1, aggregate.availability.m0NumericalFailureCount)
        assertEquals(0, aggregate.availability.n0NumericalFailureCount)
    }

    private fun session(
        id: String,
        at: Instant,
        comparisons: List<DynamicTransferM0ObservationScoreComparison>,
    ): DynamicTransferM0PrequentialSessionScore {
        val deltas = comparisons.mapNotNull { it.deltaM0MinusN0 }
        return DynamicTransferM0PrequentialSessionScore(
            protocolId = NBio7FPrequentialScoringV1.PROTOCOL_ID,
            distributionProjectionId = NBio7FPrequentialScoringV1.DISTRIBUTION_PROJECTION_ID,
            targetSessionId = id,
            firstObservationTime = at,
            frozenAt = at.minusSeconds(1),
            edgeKey = EDGE,
            sourceSelectionPolicyIdentity = POLICY_ID,
            sourceSelectionMode = DynamicTransferM0SourceSelectionMode.SCORE_INDEPENDENTLY,
            comparisons = comparisons,
            n0Aggregate = null,
            m0Aggregate = null,
            negativeTransferDiagnostics = deltas.takeIf { it.isNotEmpty() }?.let {
                DynamicTransferM0NegativeTransferDiagnostics(
                    comparableObservationCount = it.size,
                    meanDeltaM0MinusN0 = meanDelta(it),
                )
            },
        )
    }

    private fun comparison(
        id: String,
        at: Instant,
        sessionId: String,
        n0: DynamicTransferContinuousPredictiveScore,
        m0: DynamicTransferContinuousPredictiveScore,
    ): DynamicTransferM0ObservationScoreComparison = DynamicTransferM0ObservationScoreComparison(
        observation = observation(id, sessionId, at),
        n0 = DynamicTransferPredictiveScoreResult.Available(n0),
        m0 = DynamicTransferPredictiveScoreResult.Available(m0),
        deltaM0MinusN0 = DynamicTransferM0ScoreDelta.between(n0, m0),
    )

    private fun unavailableComparison(
        id: String,
        at: Instant,
        sessionId: String,
        n0: DynamicTransferContinuousPredictiveScore,
        m0Reason: DynamicTransferPredictiveUnavailableReason,
    ): DynamicTransferM0ObservationScoreComparison = DynamicTransferM0ObservationScoreComparison(
        observation = observation(id, sessionId, at),
        n0 = DynamicTransferPredictiveScoreResult.Available(n0),
        m0 = DynamicTransferPredictiveScoreResult.Unavailable(m0Reason, "synthetic refusal"),
        deltaM0MinusN0 = null,
    )

    private fun observation(id: String, sessionId: String, at: Instant) = DynamicTransferM0HeldOutObservation(
        observationId = id,
        sessionId = sessionId,
        completedAt = at,
        repetitions = 8.0,
        resistanceKg = 80.0,
    )

    private fun predictive(
        negativeLogScore: Double,
        crps: Double,
        wis: Double,
        width: Double,
        mae: Double,
        pit: Double,
        coverage: Boolean,
    ) = DynamicTransferContinuousPredictiveScore(
        p05ResistanceKg = 70.0,
        p50ResistanceKg = 80.0,
        p95ResistanceKg = 90.0,
        pit = pit,
        logPredictiveDensity = -negativeLogScore,
        negativeLogScore = negativeLogScore,
        crpsLogResistance = crps,
        weightedIntervalScoreLogResistance = wis,
        coverage90 = coverage,
        intervalLogWidth = width,
        medianAbsoluteErrorKg = mae,
        signedLogResidual = 0.01,
    )

    private fun meanDelta(values: List<DynamicTransferM0ScoreDelta>) = DynamicTransferM0ScoreDelta(
        negativeLogScore = values.map { it.negativeLogScore }.average(),
        crpsLogResistance = values.map { it.crpsLogResistance }.average(),
        weightedIntervalScoreLogResistance = values.map { it.weightedIntervalScoreLogResistance }.average(),
        intervalLogWidth = values.map { it.intervalLogWidth }.average(),
        medianAbsoluteErrorKg = values.map { it.medianAbsoluteErrorKg }.average(),
    )

    companion object {
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private const val POLICY_ID = "aggregate-policy-v1"
        private val EDGE = DynamicTransferM0DirectedEdgeKey(
            relationshipId = "aggregate-edge",
            relationshipVersion = 1,
            relationshipPolicyIdentity = "aggregate-relationship-policy-v1",
            sourceExecutionProfileVersionId = "source-version",
            destinationExecutionProfileVersionId = "destination-version",
            relationshipFingerprint = "sha256_aggregate_fixture",
        )
    }
}
