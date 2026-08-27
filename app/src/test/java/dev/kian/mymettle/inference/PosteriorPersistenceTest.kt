package dev.kian.mymettle.inference

import dev.kian.mymettle.domain.inference.EvidenceFamily
import dev.kian.mymettle.domain.inference.EvidenceSupport
import dev.kian.mymettle.domain.inference.InferenceRunId
import dev.kian.mymettle.domain.inference.ModelConfigId
import dev.kian.mymettle.domain.inference.ModelManifestId
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PosteriorPersistenceTest {
    @Test
    fun `posterior and support mapping is deterministic and lossless`() {
        val value = PosteriorEstimate(
            summary = PosteriorSummary(40.0, 50.0, 65.0, 36.0),
            support = EvidenceSupport(
                observationCount = 5,
                effectiveIndependentSessionCount = 2,
                firstEvidenceAt = Instant.parse("2026-08-01T10:00:00Z"),
                lastEvidenceAt = Instant.parse("2026-08-20T10:00:00Z"),
                evidenceFamily = EvidenceFamily("dynamic_resistance"),
            ),
            provenance = provenance(),
        )
        val columns = value.toPosteriorColumns()
        assertEquals(columns, value.toPosteriorColumns())
        assertEquals(value, columns.toPosteriorEstimate(value.provenance))
    }

    @Test
    fun `unknown posterior survives Room column mapping without fake precision`() {
        val value = PosteriorEstimate(
            summary = null,
            support = EvidenceSupport(0, 0, null, null, EvidenceFamily("duration_only")),
            provenance = provenance(),
        )
        val columns = value.toPosteriorColumns()
        assertNull(columns.p05)
        assertNull(columns.p50)
        assertNull(columns.p95)
        assertNull(columns.variance)
        assertNull(columns.toPosteriorEstimate(value.provenance).summary)
    }

    private fun provenance() = ModelOutputProvenance(
        modelConfigId = ModelConfigId("model_config_test"),
        modelManifestId = ModelManifestId("manifest_test"),
        inferenceRunId = InferenceRunId("run_test"),
        evidenceThrough = Instant.parse("2026-08-20T10:00:00Z"),
    )
}
