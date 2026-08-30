package dev.kian.mymettle.engine.inference

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicResistanceStage1VerdictTest {
    private val policy = DynamicStage1DiagnosticPolicy(
        semanticVersion = "stage1-verdict-test-v1",
        recentTrendSessionWindow = 4,
        minimumComparableTrendSessions = 3,
        minimumGlobalEvaluableEvents = 8,
        minimumTrendClassifiedEvents = 8,
        minimumUpwardEvents = 4,
        minimumStableEvents = 4,
        minimumTrendCorrelationPairs = 4,
        minimumSerialPairs = 3,
        supportUpwardMedianResidual = 0.02,
        supportUpwardPositiveRate = 0.75,
        supportHighPitRate = 0.75,
        supportTrendResidualCorrelation = 0.20,
        supportStableResidualContrast = 0.02,
        supportStableHighPitContrast = 0.25,
        supportPositivePositiveAdjacentRate = 0.40,
        supportSerialLag1ResidualCorrelation = 0.15,
    )

    @Test
    fun `coherent upward trend residual bias and serial persistence support temporal lag`() {
        val events = listOf(
            event(1, residual = -0.004, trend = 0.0, direction = DynamicRecentTrendDirection.STABLE, pit = 0.48),
            event(2, residual = 0.002, trend = 0.0, direction = DynamicRecentTrendDirection.STABLE, pit = 0.50),
            event(3, residual = 0.004, trend = 0.0, direction = DynamicRecentTrendDirection.STABLE, pit = 0.52),
            event(4, residual = 0.006, trend = 0.0, direction = DynamicRecentTrendDirection.STABLE, pit = 0.54),
            event(5, residual = 0.050, trend = 0.020, direction = DynamicRecentTrendDirection.UPWARD, pit = 0.84),
            event(6, residual = 0.060, trend = 0.025, direction = DynamicRecentTrendDirection.UPWARD, pit = 0.88),
            event(7, residual = 0.070, trend = 0.030, direction = DynamicRecentTrendDirection.UPWARD, pit = 0.92),
            event(8, residual = 0.080, trend = 0.035, direction = DynamicRecentTrendDirection.UPWARD, pit = 0.96),
        )
        val summary = DynamicStage1DiagnosticAnalyzer.aggregate(listOf(profile(events)), policy)

        assertEquals(DynamicStage1TemporalLagVerdict.TEMPORAL_LAG_SUPPORTED, summary.verdict)
        assertTrue(requireNotNull(summary.trendResidualCorrelation) > 0.20)
        assertTrue(requireNotNull(summary.serial.positivePositiveAdjacentRate) >= 0.40)
        assertTrue(requireNotNull(summary.byTrend[DynamicRecentTrendDirection.UPWARD]?.medianSignedLogResidual) > 0.02)
    }

    @Test
    fun `high PIT alone does not support temporal lag`() {
        val events = listOf(
            event(1, residual = 0.005, trend = 0.0, direction = DynamicRecentTrendDirection.STABLE, pit = 0.90),
            event(2, residual = 0.005, trend = 0.0, direction = DynamicRecentTrendDirection.STABLE, pit = 0.90),
            event(3, residual = 0.005, trend = 0.0, direction = DynamicRecentTrendDirection.STABLE, pit = 0.90),
            event(4, residual = 0.005, trend = 0.0, direction = DynamicRecentTrendDirection.STABLE, pit = 0.90),
            event(5, residual = 0.005, trend = 0.020, direction = DynamicRecentTrendDirection.UPWARD, pit = 0.90),
            event(6, residual = 0.005, trend = 0.025, direction = DynamicRecentTrendDirection.UPWARD, pit = 0.90),
            event(7, residual = 0.005, trend = 0.030, direction = DynamicRecentTrendDirection.UPWARD, pit = 0.90),
            event(8, residual = 0.005, trend = 0.035, direction = DynamicRecentTrendDirection.UPWARD, pit = 0.90),
        )
        val summary = DynamicStage1DiagnosticAnalyzer.aggregate(listOf(profile(events)), policy)

        assertEquals(DynamicStage1TemporalLagVerdict.TEMPORAL_LAG_NOT_SUPPORTED, summary.verdict)
        assertEquals(1.0, requireNotNull(summary.byTrend[DynamicRecentTrendDirection.UPWARD]?.highPitRate), 1e-12)
        assertTrue(requireNotNull(summary.byTrend[DynamicRecentTrendDirection.UPWARD]?.medianSignedLogResidual) < policy.supportUpwardMedianResidual)
    }

    @Test
    fun `CRPS is invariant to a common rescaling of deterministic weights`() {
        val first = DeterministicWeightedCrps.distribution(
            listOf(WeightedPredictivePoint(-0.2, 0.25), WeightedPredictivePoint(0.3, 0.75)),
        )
        val second = DeterministicWeightedCrps.distribution(
            listOf(WeightedPredictivePoint(-0.2, 2.5), WeightedPredictivePoint(0.3, 7.5)),
        )
        assertEquals(first.score(0.1), second.score(0.1), 1e-12)
    }

    private fun event(
        ordinal: Int,
        residual: Double,
        trend: Double,
        direction: DynamicRecentTrendDirection,
        pit: Double,
    ): DynamicStage1EventDiagnostic = DynamicStage1EventDiagnostic(
        sessionOrdinal = ordinal,
        repetitions = 8,
        observedResistanceKg = 100.0,
        priorIndependentSessionCount = ordinal + 2,
        priorRepMin = 6,
        priorRepMax = 12,
        repDomainPosition = DynamicRepDomainPosition.INSIDE,
        predictiveP05Kg = 80.0,
        predictiveP50Kg = 95.0,
        predictiveP95Kg = 110.0,
        predictiveLogWidth = ln(110.0 / 80.0),
        pit = pit,
        logPredictiveDensity = -1.0,
        crpsLogResistance = 0.05,
        frontierP05Kg = 90.0,
        frontierP50Kg = 100.0,
        frontierP95Kg = 115.0,
        signedLogResidual = residual,
        recentTrendLogPerSession = trend,
        recentTrendDirection = direction,
        recentTrendComparableSessions = 4,
        previousSessionMedianSignedLogResiduals = emptyList(),
        priorPositiveResidualSessionStreak = 0,
        coveredByPredictiveInterval = true,
        catastrophicFrontierContradiction = false,
    )

    private fun profile(events: List<DynamicStage1EventDiagnostic>): DynamicStage1ProfileDiagnostics =
        DynamicStage1ProfileDiagnostics(
            executionProfileVersionId = "test-profile-version",
            side = "unknown",
            events = events,
            summary = DynamicStage1DiagnosticSummary(
                policyId = "unused-test-placeholder",
                evaluableEventCount = 0,
                meanSignedLogResidual = null,
                medianSignedLogResidual = null,
                positiveResidualProportion = null,
                meanPredictiveLogWidth = null,
                meanCrpsLogResistance = null,
                trendClassifiedEventCount = 0,
                trendResidualCorrelation = null,
                byTrend = DynamicRecentTrendDirection.entries.associateWith {
                    DynamicStage1TrendGroupSummary(
                        count = 0,
                        meanSignedLogResidual = null,
                        medianSignedLogResidual = null,
                        positiveResidualProportion = null,
                        pitLowCount = 0,
                        pitMiddleCount = 0,
                        pitHighCount = 0,
                        highPitRate = null,
                        predictiveCoverage = null,
                        catastrophicContradictionRate = null,
                        meanPredictiveLogWidth = null,
                        meanCrpsLogResistance = null,
                    )
                },
                serial = DynamicStage1SerialSummary(
                    profileSessionCount = 0,
                    adjacentPairCount = 0,
                    sameSignAdjacentRate = null,
                    positivePositiveAdjacentRate = null,
                    lag1ResidualCorrelation = null,
                    longestPositiveRun = 0,
                ),
                verdict = DynamicStage1TemporalLagVerdict.INSUFFICIENT_DIAGNOSTIC_EVIDENCE,
                limitations = emptyList(),
            ),
        )
}
