package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.performance.MetricFamily
import kotlin.math.ln1p
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NBio7DPosteriorMathTest {
    @Test
    fun `near frontier observation has high demand-band probability`() {
        val demand = demand(listOf(0.01, 0.02, 0.04, 0.07), listOf(0.2, 0.3, 0.4, 0.1))
        assertEquals(0.9, requireNotNull(demand.probabilityAtOrWithinDelta), 1e-12)
        assertEquals(SetDemandStructuralSupport.RESOLVED, demand.structuralSupport)
    }

    @Test
    fun `clearly sub frontier observation has low demand-band probability`() {
        val demand = demand(listOf(0.20, 0.25, 0.30), listOf(0.2, 0.6, 0.2))
        assertEquals(0.0, requireNotNull(demand.probabilityAtOrWithinDelta), 1e-12)
        assertTrue(requireNotNull(demand.frontierGapSummary).p05 >= 0.20)
    }

    @Test
    fun `same observation against higher capability yields lower inferred demand`() {
        val lowerCapability = demand(listOf(0.01, 0.03, 0.07), listOf(0.3, 0.5, 0.2))
        val higherCapability = demand(listOf(0.08, 0.10, 0.14), listOf(0.3, 0.5, 0.2))
        assertTrue(requireNotNull(lowerCapability.probabilityAtOrWithinDelta) > requireNotNull(higherCapability.probabilityAtOrWithinDelta))
    }

    @Test
    fun `same capability with harder performed observation yields higher inferred demand`() {
        val frontier = nodes(listOf(4.60, 4.65, 4.70), listOf(0.25, 0.5, 0.25))
        val easier = NBio7DPosteriorMath.setDemandFromLogFrontier(
            MetricFamily.DYNAMIC_RESISTANCE,
            frontier,
            4.45,
            SetDemandStructuralSupport.RESOLVED,
        )
        val harder = NBio7DPosteriorMath.setDemandFromLogFrontier(
            MetricFamily.DYNAMIC_RESISTANCE,
            frontier,
            4.62,
            SetDemandStructuralSupport.RESOLVED,
        )
        assertTrue(requireNotNull(harder.probabilityAtOrWithinDelta) > requireNotNull(easier.probabilityAtOrWithinDelta))
        assertTrue(requireNotNull(harder.frontierGapSummary).p50 < requireNotNull(easier.frontierGapSummary).p50)
    }

    @Test
    fun `broad and prior dominated capability remain typed after demand transform`() {
        val broad = demand(listOf(0.01, 0.3), listOf(0.5, 0.5), SetDemandStructuralSupport.BROAD)
        val prior = demand(listOf(0.01, 0.3), listOf(0.5, 0.5), SetDemandStructuralSupport.PRIOR_DOMINATED)
        assertEquals(SetDemandStructuralSupport.BROAD, broad.structuralSupport)
        assertEquals(SetDemandStructuralSupport.PRIOR_DOMINATED, prior.structuralSupport)
        assertTrue(requireNotNull(broad.frontierGapSummary).posteriorVariance > 0.0)
    }

    @Test
    fun `missing pre session capability remains unsupported rather than fake demand`() {
        val demand = NBio7DPosteriorMath.unsupportedDemand(MetricFamily.DYNAMIC_RESISTANCE)
        assertEquals(SetDemandStructuralSupport.UNSUPPORTED, demand.structuralSupport)
        assertNull(demand.frontierGapSummary)
        assertNull(demand.probabilityAtOrWithinDelta)
        assertTrue(demand.frontierGapNodes.isEmpty())
    }

    @Test
    fun `strong negative frontier gap mass fails closed with typed contradiction`() {
        val demand = demand(listOf(-0.20, -0.10, -0.01), listOf(0.2, 0.3, 0.5))
        assertEquals(SetDemandStructuralSupport.FRONTIER_CONTRADICTION, demand.structuralSupport)
        assertEquals(1.0, requireNotNull(demand.contradictionProbability), 1e-12)
        assertTrue(requireNotNull(demand.frontierGapSummary).p50 < 0.0)
    }

    @Test
    fun `small negative gap mass is retained diagnostically without clamping`() {
        val demand = demand(listOf(-0.01, 0.02, 0.08), listOf(0.1, 0.6, 0.3))
        assertEquals(SetDemandStructuralSupport.RESOLVED, demand.structuralSupport)
        assertEquals(0.1, requireNotNull(demand.contradictionProbability), 1e-12)
        assertTrue(demand.frontierGapNodes.any { it.value < 0.0 })
    }

    @Test
    fun `7C families retain PD001 empirical accuracy pending status`() {
        listOf(MetricFamily.LOADED_HOLD, MetricFamily.DURATION_ONLY, MetricFamily.REPEATED_CONTRACTION).forEach { family ->
            val d = NBio7DPosteriorMath.setDemandFromLogFrontier(
                family,
                nodes(listOf(1.0, 1.1), listOf(0.5, 0.5)),
                1.0,
                SetDemandStructuralSupport.RESOLVED,
            )
            assertEquals(SetDemandEmpiricalStatus.EMPIRICAL_ACCURACY_PENDING, d.empiricalStatus)
            assertEquals("EMPIRICAL_CALIBRATION_PENDING", NBio7DModelIdentity.EMPIRICAL_STATUS)
        }
    }

    @Test
    fun `delta values are family scoped even when v1 defaults are equal`() {
        val config = NBio7DConfig(
            dynamicResistanceDeltaLog = 0.01,
            loadedHoldDeltaLog = 0.02,
            repeatedContractionDeltaLog = 0.03,
            durationOnlyDeltaLog = 0.04,
        )
        assertEquals(0.01, config.deltaFor(MetricFamily.DYNAMIC_RESISTANCE))
        assertEquals(0.02, config.deltaFor(MetricFamily.LOADED_HOLD))
        assertEquals(0.03, config.deltaFor(MetricFamily.REPEATED_CONTRACTION))
        assertEquals(0.04, config.deltaFor(MetricFamily.DURATION_ONLY))
    }

    @Test
    fun `exposure is exact historical independent recruitment weight and is not normalised`() {
        val exposures = listOf(1.0, 0.7, 0.4).mapIndexed { index, weight ->
            MuscleExposure("m$index", "bilateral", weight, "recruitment-v3")
        }
        assertEquals(listOf(1.0, 0.7, 0.4), exposures.map { it.conservativeExposure })
        assertEquals(2.1, exposures.sumOf { it.conservativeExposure }, 1e-12)
    }

    @Test
    fun `effective dose transforms full demand nodes rather than median`() {
        val dose = NBio7DPosteriorMath.effectiveDose(
            exposure(0.7),
            demand(listOf(0.01, 0.08), listOf(0.4, 0.6)),
        )
        assertEquals(listOf(0.7, 0.0), dose.nodes.map { it.value })
        assertEquals(0.1176, requireNotNull(dose.summary).posteriorVariance, 1e-12)
        assertEquals(0.0, requireNotNull(dose.summary).p50, 1e-12)
        assertTrue(requireNotNull(dose.summary).posteriorVariance > 0.0)
    }

    @Test
    fun `larger exposure cannot reduce effective dose at fixed demand`() {
        val demand = demand(listOf(0.01, 0.08), listOf(0.7, 0.3))
        val small = NBio7DPosteriorMath.effectiveDose(exposure(0.4), demand)
        val large = NBio7DPosteriorMath.effectiveDose(exposure(1.0), demand)
        assertTrue(expected(large.nodes) >= expected(small.nodes))
        assertTrue(requireNotNull(large.summary).p50 >= requireNotNull(small.summary).p50)
    }

    @Test
    fun `higher task demand cannot reduce effective dose at fixed exposure`() {
        val low = NBio7DPosteriorMath.effectiveDose(
            exposure(1.0),
            demand(listOf(0.02, 0.2), listOf(0.2, 0.8)),
        )
        val high = NBio7DPosteriorMath.effectiveDose(
            exposure(1.0),
            demand(listOf(0.02, 0.2), listOf(0.8, 0.2)),
        )
        assertTrue(expected(high.nodes) >= expected(low.nodes))
    }

    @Test
    fun `unresolved or contradictory demand never becomes exposure zero or fixed half dose`() {
        val unsupported = NBio7DPosteriorMath.effectiveDose(
            exposure(1.0),
            NBio7DPosteriorMath.unsupportedDemand(MetricFamily.DYNAMIC_RESISTANCE),
        )
        val contradiction = NBio7DPosteriorMath.effectiveDose(
            exposure(1.0),
            demand(listOf(-0.2, -0.1), listOf(0.5, 0.5)),
        )
        assertNull(unsupported.summary)
        assertTrue(unsupported.nodes.isEmpty())
        assertNull(contradiction.summary)
        assertTrue(contradiction.nodes.isEmpty())
    }

    @Test
    fun `shared capability nodes preserve within session dependence exactly`() {
        val d1 = NBio7DPosteriorMath.effectiveDose(exposure(1.0), demand(listOf(0.01, 0.20), listOf(0.5, 0.5)))
        val d2 = NBio7DPosteriorMath.effectiveDose(exposure(1.0), demand(listOf(0.02, 0.30), listOf(0.5, 0.5)))
        val shared = NBio7DPosteriorMath.aggregateSharedStream(listOf(d1, d2))
        assertEquals(listOf(2.0, 0.0), shared.map { it.value })
        assertEquals(1.0, NBio7DPosteriorMath.summary(shared).posteriorVariance, 1e-12)
    }

    @Test
    fun `shared stream aggregation is set order invariant`() {
        val d1 = NBio7DPosteriorMath.effectiveDose(exposure(0.7), demand(listOf(0.01, 0.2), listOf(0.5, 0.5)))
        val d2 = NBio7DPosteriorMath.effectiveDose(exposure(0.4), demand(listOf(0.02, 0.3), listOf(0.5, 0.5)))
        val forward = NBio7DPosteriorMath.aggregateSharedStream(listOf(d1, d2))
        val reverse = NBio7DPosteriorMath.aggregateSharedStream(listOf(d2, d1))
        assertEquals(forward.map { it.value }, reverse.map { it.value })
        assertEquals(forward.map { it.weight }, reverse.map { it.weight })
    }

    @Test
    fun `independent profile streams are convolved after within stream dependence is resolved`() {
        val a = listOf(WeightedScalarNode("a0", 0.0, 0.5), WeightedScalarNode("a1", 2.0, 0.5))
        val b = listOf(WeightedScalarNode("b0", 0.0, 0.5), WeightedScalarNode("b1", 1.0, 0.5))
        val combined = NBio7DPosteriorMath.convolveIndependentStreams(listOf(a, b))
        assertEquals(setOf(0.0, 1.0, 2.0, 3.0), combined.map { it.value }.toSet())
        assertEquals(1.0, combined.sumOf { it.weight }, 1e-12)
    }

    @Test
    fun `session raw sum and concave transform are exact over posterior distribution`() {
        val rawStream = listOf(
            WeightedScalarNode("x0", 0.0, 0.25),
            WeightedScalarNode("x1", 2.0, 0.50),
            WeightedScalarNode("x2", 4.0, 0.25),
        )
        val result = NBio7DPosteriorMath.sessionDose(listOf(rawStream), 2, 0, NBio7DConfig(tau = 4.0))
        assertEquals(SessionDoseResolution.FULLY_RESOLVED, result.resolution)
        assertEquals(2.0, requireNotNull(result.rawSummary).p50, 1e-12)
        assertEquals(4.0 * ln1p(2.0 / 4.0), requireNotNull(result.concaveSummary).p50, 1e-12)
        assertFalse(result.crossStreamIndependenceApproximation)
    }

    @Test
    fun `concave transform is nonnegative monotone and diminishing`() {
        fun transformed(value: Double): Double = requireNotNull(
            NBio7DPosteriorMath.sessionDose(
                listOf(listOf(WeightedScalarNode("x", value, 1.0))),
                1,
                0,
                NBio7DConfig(tau = 4.0),
            ).concaveSummary,
        ).p50
        assertEquals(0.0, transformed(0.0), 1e-12)
        assertTrue(transformed(4.0) > transformed(2.0))
        assertTrue(transformed(2.0) - transformed(1.0) > transformed(6.0) - transformed(5.0))
    }

    @Test
    fun `larger tau approaches linear raw dose over fixed range`() {
        fun gap(tau: Double): Double {
            val result = NBio7DPosteriorMath.sessionDose(
                listOf(listOf(WeightedScalarNode("x", 2.0, 1.0))),
                1,
                0,
                NBio7DConfig(tau = tau),
            )
            return 2.0 - requireNotNull(result.concaveSummary).p50
        }
        assertTrue(gap(8.0) < gap(2.0))
        assertTrue(gap(1000.0) < gap(8.0))
    }

    @Test
    fun `partial session exposes resolved subtotal without zero filling unknown sets`() {
        val result = NBio7DPosteriorMath.sessionDose(
            listOf(listOf(WeightedScalarNode("x", 1.2, 1.0))),
            contributingSetCount = 3,
            unresolvedSetCount = 2,
        )
        assertEquals(SessionDoseResolution.PARTIALLY_RESOLVED, result.resolution)
        assertEquals(2, result.unresolvedSetCount)
        assertEquals(1.2, requireNotNull(result.rawSummary).p50, 1e-12)
    }

    @Test
    fun `fully unresolved session has no fake zero posterior`() {
        val result = NBio7DPosteriorMath.sessionDose(emptyList(), 2, 2)
        assertEquals(SessionDoseResolution.UNRESOLVED, result.resolution)
        assertNull(result.rawSummary)
        assertNull(result.concaveSummary)
        assertTrue(result.rawNodes.isEmpty())
    }

    @Test
    fun `cross stream combination reports approximation boundary`() {
        val result = NBio7DPosteriorMath.sessionDose(
            listOf(
                listOf(WeightedScalarNode("a", 1.0, 1.0)),
                listOf(WeightedScalarNode("b", 2.0, 1.0)),
            ),
            2,
            0,
        )
        assertTrue(result.crossStreamIndependenceApproximation)
        assertEquals(3.0, requireNotNull(result.rawSummary).p50, 1e-12)
    }

    private fun demand(
        gaps: List<Double>,
        weights: List<Double>,
        support: SetDemandStructuralSupport = SetDemandStructuralSupport.RESOLVED,
        family: MetricFamily = MetricFamily.DYNAMIC_RESISTANCE,
    ) = NBio7DPosteriorMath.setDemandFromLogFrontier(
        family,
        nodes(gaps, weights),
        0.0,
        support,
    )

    private fun nodes(values: List<Double>, weights: List<Double>): List<WeightedScalarNode> {
        require(values.size == weights.size)
        return values.mapIndexed { index, value -> WeightedScalarNode("n$index", value, weights[index]) }
    }

    private fun exposure(weight: Double) = MuscleExposure("segment", "bilateral", weight, "recruitment-v1")

    private fun expected(nodes: List<WeightedScalarNode>): Double = nodes.sumOf { it.value * it.weight }
}
