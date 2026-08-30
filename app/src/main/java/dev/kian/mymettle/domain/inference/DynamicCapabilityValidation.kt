package dev.kian.mymettle.domain.inference

import java.time.Instant
import kotlin.math.abs

/** 7B.3/4 validation is descriptive evaluation policy, not a change to the frozen 7B.2 model. */
data class DynamicCapabilityValidationPolicy(
    val protocolVersion: String = "n-bio-7b34-whole-session-heldout-source-availability-v2",
    val semanticsMode: InferenceSemanticsMode = InferenceSemanticsMode.HISTORICAL_SEMANTICS,
    val predictiveLowerProbability: Double = 0.05,
    val predictiveUpperProbability: Double = 0.95,
    val minimumCalibrationObservations: Int = 15,
    val minimumVerdictObservations: Int = 12,
    val strongVerdictObservations: Int = 20,
    val descriptiveFrontierContradictionProbability: Double = 0.05,
    val acceptableCoverageFloor: Double = 0.75,
    val limitedCoverageFloor: Double = 0.60,
    val acceptableCatastrophicRate: Double = 0.10,
    val limitedCatastrophicRate: Double = 0.20,
    val maximumLimitedFailureRate: Double = 0.05,
) {
    init {
        require(protocolVersion.isNotBlank())
        require(semanticsMode == InferenceSemanticsMode.HISTORICAL_SEMANTICS)
        require(predictiveLowerProbability in 0.0..1.0)
        require(predictiveUpperProbability in 0.0..1.0 && predictiveUpperProbability > predictiveLowerProbability)
        require(minimumCalibrationObservations > 0)
        require(minimumVerdictObservations > 0)
        require(strongVerdictObservations >= minimumVerdictObservations)
        require(descriptiveFrontierContradictionProbability in 0.0..0.5)
        require(acceptableCoverageFloor in 0.0..1.0)
        require(limitedCoverageFloor in 0.0..acceptableCoverageFloor)
        require(acceptableCatastrophicRate in 0.0..1.0)
        require(limitedCatastrophicRate in acceptableCatastrophicRate..1.0)
        require(maximumLimitedFailureRate in 0.0..1.0)
    }
}

enum class DynamicHeldOutStatus(val storageValue: String) {
    EVALUABLE("evaluable"),
    INSUFFICIENT_EVIDENCE("insufficient_evidence"),
    MODEL_FAILURE("model_failure"),
}

/** Observable ordinary-set predictive interval. It is intentionally distinct from a latent frontier credible interval. */
data class DynamicDemonstrationPredictive(
    val p05ResistanceKg: Double,
    val p50ResistanceKg: Double,
    val p95ResistanceKg: Double,
    val observedCdf: Double,
    val logPredictiveDensity: Double,
) {
    init {
        require(p05ResistanceKg.isFinite() && p05ResistanceKg > 0.0)
        require(p50ResistanceKg.isFinite() && p50ResistanceKg >= p05ResistanceKg)
        require(p95ResistanceKg.isFinite() && p95ResistanceKg >= p50ResistanceKg)
        require(observedCdf.isFinite() && observedCdf in 0.0..1.0)
        require(logPredictiveDensity.isFinite())
    }

    fun contains(resistanceKg: Double): Boolean = resistanceKg in p05ResistanceKg..p95ResistanceKg
}

