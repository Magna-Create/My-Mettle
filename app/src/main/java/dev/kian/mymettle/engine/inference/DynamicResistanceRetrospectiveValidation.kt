package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.DynamicCapabilityCandidateVerdict
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationPolicy
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationSummary
import dev.kian.mymettle.domain.inference.DynamicDemonstrationPredictive
import dev.kian.mymettle.domain.inference.DynamicHeldOutEvaluation
import dev.kian.mymettle.domain.inference.DynamicHeldOutStatus
import dev.kian.mymettle.domain.inference.DynamicPitCalibration
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierConfig
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicCapabilityVerdictPolicy
import dev.kian.mymettle.engine.performance.DynamicReferenceRepSelector
import dev.kian.mymettle.engine.performance.DynamicStochasticFrontierModel
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Capability-specific extension of the 7A chronological contract.
 *
 * The held-out longitudinal unit is the whole session. Every candidate fit is built from earlier
 * sessions only; the reference rep anchor is recomputed from that training evidence only. Ordinary
 * successful held-out sets are scored against the demonstration predictive, never against the
 * latent frontier credible interval as though chosen load were a maximum test.
 */
class DynamicResistanceRetrospectiveEvaluator(
    private val model: DynamicStochasticFrontierModel = DynamicStochasticFrontierModel(),
    private val policy: DynamicCapabilityValidationPolicy = DynamicCapabilityValidationPolicy(),
    private val configCreatedAt: Instant = Instant.parse("2026-08-27T00:00:00Z"),
) {
    val protocolVersion: String get() = policy.protocolVersion

    fun evaluate(projection: DynamicResistanceEvidenceProjection): DynamicCapabilityValidationSummary {
        val observations = evaluateObservations(projection)
        val evaluable = observations.filter { it.status == DynamicHeldOutStatus.EVALUABLE }
        val pit = calibration(evaluable.mapNotNull { it.candidatePredictive?.observedCdf })
        val coverage = evaluable.mapNotNull { item ->
            item.candidatePredictive?.let { if (it.contains(item.observedResistanceKg)) 1.0 else 0.0 }
        }.averageOrNull()
        val lpd = evaluable.mapNotNull { it.candidatePredictive?.logPredictiveDensity }.averageOrNull()
        val width = evaluable.mapNotNull { item ->
            item.candidatePredictive?.let { ln(it.p95ResistanceKg / it.p05ResistanceKg) }
        }.averageOrNull()
        val candidateMae = evaluable.mapNotNull { item ->
            item.candidatePredictive?.let { abs(it.p50ResistanceKg - item.observedResistanceKg) }
        }.averageOrNull()
        val benchmarkMae = evaluable.mapNotNull { item ->
            item.benchmarkLatestResistanceAnchorKg?.let { abs(it - item.observedResistanceKg) }
        }.averageOrNull()
        val catastrophic = evaluable.count {
            (it.frontierAtOrAboveObservedProbability ?: 1.0) < policy.descriptiveFrontierContradictionProbability
        }
        val total = observations.size
        val failures = observations.count { it.status == DynamicHeldOutStatus.MODEL_FAILURE }
        return DynamicCapabilityValidationSummary(
            protocolVersion = protocolVersion,
            semanticsMode = policy.semanticsMode,
            heldOutObservationCount = total,
            heldOutSessionCount = observations.map { it.sessionId }.distinct().size,
            evaluableCount = evaluable.size,
            insufficientEvidenceCount = observations.count { it.status == DynamicHeldOutStatus.INSUFFICIENT_EVIDENCE },
            modelFailureCount = failures,
            meanCandidateLogPredictiveDensity = lpd,
            candidatePredictiveCoverage = coverage,
            meanCandidatePredictiveLogWidth = width,
            candidateDemonstrationMedianMaeKg = candidateMae,
            benchmarkLatestAnchorMaeKg = benchmarkMae,
            candidatePitCalibration = pit,
            catastrophicFrontierContradictionCount = catastrophic,
            catastrophicFrontierContradictionRate = if (evaluable.isEmpty()) null else catastrophic.toDouble() / evaluable.size,
            availabilityRate = if (total == 0) 0.0 else evaluable.size.toDouble() / total,
            modelFailureRate = if (total == 0) 0.0 else failures.toDouble() / total,
        )
    }

    fun evaluateObservations(projection: DynamicResistanceEvidenceProjection): List<DynamicHeldOutEvaluation> {
        require(projection.policy.identity == model.config.evidencePolicyIdentity)
        val sessions = projection.evidence.groupBy { it.sessionId }.entries
            .sortedWith(
                compareBy<Map.Entry<String, List<DynamicResistanceEvidence>>> { it.value.minOf { value -> value.completedAt } }
                    .thenBy { it.key },
            )
        val prior = mutableListOf<DynamicResistanceEvidence>()
        val results = mutableListOf<DynamicHeldOutEvaluation>()
        sessions.forEach { (sessionId, heldOutSession) ->
            val orderedHeldOut = heldOutSession.sortedWith(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId })
            if (prior.isEmpty()) {
                orderedHeldOut.forEach { heldOut -> results += insufficient(heldOut, sessionId, prior) }
            } else {
                val training = prior.sortedWith(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId })
                val reference = DynamicReferenceRepSelector.select(training, model.config.referenceRepPolicy)
                if (reference == null) {
                    orderedHeldOut.forEach { heldOut -> results += insufficient(heldOut, sessionId, training) }
                } else {
                    val trainingProjection = DynamicResistanceEvidenceProjection(
                        profile = projection.profile,
                        side = projection.side,
                        evidence = training,
                        exclusions = emptyList(),
                        referenceRepetitions = reference,
                        policy = projection.policy,
                    )
                    val horizon = training.maxOf { it.completedAt }
                    try {
                        val configDefinition = model.config.toModelConfig(configCreatedAt)
                        val fit = model.fit(
                            dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest(
                                projection = trainingProjection,
                                inferenceHorizon = horizon,
                                modelConfig = configDefinition,
                            ),
                        )
                        val predictiveEvaluator = DynamicDemonstrationPredictiveEvaluator(model)
                        orderedHeldOut.forEach { heldOut ->
                            val predictive = predictiveEvaluator.evaluate(
                                fit = fit,
                                repetitions = heldOut.repetitions.toDouble(),
                                observedResistanceKg = heldOut.resistance.value,
                                lowerProbability = policy.predictiveLowerProbability,
                                upperProbability = policy.predictiveUpperProbability,
                            )
                            results += DynamicHeldOutEvaluation(
                                sessionId = sessionId,
                                observationId = heldOut.observationId,
                                heldOutAt = heldOut.completedAt,
                                repetitions = heldOut.repetitions,
                                observedResistanceKg = heldOut.resistance.value,
                                status = DynamicHeldOutStatus.EVALUABLE,
                                trainingObservationIds = training.map { it.observationId },
                                trainingSessionIds = training.map { it.sessionId }.distinct(),
                                trainingEvidenceThrough = horizon,
                                referenceRepetitions = fit.referenceRepetitions,
                                candidatePredictive = predictive,
                                frontierAtOrAboveObservedProbability = frontierAtOrAboveObservedProbability(
                                    fit,
                                    heldOut.repetitions.toDouble(),
                                    heldOut.resistance.value,
                                ),
                                benchmarkLatestResistanceAnchorKg = training.maxWithOrNull(
                                    compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId },
                                )?.resistance?.value,
                            )
                        }
                    } catch (failure: DynamicCapabilityFitException) {
                        orderedHeldOut.forEach { heldOut ->
                            results += DynamicHeldOutEvaluation(
                                sessionId = sessionId,
                                observationId = heldOut.observationId,
                                heldOutAt = heldOut.completedAt,
                                repetitions = heldOut.repetitions,
                                observedResistanceKg = heldOut.resistance.value,
                                status = DynamicHeldOutStatus.MODEL_FAILURE,
                                trainingObservationIds = training.map { it.observationId },
                                trainingSessionIds = training.map { it.sessionId }.distinct(),
                                trainingEvidenceThrough = horizon,
                                referenceRepetitions = reference,
                                candidatePredictive = null,
                                frontierAtOrAboveObservedProbability = null,
                                benchmarkLatestResistanceAnchorKg = training.maxWithOrNull(
                                    compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId },
                                )?.resistance?.value,
                                modelFailureReason = failure.reason.storageValue,
                            )
                        }
                    }
                }
            }
            // Whole-session holdout: no member of Sk becomes training evidence until every member was scored.
            prior += orderedHeldOut
        }
        return results
    }

    fun verdict(summary: DynamicCapabilityValidationSummary): DynamicCapabilityCandidateVerdict =
        DynamicCapabilityVerdictPolicy.verdict(summary, policy)

    private fun insufficient(
        heldOut: DynamicResistanceEvidence,
        sessionId: String,
        training: List<DynamicResistanceEvidence>,
    ): DynamicHeldOutEvaluation = DynamicHeldOutEvaluation(
        sessionId = sessionId,
        observationId = heldOut.observationId,
        heldOutAt = heldOut.completedAt,
        repetitions = heldOut.repetitions,
        observedResistanceKg = heldOut.resistance.value,
        status = DynamicHeldOutStatus.INSUFFICIENT_EVIDENCE,
        trainingObservationIds = training.map { it.observationId },
        trainingSessionIds = training.map { it.sessionId }.distinct(),
        trainingEvidenceThrough = training.maxOfOrNull { it.completedAt },
        referenceRepetitions = DynamicReferenceRepSelector.select(training, model.config.referenceRepPolicy),
        candidatePredictive = null,
        frontierAtOrAboveObservedProbability = null,
        benchmarkLatestResistanceAnchorKg = training.maxWithOrNull(
            compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId },
        )?.resistance?.value,
    )

    private fun frontierAtOrAboveObservedProbability(
        fit: DynamicStochasticFrontierFit,
        repetitions: Double,
        observedResistanceKg: Double,
    ): Double {
        val x = ln(repetitions / fit.referenceRepetitions)
        val observedLog = ln(observedResistanceKg)
        return fit.posteriorNodes.filter {
            it.logFrontierAtReference - it.slope * x >= observedLog
        }.sumOf { it.posteriorWeight }.coerceIn(0.0, 1.0)
    }

    private fun calibration(values: List<Double>): DynamicPitCalibration {
        val low = values.count { it < 1.0 / 3.0 }
        val middle = values.count { it >= 1.0 / 3.0 && it < 2.0 / 3.0 }
        val high = values.size - low - middle
        val error = if (values.size < policy.minimumCalibrationObservations) null else {
            val expected = 1.0 / 3.0
            listOf(low, middle, high).sumOf { abs(it.toDouble() / values.size - expected) } / 3.0
        }
        return DynamicPitCalibration(values.size, low, middle, high, error)
    }
}

