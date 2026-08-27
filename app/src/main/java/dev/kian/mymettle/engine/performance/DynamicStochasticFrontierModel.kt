package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitFailureReason
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitWarning
import dev.kian.mymettle.domain.inference.DynamicCapabilityModel
import dev.kian.mymettle.domain.inference.DynamicFrontierParameterPosterior
import dev.kian.mymettle.domain.inference.DynamicFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicObservationSlackPosterior
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicSlackPosteriorMass
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierConfig
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierV1
import dev.kian.mymettle.domain.inference.EvidenceFamily
import dev.kian.mymettle.domain.inference.EvidenceSupport
import dev.kian.mymettle.domain.inference.EvidenceSupportObservation
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Deterministic 7B.2 v1 stochastic-frontier implementation.
 *
 * Model:
 *   y_s = ln(R_s)
 *   x_s = ln(reps_s / r_ref)
 *   y_s = c - b*x_s - u_s + epsilon_s
 *   u_s ~ HalfNormal(sigma_u)
 *   epsilon_s ~ StudentT(df, 0, sigma_e)
 *
 * Global parameter uncertainty is approximated on a deterministic tensor grid. Per-observation
 * slack is integrated by deterministic midpoint quadrature. This is an approximation, not exact
 * Bayesian inference and not an exercise-physiology law.
 */
