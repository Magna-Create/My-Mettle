package dev.kian.mymettle.engine.inference

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicHistoricalAvailabilityV2Test {
    @Test
    fun `legacy import uses source session finalisation rather than native ingest time`() {
        val sourceCompletedAt = Instant.parse("2026-08-14T15:43:02.232Z")
        val sourceEditedAt = Instant.parse("2026-08-14T15:38:53.688Z")
        val nativeRecordedAt = Instant.parse("2026-08-27T10:15:30.067Z")

        assertEquals(
            sourceCompletedAt,
            DynamicHistoricalAvailabilityV2.resolve(
                observationSource = "lite_legacy_v6_import",
                nativeRecordedAt = nativeRecordedAt,
                sessionCompletedAt = sourceCompletedAt,
                sessionEditedAt = sourceEditedAt,
            ),
        )
    }

    @Test
    fun `ordinary native evidence retains factual native recorded time`() {
        val sourceCompletedAt = Instant.parse("2026-08-14T15:43:02.232Z")
        val nativeRecordedAt = Instant.parse("2026-08-27T10:15:30.067Z")

        assertEquals(
            nativeRecordedAt,
            DynamicHistoricalAvailabilityV2.resolve(
                observationSource = "native_manual_entry",
                nativeRecordedAt = nativeRecordedAt,
                sessionCompletedAt = sourceCompletedAt,
                sessionEditedAt = Instant.parse("2026-08-14T15:38:53.688Z"),
            ),
        )
    }

    @Test
    fun `post completion legacy edit cannot leak backwards into session holdout`() {
        val sourceCompletedAt = Instant.parse("2026-08-14T15:43:02.232Z")
        val postCompletionEditAt = Instant.parse("2026-08-15T09:00:00Z")

        assertEquals(
            postCompletionEditAt,
            DynamicHistoricalAvailabilityV2.resolve(
                observationSource = "lite_legacy_v6_import",
                nativeRecordedAt = Instant.parse("2026-08-27T10:15:30.067Z"),
                sessionCompletedAt = sourceCompletedAt,
                sessionEditedAt = postCompletionEditAt,
            ),
        )
    }
}
