package dev.kian.mymettle.engine.inference

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
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DynamicTrendPosteriorFidelityTest {
    @Test
    fun `identical posterior has zero marginal covariance tail and forecast error`() {
        val fit = fixture("reference", listOf(-0.02, 0.04))
        val result = DynamicTrendPosteriorFidelity.compare(fit, fit)
        assertEquals(0.0, result.trendPositiveProbabilityAbsoluteError, 0.0)
        assertEquals(0.0, result.nextFrontierMedianRelativeError, 0.0)
        assertEquals(0.0, result.maxStandardisedMarginalWasserstein1, 0.0)
        assertEquals(0.0, result.maxCovarianceCorrelationScaleError ?: 0.0, 0.0)
    }

    @Test
    fun `non finite challenger forecast fails fidelity comparison closed`() {
        val reference = fixture("reference", listOf(-0.02, 0.04))
        val pathological = fixture("pathological", listOf(800.0, 810.0))
        assertFailsWith<IllegalArgumentException> {
            DynamicTrendPosteriorFidelity.compare(reference, pathological)
        }
    }

    @Test
    fun `trend distortion is visible in tails quantiles dependence and next frontier`() {
        val reference = fixture("reference", listOf(-0.02, 0.04))
        val distorted = fixture("challenger", listOf(0.03, 0.08))
        val result = DynamicTrendPosteriorFidelity.compare(reference, distorted)
        assertTrue(result.trendPositiveProbabilityAbsoluteError > 0.0)
        assertTrue(result.nextFrontierMedianRelativeError > 0.0)
        assertTrue(result.maxStandardisedMarginalWasserstein1 > 0.0)
        assertTrue(result.marginals.first { it.parameter == "frontierTrend" }.quantileWasserstein1 > 0.0)
    }

    private fun fixture(solverVersion: String, trends: List<Double>): DynamicTrendFrontierFit {
        val configId = ModelConfigId("fidelity-$solverVersion")
        val support = EvidenceSupport(2, 2, TIME, TIME, EvidenceFamily("dynamic_resistance"))
        val provenance = ModelOutputProvenance(configId, null, null, TIME)
        val parameter = DynamicFrontierParameterPosterior(
            PosteriorSummary(0.10, 0.16, 0.25, 0.0025),
            DynamicParameterIdentification.PARTIALLY_LEARNED,
            "test",
        )
        val slack = listOf("obs-1", "obs-2").map { id ->
            DynamicObservationSlackPosterior(
                id,
                PosteriorSummary(0.01, 0.05, 0.10, 0.001),
                DynamicParameterIdentification.PARTIALLY_LEARNED,
                listOf(DynamicSlackPosteriorMass(0.02, 0.5), DynamicSlackPosteriorMass(0.08, 0.5)),
            )
        }
        val nodes = listOf(
            DynamicTrendFrontierPosteriorNode(ln(78.0), 0.14, trends[0], 0.10, 0.04, 0.45),
            DynamicTrendFrontierPosteriorNode(ln(84.0), 0.20, trends[1], 0.14, 0.06, 0.55),
        )
        val frontierValues = listOf(78.0 to 0.45, 84.0 to 0.55)
        val mean = frontierValues.sumOf { it.first * it.second }
        val variance = frontierValues.sumOf { it.second * (it.first - mean) * (it.first - mean) }
        val solver = InferenceSolverIdentity(
            InferenceSolverFamily.DENSE_TENSOR_REFERENCE,
            solverVersion,
            InferenceComputeBackend.KOTLIN_JVM,
            true,
            "fixture",
        )
        return DynamicTrendFrontierFit(
            executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
            side = Laterality.BILATERAL,
            inferenceHorizon = TIME,
            referenceRepetitions = 8.0,
            modelConfigId = configId,
            modelVersion = DynamicTrendFrontierV2.MODEL_VERSION,
            evidencePolicyIdentity = "test",
            support = support,
            observedRepMin = 6,
            observedRepMax = 12,
            observedResistanceMinKg = 70.0,
            observedResistanceMaxKg = 84.0,
            frontierAtLatestSession = PosteriorEstimate(
                PosteriorSummary(78.0, 84.0, 84.0, variance), support, provenance,
            ),
            slope = parameter,
            frontierTrend = parameter.copy(
                summary = PosteriorSummary(trends.min(), trends.sorted()[1], trends.max(), 0.001),
                semanticUnit = "log resistance per session",
            ),
            slackScale = parameter.copy(semanticUnit = "log resistance"),
            noiseScale = parameter.copy(semanticUnit = "log resistance"),
            observationSlack = slack,
            selectedObservationIds = listOf("obs-1", "obs-2"),
            selectedSessionIds = listOf("s1", "s2"),
            approximationVersion = "fixture",
            laplaceValidBasePosteriorMass = null,
            laplaceFiniteDifferenceStep = null,
            posteriorEffectiveNodeCount = 1.98,
            warnings = emptySet(),
            posteriorNodes = nodes,
            mathematicalModelIdentity = DynamicTrendFrontierV2.mathematicalModelIdentity,
            solverDiagnostics = InferenceSolverDiagnostics(
                solver,
                InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES,
                evaluatedNodeCount = 2,
                effectiveNodeCount = 1.98,
            ),
        )
    }

    companion object {
        private val TIME = Instant.parse("2026-08-31T00:00:00Z")
    }
}
