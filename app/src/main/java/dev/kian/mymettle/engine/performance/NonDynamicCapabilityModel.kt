package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.inference.DurationOnlyCapabilityQuery
import dev.kian.mymettle.domain.inference.DurationOnlyEvidence
import dev.kian.mymettle.domain.inference.DynamicParameterIdentification
import dev.kian.mymettle.domain.inference.EvidenceFamily
import dev.kian.mymettle.domain.inference.EvidenceSupport
import dev.kian.mymettle.domain.inference.EvidenceSupportObservation
import dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation
import dev.kian.mymettle.domain.inference.InferenceSolverDiagnostics
import dev.kian.mymettle.domain.inference.InferenceSolverFamily
import dev.kian.mymettle.domain.inference.LoadedHoldCapabilityQuery
import dev.kian.mymettle.domain.inference.LoadedHoldEvidence
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityEvidence
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFit
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityQuery
import dev.kian.mymettle.domain.inference.NonDynamicEvidenceProjection
import dev.kian.mymettle.domain.inference.NonDynamicFamilyConfig
import dev.kian.mymettle.domain.inference.NonDynamicFitFailureReason
import dev.kian.mymettle.domain.inference.NonDynamicParameterPosterior
import dev.kian.mymettle.domain.inference.NonDynamicPosteriorNode
import dev.kian.mymettle.domain.inference.NonDynamicSolverConfig
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.inference.RepeatedContractionCapabilityQuery
import dev.kian.mymettle.domain.inference.RepeatedContractionEvidence
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.UnitId
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

interface NonDynamicCapabilitySolver {
    val familyConfig: NonDynamicFamilyConfig
    val solverConfig: NonDynamicSolverConfig

    fun fit(
        projection: NonDynamicEvidenceProjection,
        inferenceHorizon: Instant,
        configCreatedAt: Instant,
    ): NonDynamicCapabilityFit

    fun predict(fit: NonDynamicCapabilityFit, query: NonDynamicCapabilityQuery): PosteriorEstimate
}

class NonDynamicDenseReferenceSolver(
    override val familyConfig: NonDynamicFamilyConfig,
) : NonDynamicCapabilitySolver {
    override val solverConfig = NonDynamicSolverConfig(
        familyConfig = familyConfig,
        solverFamily = InferenceSolverFamily.DENSE_TENSOR_REFERENCE,
    )
    private val core = NonDynamicLogFrontierCore(solverConfig)

    override fun fit(projection: NonDynamicEvidenceProjection, inferenceHorizon: Instant, configCreatedAt: Instant) =
        core.fit(projection, inferenceHorizon, configCreatedAt)

    override fun predict(fit: NonDynamicCapabilityFit, query: NonDynamicCapabilityQuery) = core.predict(fit, query)
}

class NonDynamicAdaptiveSparseSolver(
    override val familyConfig: NonDynamicFamilyConfig,
) : NonDynamicCapabilitySolver {
    override val solverConfig = NonDynamicSolverConfig(
        familyConfig = familyConfig,
        solverFamily = InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR,
    )
    private val core = NonDynamicLogFrontierCore(solverConfig)

    override fun fit(projection: NonDynamicEvidenceProjection, inferenceHorizon: Instant, configCreatedAt: Instant) =
        core.fit(projection, inferenceHorizon, configCreatedAt)

    override fun predict(fit: NonDynamicCapabilityFit, query: NonDynamicCapabilityQuery) = core.predict(fit, query)
}

