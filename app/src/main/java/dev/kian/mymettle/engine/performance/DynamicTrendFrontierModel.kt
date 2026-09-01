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
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierConfig
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierPosteriorNode
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
import dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation
import dev.kian.mymettle.domain.inference.InferenceSolverDiagnostics
import dev.kian.mymettle.domain.inference.ModelOutputProvenance
import dev.kian.mymettle.domain.inference.PosteriorEstimate
import dev.kian.mymettle.domain.inference.PosteriorSummary
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic Candidate-v2 conditional-Laplace extension of the frozen Candidate-v1 posterior.
 *
 * Every frozen-v1 joint posterior node is retained as proposal support. For each node, Candidate v2
 * evaluates the unchanged observation likelihood at g=0 and at one symmetric finite-difference pair
 * around zero. A second-order likelihood expansion combines analytically with the proper Normal(0,
 * sigma_g) prior, yielding a conditional Gaussian posterior for g and a deterministic marginal
 * evidence correction for that v1 node. Five-point normal quadrature then propagates that conditional
 * g distribution into the full c/b/sigma/g posterior without further likelihood evaluations.
 *
 * This is an explicit deterministic Laplace approximation, not MCMC and not a physiological law.
 * It avoids both a new full tensor dimension and the support bias that would come from retaining only
 * the highest-probability v1 nodes. If local curvature is numerically invalid for more than the
 * versioned posterior-mass tolerance, fitting fails rather than silently changing the proposal.
 *
 * With fewer than three independent sessions, g is fixed exactly to zero and every v1 posterior node
 * is retained, making sparse-history Candidate v2 exactly collapse to Candidate-v1 behaviour.
 */
