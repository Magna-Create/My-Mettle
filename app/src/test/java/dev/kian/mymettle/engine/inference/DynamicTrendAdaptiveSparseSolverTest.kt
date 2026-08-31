package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
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
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceConfig
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceModel
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicTrendAdaptiveSparseSolverTest {
    @Test
    fun `same math sparse solver prunes base support while preserving dense posterior fixture`() {
        val evidence = generated(sessions = 6, trend = 0.035)
        val projection = projection(evidence)
        val horizon = evidence.maxOf { it.completedAt }
        val denseConfig = DynamicTrendDenseReferenceConfig()
        val denseModel = DynamicTrendDenseReferenceModel(denseConfig)
        val baseModel = DynamicStochasticFrontierModel(DynamicTrendFrontierV2.config.baseConfig)
        val baseFit = baseModel.fit(
            DynamicCapabilityFitRequest(
                projection,
                horizon,
                baseModel.config.toModelConfig(CONFIG_CREATED_AT),
            ),
        )
        val dense = denseModel.fitFromFrozenV1(
            DynamicCapabilityFitRequest(
                projection,
                horizon,
                denseConfig.toModelConfig(CONFIG_CREATED_AT),
            ),
            baseFit,
        )
        val sparse = DynamicTrendAdaptiveSparseSolver(
            DynamicTrendAdaptiveSparseConfig(
                denseCoreConfig = denseConfig,
                retainedBasePosteriorMass = 0.995,
                minimumRetainedBaseNodes = 32,
                maximumRetainedBaseNodes = 160,
            ),
        )
        val (prunedBase, support) = sparse.pruneBasePosterior(baseFit)
        val sparseFit = sparse.fitFromFrozenV1(
            DynamicCapabilityFitRequest(
                projection,
                horizon,
                sparse.modelConfig(CONFIG_CREATED_AT),
            ),
            baseFit,
        )
        val fidelity = DynamicTrendPosteriorFidelity.compare(dense, sparseFit)

        assertEquals(dense.mathematicalModelIdentity, sparseFit.mathematicalModelIdentity)
        assertEquals(InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR, sparseFit.solverDiagnostics.solverIdentity.solverFamily)
        assertTrue(support.retainedBaseNodeCount < support.originalBaseNodeCount)
        assertTrue(prunedBase.posteriorNodes.size <= 160)
        assertTrue(requireNotNull(sparseFit.solverDiagnostics.evaluatedNodeCount) < requireNotNull(dense.solverDiagnostics.evaluatedNodeCount))
        assertTrue(fidelity.nextFrontierMedianRelativeError <= 0.05)
        assertTrue(fidelity.trendPositiveProbabilityAbsoluteError <= 0.08)
        assertTrue(fidelity.maxStandardisedMarginalWasserstein1 <= 0.35)
    }

    @Test
    fun `adaptive sparse pruning and fit are deterministic`() {
        val evidence = generated(sessions = 5, trend = -0.02)
        val projection = projection(evidence)
        val horizon = evidence.maxOf { it.completedAt }
        val baseModel = DynamicStochasticFrontierModel(DynamicTrendFrontierV2.config.baseConfig)
        val baseFit = baseModel.fit(DynamicCapabilityFitRequest(
            projection,
            horizon,
            baseModel.config.toModelConfig(CONFIG_CREATED_AT),
        ))
        val sparse = DynamicTrendAdaptiveSparseSolver(
            DynamicTrendAdaptiveSparseConfig(maximumRetainedBaseNodes = 180),
        )
        val request = DynamicCapabilityFitRequest(projection, horizon, sparse.modelConfig(CONFIG_CREATED_AT))
        val first = sparse.fitFromFrozenV1(request, baseFit)
        val second = sparse.fitFromFrozenV1(request, baseFit)
        assertEquals(first.posteriorNodes, second.posteriorNodes)
        assertEquals(first.frontierAtLatestSession.summary, second.frontierAtLatestSession.summary)
        assertEquals(first.frontierTrend.summary, second.frontierTrend.summary)
    }

    private fun generated(sessions: Int, trend: Double): List<DynamicResistanceEvidence> = buildList {
        repeat(sessions) { session ->
            listOf(6, 8, 12).forEachIndexed { ordinal, reps ->
                val logFrontierAtEight = ln(82.0) + trend * session
                val resistance = exp(logFrontierAtEight - 0.18 * ln(reps / 8.0) - 0.035)
                add(set("${session}_$ordinal", "session_$session", reps, resistance, session, ordinal))
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
        ordinal: Int,
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
        )
    }

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val CONFIG_CREATED_AT = Instant.parse("2026-08-31T00:00:00Z")
        private val PROFILE = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("adaptive-sparse-test-version"),
            executionProfileId = ExecutionProfileId("adaptive-sparse-test-profile"),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel(
                semantics = ResistanceSemantics.EXTERNAL,
                externalLoadCoefficient = 1.0,
            ),
            entryBasis = EntryBasis.TOTAL,
            lateralityMode = LateralityMode.BILATERAL,
        )
    }
}
