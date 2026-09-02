package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.LoadedHoldEvidence
import dev.kian.mymettle.domain.inference.MuscleExposure
import dev.kian.mymettle.domain.inference.NBio7DCapabilityProjection
import dev.kian.mymettle.domain.inference.NBio7DConfig
import dev.kian.mymettle.domain.inference.NBio7DDownstreamFidelity
import dev.kian.mymettle.domain.inference.NBio7DPosteriorMath
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.NonDynamicEvidenceProjection
import dev.kian.mymettle.domain.inference.NonDynamicProfileSemantics
import dev.kian.mymettle.domain.inference.NonDynamicResistanceCoordinate
import dev.kian.mymettle.domain.inference.ProfileLocalResistanceCoordinate
import dev.kian.mymettle.domain.inference.SetDemandStructuralSupport
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import dev.kian.mymettle.engine.performance.NonDynamicAdaptiveSparseSolver
import dev.kian.mymettle.engine.performance.NonDynamicDenseReferenceSolver
import dev.kian.mymettle.inference.DynamicTrendCapabilityShadowRepository
import dev.kian.mymettle.inference.NonDynamicCapabilityShadowRepository
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

data class NBio7DDynamicFidelityScenarioResult(
    val name: String,
    val maximumGapQuantileAbsoluteError: Double,
    val maximumDemandProbabilityAbsoluteError: Double,
    val maximumEffectiveDoseQuantileAbsoluteError: Double,
    val sessionRawP50AbsoluteError: Double,
    val sessionConcaveP50AbsoluteError: Double,
    val denseElapsedMillis: Long,
    val sparseElapsedMillis: Long,
    val passed: Boolean,
)

data class NBio7DNonDynamicFidelityResult(
    val family: String,
    val gapP05AbsoluteError: Double,
    val gapP50AbsoluteError: Double,
    val gapP95AbsoluteError: Double,
    val demandProbabilityAbsoluteError: Double,
    val exposureUnchanged: Boolean,
    val denseElapsedMillis: Long,
    val sparseElapsedMillis: Long,
    val passed: Boolean,
)

data class NBio7DDownstreamFidelityValidationReport(
    val dynamicScenarios: List<NBio7DDynamicFidelityScenarioResult>,
    val nonDynamicLoadedHold: NBio7DNonDynamicFidelityResult,
) {
    val passed: Boolean get() = dynamicScenarios.all { it.passed } && nonDynamicLoadedHold.passed
}

/** Descriptive same-math solver approximation propagation through 7D; this never retunes a solver. */
object NBio7DDownstreamFidelityValidation {
    fun run(config: NBio7DConfig = NBio7DConfig()): NBio7DDownstreamFidelityValidationReport {
        val dynamic = listOf(
            DynamicScenario("stable", 5, 0.0, listOf(6, 8, 12), 0.045),
            DynamicScenario("progressing", 6, 0.035, listOf(6, 8, 12), 0.045),
            DynamicScenario("rep_extrapolation_stress", 5, -0.02, listOf(4, 8, 16), 0.055),
        ).map { dynamicScenario(it, config) }
        return NBio7DDownstreamFidelityValidationReport(
            dynamicScenarios = dynamic,
            nonDynamicLoadedHold = loadedHoldScenario(config),
        )
    }