class DynamicStochasticFrontierModel(
    val config: DynamicStochasticFrontierConfig = DynamicStochasticFrontierV1.config,
) : DynamicCapabilityModel<DynamicStochasticFrontierFit> {
    override val modelVersion: String = config.semanticVersion

    private val quadrature: List<SlackQuadraturePoint> = buildSlackQuadrature(config)

    override fun fit(request: DynamicCapabilityFitRequest): DynamicStochasticFrontierFit {
        validateRequest(request)

        val selected = selectTemporalEvidence(request)
        if (selected.evidence.isEmpty()) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.NO_ELIGIBLE_EVIDENCE,
                "No eligible 7B.1 evidence exists at or before the inference horizon.",
            )
        }
        if (selected.evidence.any { it.evidencePolicyIdentity != config.evidencePolicyIdentity }) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.EVIDENCE_POLICY_MISMATCH,
                "Projected evidence was created under a different 7B.1 evidence policy.",
            )
        }

        val referenceRepetitions = DynamicReferenceRepSelector.select(
            selected.evidence,
            config.referenceRepPolicy,
        ) ?: throw DynamicCapabilityFitException(
            DynamicCapabilityFitFailureReason.NO_ELIGIBLE_EVIDENCE,
            "Selected evidence has no valid reference-repetition anchor.",
        )

        val observations = selected.evidence
            .sortedWith(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId })
            .map { evidence ->
                ModelObservation(
                    evidence = evidence,
                    y = ln(evidence.resistance.value),
                    x = ln(evidence.repetitions / referenceRepetitions),
                )
            }

        val support = EvidenceSupport.fromObservations(
            evidenceFamily = EvidenceFamily.fromMetricFamily(request.projection.profile.metricFamily),
            observations = observations.map {
                EvidenceSupportObservation(it.evidence.observationId, it.evidence.sessionId, it.evidence.completedAt)
            },
        )
        val repLogSpan = ln(observations.maxOf { it.evidence.repetitions }.toDouble() /
            observations.minOf { it.evidence.repetitions }.toDouble())
        val nuisanceLearningUnlocked =
            support.effectiveIndependentSessionCount >= config.nuisanceLearningMinimumIndependentSessions &&
                support.observationCount >= config.nuisanceLearningMinimumObservations

        val sessionCounts = observations.groupingBy { it.evidence.sessionId }.eachCount()
        val sessionWeights = sessionCounts.mapValues { (_, count) -> 1.0 / count.toDouble() }

        val cGrid = frontierGrid(observations)
        val logSlopeGrid = centredGrid(
            centre = ln(config.slopePriorMedian),
            sd = config.slopePriorLogSd,
            radius = config.slopePriorGridLogSdRadius,
            points = config.slopeGridPoints,
        )
        val logSlackScaleGrid = if (nuisanceLearningUnlocked) {
            centredGrid(
                ln(config.slackScalePriorMedian),
                config.slackScalePriorLogSd,
                config.nuisanceGridLogSdRadius,
                config.nuisanceScaleGridPoints,
            )
        } else {
            listOf(ln(config.slackScalePriorMedian))
        }
        val logNoiseScaleGrid = if (nuisanceLearningUnlocked) {
            centredGrid(
                ln(config.noiseScalePriorMedian),
                config.noiseScalePriorLogSd,
                config.nuisanceGridLogSdRadius,
                config.nuisanceScaleGridPoints,
            )
        } else {
            listOf(ln(config.noiseScalePriorMedian))
        }

        val requestedGridEvaluations = cGrid.size.toLong() * logSlopeGrid.size.toLong() *
            logSlackScaleGrid.size.toLong() * logNoiseScaleGrid.size.toLong()
        if (requestedGridEvaluations > config.maximumGridEvaluations) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.NUMERICAL_BUDGET_EXCEEDED,
                "7B.2 grid requires $requestedGridEvaluations nodes; configured maximum is ${config.maximumGridEvaluations}.",
            )
        }

        val rawNodes = ArrayList<RawPosteriorNode>(requestedGridEvaluations.toInt())
        for (c in cGrid) {
            for (logSlope in logSlopeGrid) {
                val slope = exp(logSlope)
                val logSlopePrior = normalLogDensity(logSlope, ln(config.slopePriorMedian), config.slopePriorLogSd)
                for (logSlackScale in logSlackScaleGrid) {
                    val slackScale = exp(logSlackScale)
                    val logSlackPrior = if (nuisanceLearningUnlocked) {
                        normalLogDensity(logSlackScale, ln(config.slackScalePriorMedian), config.slackScalePriorLogSd)
                    } else 0.0
                    for (logNoiseScale in logNoiseScaleGrid) {
                        val noiseScale = exp(logNoiseScale)
                        val logNoisePrior = if (nuisanceLearningUnlocked) {
                            normalLogDensity(logNoiseScale, ln(config.noiseScalePriorMedian), config.noiseScalePriorLogSd)
                        } else 0.0

                        var logPosterior = logSlopePrior + logSlackPrior + logNoisePrior
                        val noiseLogNormalisation = studentTLogNormalisation(
                            config.studentTDegreesOfFreedom,
                            noiseScale,
                        )
                        for (observation in observations) {
                            val frontier = c - slope * observation.x
                            val residual = observation.y - frontier
                            val logLikelihood = marginalObservationLogDensity(
                                residual = residual,
                                slackScale = slackScale,
                                noiseScale = noiseScale,
                                noiseLogNormalisation = noiseLogNormalisation,
                            )
                            logPosterior += requireNotNull(sessionWeights[observation.evidence.sessionId]) * logLikelihood
                        }
                        rawNodes += RawPosteriorNode(c, slope, slackScale, noiseScale, logPosterior)
                    }
                }
            }
        }

        val posteriorNodes = normalisePosterior(rawNodes)
        val provenance = ModelOutputProvenance(
            modelConfigId = request.modelConfig.id,
            modelManifestId = null,
            inferenceRunId = null,
            evidenceThrough = support.lastEvidenceAt,
        )
        val frontierSummary = weightedSummary(
            posteriorNodes.map { WeightedValue(exp(it.logFrontierAtReference), it.posteriorWeight) },
        )
        if (frontierSummary.p05 <= 0.0) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.DEGENERATE_POSTERIOR,
                "Reference-rep frontier posterior is not strictly positive.",
            )
        }

        val slopeIdentification = slopeIdentification(support, repLogSpan)
        val nuisanceIdentification = nuisanceIdentification(support, nuisanceLearningUnlocked)
        val observationSlack = observations.map { observation ->
            inferObservationSlack(
                observation = observation,
                posteriorNodes = posteriorNodes,
                identification = if (nuisanceLearningUnlocked) {
                    DynamicParameterIdentification.PARTIALLY_LEARNED
                } else {
                    DynamicParameterIdentification.PRIOR_DOMINATED
                },
            )
        }

        val warnings = buildSet {
            add(DynamicCapabilityFitWarning.APPROXIMATE_POSTERIOR)
            if (selected.temporalWindowTruncated) add(DynamicCapabilityFitWarning.TEMPORAL_WINDOW_TRUNCATED)
            if (slopeIdentification == DynamicParameterIdentification.PRIOR_DOMINATED) {
                add(DynamicCapabilityFitWarning.SLOPE_PRIOR_DOMINATED)
            }
            if (!nuisanceLearningUnlocked) add(DynamicCapabilityFitWarning.NUISANCE_SCALES_FIXED)
        }

        return DynamicStochasticFrontierFit(
            executionProfileVersionId = request.projection.profile.executionProfileVersionId,
            side = request.projection.side,
            inferenceHorizon = request.inferenceHorizon,
            referenceRepetitions = referenceRepetitions,
            modelConfigId = request.modelConfig.id,
            modelVersion = modelVersion,
            evidencePolicyIdentity = config.evidencePolicyIdentity,
            support = support,
            observedRepMin = observations.minOf { it.evidence.repetitions },
            observedRepMax = observations.maxOf { it.evidence.repetitions },
            observedResistanceMinKg = observations.minOf { it.evidence.resistance.value },
            observedResistanceMaxKg = observations.maxOf { it.evidence.resistance.value },
            frontierAtReference = PosteriorEstimate(frontierSummary, support, provenance),
            slope = DynamicFrontierParameterPosterior(
                weightedSummary(posteriorNodes.map { WeightedValue(it.slope, it.posteriorWeight) }),
                slopeIdentification,
                "positive log-resistance per log-repetition ratio",
            ),
            slackScale = DynamicFrontierParameterPosterior(
                weightedSummary(posteriorNodes.map { WeightedValue(it.slackScale, it.posteriorWeight) }),
                nuisanceIdentification,
                "log-performance HalfNormal scale",
            ),
            noiseScale = DynamicFrontierParameterPosterior(
                weightedSummary(posteriorNodes.map { WeightedValue(it.noiseScale, it.posteriorWeight) }),
                nuisanceIdentification,
                "log-performance Student-t scale",
            ),
            observationSlack = observationSlack,
            selectedObservationIds = observations.map { it.evidence.observationId },
            selectedSessionIds = observations.map { it.evidence.sessionId }.distinct(),
            approximationVersion = DynamicStochasticFrontierV1.APPROXIMATION_VERSION,
            warnings = warnings,
            posteriorNodes = posteriorNodes,
        )
    }

    override fun predictFrontier(fit: DynamicStochasticFrontierFit, repetitions: Double): PosteriorEstimate {
        require(repetitions.isFinite() && repetitions > 0.0) { "Frontier prediction requires finite positive repetitions." }
        val x = ln(repetitions / fit.referenceRepetitions)
        val logValues = fit.posteriorNodes.map { node ->
            WeightedValue(node.logFrontierAtReference - node.slope * x, node.posteriorWeight)
        }
        val minLog = ln(config.numericalMinimumResistanceKg)
        val maxLog = ln(config.numericalMaximumResistanceKg)
        if (logValues.any { !it.value.isFinite() || it.value !in minLog..maxLog }) {
            return PosteriorEstimate(
                summary = null,
                support = fit.support,
                provenance = fit.frontierAtReference.provenance,
            )
        }

        val base = weightedSummary(logValues.map { WeightedValue(exp(it.value), it.weight) })
        val distanceOutside = when {
            repetitions < fit.observedRepMin -> ln(fit.observedRepMin / repetitions)
            repetitions > fit.observedRepMax -> ln(repetitions / fit.observedRepMax)
            else -> 0.0
        }
        if (distanceOutside == 0.0 || config.extrapolationLogSdPerUnitOutsideDomain == 0.0) {
            return PosteriorEstimate(base, fit.support, fit.frontierAtReference.provenance)
        }

        val meanLog = weightedMean(logValues)
        val baseLogVariance = weightedVariance(logValues, meanLog)
        val extraLogSd = config.extrapolationLogSdPerUnitOutsideDomain * distanceOutside
        val totalLogVariance = baseLogVariance + extraLogSd * extraLogSd
        val totalLogSd = sqrt(totalLogVariance)
        val z90 = 1.6448536269514722
        val lowerFromApproximation = exp(meanLog - z90 * totalLogSd)
        val upperFromApproximation = exp(meanLog + z90 * totalLogSd)
        val logNormalVariance = (exp(totalLogVariance) - 1.0) * exp(2.0 * meanLog + totalLogVariance)
        val widened = PosteriorSummary(
            credibleLower05 = min(base.p05, lowerFromApproximation),
            estimateMedian = base.p50,
            credibleUpper95 = max(base.p95, upperFromApproximation),
            posteriorVariance = max(base.posteriorVariance, logNormalVariance),
        )
        return PosteriorEstimate(widened, fit.support, fit.frontierAtReference.provenance)
    }

    /**
     * Predictive density of an ordinary successful demonstration, marginalising fitted slack/noise.
     * 7B.3 may use this for lower-bound calibration without reconstructing model internals.
     */
    fun demonstrationLogPredictiveDensity(
        fit: DynamicStochasticFrontierFit,
        repetitions: Double,
        resistanceKg: Double,
    ): Double {
        require(repetitions.isFinite() && repetitions > 0.0)
        require(resistanceKg.isFinite() && resistanceKg > 0.0)
        val x = ln(repetitions / fit.referenceRepetitions)
        val y = ln(resistanceKg)
        val terms = fit.posteriorNodes.map { node ->
            val frontier = node.logFrontierAtReference - node.slope * x
            val residual = y - frontier
            ln(node.posteriorWeight) + marginalObservationLogDensity(
                residual = residual,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                noiseLogNormalisation = studentTLogNormalisation(
                    config.studentTDegreesOfFreedom,
                    node.noiseScale,
                ),
            )
        }
        return logSumExp(terms)
    }

    /** Future SetDemand hook. No delta is chosen by 7B.2 and the result is explicitly not RIR. */
    fun slackProbabilityAtMost(
        fit: DynamicStochasticFrontierFit,
        observationId: String,
        deltaLogPerformance: Double,
    ): Double {
        require(deltaLogPerformance.isFinite() && deltaLogPerformance >= 0.0)
        val posterior = fit.observationSlack.firstOrNull { it.observationId == observationId }
            ?: throw IllegalArgumentException("Observation $observationId is not part of this fit.")
        return posterior.massPoints
            .filter { it.slack <= deltaLogPerformance }
            .sumOf { it.probability }
            .coerceIn(0.0, 1.0)
    }

    /** Exposed for deterministic symmetry/robustness tests; the noise family itself remains config-driven. */
    fun noiseLogDensity(residual: Double, scale: Double = config.noiseScalePriorMedian): Double {
        require(residual.isFinite() && scale.isFinite() && scale > 0.0)
        return studentTLogDensity(
            residual,
            config.studentTDegreesOfFreedom,
            scale,
            studentTLogNormalisation(config.studentTDegreesOfFreedom, scale),
        )
    }

    private fun validateRequest(request: DynamicCapabilityFitRequest) {
        val expected = config.toModelConfig(request.modelConfig.createdAt)
        if (request.modelConfig.id != expected.id || request.modelConfig.canonicalConfigPayload != expected.canonicalConfigPayload) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.MODEL_CONFIG_MISMATCH,
                "Fit request does not use the immutable config expected by $modelVersion.",
            )
        }
        if (request.projection.policy.identity != config.evidencePolicyIdentity) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.EVIDENCE_POLICY_MISMATCH,
                "7B.2 config expects a different 7B.1 evidence-policy identity.",
            )
        }
    }

    private data class TemporalSelection(
        val evidence: List<DynamicResistanceEvidence>,
        val temporalWindowTruncated: Boolean,
    )

    private fun selectTemporalEvidence(request: DynamicCapabilityFitRequest): TemporalSelection {
        val atHorizon = request.projection.evidence
            .filter { !it.completedAt.isAfter(request.inferenceHorizon) }
        val bySession = atHorizon.groupBy { it.sessionId }
        val orderedSessionIds = bySession.entries
            .sortedWith(
                compareBy<Map.Entry<String, List<DynamicResistanceEvidence>>> { entry ->
                    entry.value.maxOf { it.completedAt }
                }.thenBy { it.key },
            )
            .map { it.key }
        val selectedSessionIds = orderedSessionIds.takeLast(config.recentIndependentSessionWindow).toSet()
        return TemporalSelection(
            evidence = atHorizon
                .filter { it.sessionId in selectedSessionIds }
                .sortedWith(compareBy<DynamicResistanceEvidence> { it.completedAt }.thenBy { it.observationId }),
            temporalWindowTruncated = orderedSessionIds.size > config.recentIndependentSessionWindow,
        )
    }

    private fun frontierGrid(observations: List<ModelObservation>): List<Double> {
        val minObserved = observations.minOf { it.y }
        val maxObserved = observations.maxOf { it.y }
        val lower = max(ln(config.frontierPriorMinimumKg), minObserved - config.frontierGridLowerMarginLog)
        val upper = min(ln(config.frontierPriorMaximumKg), maxObserved + config.frontierGridUpperMarginLog)
        if (!lower.isFinite() || !upper.isFinite() || lower >= upper) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.DEGENERATE_POSTERIOR,
                "Observed resistance lies outside the configured proper frontier-prior/numerical domain.",
            )
        }
        return linearGrid(lower, upper, config.frontierGridPoints)
    }

    private fun normalisePosterior(rawNodes: List<RawPosteriorNode>): List<DynamicFrontierPosteriorNode> {
        if (rawNodes.isEmpty() || rawNodes.none { it.logPosterior.isFinite() }) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR,
                "No finite posterior grid node was produced.",
            )
        }
        val maxLog = rawNodes.filter { it.logPosterior.isFinite() }.maxOf { it.logPosterior }
        val unnormalised = rawNodes.map { node ->
            if (node.logPosterior.isFinite()) exp(node.logPosterior - maxLog) else 0.0
        }
        val total = unnormalised.sum()
        if (!total.isFinite() || total <= 0.0) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR,
                "Posterior normalisation failed.",
            )
        }
        return rawNodes.indices.map { index ->
            val raw = rawNodes[index]
            DynamicFrontierPosteriorNode(
                logFrontierAtReference = raw.c,
                slope = raw.slope,
                slackScale = raw.slackScale,
                noiseScale = raw.noiseScale,
                posteriorWeight = unnormalised[index] / total,
            )
        }
    }

    private fun inferObservationSlack(
        observation: ModelObservation,
        posteriorNodes: List<DynamicFrontierPosteriorNode>,
        identification: DynamicParameterIdentification,
    ): DynamicObservationSlackPosterior {
        val top = posteriorNodes
            .sortedByDescending { it.posteriorWeight }
            .take(config.slackPosteriorTopNodeCount)
        val topWeightTotal = top.sumOf { it.posteriorWeight }
        if (topWeightTotal <= 0.0 || !topWeightTotal.isFinite()) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.DEGENERATE_POSTERIOR,
                "Top posterior nodes carry no probability mass for slack inference.",
            )
        }

        val mass = ArrayList<DynamicSlackPosteriorMass>(top.size * quadrature.size)
        for (node in top) {
            val frontier = node.logFrontierAtReference - node.slope * observation.x
            val residual = observation.y - frontier
            val noiseNorm = studentTLogNormalisation(config.studentTDegreesOfFreedom, node.noiseScale)
            val localLogWeights = quadrature.map { point ->
                point.logPriorMass + studentTLogDensity(
                    residual + node.slackScale * point.standardisedSlack,
                    config.studentTDegreesOfFreedom,
                    node.noiseScale,
                    noiseNorm,
                )
            }
            val localLogNormaliser = logSumExp(localLogWeights)
            val globalWeight = node.posteriorWeight / topWeightTotal
            quadrature.forEachIndexed { index, point ->
                mass += DynamicSlackPosteriorMass(
                    slack = node.slackScale * point.standardisedSlack,
                    probability = globalWeight * exp(localLogWeights[index] - localLogNormaliser),
                )
            }
        }
        val massTotal = mass.sumOf { it.probability }
        val normalisedMass = mass.map { it.copy(probability = it.probability / massTotal) }
        val summary = weightedSummary(normalisedMass.map { WeightedValue(it.slack, it.probability) })
        return DynamicObservationSlackPosterior(
            observationId = observation.evidence.observationId,
            summary = summary,
            identification = identification,
            massPoints = normalisedMass,
        )
    }

    private fun marginalObservationLogDensity(
        residual: Double,
        slackScale: Double,
        noiseScale: Double,
        noiseLogNormalisation: Double,
    ): Double {
        val terms = quadrature.map { point ->
            point.logPriorMass + studentTLogDensity(
                residual + slackScale * point.standardisedSlack,
                config.studentTDegreesOfFreedom,
                noiseScale,
                noiseLogNormalisation,
            )
        }
        return logSumExp(terms)
    }

    private fun slopeIdentification(
        support: EvidenceSupport,
        repLogSpan: Double,
    ): DynamicParameterIdentification = when {
        support.effectiveIndependentSessionCount >= config.slopeDataInformedMinimumIndependentSessions &&
            repLogSpan >= config.slopeDataInformedMinimumLogRepSpan -> DynamicParameterIdentification.DATA_INFORMED
        support.effectiveIndependentSessionCount >= config.slopePartialMinimumIndependentSessions &&
            repLogSpan >= config.slopePartialMinimumLogRepSpan -> DynamicParameterIdentification.PARTIALLY_LEARNED
        else -> DynamicParameterIdentification.PRIOR_DOMINATED
    }

    private fun nuisanceIdentification(
        support: EvidenceSupport,
        learningUnlocked: Boolean,
    ): DynamicParameterIdentification = when {
        !learningUnlocked -> DynamicParameterIdentification.FIXED_BY_CONFIG
        support.effectiveIndependentSessionCount >= config.nuisanceDataInformedMinimumIndependentSessions &&
            support.observationCount >= config.nuisanceDataInformedMinimumObservations ->
            DynamicParameterIdentification.DATA_INFORMED
        else -> DynamicParameterIdentification.PARTIALLY_LEARNED
    }

    private fun weightedSummary(values: List<WeightedValue>): PosteriorSummary {
        require(values.isNotEmpty())
        val totalWeight = values.sumOf { it.weight }
        require(totalWeight.isFinite() && totalWeight > 0.0)
        val normalised = values.map { WeightedValue(it.value, it.weight / totalWeight) }
        val mean = weightedMean(normalised)
        val variance = weightedVariance(normalised, mean)
        return PosteriorSummary(
            credibleLower05 = weightedQuantile(normalised, 0.05),
            estimateMedian = weightedQuantile(normalised, 0.50),
            credibleUpper95 = weightedQuantile(normalised, 0.95),
            posteriorVariance = variance,
        )
    }

    private fun weightedMean(values: List<WeightedValue>): Double = values.sumOf { it.value * it.weight } /
        values.sumOf { it.weight }

    private fun weightedVariance(values: List<WeightedValue>, mean: Double): Double {
        val total = values.sumOf { it.weight }
        return values.sumOf { it.weight * (it.value - mean).pow(2) } / total
    }

    private fun weightedQuantile(values: List<WeightedValue>, probability: Double): Double {
        require(probability in 0.0..1.0)
        val ordered = values.sortedBy { it.value }
        val total = ordered.sumOf { it.weight }
        val target = probability * total
        var cumulative = 0.0
        for (value in ordered) {
            cumulative += value.weight
            if (cumulative >= target) return value.value
        }
        return ordered.last().value
    }

    private fun studentTLogDensity(
        residual: Double,
        degreesOfFreedom: Double,
        scale: Double,
        logNormalisation: Double,
    ): Double = logNormalisation -
        ((degreesOfFreedom + 1.0) / 2.0) * ln1p((residual / scale).pow(2) / degreesOfFreedom)

    private fun studentTLogNormalisation(degreesOfFreedom: Double, scale: Double): Double =
        logGamma((degreesOfFreedom + 1.0) / 2.0) -
            logGamma(degreesOfFreedom / 2.0) -
            0.5 * ln(degreesOfFreedom * PI) -
            ln(scale)

    private fun normalLogDensity(value: Double, mean: Double, sd: Double): Double {
        val z = (value - mean) / sd
        return -0.5 * z * z - ln(sd) - 0.5 * ln(2.0 * PI)
    }

    private fun logSumExp(values: List<Double>): Double {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return Double.NEGATIVE_INFINITY
        val maximum = finite.max()
        return maximum + ln(finite.sumOf { exp(it - maximum) })
    }

    private fun centredGrid(
        centre: Double,
        sd: Double,
        radius: Double,
        points: Int,
    ): List<Double> = linearGrid(centre - radius * sd, centre + radius * sd, points)

    private fun linearGrid(minimum: Double, maximum: Double, points: Int): List<Double> {
        if (points == 1) return listOf((minimum + maximum) / 2.0)
        val step = (maximum - minimum) / (points - 1).toDouble()
        return List(points) { index -> minimum + step * index }
    }

    private data class ModelObservation(
        val evidence: DynamicResistanceEvidence,
        val y: Double,
        val x: Double,
    )

    private data class RawPosteriorNode(
        val c: Double,
        val slope: Double,
        val slackScale: Double,
        val noiseScale: Double,
        val logPosterior: Double,
    )

    private data class WeightedValue(val value: Double, val weight: Double)

    private data class SlackQuadraturePoint(
        val standardisedSlack: Double,
        val logPriorMass: Double,
    )

    companion object {
        private fun buildSlackQuadrature(config: DynamicStochasticFrontierConfig): List<SlackQuadraturePoint> {
            val width = config.slackQuadratureMaximumSd / config.slackQuadraturePoints.toDouble()
            return List(config.slackQuadraturePoints) { index ->
                val z = (index + 0.5) * width
                SlackQuadraturePoint(
                    standardisedSlack = z,
                    logPriorMass = 0.5 * ln(2.0 / PI) - 0.5 * z * z + ln(width),
                )
            }
        }

        /** Lanczos log-gamma; deterministic numerical implementation, versioned by approximationVersion. */
        private fun logGamma(value: Double): Double {
            require(value > 0.0 && value.isFinite())
            val coefficients = doubleArrayOf(
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7,
            )
            if (value < 0.5) {
                return ln(PI) - ln(kotlin.math.sin(PI * value)) - logGamma(1.0 - value)
            }
            val z = value - 1.0
            var x = 0.99999999999980993
            coefficients.forEachIndexed { index, coefficient ->
                x += coefficient / (z + index + 1.0)
            }
            val t = z + coefficients.size - 0.5
            return 0.5 * ln(2.0 * PI) + (z + 0.5) * ln(t) - t + ln(x)
        }
    }
}
