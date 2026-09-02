package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.performance.MetricFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NBio7DSensitivityTest {
    @Test
    fun `delta sensitivity 0025 005 010 changes demand and dose monotonically without retuning`() {
        val frontier = listOf(
            WeightedScalarNode("a", 0.00, 0.20),
            WeightedScalarNode("b", 0.04, 0.30),
            WeightedScalarNode("c", 0.08, 0.30),
            WeightedScalarNode("d", 0.14, 0.20),
        )
        val exposure = MuscleExposure("segment", "bilateral", 0.7, "recruitment:v1")
        val deltas = listOf(0.025, 0.05, 0.10)
        val results = deltas.map { delta ->
            val demand = NBio7DPosteriorMath.setDemandFromLogFrontier(
                family = MetricFamily.DYNAMIC_RESISTANCE,
                logFrontierNodes = frontier,
                logObservedPerformance = 0.0,
                inheritedSupport = SetDemandStructuralSupport.RESOLVED,
                config = NBio7DConfig(dynamicResistanceDeltaLog = delta),
            )
            demand to NBio7DPosteriorMath.effectiveDose(exposure, demand)
        }

        assertEquals(listOf(0.20, 0.50, 0.80), results.map { requireNotNull(it.first.probabilityAtOrWithinDelta) })
        assertTrue(results.zipWithNext().all { (left, right) ->
            requireNotNull(left.first.probabilityAtOrWithinDelta) <= requireNotNull(right.first.probabilityAtOrWithinDelta)
        })
        assertTrue(results.zipWithNext().all { (left, right) ->
            requireNotNull(left.second.summary).estimateMedian <= requireNotNull(right.second.summary).estimateMedian
        })
        assertTrue(results.all { (demand, dose) ->
            demand.frontierGapSummary == results.first().first.frontierGapSummary &&
                dose.exposure.conservativeExposure == 0.7
        })
    }

    @Test
    fun `tau sensitivity 2 4 8 preserves raw dose while larger tau approaches the linear raw scale`() {
        val raw = listOf(
            WeightedScalarNode("a", 1.0, 0.25),
            WeightedScalarNode("b", 2.0, 0.25),
            WeightedScalarNode("c", 3.0, 0.25),
            WeightedScalarNode("d", 4.0, 0.25),
        )
        val results = listOf(2.0, 4.0, 8.0).map { tau ->
            NBio7DPosteriorMath.sessionDose(
                resolvedStreamNodes = listOf(raw),
                contributingSetCount = 4,
                unresolvedSetCount = 0,
                config = NBio7DConfig(tau = tau),
            )
        }

        assertTrue(results.all { it.rawNodes == results.first().rawNodes })
        assertTrue(results.all { it.rawSummary == results.first().rawSummary })
        assertTrue(results.zipWithNext().all { (left, right) ->
            requireNotNull(left.concaveSummary).estimateMedian < requireNotNull(right.concaveSummary).estimateMedian
        })
        val rawMedian = requireNotNull(results.first().rawSummary).estimateMedian
        val distanceFromRaw = results.map { rawMedian - requireNotNull(it.concaveSummary).estimateMedian }
        assertTrue(distanceFromRaw.zipWithNext().all { (left, right) -> right < left })
        assertTrue(results.all { requireNotNull(it.concaveSummary).estimateMedian < rawMedian })
    }
}
