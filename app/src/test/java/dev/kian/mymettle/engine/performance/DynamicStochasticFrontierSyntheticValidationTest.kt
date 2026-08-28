package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV1Contract
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.DynamicResistanceRetrospectiveEvaluator
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Frozen-candidate latent-truth acceptance fixtures for 7B.3/4.
 * These are evaluation fixtures, not a tuning loop: changing 7B.2 mathematics to satisfy them would
 * require a new candidate identity and a fresh protocol.
 */
class DynamicStochasticFrontierSyntheticValidationTest {
    @Test
    fun `coarse latent truth coverage survives multiple slack and heavy tail regimes`() {
        val scenarios = listOf(
            Scenario(72.0, 0.14, listOf(0.01, 0.03, 0.06, 0.10), listOf(0.0, 0.01, -0.01, 0.02, -0.02)),
            Scenario(82.0, 0.22, listOf(0.02, 0.07, 0.12, 0.04, 0.16), listOf(0.01, -0.02, 0.03, 0.0, -0.01)),
            Scenario(95.0, 0.28, listOf(0.03, 0.10, 0.18, 0.06, 0.22), listOf(0.0, 0.02, -0.03, 0.01, -0.02)),
            // Deliberate heavy-tailed ordinary-performance shocks; df remains the frozen Student-t(5).
            Scenario(65.0, 0.12, listOf(0.01, 0.05, 0.09, 0.14), listOf(0.0, 0.02, -0.02, 0.20, -0.18, 0.01)),
        )
        var frontierCovered = 0
        var slopeCovered = 0
        scenarios.forEachIndexed { index, scenario ->
            val fit = fit(generate(scenario, sessionOffset = index * 20))
            assertEquals(8.0, fit.referenceRepetitions)
            val frontier = fit.frontierAtReference.summary!!
            if (scenario.frontierKg in frontier.p05..frontier.p95) frontierCovered += 1
            if (scenario.slope in fit.slope.summary.p05..fit.slope.summary.p95) slopeCovered += 1
            assertTrue(frontier.p05 > 0.0 && frontier.p95.isFinite())
        }
        assertTrue(frontierCovered >= 3, "90% frontier intervals covered only $frontierCovered/${scenarios.size} fixed regimes")
        assertTrue(slopeCovered >= 3, "90% slope intervals covered only $slopeCovered/${scenarios.size} fixed regimes")
    }

    @Test
    fun `held out demonstration predictive remains calibrated under varying slack and heavy tail shocks`() {
        val scenario = Scenario(
            frontierKg = 84.0,
            slope = 0.20,
            slack = listOf(0.01, 0.04, 0.08, 0.14, 0.20, 0.03, 0.11),
            noise = listOf(0.0, 0.02, -0.03, 0.16, -0.14, 0.01, -0.01, 0.04),
        )
        val projection = projection(generate(scenario, sessions = 10))
        val evaluator = DynamicResistanceRetrospectiveEvaluator()
        val summary = evaluator.evaluate(projection)
        assertEquals(0, summary.modelFailureCount)
        assertTrue(summary.evaluableCount >= 24)
        val coverage = assertNotNull(summary.candidatePredictiveCoverage)
        assertTrue(coverage >= 0.60, "Held-out 90% predictive coverage was $coverage")
        val pitError = assertNotNull(summary.candidatePitCalibration.meanAbsoluteBinError)
        assertTrue(pitError <= 0.25, "Coarse PIT mean absolute bin error was $pitError")
        val catastrophic = assertNotNull(summary.catastrophicFrontierContradictionRate)
        assertTrue(catastrophic <= 0.20, "Confidently-too-low frontier contradiction rate was $catastrophic")
        assertTrue(summary.meanCandidateLogPredictiveDensity?.isFinite() == true)
    }

    private fun fit(evidence: List<DynamicResistanceEvidence>) = DynamicStochasticFrontierModel().let { model ->
        model.fit(
            DynamicCapabilityFitRequest(
                projection = projection(evidence),
                inferenceHorizon = BASE.plusSeconds(10_000L * DAY_SECONDS),
                modelConfig = model.config.toModelConfig(Instant.parse("2026-08-27T00:00:00Z")),
            ),
        )
    }

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

    private fun generate(
        scenario: Scenario,
        sessions: Int = 12,
        sessionOffset: Int = 0,
    ): List<DynamicResistanceEvidence> {
        val reps = listOf(4, 6, 8, 12, 16)
        return buildList {
            repeat(sessions) { sessionIndex ->
                val absoluteSession = sessionOffset + sessionIndex
                reps.forEachIndexed { repIndex, rep ->
                    val slack = scenario.slack[(sessionIndex + repIndex) % scenario.slack.size]
                    val noise = scenario.noise[(sessionIndex * 2 + repIndex) % scenario.noise.size]
                    val resistance = exp(
                        ln(scenario.frontierKg) - scenario.slope * ln(rep / 8.0) - slack + noise,
                    )
                    add(set("${absoluteSession}_$repIndex", "session_$absoluteSession", rep, resistance, absoluteSession, repIndex))
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
        ordinal: Int,
    ): DynamicResistanceEvidence = DynamicResistanceEvidence(
        observationId = "synthetic_obs_$id",
        setRecordId = "synthetic_set_$id",
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
            resistanceModelVersion = "synthetic-resistance-v1",
            resolverVersion = DynamicResistanceV1Contract.RESISTANCE_RESOLVER_VERSION,
        ),
        metricEvidence = listOf(
            DynamicMetricEvidenceAudit(
                PerformanceMetric.EXTERNAL_LOAD,
                Quantity(resistanceKg, UnitId.KILOGRAM),
                Quantity(resistanceKg, UnitId.KILOGRAM),
                "synthetic_test",
                "summary",
            ),
            DynamicMetricEvidenceAudit(
                PerformanceMetric.REPETITIONS,
                Quantity(reps.toDouble(), UnitId.REPETITION),
                Quantity(reps.toDouble(), UnitId.REPETITION),
                "synthetic_test",
                "summary",
            ),
        ),
        warmUp = false,
        setKind = "working",
        evidencePolicyIdentity = DynamicResistanceV1Contract.evidencePolicy.identity,
    )

    private data class Scenario(
        val frontierKg: Double,
        val slope: Double,
        val slack: List<Double>,
        val noise: List<Double>,
    )

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val PROFILE = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("synthetic-acceptance-profile:v1"),
            executionProfileId = ExecutionProfileId("synthetic-acceptance-profile"),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel(
                modelVersion = "synthetic-resistance-v1",
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