/** Deterministic observable-set predictive from the frozen 7B.2 posterior + HalfNormal/Student-t likelihood. */
class DynamicDemonstrationPredictiveEvaluator(
    private val model: DynamicStochasticFrontierModel,
) {
    private val config: DynamicStochasticFrontierConfig get() = model.config
    private val slackPoints: List<SlackPoint> = buildSlackPoints(config)

    fun evaluate(
        fit: DynamicStochasticFrontierFit,
        repetitions: Double,
        observedResistanceKg: Double,
        lowerProbability: Double = 0.05,
        upperProbability: Double = 0.95,
    ): DynamicDemonstrationPredictive {
        require(repetitions.isFinite() && repetitions > 0.0)
        require(observedResistanceKg.isFinite() && observedResistanceKg > 0.0)
        require(lowerProbability in 0.0..1.0 && upperProbability in 0.0..1.0 && upperProbability > lowerProbability)
        return DynamicDemonstrationPredictive(
            p05ResistanceKg = quantile(fit, repetitions, lowerProbability),
            p50ResistanceKg = quantile(fit, repetitions, 0.5),
            p95ResistanceKg = quantile(fit, repetitions, upperProbability),
            observedCdf = cdf(fit, repetitions, observedResistanceKg),
            logPredictiveDensity = model.demonstrationLogPredictiveDensity(fit, repetitions, observedResistanceKg),
        )
    }

    fun cdf(fit: DynamicStochasticFrontierFit, repetitions: Double, resistanceKg: Double): Double {
        require(repetitions.isFinite() && repetitions > 0.0)
        require(resistanceKg.isFinite() && resistanceKg > 0.0)
        val x = ln(repetitions / fit.referenceRepetitions)
        val y = ln(resistanceKg)
        var cumulative = 0.0
        fit.posteriorNodes.forEach { node ->
            val frontier = node.logFrontierAtReference - node.slope * x
            slackPoints.forEach { slack ->
                val location = frontier - node.slackScale * slack.standardised
                val standardised = (y - location) / node.noiseScale
                cumulative += node.posteriorWeight * slack.probability * studentTCdf(
                    standardised,
                    config.studentTDegreesOfFreedom,
                )
            }
        }
        return cumulative.coerceIn(0.0, 1.0)
    }

    private fun quantile(fit: DynamicStochasticFrontierFit, repetitions: Double, probability: Double): Double {
        var low = ln(config.numericalMinimumResistanceKg)
        var high = ln(config.numericalMaximumResistanceKg)
        repeat(64) {
            val middle = (low + high) / 2.0
            val value = cdf(fit, repetitions, exp(middle))
            if (value < probability) low = middle else high = middle
        }
        return exp((low + high) / 2.0)
    }

    private data class SlackPoint(val standardised: Double, val probability: Double)

    companion object {
        private fun buildSlackPoints(config: DynamicStochasticFrontierConfig): List<SlackPoint> {
            val width = config.slackQuadratureMaximumSd / config.slackQuadraturePoints
            val raw = List(config.slackQuadraturePoints) { index ->
                val z = (index + 0.5) * width
                z to exp(0.5 * ln(2.0 / PI) - 0.5 * z * z + ln(width))
            }
            val total = raw.sumOf { it.second }
            return raw.map { SlackPoint(it.first, it.second / total) }
        }

        /** Student-t CDF via the regularised incomplete-beta identity; deterministic, no sampling. */
        private fun studentTCdf(t: Double, df: Double): Double {
            if (t == 0.0) return 0.5
            if (t == Double.POSITIVE_INFINITY) return 1.0
            if (t == Double.NEGATIVE_INFINITY) return 0.0
            require(t.isFinite() && df > 0.0)
            val x = df / (df + t * t)
            val ib = regularizedBeta(x, df / 2.0, 0.5)
            return if (t > 0.0) 1.0 - 0.5 * ib else 0.5 * ib
        }

        private fun regularizedBeta(x: Double, a: Double, b: Double): Double {
            if (x <= 0.0) return 0.0
            if (x >= 1.0) return 1.0
            val logBt = logGamma(a + b) - logGamma(a) - logGamma(b) + a * ln(x) + b * ln(1.0 - x)
            val bt = exp(logBt)
            return if (x < (a + 1.0) / (a + b + 2.0)) {
                bt * betaContinuedFraction(a, b, x) / a
            } else {
                1.0 - bt * betaContinuedFraction(b, a, 1.0 - x) / b
            }.coerceIn(0.0, 1.0)
        }

        private fun betaContinuedFraction(a: Double, b: Double, x: Double): Double {
            val maxIterations = 200
            val epsilon = 3e-14
            val tiny = 1e-300
            val qab = a + b
            val qap = a + 1.0
            val qam = a - 1.0
            var c = 1.0
            var d = 1.0 - qab * x / qap
            if (abs(d) < tiny) d = tiny
            d = 1.0 / d
            var h = d
            for (m in 1..maxIterations) {
                val m2 = 2 * m
                var aa = m.toDouble() * (b - m) * x / ((qam + m2) * (a + m2))
                d = 1.0 + aa * d
                if (abs(d) < tiny) d = tiny
                c = 1.0 + aa / c
                if (abs(c) < tiny) c = tiny
                d = 1.0 / d
                h *= d * c
                aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
                d = 1.0 + aa * d
                if (abs(d) < tiny) d = tiny
                c = 1.0 + aa / c
                if (abs(c) < tiny) c = tiny
                d = 1.0 / d
                val delta = d * c
                h *= delta
                if (abs(delta - 1.0) < epsilon) return h
            }
            return h
        }

        private fun logGamma(value: Double): Double {
            val coefficients = doubleArrayOf(
                676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7,
            )
            if (value < 0.5) return ln(PI) - ln(kotlin.math.sin(PI * value)) - logGamma(1.0 - value)
            val shifted = value - 1.0
            var sum = 0.99999999999980993
            coefficients.forEachIndexed { index, coefficient -> sum += coefficient / (shifted + index + 1.0) }
            val t = shifted + coefficients.size - 0.5
            return 0.5 * ln(2.0 * PI) + (shifted + 0.5) * ln(t) - t + ln(sum)
        }
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
