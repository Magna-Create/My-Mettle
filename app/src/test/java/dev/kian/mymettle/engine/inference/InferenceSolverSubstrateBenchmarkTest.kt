package dev.kian.mymettle.engine.inference

import kotlin.test.Test
import kotlin.test.assertTrue

class InferenceSolverSubstrateBenchmarkTest {
    @Test
    fun `shared dynamic benchmark preserves dense replay and reports finite solver fidelity`() {
        val result = InferenceSolverSubstrateBenchmark.run()

        assertTrue(result.denseIncrementalReplayEquivalent)
        assertTrue(result.sparseRetainedNodeCount < result.gridNodeCount)
        assertTrue(result.sparseLevelQuantileMaxAbsoluteError <= 0.15)
        assertTrue(result.sparseDriftQuantileMaxAbsoluteError <= 0.06)
        assertTrue(result.sparseMeanMaxAbsoluteError <= 0.08)
        assertTrue(result.sparseCovarianceMaxAbsoluteError <= 0.03)
        assertTrue(result.sigmaPointMeanMaxAbsoluteError <= 0.20)
        assertTrue(result.sigmaPointCovarianceMaxAbsoluteError <= 0.08)
        assertTrue(result.lowRankScreens.size == 4)
        assertTrue(result.lowRankScreens.all { it.compressionRatio > 0.0 && it.l1ProbabilityError >= 0.0 })
        assertTrue(result.denseRuntime.medianNanos > 0)
        assertTrue(result.sparseRuntime.medianNanos > 0)
        assertTrue(result.sigmaPointRuntime.medianNanos > 0)
    }
}