    private fun dynamicScenario(
        scenario: DynamicScenario,
        config: NBio7DConfig,
    ): NBio7DDynamicFidelityScenarioResult {
        val evidence = buildList {
            repeat(scenario.sessions) { session ->
                listOf(6, 8, 12).forEachIndexed { ordinal, reps ->
                    val logFrontierAtEight = ln(82.0) + scenario.trend * session
                    val resistance = exp(logFrontierAtEight - 0.18 * ln(reps / 8.0) - 0.035)
                    add(dynamicSet("${session}_$ordinal", "session_$session", reps, resistance, session, ordinal))
                }
            }
        }
        val projection = DynamicResistanceEvidenceProjection(
            profile = DYNAMIC_PROFILE,
            side = Laterality.BILATERAL,
            evidence = evidence,
            exclusions = emptyList(),
            referenceRepetitions = null,
            policy = NBioCorrectedCandidateV2Bundle.evidencePolicy,
        )
        val horizon = evidence.maxOf { it.completedAt }
        val base = DynamicStochasticFrontierModel(NBioCorrectedCandidateV2Bundle.baseConfig)
        val baseFit = base.fit(
            DynamicCapabilityFitRequest(
                projection,
                horizon,
                base.config.toModelConfig(DynamicTrendCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT),
            ),
        )
        val dense = NBioCorrectedCandidateV2Bundle.denseSolver()
        val sparse = NBioCorrectedCandidateV2Bundle.sparseSolver()
        val denseStart = System.nanoTime()
        val denseFit = dense.fitFromFrozenV1(
            DynamicCapabilityFitRequest(
                projection,
                horizon,
                dense.modelConfig(DynamicTrendCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT),
            ),
            baseFit,
        )
        val denseMillis = elapsedMillis(denseStart)
        val sparseStart = System.nanoTime()
        val sparseFit = sparse.fitFromFrozenV1(
            DynamicCapabilityFitRequest(
                projection,
                horizon,
                sparse.modelConfig(DynamicTrendCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT),
            ),
            baseFit,
        )
        val sparseMillis = elapsedMillis(sparseStart)
        val performed = scenario.targetReps.map { reps ->
            val trueNext = ln(82.0) + scenario.trend * scenario.sessions
            val resistance = exp(trueNext - 0.18 * ln(reps.toDouble() / 8.0) - scenario.performedGap)
            NBio7DDownstreamFidelity.DynamicPerformedSet(reps, resistance, EXPOSURE)
        }
        val result = NBio7DDownstreamFidelity.compareDynamic(denseFit, sparseFit, performed, config = config)
        val passed = result.maximumGapQuantileAbsoluteError <= 0.08 &&
            result.maximumDemandProbabilityAbsoluteError <= 0.25 &&
            result.maximumEffectiveDoseQuantileAbsoluteError <= EXPOSURE.conservativeExposure &&
            result.sessionRawP50AbsoluteError <= scenario.targetReps.size * EXPOSURE.conservativeExposure
        return NBio7DDynamicFidelityScenarioResult(
            name = scenario.name,
            maximumGapQuantileAbsoluteError = result.maximumGapQuantileAbsoluteError,
            maximumDemandProbabilityAbsoluteError = result.maximumDemandProbabilityAbsoluteError,
            maximumEffectiveDoseQuantileAbsoluteError = result.maximumEffectiveDoseQuantileAbsoluteError,
            sessionRawP50AbsoluteError = result.sessionRawP50AbsoluteError,
            sessionConcaveP50AbsoluteError = result.sessionConcaveP50AbsoluteError,
            denseElapsedMillis = denseMillis,
            sparseElapsedMillis = sparseMillis,
            passed = passed,
        )
    }

