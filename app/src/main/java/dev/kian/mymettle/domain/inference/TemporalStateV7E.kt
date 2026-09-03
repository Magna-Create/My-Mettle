package dev.kian.mymettle.domain.inference

import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class TemporalCandidateLayer(val storageValue: String) {
    CAPABILITY_BASELINE("capability_baseline"),
    TEMPORAL_BASE("temporal_base"),
    DOSE_TEMPORAL("dose_temporal"),
    CONTEXT_TEMPORAL("context_temporal"),
}

data class TemporalStateConfigV1(
    val semanticVersion: String = "n-bio-7e-neutral-temporal-v1",
    val persistentPriorMean: Double = 0.0,
    val persistentPriorVariance: Double = 0.0400,
    val transientPriorMean: Double = 0.0,
    val transientPriorVariance: Double = 0.0225,
    val persistentProcessVariancePerDay: Double = 0.000025,
    val transientStationaryProcessVariance: Double = 0.0025,
    val transientHalfLifeDays: Double = 3.0,
    val observationVariance: Double = 0.0100,
    val huberThresholdStandardDeviations: Double = 3.0,
    val doseHalfLifeDays: Double = 3.0,
    val doseCoefficientPriorMean: Double = 0.0,
    val doseCoefficientPriorVariance: Double = 0.0004,
    val doseStandardisationScale: Double = 4.0,
) {
    init {
        require(semanticVersion.isNotBlank())
        require(persistentPriorVariance > 0.0)
        require(transientPriorVariance > 0.0)
        require(persistentProcessVariancePerDay >= 0.0)
        require(transientStationaryProcessVariance > 0.0)
        require(transientHalfLifeDays > 0.0)
        require(observationVariance > 0.0)
        require(huberThresholdStandardDeviations > 0.0)
        require(doseHalfLifeDays > 0.0)
        require(doseCoefficientPriorVariance > 0.0)
        require(doseStandardisationScale > 0.0)
    }

    val canonicalPayload: String = listOf(
        "schema=1",
        "semanticVersion=$semanticVersion",
        "persistentPriorMean=$persistentPriorMean",
        "persistentPriorVariance=$persistentPriorVariance",
        "transientPriorMean=$transientPriorMean",
        "transientPriorVariance=$transientPriorVariance",
        "persistentProcessVariancePerDay=$persistentProcessVariancePerDay",
        "transientStationaryProcessVariance=$transientStationaryProcessVariance",
        "transientHalfLifeDays=$transientHalfLifeDays",
        "observationVariance=$observationVariance",
        "huberThresholdStandardDeviations=$huberThresholdStandardDeviations",
        "doseHalfLifeDays=$doseHalfLifeDays",
        "doseCoefficientPriorMean=$doseCoefficientPriorMean",
        "doseCoefficientPriorVariance=$doseCoefficientPriorVariance",
        "doseStandardisationScale=$doseStandardisationScale",
    ).joinToString(";")
}

/** Symmetric covariance for [persistent, transient, dose coefficient]. */
data class TemporalCovariance3(
    val pp: Double,
    val pt: Double,
    val pd: Double,
    val tt: Double,
    val td: Double,
    val dd: Double,
) {
    init {
        require(listOf(pp, pt, pd, tt, td, dd).all(Double::isFinite))
        require(pp >= 0.0 && tt >= 0.0 && dd >= 0.0)
    }

    fun varianceFor(h0: Double, h1: Double, h2: Double): Double =
        h0 * h0 * pp + h1 * h1 * tt + h2 * h2 * dd +
            2.0 * h0 * h1 * pt + 2.0 * h0 * h2 * pd + 2.0 * h1 * h2 * td
}

data class TemporalStatePosteriorV1(
    val persistentMean: Double,
    val transientMean: Double,
    val doseCoefficientMean: Double,
    val covariance: TemporalCovariance3,
    val horizon: Instant,
    val observationCount: Int,
    val independentSessionCount: Int,
) {
    init {
        require(listOf(persistentMean, transientMean, doseCoefficientMean).all(Double::isFinite))
        require(observationCount >= 0)
        require(independentSessionCount in 0..observationCount)
    }
}

data class TemporalContextAdjustment(
    val locationMean: Double = 0.0,
    val locationVariance: Double = 0.0,
    val observationLogVarianceShift: Double = 0.0,
) {
    init {
        require(locationMean.isFinite())
        require(locationVariance.isFinite() && locationVariance >= 0.0)
        require(observationLogVarianceShift.isFinite())
    }
}

data class TemporalPredictionV1(
    val layer: TemporalCandidateLayer,
    val predictedAt: Instant,
    val evidenceThrough: Instant?,
    val persistentMean: Double,
    val transientMean: Double,
    val doseContributionMean: Double,
    val contextContributionMean: Double,
    val mean: Double,
    val variance: Double,
    val p05: Double,
    val p50: Double,
    val p95: Double,
    val doseAvailable: Boolean,
) {
    init {
        require(evidenceThrough == null || !evidenceThrough.isAfter(predictedAt))
        require(listOf(persistentMean, transientMean, doseContributionMean, contextContributionMean, mean, variance, p05, p50, p95).all(Double::isFinite))
        require(variance > 0.0 && p05 <= p50 && p50 <= p95)
    }
}

