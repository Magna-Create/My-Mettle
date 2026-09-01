package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import java.time.Instant

/** Versioned retrospective availability rule for corrected/imported 7C evidence. */
object NonDynamicHistoricalAvailabilityV1 {
    const val POLICY_ID = "n-bio-7c-historical-source-availability-v1-corrected-lite"

    fun resolve(
        observationSource: String,
        nativeRecordedAt: Instant,
        sessionCompletedAt: Instant,
        sessionEditedAt: Instant?,
    ): Instant = if (observationSource in NonDynamicCapabilityV1.evidencePolicy.eligibleHistoricalUnknownSources) {
        maxOf(sessionCompletedAt, sessionEditedAt ?: sessionCompletedAt)
    } else {
        nativeRecordedAt
    }
}
