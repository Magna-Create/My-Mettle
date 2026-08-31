package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicResistanceV3Contract
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicHistoricalAvailabilityV3Test {
    @Test
    fun `corrected Lite import uses historical source session finalisation`() {
        val sourceCompletedAt = Instant.parse("2026-08-16T12:22:26.229Z")
        val sourceEditedAt = Instant.parse("2026-08-16T12:10:12.434Z")
        val nativeRecordedAt = Instant.parse("2026-08-31T12:57:00Z")

        assertEquals(
            sourceCompletedAt,
            DynamicHistoricalAvailabilityV3.resolve(
                observationSource = DynamicResistanceV3Contract.CORRECTED_LEGACY_UNSIDED_SOURCE,
                nativeRecordedAt = nativeRecordedAt,
                sessionCompletedAt = sourceCompletedAt,
                sessionEditedAt = sourceEditedAt,
            ),
        )
    }

    @Test
    fun `v3 remains compatible with original Lite legacy provenance`() {
        val sourceCompletedAt = Instant.parse("2026-08-14T15:43:02.232Z")
        val postCompletionEditAt = Instant.parse("2026-08-15T09:00:00Z")

        assertEquals(
            postCompletionEditAt,
            DynamicHistoricalAvailabilityV3.resolve(
                observationSource = DynamicResistanceV2Contract.LEGACY_UNSIDED_SOURCE,
                nativeRecordedAt = Instant.parse("2026-08-31T12:57:00Z"),
                sessionCompletedAt = sourceCompletedAt,
                sessionEditedAt = postCompletionEditAt,
            ),
        )
    }

    @Test
    fun `native evidence keeps factual native recorded time`() {
        val sourceCompletedAt = Instant.parse("2026-08-16T12:22:26.229Z")
        val nativeRecordedAt = Instant.parse("2026-08-31T12:57:00Z")

        assertEquals(
            nativeRecordedAt,
            DynamicHistoricalAvailabilityV3.resolve(
                observationSource = "native_manual_entry",
                nativeRecordedAt = nativeRecordedAt,
                sessionCompletedAt = sourceCompletedAt,
                sessionEditedAt = Instant.parse("2026-08-16T12:10:12.434Z"),
            ),
        )
    }
}