data class DynamicHeldOutEvaluation(
    val sessionId: String,
    val observationId: String,
    val heldOutAt: Instant,
    val repetitions: Int,
    val observedResistanceKg: Double,
    val status: DynamicHeldOutStatus,
    val trainingObservationIds: List<String>,
    val trainingSessionIds: List<String>,
    val trainingEvidenceThrough: Instant?,
    val referenceRepetitions: Double?,
    val candidatePredictive: DynamicDemonstrationPredictive?,
    val frontierAtOrAboveObservedProbability: Double?,
    /** Existing BENCHMARK_V0 same-profile latest resistance anchor. Point-like only; no fake distribution. */
    val benchmarkLatestResistanceAnchorKg: Double?,
    /** Latent frontier posterior at held-out repetitions; not an observation predictive interval. */
    val candidateFrontierAtRepetitions: PosteriorSummary? = null,
    /** Deterministic CRPS on natural-log resistance scale. */
    val candidateCrpsLogResistance: Double? = null,
    val modelFailureReason: String? = null,
) {
    init {
        require(sessionId.isNotBlank() && observationId.isNotBlank())
        require(repetitions > 0 && observedResistanceKg.isFinite() && observedResistanceKg > 0.0)
        require(trainingObservationIds.distinct().size == trainingObservationIds.size)
        require(trainingSessionIds.distinct().size == trainingSessionIds.size)
        require(status == DynamicHeldOutStatus.EVALUABLE || candidatePredictive == null)
        require(frontierAtOrAboveObservedProbability == null || frontierAtOrAboveObservedProbability in 0.0..1.0)
        require(benchmarkLatestResistanceAnchorKg == null || benchmarkLatestResistanceAnchorKg > 0.0)
        require(candidateCrpsLogResistance == null || (candidateCrpsLogResistance.isFinite() && candidateCrpsLogResistance >= 0.0))
        if (status == DynamicHeldOutStatus.MODEL_FAILURE) require(!modelFailureReason.isNullOrBlank())
    }
}

data class DynamicPitCalibration(
    val sampleCount: Int,
    val lowCount: Int,
    val middleCount: Int,
    val highCount: Int,
    val meanAbsoluteBinError: Double?,
) {
    init {
        require(sampleCount >= 0)
        require(lowCount >= 0 && middleCount >= 0 && highCount >= 0)
        require(lowCount + middleCount + highCount == sampleCount)
        require(meanAbsoluteBinError == null || (meanAbsoluteBinError.isFinite() && meanAbsoluteBinError >= 0.0))
    }
}

data class DynamicCapabilityValidationSummary(
    val protocolVersion: String,
    val semanticsMode: InferenceSemanticsMode,
    val heldOutObservationCount: Int,
    val heldOutSessionCount: Int,
    val evaluableCount: Int,
    val insufficientEvidenceCount: Int,
    val modelFailureCount: Int,
    val meanCandidateLogPredictiveDensity: Double?,
    val candidatePredictiveCoverage: Double?,
    val meanCandidatePredictiveLogWidth: Double?,
    /** Secondary demonstration-point metric. This is not frontier MAE. */
    val candidateDemonstrationMedianMaeKg: Double?,
    /** Supported benchmark metric: latest same-profile physical resistance anchor error. */
    val benchmarkLatestAnchorMaeKg: Double?,
    /** Unsupported for BENCHMARK_V0 because it has no probability distribution. */
    val benchmarkLogPredictiveDensity: Double? = null,
    val benchmarkPredictiveCoverage: Double? = null,
    val benchmarkPitCalibration: DynamicPitCalibration? = null,
    val candidatePitCalibration: DynamicPitCalibration,
    val catastrophicFrontierContradictionCount: Int,
    val catastrophicFrontierContradictionRate: Double?,
    val availabilityRate: Double,
    val modelFailureRate: Double,
) {
    init {
        require(protocolVersion.isNotBlank())
        require(heldOutObservationCount >= 0 && heldOutSessionCount >= 0)
        require(evaluableCount >= 0 && insufficientEvidenceCount >= 0 && modelFailureCount >= 0)
        require(evaluableCount + insufficientEvidenceCount + modelFailureCount == heldOutObservationCount)
        require(benchmarkLogPredictiveDensity == null && benchmarkPredictiveCoverage == null && benchmarkPitCalibration == null) {
            "BENCHMARK_V0 has no probabilistic predictive semantics."
        }
        require(catastrophicFrontierContradictionCount in 0..evaluableCount)
        require(availabilityRate in 0.0..1.0 && modelFailureRate in 0.0..1.0)
    }
}

