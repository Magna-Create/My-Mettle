package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ln

/**
 * Immutable-before-outcome prediction envelope for permanent champion/challenger evaluation.
 * Historical development replay can populate the same shape but must label its evidence class honestly.
 */
enum class PrequentialEvidenceClass(val storageValue: String) {
    RETROSPECTIVE_DEVELOPMENT("retrospective_development"),
    FRESH_SHADOW_CONFIRMATORY("fresh_shadow_confirmatory"),
}

data class FrozenPrequentialPrediction(
    val predictionId: String,
    val evidenceClass: PrequentialEvidenceClass,
    val modelIdentity: InferenceMathematicalModelIdentity,
    val solverIdentity: InferenceSolverIdentity,
    val modelConfigId: ModelConfigId,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val side: Laterality,
    val targetSessionId: String,
    val predictedAt: Instant,
    val evidenceThrough: Instant?,
    val repetitions: Int,
    val p05ResistanceKg: Double,
    val p50ResistanceKg: Double,
    val p95ResistanceKg: Double,
) {
    init {
        require(predictionId.isNotBlank() && targetSessionId.isNotBlank())
        require(repetitions > 0)
        require(p05ResistanceKg.isFinite() && p05ResistanceKg > 0.0)
        require(p50ResistanceKg.isFinite() && p50ResistanceKg >= p05ResistanceKg)
        require(p95ResistanceKg.isFinite() && p95ResistanceKg >= p50ResistanceKg)
        require(evidenceThrough == null || !evidenceThrough.isAfter(predictedAt))
    }
}

data class PrequentialScore(
    val predictionId: String,
    val observedAt: Instant,
    val observedResistanceKg: Double,
    val crpsLogResistance: Double?,
    val logPredictiveDensity: Double?,
    val pit: Double?,
    val weightedIntervalScoreLogResistance: Double,
    val intervalLogWidth: Double,
    val medianAbsoluteErrorKg: Double,
    val signedLogResidual: Double,
    val catastrophicFrontierContradiction: Boolean? = null,
) {
    init {
        require(predictionId.isNotBlank())
        require(observedResistanceKg.isFinite() && observedResistanceKg > 0.0)
        require(crpsLogResistance == null || crpsLogResistance.isFinite() && crpsLogResistance >= 0.0)
        require(logPredictiveDensity == null || logPredictiveDensity.isFinite())
        require(pit == null || pit.isFinite() && pit in 0.0..1.0)
        require(weightedIntervalScoreLogResistance.isFinite() && weightedIntervalScoreLogResistance >= 0.0)
        require(intervalLogWidth.isFinite() && intervalLogWidth >= 0.0)
        require(medianAbsoluteErrorKg.isFinite() && medianAbsoluteErrorKg >= 0.0)
        require(signedLogResidual.isFinite())
    }
}

/** WIS with one central interval (default 90%) plus median, computed on natural-log resistance. */
object PrequentialWeightedIntervalScore {
    const val VERSION = "wis-log-resistance-one-central-interval-v1"

    fun score(
        observedResistanceKg: Double,
        p05ResistanceKg: Double,
        p50ResistanceKg: Double,
        p95ResistanceKg: Double,
        alpha: Double = 0.10,
    ): Double {
        require(observedResistanceKg > 0.0 && p05ResistanceKg > 0.0 && p50ResistanceKg > 0.0 && p95ResistanceKg > 0.0)
        require(p05ResistanceKg <= p50ResistanceKg && p50ResistanceKg <= p95ResistanceKg)
        require(alpha in 0.0..1.0 && alpha > 0.0)
        val y = ln(observedResistanceKg)
        val lower = ln(p05ResistanceKg)
        val median = ln(p50ResistanceKg)
        val upper = ln(p95ResistanceKg)
        val intervalScore = (upper - lower) +
            if (y < lower) 2.0 / alpha * (lower - y) else 0.0 +
            if (y > upper) 2.0 / alpha * (y - upper) else 0.0
        val medianWeight = 0.5
        val intervalWeight = alpha / 2.0
        return (medianWeight * abs(y - median) + intervalWeight * intervalScore) / 1.5
    }
}

object PrequentialScoreFactory {
    fun score(
        prediction: FrozenPrequentialPrediction,
        observedAt: Instant,
        observedResistanceKg: Double,
        crpsLogResistance: Double?,
        logPredictiveDensity: Double?,
        pit: Double?,
        catastrophicFrontierContradiction: Boolean? = null,
    ): PrequentialScore = PrequentialScore(
        predictionId = prediction.predictionId,
        observedAt = observedAt,
        observedResistanceKg = observedResistanceKg,
        crpsLogResistance = crpsLogResistance,
        logPredictiveDensity = logPredictiveDensity,
        pit = pit,
        weightedIntervalScoreLogResistance = PrequentialWeightedIntervalScore.score(
            observedResistanceKg,
            prediction.p05ResistanceKg,
            prediction.p50ResistanceKg,
            prediction.p95ResistanceKg,
        ),
        intervalLogWidth = ln(prediction.p95ResistanceKg / prediction.p05ResistanceKg),
        medianAbsoluteErrorKg = abs(prediction.p50ResistanceKg - observedResistanceKg),
        signedLogResidual = ln(observedResistanceKg / prediction.p50ResistanceKg),
        catastrophicFrontierContradiction = catastrophicFrontierContradiction,
    )
}
