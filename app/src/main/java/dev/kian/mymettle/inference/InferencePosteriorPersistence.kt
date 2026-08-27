package dev.kian.mymettle.inference

import dev.kian.mymettle.data.local.entity.PosteriorColumns
import dev.kian.mymettle.domain.inference.EvidenceFamily
import dev.kian.mymettle.domain.inference.EvidenceSupport
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import java.time.Instant

/** Deterministic Room mapping for compact posterior/support state. */
fun PosteriorEstimate.toPosteriorColumns(): PosteriorColumns = PosteriorColumns(
    p05 = summary?.credibleLower05,
    p50 = summary?.estimateMedian,
    p95 = summary?.credibleUpper95,
    variance = summary?.posteriorVariance,
    observationCount = support.observationCount,
    independentSessionCount = support.effectiveIndependentSessionCount,
    firstEvidenceAt = support.firstEvidenceAt?.toString(),
    lastEvidenceAt = support.lastEvidenceAt?.toString(),
    evidenceFamily = support.evidenceFamily.value,
)

fun PosteriorColumns.toPosteriorEstimate(provenance: ModelOutputProvenance): PosteriorEstimate {
    val hasSummary = listOf(p05, p50, p95, variance).all { it != null }
    return PosteriorEstimate(
        summary = if (hasSummary) {
            PosteriorSummary(
                credibleLower05 = requireNotNull(p05),
                estimateMedian = requireNotNull(p50),
                credibleUpper95 = requireNotNull(p95),
                posteriorVariance = requireNotNull(variance),
            )
        } else {
            null
        },
        support = EvidenceSupport(
            observationCount = observationCount,
            effectiveIndependentSessionCount = independentSessionCount,
            firstEvidenceAt = firstEvidenceAt?.let(Instant::parse),
            lastEvidenceAt = lastEvidenceAt?.let(Instant::parse),
            evidenceFamily = EvidenceFamily(evidenceFamily),
        ),
        provenance = provenance,
    )
}