enum class DynamicCapabilityCandidateVerdict(val storageValue: String) {
    INSUFFICIENT_EVIDENCE("insufficient_evidence"),
    ACCEPTABLE_FOR_SHADOW("acceptable_for_shadow"),
    ACCEPTABLE_WITH_LIMITATIONS("acceptable_with_limitations"),
    REQUIRES_NEW_CANDIDATE("requires_new_candidate"),
    REJECTED("rejected"),
}

object DynamicCapabilityVerdictPolicy {
    fun verdict(
        summary: DynamicCapabilityValidationSummary,
        policy: DynamicCapabilityValidationPolicy = DynamicCapabilityValidationPolicy(),
    ): DynamicCapabilityCandidateVerdict {
        if (summary.heldOutObservationCount < policy.minimumVerdictObservations ||
            summary.evaluableCount < policy.minimumVerdictObservations
        ) return DynamicCapabilityCandidateVerdict.INSUFFICIENT_EVIDENCE

        val coverage = summary.candidatePredictiveCoverage ?: return DynamicCapabilityCandidateVerdict.REQUIRES_NEW_CANDIDATE
        val catastrophic = summary.catastrophicFrontierContradictionRate ?: 1.0
        if (!coverage.isFinite() || !catastrophic.isFinite() || summary.meanCandidateLogPredictiveDensity?.isFinite() != true) {
            return DynamicCapabilityCandidateVerdict.REJECTED
        }
        if (summary.modelFailureRate > policy.maximumLimitedFailureRate ||
            coverage < policy.limitedCoverageFloor ||
            catastrophic > policy.limitedCatastrophicRate
        ) return DynamicCapabilityCandidateVerdict.REQUIRES_NEW_CANDIDATE

        return if (
            summary.evaluableCount >= policy.strongVerdictObservations &&
            summary.modelFailureCount == 0 &&
            coverage >= policy.acceptableCoverageFloor &&
            catastrophic <= policy.acceptableCatastrophicRate
        ) DynamicCapabilityCandidateVerdict.ACCEPTABLE_FOR_SHADOW
        else DynamicCapabilityCandidateVerdict.ACCEPTABLE_WITH_LIMITATIONS
    }
}

/** Generic revision envelope used to reconstruct only facts recorded by a historical cutoff. */
data class HistoricalObservationRevision<T>(
    val observationId: String,
    val recordedAt: Instant,
    val supersedesObservationId: String?,
    val payload: T,
) {
    init { require(observationId.isNotBlank()) }
}

object HistoricalObservationRevisionSelector {
    fun <T> currentAsKnownAt(
        revisions: List<HistoricalObservationRevision<T>>,
        recordedThrough: Instant,
    ): List<HistoricalObservationRevision<T>> {
        val visible = revisions.filter { !it.recordedAt.isAfter(recordedThrough) }
        val superseded = visible.mapNotNull { it.supersedesObservationId }.toSet()
        return visible.filter { it.observationId !in superseded }
            .sortedWith(compareBy<HistoricalObservationRevision<T>> { it.recordedAt }.thenBy { it.observationId })
    }
}

fun DynamicCapabilityValidationSummary.candidateVsBenchmarkMaeDeltaKg(): Double? {
    val candidate = candidateDemonstrationMedianMaeKg ?: return null
    val benchmark = benchmarkLatestAnchorMaeKg ?: return null
    return candidate - benchmark
}

fun DynamicPitCalibration.isInformative(minimum: Int): Boolean =
    sampleCount >= minimum && meanAbsoluteBinError != null && meanAbsoluteBinError.isFinite()

fun nearlyEqual(left: Double, right: Double, tolerance: Double = 1e-9): Boolean = abs(left - right) <= tolerance
