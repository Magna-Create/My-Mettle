package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitFailureReason
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV1Contract
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierConfig
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DynamicStochasticFrontierModelTest {
    @Test
    fun `frontier is monotonic and predictions remain positive`() {
        val fit = fit(generated(frontierKg = 80.0, slope = 0.22, sessions = 8, reps = listOf(5, 8, 12)))
        val model = DynamicStochasticFrontierModel()
        val five = model.predictFrontier(fit, 5.0).summary!!
        val eight = model.predictFrontier(fit, 8.0).summary!!
        val twelve = model.predictFrontier(fit, 12.0).summary!!
        assertTrue(five.p50 > eight.p50)
        assertTrue(eight.p50 > twelve.p50)
        assertTrue(listOf(five, eight, twelve).all { it.p05 > 0.0 && it.p95.isFinite() })
    }

    @Test
    fun `deterministic replay and input order are invariant`() {
        val evidence = generated(frontierKg = 75.0, slope = 0.18, sessions = 6, reps = listOf(5, 8, 12))
        val first = fit(evidence)
        val second = fit(evidence)
        val reversed = fit(evidence.reversed())
        assertEquals(first.frontierAtReference, second.frontierAtReference)
        assertEquals(first.slope, second.slope)
        assertEquals(first.frontierAtReference, reversed.frontierAtReference)
        assertEquals(first.slope, reversed.slope)
        assertEquals(first.observationSlack, reversed.observationSlack)
    }

    @Test
    fun `one observation is broad and does not personalise slope or nuisance scales`() {
        val fit = fit(listOf(set("one", "session_1", 8, 60.0, day = 1)))
        val posterior = fit.frontierAtReference.summary!!
        assertTrue(posterior.p95 / posterior.p05 > 1.20)
        assertEquals(DynamicParameterIdentification.PRIOR_DOMINATED, fit.slope.identification)
        assertEquals(DynamicParameterIdentification.FIXED_BY_CONFIG, fit.slackScale.identification)
        assertEquals(DynamicParameterIdentification.FIXED_BY_CONFIG, fit.noiseScale.identification)
    }

    @Test
    fun `one rep zone across sessions tightens local frontier while slope stays prior dominated`() {
        val one = fit(generated(frontierKg = 80.0, slope = 0.20, sessions = 1, reps = listOf(8)))
        val many = fit(generated(frontierKg = 80.0, slope = 0.20, sessions = 7, reps = listOf(8)))
        assertTrue(width(many.frontierAtReference.summary!!) < width(one.frontierAtReference.summary!!))
        assertEquals(DynamicParameterIdentification.PRIOR_DOMINATED, many.slope.identification)
    }

    @Test
    fun `multiple rep zones and sessions can learn a steeper slope`() {
        val fit = fit(generated(frontierKg = 80.0, slope = 0.30, sessions = 8, reps = listOf(4, 8, 16)))
        assertTrue(fit.slope.summary.p50 > 0.20)
        assertEquals(DynamicParameterIdentification.DATA_INFORMED, fit.slope.identification)
    }

    @Test
    fun `repeated higher demonstrations move frontier upward`() {
        val baseline = generated(frontierKg = 70.0, slope = 0.18, sessions = 6, reps = listOf(8))
        val higher = generated(
            frontierKg = 86.0,
            slope = 0.18,
            sessions = 6,
            reps = listOf(8),
            sessionOffset = 6,
        )
        val before = fit(baseline)
        val after = fit(baseline + higher)
        assertTrue(after.frontierAtReference.summary!!.p50 > before.frontierAtReference.summary!!.p50 * 1.08)
    }

    @Test
    fun `one adversarially poor set has bounded influence`() {
        val baseEvidence = generated(frontierKg = 80.0, slope = 0.18, sessions = 8, reps = listOf(8))
        val baseline = fit(baseEvidence)
        val poor = set("poor", "session_9", 8, 25.0, day = 9)
        val withPoor = fit(baseEvidence + poor)
        assertTrue(withPoor.frontierAtReference.summary!!.p50 > baseline.frontierAtReference.summary!!.p50 * 0.80)
    }

    @Test
    fun `recent-session policy allows current frontier to decline`() {
        val config = DynamicStochasticFrontierConfig(
            recentIndependentSessionWindow = 4,
            nuisanceLearningMinimumIndependentSessions = 8,
            nuisanceLearningMinimumObservations = 12,
            nuisanceDataInformedMinimumIndependentSessions = 10,
            nuisanceDataInformedMinimumObservations = 20,
        )
        val oldHigh = generated(frontierKg = 86.0, slope = 0.18, sessions = 4, reps = listOf(8))
        val newerLow = generated(
            frontierKg = 60.0,
            slope = 0.18,
            sessions = 4,
            reps = listOf(8),
            sessionOffset = 4,
        )
        val early = fit(oldHigh + newerLow, config, horizon = BASE.plusSeconds(4L * DAY_SECONDS - 1L))
        val current = fit(oldHigh + newerLow, config)
        assertTrue(current.frontierAtReference.summary!!.p50 < early.frontierAtReference.summary!!.p50 * 0.85)
    }

    @Test
    fun `sub-frontier observations infer larger non-negative slack`() {
        val evidence = buildList {
            repeat(8) { index ->
                add(set("high_$index", "session_$index", 8, 80.0, day = index))
                add(set("low_$index", "session_$index", 8, 58.0, day = index, ordinal = 1))
            }
        }
        val fit = fit(evidence)
        val high = fit.observationSlack.filter { it.observationId.contains("high") }
        val low = fit.observationSlack.filter { it.observationId.contains("low") }
        assertTrue(fit.observationSlack.all { it.summary.p05 >= 0.0 && it.massPoints.all { mass -> mass.slack >= 0.0 } })
        assertTrue(low.map { it.summary.p50 }.average() > high.map { it.summary.p50 }.average())
    }

    @Test
    fun `Student t ordinary noise is symmetric`() {
        val model = DynamicStochasticFrontierModel()
        assertEquals(model.noiseLogDensity(0.25), model.noiseLogDensity(-0.25), 1e-12)
        assertEquals(model.noiseLogDensity(1.0), model.noiseLogDensity(-1.0), 1e-12)
    }

    @Test
    fun `same-session duplication does not create longitudinal certainty`() {
        val one = listOf(set("one", "session_same", 8, 75.0, day = 1))
        val duplicated = List(5) { index -> set("dup_$index", "session_same", 8, 75.0, day = 1, ordinal = index) }
        val independent = List(5) { index -> set("ind_$index", "session_$index", 8, 75.0, day = index) }
        val oneFit = fit(one)
        val duplicateFit = fit(duplicated)
        val independentFit = fit(independent)
        assertEquals(oneFit.frontierAtReference.summary!!.p50, duplicateFit.frontierAtReference.summary!!.p50, 1e-12)
        assertEquals(oneFit.frontierAtReference.summary!!.posteriorVariance, duplicateFit.frontierAtReference.summary!!.posteriorVariance, 1e-12)
        assertTrue(width(independentFit.frontierAtReference.summary!!) < width(duplicateFit.frontierAtReference.summary!!))
        assertEquals(1, duplicateFit.support.effectiveIndependentSessionCount)
        assertEquals(5, duplicateFit.support.observationCount)
    }

    @Test
    fun `uncertainty widens outside observed rep domain`() {
        val fit = fit(generated(frontierKg = 80.0, slope = 0.18, sessions = 8, reps = listOf(6, 8, 10)))
        val model = DynamicStochasticFrontierModel()
        val near = model.predictFrontier(fit, 8.0).summary!!
        val highRep = model.predictFrontier(fit, 20.0).summary!!
        val lowRep = model.predictFrontier(fit, 1.0).summary!!
        assertTrue(widthRatio(highRep) > widthRatio(near))
        assertTrue(widthRatio(lowRep) > widthRatio(near))
        assertTrue(highRep.p05 > 0.0 && lowRep.p05 > 0.0)
    }

    @Test
    fun `rich synthetic evidence recovers frontier and slope within broad tolerance`() {
        val trueFrontier = 82.0
        val trueSlope = 0.22
        val fit = fit(generated(frontierKg = trueFrontier, slope = trueSlope, sessions = 12, reps = listOf(5, 8, 12)))
        val c = fit.frontierAtReference.summary!!
        assertTrue(trueFrontier in c.p05..c.p95)
        assertTrue(trueSlope in fit.slope.summary.p05..fit.slope.summary.p95)
        assertTrue(fit.slackScale.summary.p50 in 0.05..0.25)
        assertTrue(fit.noiseScale.summary.p50 in 0.02..0.10)
        assertEquals(DynamicParameterIdentification.DATA_INFORMED, fit.slackScale.identification)
        assertEquals(DynamicParameterIdentification.DATA_INFORMED, fit.noiseScale.identification)
    }

    @Test
    fun `sparse prior sensitivity is visible and rich evidence reduces it`() {
        val lowPrior = DynamicStochasticFrontierConfig(slopePriorMedian = 0.10)
        val highPrior = DynamicStochasticFrontierConfig(slopePriorMedian = 0.25)
        val sparse = generated(frontierKg = 80.0, slope = 0.18, sessions = 3, reps = listOf(8))
        val sparseLow = fit(sparse, lowPrior).slope.summary.p50
        val sparseHigh = fit(sparse, highPrior).slope.summary.p50
        val sparseGap = kotlin.math.abs(sparseHigh - sparseLow)
        assertTrue(sparseGap > 0.08)

        val rich = generated(frontierKg = 80.0, slope = 0.18, sessions = 12, reps = listOf(4, 6, 8, 12, 16))
        val richLow = fit(rich, lowPrior).slope.summary.p50
        val richHigh = fit(rich, highPrior).slope.summary.p50
        assertTrue(kotlin.math.abs(richHigh - richLow) < sparseGap)
    }

    @Test
    fun `SetDemand hook retains slack distribution without defining a threshold or RIR`() {
        val fit = fit(generated(frontierKg = 80.0, slope = 0.18, sessions = 6, reps = listOf(8)))
        val model = DynamicStochasticFrontierModel()
        val observationId = fit.selectedObservationIds.first()
        val small = model.slackProbabilityAtMost(fit, observationId, 0.05)
        val large = model.slackProbabilityAtMost(fit, observationId, 0.25)
        assertTrue(small in 0.0..1.0)
        assertTrue(large in 0.0..1.0)
        assertTrue(large >= small)
        assertTrue(fit.observationSlack.none { it.semanticDefinition.contains("rir", ignoreCase = true) })
    }

    @Test
    fun `demonstration predictive is available without chronological evaluation loop`() {
        val fit = fit(generated(frontierKg = 80.0, slope = 0.18, sessions = 6, reps = listOf(5, 8, 12)))
        val model = DynamicStochasticFrontierModel()
        val logDensity = model.demonstrationLogPredictiveDensity(fit, 8.0, 75.0)
        assertTrue(logDensity.isFinite())
    }

    @Test
    fun `zero evidence and numerical budget failure are explicit`() {
        val empty = projection(emptyList())
        val model = DynamicStochasticFrontierModel()
        val noEvidence = assertFailsWith<DynamicCapabilityFitException> {
            model.fit(request(empty, model.config))
        }
        assertEquals(DynamicCapabilityFitFailureReason.NO_ELIGIBLE_EVIDENCE, noEvidence.reason)

        val tinyBudget = DynamicStochasticFrontierConfig(maximumGridEvaluations = 100)
        val budgetModel = DynamicStochasticFrontierModel(tinyBudget)
        val failure = assertFailsWith<DynamicCapabilityFitException> {
            budgetModel.fit(request(projection(generated(sessions = 3, reps = listOf(8))), tinyBudget))
        }
        assertEquals(DynamicCapabilityFitFailureReason.NUMERICAL_BUDGET_EXCEEDED, failure.reason)
    }

    @Test
    fun `invalid config and impossible repetition query fail explicitly`() {
        assertFailsWith<IllegalArgumentException> { DynamicStochasticFrontierConfig(studentTDegreesOfFreedom = 2.0) }
        assertFailsWith<IllegalArgumentException> { DynamicStochasticFrontierConfig(noiseScalePriorMedian = 0.0) }
        assertFailsWith<IllegalArgumentException> { DynamicStochasticFrontierConfig(slackScalePriorMedian = -0.1) }
        val fit = fit(generated(sessions = 3, reps = listOf(8)))
        assertFailsWith<IllegalArgumentException> { DynamicStochasticFrontierModel().predictFrontier(fit, 0.0) }
        assertFailsWith<IllegalArgumentException> { DynamicStochasticFrontierModel().predictFrontier(fit, Double.NaN) }
    }

    @Test
    fun `near-identical and broad rep domains remain finite`() {
        val near = fit(generated(frontierKg = 70.0, slope = 0.18, sessions = 6, reps = listOf(8, 9)))
        assertTrue(near.frontierAtReference.summary!!.p50.isFinite())
        val broad = fit(generated(frontierKg = 80.0, slope = 0.18, sessions = 8, reps = listOf(1, 8, 100)))
        val prediction = DynamicStochasticFrontierModel().predictFrontier(broad, 20.0).summary!!
        assertTrue(prediction.p05 > 0.0 && prediction.p95.isFinite())
    }

    @Test
    fun `extreme finite in-prior resistance remains numerically stable`() {
        val low = fit(listOf(set("tiny", "session_tiny", 8, 0.11, day = 1)))
        val high = fit(listOf(set("huge", "session_huge", 8, 4_900.0, day = 1)))
        assertTrue(low.frontierAtReference.summary!!.p05 > 0.0)
        assertTrue(high.frontierAtReference.summary!!.p95.isFinite())
    }

    @Test
    fun `config provenance names every behaviour choice and remains context none with no e1RM`() {
        val config = DynamicStochasticFrontierConfig()
        val payload = config.toModelConfig(Instant.EPOCH).canonicalConfigPayload
        listOf(
            "evidencePolicyIdentity",
            "resistanceResolverIdentity",
            "slackDistribution",
            "noiseDistribution",
            "studentTDegreesOfFreedom",
            "slopePrior",
            "frontierPrior",
            "slackScalePrior",
            "noiseScalePrior",
            "withinSessionPolicy",
            "currentCapabilityPolicy",
            "recentIndependentSessionWindow",
            "approximation",
            "extrapolationLogSdPerUnitOutsideDomain",
            "contextConsumption",
        ).forEach { assertTrue(payload.contains(it), it) }
        assertTrue(payload.contains("NONE"))
        assertTrue(!payload.contains("e1rm", ignoreCase = true))
        assertTrue(!DynamicResistanceV1Contract.CAPABILITY_STATE_SEMANTICS.contains("e1rm", ignoreCase = true))
    }

    @Test
    fun `several years of one-profile history stays within bounded runtime`() {
        val history = generated(frontierKg = 85.0, slope = 0.19, sessions = 60, reps = listOf(5, 8, 10, 12))
        lateinit var fitResult: dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
        val elapsed = measureTimeMillis { fitResult = fit(history) }
        assertEquals(12, fitResult.support.effectiveIndependentSessionCount)
        assertEquals(48, fitResult.support.observationCount)
        assertTrue(elapsed < 20_000, "Default 7B.2 fit took ${elapsed}ms")
    }

    private fun fit(
        evidence: List<DynamicResistanceEvidence>,
        config: DynamicStochasticFrontierConfig = DynamicStochasticFrontierConfig(),
        horizon: Instant = BASE.plusSeconds(10_000L * DAY_SECONDS),
    ): dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit {
        val model = DynamicStochasticFrontierModel(config)
        return model.fit(request(projection(evidence), config, horizon))
    }

    private fun request(
        projection: DynamicResistanceEvidenceProjection,
        config: DynamicStochasticFrontierConfig,
        horizon: Instant = BASE.plusSeconds(10_000L * DAY_SECONDS),
    ) = DynamicCapabilityFitRequest(
        projection = projection,
        inferenceHorizon = horizon,
        modelConfig = config.toModelConfig(Instant.EPOCH),
    )

    private fun projection(evidence: List<DynamicResistanceEvidence>): DynamicResistanceEvidenceProjection {
        val sorted = evidence.sortedWith(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId })
        return DynamicResistanceEvidenceProjection(
            profile = PROFILE,
            side = Laterality.BILATERAL,
            evidence = sorted,
            exclusions = emptyList(),
            referenceRepetitions = DynamicReferenceRepSelector.select(sorted),
            policy = DynamicResistanceV1Contract.evidencePolicy,
        )
    }

    private fun generated(
        frontierKg: Double = 80.0,
        slope: Double = 0.18,
        sessions: Int,
        reps: List<Int>,
        sessionOffset: Int = 0,
    ): List<DynamicResistanceEvidence> {
        val slackPattern = listOf(0.006, 0.030, 0.070, 0.018, 0.110, 0.010, 0.050, 0.025, 0.085, 0.014, 0.060, 0.020)
        val noisePattern = listOf(0.010, -0.015, 0.020, -0.010, 0.0, 0.015, -0.020, 0.005, 0.010, -0.005, 0.0, 0.010)
        return buildList {
            repeat(sessions) { sessionIndex ->
                val absoluteSession = sessionOffset + sessionIndex
                reps.forEachIndexed { repIndex, rep ->
                    val slack = slackPattern[(absoluteSession + repIndex) % slackPattern.size]
                    val noise = noisePattern[(absoluteSession * 2 + repIndex) % noisePattern.size]
                    val logResistance = ln(frontierKg) - slope * ln(rep / 8.0) - slack + noise
                    add(
                        set(
                            id = "g_${absoluteSession}_$repIndex",
                            sessionId = "session_$absoluteSession",
                            reps = rep,
                            resistanceKg = exp(logResistance),
                            day = absoluteSession,
                            ordinal = repIndex,
                        ),
                    )
                }
            }
        }
    }

    private fun set(
        id: String,
        sessionId: String,
        reps: Int,
        resistanceKg: Double,
        day: Int,
        ordinal: Int = 0,
    ): DynamicResistanceEvidence = DynamicResistanceEvidence(
        observationId = "obs_$id",
        setRecordId = "set_$id",
        sessionId = sessionId,
        executionProfileVersionId = PROFILE.executionProfileVersionId,
        side = Laterality.BILATERAL,
        completedAt = BASE.plusSeconds(day.toLong() * DAY_SECONDS + ordinal),
        repetitions = reps,
        resistance = dev.kian.mymettle.domain.inference.ProfileLocalResistanceCoordinate(
            value = resistanceKg,
            unit = UnitId.KILOGRAM,
            resistanceSemantics = ResistanceSemantics.EXTERNAL,
            entryBasis = EntryBasis.TOTAL,
            resistanceModelVersion = "test-resistance-v1",
            resolverVersion = DynamicResistanceV1Contract.RESISTANCE_RESOLVER_VERSION,
        ),
        metricEvidence = listOf(
            DynamicMetricEvidenceAudit(
                metric = PerformanceMetric.EXTERNAL_LOAD,
                entered = Quantity(resistanceKg, UnitId.KILOGRAM),
                canonical = Quantity(resistanceKg, UnitId.KILOGRAM),
                acquisitionMethod = "synthetic_test",
                evidenceGranularity = "summary",
            ),
            DynamicMetricEvidenceAudit(
                metric = PerformanceMetric.REPETITIONS,
                entered = Quantity(reps.toDouble(), UnitId.REPETITION),
                canonical = Quantity(reps.toDouble(), UnitId.REPETITION),
                acquisitionMethod = "synthetic_test",
                evidenceGranularity = "summary",
            ),
        ),
        warmUp = false,
        setKind = "working",
        evidencePolicyIdentity = DynamicResistanceV1Contract.evidencePolicy.identity,
    )

    private fun width(summary: dev.kian.mymettle.domain.inference.PosteriorSummary): Double = summary.p95 - summary.p05
    private fun widthRatio(summary: dev.kian.mymettle.domain.inference.PosteriorSummary): Double = summary.p95 / summary.p05

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val PROFILE = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("synthetic-profile:v1"),
            executionProfileId = ExecutionProfileId("synthetic-profile"),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel(
                modelVersion = "test-resistance-v1",
                semantics = ResistanceSemantics.EXTERNAL,
                bodyweightCoefficient = 0.0,
                externalLoadCoefficient = 1.0,
                assistanceCoefficient = 0.0,
            ),
            entryBasis = EntryBasis.TOTAL,
            lateralityMode = LateralityMode.BILATERAL_ONLY,
        )
    }
}