private class NonDynamicLogFrontierCore(
    private val solverConfig: NonDynamicSolverConfig,
) {
    private val config = solverConfig.familyConfig
    private val quadrature = buildSlackQuadrature(solverConfig)

    fun fit(
        projection: NonDynamicEvidenceProjection,
        inferenceHorizon: Instant,
        configCreatedAt: Instant,
    ): NonDynamicCapabilityFit {
        val started = System.nanoTime()
        validateProjection(projection)
        val selected = selectTemporalEvidence(projection, inferenceHorizon)
        if (selected.evidence.isEmpty()) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.NO_ELIGIBLE_EVIDENCE,
                "No eligible ${config.family.storageValue} evidence exists at or before the inference horizon.",
            )
        }
        if (selected.evidence.any { it.evidencePolicyIdentity != config.evidencePolicyIdentity }) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.EVIDENCE_POLICY_MISMATCH,
                "Projected 7C evidence was created under a different evidence policy.",
            )
        }
        validateContext(selected.evidence)

        val reference = referenceCoordinate(selected.evidence)
        val sessionOrder = selected.evidence.groupBy { it.sessionId }.entries
            .sortedWith(compareBy<Map.Entry<String, List<NonDynamicCapabilityEvidence>>> { it.value.maxOf { e -> e.completedAt } }.thenBy { it.key })
            .map { it.key }
        val zBySession = sessionOrder.mapIndexed { index, id -> id to (index - (sessionOrder.size - 1)).toDouble() }.toMap()
        val observations = selected.evidence
            .sortedWith(compareBy<NonDynamicCapabilityEvidence> { it.completedAt }.thenBy { it.observationId })
            .map { evidence -> modelObservation(evidence, reference, requireNotNull(zBySession[evidence.sessionId])) }
        val support = EvidenceSupport.fromObservations(
            EvidenceFamily.fromMetricFamily(config.family),
            observations.map { EvidenceSupportObservation(it.evidence.observationId, it.evidence.sessionId, it.evidence.completedAt) },
        )
        val inputLogSpan = if (config.family == MetricFamily.DURATION_ONLY) 0.0 else {
            val values = observations.mapNotNull { it.inputCoordinate }
            ln(values.max() / values.min())
        }
        val nuisanceLearning = support.effectiveIndependentSessionCount >= config.nuisanceLearningMinimumSessions &&
            support.observationCount >= config.nuisanceLearningMinimumObservations
        val sessionCounts = observations.groupingBy { it.evidence.sessionId }.eachCount()
        val sessionWeights = sessionCounts.mapValues { 1.0 / it.value.toDouble() }

        val cGrid = frontierGrid(observations)
        val slopeGrid = if (config.slopePriorMedian == null) listOf<Double?>(null) else centredGrid(
            ln(requireNotNull(config.slopePriorMedian)),
            requireNotNull(config.slopePriorLogSd),
            3.0,
            solverConfig.slopeGridPoints,
        ).map { exp(it) }
        val slackGrid = if (nuisanceLearning) centredGrid(
            ln(config.slackScalePriorMedian), config.slackScalePriorLogSd, 1.0, solverConfig.nuisanceGridPoints,
        ).map(::exp) else listOf(config.slackScalePriorMedian)
        val noiseGrid = if (nuisanceLearning) centredGrid(
            ln(config.noiseScalePriorMedian), config.noiseScalePriorLogSd, 1.0, solverConfig.nuisanceGridPoints,
        ).map(::exp) else listOf(config.noiseScalePriorMedian)

        val baseNodeBudget = cGrid.size.toLong() * slopeGrid.size * slackGrid.size * noiseGrid.size
        if (baseNodeBudget > MAX_BASE_GRID_NODES) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.NUMERICAL_BUDGET_EXCEEDED,
                "7C base grid requires $baseNodeBudget nodes; maximum is $MAX_BASE_GRID_NODES.",
            )
        }
        val baseRaw = ArrayList<BaseRawNode>(baseNodeBudget.toInt())
        for (c in cGrid) {
            for (slope in slopeGrid) {
                val slopePrior = if (slope == null) 0.0 else normalLogDensity(
                    ln(slope), ln(requireNotNull(config.slopePriorMedian)), requireNotNull(config.slopePriorLogSd),
                )
                for (slack in slackGrid) {
                    val slackPrior = if (nuisanceLearning) normalLogDensity(
                        ln(slack), ln(config.slackScalePriorMedian), config.slackScalePriorLogSd,
                    ) else 0.0
                    for (noise in noiseGrid) {
                        val noisePrior = if (nuisanceLearning) normalLogDensity(
                            ln(noise), ln(config.noiseScalePriorMedian), config.noiseScalePriorLogSd,
                        ) else 0.0
                        var logLikelihood = 0.0
                        for (observation in observations) {
                            logLikelihood += requireNotNull(sessionWeights[observation.evidence.sessionId]) *
                                marginalLogLikelihood(observation, c, slope, 0.0, slack, noise)
                        }
                        baseRaw += BaseRawNode(c, slope, slack, noise, slopePrior + slackPrior + noisePrior + logLikelihood)
                    }
                }
            }
        }
        val basePosterior = normaliseBase(baseRaw)
        val retained = if (solverConfig.solverFamily == InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR) {
            pruneBase(basePosterior)
        } else BaseSelection(basePosterior, basePosterior.size, 1.0)

        val trendGrid = if (support.effectiveIndependentSessionCount < config.trajectoryLearningMinimumSessions) {
            listOf(0.0)
        } else centredGrid(0.0, config.trajectoryPriorSd, 3.0, solverConfig.trajectoryGridPoints)
        val expandedBudget = retained.nodes.size.toLong() * trendGrid.size
        if (expandedBudget > MAX_EXPANDED_GRID_NODES) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.NUMERICAL_BUDGET_EXCEEDED,
                "7C trajectory expansion requires $expandedBudget nodes; maximum is $MAX_EXPANDED_GRID_NODES.",
            )
        }
        val expandedRaw = ArrayList<ExpandedRawNode>(expandedBudget.toInt())
        for (base in retained.nodes) {
            for (trajectory in trendGrid) {
                var logRatio = 0.0
                if (trajectory != 0.0) {
                    for (observation in observations) {
                        val w = requireNotNull(sessionWeights[observation.evidence.sessionId])
                        val current = marginalLogLikelihood(observation, base.c, base.slope, trajectory, base.slackScale, base.noiseScale)
                        val baseline = marginalLogLikelihood(observation, base.c, base.slope, 0.0, base.slackScale, base.noiseScale)
                        logRatio += w * (current - baseline)
                    }
                }
                val trendPrior = if (trendGrid.size == 1) 0.0 else normalLogDensity(trajectory, 0.0, config.trajectoryPriorSd)
                expandedRaw += ExpandedRawNode(
                    base.c,
                    base.slope,
                    trajectory,
                    base.slackScale,
                    base.noiseScale,
                    ln(base.weight) + trendPrior + logRatio,
                )
            }
        }
        val nodes = normaliseExpanded(expandedRaw)
        val modelConfig = config.toModelConfig(configCreatedAt)
        val provenance = ModelOutputProvenance(
            modelConfigId = modelConfig.id,
            modelManifestId = null,
            inferenceRunId = null,
            evidenceThrough = support.lastEvidenceAt,
        )
        val frontierSummary = summary(nodes.map { WeightedValue(exp(it.logFrontierAtReference), it.posteriorWeight) })
        val slopePosterior = if (config.slopePriorMedian == null) null else NonDynamicParameterPosterior(
            summary(nodes.map { WeightedValue(requireNotNull(it.slope), it.posteriorWeight) }),
            slopeIdentification(support, inputLogSpan),
            when (config.family) {
                MetricFamily.LOADED_HOLD -> "positive log-resistance per log-duration ratio"
                MetricFamily.REPEATED_CONTRACTION -> "positive log-resistance per log-cycle ratio"
                else -> error("No slope for ${config.family}")
            },
        )
        val nuisanceIdentification = nuisanceIdentification(support, nuisanceLearning)
        val trajectoryIdentification = when {
            support.effectiveIndependentSessionCount < config.trajectoryLearningMinimumSessions -> DynamicParameterIdentification.FIXED_BY_CONFIG
            support.effectiveIndependentSessionCount >= 6 -> DynamicParameterIdentification.DATA_INFORMED
            else -> DynamicParameterIdentification.PARTIALLY_LEARNED
        }
        val observedInputs = observations.mapNotNull { it.inputCoordinate }
        val observedOutputs = observations.map { it.output }
        val canonicalUnit = if (config.family == MetricFamily.DURATION_ONLY) UnitId.SECOND else UnitId.KILOGRAM
        val warnings = buildSet {
            add("lower_bound_capability_not_action_policy")
            if (selected.truncated) add("temporal_window_truncated")
            if (slopePosterior?.identification == DynamicParameterIdentification.PRIOR_DOMINATED) add("slope_prior_dominated")
            if (!nuisanceLearning) add("nuisance_scales_fixed")
            if (solverConfig.solverFamily == InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR) {
                add("adaptive_sparse_base_support")
                add("retained_base_mass=${retained.massBeforeRenormalisation}")
            }
            if (config.family == MetricFamily.DURATION_ONLY && selected.evidence.mapNotNull { (it as DurationOnlyEvidence).bodyMassContextKg }.distinct().size > 1) {
                add("body_mass_context_varies_but_is_not_model_driving_v1")
            }
        }
        return NonDynamicCapabilityFit(
            executionProfileVersionId = projection.profile.executionProfileVersionId,
            side = projection.side,
            family = config.family,
            inferenceHorizon = inferenceHorizon,
            referenceCoordinate = reference,
            canonicalUnit = canonicalUnit,
            modelConfigId = modelConfig.id,
            mathematicalModelIdentity = config.mathematicalModelIdentity,
            solverDiagnostics = InferenceSolverDiagnostics(
                solverIdentity = solverConfig.solverIdentity,
                posteriorRepresentation = if (solverConfig.solverFamily == InferenceSolverFamily.DENSE_TENSOR_REFERENCE) {
                    InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES
                } else InferencePosteriorRepresentation.WEIGHTED_SPARSE_NODES,
                evaluatedNodeCount = baseRaw.size.toLong() + expandedRaw.size.toLong(),
                effectiveNodeCount = 1.0 / nodes.sumOf { it.posteriorWeight * it.posteriorWeight },
                updateRuntimeNanos = System.nanoTime() - started,
                notes = setOf(
                    "n_bio_7c_same_family_mathematics",
                    "base_posterior_then_trajectory_expansion",
                    "action_policy_unmodelled",
                ),
            ),
            evidencePolicyIdentity = config.evidencePolicyIdentity,
            support = support,
            observedInputMin = observedInputs.minOrNull(),
            observedInputMax = observedInputs.maxOrNull(),
            observedOutputMin = observedOutputs.min(),
            observedOutputMax = observedOutputs.max(),
            frontierAtReference = PosteriorEstimate(frontierSummary, support, provenance),
            slope = slopePosterior,
            trajectory = NonDynamicParameterPosterior(
                summary(nodes.map { WeightedValue(it.trajectory, it.posteriorWeight) }),
                trajectoryIdentification,
                "log-performance per independent-session step",
            ),
            slackScale = NonDynamicParameterPosterior(
                summary(nodes.map { WeightedValue(it.slackScale, it.posteriorWeight) }),
                nuisanceIdentification,
                "log-performance HalfNormal scale",
            ),
            noiseScale = NonDynamicParameterPosterior(
                summary(nodes.map { WeightedValue(it.noiseScale, it.posteriorWeight) }),
                nuisanceIdentification,
                "log-performance Student-t scale",
            ),
            posteriorNodes = nodes,
            selectedObservationIds = observations.map { it.evidence.observationId },
            selectedSessionIds = sessionOrder,
            originalBaseNodeCount = basePosterior.size,
            retainedBaseNodeCount = retained.nodes.size,
            warnings = warnings,
        )
    }

    fun predict(fit: NonDynamicCapabilityFit, query: NonDynamicCapabilityQuery): PosteriorEstimate {
        require(fit.mathematicalModelIdentity == config.mathematicalModelIdentity) { "Fit mathematical identity does not match this 7C solver." }
        val (input, offset) = when (query) {
            is LoadedHoldCapabilityQuery -> {
                require(fit.family == MetricFamily.LOADED_HOLD) { "Loaded-hold query cannot be used with ${fit.family.storageValue}." }
                query.durationSeconds to query.independentSessionOffset
            }
            is DurationOnlyCapabilityQuery -> {
                require(fit.family == MetricFamily.DURATION_ONLY) { "Duration-only query cannot be used with ${fit.family.storageValue}." }
                null to query.independentSessionOffset
            }
            is RepeatedContractionCapabilityQuery -> {
                require(fit.family == MetricFamily.REPEATED_CONTRACTION) { "Repeated-contraction query cannot be used with ${fit.family.storageValue}." }
                query.cycles.toDouble() to query.independentSessionOffset
            }
        }
        val x = if (input == null) 0.0 else ln(input / requireNotNull(fit.referenceCoordinate))
        val safeExpLogMin = ln(Double.MIN_NORMAL)
        val safeExpLogMax = ln(Double.MAX_VALUE)
        val logValues = fit.posteriorNodes.map { node ->
            val value = node.logFrontierAtReference + node.trajectory * offset - (node.slope ?: 0.0) * x
            if (!value.isFinite() || value !in safeExpLogMin..safeExpLogMax) {
                throw NonDynamicCapabilityFitException(
                    NonDynamicFitFailureReason.NON_FINITE_POSTERIOR,
                    "7C ${fit.family.storageValue} query cannot be represented safely in finite positive output space.",
                )
            }
            WeightedValue(value, node.posteriorWeight)
        }
        val direct = summary(logValues.map { WeightedValue(exp(it.value), it.weight) })
        if (direct.p50 !in config.outputPriorMinimum..config.outputPriorMaximum) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.NON_FINITE_POSTERIOR,
                "7C ${fit.family.storageValue} query median left the configured numerical output domain; prediction is unavailable.",
            )
        }
        val inputDistance = if (input == null || fit.observedInputMin == null || fit.observedInputMax == null) 0.0 else when {
            input < fit.observedInputMin -> ln(fit.observedInputMin / input)
            input > fit.observedInputMax -> ln(input / fit.observedInputMax)
            else -> 0.0
        }
        val outputDistance = when {
            direct.p50 < fit.observedOutputMin -> ln(fit.observedOutputMin / direct.p50)
            direct.p50 > fit.observedOutputMax -> ln(direct.p50 / fit.observedOutputMax)
            else -> 0.0
        }
        val processSd = config.processLogSdPerSqrtSession * sqrt(abs(offset.toDouble()))
        val inputSd = config.inputExtrapolationLogSdPerLogUnit * inputDistance
        val outputSd = config.outputExtrapolationLogSdPerLogUnit * outputDistance
        val extraVariance = processSd * processSd + inputSd * inputSd + outputSd * outputSd
        if (extraVariance <= 0.0) {
            return PosteriorEstimate(direct, fit.support, fit.frontierAtReference.provenance)
        }
        val meanLog = weightedMean(logValues)
        val baseLogVariance = weightedVariance(logValues, meanLog)
        val totalLogVariance = baseLogVariance + extraVariance
        val totalLogSd = sqrt(totalLogVariance)
        val z90 = 1.6448536269514722
        val approximateLower = exp(meanLog - z90 * totalLogSd)
        val approximateUpper = exp(meanLog + z90 * totalLogSd)
        val approximateVariance = (exp(totalLogVariance) - 1.0) * exp(2.0 * meanLog + totalLogVariance)
        if (!approximateLower.isFinite() || !approximateUpper.isFinite() || !approximateVariance.isFinite()) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.NON_FINITE_POSTERIOR,
                "7C ${fit.family.storageValue} extrapolation uncertainty became non-finite; prediction is unavailable.",
            )
        }
        val widened = PosteriorSummary(
            credibleLower05 = min(direct.p05, approximateLower),
            estimateMedian = direct.p50,
            credibleUpper95 = max(direct.p95, approximateUpper),
            posteriorVariance = max(direct.posteriorVariance, approximateVariance),
        )
        return PosteriorEstimate(widened, fit.support, fit.frontierAtReference.provenance)
    }

    /** Capability-only held-out diagnostic: posterior probability that frontier >= demonstrated output. */
    fun frontierExceedanceProbability(
        fit: NonDynamicCapabilityFit,
        query: NonDynamicCapabilityQuery,
        demonstratedOutput: Double,
    ): Double {
        require(demonstratedOutput.isFinite() && demonstratedOutput > 0.0)
        val (input, offset) = when (query) {
            is LoadedHoldCapabilityQuery -> query.durationSeconds to query.independentSessionOffset
            is DurationOnlyCapabilityQuery -> null to query.independentSessionOffset
            is RepeatedContractionCapabilityQuery -> query.cycles.toDouble() to query.independentSessionOffset
        }
        val x = if (input == null) 0.0 else ln(input / requireNotNull(fit.referenceCoordinate))
        val logObserved = ln(demonstratedOutput)
        return fit.posteriorNodes.filter { node ->
            node.logFrontierAtReference + node.trajectory * offset - (node.slope ?: 0.0) * x >= logObserved
        }.sumOf { it.posteriorWeight }.coerceIn(0.0, 1.0)
    }

    private fun validateProjection(projection: NonDynamicEvidenceProjection) {
        if (projection.profile.metricFamily != config.family) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.MODEL_CONFIG_MISMATCH,
                "${config.family.storageValue} model cannot fit ${projection.profile.metricFamily.storageValue} evidence.",
            )
        }
        if (projection.policy.identity != config.evidencePolicyIdentity) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.EVIDENCE_POLICY_MISMATCH,
                "7C model expects a different evidence-policy identity.",
            )
        }
    }

    private fun validateContext(evidence: List<NonDynamicCapabilityEvidence>) {
        if (config.family != MetricFamily.REPEATED_CONTRACTION) return
        val cadences = evidence.filterIsInstance<RepeatedContractionEvidence>().mapNotNull { it.cadencePerMinute }
        if (cadences.isNotEmpty()) {
            val minimum = cadences.min()
            val maximum = cadences.max()
            if (maximum - minimum > 1e-9 * max(1.0, maximum)) {
                throw NonDynamicCapabilityFitException(
                    NonDynamicFitFailureReason.UNSUPPORTED_CONTEXT,
                    "Repeated-contraction v1 preserves cadence but cannot pool varying cadence within one capability stream.",
                )
            }
        }
    }

    private data class TemporalSelection(val evidence: List<NonDynamicCapabilityEvidence>, val truncated: Boolean)

    private fun selectTemporalEvidence(projection: NonDynamicEvidenceProjection, horizon: Instant): TemporalSelection {
        val atHorizon = projection.evidence.filter { !it.completedAt.isAfter(horizon) }
        val sessions = atHorizon.groupBy { it.sessionId }.entries
            .sortedWith(compareBy<Map.Entry<String, List<NonDynamicCapabilityEvidence>>> { it.value.maxOf { e -> e.completedAt } }.thenBy { it.key })
            .map { it.key }
        val selectedIds = sessions.takeLast(config.recentIndependentSessionWindow).toSet()
        return TemporalSelection(
            atHorizon.filter { it.sessionId in selectedIds }
                .sortedWith(compareBy<NonDynamicCapabilityEvidence> { it.completedAt }.thenBy { it.observationId }),
            sessions.size > config.recentIndependentSessionWindow,
        )
    }

    private fun referenceCoordinate(evidence: List<NonDynamicCapabilityEvidence>): Double? = when (config.family) {
        MetricFamily.LOADED_HOLD -> 30.0
        MetricFamily.DURATION_ONLY -> null
        MetricFamily.REPEATED_CONTRACTION -> evidence.filterIsInstance<RepeatedContractionEvidence>()
            .map { it.cycles }.sorted().let { it[(it.size - 1) / 2].toDouble() }
        else -> error("Unsupported 7C family")
    }

    private fun modelObservation(evidence: NonDynamicCapabilityEvidence, reference: Double?, z: Double): ModelObservation = when (evidence) {
        is LoadedHoldEvidence -> ModelObservation(evidence, ln(evidence.resistance.valueKg), evidence.durationSeconds, ln(evidence.durationSeconds / requireNotNull(reference)), z)
        is DurationOnlyEvidence -> ModelObservation(evidence, ln(evidence.durationSeconds), null, 0.0, z)
        is RepeatedContractionEvidence -> ModelObservation(evidence, ln(evidence.resistance.valueKg), evidence.cycles.toDouble(), ln(evidence.cycles / requireNotNull(reference)), z)
    }

    private fun frontierGrid(observations: List<ModelObservation>): List<Double> {
        val lower = max(ln(config.outputPriorMinimum), observations.minOf { it.y } - 0.25)
        val upper = min(ln(config.outputPriorMaximum), observations.maxOf { it.y } + 1.0)
        if (!lower.isFinite() || !upper.isFinite() || lower >= upper) {
            throw NonDynamicCapabilityFitException(
                NonDynamicFitFailureReason.DEGENERATE_POSTERIOR,
                "Observed ${config.family.storageValue} output lies outside the configured numerical/prior domain.",
            )
        }
        return linearGrid(lower, upper, solverConfig.frontierGridPoints)
    }

    private fun marginalLogLikelihood(
        observation: ModelObservation,
        c: Double,
        slope: Double?,
        trajectory: Double,
        slackScale: Double,
        noiseScale: Double,
    ): Double {
        val frontier = c + trajectory * observation.z - (slope ?: 0.0) * observation.x
        val residual = observation.y - frontier
        val noiseNorm = studentTLogNormalisation(config.studentTDegreesOfFreedom, noiseScale)
        val terms = quadrature.map { point ->
            point.logPriorMass + studentTLogDensity(
                residual + slackScale * point.standardisedSlack,
                config.studentTDegreesOfFreedom,
                noiseScale,
                noiseNorm,
            )
        }
        return logSumExp(terms)
    }

    private fun normaliseBase(raw: List<BaseRawNode>): List<BaseNode> {
        val weights = normalisedWeights(raw.map { it.logPosterior })
        return raw.indices.map { i -> raw[i].let { BaseNode(it.c, it.slope, it.slackScale, it.noiseScale, weights[i]) } }
    }

    private fun normaliseExpanded(raw: List<ExpandedRawNode>): List<NonDynamicPosteriorNode> {
        val weights = normalisedWeights(raw.map { it.logPosterior })
        return raw.indices.map { i -> raw[i].let {
            NonDynamicPosteriorNode(it.c, it.slope, it.trajectory, it.slackScale, it.noiseScale, weights[i])
        } }
    }

    private fun normalisedWeights(logWeights: List<Double>): List<Double> {
        val finite = logWeights.filter { it.isFinite() }
        if (finite.isEmpty()) throw NonDynamicCapabilityFitException(NonDynamicFitFailureReason.NON_FINITE_POSTERIOR, "No finite 7C posterior nodes were produced.")
        val maximum = finite.max()
        val unnormalised = logWeights.map { if (it.isFinite()) exp(it - maximum) else 0.0 }
        val total = unnormalised.sum()
        if (!total.isFinite() || total <= 0.0) throw NonDynamicCapabilityFitException(NonDynamicFitFailureReason.NON_FINITE_POSTERIOR, "7C posterior normalisation failed.")
        return unnormalised.map { it / total }
    }

    private fun pruneBase(nodes: List<BaseNode>): BaseSelection {
        val ordered = nodes.sortedWith(
            compareByDescending<BaseNode> { it.weight }
                .thenBy { it.c }
                .thenBy { it.slope ?: 0.0 }
                .thenBy { it.slackScale }
                .thenBy { it.noiseScale },
        )
        val selected = mutableListOf<BaseNode>()
        var mass = 0.0
        val minimum = min(solverConfig.minimumRetainedBaseNodes, ordered.size)
        for (node in ordered) {
            if (selected.size >= solverConfig.maximumRetainedBaseNodes) break
            selected += node
            mass += node.weight
            if (selected.size >= minimum && mass >= solverConfig.retainedBasePosteriorMass) break
        }
        if (selected.isEmpty() || mass <= 0.0 || !mass.isFinite()) {
            throw NonDynamicCapabilityFitException(NonDynamicFitFailureReason.DEGENERATE_POSTERIOR, "Adaptive Sparse retained no usable 7C base posterior mass.")
        }
        return BaseSelection(selected.map { it.copy(weight = it.weight / mass) }, nodes.size, mass.coerceIn(0.0, 1.0))
    }

    private fun slopeIdentification(support: EvidenceSupport, logSpan: Double): DynamicParameterIdentification = when {
        support.effectiveIndependentSessionCount >= config.slopeDataInformedMinimumSessions && logSpan >= config.slopeDataInformedMinimumLogSpan -> DynamicParameterIdentification.DATA_INFORMED
        support.effectiveIndependentSessionCount >= config.slopePartialMinimumSessions && logSpan >= config.slopePartialMinimumLogSpan -> DynamicParameterIdentification.PARTIALLY_LEARNED
        else -> DynamicParameterIdentification.PRIOR_DOMINATED
    }

    private fun nuisanceIdentification(support: EvidenceSupport, learning: Boolean): DynamicParameterIdentification = when {
        !learning -> DynamicParameterIdentification.FIXED_BY_CONFIG
        support.effectiveIndependentSessionCount >= config.nuisanceDataInformedMinimumSessions && support.observationCount >= config.nuisanceDataInformedMinimumObservations -> DynamicParameterIdentification.DATA_INFORMED
        else -> DynamicParameterIdentification.PARTIALLY_LEARNED
    }

    private fun summary(values: List<WeightedValue>): PosteriorSummary {
        require(values.isNotEmpty() && values.all { it.value.isFinite() && it.weight.isFinite() && it.weight >= 0.0 })
        val total = values.sumOf { it.weight }
        if (!total.isFinite() || total <= 0.0) throw NonDynamicCapabilityFitException(NonDynamicFitFailureReason.NON_FINITE_POSTERIOR, "7C weighted summary has invalid probability mass.")
        val normalised = values.map { WeightedValue(it.value, it.weight / total) }
        val mean = weightedMean(normalised)
        val variance = weightedVariance(normalised, mean)
        if (!mean.isFinite() || !variance.isFinite()) throw NonDynamicCapabilityFitException(NonDynamicFitFailureReason.NON_FINITE_POSTERIOR, "7C posterior summary is non-finite.")
        return PosteriorSummary(weightedQuantile(normalised, 0.05), weightedQuantile(normalised, 0.5), weightedQuantile(normalised, 0.95), variance)
    }

    private fun weightedMean(values: List<WeightedValue>): Double = values.sumOf { it.value * it.weight } / values.sumOf { it.weight }
    private fun weightedVariance(values: List<WeightedValue>, mean: Double): Double = values.sumOf { it.weight * (it.value - mean).pow(2) } / values.sumOf { it.weight }
    private fun weightedQuantile(values: List<WeightedValue>, p: Double): Double {
        val ordered = values.sortedBy { it.value }
        val target = p * ordered.sumOf { it.weight }
        var cumulative = 0.0
        for (value in ordered) {
            cumulative += value.weight
            if (cumulative >= target) return value.value
        }
        return ordered.last().value
    }

    private fun studentTLogDensity(residual: Double, df: Double, scale: Double, norm: Double): Double =
        norm - ((df + 1.0) / 2.0) * ln1p((residual / scale).pow(2) / df)

    private fun studentTLogNormalisation(df: Double, scale: Double): Double =
        logGamma((df + 1.0) / 2.0) - logGamma(df / 2.0) - 0.5 * ln(df * PI) - ln(scale)

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

    private fun centredGrid(centre: Double, sd: Double, radius: Double, points: Int) = linearGrid(centre - radius * sd, centre + radius * sd, points)
    private fun linearGrid(minimum: Double, maximum: Double, points: Int): List<Double> {
        if (points == 1) return listOf((minimum + maximum) / 2.0)
        val step = (maximum - minimum) / (points - 1).toDouble()
        return List(points) { minimum + step * it }
    }

    private data class ModelObservation(
        val evidence: NonDynamicCapabilityEvidence,
        val y: Double,
        val inputCoordinate: Double?,
        val x: Double,
        val z: Double,
    ) {
        val output: Double get() = exp(y)
    }

    private data class BaseRawNode(val c: Double, val slope: Double?, val slackScale: Double, val noiseScale: Double, val logPosterior: Double)
    private data class BaseNode(val c: Double, val slope: Double?, val slackScale: Double, val noiseScale: Double, val weight: Double)
    private data class BaseSelection(val nodes: List<BaseNode>, val originalCount: Int, val massBeforeRenormalisation: Double)
    private data class ExpandedRawNode(val c: Double, val slope: Double?, val trajectory: Double, val slackScale: Double, val noiseScale: Double, val logPosterior: Double)
    private data class WeightedValue(val value: Double, val weight: Double)
    private data class SlackQuadraturePoint(val standardisedSlack: Double, val logPriorMass: Double)

    companion object {
        private const val MAX_BASE_GRID_NODES = 50_000L
        private const val MAX_EXPANDED_GRID_NODES = 250_000L

        private fun buildSlackQuadrature(config: NonDynamicSolverConfig): List<SlackQuadraturePoint> {
            val width = config.slackQuadratureMaximumSd / config.slackQuadraturePoints.toDouble()
            return List(config.slackQuadraturePoints) { index ->
                val z = (index + 0.5) * width
                SlackQuadraturePoint(z, 0.5 * ln(2.0 / PI) - 0.5 * z * z + ln(width))
            }
        }

        private fun logGamma(value: Double): Double {
            require(value > 0.0 && value.isFinite())
            val coefficients = doubleArrayOf(
                676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7,
            )
            if (value < 0.5) return ln(PI) - ln(kotlin.math.sin(PI * value)) - logGamma(1.0 - value)
            val z = value - 1.0
            var x = 0.99999999999980993
            coefficients.forEachIndexed { index, coefficient -> x += coefficient / (z + index + 1.0) }
            val t = z + coefficients.size - 0.5
            return 0.5 * ln(2.0 * PI) + (z + 0.5) * ln(t) - t + ln(x)
        }
    }
}