class DynamicTrendFrontierModel(
    val config: DynamicTrendFrontierConfig = DynamicTrendFrontierV2.config,
    private val baseModel: DynamicStochasticFrontierModel = DynamicStochasticFrontierModel(config.baseConfig),
) : DynamicCapabilityModel<DynamicTrendFrontierFit> {
    override val modelVersion: String get() = config.semanticVersion
    val mathematicalModelIdentity get() = DynamicTrendFrontierV2.mathematicalIdentity(config)
    val solverIdentity get() = DynamicTrendFrontierV2.conditionalLaplaceSolverIdentity(config)

    private val slackQuadrature = buildSlackQuadrature(config.baseConfig)
    private val slackScratch = object : ThreadLocal<DoubleArray>() {
        override fun initialValue(): DoubleArray = DoubleArray(config.baseConfig.slackQuadraturePoints)
    }

    override fun fit(request: DynamicCapabilityFitRequest): DynamicTrendFrontierFit {
        validateRequest(request)
        val baseConfigDefinition = config.baseConfig.toModelConfig(request.modelConfig.createdAt)
        val baseFit = baseModel.fit(
            DynamicCapabilityFitRequest(
                projection = request.projection,
                inferenceHorizon = request.inferenceHorizon,
                modelConfig = baseConfigDefinition,
            ),
        )
        return fitFromFrozenV1(request, baseFit)
    }

    /** Reuses an already-computed frozen-v1 fit so comparative retrospective evaluation does not refit v1 twice. */
    fun fitFromFrozenV1(
        request: DynamicCapabilityFitRequest,
        baseFit: DynamicStochasticFrontierFit,
    ): DynamicTrendFrontierFit {
        val solverStart = System.nanoTime()
        validateRequest(request)
        require(baseFit.executionProfileVersionId == request.projection.profile.executionProfileVersionId)
        require(baseFit.side == request.projection.side)
        require(baseFit.evidencePolicyIdentity == config.evidencePolicyIdentity)
        require(baseFit.referenceRepetitions > 0.0)

        val selectedById = request.projection.evidence.associateBy { it.observationId }
        val selected = baseFit.selectedObservationIds.map { observationId ->
            requireNotNull(selectedById[observationId]) { "Frozen-v1 proposal selected missing observation $observationId." }
        }
        val sessionIds = baseFit.selectedSessionIds
        val sessionZ = sessionIds.mapIndexed { index, sessionId ->
            sessionId to (index - (sessionIds.size - 1)).toDouble()
        }.toMap()
        val sessionCounts = selected.groupingBy { it.sessionId }.eachCount()
        val observations = selected.map { evidence ->
            TrendObservation(
                evidence = evidence,
                y = ln(evidence.resistance.value),
                x = ln(evidence.repetitions / baseFit.referenceRepetitions),
                z = requireNotNull(sessionZ[evidence.sessionId]),
                sessionWeight = 1.0 / requireNotNull(sessionCounts[evidence.sessionId]).toDouble(),
            )
        }

        val learnTrend = baseFit.support.effectiveIndependentSessionCount >= config.trendMinimumIndependentSessionsToLearn
        val rawNodes: List<RawTrendNode>
        val validBaseMass: Double
        if (!learnTrend) {
            rawNodes = baseFit.posteriorNodes.map { node ->
                RawTrendNode(
                    logFrontierAtLatestSession = node.logFrontierAtReference,
                    slope = node.slope,
                    frontierTrend = 0.0,
                    slackScale = node.slackScale,
                    noiseScale = node.noiseScale,
                    logWeight = if (node.posteriorWeight > 0.0) ln(node.posteriorWeight) else Double.NEGATIVE_INFINITY,
                )
            }
            validBaseMass = 1.0
        } else {
            val delta = config.trendLaplaceFiniteDifferenceStep
            val chunks = baseFit.posteriorNodes.chunked(max(1, (baseFit.posteriorNodes.size + FIT_PARALLELISM - 1) / FIT_PARALLELISM))
            val tasks = chunks.map { chunk ->
                Callable {
                    val localNodes = ArrayList<RawTrendNode>(chunk.size * config.trendPosteriorQuadraturePoints)
                    var localValidMass = 0.0
                    chunk.forEach { baseNode ->
                        if (baseNode.posteriorWeight <= 0.0) return@forEach
                        val zero = logLikelihood(baseNode, 0.0, observations)
                        val plus = logLikelihood(baseNode, delta, observations)
                        val minus = logLikelihood(baseNode, -delta, observations)
                        if (!zero.isFinite() || !plus.isFinite() || !minus.isFinite()) return@forEach
                        val score = (plus - minus) / (2.0 * delta)
                        val curvature = (plus - 2.0 * zero + minus) / (delta * delta)
                        val priorVariance = config.trendPriorSdLogResistancePerSession.pow(2)
                        val precision = 1.0 / priorVariance - curvature
                        if (!score.isFinite() || !curvature.isFinite() || !precision.isFinite() || precision <= 0.0) return@forEach
                        val variance = 1.0 / precision
                        val mean = score / precision
                        val sd = sqrt(variance)
                        if (!mean.isFinite() || !sd.isFinite() || sd <= 0.0) return@forEach
                        val logMarginalRatio = -ln(config.trendPriorSdLogResistancePerSession) -
                            0.5 * ln(precision) + 0.5 * score * score / precision
                        if (!logMarginalRatio.isFinite()) return@forEach
                        localValidMass += baseNode.posteriorWeight
                        normalQuadrature(mean, sd).forEach { point ->
                            localNodes += RawTrendNode(
                                logFrontierAtLatestSession = baseNode.logFrontierAtReference,
                                slope = baseNode.slope,
                                frontierTrend = point.value,
                                slackScale = baseNode.slackScale,
                                noiseScale = baseNode.noiseScale,
                                logWeight = ln(baseNode.posteriorWeight) + logMarginalRatio + ln(point.mass),
                            )
                        }
                    }
                    LaplaceChunk(localNodes, localValidMass)
                }
            }
            val chunksOut = FIT_EXECUTOR.invokeAll(tasks).map { it.get() }
            rawNodes = chunksOut.flatMap { it.nodes }
            validBaseMass = chunksOut.sumOf { it.validBaseMass }.coerceIn(0.0, 1.0)
            if (validBaseMass < config.laplaceMinimumValidBasePosteriorMass) {
                throw DynamicCapabilityFitException(
                    DynamicCapabilityFitFailureReason.DEGENERATE_POSTERIOR,
                    "Candidate v2 conditional Laplace update retained only $validBaseMass of frozen-v1 posterior mass; minimum is ${config.laplaceMinimumValidBasePosteriorMass}.",
                )
            }
        }

        val posteriorNodes = normalise(rawNodes)
        val effectiveNodeCount = 1.0 / posteriorNodes.sumOf { it.posteriorWeight * it.posteriorWeight }
        val provenance = ModelOutputProvenance(
            modelConfigId = request.modelConfig.id,
            modelManifestId = null,
            inferenceRunId = null,
            evidenceThrough = baseFit.support.lastEvidenceAt,
        )
        val frontierSummary = weightedSummary(
            posteriorNodes.map { WeightedValue(exp(it.logFrontierAtLatestSession), it.posteriorWeight) },
        )
        val trendSummary = weightedSummary(posteriorNodes.map { WeightedValue(it.frontierTrend, it.posteriorWeight) })
        val trendIdentification = trendIdentification(baseFit.support.effectiveIndependentSessionCount, trendSummary)
        val topSlackNodes = posteriorNodes.sortedByDescending { it.posteriorWeight }
            .take(config.baseConfig.slackPosteriorTopNodeCount)
        val topSlackWeight = topSlackNodes.sumOf { it.posteriorWeight }
        require(topSlackWeight > 0.0 && topSlackWeight.isFinite())
        val slackIdentification = if (baseFit.slackScale.identification == DynamicParameterIdentification.FIXED_BY_CONFIG) {
            DynamicParameterIdentification.PRIOR_DOMINATED
        } else {
            DynamicParameterIdentification.PARTIALLY_LEARNED
        }
        val observationSlack = observations.map { observation ->
            inferObservationSlack(observation, topSlackNodes, topSlackWeight, slackIdentification)
        }

        val warnings = buildSet {
            add("approximate_posterior")
            add("conditional_laplace_frontier_trend")
            add("model_development_retrospective_candidate")
            if (!learnTrend) add("frontier_trend_fixed_zero_sparse_history")
            if (learnTrend && validBaseMass < 1.0 - 1e-12) add("conditional_laplace_discarded_invalid_base_mass")
            if (trendIdentification == DynamicParameterIdentification.PRIOR_DOMINATED) add("frontier_trend_prior_dominated")
            baseFit.warnings.forEach { add("inherited_v1:${it.storageValue}") }
        }
        return DynamicTrendFrontierFit(
            executionProfileVersionId = baseFit.executionProfileVersionId,
            side = baseFit.side,
            inferenceHorizon = request.inferenceHorizon,
            referenceRepetitions = baseFit.referenceRepetitions,
            modelConfigId = request.modelConfig.id,
            modelVersion = modelVersion,
            evidencePolicyIdentity = config.evidencePolicyIdentity,
            support = baseFit.support,
            observedRepMin = baseFit.observedRepMin,
            observedRepMax = baseFit.observedRepMax,
            observedResistanceMinKg = baseFit.observedResistanceMinKg,
            observedResistanceMaxKg = baseFit.observedResistanceMaxKg,
            frontierAtLatestSession = PosteriorEstimate(frontierSummary, baseFit.support, provenance),
            slope = DynamicFrontierParameterPosterior(
                weightedSummary(posteriorNodes.map { WeightedValue(it.slope, it.posteriorWeight) }),
                baseFit.slope.identification,
                baseFit.slope.semanticUnit,
            ),
            frontierTrend = DynamicFrontierParameterPosterior(
                trendSummary,
                trendIdentification,
                "statistical log-frontier trajectory per independent-session ordinal",
            ),
            slackScale = DynamicFrontierParameterPosterior(
                weightedSummary(posteriorNodes.map { WeightedValue(it.slackScale, it.posteriorWeight) }),
                baseFit.slackScale.identification,
                baseFit.slackScale.semanticUnit,
            ),
            noiseScale = DynamicFrontierParameterPosterior(
                weightedSummary(posteriorNodes.map { WeightedValue(it.noiseScale, it.posteriorWeight) }),
                baseFit.noiseScale.identification,
                baseFit.noiseScale.semanticUnit,
            ),
            observationSlack = observationSlack,
            selectedObservationIds = baseFit.selectedObservationIds,
            selectedSessionIds = baseFit.selectedSessionIds,
            approximationVersion = config.approximationVersion,
            laplaceValidBasePosteriorMass = validBaseMass,
            laplaceFiniteDifferenceStep = config.trendLaplaceFiniteDifferenceStep,
            posteriorEffectiveNodeCount = effectiveNodeCount,
            warnings = warnings,
            posteriorNodes = posteriorNodes,
            mathematicalModelIdentity = mathematicalModelIdentity,
            solverDiagnostics = InferenceSolverDiagnostics(
                solverIdentity = solverIdentity,
                posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES,
                evaluatedNodeCount = rawNodes.size.toLong(),
                effectiveNodeCount = effectiveNodeCount,
                updateRuntimeNanos = System.nanoTime() - solverStart,
                notes = setOf(
                    "same_candidate_v2_mathematics_as_dense_reference",
                    "conditional_laplace_solver",
                ),
            ),
        )
    }

    fun projectToSessionOffset(fit: DynamicTrendFrontierFit, sessionOffset: Double): DynamicStochasticFrontierFit {
        require(sessionOffset.isFinite())
        val minimumLogResistance = ln(config.baseConfig.numericalMinimumResistanceKg)
        val maximumLogResistance = ln(config.baseConfig.numericalMaximumResistanceKg)
        val projectedNodes = fit.posteriorNodes.map { node ->
            val projectedLogFrontier = node.logFrontierAtLatestSession + node.frontierTrend * sessionOffset
            if (!projectedLogFrontier.isFinite() || projectedLogFrontier !in minimumLogResistance..maximumLogResistance) {
                throw DynamicCapabilityFitException(
                    DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR,
                    "Conditional-Laplace Candidate-v2 projection left the configured numerical resistance domain; approximation is unavailable for this horizon.",
                )
            }
            DynamicFrontierPosteriorNode(
                logFrontierAtReference = projectedLogFrontier,
                slope = node.slope,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                posteriorWeight = node.posteriorWeight,
            )
        }
        val frontierSummary = weightedSummary(projectedNodes.map { WeightedValue(exp(it.logFrontierAtReference), it.posteriorWeight) })
        return DynamicStochasticFrontierFit(
            executionProfileVersionId = fit.executionProfileVersionId,
            side = fit.side,
            inferenceHorizon = fit.inferenceHorizon,
            referenceRepetitions = fit.referenceRepetitions,
            modelConfigId = fit.modelConfigId,
            modelVersion = fit.modelVersion,
            evidencePolicyIdentity = fit.evidencePolicyIdentity,
            support = fit.support,
            observedRepMin = fit.observedRepMin,
            observedRepMax = fit.observedRepMax,
            observedResistanceMinKg = fit.observedResistanceMinKg,
            observedResistanceMaxKg = fit.observedResistanceMaxKg,
            frontierAtReference = PosteriorEstimate(frontierSummary, fit.support, fit.frontierAtLatestSession.provenance),
            slope = fit.slope,
            slackScale = fit.slackScale,
            noiseScale = fit.noiseScale,
            observationSlack = fit.observationSlack,
            selectedObservationIds = fit.selectedObservationIds,
            selectedSessionIds = fit.selectedSessionIds,
            approximationVersion = fit.approximationVersion,
            warnings = setOf(DynamicCapabilityFitWarning.APPROXIMATE_POSTERIOR),
            posteriorNodes = projectedNodes,
        )
    }

    override fun predictFrontier(fit: DynamicTrendFrontierFit, repetitions: Double): PosteriorEstimate =
        predictFrontier(fit, repetitions, 0.0)

    fun predictFrontier(fit: DynamicTrendFrontierFit, repetitions: Double, sessionOffset: Double): PosteriorEstimate =
        baseModel.predictFrontier(projectToSessionOffset(fit, sessionOffset), repetitions)

    fun basePredictiveModel(): DynamicStochasticFrontierModel = baseModel

    private fun validateRequest(request: DynamicCapabilityFitRequest) {
        val expected = config.toModelConfig(request.modelConfig.createdAt)
        if (request.modelConfig.id != expected.id || request.modelConfig.canonicalConfigPayload != expected.canonicalConfigPayload) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.MODEL_CONFIG_MISMATCH,
                "Fit request does not use immutable Candidate-v2 config expected by $modelVersion.",
            )
        }
        if (request.projection.policy.identity != config.evidencePolicyIdentity) {
            throw DynamicCapabilityFitException(
                DynamicCapabilityFitFailureReason.EVIDENCE_POLICY_MISMATCH,
                "Candidate v2 expects the same corrected 7B.1 evidence-policy identity as frozen v1 evaluation.",
            )
        }
    }

    private fun logLikelihood(node: DynamicFrontierPosteriorNode, trend: Double, observations: List<TrendObservation>): Double {
        val noiseNormalisation = studentTLogNormalisation(config.baseConfig.studentTDegreesOfFreedom, node.noiseScale)
        var total = 0.0
        observations.forEach { observation ->
            val frontier = node.logFrontierAtReference + trend * observation.z - node.slope * observation.x
            val residual = observation.y - frontier
            total += observation.sessionWeight * marginalObservationLogDensity(
                residual = residual,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                noiseLogNormalisation = noiseNormalisation,
            )
        }
        return total
    }

    private fun inferObservationSlack(
        observation: TrendObservation,
        topNodes: List<DynamicTrendFrontierPosteriorNode>,
        topWeightTotal: Double,
        identification: DynamicParameterIdentification,
    ): DynamicObservationSlackPosterior {
        val mass = ArrayList<DynamicSlackPosteriorMass>(topNodes.size * slackQuadrature.size)
        topNodes.forEach { node ->
            val frontier = node.logFrontierAtLatestSession + node.frontierTrend * observation.z - node.slope * observation.x
            val residual = observation.y - frontier
            val noiseNorm = studentTLogNormalisation(config.baseConfig.studentTDegreesOfFreedom, node.noiseScale)
            val localLogWeights = slackQuadrature.map { point ->
                point.logPriorMass + studentTLogDensity(
                    residual + node.slackScale * point.standardisedSlack,
                    config.baseConfig.studentTDegreesOfFreedom,
                    node.noiseScale,
                    noiseNorm,
                )
            }
            val localNormaliser = logSumExp(localLogWeights)
            val globalWeight = node.posteriorWeight / topWeightTotal
            slackQuadrature.forEachIndexed { index, point ->
                mass += DynamicSlackPosteriorMass(
                    slack = node.slackScale * point.standardisedSlack,
                    probability = globalWeight * exp(localLogWeights[index] - localNormaliser),
                )
            }
        }
        val total = mass.sumOf { it.probability }
        val normalised = mass.map { it.copy(probability = it.probability / total) }
        return DynamicObservationSlackPosterior(
            observationId = observation.evidence.observationId,
            summary = weightedSummary(normalised.map { WeightedValue(it.slack, it.probability) }),
            identification = identification,
            massPoints = normalised,
        )
    }

    private fun trendIdentification(sessionCount: Int, summary: PosteriorSummary): DynamicParameterIdentification {
        if (sessionCount < config.trendMinimumIndependentSessionsToLearn) return DynamicParameterIdentification.FIXED_BY_CONFIG
        val posteriorSd = sqrt(max(0.0, summary.posteriorVariance))
        val fraction = posteriorSd / config.trendPriorSdLogResistancePerSession
        return when {
            sessionCount >= config.trendDataInformedMinimumIndependentSessions &&
                fraction <= config.trendDataInformedPosteriorSdFraction -> DynamicParameterIdentification.DATA_INFORMED
            fraction >= config.trendPriorDominatedPosteriorSdFraction -> DynamicParameterIdentification.PRIOR_DOMINATED
            else -> DynamicParameterIdentification.PARTIALLY_LEARNED
        }
    }

    private fun normalise(rawNodes: List<RawTrendNode>): List<DynamicTrendFrontierPosteriorNode> {
        val finite = rawNodes.filter { it.logWeight.isFinite() }
        if (finite.isEmpty()) {
            throw DynamicCapabilityFitException(DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR, "Candidate v2 produced no finite posterior node.")
        }
        val maxLog = finite.maxOf { it.logWeight }
        val weights = rawNodes.map { if (it.logWeight.isFinite()) exp(it.logWeight - maxLog) else 0.0 }
        val total = weights.sum()
        if (!total.isFinite() || total <= 0.0) {
            throw DynamicCapabilityFitException(DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR, "Candidate v2 posterior normalisation failed.")
        }
        return rawNodes.mapIndexed { index, node ->
            DynamicTrendFrontierPosteriorNode(
                logFrontierAtLatestSession = node.logFrontierAtLatestSession,
                slope = node.slope,
                frontierTrend = node.frontierTrend,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                posteriorWeight = weights[index] / total,
            )
        }
    }

    private fun marginalObservationLogDensity(
        residual: Double,
        slackScale: Double,
        noiseScale: Double,
        noiseLogNormalisation: Double,
    ): Double {
        val scratch = requireNotNull(slackScratch.get())
        var maximum = Double.NEGATIVE_INFINITY
        slackQuadrature.forEachIndexed { index, point ->
            val term = point.logPriorMass + studentTLogDensity(
                residual + slackScale * point.standardisedSlack,
                config.baseConfig.studentTDegreesOfFreedom,
                noiseScale,
                noiseLogNormalisation,
            )
            scratch[index] = term
            if (term.isFinite() && term > maximum) maximum = term
        }
        if (!maximum.isFinite()) return Double.NEGATIVE_INFINITY
        var scaledTotal = 0.0
        slackQuadrature.indices.forEach { index ->
            val term = scratch[index]
            if (term.isFinite()) scaledTotal += exp(term - maximum)
        }
        return maximum + ln(scaledTotal)
    }

    private fun weightedSummary(values: List<WeightedValue>): PosteriorSummary {
        require(values.isNotEmpty())
        val totalWeight = values.sumOf { it.weight }
        require(totalWeight.isFinite() && totalWeight > 0.0)
        val normalised = values.map { WeightedValue(it.value, it.weight / totalWeight) }
        val mean = normalised.sumOf { it.value * it.weight }
        val variance = normalised.sumOf { it.weight * (it.value - mean).pow(2) }
        return PosteriorSummary(
            credibleLower05 = weightedQuantile(normalised, 0.05),
            estimateMedian = weightedQuantile(normalised, 0.50),
            credibleUpper95 = weightedQuantile(normalised, 0.95),
            posteriorVariance = variance,
        )
    }

    private fun weightedQuantile(values: List<WeightedValue>, probability: Double): Double {
        val ordered = values.sortedBy { it.value }
        var cumulative = 0.0
        for (value in ordered) {
            cumulative += value.weight
            if (cumulative >= probability) return value.value
        }
        return ordered.last().value
    }

    private fun studentTLogDensity(residual: Double, df: Double, scale: Double, norm: Double): Double =
        norm - ((df + 1.0) / 2.0) * ln1p((residual / scale).pow(2) / df)

    private fun studentTLogNormalisation(df: Double, scale: Double): Double =
        logGamma((df + 1.0) / 2.0) - logGamma(df / 2.0) - 0.5 * ln(df * PI) - ln(scale)

    private fun logSumExp(values: List<Double>): Double {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return Double.NEGATIVE_INFINITY
        val maximum = finite.max()
        return maximum + ln(finite.sumOf { exp(it - maximum) })
    }

    private data class TrendObservation(
        val evidence: DynamicResistanceEvidence,
        val y: Double,
        val x: Double,
        val z: Double,
        val sessionWeight: Double,
    )

    private data class NormalQuadraturePoint(val value: Double, val mass: Double)
    private data class SlackQuadraturePoint(val standardisedSlack: Double, val logPriorMass: Double)
    private data class WeightedValue(val value: Double, val weight: Double)
    private data class LaplaceChunk(val nodes: List<RawTrendNode>, val validBaseMass: Double)
    private data class RawTrendNode(
        val logFrontierAtLatestSession: Double,
        val slope: Double,
        val frontierTrend: Double,
        val slackScale: Double,
        val noiseScale: Double,
        val logWeight: Double,
    )

    companion object {
        const val FIT_PARALLELISM = 3
        private val FIT_EXECUTOR = Executors.newFixedThreadPool(FIT_PARALLELISM) { runnable ->
            Thread(runnable, "n-bio-7b-v2-trend").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
        private val STANDARD_NORMAL_GH5_Z = doubleArrayOf(
            -2.8569700138728056,
            -1.355626179974266,
            0.0,
            1.355626179974266,
            2.8569700138728056,
        )
        private val STANDARD_NORMAL_GH5_WEIGHT = doubleArrayOf(
            0.0112574113277207,
            0.2220759220056126,
            0.5333333333333333,
            0.2220759220056126,
            0.0112574113277207,
        )

        private fun normalQuadrature(mean: Double, sd: Double): List<NormalQuadraturePoint> =
            STANDARD_NORMAL_GH5_Z.indices.map { index ->
                NormalQuadraturePoint(mean + STANDARD_NORMAL_GH5_Z[index] * sd, STANDARD_NORMAL_GH5_WEIGHT[index])
            }

        private fun buildSlackQuadrature(config: dev.kian.mymettle.domain.inference.DynamicStochasticFrontierConfig): List<SlackQuadraturePoint> {
            val width = config.slackQuadratureMaximumSd / config.slackQuadraturePoints.toDouble()
            return List(config.slackQuadraturePoints) { index ->
                val z = (index + 0.5) * width
                SlackQuadraturePoint(
                    standardisedSlack = z,
                    logPriorMass = 0.5 * ln(2.0 / PI) - 0.5 * z * z + ln(width),
                )
            }
        }

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
            if (value < 0.5) return ln(PI) - ln(sin(PI * value)) - logGamma(1.0 - value)
            val shifted = value - 1.0
            var x = 0.99999999999980993
            coefficients.forEachIndexed { index, coefficient -> x += coefficient / (shifted + index + 1.0) }
            val t = shifted + coefficients.size - 0.5
            return 0.5 * ln(2.0 * PI) + (shifted + 0.5) * ln(t) - t + ln(x)
        }
    }
}