    private fun loadedHoldScenario(config: NBio7DConfig): NBio7DNonDynamicFidelityResult {
        val evidence = buildList {
            repeat(5) { session ->
                listOf(20.0, 30.0, 45.0).forEachIndexed { ordinal, duration ->
                    val logReferenceFrontier = ln(42.0) + 0.018 * session
                    val resistance = exp(logReferenceFrontier - 0.55 * ln(duration / 30.0) - 0.04)
                    add(
                        LoadedHoldEvidence(
                            observationId = "hold_obs_${session}_$ordinal",
                            setRecordId = "hold_set_${session}_$ordinal",
                            sessionId = "hold_session_$session",
                            executionProfileVersionId = HOLD_PROFILE.executionProfileVersionId,
                            side = Laterality.BILATERAL,
                            completedAt = BASE.plusSeconds(session * DAY_SECONDS + ordinal.toLong()),
                            resistance = NonDynamicResistanceCoordinate(
                                resistance,
                                ResistanceSemantics.EXTERNAL,
                                EntryBasis.TOTAL,
                                "fixture-resistance-v1",
                            ),
                            durationSeconds = duration,
                            bodyMassContextKg = null,
                            evidencePolicyIdentity = NonDynamicCapabilityV1.evidencePolicy.identity,
                        ),
                    )
                }
            }
        }
        val projection = NonDynamicEvidenceProjection(
            profile = HOLD_PROFILE,
            side = Laterality.BILATERAL,
            evidence = evidence,
            exclusions = emptyList(),
            referenceCoordinate = 30.0,
            policy = NonDynamicCapabilityV1.evidencePolicy,
        )
        val horizon = evidence.maxOf { it.completedAt }
        val dense = NonDynamicDenseReferenceSolver(NonDynamicCapabilityV1.loadedHold)
        val sparse = NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.loadedHold)
        val denseStart = System.nanoTime()
        val denseFit = dense.fit(projection, horizon, NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
        val denseMillis = elapsedMillis(denseStart)
        val sparseStart = System.nanoTime()
        val sparseFit = sparse.fit(projection, horizon, NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
        val sparseMillis = elapsedMillis(sparseStart)
        val duration = 30.0
        val resistance = exp(ln(42.0) + 0.018 * 5 - 0.045)
        val denseDemand = NBio7DPosteriorMath.setDemandFromLogFrontier(
            MetricFamily.LOADED_HOLD,
            NBio7DCapabilityProjection.loadedHoldLogFrontier(denseFit, duration),
            ln(resistance),
            SetDemandStructuralSupport.RESOLVED,
            config,
        )
        val sparseDemand = NBio7DPosteriorMath.setDemandFromLogFrontier(
            MetricFamily.LOADED_HOLD,
            NBio7DCapabilityProjection.loadedHoldLogFrontier(sparseFit, duration),
            ln(resistance),
            SetDemandStructuralSupport.RESOLVED,
            config,
        )
        val dg = requireNotNull(denseDemand.frontierGapSummary)
        val sg = requireNotNull(sparseDemand.frontierGapSummary)
        val denseDose = NBio7DPosteriorMath.effectiveDose(EXPOSURE, denseDemand)
        val sparseDose = NBio7DPosteriorMath.effectiveDose(EXPOSURE, sparseDemand)
        val p05 = abs(dg.credibleLower05 - sg.credibleLower05)
        val p50 = abs(dg.estimateMedian - sg.estimateMedian)
        val p95 = abs(dg.credibleUpper95 - sg.credibleUpper95)
        val q = abs(
            requireNotNull(denseDemand.probabilityAtOrWithinDelta) -
                requireNotNull(sparseDemand.probabilityAtOrWithinDelta),
        )
        val exposureSame = denseDose.exposure == sparseDose.exposure && denseDose.exposure.conservativeExposure == 0.7
        return NBio7DNonDynamicFidelityResult(
            family = MetricFamily.LOADED_HOLD.storageValue,
            gapP05AbsoluteError = p05,
            gapP50AbsoluteError = p50,
            gapP95AbsoluteError = p95,
            demandProbabilityAbsoluteError = q,
            exposureUnchanged = exposureSame,
            denseElapsedMillis = denseMillis,
            sparseElapsedMillis = sparseMillis,
            passed = p05 <= 0.12 && p50 <= 0.08 && p95 <= 0.12 && q <= 0.30 && exposureSame,
        )
    }

    private fun dynamicSet(
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
            executionProfileVersionId = DYNAMIC_PROFILE.executionProfileVersionId,
            side = Laterality.BILATERAL,
            completedAt = BASE.plusSeconds(day.toLong() * DAY_SECONDS + ordinal),
            repetitions = reps,
            resistance = ProfileLocalResistanceCoordinate(
                resistanceKg,
                UnitId.KILOGRAM,
                ResistanceSemantics.EXTERNAL,
                EntryBasis.TOTAL,
                "fixture-resistance-v1",
                NBioCorrectedCandidateV2Bundle.evidencePolicy.resistanceCoordinateResolverVersion,
            ),
            metricEvidence = listOf(
                DynamicMetricEvidenceAudit(
                    PerformanceMetric.EXTERNAL_LOAD,
                    load,
                    load,
                    "synthetic",
                    "set",
                ),
            ),
            warmUp = false,
            setKind = "working",
            evidencePolicyIdentity = NBioCorrectedCandidateV2Bundle.evidencePolicy.identity,
        )
    }

    private data class DynamicScenario(
        val name: String,
        val sessions: Int,
        val trend: Double,
        val targetReps: List<Int>,
        val performedGap: Double,
    )

    private const val DAY_SECONDS = 86_400L
    private val BASE = Instant.parse("2026-01-01T00:00:00Z")
    private val EXPOSURE = MuscleExposure("segment", "bilateral", 0.7, "recruitment:v1")
    private val DYNAMIC_PROFILE = DynamicResistanceProfileSemantics(
        ExecutionProfileVersionId("7d-fidelity-version"),
        ExecutionProfileId("7d-fidelity-profile"),
        MetricFamily.DYNAMIC_RESISTANCE,
        ResistanceModel(
            "fixture-resistance-v1",
            ResistanceSemantics.EXTERNAL,
            0.0,
            1.0,
            0.0,
        ),
        EntryBasis.TOTAL,
        LateralityMode.BILATERAL_ONLY,
    )
    private val HOLD_PROFILE = NonDynamicProfileSemantics(
        ExecutionProfileVersionId("7d-loaded-hold:v1"),
        ExecutionProfileId("7d-loaded-hold"),
        MetricFamily.LOADED_HOLD,
        ResistanceModel(
            "fixture-resistance-v1",
            ResistanceSemantics.EXTERNAL,
            0.0,
            1.0,
            0.0,
        ),
        EntryBasis.TOTAL,
        LateralityMode.BILATERAL_ONLY,
    )

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000L
}
