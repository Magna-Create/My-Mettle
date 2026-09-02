package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.DynamicTrendAdaptiveSparseConfig
import dev.kian.mymettle.engine.inference.DynamicTrendAdaptiveSparseSolver
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceConfig
import dev.kian.mymettle.engine.performance.DynamicTrendDenseReferenceModel
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertTrue

class NBio7DDownstreamFidelityTest {
    @Test
    fun `adaptive sparse downstream 7D errors remain quantified on stable progressing and stress fixtures`() {
        val scenarios = listOf(
            Scenario("stable", sessions = 5, trend = 0.0, targetReps = listOf(6, 8, 12), performedGap = 0.045),
            Scenario("progressing", sessions = 6, trend = 0.035, targetReps = listOf(6, 8, 12), performedGap = 0.045),
            Scenario("rep_extrapolation_stress", sessions = 5, trend = -0.02, targetReps = listOf(4, 8, 16), performedGap = 0.055),
        )

        scenarios.forEach { scenario ->
            val evidence = generated(scenario.sessions, scenario.trend)
            val projection = projection(evidence)
            val horizon = evidence.maxOf { it.completedAt }
            val denseConfig = DynamicTrendDenseReferenceConfig()
            val dense = DynamicTrendDenseReferenceModel(denseConfig)
            val base = DynamicStochasticFrontierModel(DynamicTrendFrontierV2.config.baseConfig)
            val baseFit = base.fit(
                DynamicCapabilityFitRequest(
                    projection,
                    horizon,
                    base.config.toModelConfig(CONFIG_CREATED_AT),
                ),
            )
            val denseFit = dense.fitFromFrozenV1(
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
                    retainedBasePosteriorMass = 0.9995,
                    minimumRetainedBaseNodes = 64,
                    maximumRetainedBaseNodes = 512,
                ),
            )
            val sparseFit = sparse.fitFromFrozenV1(
                DynamicCapabilityFitRequest(
                    projection,
                    horizon,
                    sparse.modelConfig(CONFIG_CREATED_AT),
                ),
                baseFit,
            )
            val performed = scenario.targetReps.map { reps ->
                val trueNextLogFrontierAtEight = ln(82.0) + scenario.trend * scenario.sessions
                val resistance = exp(
                    trueNextLogFrontierAtEight -
                        0.18 * ln(reps.toDouble() / 8.0) -
                        scenario.performedGap,
                )
                NBio7DDownstreamFidelity.DynamicPerformedSet(
                    repetitions = reps,
                    resistanceKg = resistance,
                    exposure = EXPOSURE,
                )
            }
            val result = NBio7DDownstreamFidelity.compareDynamic(denseFit, sparseFit, performed)

            // These are downstream discrepancy guardrails, not solver retuning targets. The binary
            // ED transform makes quantiles intentionally discontinuous near delta, so q(delta) is
            // the more informative tail comparison and the full errors are retained in Result.
            assertTrue(
                result.maximumGapQuantileAbsoluteError <= 0.08,
                "${scenario.name}: max gap quantile error=${result.maximumGapQuantileAbsoluteError}",
            )
            assertTrue(
                result.maximumDemandProbabilityAbsoluteError <= 0.25,
                "${scenario.name}: max q(delta) error=${result.maximumDemandProbabilityAbsoluteError}",
            )
            assertTrue(
                result.maximumEffectiveDoseQuantileAbsoluteError <= EXPOSURE.conservativeExposure,
                "${scenario.name}: ED quantile error=${result.maximumEffectiveDoseQuantileAbsoluteError}",
            )
            assertTrue(
                result.sessionRawP50AbsoluteError <= scenario.targetReps.size * EXPOSURE.conservativeExposure,
                "${scenario.name}: session raw p50 error=${result.sessionRawP50AbsoluteError}",
            )
        }
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
            warmUp = false,
            setKind = "working",
            evidencePolicyIdentity = DynamicResistanceV2Contract.evidencePolicy.identity,
        )
    }

    private data class Scenario(
        val name: String,
        val sessions: Int,
        val trend: Double,
        val targetReps: List<Int>,
        val performedGap: Double,
    )

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val CONFIG_CREATED_AT = Instant.parse("2026-08-31T00:00:00Z")
        private val EXPOSURE = MuscleExposure("segment", "bilateral", 0.7, "recruitment:v1")
        private val PROFILE = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("7d-fidelity-version"),
            executionProfileId = ExecutionProfileId("7d-fidelity-profile"),
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
