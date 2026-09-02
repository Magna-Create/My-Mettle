package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.performance.NonDynamicAdaptiveSparseSolver
import dev.kian.mymettle.engine.performance.NonDynamicDenseReferenceSolver
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertTrue

class NBio7DNonDynamicDownstreamFidelityTest {
    @Test
    fun `7C loaded hold adaptive sparse approximation remains quantified after 7D projection`() {
        val evidence = buildList {
            repeat(5) { session ->
                listOf(20.0, 30.0, 45.0).forEachIndexed { ordinal, duration ->
                    val logReferenceFrontier = ln(42.0) + 0.018 * session
                    val resistance = exp(logReferenceFrontier - 0.55 * ln(duration / 30.0) - 0.04)
                    add(
                        LoadedHoldEvidence(
                            observationId = "obs_${session}_$ordinal",
                            setRecordId = "set_${session}_$ordinal",
                            sessionId = "session_$session",
                            executionProfileVersionId = PROFILE.executionProfileVersionId,
                            side = Laterality.BILATERAL,
                            completedAt = BASE.plusSeconds(session * DAY_SECONDS + ordinal.toLong()),
                            resistance = NonDynamicResistanceCoordinate(
                                valueKg = resistance,
                                resistanceSemantics = ResistanceSemantics.EXTERNAL,
                                entryBasis = EntryBasis.TOTAL,
                                resistanceModelVersion = "fixture-resistance-v1",
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
            profile = PROFILE,
            side = Laterality.BILATERAL,
            evidence = evidence,
            exclusions = emptyList(),
            referenceCoordinate = 30.0,
            policy = NonDynamicCapabilityV1.evidencePolicy,
        )
        val horizon = evidence.maxOf { it.completedAt }
        val denseSolver = NonDynamicDenseReferenceSolver(NonDynamicCapabilityV1.loadedHold)
        val sparseSolver = NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.loadedHold)
        val dense = denseSolver.fit(projection, horizon, CONFIG_CREATED_AT)
        val sparse = sparseSolver.fit(projection, horizon, CONFIG_CREATED_AT)

        val performedDuration = 30.0
        val performedResistance = exp(ln(42.0) + 0.018 * 5 - 0.045)
        val denseDemand = NBio7DPosteriorMath.setDemandFromLogFrontier(
            family = MetricFamily.LOADED_HOLD,
            logFrontierNodes = NBio7DCapabilityProjection.loadedHoldLogFrontier(dense, performedDuration),
            logObservedPerformance = ln(performedResistance),
            inheritedSupport = SetDemandStructuralSupport.RESOLVED,
        )
        val sparseDemand = NBio7DPosteriorMath.setDemandFromLogFrontier(
            family = MetricFamily.LOADED_HOLD,
            logFrontierNodes = NBio7DCapabilityProjection.loadedHoldLogFrontier(sparse, performedDuration),
            logObservedPerformance = ln(performedResistance),
            inheritedSupport = SetDemandStructuralSupport.RESOLVED,
        )
        val exposure = MuscleExposure("segment", "bilateral", 0.7, "recruitment:v1")
        val denseDose = NBio7DPosteriorMath.effectiveDose(exposure, denseDemand)
        val sparseDose = NBio7DPosteriorMath.effectiveDose(exposure, sparseDemand)
        val dg = requireNotNull(denseDemand.frontierGapSummary)
        val sg = requireNotNull(sparseDemand.frontierGapSummary)

        // Descriptive downstream guardrails only: 7C's model/solver remains frozen under PD-001.
        assertTrue(abs(dg.credibleLower05 - sg.credibleLower05) <= 0.12)
        assertTrue(abs(dg.estimateMedian - sg.estimateMedian) <= 0.08)
        assertTrue(abs(dg.credibleUpper95 - sg.credibleUpper95) <= 0.12)
        assertTrue(
            abs(
                requireNotNull(denseDemand.probabilityAtOrWithinDelta) -
                    requireNotNull(sparseDemand.probabilityAtOrWithinDelta),
            ) <= 0.30,
        )
        assertTrue(denseDose.exposure == sparseDose.exposure)
        assertTrue(denseDose.exposure.conservativeExposure == 0.7)
        assertTrue(denseDemand.empiricalStatuses.contains(SetDemandDoseEmpiricalStatus.EMPIRICAL_ACCURACY_PENDING))
        assertTrue(denseDemand.empiricalStatuses.contains(SetDemandDoseEmpiricalStatus.EMPIRICAL_CALIBRATION_PENDING))
    }

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T10:00:00Z")
        private val CONFIG_CREATED_AT = Instant.parse("2026-08-31T00:00:00Z")
        private val PROFILE = NonDynamicProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("7d-loaded-hold:v1"),
            executionProfileId = ExecutionProfileId("7d-loaded-hold"),
            metricFamily = MetricFamily.LOADED_HOLD,
            resistanceModel = ResistanceModel(
                modelVersion = "fixture-resistance-v1",
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
