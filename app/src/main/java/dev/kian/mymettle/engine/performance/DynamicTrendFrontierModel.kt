package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.inference.DynamicCapabilityFitException
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitFailureReason
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitRequest
import dev.kian.mymettle.domain.inference.DynamicCapabilityFitWarning
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
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic Candidate-v2 importance/quadrature extension of the frozen Candidate-v1 posterior.
 *
 * Candidate v1 is first fitted unchanged and acts as the proposal distribution at g=0. Candidate v2
 * expands a deterministic, high-posterior-mass subset of that joint c/b/sigma posterior across a
 * five-point Gauss-Hermite representation of g~Normal(0, sigma_g). Importance weights are exactly
 * proportional to L(g)/L(g=0) for the retained proposal nodes. This preserves joint frontier/slope/
 * nuisance/trend dependence without constructing a full new c*b*g*sigma tensor product.
 *
 * If fewer than three independent sessions exist, g is fixed exactly to zero and the complete v1
 * posterior is retained, so sparse-history Candidate v2 collapses to Candidate-v1 behaviour.
 */
class DynamicTrendFrontierModel(
    val config: DynamicTrendFrontierConfig = DynamicTrendFrontierV2.config,
    private val baseModel: DynamicStochasticFrontierModel = DynamicStochasticFrontierModel(config.baseConfig),
) {
    val modelVersion: String get() = config.semanticVersion

    private val slackQuadrature = buildSlackQuadrature(config.baseConfig)
    private val slackScratch = object : ThreadLocal<DoubleArray>() {
        override fun initialValue(): DoubleArray = DoubleArray(config.baseConfig.slackQuadraturePoints)
    }

    fun fit(request: DynamicCapabilityFitRequest): DynamicTrendFrontierFit {
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
        val baseMassCaptured: Double
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
            baseMassCaptured = 1.0
        } else {
            val proposal = selectBaseProposal(baseFit.posteriorNodes)
            baseMassCaptured = proposal.capturedMass
            if (baseMassCaptured < config.importanceMinimumBasePosteriorMass) {
                throw DynamicCapabilityFitException(
                    DynamicCapabilityFitFailureReason.NUMERICAL_BUDGET_EXCEEDED,
                    "Candidate v2 retained only $baseMassCaptured of frozen-v1 posterior mass; minimum is ${config.importanceMinimumBasePosteriorMass}.",
                )
            }
            val trendPoints = gaussHermiteTrendPoints(config.trendPriorSdLogResistancePerSession)
            val chunks = proposal.nodes.chunked(max(1, (proposal.nodes.size + FIT_PARALLELISM - 1) / FIT_PARALLELISM))
            val tasks = chunks.map { chunk ->
                Callable {
                    val local = ArrayList<RawTrendNode>(chunk.size * trendPoints.size)
                    chunk.forEach { baseNode ->
                        if (baseNode.posteriorWeight <= 0.0) return@forEach
                        val logLikelihoodAtZero = logLikelihood(baseNode, 0.0, observations)
                        trendPoints.forEach { point ->
                            val logLikelihood = if (point.trend == 0.0) logLikelihoodAtZero
                            else logLikelihood(baseNode, point.trend, observations)
                            local += RawTrendNode(
                                logFrontierAtLatestSession = baseNode.logFrontierAtReference,
                                slope = baseNode.slope,
                                frontierTrend = point.trend,
                                slackScale = baseNode.slackScale,
                                noiseScale = baseNode.noiseScale,
                                logWeight = ln(baseNode.posteriorWeight) + ln(point.priorMass) +
                                    (logLikelihood - logLikelihoodAtZero),
                            )
                        }
                    }
                    local
                }
            }
            rawNodes = FIT_EXECUTOR.invokeAll(tasks).flatMap { it.get() }
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
        val trendSummary = weightedSummary(
            posteriorNodes.map { WeightedValue(it.frontierTrend, it.posteriorWeight) },
        )
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
            add("model_development_retrospective_candidate")
            if (!learnTrend) add("frontier_trend_fixed_zero_sparse_history")
            if (learnTrend && baseMassCaptured < config.importanceTargetBasePosteriorMass) add("importance_base_mass_below_target")
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
            basePosteriorMassCaptured = baseMassCaptured,
            posteriorEffectiveNodeCount = effectiveNodeCount,
            warnings = warnings,
            posteriorNodes = posteriorNodes,
        )
    }

    /**
     * Projects the v2 joint posterior to an explicit independent-session offset and returns a
     * v1-compatible numerical view. This reuses the frozen Candidate-v1 predictive machinery while
     * retaining joint c/g covariance: every posterior node is transformed before any summary.
     */
    fun projectToSessionOffset(
        fit: DynamicTrendFrontierFit,
        sessionOffset: Double,
    ): DynamicStochasticFrontierFit {
        require(sessionOffset.isFinite())
        val projectedNodes = fit.posteriorNodes.map { node ->
            DynamicFrontierPosteriorNode(
                logFrontierAtReference = node.logFrontierAtLatestSession + node.frontierTrend * sessionOffset,
                slope = node.slope,
                slackScale = node.slackScale,
                noiseScale = node.noiseScale,
                posteriorWeight = node.posteriorWeight,
            )
        }
        val frontierSummary = weightedSummary(
            projectedNodes.map { WeightedValue(exp(it.logFrontierAtReference), it.posteriorWeight) },
        )
        val provenance = fit.frontierAtLatestSession.provenance
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
            frontierAtReference = PosteriorEstimate(frontierSummary, fit.support, provenance),
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

    fun predictFrontier(
        fit: DynamicTrendFrontierFit,
        repetitions: Double,
        sessionOffset: Double = 0.0,
    ): PosteriorEstimate = baseModel.predictFrontier(projectToSessionOffset(fit, sessionOffset), repetitions)

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

    private fun selectBaseProposal(nodes: List<DynamicFrontierPosteriorNode>): ProposalSelection {
        val indexed = nodes.withIndex().sortedWith(
            compareByDescending<IndexedValue<DynamicFrontierPosteriorNode>> { it.value.posteriorWeight }
                .thenBy { it.index },
        )
        val selected = ArrayList<DynamicFrontierPosteriorNode>()
        var mass = 0.0
        val minimum = min(config.importanceMinimumBaseNodes, indexed.size)
        for (indexedNode in indexed) {
            if (selected.size >= config.importanceMaximumBaseNodes) break
            selected += indexedNode.value
            mass += indexedNode.value.posteriorWeight
            if (selected.size >= minimum && mass >= config.importanceTargetBasePosteriorMass) break
        }
        return ProposalSelection(selected, mass.coerceIn(0.0, 1.0))
    }

    private fun logLikelihood(
        node: DynamicFrontierPosteriorNode,
        trend: Double,
        observations: List<TrendObservation>,
    ): Double {
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
            throw DynamicCapabilityFitException(DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR, "Candidate v2 produced no finite importance node.")
        }
        val maxLog = finite.maxOf { it.logWeight }
        val weights = rawNodes.map { if (it.logWeight.isFinite()) exp(it.logWeight - maxLog) else 0.0 }
        val total = weights.sum()
        if (!total.isFinite() || total <= 0.0) {
            throw DynamicCapabilityFitException(DynamicCapabilityFitFailureReason.NON_FINITE_POSTERIOR, "Candidate v2 importance normalisation failed.")
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

    private data class TrendQuadraturePoint(val trend: Double, val priorMass: Double)
    private data class SlackQuadraturePoint(val standardisedSlack: Double, val logPriorMass: Double)
    private data class ProposalSelection(val nodes: List<DynamicFrontierPosteriorNode>, val capturedMass: Double)
    private data class WeightedValue(val value: Double, val weight: Double)
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

        private fun gaussHermiteTrendPoints(sd: Double): List<TrendQuadraturePoint> {
            val z = doubleArrayOf(-2.8569700138728056, -1.355626179974266, 0.0, 1.355626179974266, 2.8569700138728056)
            val weight = doubleArrayOf(0.0112574113277207, 0.2220759220056126, 0.5333333333333333, 0.2220759220056126, 0.0112574113277207)
            return z.indices.map { TrendQuadraturePoint(z[it] * sd, weight[it]) }
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
