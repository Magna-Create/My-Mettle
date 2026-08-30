package dev.kian.mymettle.engine.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicResistanceStage1DiagnosticsTest {
    @Test
    fun `weighted CRPS matches simple deterministic distributions`() {
        val point = DeterministicWeightedCrps.distribution(listOf(WeightedPredictivePoint(0.0, 1.0)))
        assertEquals(0.0, point.score(0.0), 1e-12)
        assertEquals(1.0, point.score(1.0), 1e-12)

        val twoPoint = DeterministicWeightedCrps.distribution(
            listOf(WeightedPredictivePoint(0.0, 0.5), WeightedPredictivePoint(2.0, 0.5)),
        )
        assertEquals(0.5, twoPoint.score(1.0), 1e-12)
        assertEquals(twoPoint.score(0.25), twoPoint.score(1.75), 1e-12)
    }

    @Test
    fun `trend diagnostic is directional and sparse history remains insufficient`() {
        val policy = DynamicStage1DiagnosticPolicy()
        assertTrue(DynamicStage1DiagnosticAnalyzer.theilSen(listOf(0.0, 0.03, 0.06, 0.09)) > 0.0)
        assertEquals(
            DynamicRecentTrendDirection.UPWARD,
            DynamicStage1DiagnosticAnalyzer.classifyTrend(0.03, 4, policy),
        )
        assertEquals(
            DynamicRecentTrendDirection.DOWNWARD,
            DynamicStage1DiagnosticAnalyzer.classifyTrend(-0.03, 4, policy),
        )
        assertEquals(
            DynamicRecentTrendDirection.STABLE,
            DynamicStage1DiagnosticAnalyzer.classifyTrend(0.005, 4, policy),
        )
        assertEquals(
            DynamicRecentTrendDirection.INSUFFICIENT,
            DynamicStage1DiagnosticAnalyzer.classifyTrend(0.04, 2, policy),
        )
    }
}
