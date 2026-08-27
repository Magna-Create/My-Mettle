package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.performance.MetricFamily
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PosteriorFoundationTest {
    @Test
    fun `posterior interval accepts ordered finite values`() {
        val posterior = PosteriorSummary(10.0, 20.0, 40.0, 64.0)
        assertTrue(posterior.p05 <= posterior.p50)
        assertTrue(posterior.p50 <= posterior.p95)
    }

    @Test
    fun `posterior rejects invalid ordering`() {
        assertFailsWith<IllegalArgumentException> { PosteriorSummary(20.0, 10.0, 40.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { PosteriorSummary(10.0, 40.0, 20.0, 1.0) }
    }

    @Test
    fun `posterior rejects negative variance and non finite values`() {
        assertFailsWith<IllegalArgumentException> { PosteriorSummary(10.0, 20.0, 30.0, -0.1) }
        assertFailsWith<IllegalArgumentException> { PosteriorSummary(Double.NaN, 20.0, 30.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { PosteriorSummary(10.0, Double.POSITIVE_INFINITY, 30.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { PosteriorSummary(10.0, 20.0, 30.0, Double.NaN) }
    }

    @Test
    fun `broad posterior uncertainty is preserved without narrowing`() {
        val posterior = PosteriorSummary(-100.0, 0.0, 100.0, 2_500.0)
        assertEquals(-100.0, posterior.credibleLower05)
        assertEquals(100.0, posterior.credibleUpper95)
        assertEquals(2_500.0, posterior.posteriorVariance)
    }

    @Test
    fun `unknown posterior remains representable with provenance`() {
        val config = config(mapOf("state" to "not_implemented"))
        val estimate = PosteriorEstimate(
            summary = null,
            support = EvidenceSupport(0, 0, null, null, EvidenceFamily("dynamic_resistance")),
            provenance = ModelOutputProvenance(config.id, null, null, null),
        )
        assertFalse(estimate.isKnown)
        assertNull(estimate.summary)
    }

    @Test
    fun `observation count remains distinct from independent session count`() {
        val family = EvidenceFamily.fromMetricFamily(MetricFamily.DYNAMIC_RESISTANCE)
        val support = EvidenceSupport.fromObservations(
            family,
            listOf(
                evidence("o1", "session_a", "2026-08-01T10:00:00Z"),
                evidence("o2", "session_a", "2026-08-01T10:05:00Z"),
                evidence("o3", "session_b", "2026-08-05T10:00:00Z"),
                evidence("o3", "session_b", "2026-08-05T10:00:00Z"),
            ),
        )
        assertEquals(3, support.observationCount)
        assertEquals(2, support.effectiveIndependentSessionCount)
    }

    @Test
    fun `identical model config has deterministic identity irrespective of map order`() {
        val first = config(linkedMapOf("beta" to "2", "alpha" to "1"))
        val second = config(linkedMapOf("alpha" to "1", "beta" to "2"))
        assertEquals(first.id, second.id)
        assertEquals(first.canonicalConfigPayload, second.canonicalConfigPayload)
    }

    @Test
    fun `behaviour driving config change changes identity`() {
        val first = config(mapOf("workingSetConfidence" to "0.40"))
        val second = config(mapOf("workingSetConfidence" to "0.41"))
        assertTrue(first.id != second.id)
    }

    @Test
    fun `historical identity cannot be restored against mutated payload`() {
        val original = config(mapOf("workingSetConfidence" to "0.40"))
        assertFailsWith<IllegalArgumentException> {
            ModelConfigDefinition.restore(
                id = original.id,
                component = original.component,
                modelFamily = original.modelFamily,
                modelName = original.modelName,
                semanticVersion = original.semanticVersion,
                configSchemaVersion = original.configSchemaVersion,
                canonicalConfigPayload = "workingSetConfidence=0.41",
                createdAt = original.createdAt,
                effectiveAt = original.effectiveAt,
            )
        }
    }

    @Test
    fun `manifest identity is deterministic and can enforce required components`() {
        val configs = REQUIRED_NBIO7_COMPONENTS.associateWith { component ->
            ModelConfigDefinition.create(
                component = component,
                modelFamily = "n-bio-7a",
                modelName = "${component.storageValue}-foundation",
                semanticVersion = "7a.0.0",
                configSchemaVersion = 1,
                parameters = mapOf("implementationState" to "not_implemented"),
                createdAt = CREATED,
            ).id
        }
        val manifest = ModelManifest.create(configs).requireComponents(REQUIRED_NBIO7_COMPONENTS)
        val reordered = ModelManifest.create(configs.entries.reversed().associate { it.toPair() })
        assertEquals(manifest.id, reordered.id)
    }

    private fun config(parameters: Map<String, String>): ModelConfigDefinition = ModelConfigDefinition.create(
        component = InferenceModelComponent.EXPOSURE,
        modelFamily = "working_set_exposure",
        modelName = "weighted-working-set",
        semanticVersion = "0.0.1",
        configSchemaVersion = 1,
        parameters = parameters,
        createdAt = CREATED,
    )

    private fun evidence(id: String, session: String, at: String) = EvidenceSupportObservation(
        observationId = id,
        sessionId = session,
        observedAt = Instant.parse(at),
    )

    private companion object {
        val CREATED: Instant = Instant.parse("2026-08-27T00:00:00Z")
    }
}
