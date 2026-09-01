package dev.kian.mymettle.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicFrontierParameterPosterior
import dev.kian.mymettle.domain.inference.DynamicObservationSlackPosterior
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicSlackPosteriorMass
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.inference.EvidenceFamily
import dev.kian.mymettle.domain.inference.EvidenceSupport
import dev.kian.mymettle.domain.inference.InferenceComputeBackend
import dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation
import dev.kian.mymettle.domain.inference.InferenceSolverDiagnostics
import dev.kian.mymettle.domain.inference.InferenceSolverFamily
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
import dev.kian.mymettle.domain.inference.ModelConfigId
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.performance.Laterality
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DynamicTrendCapabilityParameterCodecTest {
    @Test
    fun `solver aware Candidate v2 state round trips deterministically`() {
        val fit = fixture()
        val encoded = DynamicTrendCapabilityParameterCodec.encode(fit)
        val decoded = DynamicTrendCapabilityParameterCodec.decode(
            parameterSchemaVersion = DynamicTrendCapabilityParameterCodec.SCHEMA_VERSION,
            encodedParameters = encoded,
            frontierAtLatestSession = fit.frontierAtLatestSession,
            executionProfileVersionId = fit.executionProfileVersionId,
            side = fit.side,
            modelConfigId = fit.modelConfigId,
        )
        assertEquals(fit, decoded)
        assertEquals(encoded, DynamicTrendCapabilityParameterCodec.encode(decoded))
    }

    @Test
    fun `scientific replay equality ignores runtime and operational telemetry`() {
        val fit = fixture()
        val replay = fit.copy(
            solverDiagnostics = fit.solverDiagnostics.copy(
                evaluatedNodeCount = 999_999,
                effectiveNodeCount = 77.0,
                updateRuntimeNanos = 9_999_999,
                peakWorkingBytes = 999_999_999,
                notes = setOf("different_worker_count", "different_hardware_sample"),
            ),
        )
        assertNotEquals(DynamicTrendCapabilityParameterCodec.encode(fit), DynamicTrendCapabilityParameterCodec.encode(replay))
        assertTrue(DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, replay))
    }

    @Test
    fun `scientific replay equality rejects posterior config and solver mismatches`() {
        val fit = fixture()
        val posteriorMismatch = fit.copy(
            posteriorNodes = fit.posteriorNodes.map { it.copy(slope = it.slope + 0.01) },
        )
        assertFalse(DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, posteriorMismatch))

        val otherConfig = ModelConfigId("candidate-v2-codec-test-other")
        val configMismatch = fit.copy(
            modelConfigId = otherConfig,
            frontierAtLatestSession = fit.frontierAtLatestSession.copy(
                provenance = fit.frontierAtLatestSession.provenance.copy(modelConfigId = otherConfig),
            ),
        )
        assertFalse(DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, configMismatch))

        val solverMismatch = fit.copy(
            solverDiagnostics = fit.solverDiagnostics.copy(
                solverIdentity = fit.solverDiagnostics.solverIdentity.copy(semanticVersion = "codec-test-solver-v2"),
            ),
        )
        assertFalse(DynamicTrendCapabilityParameterCodec.scientificallyEquivalent(fit, solverMismatch))
    }

    @Test
    fun `unknown Candidate v2 parameter schema fails closed`() {
        val fit = fixture()
        assertFailsWith<IllegalArgumentException> {
            DynamicTrendCapabilityParameterCodec.decode(
                parameterSchemaVersion = 99,
                encodedParameters = DynamicTrendCapabilityParameterCodec.encode(fit),
                frontierAtLatestSession = fit.frontierAtLatestSession,
                executionProfileVersionId = fit.executionProfileVersionId,
                side = fit.side,
                modelConfigId = fit.modelConfigId,
            )
        }
    }

    private fun fixture(): DynamicTrendFrontierFit {
        val configId = ModelConfigId("candidate-v2-codec-test")
        val support = EvidenceSupport(
            observationCount = 1,
            effectiveIndependentSessionCount = 1,
            firstEvidenceAt = TIME,
            lastEvidenceAt = TIME,
            evidenceFamily = EvidenceFamily("dynamic_resistance"),
        )
        val frontierSummary = PosteriorSummary(72.0, 80.0, 91.0, 24.0)
        val provenance = ModelOutputProvenance(configId, null, null, TIME)
        val parameter = DynamicFrontierParameterPosterior(
            summary = PosteriorSummary(0.10, 0.16, 0.25, 0.0025),
            identification = DynamicParameterIdentification.PRIOR_DOMINATED,
            semanticUnit = "test",
        )
        val slack = DynamicObservationSlackPosterior(
            observationId = "obs-1",
            summary = PosteriorSummary(0.01, 0.05, 0.12, 0.001),
            identification = DynamicParameterIdentification.PRIOR_DOMINATED,
            massPoints = listOf(
                DynamicSlackPosteriorMass(0.01, 0.4),
                DynamicSlackPosteriorMass(0.08, 0.6),
            ),
        )
        val solver = InferenceSolverIdentity(
            solverFamily = InferenceSolverFamily.DENSE_TENSOR_REFERENCE,
            semanticVersion = "codec-test-solver-v1",
            computeBackend = InferenceComputeBackend.KOTLIN_JVM,
            deterministicReplay = true,
            approximationDefinition = "test dense posterior",
        )
        return DynamicTrendFrontierFit(
            executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
            side = Laterality.BILATERAL,
            inferenceHorizon = TIME,
            referenceRepetitions = 8.0,
            modelConfigId = configId,
            modelVersion = DynamicTrendFrontierV2.MODEL_VERSION,
            evidencePolicyIdentity = "test-evidence",
            support = support,
            observedRepMin = 8,
            observedRepMax = 8,
            observedResistanceMinKg = 75.0,
            observedResistanceMaxKg = 75.0,
            frontierAtLatestSession = PosteriorEstimate(frontierSummary, support, provenance),
            slope = parameter,
            frontierTrend = parameter.copy(
                summary = PosteriorSummary(-0.04, 0.01, 0.06, 0.0008),
                semanticUnit = "log resistance per session",
            ),
            slackScale = parameter.copy(semanticUnit = "log resistance"),
            noiseScale = parameter.copy(semanticUnit = "log resistance"),
            observationSlack = listOf(slack),
            selectedObservationIds = listOf("obs-1"),
            selectedSessionIds = listOf("session-1"),
            approximationVersion = "codec-test-approx-v1",
            laplaceValidBasePosteriorMass = null,
            laplaceFiniteDifferenceStep = null,
            posteriorEffectiveNodeCount = 1.0,
            warnings = setOf("test-warning"),
            posteriorNodes = listOf(
                DynamicTrendFrontierPosteriorNode(
                    logFrontierAtLatestSession = kotlin.math.ln(80.0),
                    slope = 0.16,
                    frontierTrend = 0.01,
                    slackScale = 0.12,
                    noiseScale = 0.05,
                    posteriorWeight = 1.0,
                ),
            ),
            mathematicalModelIdentity = DynamicTrendFrontierV2.mathematicalModelIdentity,
            solverDiagnostics = InferenceSolverDiagnostics(
                solverIdentity = solver,
                posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES,
                evaluatedNodeCount = 1,
                effectiveNodeCount = 1.0,
                updateRuntimeNanos = 1234,
                peakWorkingBytes = 4096,
                notes = setOf("test"),
            ),
        )
    }

    companion object {
        private val TIME = Instant.parse("2026-08-31T00:00:00Z")
    }
}
