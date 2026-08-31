package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicResistanceV3Contract
import java.time.Instant

/**
 * Historical source-availability policy for the corrected Lite -> Room14 baseline.
 *
 * Room14 retains the factual Native ingest timestamp in set_observation.recordedAt. For retrospective
 * validation only, explicitly Lite-derived observations become knowable at source-session finalisation
 * instead, so importing the backup on a later date does not collapse all historical evidence onto the
 * ingest day. Ordinary Native/manual evidence keeps its factual Native recordedAt.
 */
object DynamicHistoricalAvailabilityV3 {
    const val POLICY_ID = "n-bio-7b4-historical-source-availability-v3-corrected-lite"

    fun resolve(
        observationSource: String,
        nativeRecordedAt: Instant,
        sessionCompletedAt: Instant,
        sessionEditedAt: Instant?,
    ): Instant = if (observationSource in DynamicResistanceV3Contract.evidencePolicy.eligibleHistoricalUnknownSources) {
        maxOf(sessionCompletedAt, sessionEditedAt ?: sessionCompletedAt)
    } else {
        nativeRecordedAt
    }
}
