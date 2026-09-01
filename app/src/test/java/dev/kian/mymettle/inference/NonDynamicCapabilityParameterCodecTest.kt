package dev.kian.mymettle.inference

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.NonDynamicProfileSemantics
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.performance.NonDynamicAdaptiveSparseSolver
import dev.kian.mymettle.engine.performance.NonDynamicCapabilityEvidenceProjector
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NonDynamicCapabilityParameterCodecTest {
    @Test
    fun `codec round trip preserves scientific joint state and exact solver identity`() {
        val fit = fitDurationHistory()
        val encoded = NonDynamicCapabilityParameterCodec.encode(fit)
        val decoded = NonDynamicCapabilityParameterCodec.decode(
            parameterSchemaVersion = NonDynamicCapabilityParameterCodec.SCHEMA_VERSION,
            encodedParameters = encoded,
            frontierAtReference = fit.frontierAtReference,
            executionProfileVersionId = fit.executionProfileVersionId,
            side = fit.side,
            modelConfigId = fit.modelConfigId,
        )
        assertTrue(NonDynamicCapabilityParameterCodec.scientificallyEquivalent(fit, decoded))
        assertEquals(fit.mathematicalModelIdentity, decoded.mathematicalModelIdentity)
        assertEquals(fit.solverDiagnostics.solverIdentity, decoded.solverDiagnostics.solverIdentity)
        assertEquals(fit.posteriorNodes, decoded.posteriorNodes)
    }

    @Test
    fun `different operational telemetry remains replay equivalent`() {
        val fit = fitDurationHistory()
        val telemetryChanged = fit.copy(
            solverDiagnostics = fit.solverDiagnostics.copy(
                evaluatedNodeCount = 999_999,
                effectiveNodeCount = 73.0,
                updateRuntimeNanos = 8_888_888,
                peakWorkingBytes = 777_777_777,
                notes = setOf("different_worker", "different_device_telemetry"),
            ),
        )
        assertNotEquals(NonDynamicCapabilityParameterCodec.encode(fit), NonDynamicCapabilityParameterCodec.encode(telemetryChanged))
        assertTrue(NonDynamicCapabilityParameterCodec.scientificallyEquivalent(fit, telemetryChanged))
    }

    @Test
    fun `posterior config and solver mismatches are not scientifically equivalent`() {
        val fit = fitDurationHistory()
        val posteriorMismatch = fit.copy(
            posteriorNodes = fit.posteriorNodes.mapIndexed { index, node ->
                if (index == 0) node.copy(trajectory = node.trajectory + 0.001) else node
            },
        )
        assertFalse(NonDynamicCapabilityParameterCodec.scientificallyEquivalent(fit, posteriorMismatch))

        val solverMismatch = fit.copy(
            solverDiagnostics = fit.solverDiagnostics.copy(
                solverIdentity = fit.solverDiagnostics.solverIdentity.copy(semanticVersion = "different-7c-solver"),
            ),
        )
        assertFalse(NonDynamicCapabilityParameterCodec.scientificallyEquivalent(fit, solverMismatch))

        val configMismatch = fit.copy(modelConfigId = dev.kian.mymettle.domain.inference.ModelConfigId("different-config"))
        assertFalse(NonDynamicCapabilityParameterCodec.scientificallyEquivalent(fit, configMismatch))
    }

    @Test
    fun `unknown future codec fails closed`() {
        val fit = fitDurationHistory()
        assertFailsWith<IllegalArgumentException> {
            NonDynamicCapabilityParameterCodec.decode(
                parameterSchemaVersion = 99,
                encodedParameters = NonDynamicCapabilityParameterCodec.encode(fit),
                frontierAtReference = fit.frontierAtReference,
                executionProfileVersionId = fit.executionProfileVersionId,
                side = fit.side,
                modelConfigId = fit.modelConfigId,
            )
        }
    }

    private fun fitDurationHistory(): dev.kian.mymettle.domain.inference.NonDynamicCapabilityFit {
        val profile = NonDynamicProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("epv_duration_codec"),
            executionProfileId = ExecutionProfileId("ep_duration_codec"),
            metricFamily = MetricFamily.DURATION_ONLY,
            resistanceModel = ResistanceModel("none-v1", ResistanceSemantics.NONE, 0.0, 0.0, 0.0),
            entryBasis = EntryBasis.TOTAL,
            lateralityMode = LateralityMode.UNKNOWN,
        )
        val evidence = List(5) { session ->
            CompletedSetEvidence(
                setRecordId = "set_$session",
                observationId = "obs_$session",
                sessionExerciseId = "se_$session",
                executionProfileVersionId = profile.executionProfileVersionId,
                metricFamily = MetricFamily.DURATION_ONLY,
                laterality = Laterality.UNKNOWN,
                completedAt = BASE.plusSeconds(86_400L * session),
                metricValues = listOf(
                    PerformanceMetricValue(
                        PerformanceMetric.DURATION,
                        Quantity(30.0 + 5.0 * session, UnitId.SECOND),
                    ),
                ),
                bodyMassContextKg = null,
                warmUp = false,
                kind = "work",
                observationSource = "corrected_lite_import",
                sessionId = "session_$session",
            )
        }
        val projection = NonDynamicCapabilityEvidenceProjector.project(profile, Laterality.UNKNOWN, evidence)
        return NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.durationOnly).fit(
            projection,
            projection.evidence.maxOf { it.completedAt },
            CREATED_AT,
        )
    }

    companion object {
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val CREATED_AT = Instant.parse("2026-09-01T00:00:00Z")
    }
}
