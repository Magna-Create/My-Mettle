package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.performance.MetricFamily
import java.time.Instant

/** Stable label for the evidence family supporting one inferred quantity. */
@JvmInline
value class EvidenceFamily(val value: String) {
    init {
        require(value.isNotBlank()) { "Evidence family cannot be blank." }
    }

    companion object {
        fun fromMetricFamily(family: MetricFamily): EvidenceFamily = EvidenceFamily(family.storageValue)
    }
}

/**
 * Longitudinal evidence support is deliberately separate from posterior precision.
 * Multiple observations from one session remain multiple observations, but only one independent
 * session for this conservative support count.
 */
data class EvidenceSupport(
    val observationCount: Int,
    val effectiveIndependentSessionCount: Int,
    val firstEvidenceAt: Instant?,
    val lastEvidenceAt: Instant?,
    val evidenceFamily: EvidenceFamily,
) {
    init {
        require(observationCount >= 0) { "Observation count cannot be negative." }
        require(effectiveIndependentSessionCount >= 0) { "Independent-session count cannot be negative." }
        require(effectiveIndependentSessionCount <= observationCount) {
            "Independent-session count cannot exceed observation count."
        }
        require(firstEvidenceAt == null || lastEvidenceAt == null || !firstEvidenceAt.isAfter(lastEvidenceAt)) {
            "First evidence cannot be after last evidence."
        }
        if (observationCount == 0) {
            require(firstEvidenceAt == null && lastEvidenceAt == null) {
                "Zero-observation support cannot claim an evidence horizon."
            }
            require(effectiveIndependentSessionCount == 0)
        } else {
            require(firstEvidenceAt != null && lastEvidenceAt != null) {
                "Observed evidence requires first and last evidence timestamps."
            }
        }
    }

    companion object {
        fun fromObservations(
            evidenceFamily: EvidenceFamily,
            observations: Collection<EvidenceSupportObservation>,
        ): EvidenceSupport {
            if (observations.isEmpty()) {
                return EvidenceSupport(0, 0, null, null, evidenceFamily)
            }
            val distinctObservations = observations.distinctBy { it.observationId }
            return EvidenceSupport(
                observationCount = distinctObservations.size,
                effectiveIndependentSessionCount = distinctObservations.map { it.sessionId }.distinct().size,
                firstEvidenceAt = distinctObservations.minOf { it.observedAt },
                lastEvidenceAt = distinctObservations.maxOf { it.observedAt },
                evidenceFamily = evidenceFamily,
            )
        }
    }
}

data class EvidenceSupportObservation(
    val observationId: String,
    val sessionId: String,
    val observedAt: Instant,
) {
    init {
        require(observationId.isNotBlank()) { "Evidence observation id cannot be blank." }
        require(sessionId.isNotBlank()) { "Evidence session id cannot be blank." }
    }
}

/** Compact persisted summary of an individual posterior, not scientific evidence quality. */
data class PosteriorSummary(
    val credibleLower05: Double,
    val estimateMedian: Double,
    val credibleUpper95: Double,
    val posteriorVariance: Double,
) {
    init {
        require(credibleLower05.isFinite()) { "p05 must be finite." }
        require(estimateMedian.isFinite()) { "p50 must be finite." }
        require(credibleUpper95.isFinite()) { "p95 must be finite." }
        require(posteriorVariance.isFinite()) { "Posterior variance must be finite." }
        require(posteriorVariance >= 0.0) { "Posterior variance cannot be negative." }
        require(credibleLower05 <= estimateMedian && estimateMedian <= credibleUpper95) {
            "Posterior interval must satisfy p05 <= p50 <= p95."
        }
    }

    val p05: Double get() = credibleLower05
    val p50: Double get() = estimateMedian
    val p95: Double get() = credibleUpper95
}

/** Provenance carried by an inferred output independently of its numerical uncertainty. */
data class ModelOutputProvenance(
    val modelConfigId: ModelConfigId,
    val modelManifestId: ModelManifestId?,
    val inferenceRunId: InferenceRunId?,
    val evidenceThrough: Instant?,
)

/**
 * A missing summary is a valid unknown result. Support/provenance remain inspectable so blank
 * behaviour is distinguishable from missing bookkeeping.
 */
data class PosteriorEstimate(
    val summary: PosteriorSummary?,
    val support: EvidenceSupport,
    val provenance: ModelOutputProvenance,
) {
    val isKnown: Boolean get() = summary != null
}
