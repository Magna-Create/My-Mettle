package dev.kian.mymettle.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitWarning
import dev.kian.mymettle.domain.inference.DynamicFrontierParameterPosterior
import dev.kian.mymettle.domain.inference.DynamicFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicObservationSlackPosterior
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicSlackPosteriorMass
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.EvidenceFamily
import dev.kian.mymettle.domain.inference.EvidenceSupport
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DynamicCapabilityParameterCodecTest {
    @Test
    fun `codec round trip preserves joint posterior slack and predictions`() {
        val fit = fit()
        val encoded = DynamicCapabilityParameterCodec.encode(fit)
        val decoded = DynamicCapabilityParameterCodec.decode(
            parameterSchemaVersion = DynamicCapabilityParameterCodec.SCHEMA_VERSION,
            encodedParameters = encoded,
            frontierAtReference = fit.frontierAtReference,
            executionProfileVersionId = fit.executionProfileVersionId,
            side = fit.side,
            modelConfigId = fit.modelConfigId,
        )
        assertEquals(fit.referenceRepetitions, decoded.referenceRepetitions)
        assertEquals(fit.slope, decoded.slope)
        assertEquals(fit.slackScale, decoded.slackScale)
        assertEquals(fit.noiseScale, decoded.noiseScale)
        assertEquals(fit.posteriorNodes, decoded.posteriorNodes)
        assertEquals(fit.observationSlack, decoded.observationSlack)
        assertEquals(fit.selectedObservationIds, decoded.selectedObservationIds)
        assertEquals(fit.selectedSessionIds, decoded.selectedSessionIds)
        val model = DynamicStochasticFrontierModel()
        listOf(5.0, 8.0, 12.0, 20.0).forEach { reps ->
            assertEquals(model.predictFrontier(fit, reps), model.predictFrontier(decoded, reps))
        }
    }

    @Test
    fun `unsupported schema and malformed payload fail closed`() {
        val fit = fit()
        val encoded = DynamicCapabilityParameterCodec.encode(fit)
        assertFailsWith<IllegalArgumentException> {
            DynamicCapabilityParameterCodec.decode(
                999,
                encoded,
                fit.frontierAtReference,
                fit.executionProfileVersionId,
                fit.side,
                fit.modelConfigId,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DynamicCapabilityParameterCodec.decode(
                DynamicCapabilityParameterCodec.SCHEMA_VERSION,
                "codec=not-supported",
                fit.frontierAtReference,
                fit.executionProfileVersionId,
                fit.side,
                fit.modelConfigId,
            )
        }
    }

    private fun fit(): DynamicStochasticFrontierFit {
        val model = DynamicStochasticFrontierModel()
        val config = model.config.toModelConfig(DynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
        val support = EvidenceSupport(
            observationCount = 2,
            effectiveIndependentSessionCount = 2,
            firstEvidenceAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastEvidenceAt = Instant.parse("2026-01-08T00:00:00Z"),
            evidenceFamily = EvidenceFamily("dynamic_resistance"),
        )
        val provenance = ModelOutputProvenance(config.id, null, null, support.lastEvidenceAt)
        val nodes = listOf(
            DynamicFrontierPosteriorNode(kotlin.math.ln(70.0), 0.14, 0.12, 0.05, 0.25),
            DynamicFrontierPosteriorNode(kotlin.math.ln(76.0), 0.18, 0.12, 0.05, 0.50),
            DynamicFrontierPosteriorNode(kotlin.math.ln(82.0), 0.22, 0.12, 0.05, 0.25),
        )
        fun parameter(p05: Double, p50: Double, p95: Double, variance: Double, unit: String) =
            DynamicFrontierParameterPosterior(
                PosteriorSummary(p05, p50, p95, variance),
                DynamicParameterIdentification.PRIOR_DOMINATED,
                unit,
            )
        fun slack(observationId: String, offset: Double) = DynamicObservationSlackPosterior(
            observationId = observationId,
            summary = PosteriorSummary(offset, offset + 0.05, offset + 0.10, 0.001),
            identification = DynamicParameterIdentification.PRIOR_DOMINATED,
            massPoints = listOf(
                DynamicSlackPosteriorMass(offset, 0.25),
                DynamicSlackPosteriorMass(offset + 0.05, 0.50),
                DynamicSlackPosteriorMass(offset + 0.10, 0.25),
            ),
        )
        return DynamicStochasticFrontierFit(
            executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
            side = Laterality.BILATERAL,
            inferenceHorizon = Instant.parse("2026-01-08T00:00:00Z"),
            referenceRepetitions = 8.0,
            modelConfigId = config.id,
            modelVersion = model.modelVersion,
            evidencePolicyIdentity = model.config.evidencePolicyIdentity,
            support = support,
            observedRepMin = 5,
            observedRepMax = 12,
            observedResistanceMinKg = 55.0,
            observedResistanceMaxKg = 82.0,
            frontierAtReference = PosteriorEstimate(
                PosteriorSummary(70.0, 76.0, 82.0, 20.0),
                support,
                provenance,
            ),
            slope = parameter(0.10, 0.18, 0.26, 0.002, "positive log-resistance per log-repetition ratio"),
            slackScale = parameter(0.08, 0.12, 0.18, 0.001, "log-performance HalfNormal scale"),
            noiseScale = parameter(0.03, 0.05, 0.08, 0.0002, "log-performance Student-t scale"),
            observationSlack = listOf(slack("obs1", 0.0), slack("obs2", 0.02)),
            selectedObservationIds = listOf("obs1", "obs2"),
            selectedSessionIds = listOf("s1", "s2"),
            approximationVersion = "tensor-grid-midpoint-slack-quadrature-v1",
            warnings = setOf(DynamicCapabilityFitWarning.APPROXIMATE_POSTERIOR),
            posteriorNodes = nodes,
        )
    }
}
