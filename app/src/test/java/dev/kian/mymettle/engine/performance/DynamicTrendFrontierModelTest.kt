package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.inference.InferenceSolverFamily
import dev.kian.mymettle.domain.inference.ProfileLocalResistanceCoordinate
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicTrendFrontierModelTest {
    @Test
    fun `sparse history fixes trend to zero and dense v2 collapses exactly to frozen v1`() {
        val evidence = generated(sessions = 2, trend = 0.08, repsBySession = { listOf(8) })
        val projection = projection(evidence)
        val horizon = evidence.maxOf { it.completedAt }
        val v1 = DynamicStochasticFrontierModel(TEST_MATH_CONFIG.baseConfig)
        val v1Fit = v1.fit(DynamicCapabilityFitRequest(
            projection,
            horizon,
            TEST_MATH_CONFIG.baseConfig.toModelConfig(CONFIG_CREATED_AT),
        ))
        val dense = DynamicTrendDenseReferenceModel(TEST_DENSE_CONFIG)
        val v2Fit = dense.fit(DynamicCapabilityFitRequest(
            projection,
            horizon,
            dense.solverConfig.toModelConfig(CONFIG_CREATED_AT),
        ))
        assertEquals(DynamicParameterIdentification.FIXED_BY_CONFIG, v2Fit.frontierTrend.identification)
        assertEquals(0.0, v2Fit.frontierTrend.summary.p50, 0.0)
        assertEquals(v1Fit.frontierAtReference.summary, v2Fit.frontierAtLatestSession.summary)
        assertEquals(v1Fit.posteriorNodes.size, v2Fit.posteriorNodes.size)
    }

    @Test
    fun `flat capability keeps strongly shrunk trend near zero`() {
        val fit = fitDense(generated(sessions = 5, trend = 0.0, repsBySession = { listOf(6, 8, 12) }))
        assertTrue(abs(fit.frontierTrend.summary.p50) <= 0.02)
        val current = DENSE_MODEL.predictFrontier(fit, 8.0, 0.0).summary!!
        val next = DENSE_MODEL.predictFrontier(fit, 8.0, 1.0).summary!!
        assertTrue(abs(ln(next.p50 / current.p50)) <= 0.03)
    }

    @Test
    fun `upward trend is learned and next-session frontier moves upward`() {
        val fit = fitDense(generated(sessions = 5, trend = 0.055, repsBySession = { listOf(6, 8, 12) }))
        assertTrue(fit.frontierTrend.summary.p50 > 0.01)
        val current = DENSE_MODEL.predictFrontier(fit, 8.0, 0.0).summary!!
        val next = DENSE_MODEL.predictFrontier(fit, 8.0, 1.0).summary!!
        assertTrue(next.p50 > current.p50)
    }

    @Test
    fun `downward trend can be learned without positive-growth bias`() {
        val fit = fitDense(generated(sessions = 5, trend = -0.055, repsBySession = { listOf(6, 8, 12) }))
        assertTrue(fit.frontierTrend.summary.p50 < -0.01)
        val current = DENSE_MODEL.predictFrontier(fit, 8.0, 0.0).summary!!
        val next = DENSE_MODEL.predictFrontier(fit, 8.0, 1.0).summary!!
        assertTrue(next.p50 < current.p50)
    }

    @Test
    fun `same-session duplication does not manufacture longitudinal trend evidence`() {
        val onePerSession = generated(sessions = 4, trend = 0.03, repsBySession = { listOf(8) })
        val duplicated = buildList {
            onePerSession.groupBy { it.sessionId }.values.forEach { session ->
                val original = session.single()
                repeat(5) { index -> add(original.copy(
                    observationId = "${original.observationId}_$index",
                    setRecordId = "${original.setRecordId}_$index",
                    completedAt = original.completedAt.plusSeconds(index.toLong()),
                )) }
            }
        }
        val singleFit = fitDense(onePerSession)
        val duplicateFit = fitDense(duplicated)
        assertEquals(4, duplicateFit.support.effectiveIndependentSessionCount)
        assertEquals(singleFit.frontierTrend.summary.p50, duplicateFit.frontierTrend.summary.p50, 1e-10)
        assertEquals(singleFit.frontierTrend.summary.posteriorVariance, duplicateFit.frontierTrend.summary.posteriorVariance, 1e-10)
    }

    @Test
    fun `one isolated submaximal set does not create a strong negative trend`() {
        val evidence = generated(sessions = 5, trend = 0.0, repsBySession = { listOf(8, 8, 8) }).toMutableList()
        val target = evidence.indexOfLast { it.sessionId == "session_3" }
        evidence[target] = evidence[target].copy(
            resistance = evidence[target].resistance.copy(value = evidence[target].resistance.value * 0.65),
        )
        val fit = fitDense(evidence)
        assertTrue(fit.frontierTrend.summary.p50 > -0.03)
    }

    @Test
    fun `rep-domain mixing does not create trend when slope explains the change`() {
        val evidence = generated(
            sessions = 5,
            trend = 0.0,
            slope = 0.24,
            repsBySession = { session -> if (session % 2 == 0) listOf(6, 8) else listOf(8, 12) },
        )
        val fit = fitDense(evidence)
        assertTrue(abs(fit.frontierTrend.summary.p50) <= 0.025)
        assertTrue(fit.slope.summary.p50 > 0.10)
    }

    @Test
    fun `math identity is shared while dense and conditional Laplace solver identities are distinct`() {
        val legacyComposite = DynamicTrendFrontierV2.config.toModelConfig(CONFIG_CREATED_AT)
        val v1 = DynamicTrendFrontierV2.config.baseConfig.toModelConfig(CONFIG_CREATED_AT)
        val denseComposite = TEST_DENSE_CONFIG.toModelConfig(CONFIG_CREATED_AT)
        assertTrue(legacyComposite.id != v1.id)
        assertTrue(denseComposite.id != legacyComposite.id)
        assertEquals(
            DynamicTrendFrontierV2.mathematicalIdentity(TEST_MATH_CONFIG),
            TEST_DENSE_CONFIG.mathematicalModelIdentity,
        )
        assertEquals(InferenceSolverFamily.DENSE_TENSOR_REFERENCE, TEST_DENSE_CONFIG.solverIdentity.solverFamily)
        assertEquals(InferenceSolverFamily.SEQUENTIAL_LAPLACE, DynamicTrendFrontierV2.conditionalLaplaceSolverIdentity.solverFamily)
        assertTrue(DynamicTrendFrontierV2.mathematicalModelIdentity.identity.contains("trendPrior=normal(0,0.04)"))
        assertEquals(5.0, DynamicTrendFrontierV2.config.baseConfig.studentTDegreesOfFreedom)
        assertEquals(0.12, DynamicTrendFrontierV2.config.baseConfig.slackScalePriorMedian)
        assertEquals(0.05, DynamicTrendFrontierV2.config.baseConfig.noiseScalePriorMedian)
        assertEquals(0.16, DynamicTrendFrontierV2.config.baseConfig.slopePriorMedian)
        assertEquals(12, DynamicTrendFrontierV2.config.baseConfig.recentIndependentSessionWindow)
        assertTrue(DynamicTrendFrontierV2.config.contextConsumption.startsWith("NONE:"))
    }

    private fun fitDense(evidence: List<DynamicResistanceEvidence>): dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit =
        DENSE_MODEL.fit(DynamicCapabilityFitRequest(
            projection = projection(evidence),
            inferenceHorizon = evidence.maxOf { it.completedAt },
            modelConfig = DENSE_MODEL.solverConfig.toModelConfig(CONFIG_CREATED_AT),
        ))

    private fun generated(
        sessions: Int,
        trend: Double,
        slope: Double = 0.18,
        repsBySession: (Int) -> List<Int>,
    ): List<DynamicResistanceEvidence> = buildList {
        repeat(sessions) { session ->
            repsBySession(session).forEachIndexed { ordinal, reps ->
                val logFrontierAtEight = ln(80.0) + trend * session
                val logResistance = logFrontierAtEight - slope * ln(reps / 8.0) - 0.04
                add(set(
                    id = "${session}_$ordinal",
                    sessionId = "session_$session",
                    reps = reps,
                    resistanceKg = exp(logResistance),
                    day = session,
                    ordinal = ordinal,
                ))
            }
        }
    }

    private fun projection(evidence: List<DynamicResistanceEvidence>) = DynamicResistanceEvidenceProjection(
        profile = PROFILE,
        side = Laterality.BILATERAL,
        evidence = evidence,
        exclusions = emptyList(),
        referenceRepetitions = null,
        policy = DynamicResistanceV2Contract.evidencePolicy,
    )

    private fun set(
        id: String,
        sessionId: String,
        reps: Int,
        resistanceKg: Double,
        day: Int,
        ordinal: Int = 0,
    ): DynamicResistanceEvidence {
        val load = Quantity(resistanceKg, UnitId.KILOGRAM)
        return DynamicResistanceEvidence(
            observationId = "obs_$id",
            setRecordId = "set_$id",
            sessionId = sessionId,
            executionProfileVersionId = PROFILE.executionProfileVersionId,
            side = Laterality.BILATERAL,
            completedAt = BASE.plusSeconds(day.toLong() * DAY_SECONDS + ordinal),
            repetitions = reps,
            resistance = ProfileLocalResistanceCoordinate(
                value = resistanceKg,
                unit = UnitId.KILOGRAM,
                resistanceSemantics = ResistanceSemantics.EXTERNAL,
                entryBasis = EntryBasis.TOTAL,
                resistanceModelVersion = "test-resistance-v1",
                resolverVersion = DynamicResistanceV2Contract.evidencePolicy.resistanceCoordinateResolverVersion,
            ),
            metricEvidence = listOf(
                DynamicMetricEvidenceAudit(
                    metric = PerformanceMetric.EXTERNAL_LOAD,
                    entered = load,
                    canonical = load,
                    acquisitionMethod = "synthetic",
                    evidenceGranularity = "set",
                ),
            ),
            warmUp = false,
            setKind = "working",
            evidencePolicyIdentity = DynamicResistanceV2Contract.evidencePolicy.identity,
        )
    }

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val CONFIG_CREATED_AT = Instant.parse("2026-08-27T00:00:00Z")
        /** Production Candidate-v2 math is unchanged; behavioural fixtures use smaller v1 grids only to keep CI bounded. */
        private val TEST_MATH_CONFIG = DynamicTrendFrontierV2.config.copy(
            baseConfig = DynamicTrendFrontierV2.config.baseConfig.copy(
                frontierGridPoints = 13,
                slopeGridPoints = 7,
                slackQuadraturePoints = 8,
                slackPosteriorTopNodeCount = 32,
            ),
        )
        private val TEST_DENSE_CONFIG = DynamicTrendDenseReferenceConfig(
            mathematicalConfig = TEST_MATH_CONFIG,
            trendGridPoints = 17,
            trendGridPriorSdRadius = 4.0,
        )
        private val DENSE_MODEL = DynamicTrendDenseReferenceModel(TEST_DENSE_CONFIG)
        private val PROFILE = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("synthetic-trend-profile:v1"),
            executionProfileId = ExecutionProfileId("synthetic-trend-profile"),
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
