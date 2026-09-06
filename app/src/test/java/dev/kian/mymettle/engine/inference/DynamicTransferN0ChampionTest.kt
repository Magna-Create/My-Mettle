package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.inference.InferenceModelComponent
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
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DynamicTransferN0ChampionTest {
    @Test
    fun `n0 binds destination-only fit to selected candidate-v2 adaptive sparse identity`() {
        val evidence = generated(sessions = 4, trend = 0.025)
        val projection = projection(evidence)
        val champion = DynamicTransferN0Champion()
        val fit = champion.fit(
            destinationProjection = projection,
            inferenceHorizon = evidence.maxOf { it.completedAt },
            configCreatedAt = CONFIG_CREATED_AT,
        )

        assertEquals(NBio7FN0V1.ROLE_IDENTITY, fit.roleIdentity)
        assertEquals(DynamicTrendFrontierV2.MODEL_VERSION, fit.destinationFit.modelVersion)
        assertEquals(NBio7FN0V1.mathematicalModelIdentity, fit.destinationFit.mathematicalModelIdentity)
        assertEquals(NBio7FN0V1.solverIdentity, fit.destinationFit.solverDiagnostics.solverIdentity)
        assertEquals(champion.modelConfig(CONFIG_CREATED_AT).id, fit.destinationFit.modelConfigId)
        assertEquals(evidence.map { it.observationId }.toSet(), fit.destinationFit.selectedObservationIds.toSet())
        assertEquals(evidence.map { it.sessionId }.toSet(), fit.destinationFit.selectedSessionIds.toSet())
        assertEquals(InferenceModelComponent.DYNAMIC_CAPABILITY, champion.modelConfig(CONFIG_CREATED_AT).component)

        val next = champion.projectToNextSession(fit)
        assertEquals(fit.destinationFit.executionProfileVersionId, next.executionProfileVersionId)
        assertEquals(fit.destinationFit.side, next.side)
        assertEquals(fit.destinationFit.referenceRepetitions, next.referenceRepetitions)
        assertTrue(requireNotNull(next.frontierAtReference.summary).median > 0.0)
    }

    @Test
    fun `n0 wrapper rejects a fit produced by another solver identity`() {
        val evidence = generated(sessions = 4, trend = -0.01)
        val projection = projection(evidence)
        val champion = DynamicTransferN0Champion()
        val fit = champion.fit(
            destinationProjection = projection,
            inferenceHorizon = evidence.maxOf { it.completedAt },
            configCreatedAt = CONFIG_CREATED_AT,
        )
        val wrongSolverFit = fit.destinationFit.copy(
            solverDiagnostics = fit.destinationFit.solverDiagnostics.copy(
                solverIdentity = DynamicTrendFrontierV2.conditionalLaplaceSolverIdentity,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            DynamicTransferN0Fit(wrongSolverFit)
        }
    }

    private fun generated(sessions: Int, trend: Double): List<DynamicResistanceEvidence> = buildList {
        repeat(sessions) { session ->
            listOf(6, 8, 12).forEachIndexed { ordinal, reps ->
                val logFrontierAtEight = ln(78.0) + trend * session
                val resistance = exp(logFrontierAtEight - 0.17 * ln(reps / 8.0) - 0.03)
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
                resistanceModelVersion = "n0-test-resistance-v1",
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
        private val CONFIG_CREATED_AT = Instant.parse("2026-09-06T00:00:00Z")
        private val PROFILE = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("n0-test-version"),
            executionProfileId = ExecutionProfileId("n0-test-profile"),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel(
                modelVersion = "n0-test-resistance-v1",
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