data class TemporalUpdateV1(
    val prior: TemporalStatePosteriorV1,
    val prediction: TemporalPredictionV1,
    val posterior: TemporalStatePosteriorV1,
    val innovation: Double,
    val standardisedInnovation: Double,
    val robustWeight: Double,
    val logPredictiveDensity: Double,
    val pit: Double,
) {
    init {
        require(innovation.isFinite() && standardisedInnovation.isFinite())
        require(robustWeight.isFinite() && robustWeight in 0.0..1.0)
        require(logPredictiveDensity.isFinite())
        require(pit.isFinite() && pit in 0.0..1.0)
    }
}

/** Deterministic robust Gaussian filter for the pre-registered neutral 7E v1 model. */
class NeutralTemporalStateFilterV1(
    val config: TemporalStateConfigV1 = TemporalStateConfigV1(),
) {
    fun initial(at: Instant): TemporalStatePosteriorV1 = TemporalStatePosteriorV1(
        persistentMean = config.persistentPriorMean,
        transientMean = config.transientPriorMean,
        doseCoefficientMean = config.doseCoefficientPriorMean,
        covariance = TemporalCovariance3(
            pp = config.persistentPriorVariance,
            pt = 0.0,
            pd = 0.0,
            tt = config.transientPriorVariance,
            td = 0.0,
            dd = config.doseCoefficientPriorVariance,
        ),
        horizon = at,
        observationCount = 0,
        independentSessionCount = 0,
    )

    fun predictState(previous: TemporalStatePosteriorV1, at: Instant): TemporalStatePosteriorV1 {
        require(!at.isBefore(previous.horizon)) { "Temporal replay must be chronological." }
        val days = max(0.0, Duration.between(previous.horizon, at).toMillis() / MILLIS_PER_DAY)
        val phi = exp(-LN_2 * days / config.transientHalfLifeDays)
        val c = previous.covariance
        return previous.copy(
            transientMean = phi * previous.transientMean,
            covariance = TemporalCovariance3(
                pp = c.pp + config.persistentProcessVariancePerDay * days,
                pt = phi * c.pt,
                pd = c.pd,
                tt = phi * phi * c.tt + config.transientStationaryProcessVariance * (1.0 - phi * phi),
                td = phi * c.td,
                dd = c.dd,
            ),
            horizon = at,
        )
    }

    fun predict(
        previous: TemporalStatePosteriorV1,
        at: Instant,
        layer: TemporalCandidateLayer,
        standardisedRecentDose: Double? = null,
        context: TemporalContextAdjustment = TemporalContextAdjustment(),
    ): Pair<TemporalStatePosteriorV1, TemporalPredictionV1> {
        require(standardisedRecentDose == null || standardisedRecentDose.isFinite())
        val prior = predictState(previous, at)
        val doseEnabled = layer in setOf(TemporalCandidateLayer.DOSE_TEMPORAL, TemporalCandidateLayer.CONTEXT_TEMPORAL)
        val contextEnabled = layer == TemporalCandidateLayer.CONTEXT_TEMPORAL
        val dose = if (doseEnabled) standardisedRecentDose ?: 0.0 else 0.0
        val contextValue = if (contextEnabled) context else TemporalContextAdjustment()
        val h2 = dose
        val stateVariance = prior.covariance.varianceFor(1.0, 1.0, h2)
        val observationVariance = config.observationVariance * exp(contextValue.observationLogVarianceShift)
        val variance = max(MIN_VARIANCE, stateVariance + observationVariance + contextValue.locationVariance)
        val doseContribution = prior.doseCoefficientMean * dose
        val mean = prior.persistentMean + prior.transientMean + doseContribution + contextValue.locationMean
        val sd = sqrt(variance)
        return prior to TemporalPredictionV1(
            layer = layer,
            predictedAt = at,
            evidenceThrough = previous.horizon,
            persistentMean = prior.persistentMean,
            transientMean = prior.transientMean,
            doseContributionMean = doseContribution,
            contextContributionMean = contextValue.locationMean,
            mean = mean,
            variance = variance,
            p05 = mean - NORMAL_90_Z * sd,
            p50 = mean,
            p95 = mean + NORMAL_90_Z * sd,
            doseAvailable = !doseEnabled || standardisedRecentDose != null,
        )
    }

    fun update(
        previous: TemporalStatePosteriorV1,
        observedAt: Instant,
        observedLogResidual: Double,
        layer: TemporalCandidateLayer,
        standardisedRecentDose: Double? = null,
        context: TemporalContextAdjustment = TemporalContextAdjustment(),
    ): TemporalUpdateV1 {
        require(observedLogResidual.isFinite())
        val (prior, firstPrediction) = predict(previous, observedAt, layer, standardisedRecentDose, context)
        val dose = if (layer in setOf(TemporalCandidateLayer.DOSE_TEMPORAL, TemporalCandidateLayer.CONTEXT_TEMPORAL)) {
            standardisedRecentDose ?: 0.0
        } else 0.0
        val contextValue = if (layer == TemporalCandidateLayer.CONTEXT_TEMPORAL) context else TemporalContextAdjustment()
        val innovation = observedLogResidual - firstPrediction.mean
        val initialSd = sqrt(firstPrediction.variance)
        val standardised = innovation / initialSd
        val weight = if (abs(standardised) <= config.huberThresholdStandardDeviations) 1.0 else {
            config.huberThresholdStandardDeviations / abs(standardised)
        }
        val robustObservationVariance = config.observationVariance * exp(contextValue.observationLogVarianceShift) / (weight * weight)
        val h = doubleArrayOf(1.0, 1.0, dose)
        val c = prior.covariance
        val ph = doubleArrayOf(
            c.pp * h[0] + c.pt * h[1] + c.pd * h[2],
            c.pt * h[0] + c.tt * h[1] + c.td * h[2],
            c.pd * h[0] + c.td * h[1] + c.dd * h[2],
        )
        val innovationVariance = max(
            MIN_VARIANCE,
            h[0] * ph[0] + h[1] * ph[1] + h[2] * ph[2] + robustObservationVariance + contextValue.locationVariance,
        )
        val robustPrediction = firstPrediction.copy(
            variance = innovationVariance,
            p05 = firstPrediction.mean - NORMAL_90_Z * sqrt(innovationVariance),
            p95 = firstPrediction.mean + NORMAL_90_Z * sqrt(innovationVariance),
        )
        val gain = ph.map { it / innovationVariance }
        val posteriorCovariance = TemporalCovariance3(
            pp = nonNegative(c.pp - ph[0] * ph[0] / innovationVariance),
            pt = c.pt - ph[0] * ph[1] / innovationVariance,
            pd = c.pd - ph[0] * ph[2] / innovationVariance,
            tt = nonNegative(c.tt - ph[1] * ph[1] / innovationVariance),
            td = c.td - ph[1] * ph[2] / innovationVariance,
            dd = nonNegative(c.dd - ph[2] * ph[2] / innovationVariance),
        )
        val posterior = TemporalStatePosteriorV1(
            persistentMean = prior.persistentMean + gain[0] * innovation,
            transientMean = prior.transientMean + gain[1] * innovation,
            doseCoefficientMean = prior.doseCoefficientMean + gain[2] * innovation,
            covariance = posteriorCovariance,
            horizon = observedAt,
            observationCount = prior.observationCount + 1,
            independentSessionCount = prior.independentSessionCount + 1,
        )
        return TemporalUpdateV1(
            prior = prior,
            prediction = robustPrediction,
            posterior = posterior,
            innovation = innovation,
            standardisedInnovation = standardised,
            robustWeight = weight,
            logPredictiveDensity = -0.5 * (ln(2.0 * PI * innovationVariance) + innovation * innovation / innovationVariance),
            pit = normalCdf(innovation / sqrt(innovationVariance)),
        )
    }

    private fun nonNegative(value: Double): Double = max(0.0, value)

    private fun normalCdf(value: Double): Double {
        // Abramowitz-Stegun 7.1.26; deterministic and sufficient for diagnostics.
        val sign = if (value < 0.0) -1.0 else 1.0
        val x = abs(value) / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * x)
        val erf = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
        return min(1.0, max(0.0, 0.5 * (1.0 + sign * erf)))
    }

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000.0
        private const val LN_2 = 0.6931471805599453
        private const val NORMAL_90_Z = 1.6448536269514722
        private const val MIN_VARIANCE = 1e-12
    }
}

data class DatedSessionDose(
    val at: Instant,
    val value: Double,
) {
    init { require(value.isFinite() && value >= 0.0) }
}

object RecentDoseCovariateV1 {
    fun calculate(
        priorDoses: List<DatedSessionDose>,
        horizon: Instant,
        config: TemporalStateConfigV1 = TemporalStateConfigV1(),
    ): Double? {
        if (priorDoses.isEmpty()) return null
        require(priorDoses.none { it.at.isAfter(horizon) }) { "Future SessionDose cannot enter a pre-session covariate." }
        val strictlyPrior = priorDoses.filter { it.at.isBefore(horizon) }
        if (strictlyPrior.isEmpty()) return null
        return strictlyPrior.sumOf { dose ->
            val ageDays = Duration.between(dose.at, horizon).toMillis() / 86_400_000.0
            (dose.value / config.doseStandardisationScale) * exp(-ln(2.0) * ageDays / config.doseHalfLifeDays)
        }
    }
}
